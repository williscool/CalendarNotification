<!-- 05673065-27e0-4a04-a257-8525be445a15 2c6d8451-d9a7-4305-a51d-3200c215c5f0 -->
# Test Suite Completion and Feature Deprecation Plan

## Part 1: Deprecation Tracking

### 1.1 Create Deprecation Documentation

Create [`docs/dev_todo/deprecated_features.md`](docs/dev_todo/deprecated_features.md) to track:

- Quiet Hours feature (replaced by Android DND)
- Calendar Editor feature (replaced by native calendar app rescheduling)
- List of files to remove and dependencies to clean up
- Removal prerequisites

### 1.2 Add @Deprecated Annotations

**Quiet Hours Package** ([`quiethours/`](android/app/src/main/java/com/github/quarck/calnotify/quiethours/)):

- `QuietHoursManager.kt`
- `QuietHoursManagerInterface.kt`

**Calendar Editor Package** ([`calendareditor/`](android/app/src/main/java/com/github/quarck/calnotify/calendareditor/)):

- `CalendarChangeManager.kt`, `CalendarChangeManagerInterface.kt`
- `CalendarChangePersistentState.kt`, `CalendarChangeRequest.kt`
- `CalendarChangeRequestMonitor.kt`, `CalendarChangeRequestMonitorInterface.kt`
- `storage/` subdirectory files

**UI**: [`prefs/fragments/QuietHoursSettingsFragment.kt`](android/app/src/main/java/com/github/quarck/calnotify/prefs/fragments/QuietHoursSettingsFragment.kt)

---

## Part 2: Core Feature Tests

### 2.1 Snooze Tests

Core feature with zero current coverage. Uses existing `BaseCalendarTestFixture`.

**Robolectric:** [`test/.../app/SnoozeRobolectricTest.kt`](android/app/src/test/java/com/github/quarck/calnotify/app/SnoozeRobolectricTest.kt)
**Instrumented:** [`androidTest/.../app/SnoozeTest.kt`](android/app/src/androidTest/java/com/github/quarck/calnotify/app/SnoozeTest.kt)

Test cases (shared):

- `snoozeEvent()` - single event, verify snoozedUntil updated in storage
- `snoozeEvents()` - bulk snooze with filter lambda
- `snoozeAllEvents()` - with search query filtering
- Edge case: past snooze time triggers FAILBACK_SHORT_SNOOZE

### 2.2 Broadcast Receiver Tests

System entry points with zero current coverage.

**Robolectric:** [`test/.../broadcastreceivers/BroadcastReceiverRobolectricTest.kt`](android/app/src/test/java/com/github/quarck/calnotify/broadcastreceivers/BroadcastReceiverRobolectricTest.kt)
**Instrumented:** [`androidTest/.../broadcastreceivers/BroadcastReceiverTest.kt`](android/app/src/androidTest/java/com/github/quarck/calnotify/broadcastreceivers/BroadcastReceiverTest.kt)

Test cases (shared):

- `BootCompleteBroadcastReceiver.onReceive()` - verify calls `ApplicationController.onBootComplete()`
- `SnoozeAlarmBroadcastReceiver.onReceive()` - verify calls `ApplicationController.onEventAlarm()`

Note: `EventReminderBroadcastReceiver` already covered by calendar monitoring tests.

---

## Skipped (Already Exercised)

No dedicated tests needed - covered through existing fixtures:

- **EventsStorage** - used by 12 test files
- **MonitorStorage** - used by 8 test files
- **CalendarReloadManager** - used in fixture setup

---

## Implementation Order

1. Deprecation docs + annotations
2. Snooze tests (core feature)
3. Broadcast receiver tests (triggers snooze/boot flows)

---

## Files Summary

**New files (3):**

- `docs/dev_todo/deprecated_features.md`
- `test/.../app/SnoozeRobolectricTest.kt`
- `test/.../broadcastreceivers/BroadcastReceiverRobolectricTest.kt`

**Files to modify (~10, add @Deprecated):**

- 2 in `quiethours/`
- 7 in `calendareditor/`
- 1 in `prefs/fragments/`

### To-dos

- [ ] Create docs/dev_todo/deprecated_features.md tracking Quiet Hours and Calendar Editor removal
- [ ] Add @Deprecated annotations to quiethours/ and calendareditor/ packages
- [ ] Create SnoozeTest.kt (instrumented) and SnoozeRobolectricTest.kt for ApplicationController snooze methods
- [ ] Create EventsStorageTest.kt and EventsStorageRobolectricTest.kt for storage layer
- [ ] Create BroadcastReceiverTest.kt for Boot, Snooze, and EventReminder receivers
- [ ] Create CalendarReloadManagerTest.kt for rescan flow coverage
- [ ] Create SnoozeTestFixture.kt extending BaseCalendarTestFixture