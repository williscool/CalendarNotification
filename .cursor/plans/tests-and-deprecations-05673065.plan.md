---
name: Test Suite Completion and Feature Deprecation Plan
overview: ""
todos:
  - id: 79b81bf4-3f65-4e9f-bc0f-fb6ddce985c7
    content: Create docs/dev_todo/deprecated_features.md for Quiet Hours and Calendar Editor
    status: completed
  - id: c4df911b-675b-4368-a071-6d3d217eef33
    content: Add @Deprecated annotations to quiethours/ and calendareditor/ packages
    status: completed
  - id: 1e277f70-4abe-4c61-92c8-33e53295a31d
    content: Create SettingsTest for snoozePresets and reminder interval logic
    status: completed
  - id: 6c9bf775-f418-4e52-b99d-f06ee604f3a8
    content: Create SnoozeTest for snoozeEvent, snoozeEvents, snoozeAllEvents
    status: completed
  - id: 8598291e-6765-48e1-bc2e-466899a1f890
    content: Create CalendarReloadManagerTest for calendar change handling
    status: completed
  - id: 65d33916-bf6f-4294-9369-fa2594421973
    content: Create BroadcastReceiverTest for Boot, Snooze, and Reminder receivers
    status: completed
---

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

### 2.1 Settings Logic Tests (Foundation)

Pure logic in Settings.kt that snooze and reminders depend on.

**Robolectric:** [`test/.../SettingsRobolectricTest.kt`](android/app/src/test/java/com/github/quarck/calnotify/SettingsRobolectricTest.kt)
**Instrumented:** [`androidTest/.../SettingsTest.kt`](android/app/src/androidTest/java/com/github/quarck/calnotify/SettingsTest.kt)

Test cases:

- `snoozePresets` - parsing from raw string, default fallback
- `currentAndNextReminderIntervalsMillis()` - interval pattern, index wrapping
- `reminderIntervalMillisAt()` - single interval lookup

### 2.2 TagsManager Tests

Event tag parsing logic - affects muted/task behavior.

**Robolectric:** [`test/.../app/TagsManagerRobolectricTest.kt`](android/app/src/test/java/com/github/quarck/calnotify/app/TagsManagerRobolectricTest.kt)
**Instrumented:** [`androidTest/.../app/TagsManagerTest.kt`](android/app/src/androidTest/java/com/github/quarck/calnotify/app/TagsManagerTest.kt)

Test cases:

- `parseEventTags()` - detect #muted, #task tags in title/description
- Tag at end of string, middle of string, case insensitivity

### 2.3 Snooze Tests

Core snooze feature - zero current coverage.

**Robolectric:** [`test/.../app/SnoozeRobolectricTest.kt`](android/app/src/test/java/com/github/quarck/calnotify/app/SnoozeRobolectricTest.kt)
**Instrumented:** [`androidTest/.../app/SnoozeTest.kt`](android/app/src/androidTest/java/com/github/quarck/calnotify/app/SnoozeTest.kt)

Test cases:

- `snoozeEvent()` - single event, verify snoozedUntil updated
- `snoozeEvents()` - bulk snooze with filter lambda
- `snoozeAllEvents()` - with search query filtering
- Edge case: past snooze time triggers FAILBACK_SHORT_SNOOZE

### 2.4 CalendarReloadManager Tests

Core calendar change handling - zero current coverage.

**Robolectric:** [`test/.../app/CalendarReloadManagerRobolectricTest.kt`](android/app/src/test/java/com/github/quarck/calnotify/app/CalendarReloadManagerRobolectricTest.kt)
**Instrumented:** [`androidTest/.../app/CalendarReloadManagerTest.kt`](android/app/src/androidTest/java/com/github/quarck/calnotify/app/CalendarReloadManagerTest.kt)

Test cases:

- `reloadCalendar()` - event moved, details updated, no change
- `ReloadCalendarResultCode` handling

### 2.5 Broadcast Receiver Tests

System entry points - zero current coverage.

**Robolectric:** [`test/.../broadcastreceivers/BroadcastReceiverRobolectricTest.kt`](android/app/src/test/java/com/github/quarck/calnotify/broadcastreceivers/BroadcastReceiverRobolectricTest.kt)
**Instrumented:** [`androidTest/.../broadcastreceivers/BroadcastReceiverTest.kt`](android/app/src/androidTest/java/com/github/quarck/calnotify/broadcastreceivers/BroadcastReceiverTest.kt)

Test cases:

- `BootCompleteBroadcastReceiver.onReceive()` - triggers onBootComplete
- `SnoozeAlarmBroadcastReceiver.onReceive()` - triggers onEventAlarm
- `ReminderAlarmBroadcastReceiver.onReceive()` - reminder firing, interval logic

---

## Skipped (Already Exercised or Low Value)

- **EventsStorage** - used by 12 test files
- **MonitorStorage** - used by 8 test files
- **AlarmScheduler** - Android API dependent, effects tested via flows
- **Thin wrapper receivers** (CalendarChanged, AppUpdated, TimeSet) - underlying methods tested
- **Notification services** - thin wrappers calling tested methods
- **UndoManager** - simple UI state holder

---

## Implementation Order

1. Deprecation docs + annotations
2. Settings logic tests (foundation)
3. TagsManager tests (simple, affects event behavior)
4. Snooze tests (core feature)
5. CalendarReloadManager tests
6. Broadcast receiver tests (integration layer)

---

## Files Summary

**New files (12):**

- `docs/dev_todo/deprecated_features.md`
- `test/.../SettingsRobolectricTest.kt`
- `androidTest/.../SettingsTest.kt`
- `test/.../app/TagsManagerRobolectricTest.kt`
- `androidTest/.../app/TagsManagerTest.kt`
- `test/.../app/SnoozeRobolectricTest.kt`
- `androidTest/.../app/SnoozeTest.kt`
- `test/.../app/CalendarReloadManagerRobolectricTest.kt`
- `androidTest/.../app/CalendarReloadManagerTest.kt`
- `test/.../broadcastreceivers/BroadcastReceiverRobolectricTest.kt`
- `androidTest/.../broadcastreceivers/BroadcastReceiverTest.kt`

**Files to modify (~10, add @Deprecated):**

- 2 in `quiethours/`
- 7 in `calendareditor/`
- 1 in `prefs/fragments/`