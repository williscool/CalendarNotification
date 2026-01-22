# Deprecated Features

Features scheduled for removal. These duplicate functionality now built into modern Android.

## Status Legend
- **DEPRECATED** - Do not add tests, scheduled for removal
- **REMOVAL PENDING** - Test suite complete, ready to remove
- **REMOVED** - Feature has been removed

---

## Quiet Hours

**Status:** DEPRECATED

**Reason:** Android's built-in Do Not Disturb (DND) mode provides equivalent functionality.

### Files to Remove

**Package:** `com.github.quarck.calnotify.quiethours`
- [ ] `QuietHoursManager.kt`
- [ ] `QuietHoursManagerInterface.kt`

**UI:**
- [ ] `prefs/fragments/QuietHoursSettingsFragment.kt`
- [ ] Related entries in `res/xml/preference_headers.xml`

**Dependencies to clean up:**
- [ ] `ApplicationController.kt` - `isCustomQuietHoursActive()`, quiet hours references
- [ ] `AlarmScheduler.kt` / `AlarmSchedulerInterface.kt` - quiet hours parameters
- [ ] `EventNotificationManager.kt` / `EventNotificationManagerInterface.kt` - silent period checks
- [ ] `ReminderAlarmBroadcastReceiver.kt` - quiet hours checks
- [ ] `MainActivity.kt`, `SnoozeAllActivity.kt`, `ViewEventActivityNoRecents.kt` - UI references
- [ ] `SettingsActivityNew.kt` - settings UI

---

## Calendar Editor / Quick Rescheduling

**Status:** DEPRECATED

**Reason:** Modern calendar apps have robust rescheduling built-in. Users can reschedule directly from notification tap → calendar app.

### Files to Remove

**Package:** `com.github.quarck.calnotify.calendareditor`
- [ ] `CalendarChangeManager.kt`
- [ ] `CalendarChangeManagerInterface.kt`
- [ ] `CalendarChangePersistentState.kt`
- [ ] `CalendarChangeRequest.kt`
- [ ] `CalendarChangeRequestMonitor.kt`
- [ ] `CalendarChangeRequestMonitorInterface.kt`

**Storage subpackage:** `com.github.quarck.calnotify.calendareditor.storage`
- [ ] `CalendarChangeRequestsStorage.kt`
- [ ] `CalendarChangeRequestsStorageImplInterface.kt`
- [ ] `CalendarChangeRequestsStorageImplV3.kt`
- [ ] `CalendarChangeRequestsStorageInterface.kt`

**UI:**
- [ ] `EditEventActivity.kt`

**Dependencies to clean up:**
- [ ] `ApplicationController.kt` - `CalendarChangeRequestMonitor` usage
- [ ] `CalendarMonitorService.kt` - change request handling
- [ ] `AndroidManifest.xml` - activity registrations

---

## Removal Prerequisites

Before removing these features:

1. Complete test suite for remaining core features
2. Update README to remove references to deprecated features
3. Verify no crashes when deprecated code paths are hit

---

---

## Legacy Storage & `.use {}` Pattern

**Status:** DEPRECATED (cleanup deferred until legacy storage removal)

**Reason:** The `.use {}` / `Closeable` pattern exists only for `LegacyEventsStorage` compatibility. Room storage has no-op `close()` because Room databases are singletons.

### Current State

- **Room storage `close()`**: No-op (intentional - Room manages singleton lifecycle)
- **Legacy storage `close()`**: Actually closes SQLiteOpenHelper
- **Code pattern**: Uses `.use {}` everywhere for consistency
- **Result**: Code can use storage after `.use {}` without crashing (but is conceptually wrong)

### Files to Clean Up (when legacy is removed)

**Remove `Closeable` from interfaces:**
- [ ] `EventsStorageInterface.kt`
- [ ] `DismissedEventsStorageInterface.kt`
- [ ] `MonitorStorageInterface.kt`

**Simplify wrapper classes:**
- [ ] `EventsStorage.kt` - remove delegation, simplify to direct Room usage
- [ ] `DismissedEventsStorage.kt` - same
- [ ] `MonitorStorage.kt` - same

**Remove `.use {}` calls throughout codebase** (or keep as harmless scope functions):
- [ ] `ApplicationController.kt` - many `.use {}` calls
- [ ] UI fragments and activities

**Update mocks:**
- [ ] `MockEventsStorage.kt` - remove closed-state tracking
- [ ] `MockDismissedEventsStorage.kt` - same
- [ ] `MockMonitorStorage.kt` - same

**See also:** `docs/architecture/storage_lifecycle.md`

---

## Custom Date/Time Picker Layouts

**Status:** REMOVED ✅

**Reason:** These custom layouts existed only to work around limitations of the old callback-based `DatePickerDialog`/`TimePickerDialog` (state loss on rotation, manual flow management). Modern `MaterialDatePicker`/`MaterialTimePicker` are fragment-based and handle all of this automatically.

### Files Removed

**Layouts (DELETED):**
- [x] `dialog_date_picker.xml` 
- [x] `dialog_time_picker.xml`
- [x] `dialog_date_time_picker.xml` (orphaned, never used)
- [x] `dialog_date_picker_halo_light.xml` (orphaned, never used)
- [x] `dialog_time_picker_halo_light.xml` (orphaned, never used)

**Code simplified:**
- [x] `ViewEventActivityNoRecents.kt` - Replaced ~100 lines of state tracking with ~30 lines using MaterialPickers
- [x] `SnoozeAllActivity.kt` - Same simplification
- [x] `EditEventActivity.kt` - Migrated to MaterialDatePicker/MaterialTimePicker

**Benefits achieved:**
- Automatic state restoration on rotation (DialogFragments handle their own state)
- Beautiful Material 3 UI with clock face time picker and calendar date picker
- ~200+ lines of manual state management code removed
- Built-in accessibility support

---

## LED Notification Pattern

**Status:** DEPRECATED

**Reason:** LED notification lights have been removed from virtually all modern phones since ~2018. Replaced by Always-On Display, edge lighting, and screen-based notification effects.

### Files to Remove

**UI:**
- [ ] `dialog_led_pattern.xml` - LED on/off time SeekBars
- [ ] `dialog_color_picker.xml` - LED color picker

**Code:**
- [ ] LED color/pattern handling in notification builders
- [ ] Settings preferences for LED customization

**Note:** Android still has the `Notification.Builder.setLights()` API but it's effectively a no-op on modern devices.

---

## Removal Timeline

| Phase | Action | Status |
|-------|--------|--------|
| 1 | Add @Deprecated annotations to code | Pending |
| 2 | Complete core feature test suite | In Progress |
| 3 | Remove Quiet Hours feature | Pending |
| 4 | Remove Calendar Editor feature | Pending |
| 5 | Remove LED notification settings | Pending |
| 6 | Remove custom picker layouts (use MaterialDatePicker/MaterialTimePicker) | ✅ DONE |
| 7 | Remove Legacy Storage & `.use {}` pattern | Pending |
| 8 | Clean up dependencies and update docs | Pending |

