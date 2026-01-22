# MaterialComponents Theme Migration

## Status: Planning
## Related: [#15 - Upgrade the UI](https://github.com/williscool/CalendarNotification/issues/15), [#225 - Filter pills bottom sheet dark theme](https://github.com/williscool/CalendarNotification/issues/225)

## Overview

The app currently uses `Theme.AppCompat.DayNight` as its base theme. While this provides basic dark mode support, Material Components (bottom sheets, dialogs, etc.) don't automatically inherit the dark theme styling.

This document outlines the migration path to `Theme.MaterialComponents.DayNight` for full Material Design support with automatic dark mode.

## Background

### Current State
- Base theme: `Theme.AppCompat.DayNight.DarkActionBar`
- Material library version: `com.google.android.material:material:1.12.0`
- Bottom sheets use `BottomSheetDialogFragment` from Material library
- Dialogs use `AlertDialog.Builder` from AppCompat

### Problem
Material Components don't inherit dark mode styling from AppCompat themes. This causes:
- Bottom sheets with light backgrounds in dark mode
- Inconsistent dialog styling
- Manual theme overrides needed for each component

### Solution Options

| Option | Approach | Scope | Risk |
|--------|----------|-------|------|
| **A** | Add `bottomSheetDialogTheme` attribute | Targeted fix for bottom sheets only | Low |
| **B** | Switch to `Theme.MaterialComponents.DayNight` | App-wide Material styling | Medium |

**Option A** was merged for the immediate bottom sheet fix.
**Option B** is tracked here for the broader UI upgrade.

## Option B: Full MaterialComponents Migration

### Changes Required

#### 1. Theme Parent Change (DONE in test branch)

**`values/styles.xml`:**
```xml
<!-- Before -->
<style name="AppTheme" parent="Theme.AppCompat.DayNight.DarkActionBar">

<!-- After -->
<style name="AppTheme" parent="Theme.MaterialComponents.DayNight.DarkActionBar">
    <item name="colorSecondary">@color/accent</item>  <!-- For FAB, etc. -->
```

**`values-night/styles.xml`:**
```xml
<!-- Update overlays to MaterialComponents equivalents -->
<style name="AppTheme.PopupOverlay" parent="ThemeOverlay.MaterialComponents.Dark">
```

---

#### 2. AlertDialog → MaterialAlertDialogBuilder

**Impact:** 52 usages across 14 files  
**Effort:** Medium (2-3 hours)  
**Visual Impact:** High (Material dialogs have rounded corners, different button styling)

**Files to update:**
- `MainActivityModern.kt` (3 usages)
- `MainActivityLegacy.kt` (4 usages)
- `MainActivityBase.kt` (4 usages)
- `ViewEventActivityNoRecents.kt` (7 usages)
- `SnoozeAllActivity.kt` (7 usages)
- `PreActionActivity.kt` (2 usages)
- `EditEventActivity.kt` (9 usages)
- `DismissedEventsActivity.kt` (2 usages)
- `CalendarsActivity.kt` (2 usages)
- `DismissedEventsFragment.kt` (2 usages)
- `NavigationSettingsFragmentX.kt` (3 usages)
- `MiscSettingsFragmentX.kt` (3 usages)
- `SnoozePresetPreferenceX.kt` (2 usages)
- `ListPreference.kt` (2 usages)

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

#### 3. Material Color Attributes

**Impact:** Improves theme consistency  
**Effort:** Low (30 minutes)

Add to `values/colors.xml`:
```xml
<!-- Material Design color attributes -->
<color name="colorSurface">@color/cardview_light_background</color>
<color name="colorOnSurface">@color/primary_text</color>
<color name="colorOnPrimary">@color/icons</color>
<color name="colorPrimaryVariant">@color/primary_dark</color>
<color name="colorSecondaryVariant">@color/accent</color>
```

Add to `values-night/colors.xml`:
```xml
<!-- Dark mode surface colors -->
<color name="colorSurface">@color/cardview_light_background</color>  <!-- #1E1E1E -->
<color name="colorOnSurface">@color/primary_text</color>  <!-- #E1E1E1 -->
<color name="colorOnPrimary">@color/icons</color>
<color name="colorPrimaryVariant">@color/primary_dark</color>
<color name="colorSecondaryVariant">@color/accent</color>
```

Add to `values/styles.xml` AppTheme:
```xml
<item name="colorSurface">@color/colorSurface</item>
<item name="colorOnSurface">@color/colorOnSurface</item>
<item name="colorOnPrimary">@color/colorOnPrimary</item>
<item name="colorPrimaryVariant">@color/colorPrimaryVariant</item>
<item name="colorSecondaryVariant">@color/colorSecondaryVariant</item>
```

---

#### 4. Fix EditText Styling

**Impact:** Bottom sheet search box  
**Effort:** Low (15 minutes)

**File:** `bottom_sheet_calendar_filter.xml`

```xml
<!-- Before -->
<EditText
    android:background="@android:drawable/editbox_background"
    ... />

<!-- After - Option 1: Simple fix -->
<EditText
    android:background="?android:attr/editTextBackground"
    ... />

<!-- After - Option 2: Full Material (more work) -->
<com.google.android.material.textfield.TextInputLayout
    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/search_calendars"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/calendar_filter_search_hint" />
</com.google.android.material.textfield.TextInputLayout>
```

---

#### 5. CardView → MaterialCardView (Optional)

**Impact:** Consistent card styling  
**Effort:** Low (15 minutes)

**File:** `content_main.xml`

```xml
<!-- Before -->
<androidx.cardview.widget.CardView ... >

<!-- After -->
<com.google.android.material.card.MaterialCardView ... >
```

MaterialCardView provides:
- Consistent corner radius
- Better elevation handling in dark mode
- Ripple effects

---

#### 6. Button Styling (If Needed)

MaterialComponents buttons default to **filled** style. If you want text or outlined buttons:

```xml
<!-- Text button (like a link) -->
<Button
    style="@style/Widget.MaterialComponents.Button.TextButton"
    ... />

<!-- Outlined button -->
<Button
    style="@style/Widget.MaterialComponents.Button.OutlinedButton"
    ... />

<!-- Filled button (default in MaterialComponents) -->
<Button
    style="@style/Widget.MaterialComponents.Button"
    ... />
```

---

## Implementation Priority

| Priority | Task | Effort | Visual Impact |
|----------|------|--------|---------------|
| 1 | Material color attributes | 30 min | Medium |
| 2 | AlertDialog → MaterialAlertDialogBuilder | 2-3 hours | **High** |
| 3 | Fix EditText in bottom sheet | 15 min | Medium |
| 4 | CardView → MaterialCardView | 15 min | Low |
| 5 | Button style audit | 1 hour | Medium |
| 6 | TextInputLayout migration (optional) | 2-3 hours | Medium |

**Total estimated effort:** 4-6 hours for cohesive Material look

---

## Visual Differences Summary

| Component | AppCompat | MaterialComponents |
|-----------|-----------|-------------------|
| Dialogs | Sharp corners, standard buttons | Rounded corners, Material buttons |
| Bottom sheets | Needs manual theming | Auto dark mode |
| Buttons | Borderless/text style | Filled by default |
| Cards | Standard elevation | Better dark mode elevation |
| Text fields | Platform default | Outlined/Filled with labels |
| Checkboxes | Platform style | Material ripple + animation |
| Radio buttons | Platform style | Material ripple + animation |

---

## Testing Checklist

After migration, test these in both light and dark mode:

- [ ] Main activity (Modern UI)
- [ ] Main activity (Legacy UI)
- [ ] Settings screens
- [ ] Handled Calendars page
- [ ] View Event screen
- [ ] Snooze All dialog
- [ ] All AlertDialog confirmations
- [ ] Filter bottom sheets (Time, Calendar)
- [ ] Edit Event screen
- [ ] About screen

---

## References

- [Material Components Android - Getting Started](https://material.io/develop/android/docs/getting-started)
- [Migrating to Material Components](https://material.io/blog/migrate-android-material-components)
- [Material Theming](https://material.io/design/material-theming/overview.html)
