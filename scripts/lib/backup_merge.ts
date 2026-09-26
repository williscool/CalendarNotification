//
//   Calendar Notifications Plus
//   Copyright (C) 2026 William Harris (wharris+cnplus@upscalews.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//

/**
 * Merge logic for injecting only-on-old rows from one phone's Android
 * backup into another phone's backup, remapping calendarId and
 * (optionally) eventId into the destination phone's namespace.
 *
 * Pure functions over open `better-sqlite3` handles so the flow can be
 * unit-tested with in-memory databases and small fixtures. See the plan
 * in docs/dev_todo/merge_old_phone_backup_script.md.
 */

import type { Database } from 'better-sqlite3'

/** Tuple that uniquely identifies a calendar across devices (measured 16/16 unique). */
export interface CalendarTuple {
  accountName: string
  accountType: string
  ownerAccount: string
  displayName: string
  name: string
}

/** One row of `eventIdentityV1`, projected to what the merge needs. */
export interface IdentityRow extends CalendarTuple {
  eventId: number
  instanceStartTime: number
  eventSyncId: string | null
  eventUid: string | null
  originalCalendarId: number
  originalEventId: number
  capturedAtTime: number
}

/** One row of the new-phone provider snapshot (events.txt). */
export interface ProviderEvent {
  id: number
  calendarId: number
  syncId: string | null
  uid2445: string | null
}

export interface Diff {
  onlyOnOld: IdentityRow[]
  onlyOnNew: IdentityRow[]
  overlap: IdentityRow[]
  droppedNoSyncId: IdentityRow[]
}

/**
 * Bucket identities by (syncId, instanceStartTime).
 *
 * Rows without a syncId are unmergeable -- there is no stable key to
 * match them across devices -- so they are separated for reporting and
 * dropped. The old-phone measurement showed ~1663/4563 in this bucket,
 * mostly historical dismissed events past the sync window.
 */
export function diffIdentities(oldRows: IdentityRow[], newRows: IdentityRow[]): Diff {
  const key = (r: IdentityRow) => `${r.eventSyncId}|${r.instanceStartTime}`

  const oldWithSync: IdentityRow[] = []
  const droppedNoSyncId: IdentityRow[] = []
  for (const r of oldRows) {
    if (r.eventSyncId) oldWithSync.push(r)
    else droppedNoSyncId.push(r)
  }

  const newKeys = new Set<string>()
  for (const r of newRows) if (r.eventSyncId) newKeys.add(key(r))

  const oldKeys = new Set<string>()
  for (const r of oldWithSync) oldKeys.add(key(r))

  const onlyOnOld: IdentityRow[] = []
  const overlap: IdentityRow[] = []
  for (const r of oldWithSync) {
    if (newKeys.has(key(r))) overlap.push(r)
    else onlyOnOld.push(r)
  }

  const onlyOnNew: IdentityRow[] = []
  for (const r of newRows) {
    if (r.eventSyncId && !oldKeys.has(key(r))) onlyOnNew.push(r)
  }

  return { onlyOnOld, onlyOnNew, overlap, droppedNoSyncId }
}

/**
 * Build a calendar-tuple → new `cid` lookup from the destination phone's
 * identity table. Every distinct tuple on the new side maps to the
 * `originalCalendarId` its identity rows were captured under -- which
 * on the destination phone is the live cid.
 */
export function buildCalendarRemap(newRows: IdentityRow[]): Map<string, number> {
  const tupleKey = (t: CalendarTuple) =>
    `${t.accountName}\u0000${t.accountType}\u0000${t.ownerAccount}\u0000${t.displayName}\u0000${t.name}`
  const out = new Map<string, number>()
  for (const r of newRows) {
    out.set(tupleKey(r), r.originalCalendarId)
  }
  return out
}

export function tupleKey(t: CalendarTuple): string {
  return `${t.accountName}\u0000${t.accountType}\u0000${t.ownerAccount}\u0000${t.displayName}\u0000${t.name}`
}

/**
 * Look up the destination-phone `cid` for a source-phone identity's
 * calendar tuple. Throws on miss -- unmatched tuples would silently
 * orphan rows, and both phones measured 16/16 unique so a miss is
 * a real problem to investigate, not to paper over.
 */
export function remapCalendarId(
  row: IdentityRow,
  calendarRemap: Map<string, number>
): number {
  const key = tupleKey(row)
  const cid = calendarRemap.get(key)
  if (cid === undefined) {
    throw new Error(
      `No calendar match on destination for tuple: ` +
        `accountName=${row.accountName}, accountType=${row.accountType}, ` +
        `ownerAccount=${row.ownerAccount}, displayName=${row.displayName}, name=${row.name}`
    )
  }
  return cid
}

/**
 * Look up the destination-phone event id for a source-phone identity,
 * by matching syncId in the new phone's provider snapshot. Returns null
 * when the provider doesn't know this syncId (e.g. the calendar hasn't
 * synced it yet) -- caller falls back to keeping the old id, which the
 * runtime re-key layer in PR #291 will resolve on the next rescan.
 */
export function remapEventId(
  row: IdentityRow,
  providerBySyncId: Map<string, ProviderEvent>
): number | null {
  if (!row.eventSyncId) return null
  const hit = providerBySyncId.get(row.eventSyncId)
  return hit ? hit.id : null
}

// ---- Dedup -------------------------------------------------------------

/**
 * One row of `eventsV9` reduced to what the dedup planner needs.
 * `isRepeating` mirrors `rep` (0/1/null) so the planner can skip
 * repeating events -- their instances legitimately share (title, istart)
 * across occurrences and are not dupes.
 */
export interface EventKey {
  id: number
  istart: number
  title: string
  isRepeating: boolean
}

export interface DedupPlan {
  deletionsByGroup: Array<{ title: string; istart: number; keep: number; drop: number[] }>
  ambiguousGroups: number
  totalDeletions: number
}

/**
 * Plan a dedup pass over a merged eventsV9.
 *
 * The merge script's diff key is (syncId, istart). That misses a case:
 * the *same* underlying task exists as multiple independent Google events
 * across accounts, each with its own syncId. The merge treats them as
 * distinct and injects all of them, producing (title, istart) dupes
 * where the syncIds legitimately differ.
 *
 * The dedup rule: for each (title, istart) non-repeating dup group,
 * classify each row as "live" if its identity syncId matches the live
 * provider's syncId for that eventId. Keep the live row, drop the rest.
 * A dup group with zero or 2+ live rows is left alone (nothing safe to
 * do) and counted as ambiguous.
 *
 * @param events                 rows of eventsV9 (id, istart, title, isRepeating)
 * @param syncIdByEventId        eventId -> identity's syncId, or null if empty/absent
 * @param providerSyncIdByEventId eventId -> provider's live syncId, or null
 */
export function planDedup(
  events: EventKey[],
  syncIdByEventId: Map<number, string | null>,
  providerSyncIdByEventId: Map<number, string | null>
): DedupPlan {
  const groups = new Map<string, EventKey[]>()
  for (const e of events) {
    if (e.isRepeating) continue
    const key = `${e.title}\u0000${e.istart}`
    let bucket = groups.get(key)
    if (!bucket) {
      bucket = []
      groups.set(key, bucket)
    }
    bucket.push(e)
  }

  const deletionsByGroup: DedupPlan['deletionsByGroup'] = []
  let ambiguousGroups = 0
  let totalDeletions = 0

  for (const [, bucket] of groups) {
    if (bucket.length < 2) continue

    const live: EventKey[] = []
    const dead: EventKey[] = []
    for (const row of bucket) {
      const ident = syncIdByEventId.get(row.id) ?? null
      const prov = providerSyncIdByEventId.get(row.id) ?? null
      const isLive = !!ident && ident === prov
      if (isLive) live.push(row)
      else dead.push(row)
    }

    if (live.length === 1 && dead.length >= 1) {
      deletionsByGroup.push({
        title: bucket[0].title,
        istart: bucket[0].istart,
        keep: live[0].id,
        drop: dead.map((r) => r.id),
      })
      totalDeletions += dead.length
    } else {
      ambiguousGroups += 1
    }
  }

  return { deletionsByGroup, ambiguousGroups, totalDeletions }
}

// ---- SQLite readers/writers --------------------------------------------

export function readIdentities(db: Database): IdentityRow[] {
  return db
    .prepare(
      `SELECT
         eventId, instanceStartTime,
         calendarAccountName  AS accountName,
         calendarAccountType  AS accountType,
         calendarOwnerAccount AS ownerAccount,
         calendarDisplayName  AS displayName,
         calendarName         AS name,
         eventSyncId, eventUid,
         originalCalendarId, originalEventId, capturedAtTime
       FROM eventIdentityV1`
    )
    .all() as IdentityRow[]
}

export interface EventRow {
  cid: number | null
  id: number
  istart: number
  [col: string]: unknown
}

export function readEvent(db: Database, id: number, istart: number): EventRow | undefined {
  return db
    .prepare('SELECT * FROM eventsV9 WHERE id = ? AND istart = ?')
    .get(id, istart) as EventRow | undefined
}

export interface DismissedRow {
  eventId: number
  instanceStart: number
  calendarId: number | null
  [col: string]: unknown
}

export function readDismissed(
  db: Database,
  eventId: number,
  instanceStart: number
): DismissedRow | undefined {
  return db
    .prepare('SELECT * FROM dismissedEventsV2 WHERE eventId = ? AND instanceStart = ?')
    .get(eventId, instanceStart) as DismissedRow | undefined
}

/**
 * Insert a row into a table, skipping (returning false) on any PK
 * collision. Uses INSERT OR IGNORE with a rowcount check so a collision
 * is silent-but-detectable rather than throwing.
 */
export function insertOrSkip(
  db: Database,
  table: string,
  row: Record<string, unknown>
): boolean {
  const cols = Object.keys(row)
  const placeholders = cols.map(() => '?').join(', ')
  const colList = cols.map((c) => `"${c}"`).join(', ')
  const stmt = db.prepare(
    `INSERT OR IGNORE INTO "${table}" (${colList}) VALUES (${placeholders})`
  )
  const info = stmt.run(...cols.map((c) => row[c] as never))
  return info.changes === 1
}

// ---- Provider snapshot parser ------------------------------------------

/**
 * Parse the events.txt file produced by capture_calendar_snapshot.sh:
 *
 *   Row: 0 _id=1, calendar_id=6, _sync_id=abc..., uid2445=NULL, ...
 *
 * Just the fields we need. NULL literal is preserved as null; missing
 * lines are ignored.
 */
export function parseProviderEvents(text: string): ProviderEvent[] {
  const out: ProviderEvent[] = []
  for (const line of text.split(/\r?\n/)) {
    if (!line.startsWith('Row:')) continue
    const fields = parseRowLine(line)
    const idStr = fields.get('_id')
    const calStr = fields.get('calendar_id')
    if (!idStr || !calStr) continue
    out.push({
      id: Number(idStr),
      calendarId: Number(calStr),
      syncId: nullOrString(fields.get('_sync_id')),
      uid2445: nullOrString(fields.get('uid2445')),
    })
  }
  return out
}

function parseRowLine(line: string): Map<string, string> {
  const body = line.replace(/^Row:\s*\d+\s*/, '')
  const out = new Map<string, string>()
  for (const pair of body.split(', ')) {
    const eq = pair.indexOf('=')
    if (eq === -1) continue
    out.set(pair.substring(0, eq).trim(), pair.substring(eq + 1))
  }
  return out
}

function nullOrString(v: string | undefined): string | null {
  if (v === undefined || v === 'NULL' || v === '') return null
  return v
}
