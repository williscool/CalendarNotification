# Migration Tool: Merge Old-Phone Backup Into New-Phone Backup

**One-off migration for issue [#273](https://github.com/williscool/CalendarNotification/issues/273).** Not a shipping feature; a tool to recover events off an old phone's backup and inject them into a new phone's backup, so a restore lands a working state instead of clobbering current-day data.

## Overview

The scenario: transferring event state from one phone to another when both have live app data. The old phone had 4563 captured identity rows across two Room databases. The new phone has 4593 identity rows of its own but a different set of live events — 200 events exist only on the old phone, 191 only on the new, 181 overlap.

This tool reads the old-phone `.ab`, remaps calendar and event ids into the new phone's namespace, injects the only-on-old rows into a copy of the new-phone `.ab`, and produces a merged `.ab` file that `adb restore` accepts. Nothing on the new phone is deleted or overwritten — merge is additive.

**Invocation shape follows `scripts/clean_logs.ts`:** TypeScript via `ts-node`, `commander` for flags, npm-script alias in `package.json`.

## Background

Both `.ab` files are the standard Android backup format: a 24-byte plain-text header, then a zlib-deflated tarball of `apps/<pkg>/{db,f,sp}/…`. The tarball contains four SQLite files that carry event state:

- `RoomEvents` (`eventsV9`) — live events
- `RoomEventIdentity` (`eventIdentityV1`) — portable identity per event, added by [#277](https://github.com/williscool/CalendarNotification/pull/277)
- `DismissedEvents` (`dismissedEventsV2`) — history
- `CalendarMonitor` (`manualAlertsV1`) — pending alerts

Identity rows carry the (`accountName`, `accountType`, `ownerAccount`, `displayName`, `name`) tuple used by the shipped calendar-resolution matcher (see [`docs/architecture/portable_event_identity.md`](../architecture/portable_event_identity.md)). Both phones measured 16/16 unique matches on that tuple — remapping `cid` is deterministic, no ambiguity to resolve.

Event-id remapping is the same shape as the runtime layer that just shipped in [#291](https://github.com/williscool/CalendarNotification/pull/291): look up each identity's `syncId` in the new phone's provider via a captured snapshot, replace the stored `eventId`. Doing it in the merge script avoids a whole rescan cycle after restore where notifications open the wrong event.

**Why a script and not the runtime**: on-device restore replaces the whole database. Events that exist only on the old phone would come through fine once #291 rescans; events that exist only on the new phone would be destroyed. Merging preserves both sides.

## Plan

### Phase 1: Unpack and load

Take an `.ab` file, strip the 24-byte header, zlib-inflate the payload, extract the four SQLite files into a working directory. Load each into an in-memory sqlite handle (`better-sqlite3`, already indirectly usable via `npm install`).

Files: `scripts/merge_old_phone_backup.ts` (new), `scripts/lib/ab_unpack.ts` (new, extracted so it's independently testable).

### Phase 2: Diff old vs new

Enumerate identity rows on both sides, keyed by (`syncId`, `instanceStartTime`). Bucket into:

- **only-on-old** — inject into new
- **only-on-new** — leave alone
- **overlap** — skip; new phone's row is more trustworthy

Rows with a null `syncId` are dropped (they can't be safely matched — the old-phone snapshot showed 1663/4563 in this bucket, mostly historical dismissed events past the sync window).

### Phase 3: Remap `cid`

For each only-on-old row, look up its calendar tuple in the new phone's identity table, find the corresponding `cid` in the new phone's namespace. Fail loudly if a calendar tuple isn't matched — this shouldn't happen given the 16/16 unique measurement, but a missed match would silently orphan the row.

### Phase 4: Remap `eventId` (optional, via `--rekey-events`)

Two modes:

- **`--rekey-events` (default)**: query the new phone's `provider_events_snapshot.csv` (captured by `scripts/capture_calendar_snapshot.sh`) for each identity's `syncId`, rewrite `eventId` to the new value. Skip rows whose syncId isn't in the snapshot — they get injected under the old id, and #291's runtime layer catches them on the next rescan.
- **`--no-rekey-events`**: inject rows with old ids intact; rely on #291 to fix them at runtime after restore.

The default is on because it makes the "restore then tap a notification" path work immediately, without waiting for a rescan cycle.

### Phase 5: Inject and rewrite `eventsV9`, `eventIdentityV1`, `dismissedEventsV2`

Bulk-insert the remapped rows into copies of the new phone's SQLite files, skipping any PK collision (belt-and-braces — the diff already excluded overlaps, so a collision means the diff was wrong). Skip `manualAlertsV1` entirely — those are pending alarms for events that no longer exist as scheduled reminders on the new phone; injecting stale ones would fire notifications for events the user already dealt with.

### Phase 6: Repack

Re-tar the modified SQLite files, re-deflate, prepend the 24-byte header, write out `cnp_backup_<timestamp>_merged.ab`. Print a summary: rows injected per table, rows skipped, calendar-tuple matches used.

## Files Changed Summary

| File | Change |
|------|--------|
| `scripts/merge_old_phone_backup.ts` | New — main entry point, commander CLI |
| `scripts/lib/ab_unpack.ts` | New — pack/unpack the `.ab` format, unit-testable |
| `scripts/lib/backup_merge.ts` | New — the diff/remap/inject logic, pure functions over sqlite handles |
| `scripts/__tests__/backup_merge.test.ts` | New — Jest unit tests using in-memory SQLite fixtures |
| `package.json` | Add `merge-old-phone-backup` script alias, add `better-sqlite3` if missing |

## Invocation

Mirrors `clean-logs`:

```bash
npx ts-node scripts/merge_old_phone_backup.ts \
  --old tmp/android_backups/cnp_backup_2026_09_26_quiesced.ab \
  --new tmp/android_backups/cnp_backup_2026_09_26_new_phone_v9.28.0.ab \
  --new-provider-snapshot tmp/calendar_snapshot_20260926_162050/provider_events_snapshot.csv \
  --out tmp/android_backups/cnp_backup_2026_09_26_merged.ab \
  [--rekey-events | --no-rekey-events] \
  [--dry-run]
```

`--dry-run` prints the summary without writing the output `.ab` — used to inspect what the tool would inject before committing.

Also usable as `npm run merge-old-phone-backup -- --old … --new … …`.

## Testing

Unit tests (Jest, following `scripts/__tests__/bundle_or_skip.test.ts`):

- **`.ab` pack/unpack round-trip** — deflate then inflate a fixture returns the original bytes; header stripped and restored correctly
- **Calendar-tuple remap** — a fixture with a known tuple on both sides remaps to the new `cid`; an unmatched tuple throws
- **Diff bucketing** — only-on-old, only-on-new, overlap all sort correctly by (syncId, istart)
- **Event-id remap** — with and without `--rekey-events`; missing syncid in snapshot falls through to old id
- **Injection idempotence** — running the script twice on the same inputs produces the same output bytes (deterministic; important because I'll be running it multiple times as I refine)

Fixture data goes in `scripts/__tests__/fixtures/` — small hand-built SQLite files with maybe 3–5 identity rows each, not real user data.

**Manual verification before restore**:

1. Extract the merged `.ab`, count rows in each SQLite file, cross-check against the summary the script printed.
2. `adb install` fresh, `adb restore merged.ab`, open the app — verify the injected events appear on their real calendars (filter pills, "open in calendar" both work).
3. **Only after that** do I clear-data on the daily-driver and restore. This is my real phone; the manual step is non-negotiable.

## Non-Goals

- Not a shipping feature. Lives in `scripts/` under a name that makes clear it's a one-off. If someone else needs it later, they can generalize.
- Doesn't touch `powerSyncEvents.db` or the RN JS bundles in the `.ab` — those are handled by their own subsystems.
- Doesn't attempt to recover events that lack a `syncId` on the old phone. That's the "content-heuristic" Phase 6 in the parent portable-event-identity plan and is out of scope here.
- Doesn't merge `manualAlertsV1` — see Phase 5.
- No signing / re-hash / bmgr integration. The output `.ab` is plain `adb backup` format, restored via `adb restore`.

## Open Questions

- **Only-on-old dismissed events**: 1042 vs 1663 vs 2011 — how much dismissed history is worth keeping? Leaning "keep all rows whose calendar still exists and whose syncId still resolves; drop the rest silently." Confirm before injecting thousands of rows the user will never look at again.
- **What if the new-phone `.ab` is regenerated later** (bug fixes on the new phone, new events created)? The merge is a one-shot output — regenerating would need re-running the script. Acceptable for a one-off; document it in the script header.
