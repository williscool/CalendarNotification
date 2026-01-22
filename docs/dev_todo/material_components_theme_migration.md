# Material 3 Theme Migration

## Status: In Progress (Phase 4 - Cleanup)

## Post-Migration Cleanup Notes

Items to address after core migration is complete:

- [ ] **FAB styling** - Edit FAB on main screen and pencil on activity view look slightly off with M3. Consider using `ExtendedFloatingActionButton` or adjusting `app:fabSize` and icon tint.
- [ ] **Button audit** - Review buttons throughout app for consistent M3 styling (filled vs tonal vs outlined)
## Related: [#15 - Upgrade the UI](https://github.com/williscool/CalendarNotification/issues/15), [#225 - Filter pills bottom sheet dark theme](https://github.com/williscool/CalendarNotification/issues/225)

## Overview

The app currently uses `Theme.AppCompat.DayNight` as its base theme. This document outlines the migration path to **Material 3** (`Theme.Material3.*`) for the latest Material Design language with:
- Automatic dark mode for all components
- Dynamic color support (Android 12+ wallpaper-based colors)
- Modern, rounded aesthetic
- Updated component styles

## Background

### Material Design Evolution

| Version | Theme | Android Library | Key Features |
|---------|-------|-----------------|--------------|
| Material 2 | `Theme.MaterialComponents.*` | material:1.x | Components, dark mode |
| **Material 3** | `Theme.Material3.*` | material:1.5+ | Dynamic color, updated design, M3 components |

The app uses `com.google.android.material:material:1.12.0` which includes full Material 3 support.

### Current State
- Base theme: `Theme.AppCompat.DayNight.DarkActionBar`
- Material library: `1.12.0` (supports Material 3)
- Problem: Material components don't inherit AppCompat dark mode

### Already Using Material Components ✅
The Modern UI already uses several Material components:
- `BottomNavigationView` - Tab navigation
- `FloatingActionButton` - Add event button
- `ChipGroup` - Filter pills
- `AppBarLayout` + `CoordinatorLayout` - Collapsing toolbar
- `SwipeRefreshLayout` - Pull to refresh

---

# Phase 1: Core Theme Migration

## 1.1 Theme Parent Change

**`values/styles.xml`:**
```xml
<!-- Before -->
<style name="AppTheme" parent="Theme.AppCompat.DayNight.DarkActionBar">

<!-- After - Material 3 -->
<style name="AppTheme" parent="Theme.Material3.DayNight">
    <!-- Primary brand colors -->
    <item name="colorPrimary">@color/primary</item>
    <item name="colorOnPrimary">@color/icons</item>
    <item name="colorPrimaryContainer">@color/primary_light</item>
    <item name="colorOnPrimaryContainer">@color/primary_dark</item>
    
    <!-- Secondary/accent colors -->
    <item name="colorSecondary">@color/accent</item>
    <item name="colorOnSecondary">@color/icons</item>
    
    <!-- Surface colors -->
    <item name="colorSurface">@color/cardview_light_background</item>
    <item name="colorOnSurface">@color/primary_text</item>
    <item name="colorSurfaceVariant">@color/background</item>
    <item name="colorOnSurfaceVariant">@color/secondary_text</item>
    
    <!-- Background -->
    <item name="android:colorBackground">@color/background</item>
    
    <!-- Optional: Enable dynamic colors on Android 12+ -->
    <!-- <item name="dynamicColorThemeOverlay">@style/ThemeOverlay.Material3.DynamicColors.DayNight</item> -->
</style>
```

**`values-night/styles.xml`:**
```xml
<style name="AppTheme.PopupOverlay" parent="ThemeOverlay.Material3.Dark"></style>
```

**Effort:** 30 minutes  
**Impact:** High - enables all Material 3 component styling

---

## 1.2 AlertDialog → MaterialAlertDialogBuilder

**Impact:** 52 usages across 14 files  
**Effort:** 2-3 hours  
**Visual Impact:** High (M3 dialogs have full-width buttons, more padding)

**Files to update:**
| File | Count |
|------|-------|
| `EditEventActivity.kt` | 9 |
| `ViewEventActivityNoRecents.kt` | 7 |
| `SnoozeAllActivity.kt` | 7 |
| `MainActivityLegacy.kt` | 4 |
| `MainActivityBase.kt` | 4 |
| `MainActivityModern.kt` | 3 |
| `NavigationSettingsFragmentX.kt` | 3 |
| `MiscSettingsFragmentX.kt` | 3 |
| `PreActionActivity.kt` | 2 |
| `DismissedEventsActivity.kt` | 2 |
| `CalendarsActivity.kt` | 2 |
| `DismissedEventsFragment.kt` | 2 |
| `SnoozePresetPreferenceX.kt` | 2 |
| `ListPreference.kt` | 2 |

**Change pattern:**
```kotlin
// Before
import androidx.appcompat.app.AlertDialog

AlertDialog.Builder(context)
    .setTitle("Title")
    .setMessage("Message")
    .setPositiveButton("OK") { _, _ -> }
    .show()

// After
import com.google.android.material.dialog.MaterialAlertDialogBuilder

MaterialAlertDialogBuilder(context)
    .setTitle("Title")
    .setMessage("Message")
    .setPositiveButton("OK") { _, _ -> }
    .show()
```

---

## 1.3 Fix EditText Styling

**File:** `bottom_sheet_calendar_filter.xml`  
**Effort:** 15 minutes

```xml
<!-- Before -->
<EditText
    android:background="@android:drawable/editbox_background"
    ... />

<!-- After - Material 3 outlined text field -->
<com.google.android.material.textfield.TextInputLayout
    style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="@string/calendar_filter_search_hint"
    app:startIconDrawable="@android:drawable/ic_menu_search">
    
    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/search_calendars"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="text"
        android:imeOptions="actionSearch" />
        
</com.google.android.material.textfield.TextInputLayout>
```

---

## 1.4 CardView → MaterialCardView

**File:** `content_main.xml`  
**Effort:** 15 minutes

```xml
<!-- Before -->
<androidx.cardview.widget.CardView
    app:cardCornerRadius="8dp"
    ... >

<!-- After - Material 3 card with automatic shape -->
<com.google.android.material.card.MaterialCardView
    style="@style/Widget.Material3.CardView.Elevated"
    ... >
```

---

## 1.5 Button Styling

Material 3 buttons have different default styles:

```xml
<!-- Filled button (default, high emphasis) -->
<Button
    style="@style/Widget.Material3.Button"
    ... />

<!-- Tonal button (medium emphasis) -->
<Button
    style="@style/Widget.Material3.Button.TonalButton"
    ... />

<!-- Outlined button (medium emphasis) -->
<Button
    style="@style/Widget.Material3.Button.OutlinedButton"
    ... />

<!-- Text button (low emphasis) -->
<Button
    style="@style/Widget.Material3.Button.TextButton"
    ... />
```

---

## Phase 1 Summary

| Task | Effort | Visual Impact |
|------|--------|---------------|
| Theme parent change to Material 3 | 30 min | High |
| AlertDialog → MaterialAlertDialogBuilder | 2-3 hours | High |
| Fix EditText in bottom sheet | 15 min | Medium |
| CardView → MaterialCardView | 15 min | Low |
| Button style audit | 1 hour | Medium |

**Phase 1 Total:** ~4-5 hours

---

# Phase 2: Quick Component Upgrades

Simple drop-in replacements with minimal code changes.

## 2.1 Switch → MaterialSwitch ✅ COMPLETE

**Files updated:**
- `pref_switch_with_text.xml`
- `content_add_event.xml`

## 2.2 Dividers → MaterialDivider (OPTIONAL)

~15 dividers across 6 files. Low visual impact, can be done incrementally.

**Pattern:**
```xml
<!-- Before -->
<View
    android:layout_width="match_parent"
    android:layout_height="1dp"
    android:background="@color/divider" />

<!-- After -->
<com.google.android.material.divider.MaterialDivider
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

**Files:** `content_add_event.xml` (4), `content_main.xml` (3), `activity_view.xml` (2), `activity_pre_action.xml` (2), `dialog_color_picker.xml` (3), `bottom_sheet_calendar_filter.xml` (1)

**Note:** One divider has ID `snooze_view_inter_view_divider` used in code - preserve the ID.

---

# Phase 3: Snackbar Migration (SELECTIVE)

Requires View anchor - only practical in Activities/Fragments, not services.

## 3.1 Toast → Snackbar

**Impact:** 32 usages across 11 files  
**Effort:** 2-3 hours  
**Benefit:** Actionable messages (undo), better UX, Material styling

**Files to update:**
| File | Count |
|------|-------|
| `EditEventActivity.kt` | 10 |
| `PreActionActivity.kt` | 6 |
| `ApplicationController.kt` | 5 |
| `ViewEventActivityNoRecents.kt` | 2 |
| `ReminderPatternPreferenceX.kt` | 2 |
| `NavigationSettingsFragmentX.kt` | 2 |
| Others | 5 |

**Change pattern:**
```kotlin
// Before
Toast.makeText(context, "Event dismissed", Toast.LENGTH_SHORT).show()

// After - with optional undo action!
Snackbar.make(rootView, "Event dismissed", Snackbar.LENGTH_LONG)
    .setAction("Undo") { restoreEvent(event) }
    .show()
```

**Note:** Snackbar requires a View anchor. Activities need to pass a root view.

---

# Phase 4: Picker Upgrades

Requires API changes from callback-based to fragment-based pickers.

## 4.1 TimePickerDialog → MaterialTimePicker

**Impact:** ~4 actual picker instantiations + 2 deprecated custom layouts  
**Effort:** 2-3 hours  
**Benefit:** Modern clock-style picker, Material 3 styling, DELETE legacy code

**Files to update:**
- `EditEventActivity.kt` - 2 `TimePickerDialog` instantiations (start/end time)
- `ViewEventActivityNoRecents.kt` - Custom picker → MaterialTimePicker (deletes ~50 lines)
- `SnoozeAllActivity.kt` - Same custom picker → MaterialTimePicker (deletes ~50 lines)

**Legacy files to DELETE:**
- `dialog_time_picker.xml` - No longer needed with MaterialTimePicker

**Change pattern:**
```kotlin
// Before
TimePickerDialog(context, { _, hour, minute ->
    onTimeSelected(hour, minute)
}, currentHour, currentMinute, true).show()

// After
val picker = MaterialTimePicker.Builder()
    .setTimeFormat(TimeFormat.CLOCK_24H)
    .setHour(currentHour)
    .setMinute(currentMinute)
    .setTitleText("Select time")
    .build()
    
picker.addOnPositiveButtonClickListener {
    onTimeSelected(picker.hour, picker.minute)
}
picker.show(supportFragmentManager, "timePicker")
```

---

## 4.2 DatePickerDialog → MaterialDatePicker

**Impact:** ~4 actual picker instantiations + 2 deprecated custom layouts  
**Effort:** Bundled with time picker work  
**Benefit:** Modern calendar picker, DELETE legacy code

**Files to update:**
- `EditEventActivity.kt` - 2 `DatePickerDialog` instantiations (start/end date)
- `ViewEventActivityNoRecents.kt` - Custom picker → MaterialDatePicker
- `SnoozeAllActivity.kt` - Same custom picker → MaterialDatePicker

**Legacy files to DELETE:**
- `dialog_date_picker.xml` - No longer needed with MaterialDatePicker

**Change pattern:**
```kotlin
// Before
DatePickerDialog(context, { _, year, month, day ->
    onDateSelected(year, month, day)
}, currentYear, currentMonth, currentDay).show()

// After
val picker = MaterialDatePicker.Builder.datePicker()
    .setTitleText("Select date")
    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
    .build()
    
picker.addOnPositiveButtonClickListener { selection ->
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    calendar.timeInMillis = selection
    onDateSelected(calendar.get(Calendar.YEAR), 
                   calendar.get(Calendar.MONTH), 
                   calendar.get(Calendar.DAY_OF_MONTH))
}
picker.show(supportFragmentManager, "datePicker")
```

---

## 2.4 SwitchCompat → MaterialSwitch

**Impact:** 2 files  
**Effort:** 30 minutes  
**Benefit:** Material 3 switch with thumb/track styling

**Files:**
- `content_add_event.xml`
- `pref_switch_with_text.xml`

```xml
<!-- Before -->
<androidx.appcompat.widget.SwitchCompat
    ... />

<!-- After -->
<com.google.android.material.materialswitch.MaterialSwitch
    ... />
```

---

## 2.5 Manual Dividers → MaterialDivider

**Impact:** ~28 manual dividers across 15 layout files  
**Effort:** 1-2 hours  
**Benefit:** Consistent divider styling, proper insets

```xml
<!-- Before -->
<View
    android:layout_width="match_parent"
    android:layout_height="1dp"
    android:background="?android:attr/listDivider" />

<!-- After -->
<com.google.android.material.divider.MaterialDivider
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:dividerInsetStart="16dp"
    app:dividerInsetEnd="16dp" />
```

---

## 2.6 Toolbar → MaterialToolbar (Optional)

**Impact:** 13 layout files  
**Effort:** 1 hour  
**Benefit:** Menu handling improvements, Material 3 styling

```xml
<!-- Before -->
<androidx.appcompat.widget.Toolbar ... />

<!-- After -->
<com.google.android.material.appbar.MaterialToolbar ... />
```

---

## Phase 2 Summary

| Task | Effort | Visual Impact | UX Benefit |
|------|--------|---------------|------------|
| Toast → Snackbar | 2-3 hours | Medium | **High** (undo actions!) |
| MaterialTimePicker | 1 hour | High | ✅ Done |
| MaterialDatePicker | 1 hour | High | ✅ Done |
| MaterialSwitch | 30 min | Medium | M3 styling |
| MaterialDivider | 1-2 hours | Low | Consistency |
| MaterialToolbar | 1 hour | Low | Minor |

**Phase 2 Total:** ~10-13 hours

---

# Phase 3: Advanced Component Upgrades

Lower priority upgrades for complete Material 3 consistency.

## 3.1 Spinner → ExposedDropdownMenu

**Impact:** 4 files  
**Effort:** 2-3 hours  
**Benefit:** Modern dropdown with Material 3 styling, search support

**Files to update:**
- `dialog_interval_picker.xml`
- `dialog_add_event_notification.xml`
- `dialog_default_manual_notification.xml`
- `dialog_reminder_interval_configuration.xml`

**Change pattern:**
```xml
<!-- Before -->
<Spinner
    android:id="@+id/spinner_unit"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content" />

<!-- After - Material 3 Exposed Dropdown Menu -->
<com.google.android.material.textfield.TextInputLayout
    style="@style/Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:hint="Unit">
    
    <AutoCompleteTextView
        android:id="@+id/dropdown_unit"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="none" />
        
</com.google.android.material.textfield.TextInputLayout>
```

**Kotlin changes required:**
```kotlin
// Before (Spinner)
spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, items)
spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { ... }

// After (ExposedDropdownMenu)
val adapter = ArrayAdapter(context, R.layout.list_item, items)
(textInputLayout.editText as? AutoCompleteTextView)?.setAdapter(adapter)
autoCompleteTextView.setOnItemClickListener { _, _, position, _ -> ... }
```

---

## 3.2 SeekBar → Slider

**Status:** SKIPPED - LED notification feature deprecated (see `deprecated_features.md`)

LED notification lights have been removed from modern phones since ~2018. No point upgrading UI for a feature scheduled for removal.

---

## 3.3 ImageButton → MaterialButton (Icon style)

**Impact:** 2 usages in `fragment_event_list.xml`  
**Effort:** 30 minutes  
**Benefit:** Consistent ripple, better accessibility

```xml
<!-- Before -->
<ImageButton
    android:id="@+id/btn_close_selection"
    android:src="@drawable/ic_clear_white_24dp"
    android:background="?attr/selectableItemBackgroundBorderless" />

<!-- After -->
<com.google.android.material.button.MaterialButton
    android:id="@+id/btn_close_selection"
    style="@style/Widget.Material3.Button.IconButton"
    app:icon="@drawable/ic_clear_white_24dp"
    app:iconTint="@android:color/white" />
```

---

## Phase 3 Summary

| Task | Effort | Visual Impact | Status |
|------|--------|---------------|--------|
| Spinner → ExposedDropdownMenu | 2-3 hours | Medium | ✅ Done |
| ~~SeekBar → Slider~~ | - | - | SKIPPED (LED deprecated) |
| ImageButton → MaterialButton | 30 min | Low | ✅ Done |

**Phase 3 Total:** ✅ Complete!

---

# Optional: Dynamic Colors (Android 12+)

Material 3 supports **Dynamic Colors** - extracting colors from the user's wallpaper. This is opt-in.

**To enable:**
```xml
<style name="AppTheme" parent="Theme.Material3.DayNight">
    <!-- Enable dynamic colors -->
    <item name="dynamicColorThemeOverlay">@style/ThemeOverlay.Material3.DynamicColors.DayNight</item>
</style>
```

Or programmatically (recommended for gradual rollout):
```kotlin
// In Application.onCreate() or Activity.onCreate()
DynamicColors.applyToActivitiesIfAvailable(this)
```

**Considerations:**
- Only works on Android 12+ (falls back to defined colors on older versions)
- App brand colors may be overridden
- Can be disabled per-activity if needed

---

# Visual Comparison

| Component | AppCompat | Material 3 |
|-----------|-----------|------------|
| Dialogs | Sharp corners | Large rounded corners, full-width buttons |
| Bottom sheets | Manual theming | Auto dark mode, rounded top corners |
| Buttons | Borderless default | Filled default, tonal variants |
| Cards | Standard elevation | Tinted surfaces, larger radius |
| Switches | Small toggle | Large thumb/track, ripple |
| Text fields | Underline | Outlined/Filled with labels |
| Time picker | Spinner style | Clock face, Material 3 |
| Date picker | Calendar grid | Modern calendar, range support |
| Chips | Flat | Tonal, elevated variants |

---

# Testing Checklist

## Phase 1
- [ ] Main activity (Modern UI) - light & dark
- [ ] Main activity (Legacy UI) - light & dark
- [ ] All AlertDialog confirmations
- [ ] Filter bottom sheets (Time, Calendar)
- [ ] Settings screens
- [ ] Handled Calendars page
- [ ] View Event screen
- [ ] Edit Event screen
- [ ] About screen

## Phase 1: Core Theme ✅ COMPLETE
- [x] Theme parent → Material3.DayNight
- [x] AlertDialog → MaterialAlertDialogBuilder (18 files)
- [x] TextInputLayout for bottom sheet search
- [x] CardView → MaterialCardView

## Phase 2: Quick Components ✅ COMPLETE  
- [x] MaterialSwitch (2 files)
- [x] MaterialDivider (~15 locations across 10 files)

## Phase 3: Snackbar ✅ COMPLETE
- [x] Toast → Snackbar in Activities (selective)
  - MainActivityModern, ViewEventActivityNoRecents, PreActionActivity
  - EditEventActivity, ReportABugActivity, MiscSettingsFragmentX
  - ReminderPatternPreferenceX
- [x] Keep Toast in services (ApplicationController, SnoozeResult, NotificationActionSnoozeService)
- [x] Keep Toast in NavigationSettingsFragmentX (app restart scenario)

## Phase 4: Advanced Components ✅ COMPLETE
- [x] ImageButton → MaterialButton (2 usages in fragment_event_list.xml)
- [x] Spinner → ExposedDropdownMenu (4 layouts + 2 Kotlin files)
- [x] ~~SeekBar → Slider~~ - SKIPPED (LED feature deprecated, see deprecated_features.md)

## Phase 5: Pickers - ✅ Complete!
- [x] MaterialTimePicker (2 in EditEventActivity + 2 custom picker dialogs)
- [x] MaterialDatePicker (2 in EditEventActivity + 2 custom picker dialogs)
- [x] DELETED `dialog_date_picker.xml` (legacy custom layout)
- [x] DELETED `dialog_time_picker.xml` (legacy custom layout)

**Benefits achieved:**
- Removed ~200 lines of manual state restoration code
- Material pickers are DialogFragments and handle their own state
- Modern M3 styling with clock face time picker and calendar date picker
- Simplified ViewEventActivityNoRecents and SnoozeAllActivity significantly

---

## Phase 6: Event Card & List Redesign - ✅ Complete!
- [x] Event card → MaterialCardView with ConstraintLayout
- [x] M3 typography and spacing
- [x] MaterialCheckBox and MaterialRadioButton across app
- [x] Tighter list spacing per M3 guidelines

## Phase 7: App Bar Modernization - PARTIAL
- [x] Main activity → MaterialToolbar with M3 styling
- [x] Main activity legacy → Same
- [x] Day mode keeps original `colorPrimary` (teal/gray)
- [x] Night mode uses surface color for M3 dark theme look

### Remaining Toolbar Updates (Polish Pass)
The following activities still use old `androidx.appcompat.widget.Toolbar`:
- [ ] `activity_view.xml` - Event view screen
- [ ] `activity_add_event.xml` - Add/Edit event
- [ ] `activity_settings.xml` / `activity_settings_x.xml` - Settings
- [ ] `activity_about.xml` - About screen
- [ ] `activity_calendars.xml` - Calendar selection
- [ ] `activity_dismissed_events.xml` - Dismissed events
- [ ] `activity_snooze_all.xml` - Snooze all
- [ ] `activity_privacy_policy.xml` - Privacy policy
- [ ] `activity_report_a_bug.xml` - Bug report
- [ ] `activity_car_mode.xml` - Car mode

**Pattern to apply:**
```xml
<com.google.android.material.appbar.MaterialToolbar
    android:id="@+id/toolbar"
    android:layout_width="match_parent"
    android:layout_height="?attr/actionBarSize"
    app:titleTextAppearance="?attr/textAppearanceTitleLarge"
    app:titleTextColor="@color/app_bar_on_background"
    app:navigationIconTint="@color/app_bar_on_background" />
```

---

## Phase 8: Event View Modernization - TODO
The event view screen (`activity_view.xml`) needs a full redesign:
- [ ] Replace colored header with MaterialCardView or surface treatment
- [ ] Update FAB styling
- [ ] Modernize action items/buttons
- [ ] Consider using M3 list items for snooze options

---

# References

- [Material 3 for Android](https://m3.material.io/develop/android/mdc-android)
- [Migrate to Material 3](https://github.com/material-components/material-components-android/blob/master/docs/theming/Migration.md)
- [Material 3 Color System](https://m3.material.io/styles/color/overview)
- [Dynamic Color](https://m3.material.io/styles/color/dynamic-color/overview)
- [Material 3 Components Catalog](https://github.com/material-components/material-components-android/tree/master/catalog)
