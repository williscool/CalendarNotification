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
| WorkManager / `expo-background-task` | ❌ Deferrable and periodic (15 min minimum). Not suited to a user-initiated job that should run now. |
| Reimplement upload in Kotlin | ❌ Duplicates `Connector.ts` (retry, fatal-error handling, failed-op storage). Too much surface for the payoff. |

### What RN 0.81.5's `HeadlessJsTaskService` gives us (read from source)

- **It supports the New Architecture.** With `enableBridgelessArchitecture` it resolves the context through `ReactApplication.reactHost` and starts the host itself on a cold process. `GlobalState` already exposes `reactHost`, so this is not an unknown.
- **The timeout is native-only.** `HeadlessJsTaskContext` schedules a runnable that calls `finishTask`; the JS promise is never told. Anything that must happen on timeout has to happen in Kotlin.
- **Every `onStartCommand` starts another task.** A second start while one is running gives two drain tasks.
- **It returns `START_REDELIVER_INTENT`.** After a process kill the system restarts the service from the background and redelivers the intent, so an interrupted sync resumes without you. Android 12+ normally blocks a background `startForeground`, but apps exempt from battery optimizations are excepted, and this app already asks for that exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, prompted in `MainActivityBase`). We keep the default. For anyone who declined or revoked the exemption, the service catches `ForegroundServiceStartNotAllowedException` (API 31+) specifically and stops; the queue is persisted and resumes on the next sync they start.
- It already holds a partial wake lock for the life of the service.

## Non-goals

- **Starting the service automatically when `ps_crud > 0` on app exit.** That crosses from a user-initiated FGS to a background-initiated one, which has stricter Android 12+ start rules and would need WorkManager scheduling. It is a different design and gets its own plan if it ever comes up.
- Scheduled/periodic sync.

## Plan

### Phase 1: The foreground service running a no-op headless task (smoke test)

Stand the mechanism up end to end before wiring in sync:

- `SyncForegroundService.kt` (new) extends `HeadlessJsTaskService`. `onStartCommand` calls `startForeground` (type `dataSync`) with an ongoing notification, catching `ForegroundServiceStartNotAllowedException` and stopping if the start is refused. The return value stays the base class's `START_REDELIVER_INTENT`. `getTaskConfig` returns a `HeadlessJsTaskConfig("CNPlusBackgroundSync", …, timeout, allowedInForeground = true)`.
- The notification's tap target is a `PendingIntent` to `MyReactActivity`, so tapping it opens Data Sync.
- Add a silent, low-importance `CHANNEL_ID_SYNC` in `NotificationChannels.kt`.
- Manifest: the `<service android:foregroundServiceType="dataSync">` entry plus the `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` permissions.
- `index.tsx`: `AppRegistry.registerHeadlessTask('CNPlusBackgroundSync', …)`, with a placeholder task that logs on a timer and resolves.
- `MyModule.kt`: an `AsyncFunction("startBackgroundSync")` that calls `ContextCompat.startForegroundService`. It is a **no-op when the service is already running**, so a second call never produces a second drain task.

Exit criterion: start the service from the Data Sync screen and confirm the placeholder's timer-based log line still fires (logcat / SyncDebug) in both cases: after pressing **Home**, and after **back-press** (activity destroyed). Also confirm what the native timeout does to the service and its notification.

### Phase 2: A drain-until-empty task

Replace the placeholder with the real task, in a new file (`src/lib/powersync/backgroundSync.ts`):

1. If `db` isn't connected (the process was cold-started by the service), load settings and call `setupPowerSync(settings)`. This needs a loader that works outside React: extract `loadStoredSettings()` from `SettingsProvider.loadSettings` in `SettingsContext.tsx` and share it between the provider and the task. With no stored (or unconfigured) settings the task ends as failed.
2. Resolve when `ps_crud` is empty and `currentStatus.dataFlowStatus.uploading` is false. Drive this from `db.registerListener({ statusChanged })`, re-checking the count on each status change. **No polling loop or sleeps**, per the repo rule. If a `db.watch` on `ps_crud` turns out to emit changes it can replace the listener, but the fallback is always "`statusChanged` → re-check count", never a periodic poll.
3. Before resolving, the task reports its outcome to the service through a `MyModule` function: complete, or failed with a message (could not connect, or ops were discarded as fatal during the run). If the service sees the task finish with no outcome reported, the native timeout fired: that is the paused state.

`SetupSync.handleSync` calls `startBackgroundSync(total)` right after `psResyncTable` queues its ops, where `total` is the `ps_crud` count at that moment. The in-screen progress banner keeps working as it does today.

### Phase 3: Terminal states, so you know what happened while you were away

The service owns the terminal state, because only native code sees the timeout. On task finish it:

- **Cancels the ongoing notification unconditionally**, whichever state follows.
- **Posts a separate, non-ongoing notification** for one of three states: `Sync complete`, `Sync failed` (tap opens Data Sync), or `Sync paused — N ops pending` (timeout with work left; the ops stay queued, so it is not a failure). The complete notification is suppressed when `MyReactActivity` is resumed, since the screen already shows it.
- **Persists three fields** in a dedicated SharedPreferences file: `sync_last_completed_at` (from `CNPlusClockInterface`), `sync_last_completed_ok`, `sync_last_error`.

`SetupSync` reads those fields on mount (a `MyModule` getter) and shows "Sync complete at HH:MM" or the error, so a finished sync is distinguishable from "never synced" after the process has been restarted. If the process is killed mid-drain no terminal state is written at that point. With the battery-optimization exemption the service is redelivered and finishes the drain; without it, the live `ps_crud` count on the screen still shows the ops pending.

### Phase 4: Progress in the ongoing notification

The service keeps `total` from `startBackgroundSync(total)`; the task pushes the current `ps_crud` count on each status change through a `MyModule` function. The notification shows a progress bar at `total - queued` of `total`, and is indeterminate when the total is unknown (cold start). The numbers do **not** come from `getUploadProgress()`: those counters are module globals and reset to zero if the service cold-starts the process. Optional: a Stop action that ends the task.

## Files Changed Summary

| File | Change |
|------|--------|
| `android/.../SyncForegroundService.kt` | **New.** `HeadlessJsTaskService` + `startForeground(dataSync)`, terminal-state handling |
| `android/.../notification/NotificationChannels.kt` | Add a silent sync channel |
| `android/app/src/main/AndroidManifest.xml` | Service entry and FGS permissions |
| `modules/my-module/.../MyModule.kt`, `modules/my-module/index.ts` | `startBackgroundSync(total)`, outcome report, progress update, last-result getter |
| `index.tsx` | `registerHeadlessTask` |
| `src/lib/powersync/backgroundSync.ts` | **New.** The drain-until-empty task |
| `src/lib/hooks/SettingsContext.tsx` | Extract `loadStoredSettings()` for use outside React |
| `src/lib/features/SetupSync.tsx` | Start the service after queueing a resync; show the persisted last result |

## Testing

- **Jest (`backgroundSync.test.ts`)**, driving a fake `db`/status listener:
  - Resolves when the queue drains, and reports complete.
  - Stays pending while `uploading` is true, even if the count momentarily hits 0.
  - Connects when disconnected and skips connecting when already connected.
  - Cold start with no stored settings reports failed and resolves.
  - Resolves immediately on an empty queue.
- **Jest (`SetupSync.test.ts` / `SetupSync.ui.test.tsx`)**: `handleSync` calls `startBackgroundSync` with the queued count after `psResyncTable`, and doesn't call it if the resync throws. On mount the screen shows the persisted last result.
- **Robolectric**, with the testable pieces as plain functions rather than `mockkConstructor` (see `constructor-mocking-android.md`; `super.onStartCommand` boots RN, which `GlobalState` skips under Robolectric):
  - `getTaskConfig` returns the right task key, timeout, and `allowedInForeground`.
  - Notification content: on the sync channel, ongoing, with the tap intent; indeterminate when the total is unknown; determinate at e.g. 3 of 10; and the complete / failed / paused terminal states.
  - `startBackgroundSync` is a no-op when the service is already running.
  - Terminal state writes the three fields with the injected clock's time; a finish with no reported outcome is recorded as paused.
  - A refused foreground start (`ForegroundServiceStartNotAllowedException`) stops the service without crashing.
- **Manual on device (required; tests can't observe backgrounding)**:
  - Run a Full Resync with ~150 events, press Home right away, and confirm the Supabase row count reaches the local count and the ongoing notification is replaced by "Sync complete". Repeat with back-press and with the screen off.
  - Kill the process mid-sync (`adb shell am kill`, battery-optimization exemption granted) and confirm the service restarts and the drain finishes. Repeat with the exemption revoked and confirm there is no crash and the ops stay queued.
  - After the service has finished and the process has been killed, reopen Data Sync and confirm it shows the final "complete at HH:MM" or error state.

## Open Questions

- **Does a `db.watch` on `ps_crud` emit changes?** It's an internal table populated by triggers. Either way the plan of record is `statusChanged` → re-check count.
- **Timeout value.** A 150-event resync with ~300 sequential requests plus backoff probably takes a few minutes. Start at 15 min. Android 15 caps `dataSync` FGS at 6 h/day, which is far above that.

## References

- [data_sync_improvements.md](./data_sync_improvements.md): Full Resync and progress banner (#260)
- [sync_database_mismatch.md](../dev_completed/sync_database_mismatch.md): Room vs Legacy DB name
- [DATA_SYNC_README.md](../DATA_SYNC_README.md): Supabase/PowerSync setup
- RN Headless JS: https://reactnative.dev/docs/headless-js-android
- Android FGS types: https://developer.android.com/develop/background-work/services/fg-service-types#data-sync
