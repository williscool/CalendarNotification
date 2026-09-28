# Feature: Background Data Sync

## Overview

Keep the PowerSync upload running after you leave the Data Sync screen. Today a Full Resync only finishes if `MyReactActivity` stays in the foreground. The plan is to wrap the upload drain in an Android **foreground service** (`dataSync` type) that runs a **React Native Headless JS task**. The shared JS runtime keeps running while that task is active, so the existing `Connector.uploadData()` path does the work unchanged. We don't rewrite any sync logic.

## Background

### How a Full Resync works today

1. `SetupSync.handleSync` calls `psResyncTable` (`src/lib/orm/index.ts`). This is a fast, **local-only** step: `DELETE FROM eventsV9` plus one `executeBatch` insert into `powerSyncEvents.db`. It queues roughly 2×N ops in `ps_crud`.
2. The PowerSync SDK drains `ps_crud` through `Connector.uploadData()`, which makes **one Supabase HTTP request per op**, with per-op retry/backoff built on `setTimeout` (`sleep` in `Connector.ts`).
3. `SetupSync` polls `ps_crud` every second for the progress banner. Leaving for Settings/SyncDebug inside RN is fine, because the upload is not tied to the component.

Step 2 is the slow part, and it is the part that stops when the app leaves the foreground.

### Why it stalls in the background

- **JS timers pause.** When the host activity pauses, RN's `TimingModule` stops firing `setTimeout`/`setInterval`. The only exception is while a Headless JS task is active. The SDK's upload scheduling and the Connector's retry backoff both depend on timers. In-flight `fetch` calls may finish, but the loop never continues.
- **The process is killable.** With no foreground component, Android can kill the process at any point. `ps_crud` is persisted, so no data is lost, but the upload only resumes the next time you open Data Sync (`HomeScreen` is the only caller of `setupPowerSync`).
- Nothing calls `db.disconnect()` when you leave, so the connection object itself isn't the problem. The runtime just gets frozen.

### Design decision

| Option | Verdict |
|--------|---------|
| **Foreground service + Headless JS task** | ✅ Reuses the existing JS connector/queue. The user starts it from a foreground action, so Android 12+ FGS start restrictions are satisfied. An ongoing notification shows progress. |
| WorkManager / `expo-background-task` | ❌ Deferrable and periodic (15 min minimum). Not suited to a user-initiated job that should run now. A possible later add-on for scheduled sync. |
| Reimplement upload in Kotlin | ❌ Duplicates `Connector.ts` (retry, fatal-error handling, failed-op storage). Too much surface for the payoff. |

New Architecture (bridgeless `ReactHost`) is enabled (`newArchEnabled=true`), so we need to confirm that `HeadlessJsTaskService` works with `getDefaultReactHost`. That confirmation is the first thing Phase 1 does.

## Plan

### Phase 1: The foreground service running a no-op headless task (spike)

Prove the mechanism before wiring in sync:

- `SyncForegroundService.kt` (new, `com.github.quarck.calnotify.sync` or alongside `MyReactActivity`) extends `HeadlessJsTaskService`. `onStartCommand` calls `startForeground` (type `dataSync`) with an ongoing notification. `getTaskConfig` returns a `HeadlessJsTaskConfig("CNPlusBackgroundSync", …, timeout, allowedInForeground = true)`.
- Add a silent, low-importance `CHANNEL_ID_SYNC` in `NotificationChannels.kt`.
- Manifest: the `<service android:foregroundServiceType="dataSync">` entry plus the `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` permissions.
- `index.tsx`: `AppRegistry.registerHeadlessTask('CNPlusBackgroundSync', …)`, with a placeholder task that logs and resolves.
- `MyModule.kt`: an `AsyncFunction("startBackgroundSync")` that calls `ContextCompat.startForegroundService`.

Exit criterion: from the Data Sync screen, start the service, press Home, and the headless task's timer-based log line still fires (visible in logcat / SyncDebug).

### Phase 2: A drain-until-empty task

Replace the placeholder with the real task, in a new file (`src/lib/powersync/backgroundSync.ts`):

1. If `db` isn't connected (the process was cold-started by the service), call `setupPowerSync(settings)` using the settings from storage.
2. Resolve when `ps_crud` is empty and `currentStatus.dataFlowStatus.uploading` is false. Drive this from `db.registerListener({ statusChanged })` and a count check on each status change. **No polling loop or sleeps**, per the repo rule.
3. Reject/resolve on the `HeadlessJsTaskConfig` timeout so the service always stops. Anything left in `ps_crud` stays queued for next time.

`SetupSync.handleSync` then calls `startBackgroundSync()` right after `psResyncTable` queues its ops. The in-screen progress banner keeps working as it does today.

### Phase 3: Progress in the notification

Add `MyModule` `Function("updateBackgroundSyncProgress")` so the task can push `"X upserted, Y deleted — Z queued"` (from `getUploadProgress()` and the `ps_crud` count) into the ongoing notification, plus a final "Sync complete" / "Sync paused — N ops pending" state. Optional: a Stop action that ends the task.

## Files Changed Summary

| File | Change |
|------|--------|
| `android/.../SyncForegroundService.kt` | **New.** `HeadlessJsTaskService` + `startForeground(dataSync)` |
| `android/.../notification/NotificationChannels.kt` | Add a silent sync channel |
| `android/app/src/main/AndroidManifest.xml` | Service entry and FGS permissions |
| `modules/my-module/.../MyModule.kt`, `modules/my-module/index.ts` | `startBackgroundSync`, then `updateBackgroundSyncProgress` |
| `index.tsx` | `registerHeadlessTask` |
| `src/lib/powersync/backgroundSync.ts` | **New.** The drain-until-empty task |
| `src/lib/features/SetupSync.tsx` | Start the service after queueing a resync |

## Testing

- **Jest (`backgroundSync.test.ts`)**, driving a fake `db`/status listener:
  - Resolves when the queue drains.
  - Stays pending while `uploading` is true, even if the count momentarily hits 0.
  - Connects when disconnected and skips connecting when already connected.
  - Resolves immediately on an empty queue.
  - Honors the timeout.
- **Jest (`SetupSync.test.ts`)**: `handleSync` calls `startBackgroundSync` after `psResyncTable`, and doesn't call it if the resync throws.
- **Robolectric**:
  - `getTaskConfig` returns the right task key, timeout, and `allowedInForeground`.
  - The notification lands on the sync channel and is ongoing.
  - Note: `super.onStartCommand` boots RN, which `GlobalState` skips under Robolectric. Keep the testable pieces as plain functions rather than using `mockkConstructor` (see `constructor-mocking-android.md`).
- **Manual on device (required; tests can't observe backgrounding)**: run a Full Resync with ~150 events, press Home right away, and confirm the Supabase row count reaches the local count and the notification clears. Repeat with back-press (activity destroyed) and with the screen off.

## Open Questions

- **Does `HeadlessJsTaskService` work in bridgeless mode on RN 0.81?** Phase 1 exists to answer this. If it doesn't, the fallback is a plain FGS that only keeps the process alive, plus a JS-side `HeadlessJsTaskContext` workaround. Decide after the spike.
- **Does a `db.watch` on `ps_crud` emit changes?** It's an internal table populated by triggers. If it doesn't, the `statusChanged`-plus-count approach in Phase 2 is the plan of record.
- **Timeout value.** A 150-event resync with ~300 sequential requests plus backoff probably takes a few minutes. Start at 15 min. Android 15 caps `dataSync` FGS at 6 h/day, which is far above that.
- **Should the service also start automatically whenever `ps_crud > 0` on app exit** (covering incremental ops, not just Full Resync)? Leaning no for now: Full Resync is the documented workflow ([data_sync_improvements.md](./data_sync_improvements.md)).

## References

- [data_sync_improvements.md](./data_sync_improvements.md): Full Resync and progress banner (#260)
- [sync_database_mismatch.md](../dev_completed/sync_database_mismatch.md): Room vs Legacy DB name
- [DATA_SYNC_README.md](../DATA_SYNC_README.md): Supabase/PowerSync setup
- RN Headless JS: https://reactnative.dev/docs/headless-js-android
- Android FGS types: https://developer.android.com/develop/background-work/services/fg-service-types#data-sync
