#!/usr/bin/env node
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
 * One-off migration tool: inject only-on-old events from one phone's
 * .ab into another phone's .ab, remapping calendar and (optionally)
 * event ids into the destination phone's namespace. See the plan doc
 * at docs/dev_todo/merge_old_phone_backup_script.md.
 *
 * Two modes:
 *
 * 1. Merge (+ optional dedup):
 *
 *     npx ts-node scripts/merge_old_phone_backup.ts \
 *       --old  path/to/old.ab \
 *       --new  path/to/new.ab \
 *       --new-provider-snapshot path/to/events.txt \
 *       --out  path/to/merged.ab \
 *       [--no-rekey-events] [--dedup] [--dry-run]
 *
 *    Injects only-on-old events into a copy of --new, writing --out.
 *    --dedup runs the dedup pass at the end of the same invocation.
 *
 * 2. Dedup-only (post-hoc against an already-merged .ab):
 *
 *     npx ts-node scripts/merge_old_phone_backup.ts \
 *       --dedup-only \
 *       --new  path/to/merged.ab \
 *       --new-provider-snapshot path/to/events.txt \
 *       --out  path/to/merged_deduped.ab
 *
 *    Skips merge entirely; --old is not used. Drops orphaned
 *    (title, istart) dupes whose identity syncId does not match the
 *    live provider syncId for their eventId.
 *
 * --new-provider-snapshot is the events.txt produced by
 * scripts/capture_calendar_snapshot.sh against the destination phone.
 * It is always required (the eventId remap and the dedup classifier
 * both consult it).
 *
 * Invocation shape mirrors scripts/clean_logs.ts:
 *   yarn merge-old-phone-backup -- --old ... --new ... [flags]
 */

import { Command } from 'commander'
import Database from 'better-sqlite3'
import * as fs from 'fs'
import * as os from 'os'
import * as path from 'path'
import { execFileSync } from 'child_process'

import { unpackAb, packAb } from './lib/ab_unpack'

/**
 * The app package we operate on. An `adb backup <pkg>` produces a single
 * package under `apps/`, but a broader `bmgr` backup can contain many:
 * pinning the package here keeps every path lookup deterministic and
 * makes a wrong-app .ab fail loudly instead of picking a sibling.
 */
const APP_PACKAGE = 'com.github.quarck.calnotify'
import {
  buildCalendarRemap,
  diffIdentities,
  insertOrSkip,
  parseProviderEvents,
  planDedup,
  readDismissed,
  readEvent,
  readIdentities,
  remapCalendarId,
  remapEventId,
  tupleKey,
  type EventKey,
  type IdentityRow,
  type ProviderEvent,
} from './lib/backup_merge'

interface Opts {
  old?: string
  new: string
  newProviderSnapshot: string
  out: string
  rekeyEvents: boolean
  dedup: boolean
  dedupOnly: boolean
  dryRun: boolean
}

interface Summary {
  identitiesOnlyOnOld: number
  identitiesOnlyOnNew: number
  identitiesOverlap: number
  identitiesDroppedNoSyncId: number
  eventsInjected: number
  eventsSkippedNoLiveRow: number
  eventsSkippedCollision: number
  identitiesInjected: number
  identitiesSkippedCollision: number
  dismissedInjected: number
  dismissedSkippedNoLiveRow: number
  dismissedSkippedCollision: number
  eventIdRemapped: number
  eventIdKeptOld: number
  calendarTuplesUsed: number
}

interface DedupSummary {
  dupGroupsSeen: number
  dupGroupsResolved: number
  dupGroupsAmbiguous: number
  eventsDeleted: number
  identityRowsDeleted: number
}

function main() {
  const program = new Command()
  program
    .option('--old <path>', 'source .ab (old phone); omit with --dedup-only')
    .requiredOption('--new <path>', 'destination .ab (new phone), or the .ab to dedup')
    .requiredOption('--new-provider-snapshot <path>', 'new phone provider events.txt from capture_calendar_snapshot.sh')
    .requiredOption('--out <path>', 'output .ab path')
    .option('--no-rekey-events', 'skip eventId remap during merge; rely on PR #291 at runtime')
    .option('--dedup', 'after merge, drop orphaned (title, istart) dupes whose identity syncId does not match live provider', false)
    .option('--dedup-only', 'skip merge; run only the dedup pass against --new and write --out', false)
    .option('--dry-run', 'print the plan without writing the output .ab', false)
    .parse(process.argv)

  const opts = program.opts<Opts>()
  if (!opts.dedupOnly && !opts.old) {
    throw new Error('--old is required unless --dedup-only is set')
  }
  console.log(opts.dedupOnly ? 'merge_old_phone_backup [dedup-only]' : 'merge_old_phone_backup')
  if (opts.old) console.log(`  --old:  ${opts.old}`)
  console.log(`  --new:  ${opts.new}`)
  console.log(`  --out:  ${opts.out}`)
  if (!opts.dedupOnly) console.log(`  --rekey-events: ${opts.rekeyEvents}`)
  console.log(`  --dedup: ${opts.dedup || opts.dedupOnly}`)
  console.log(`  --dry-run: ${opts.dryRun}`)
  console.log()

  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'ab-merge-'))
  try {
    const newDbDir = path.join(workDir, 'new')
    fs.mkdirSync(newDbDir, { recursive: true })

    console.log('unpacking .ab files...')
    extractDbs(opts.new, newDbDir)

    const providerEvents = parseProviderEvents(fs.readFileSync(opts.newProviderSnapshot, 'utf8'))
    console.log(`  new-phone provider snapshot: ${providerEvents.length} events`)
    const providerBySyncId = new Map<string, ProviderEvent>()
    for (const e of providerEvents) {
      if (e.syncId) providerBySyncId.set(e.syncId, e)
    }

    if (!opts.dedupOnly) {
      const oldDbDir = path.join(workDir, 'old')
      fs.mkdirSync(oldDbDir, { recursive: true })
      extractDbs(opts.old!, oldDbDir)
      const summary = mergeAll(oldDbDir, newDbDir, providerBySyncId, opts.rekeyEvents)
      console.log()
      console.log('== Merge summary ==')
      for (const [k, v] of Object.entries(summary)) {
        console.log(`  ${k.padEnd(28)} ${v}`)
      }
    }

    if (opts.dedup || opts.dedupOnly) {
      console.log()
      console.log('running dedup pass...')
      const dedupSummary = dedupInDbs(newDbDir, providerEvents)
      console.log('== Dedup summary ==')
      for (const [k, v] of Object.entries(dedupSummary)) {
        console.log(`  ${k.padEnd(28)} ${v}`)
      }
    }

    if (opts.dryRun) {
      console.log()
      console.log('--dry-run: not writing output .ab')
      return
    }

    console.log()
    console.log('repacking output .ab...')
    repackAb(newDbDir, opts.new, opts.out)
    console.log(`  wrote ${opts.out}`)
  } finally {
    fs.rmSync(workDir, { recursive: true, force: true })
  }
}

/**
 * Run the dedup pass in place against the already-extracted destination
 * SQLite files. Uses `planDedup` to decide which eventIds to drop, then
 * deletes those rows from `eventsV9` and their matching identity rows
 * from `eventIdentityV1`. Same idempotence guarantee as the merge pass:
 * running it twice is a no-op the second time.
 */
function dedupInDbs(newDir: string, providerEvents: ProviderEvent[]): DedupSummary {
  const eventsDb = openAndCheckpoint(findDb(newDir, 'RoomEvents'))
  const identityDb = openAndCheckpoint(findDb(newDir, 'RoomEventIdentity'))

  const events: EventKey[] = eventsDb
    .prepare('SELECT id, istart, ttl AS title, rep AS repInt FROM eventsV9')
    .all()
    .map((r: unknown) => {
      const row = r as { id: number; istart: number; title: string | null; repInt: number | null }
      return {
        id: row.id,
        istart: row.istart,
        title: row.title ?? '',
        isRepeating: row.repInt === 1,
      }
    })

  // eventId -> identity syncId (nullable). Take any one identity row per
  // eventId; a repeating event has one per instance but they carry the
  // same syncId, so `LIMIT 1` is fine for the dedup decision.
  const syncIdByEventId = new Map<number, string | null>()
  for (const row of identityDb.prepare('SELECT eventId, eventSyncId FROM eventIdentityV1').iterate()) {
    const r = row as { eventId: number; eventSyncId: string | null }
    if (!syncIdByEventId.has(r.eventId)) {
      syncIdByEventId.set(r.eventId, r.eventSyncId && r.eventSyncId !== '' ? r.eventSyncId : null)
    }
  }

  const providerSyncIdByEventId = new Map<number, string | null>()
  for (const e of providerEvents) providerSyncIdByEventId.set(e.id, e.syncId)

  const plan = planDedup(events, syncIdByEventId, providerSyncIdByEventId)

  // Actually delete.
  const deleteEvent = eventsDb.prepare('DELETE FROM eventsV9 WHERE id = ?')
  const deleteIdentity = identityDb.prepare('DELETE FROM eventIdentityV1 WHERE eventId = ?')
  let identityRowsDeleted = 0

  eventsDb.prepare('BEGIN').run()
  identityDb.prepare('BEGIN').run()
  try {
    for (const g of plan.deletionsByGroup) {
      for (const id of g.drop) {
        deleteEvent.run(id)
        const info = deleteIdentity.run(id)
        identityRowsDeleted += info.changes
      }
    }
    eventsDb.prepare('COMMIT').run()
    identityDb.prepare('COMMIT').run()
  } catch (ex) {
    try { eventsDb.prepare('ROLLBACK').run() } catch { /* already rolled back */ }
    try { identityDb.prepare('ROLLBACK').run() } catch { /* already rolled back */ }
    throw ex
  }

  eventsDb.close()
  identityDb.close()

  return {
    dupGroupsSeen: plan.deletionsByGroup.length + plan.ambiguousGroups,
    dupGroupsResolved: plan.deletionsByGroup.length,
    dupGroupsAmbiguous: plan.ambiguousGroups,
    eventsDeleted: plan.totalDeletions,
    identityRowsDeleted,
  }
}

/**
 * Extract just the SQLite files (`db/RoomEventIdentity`, `db/RoomEvents`,
 * `db/DismissedEvents`, `db/CalendarMonitor`) plus their `-wal` and
 * `-shm` sidecars from the .ab into `outDir`. Scoped to `APP_PACKAGE` so
 * a multi-package backup doesn't pick a sibling app's DBs.
 */
function extractDbs(abPath: string, outDir: string): void {
  const tarBytes = unpackAb(abPath)
  const tarPath = path.join(outDir, 'source.tar')
  fs.writeFileSync(tarPath, tarBytes)
  execFileSync(
    'tar',
    [
      '-xf', tarPath,
      '-C', outDir,
      '--wildcards',
      `apps/${APP_PACKAGE}/db/RoomEventIdentity*`,
      `apps/${APP_PACKAGE}/db/RoomEvents*`,
      `apps/${APP_PACKAGE}/db/DismissedEvents*`,
      `apps/${APP_PACKAGE}/db/CalendarMonitor*`,
    ],
    { stdio: 'inherit' }
  )
  fs.unlinkSync(tarPath)

  const expected = path.join(outDir, 'apps', APP_PACKAGE, 'db')
  if (!fs.existsSync(expected)) {
    throw new Error(
      `Extracted .ab has no ${APP_PACKAGE} DB dir. Is ${abPath} a backup of a different app?`
    )
  }
}

function findDb(dir: string, name: string): string {
  return path.join(dir, 'apps', APP_PACKAGE, 'db', name)
}

/**
 * Open a Room database file, force a full WAL checkpoint so all data is
 * in the main .db (so re-tarring the file alone captures everything),
 * then return the handle. Any -wal / -shm sidecars can then be dropped
 * without losing data.
 */
function openAndCheckpoint(dbPath: string): Database.Database {
  const db = new Database(dbPath)
  db.pragma('journal_mode = DELETE')
  db.pragma('wal_checkpoint(TRUNCATE)')
  return db
}

function mergeAll(
  oldDir: string,
  newDir: string,
  providerBySyncId: Map<string, ProviderEvent>,
  rekeyEvents: boolean
): Summary {
  const oldIdentityDb = openAndCheckpoint(findDb(oldDir, 'RoomEventIdentity'))
  const oldEventsDb = openAndCheckpoint(findDb(oldDir, 'RoomEvents'))
  const oldDismissedDb = openAndCheckpoint(findDb(oldDir, 'DismissedEvents'))

  const newIdentityDb = openAndCheckpoint(findDb(newDir, 'RoomEventIdentity'))
  const newEventsDb = openAndCheckpoint(findDb(newDir, 'RoomEvents'))
  const newDismissedDb = openAndCheckpoint(findDb(newDir, 'DismissedEvents'))

  const oldIdentities = readIdentities(oldIdentityDb)
  const newIdentities = readIdentities(newIdentityDb)
  const diff = diffIdentities(oldIdentities, newIdentities)
  const calendarRemap = buildCalendarRemap(newIdentities)

  console.log(`  old identities: ${oldIdentities.length}`)
  console.log(`  new identities: ${newIdentities.length}`)
  console.log(`  only-on-old:    ${diff.onlyOnOld.length}`)
  console.log(`  only-on-new:    ${diff.onlyOnNew.length}`)
  console.log(`  overlap:        ${diff.overlap.length}`)
  console.log(`  dropped (no syncId): ${diff.droppedNoSyncId.length}`)
  console.log(`  distinct dest calendars: ${calendarRemap.size}`)

  const summary: Summary = {
    identitiesOnlyOnOld: diff.onlyOnOld.length,
    identitiesOnlyOnNew: diff.onlyOnNew.length,
    identitiesOverlap: diff.overlap.length,
    identitiesDroppedNoSyncId: diff.droppedNoSyncId.length,
    eventsInjected: 0,
    eventsSkippedNoLiveRow: 0,
    eventsSkippedCollision: 0,
    identitiesInjected: 0,
    identitiesSkippedCollision: 0,
    dismissedInjected: 0,
    dismissedSkippedNoLiveRow: 0,
    dismissedSkippedCollision: 0,
    eventIdRemapped: 0,
    eventIdKeptOld: 0,
    calendarTuplesUsed: 0,
  }

  const usedTuples = new Set<string>()

  // Three separate SQLite files, no shared transaction. Each DB gets
  // its own transaction so a mid-run failure leaves that DB consistent;
  // cross-DB atomicity is unnecessary because the output .ab is a
  // throwaway artifact -- rerun the tool if it dies.
  const dbs = [newEventsDb, newIdentityDb, newDismissedDb]
  for (const db of dbs) db.prepare('BEGIN').run()
  try {
    for (const src of diff.onlyOnOld) {
      injectOne(
        src,
        oldEventsDb,
        oldDismissedDb,
        newEventsDb,
        newIdentityDb,
        newDismissedDb,
        calendarRemap,
        providerBySyncId,
        rekeyEvents,
        summary,
        usedTuples
      )
    }
    for (const db of dbs) db.prepare('COMMIT').run()
  } catch (ex) {
    for (const db of dbs) {
      try { db.prepare('ROLLBACK').run() } catch { /* already rolled back */ }
    }
    throw ex
  }

  summary.calendarTuplesUsed = usedTuples.size

  oldIdentityDb.close()
  oldEventsDb.close()
  oldDismissedDb.close()
  newIdentityDb.close()
  newEventsDb.close()
  newDismissedDb.close()

  return summary
}

function injectOne(
  src: IdentityRow,
  oldEventsDb: Database.Database,
  oldDismissedDb: Database.Database,
  newEventsDb: Database.Database,
  newIdentityDb: Database.Database,
  newDismissedDb: Database.Database,
  calendarRemap: Map<string, number>,
  providerBySyncId: Map<string, ProviderEvent>,
  rekeyEvents: boolean,
  summary: Summary,
  usedTuples: Set<string>
): void {
  const oldEvent = readEvent(oldEventsDb, src.eventId, src.instanceStartTime)
  if (!oldEvent) {
    summary.eventsSkippedNoLiveRow += 1
    return
  }

  const newCid = remapCalendarId(src, calendarRemap)
  usedTuples.add(tupleKey(src))

  let newEventId = src.eventId
  if (rekeyEvents) {
    const remapped = remapEventId(src, providerBySyncId)
    if (remapped !== null) {
      newEventId = remapped
      summary.eventIdRemapped += 1
    } else {
      summary.eventIdKeptOld += 1
    }
  } else {
    summary.eventIdKeptOld += 1
  }

  const eventRow: Record<string, unknown> = { ...oldEvent, cid: newCid, id: newEventId }
  if (insertOrSkip(newEventsDb, 'eventsV9', eventRow)) {
    summary.eventsInjected += 1
  } else {
    summary.eventsSkippedCollision += 1
    return
  }

  const identityRow: Record<string, unknown> = {
    eventId: newEventId,
    instanceStartTime: src.instanceStartTime,
    calendarAccountName: src.accountName,
    calendarAccountType: src.accountType,
    calendarOwnerAccount: src.ownerAccount,
    calendarDisplayName: src.displayName,
    calendarName: src.name,
    eventSyncId: src.eventSyncId,
    eventUid: src.eventUid,
    originalCalendarId: newCid,
    originalEventId: newEventId,
    capturedAtTime: src.capturedAtTime,
    resolutionAttemptCount: 0,
    lastResolutionAttemptTime: 0,
  }
  if (insertOrSkip(newIdentityDb, 'eventIdentityV1', identityRow)) {
    summary.identitiesInjected += 1
  } else {
    summary.identitiesSkippedCollision += 1
  }

  const oldDismissed = readDismissed(oldDismissedDb, src.eventId, src.instanceStartTime)
  if (oldDismissed) {
    const dismissedRow: Record<string, unknown> = {
      ...oldDismissed,
      calendarId: newCid,
      eventId: newEventId,
    }
    if (insertOrSkip(newDismissedDb, 'dismissedEventsV2', dismissedRow)) {
      summary.dismissedInjected += 1
    } else {
      summary.dismissedSkippedCollision += 1
    }
  } else {
    summary.dismissedSkippedNoLiveRow += 1
  }
}

/**
 * Re-tar the destination phone's extracted app dir (with the modified
 * DB files in place) and re-wrap it as an .ab, using the original .ab's
 * inner tar as the ordering/permissions template.
 *
 * The approach: unpack the destination .ab in full to a temp dir (not
 * just the DB files), overlay our modified DB files, tar it back up
 * preserving the same file order the original had, deflate, prepend
 * the 24-byte header.
 */
function repackAb(newDbDir: string, newAbPath: string, outAbPath: string): void {
  const repackDir = fs.mkdtempSync(path.join(os.tmpdir(), 'ab-repack-'))
  try {
    const fullTar = unpackAb(newAbPath)
    const fullTarPath = path.join(repackDir, 'full.tar')
    fs.writeFileSync(fullTarPath, fullTar)

    const listRaw = execFileSync('tar', ['-tf', fullTarPath], { encoding: 'utf8' })
    const fileList = listRaw.split('\n').filter((l) => l.length > 0)

    execFileSync('tar', ['-xf', fullTarPath, '-C', repackDir], { stdio: 'inherit' })
    fs.unlinkSync(fullTarPath)

    for (const dbName of ['RoomEventIdentity', 'RoomEvents', 'DismissedEvents', 'CalendarMonitor']) {
      const src = findDb(newDbDir, dbName)
      const dst = path.join(repackDir, 'apps', APP_PACKAGE, 'db', dbName)
      fs.copyFileSync(src, dst)
      // Drop any -wal/-shm sidecars from the repack -- we checkpointed
      // and switched to journal_mode=DELETE, so all data is in the .db.
      for (const suffix of ['-wal', '-shm']) {
        const sidecar = dst + suffix
        if (fs.existsSync(sidecar)) fs.unlinkSync(sidecar)
      }
    }

    // Re-tar in the ORIGINAL file order, minus the -wal/-shm sidecars
    // we removed. Preserving order matters because Android's restore
    // reads the tar sequentially.
    const dbSidecarSuffixes = ['-wal', '-shm']
    const keptFiles = fileList.filter((f) => {
      const base = path.basename(f)
      return !dbSidecarSuffixes.some((sfx) =>
        ['RoomEventIdentity', 'RoomEvents', 'DismissedEvents', 'CalendarMonitor'].some(
          (db) => base === db + sfx
        )
      )
    })

    const listFile = path.join(repackDir, 'files.list')
    fs.writeFileSync(listFile, keptFiles.join('\n') + '\n')

    const outTarPath = path.join(repackDir, 'out.tar')
    execFileSync(
      'tar',
      ['-cf', outTarPath, '-C', repackDir, '--no-recursion', '-T', listFile, '--format=ustar'],
      { stdio: 'inherit' }
    )

    const outTarBytes = fs.readFileSync(outTarPath)
    packAb(outTarBytes, outAbPath)
  } finally {
    fs.rmSync(repackDir, { recursive: true, force: true })
  }
}

main()
