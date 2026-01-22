# Material 3 Theme Migration

## Status: In Progress (Phase 1)
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

# Phase 2: Component Upgrades

Once Phase 1 is complete, these additional upgrades will further modernize the UI.

## 2.1 Toast → Snackbar

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

## 2.2 TimePickerDialog → MaterialTimePicker

**Impact:** ~77 usages across 6 files  
**Effort:** 3-4 hours  
**Benefit:** Modern clock-style picker, Material 3 styling

**Files to update:**
- `ViewEventActivityNoRecents.kt` (32 usages)
- `SnoozeAllActivity.kt` (30 usages)
- `EditEventActivity.kt` (7 usages)
- `SystemUtils.kt` (4 usages)
- `TimeOfDayPreferenceX.kt` (2 usages)
- `DefaultManualAllDayNotificationPreferenceX.kt` (2 usages)

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

## 2.3 DatePickerDialog → MaterialDatePicker

**Effort:** 2-3 hours (bundled with time picker work)  
**Benefit:** Modern calendar picker, range selection support

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
| MaterialTimePicker | 3-4 hours | High | Modern look |
| MaterialDatePicker | 2-3 hours | High | Modern look |
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

**Impact:** 2 usages in `dialog_led_pattern.xml`  
**Effort:** 1 hour  
**Benefit:** Material 3 slider with value labels, tick marks

```xml
<!-- Before -->
<SeekBar
    android:id="@+id/seekbar_led_on"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:max="100" />

<!-- After -->
<com.google.android.material.slider.Slider
    android:id="@+id/slider_led_on"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:valueFrom="0"
    android:valueTo="100"
    android:stepSize="1"
    app:labelBehavior="floating" />
```

**Kotlin changes:**
```kotlin
// Before
seekBar.progress = value
seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { ... })

// After
slider.value = value.toFloat()
slider.addOnChangeListener { _, value, fromUser -> ... }
```

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

| Task | Effort | Visual Impact | Priority |
|------|--------|---------------|----------|
| Spinner → ExposedDropdownMenu | 2-3 hours | Medium | Low |
| SeekBar → Slider | 1 hour | Medium | Low |
| ImageButton → MaterialButton | 30 min | Low | Low |

**Phase 3 Total:** ~4-5 hours

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

## Phase 2
- [ ] Snackbar with undo actions
- [ ] Time picker (snooze, event editing)
- [ ] Date picker (event editing)
- [ ] Switch toggles
- [ ] Divider consistency

## Phase 3
- [ ] Dropdown menus in interval dialogs
- [ ] LED pattern sliders
- [ ] Icon buttons in selection bar

---

# References

- [Material 3 for Android](https://m3.material.io/develop/android/mdc-android)
- [Migrate to Material 3](https://github.com/material-components/material-components-android/blob/master/docs/theming/Migration.md)
- [Material 3 Color System](https://m3.material.io/styles/color/overview)
- [Dynamic Color](https://m3.material.io/styles/color/dynamic-color/overview)
- [Material 3 Components Catalog](https://github.com/material-components/material-components-android/tree/master/catalog)
