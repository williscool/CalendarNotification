# Background Data Sync

How a Full Resync keeps uploading after you leave the Data Sync screen, and how the app reports what happened.

The design history (what was planned, what changed during the build, and why) lives in [`docs/dev_completed/background_data_sync.md`](../dev_completed/background_data_sync.md). This doc explains *what is there and how the pieces fit* right now. Supabase and PowerSync setup is in [DATA_SYNC_README.md](../DATA_SYNC_README.md).

## The problem in one paragraph

A Full Resync queues its changes locally in a moment (`psResyncTable` writes into PowerSync's `ps_crud` queue), but uploading them to Supabase takes one HTTP request per change. That upload runs in React Native JS, and React Native stops firing JS timers once its screen is in the background. The PowerSync upload loop and the Connector's retry backoff both depend on timers, so leaving the screen stalled the upload until you came back. The fix wraps the upload in an Android foreground service that runs a React Native headless JS task: while a headless task is active, React Native keeps timers running, so the existing upload code carries on unchanged.

## What lives where

**Native (`android/app/.../calnotify/`)**

| File | Role |
|------|------|
| `sync/SyncForegroundService.kt` | `dataSync` foreground service extending React Native's `HeadlessJsTaskService`. Shows the ongoing notification, runs the JS task, and owns the terminal state (result, notification) because only native code sees the timeout. |
| `sync/BackgroundSyncState.kt` | Reads and writes the shared preferences file: clears the last run's reports, watches progress, records the result. |
| `react/HeadlessTaskSupportModule.kt` | Tells native when a headless task finishes. React Native 0.81 only registers its own version for the old architecture, so without this the task never ends before its timeout. Registered in `ThemePackage`. |
| `ui/MyReactActivity.kt` | `isResumed` flag, so the service can skip "Sync complete" while the sync screens are open. |
| `ui/TestActivity.kt` | Dev page "BACKGROUND SYNC TEST" button: runs the service against a fake queue. |
| `notification/NotificationChannels.kt` | The two sync channels. |

**Bridge (`modules/my-module/`)**: `MyModule` functions `startBackgroundSync`, `reportBackgroundSyncOutcome`, `reportBackgroundSyncProgress`, `getLastBackgroundSyncResult` and `areNotificationsEnabled`, plus the preference key constants that `BackgroundSyncState` uses directly.

**JS (`src/lib/`)**

| File | Role |
|------|------|
| `powersync/backgroundSync.ts` | The headless task: connect if needed, wait for the queue to drain while reporting progress, report the outcome. Registered in `index.tsx`. |
| `powersync/Connector.ts` | `inFlightUploads`: counts ops uploaded from the transaction in flight (a PowerSync `BaseObserver`). |
| `hooks/settingsStorage.ts` | `loadStoredSettings()`, usable outside React when the service cold-starts the app. |
| `features/SetupSync.tsx` | Full Resync starts the service; the screen shows the last result and a note when notifications are off. |

## What runs each sync

```
Full Resync (SetupSync.handleSync)              Dev page "BACKGROUND SYNC TEST"
    │                                                │
    ├─ psResyncTable()      queues deletes + inserts  │  (intent extra: fake queue)
    └─ startBackgroundSync() ───────┐                │
                                    ▼                ▼
SyncForegroundService.onStartCommand         (dataSync foreground service, wake lock)
    │
    ├─ clear the last run's reports            (before the notification is first built)
    ├─ startForeground("Syncing events…")      (a second start re-enters the foreground
    │                                            but does not start a second task)
    └─► headless JS task "CNPlusBackgroundSync"     (JS timers keep running)
           │
           └─► runBackgroundSync(environment)
                  │
                  ├─ connect PowerSync if the service cold-started the app
                  │      (loadStoredSettings; not configured → outcome notConfigured)
                  │
                  ├─► drainReportingProgress
                  │      ├─ total = queue count when the task starts
                  │      ├─ on each PowerSync status change → re-read the queue count ─┐
                  │      ├─ on each op uploaded in flight (inFlightUploads) ───────────┤
                  │      │                                                              ▼
                  │      │     reportBackgroundSyncProgress(done, total, queued) → prefs
                  │      │          → service re-posts "X of Y uploaded"
                  │      └─ waitForDrain: resolves when nothing is queued or uploading
                  │
                  └─ reportBackgroundSyncOutcome(complete | failed + reason) → prefs

      (meanwhile PowerSync's upload loop runs Connector.uploadData:
       one Supabase request per op, two big transactions per Full Resync)

Task resolves, or the native timeout fires (15 min)
    └─► SyncForegroundService.onHeadlessJsTaskFinish
           ├─ stop watching progress
           ├─ reported ok → COMPLETE · reported error → FAILED · nothing → PAUSED
           ├─ save the last result                    ← Data Sync screen reads it on open
           ├─ remove the ongoing notification
           └─ COMPLETE      → "Sync complete" on the sync channel
                              (skipped while the sync screens are open)
              FAILED/PAUSED → "Sync failed" / "Sync paused" on the sync problems channel
```

The JS outcome is a `BackgroundSyncOutcome` (`complete`, `notConfigured`, `changesRejected`, `error`), turned into text by `describeFailure`. `changesRejected` means the server rejected an op during the run: rejected ops are dropped from the queue, so it drains even though they never uploaded. The task spots this by comparing the newest failed-operation id before and after.

## How JS and native talk

Both sides share one SharedPreferences file, `background_sync_state`. JS writes through `MyModule`, and native reads it in the same process. The outcome write is awaited before the task finishes, so it is always in place when the service looks. An intent to the service would race the task finishing, and could restart a service that had already stopped.

| Key | Written by | Read by |
|-----|-----------|---------|
| `reported_ok`, `reported_error` | JS task, just before it finishes | Service, when the task ends. Missing means the timeout ended it. |
| `progress_done`, `progress_total`, `queued` | JS task, as uploads go through | Service (watches for changes, re-posts the progress notification). `queued` also feeds the paused text. |
| `sync_last_completed_at`, `sync_last_completed_ok`, `sync_last_error` | Service, when the task ends | Data Sync screen, on open (`getLastBackgroundSyncResult`) |

The service clears the reported and progress keys at the start of each run. The file is not in `backup_rules.xml`, so a restore never brings back a stale result.

## Notifications

| | Notification ID | Channel | Group |
|--|-----------------|---------|-------|
| Ongoing "Syncing events… X of Y uploaded" | `NOTIFICATION_ID_SYNC` | `data_sync` (low importance, silent, Silent group) | `BACKGROUND_SYNC` |
| "Sync complete" | `NOTIFICATION_ID_SYNC_RESULT` | `data_sync` | `BACKGROUND_SYNC` |
| "Sync failed" / "Sync paused" | `NOTIFICATION_ID_SYNC_RESULT` | `data_sync_problems` (default importance, Main group) | `BACKGROUND_SYNC` |

- **Two IDs:** Android removes the foreground service's notification when the service stops, so the result needs its own ID to outlive it. The next sync cancels an old result.
- **Two channels:** since Android 8, sound and pop-up are set per channel. Progress must stay silent so it doesn't buzz on every update; failures should make a sound.
- **Own group:** without one, Android bundles an app's ungrouped notifications, which hid the progress bar inside the collapsed "N more events" bundle.
- Every notification taps through to Data Sync.

## Progress

PowerSync removes rows from `ps_crud` only when a whole transaction completes, and a Full Resync is two large transactions (one `DELETE`, one batch insert). A bar driven by the queue count alone would sit at zero, jump to half, and jump to done. So the task reports `done = (total - queued) + inFlightUploads.count`, clamped to `0..total`: what has left the queue plus what the current transaction has uploaded so far. The in-flight count resets when a transaction completes or fails and will be retried. On a real sync of 160 events this moved smoothly through the 30-second insert transaction.

`total` is the queue count when the task starts, so nothing depends on counters from an earlier process: a cold start after a process kill picks up from what is left.

## Things that bite

- **Task completion under the New Architecture.** React Native 0.81 only registers `HeadlessJsTaskSupport` for the old architecture, and the class is internal. `HeadlessTaskSupportModule` provides the same JS-facing module so a finished task actually ends.
- **A rejected task never finishes.** `AppRegistry.startHeadlessTask` only reports a finish when the promise resolves. `runBackgroundSync` catches everything and always resolves.
- **The timeout is native-only.** JS is never told; the service records a finish with nothing reported as paused.
- **Restart after a process kill.** The service keeps React Native's `START_REDELIVER_INTENT`, so Android restarts it and the drain resumes. That background restart is allowed on Android 12+ because the app is exempt from battery optimizations. Without the exemption the service catches `ForegroundServiceStartNotAllowedException` and stops; the queue stays persisted.
- **Permissions.** `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` are granted at install. If notifications are denied the sync still runs; only its notifications are hidden, and the Data Sync screen says so.

## Testing

- **Jest:** `powersync/backgroundSync.test.ts` (drain, connect, outcomes, progress, the fake queue), `powersync/Connector.test.ts` (the in-flight count), `features/__tests__/SetupSync.ui.test.tsx` (Full Resync starts the service, last-result banners).
- **Robolectric:** `sync/SyncForegroundServiceRobolectricTest` (task config, notifications, terminal states, progress) and `notification/NotificationChannelsRobolectricTest`.
- **Emulator, no backend:** the Dev page button drains a fake 24-op queue over about two minutes. Press Home and watch the notification keep going. `adb shell am kill com.github.quarck.calnotify` kills the process mid-sync; `adb shell dumpsys deviceidle force-idle` forces Doze.
- **Emulator, real sync:** point Sync Settings at a development PowerSync instance (Full Resync clears the remote table first). The Dev page "ADD RANDOM EVENT" button adds events so the sync lasts long enough to watch.

## Related documentation

- [Notification Architecture](notification_architecture.md): the app's other channels and notification IDs
- [Data Sync Setup](../DATA_SYNC_README.md): Supabase and PowerSync configuration
- [Background data sync plan](../dev_completed/background_data_sync.md): design history
