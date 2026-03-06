# Data Sync Improvements

**GitHub Issue:** [#260](https://github.com/williscool/CalendarNotification/issues/260)

## Overview

Formalize the "delete all + re-upload" workflow as the primary sync action and add a progress indicator so you know when sync is truly complete. This is critical because a downstream app consumes the synced events (and their snooze targets) to reschedule them in bulk — incorrect or incomplete data means events get rescheduled wrong.

## Background

### How sync works today

1. User opens "Data Sync" from the Android menu → launches React Native `SetupSync` screen
2. Taps "Sync Events Local To PowerSync Now" → `psInsertDbTable` copies all rows from the local SQLite `eventsV9` into the PowerSync local DB via `INSERT OR REPLACE`
3. PowerSync SDK detects the pending CRUD changes and uploads them to Supabase Postgres via `Connector.uploadData()` (with retries and error handling)
4. "Clear Remote PowerSync Events" is a separate button hidden in the Danger Zone — runs `DELETE FROM eventsV9` on the PowerSync DB, which then propagates deletes to Supabase

### The problems

**No sync completion indicator.** PowerSync does a lot of background work before data is fully reflected in Supabase Postgres. The only way to know sync is done is to watch the raw `currentStatus` JSON in the debug UI and wait for operation counts to stop changing. There is no dedicated progress display.

**Incremental updates don't work reliably.** When events get updated (particularly around dismissed events lingering), the `INSERT OR REPLACE` approach produces unexpected results. The practical workaround is to clear all remote events first, then re-upload — but this requires manually opening the Danger Zone, clearing, then syncing. This is what you end up doing every time.

### History of related issues

| Issue | Status | Summary |
|-------|--------|---------|
| [#47](https://github.com/williscool/CalendarNotification/issues/47) | Closed | Original feature request — sync local DB to remote Postgres for analytics |
| [#86](https://github.com/williscool/CalendarNotification/issues/86) | Closed | Delete should use remote truncate; turned out the real problem was no error handling in `uploadData`; closed in favor of #93 |
| [#93](https://github.com/williscool/CalendarNotification/issues/93) | Open | Need to verify sync completion before trusting the data; credential UX (done) |
| [#245](https://github.com/williscool/CalendarNotification/issues/245) | Closed | Event count mismatch (138 local vs 135 remote); fixed in #247 |
| [#246](https://github.com/williscool/CalendarNotification/issues/246) | Closed | Snoozed events not syncing due to `displayStatus` filter bug |

The `uploadData` error handling and retry logic was added to `Connector.ts` after #86. The `displayStatus` filter bug (#246) was fixed by removing the `WHERE dsts != 0` filter (see [fix_sync_display_status_filter.md](./fix_sync_display_status_filter.md)). Despite these fixes, the fundamental issue remains: PowerSync takes real time to process and there's no good feedback loop.

### Key technical detail for progress tracking

PowerSync exposes the pending upload queue size via:

```sql
SELECT COUNT(*) FROM ps_crud;
```

Combined with `currentStatus.dataFlowStatus.uploading` (boolean), this gives us everything needed for a progress indicator.

## Plan

### Phase 1: Formalize "Delete + Re-upload" as the primary sync action

Combine the current two-step manual workflow (Danger Zone clear → sync) into the primary sync button. The button becomes a full resync: clear remote, then re-upload all active events.

**Files involved:**
- `src/lib/orm/index.ts` — new combined resync function
- `src/lib/features/SetupSync.tsx` — update `handleSync` to call the combined function, update button label

The individual "Clear Remote PowerSync Events" button stays in the Danger Zone for manual use.

### Phase 2: Sync progress indicator

Replace the raw `currentStatus` JSON dump with a human-readable progress display:

1. When sync starts, button goes disabled/loading
2. Poll `ps_crud` count to show "X operations remaining"
3. Use `dataFlowStatus.uploading` to distinguish "queued" vs "actively uploading"
4. When count hits 0 and uploading is false → show "Sync complete!" with timestamp
5. Re-enable button

**Files involved:**
- `src/lib/features/SetupSync.tsx` — progress state, polling, status banner

### Phase 3 (future): Investigate update weirdness

The likely culprit is dismissed events that linger in `eventsV9` and don't get cleaned up, causing stale data in the remote DB after incremental sync. The delete+reupload approach from Phase 1 sidesteps this entirely, but if we ever want true incremental sync it will need investigation.

## Related Files

| File | Role |
|------|------|
| `src/lib/features/SetupSync.tsx` | Main sync UI — sync button, danger zone, status display |
| `src/lib/orm/index.ts` | `psInsertDbTable`, `psClearTable` — the sync and clear primitives |
| `src/lib/powersync/Connector.ts` | `uploadData` with retry logic, `emitSyncLog` |
| `src/screens/sync-debug.tsx` | Debug screen with raw status, logs, failed ops |
| `src/lib/powersync/index.tsx` | PowerSync DB setup, `setupPowerSync()` |

## References

- [fix_sync_display_status_filter.md](./fix_sync_display_status_filter.md) — the `displayStatus` filter bug and fix
- [sync_database_mismatch.md](../dev_completed/sync_database_mismatch.md) — Room vs Legacy DB name mismatch fix
- [PowerSync SyncStatus docs](https://powersync-ja.github.io/powersync-js/react-native-sdk/classes/SyncStatus) — `dataFlowStatus`, `uploading`, `hasSynced`
