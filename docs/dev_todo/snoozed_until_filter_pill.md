# Snoozed Until Filter Pill / Chip

**Parent Doc:** [event_lookahead_milestone3_filter_pills.md](./event_lookahead_milestone3_filter_pills.md)  
**GitHub Issue:** [#255](https://github.com/williscool/CalendarNotification/issues/255)

## Overview

Add a "Snoozed Until" filter chip to the Active tab that filters events based on when their snooze expires (`event.snoozedUntil`). Features configurable interval presets, a before/after direction toggle, and custom period / specific date-time pickers — mirroring patterns from the Upcoming Time Filter and View Event snooze dialogs.

## Current State

| Feature | Status |
|---------|--------|
| Time filter chip (Active/Dismissed) | ✅ Filters on `instanceStartTime` / `instanceEndTime` |
| Status filter chip | ✅ Can filter to show only snoozed events |
| Upcoming time filter | ✅ Configurable presets, persisted to Settings |
| View Event "For a custom period" | ✅ `TimeIntervalPickerController` + quick presets dialog |
| View Event "Until a specific time and date" | ✅ `DatePicker` → `TimePicker` two-step flow |
| Snoozed Until filter chip | ❌ Not implemented (this feature) |

### Existing Patterns to Reuse

| Pattern | Source | How we use it |
|---------|--------|---------------|
| Dynamic radio button presets | `UpcomingTimeFilterBottomSheet` | Generate interval options from configurable presets |
| Preset parsing/formatting | `PreferenceUtils.parseSnoozePresets()` / `formatPresetHumanReadable()` | Parse `"12h, 1d, 3d, 7d, 4w"` format |
| Custom period preference | `UpcomingTimePresetPreferenceX` / `SnoozePresetPreferenceX` | Settings UI for configuring presets |
| Duration picker | `TimeIntervalPickerController` + `dialog_interval_picker.xml` | "For a custom period" option |
| Date + time picker | `dialog_date_picker.xml` → `dialog_time_picker.xml` | "Until a specific time and date" option |
| Fragment Result API | `TimeFilterBottomSheet`, `UpcomingTimeFilterBottomSheet` | Communicate selection back to activity |
| In-memory filter state | `FilterState` + Bundle serialization | Filter clears on tab switch, survives rotation |

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Which tabs | **Active only** | Only active events can be snoozed. Dismissed/Upcoming don't have meaningful `snoozedUntil`. |
| Non-snoozed events | **Excluded when filter active** | Events with `snoozedUntil == 0` don't match any non-ALL filter — they have no snooze time to filter on. |
| Preset intervals | **Configurable via Settings** | Follow `UpcomingTimeFilterBottomSheet` pattern — presets parsed from comma-separated string using `PreferenceUtils`. |
| Default presets | **12h, 1d, 3d, 7d, 4w** | Practical defaults for "show events waking up within X". |
| Settings location | **Navigation & UI → Active Events** (new category) | Parallel to the existing "Upcoming Events" category. |
| Filter persistence | **In-memory (`FilterState`)** | Same as Time filter — clears on tab switch, survives rotation. Presets live in Settings, but the current _selection_ is in-memory. |
| Before/After | **Toggle in bottom sheet** | "Before" = `snoozedUntil <= now + interval`. "After" = `snoozedUntil > now + interval`. |
| Custom period | **Reuse `TimeIntervalPickerController`** | Same widget used by View Event Activity's "For a custom period". |
| Specific time | **Reuse `DatePicker` → `TimePicker` flow** | Same two-step flow used by View Event Activity's "Until a specific time and date". |

## UI Vision

### Active Tab Chip Row (after this change)

```
┌──────────────────────────────────────────────────────────────────────────┐
│  [ Calendar ▼ ]  [ Status ▼ ]  [ Time ▼ ]  [ Snoozed Until ▼ ]     →  │
└──────────────────────────────────────────────────────────────────────────┘
```

### Snoozed Until Bottom Sheet

```
┌─────────────────────────────────────────────────┐
│  Filter by Snoozed Until                        │
├─────────────────────────────────────────────────┤
│  [ ● Before | ○ After ]          ← toggle       │
├─────────────────────────────────────────────────┤
│  ○  All                                         │
│  ─────────────────────────────────              │
│  ○  12 hours                                    │
│  ●  1 day                          ← current   │
│  ○  3 days                                      │
│  ○  7 days                                      │
│  ○  4 weeks                                     │
│  ─────────────────────────────────              │
│  ○  For a custom period...                      │
│  ○  Until a specific time and date...           │
├─────────────────────────────────────────────────┤
│              [ APPLY ]                          │
└─────────────────────────────────────────────────┘
```

**Behavior:**

- **Before + preset**: Show events where `snoozedUntil <= now + preset` (waking up within X)
- **After + preset**: Show events where `snoozedUntil > now + preset` (won't wake up for at least X)
- **Before + specific time**: Show events where `snoozedUntil <= specificTime`
- **After + specific time**: Show events where `snoozedUntil > specificTime`
- **All**: No filter regardless of direction toggle

**Custom period flow:** Selecting "For a custom period..." opens the `TimeIntervalPickerController` dialog (same as snooze). The entered duration replaces the preset selection.

**Specific time flow:** Selecting "Until a specific time and date..." opens the DatePicker → TimePicker two-step flow (same as snooze). The chosen absolute timestamp replaces the preset selection.

### Chip Text Examples

| State | Chip Text |
|-------|-----------|
| No filter | "Snoozed Until" |
| Before 1 day | "Snoozed ≤ 1 day" |
| After 7 days | "Snoozed > 7 days" |
| Before specific time | "Snoozed ≤ Mar 15 3:00 PM" |
| After custom 6h | "Snoozed > 6 hours" |

---

## Filter Definitions

### SnoozedUntilFilterConfig

Unlike the simple `TimeFilter` enum, this filter needs to represent multiple modes and a direction. Stored as a data class in `FilterState`:

```kotlin
data class SnoozedUntilFilterConfig(
    val mode: SnoozedUntilFilterMode = SnoozedUntilFilterMode.ALL,
    val direction: FilterDirection = FilterDirection.BEFORE,
    val valueMillis: Long = 0L
)

enum class SnoozedUntilFilterMode {
    ALL,            // no filter (default)
    PRESET,         // from configurable interval presets; valueMillis = duration
    CUSTOM_PERIOD,  // user-entered duration; valueMillis = duration
    SPECIFIC_TIME   // user-picked date+time; valueMillis = absolute timestamp
}

enum class FilterDirection { BEFORE, AFTER }
```

### Matching Logic

```kotlin
fun matches(event: EventAlertRecord, now: Long): Boolean {
    if (mode == ALL) return true
    if (event.snoozedUntil == 0L) return false

    val threshold = when (mode) {
        ALL -> return true
        PRESET, CUSTOM_PERIOD -> now + valueMillis
        SPECIFIC_TIME -> valueMillis
    }

    return when (direction) {
        BEFORE -> event.snoozedUntil <= threshold
        AFTER -> event.snoozedUntil > threshold
    }
}
```

### Bundle Serialization

Three values to persist: `mode.ordinal` (Int), `direction.ordinal` (Int), `valueMillis` (Long). Same pattern as other `FilterState` fields.

---

## Settings: Active Events Category

New category in `navigation_preferences.xml`, parallel to the existing "Upcoming Events" category:

```xml
<PreferenceCategory
    android:title="@string/active_events_category"
    android:key="_active_events"
    android:dependency="use_new_navigation_ui">

    <com.github.quarck.calnotify.prefs.SnoozedUntilPresetPreferenceX
        android:key="pref_snoozed_until_presets"
        android:title="@string/snoozed_until_presets_title"
        android:summary="@string/snoozed_until_presets_summary"
        android:defaultValue="12h, 1d, 3d, 7d, 4w" />

</PreferenceCategory>
```

### Settings Properties

```kotlin
// In Settings.kt
private const val SNOOZED_UNTIL_PRESETS_KEY = "pref_snoozed_until_presets"
const val DEFAULT_SNOOZED_UNTIL_PRESETS = "12h, 1d, 3d, 7d, 4w"

val snoozedUntilPresetsRaw: String
    get() = getString(SNOOZED_UNTIL_PRESETS_KEY, DEFAULT_SNOOZED_UNTIL_PRESETS)

val snoozedUntilPresets: LongArray
    get() {
        val ret = PreferenceUtils.parseSnoozePresets(snoozedUntilPresetsRaw)
            ?: PreferenceUtils.parseSnoozePresets(DEFAULT_SNOOZED_UNTIL_PRESETS)
            ?: return longArrayOf()
        return ret.filter { it > 0 }.toLongArray()
    }
```

Uses the same `PreferenceUtils.parseSnoozePresets()` / `formatPresetHumanReadable()` infrastructure as snooze presets and upcoming time presets. No max lookahead clamping needed (unlike upcoming, there's no scan window limit for snoozed events).

The `SnoozedUntilPresetPreferenceX` can either reuse or extend the existing `UpcomingTimePresetPreferenceX` / `SnoozePresetPreferenceX` pattern — custom `DialogPreference` with EditText, parsing via `PreferenceUtils`.

---

## Implementation Phases

### Phase 1: Settings Infrastructure

**Goal:** Add configurable presets and the Active Events settings category.

**Files to modify:**
- `Settings.kt` — Add `snoozedUntilPresetsRaw`, `snoozedUntilPresets`
- `navigation_preferences.xml` — Add "Active Events" category with preset preference
- `strings.xml` — Add `active_events_category`, `snoozed_until_presets_title`, `snoozed_until_presets_summary`

**New files:**
- `SnoozedUntilPresetPreferenceX.kt` — Custom preference (or generalize the existing `UpcomingTimePresetPreferenceX`)

---

### Phase 2: Filter State Model

**Goal:** Add `SnoozedUntilFilterConfig` to `FilterState` with matching, serialization, and display.

**Files to modify:**
- `FilterState.kt`

**Changes:**

1. Add `SNOOZED_UNTIL` to `FilterType` enum:

```kotlin
enum class FilterType {
    CALENDAR, STATUS, TIME, SNOOZED_UNTIL
}
```

2. Add `FilterDirection` enum and `SnoozedUntilFilterMode` enum.

3. Add `SnoozedUntilFilterConfig` data class with `matches()` method.

4. Add `snoozedUntilFilter: SnoozedUntilFilterConfig` field to `FilterState`.

5. Add `matchesSnoozedUntil()` method to `FilterState`.

6. Update `filterEvents()` inline fun to include `SNOOZED_UNTIL`:

```kotlin
(FilterType.SNOOZED_UNTIL !in apply || matchesSnoozedUntil(event, now))
```

7. Update `hasActiveFilters()`:

```kotlin
snoozedUntilFilter.mode != SnoozedUntilFilterMode.ALL
```

8. Update `toDisplayString()` — show direction + value (e.g., "Snoozed ≤ 1 day").

9. Update `toBundle()` / `fromBundle()` — serialize `mode.ordinal`, `direction.ordinal`, `valueMillis`.

10. Update active events `filterEvents()` default apply set to include `FilterType.SNOOZED_UNTIL`.

---

### Phase 3: Tests

**Goal:** Tests before building UI.

**Files to modify:**
- `FilterStateTest.kt`

**Tests to add:**

```
SnoozedUntilFilter:
- ALL matches all events including non-snoozed
- ALL matches non-snoozed events (snoozedUntil == 0)
- PRESET BEFORE matches events snoozed until within interval
- PRESET BEFORE excludes events snoozed until beyond interval
- PRESET BEFORE excludes non-snoozed events
- PRESET AFTER matches events snoozed until beyond interval
- PRESET AFTER excludes events snoozed until within interval
- PRESET AFTER excludes non-snoozed events
- CUSTOM_PERIOD works same as PRESET (both use now + valueMillis)
- SPECIFIC_TIME BEFORE matches events snoozed until before timestamp
- SPECIFIC_TIME BEFORE excludes events snoozed until after timestamp
- SPECIFIC_TIME AFTER matches events snoozed until after timestamp
- SPECIFIC_TIME AFTER excludes events snoozed until before timestamp
- boundary: event.snoozedUntil == threshold with BEFORE matches (<=)
- boundary: event.snoozedUntil == threshold with AFTER does not match (>)

FilterState integration:
- matchesSnoozedUntil delegates to config
- default has ALL snoozedUntilFilter
- hasActiveFilters true when snoozedUntilFilter is not ALL
- toDisplayString shows snoozed until when not ALL

Serialization:
- toBundle/fromBundle round-trip for each mode
- toBundle/fromBundle round-trip preserves direction
- toBundle/fromBundle round-trip preserves valueMillis
- fromBundle with null returns default
```

---

### Phase 4: Bottom Sheet UI

**Goal:** Create the bottom sheet with dynamic presets, before/after toggle, and custom options.

**New files:**
- `SnoozedUntilFilterBottomSheet.kt`
- `layout/bottom_sheet_snoozed_until_filter.xml`

**Bottom sheet structure** (follows `UpcomingTimeFilterBottomSheet` dynamic pattern):

1. **Header** — "Filter by Snoozed Until"
2. **Before/After toggle** — `RadioGroup` or `MaterialButtonToggleGroup` with two options
3. **"All" radio button** — clears filter
4. **Divider**
5. **Dynamic preset radio buttons** — generated from `settings.snoozedUntilPresets` using `PreferenceUtils.formatPresetHumanReadable()` (same as `UpcomingTimeFilterBottomSheet`)
6. **Divider**
7. **"For a custom period..." radio button** — on select, opens `TimeIntervalPickerController` dialog (reuse from `ViewEventActivityNoRecents.customSnoozeShowDialog`)
8. **"Until a specific time and date..." radio button** — on select, opens DatePicker → TimePicker flow (reuse from `ViewEventActivityNoRecents.snoozeUntilShowDatePickerDialog`)
9. **Apply button** — sends result via Fragment Result API

**Result bundle:**

```kotlin
companion object {
    const val REQUEST_KEY = "snoozed_until_filter_request"
    const val RESULT_MODE = "result_mode"        // SnoozedUntilFilterMode ordinal
    const val RESULT_DIRECTION = "result_direction" // FilterDirection ordinal
    const val RESULT_VALUE = "result_value"       // Long — duration or timestamp
}
```

**Strings to add:**

```xml
<string name="filter_snoozed_until">Snoozed Until</string>
<string name="filter_snoozed_until_all">All</string>
<string name="filter_snoozed_until_before">Before</string>
<string name="filter_snoozed_until_after">After</string>
<string name="filter_snoozed_until_custom_period">For a custom period…</string>
<string name="filter_snoozed_until_specific_time">Until a specific time and date…</string>
```

---

### Phase 5: Wire Up Chip in MainActivityModern

**Goal:** Add the chip to the Active tab and connect it to the bottom sheet.

**Files to modify:**
- `MainActivityModern.kt`

**Changes:**

1. `addSnoozedUntilChip()` — creates chip, shows `SnoozedUntilFilterBottomSheet` on click.

2. `getSnoozedUntilChipText()` — returns text based on current `filterState.snoozedUntilFilter`:
   - ALL → "Snoozed Until"
   - BEFORE + preset/custom → "Snoozed ≤ {formatted duration}"
   - AFTER + preset/custom → "Snoozed > {formatted duration}"
   - BEFORE + specific → "Snoozed ≤ {formatted date}"
   - AFTER + specific → "Snoozed > {formatted date}"

3. `showSnoozedUntilFilterBottomSheet()` — instantiates and shows the bottom sheet.

4. Fragment result listener in `setupFilterResultListeners()` for `SnoozedUntilFilterBottomSheet.REQUEST_KEY` — builds `SnoozedUntilFilterConfig` from result bundle, updates `filterState`, refreshes chips + fragment.

5. Update `updateFilterChipsForCurrentTab()`:

```kotlin
R.id.activeEventsFragment -> {
    addCalendarChip()
    addStatusChip()
    addTimeChip(TimeFilterBottomSheet.TabType.ACTIVE)
    addSnoozedUntilChip()  // NEW
}
```

---

### Phase 6: Verify Fragment Filtering

**Goal:** Ensure Active events fragment applies the snoozed until filter.

**Files to verify:**
- `ActiveEventsFragment.kt`

The fragment's `loadEvents()` calls `filterState.filterEvents()` which applies all `FilterType`s in its `apply` set. Adding `FilterType.SNOOZED_UNTIL` to the active tab's default apply set (done in Phase 2) should be sufficient. If the active tab currently uses an explicit set that doesn't include `SNOOZED_UNTIL`, update it.

---

## Files Summary

### New Files

| File | Phase | Purpose |
|------|-------|---------|
| `SnoozedUntilFilterBottomSheet.kt` | 4 | Bottom sheet with presets, toggle, custom pickers |
| `bottom_sheet_snoozed_until_filter.xml` | 4 | Bottom sheet layout |
| `SnoozedUntilPresetPreferenceX.kt` | 1 | Custom Settings preference for presets (or generalize existing) |

### Modified Files

| File | Phase | Changes |
|------|-------|---------|
| `Settings.kt` | 1 | `snoozedUntilPresetsRaw`, `snoozedUntilPresets` |
| `navigation_preferences.xml` | 1 | New "Active Events" category with preset preference |
| `FilterState.kt` | 2 | `SnoozedUntilFilterConfig`, `FilterDirection`, `SnoozedUntilFilterMode`, `FilterType.SNOOZED_UNTIL`, matching/serialization |
| `FilterStateTest.kt` | 3 | Tests for all matching modes, directions, serialization |
| `strings.xml` | 1, 4 | Settings strings, bottom sheet strings, chip text strings |
| `MainActivityModern.kt` | 5 | Chip, bottom sheet wiring, result listener |
| `ActiveEventsFragment.kt` | 6 | Include `SNOOZED_UNTIL` in filter apply set (if needed) |

### Unchanged Files

| File | Reason |
|------|--------|
| `TimeFilterBottomSheet.kt` | Separate filter, no changes |
| `UpcomingTimeFilterBottomSheet.kt` | Reference pattern only, no changes |
| `TimeIntervalPickerController.kt` | Reused as-is from bottom sheet |
| `dialog_interval_picker.xml` | Reused as-is |
| `dialog_date_picker.xml` | Reused as-is |
| `dialog_time_picker.xml` | Reused as-is |
| `DismissedEventsFragment.kt` | No snoozed until chip on Dismissed tab |
| `UpcomingEventsFragment.kt` | No snoozed until chip on Upcoming tab |

---

## Testing Strategy

### Unit Tests (Phase 3)

All in `FilterStateTest.kt` — Robolectric tests following existing patterns.

**SnoozedUntilFilterConfig.matches():**
- ALL mode — matches everything (snoozed and non-snoozed)
- Non-snoozed events (snoozedUntil == 0) excluded for all non-ALL modes
- PRESET + BEFORE — snoozedUntil <= now + duration
- PRESET + AFTER — snoozedUntil > now + duration
- CUSTOM_PERIOD — same threshold logic as PRESET
- SPECIFIC_TIME + BEFORE — snoozedUntil <= absolute timestamp
- SPECIFIC_TIME + AFTER — snoozedUntil > absolute timestamp
- Boundary: snoozedUntil == threshold → BEFORE matches, AFTER doesn't

**FilterState integration:**
- `matchesSnoozedUntil()` delegates correctly
- Default constructor has ALL mode
- `hasActiveFilters()` detects non-ALL snoozedUntilFilter

**Bundle serialization:**
- Round-trip each mode (ALL, PRESET, CUSTOM_PERIOD, SPECIFIC_TIME)
- Round-trip both directions (BEFORE, AFTER)
- Round-trip preserves `valueMillis`
- Null bundle returns default

**Settings presets (separate test or existing PreferenceUtils tests):**
- Parse `"12h, 1d, 3d, 7d, 4w"` correctly
- Fallback to defaults on invalid input
- Negative values filtered out

### Manual Testing Checklist

- [ ] "Snoozed Until" chip appears only on Active tab
- [ ] Chip does NOT appear on Upcoming or Dismissed tabs
- [ ] Tapping chip opens bottom sheet with toggle + presets + custom options
- [ ] Before/After toggle switches direction
- [ ] Preset radio buttons match configured intervals from Settings
- [ ] Selecting preset + Apply filters the event list
- [ ] "For a custom period..." opens duration picker dialog
- [ ] Entering custom duration and Apply filters correctly
- [ ] "Until a specific time and date..." opens DatePicker → TimePicker
- [ ] Picking a specific date/time and Apply filters correctly
- [ ] Non-snoozed events are hidden when filter is not "All"
- [ ] Chip text updates with direction symbol (≤ / >) and value
- [ ] "All" option clears the filter regardless of toggle state
- [ ] Switching tabs clears the snoozed until filter
- [ ] Filter survives app backgrounding (via `onSaveInstanceState`)
- [ ] Filter clears on app restart
- [ ] Changing presets in Settings → Navigation & UI → Active Events updates the bottom sheet
- [ ] Invalid presets in Settings fall back to defaults
- [ ] Combines correctly with Status, Time, and Calendar filters

---

## Edge Cases

| Scenario | Expected Behavior |
|----------|-------------------|
| No snoozed events, filter active | Empty list (all events filtered out) |
| Event snoozedUntil exactly at threshold | BEFORE matches (≤), AFTER doesn't (>) |
| Specific time in the past | Valid — shows events snoozed until before/after that past time |
| Custom period of 0 | Effectively `now` — BEFORE shows events waking up now or earlier, AFTER shows all future snoozes |
| All filters combined (Calendar + Status + Time + SnoozedUntil) | AND logic across all filter types |
| Filter set, then switch to Dismissed tab and back | Filter cleared (same as all other filters) |
| Presets changed in Settings while bottom sheet open | Bottom sheet reads presets on create — won't update mid-dialog, next open picks up changes |
| SPECIFIC_TIME mode + rotation | `valueMillis` (absolute timestamp) preserved via Bundle — still correct after rotation |
| PRESET/CUSTOM_PERIOD mode + time passes | Threshold is `now + duration` — recalculated each time `matches()` is called, so filter stays relative |

---

## Implementation Order

1. **Phase 1** — Settings infrastructure (presets + Active Events category)
2. **Phase 2** — `SnoozedUntilFilterConfig` + `FilterState` integration
3. **Phase 3** — Tests (run before UI work)
4. **Phase 4** — Bottom sheet UI (dynamic presets, toggle, custom pickers)
5. **Phase 5** — Wire up chip in `MainActivityModern`
6. **Phase 6** — Verify fragment filtering

Phases 1-3 are purely logic/settings/tests with no UI changes. Phase 4 is the bulk of the UI work. Phases 5-6 wire everything together.
