//
//   Calendar Notifications Plus
//   Copyright (C) 2026 William Harris (wharris+cnplus@upscalews.com)
//

import Database from 'better-sqlite3'
import * as fs from 'fs'
import * as os from 'os'
import * as path from 'path'

import { packAb, unpackAb } from '../lib/ab_unpack'
import {
  buildCalendarRemap,
  diffIdentities,
  insertOrSkip,
  parseProviderEvents,
  planDedup,
  remapCalendarId,
  remapEventId,
  type EventKey,
  type IdentityRow,
} from '../lib/backup_merge'

function identity(overrides: Partial<IdentityRow>): IdentityRow {
  return {
    eventId: 100,
    instanceStartTime: 1_700_000_000_000,
    accountName: 'user@example.com',
    accountType: 'com.google',
    ownerAccount: 'user@example.com',
    displayName: 'Work',
    name: 'Work',
    eventSyncId: 'sync-1',
    eventUid: null,
    originalCalendarId: 6,
    originalEventId: 100,
    capturedAtTime: 1,
    ...overrides,
  }
}

describe('ab_unpack', () => {
  test('pack then unpack round-trips the payload', () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'ab-rt-'))
    try {
      const payload = Buffer.from('hello android backup')
      const abPath = path.join(tmp, 'test.ab')
      packAb(payload, abPath)
      expect(unpackAb(abPath).equals(payload)).toBe(true)
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true })
    }
  })

  test('rejects a file without the ANDROID BACKUP header', () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'ab-rt-'))
    try {
      const abPath = path.join(tmp, 'bad.ab')
      fs.writeFileSync(abPath, Buffer.from('not-a-backup-file at all'))
      expect(() => unpackAb(abPath)).toThrow(/Not a plain unencrypted \.ab/)
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true })
    }
  })
})

describe('diffIdentities', () => {
  test('buckets by (syncId, istart)', () => {
    const oldRows = [
      identity({ eventId: 1, eventSyncId: 'a' }),
      identity({ eventId: 2, eventSyncId: 'b' }),
      identity({ eventId: 3, eventSyncId: null }),
      identity({ eventId: 4, eventSyncId: 'shared' }),
    ]
    const newRows = [
      identity({ eventId: 10, eventSyncId: 'shared' }),
      identity({ eventId: 11, eventSyncId: 'c' }),
    ]
    const d = diffIdentities(oldRows, newRows)
    expect(d.onlyOnOld.map((r) => r.eventSyncId).sort()).toEqual(['a', 'b'])
    expect(d.onlyOnNew.map((r) => r.eventSyncId).sort()).toEqual(['c'])
    expect(d.overlap.map((r) => r.eventSyncId)).toEqual(['shared'])
    expect(d.droppedNoSyncId.map((r) => r.eventId)).toEqual([3])
  })

  test('same syncId but different istart is not an overlap', () => {
    const oldRows = [identity({ eventSyncId: 'a', instanceStartTime: 100 })]
    const newRows = [identity({ eventSyncId: 'a', instanceStartTime: 200 })]
    const d = diffIdentities(oldRows, newRows)
    expect(d.overlap).toHaveLength(0)
    expect(d.onlyOnOld).toHaveLength(1)
    expect(d.onlyOnNew).toHaveLength(1)
  })
})

describe('calendar remap', () => {
  test('maps a source tuple to the destination cid', () => {
    const newRows = [
      identity({ displayName: 'Work', originalCalendarId: 42 }),
      identity({ displayName: 'Personal', name: 'Personal', originalCalendarId: 99 }),
    ]
    const remap = buildCalendarRemap(newRows)
    const src = identity({ displayName: 'Work' })
    expect(remapCalendarId(src, remap)).toBe(42)
  })

  test('throws on an unmatched tuple', () => {
    const remap = buildCalendarRemap([identity({ displayName: 'Work', originalCalendarId: 42 })])
    const src = identity({ displayName: 'Unknown', name: 'Unknown' })
    expect(() => remapCalendarId(src, remap)).toThrow(/No calendar match/)
  })
})

describe('remapEventId', () => {
  test('returns the destination id when provider knows the syncId', () => {
    const provider = new Map([
      ['sync-1', { id: 500, calendarId: 6, syncId: 'sync-1', uid2445: null }],
    ])
    const src = identity({ eventSyncId: 'sync-1' })
    expect(remapEventId(src, provider)).toBe(500)
  })

  test('returns null when syncId is unknown or missing', () => {
    const provider = new Map()
    expect(remapEventId(identity({ eventSyncId: 'unknown' }), provider)).toBeNull()
    expect(remapEventId(identity({ eventSyncId: null }), provider)).toBeNull()
  })
})

describe('parseProviderEvents', () => {
  test('parses events.txt Row: format including NULLs', () => {
    const text = [
      'Row: 0 _id=1, calendar_id=6, _sync_id=abc123, uid2445=NULL, original_sync_id=NULL, title=Meet',
      'Row: 1 _id=2, calendar_id=7, _sync_id=NULL, uid2445=xyz@example.com, title=Ping',
      '',
      'not a row line',
    ].join('\n')
    const events = parseProviderEvents(text)
    expect(events).toEqual([
      { id: 1, calendarId: 6, syncId: 'abc123', uid2445: null },
      { id: 2, calendarId: 7, syncId: null, uid2445: 'xyz@example.com' },
    ])
  })
})

describe('planDedup', () => {
  const ev = (id: number, title: string, istart = 100, isRepeating = false): EventKey => ({
    id, istart, title, isRepeating,
  })

  test('keeps the live row and drops the orphan in a 2-row dup group', () => {
    const events = [ev(4223, 'Brewton'), ev(3380, 'Brewton')]
    // 4223's identity has empty syncId (orphan); 3380's syncId matches provider
    const idSyncs = new Map([[4223, null], [3380, 'sync-3380']])
    const provSyncs = new Map([[4223, null], [3380, 'sync-3380']])
    const plan = planDedup(events, idSyncs, provSyncs)
    expect(plan.deletionsByGroup).toEqual([
      { title: 'Brewton', istart: 100, keep: 3380, drop: [4223] },
    ])
    expect(plan.ambiguousGroups).toBe(0)
    expect(plan.totalDeletions).toBe(1)
  })

  test('leaves a group alone when zero rows associate', () => {
    const events = [ev(1, 'x'), ev(2, 'x')]
    const idSyncs = new Map([[1, null], [2, null]])
    const provSyncs = new Map<number, string | null>()
    const plan = planDedup(events, idSyncs, provSyncs)
    expect(plan.deletionsByGroup).toEqual([])
    expect(plan.ambiguousGroups).toBe(1)
    expect(plan.totalDeletions).toBe(0)
  })

  test('leaves a group alone when 2+ rows associate (genuine multi-cal duplicate)', () => {
    const events = [ev(1, 'x'), ev(2, 'x')]
    const idSyncs = new Map([[1, 'a'], [2, 'b']])
    const provSyncs = new Map([[1, 'a'], [2, 'b']])
    const plan = planDedup(events, idSyncs, provSyncs)
    expect(plan.deletionsByGroup).toEqual([])
    expect(plan.ambiguousGroups).toBe(1)
  })

  test('skips repeating events entirely -- occurrences share (title, istart) legitimately', () => {
    const events = [ev(1, 'weekly', 100, true), ev(2, 'weekly', 100, true)]
    const idSyncs = new Map<number, string | null>()
    const provSyncs = new Map<number, string | null>()
    const plan = planDedup(events, idSyncs, provSyncs)
    expect(plan.deletionsByGroup).toEqual([])
    expect(plan.ambiguousGroups).toBe(0)
  })

  test('ignores non-dup rows', () => {
    const events = [ev(1, 'unique-a'), ev(2, 'unique-b')]
    const plan = planDedup(events, new Map(), new Map())
    expect(plan.deletionsByGroup).toEqual([])
    expect(plan.totalDeletions).toBe(0)
  })

  test('empty identity syncId is treated the same as null', () => {
    const events = [ev(1, 'x'), ev(2, 'x')]
    const idSyncs = new Map([[1, ''], [2, 'sync-2']])
    const provSyncs = new Map([[1, null], [2, 'sync-2']])
    const plan = planDedup(events, idSyncs, provSyncs)
    // The empty-string syncId cannot match provider's null, so id 1 is dead
    expect(plan.deletionsByGroup).toEqual([
      { title: 'x', istart: 100, keep: 2, drop: [1] },
    ])
  })
})

describe('insertOrSkip', () => {
  test('inserts a new row and skips on PK collision', () => {
    const db = new Database(':memory:')
    db.exec('CREATE TABLE t (id INTEGER PRIMARY KEY, name TEXT)')
    expect(insertOrSkip(db, 't', { id: 1, name: 'a' })).toBe(true)
    expect(insertOrSkip(db, 't', { id: 1, name: 'b' })).toBe(false)
    const row = db.prepare('SELECT name FROM t WHERE id = 1').get() as { name: string }
    expect(row.name).toBe('a')
    db.close()
  })
})
