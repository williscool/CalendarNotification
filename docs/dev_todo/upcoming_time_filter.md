# Feature: Time Filter for Upcoming Tab

**GitHub Issue:** [#216](https://github.com/williscool/CalendarNotification/issues/216)  
**Parent Doc:** [events_view_lookahead.md](./events_view_lookahead.md)  
**Related:** [Milestone 3: Filter Pills](./event_lookahead_milestone3_filter_pills.md) (where this was deferred)

## Background

Milestone 3 added filter pills to all tabs but explicitly deferred the Time filter for the Upcoming tab:

> Time filter for Upcoming tab is deferred - it needs thoughtful integration with the existing lookahead settings (fixed hours vs. day boundary mode).

The Active and Dismissed tab Time filters are simple in-memory filters that operate *within* already-loaded events. The Upcoming tab is different: its "time filter" actually **controls the lookahead window** — it determines which events get fetched from MonitorStorage in the first place.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Filter persistence | **Persists to Settings** | Unlike other filter pills (in-memory), this controls the data query window. User explicitly said "changing this pill should be the same as changing the option in the settings UI" |
| Pill behavior | **Dual-purpose: mode + interval** | Shows Day Boundary as a special option, plus configurable fixed-hour intervals |
| Configurable intervals | **Snooze preset pattern** | Comma-separated string (e.g., `"4h, 8h, 1d, 3d, 1w"`) using `PreferenceUtils.parseSnoozePresets()` |
| Invalid config fallback | **Standard default list** | Same pattern as snooze presets: if parse fails, use defaults |
| Max lookahead | **30 days (scan window)** | `manualCalWatchScanWindow` = 30 days. MonitorStorage has no data beyond this. Values > 30d are clamped |
| Week unit support | **Add `w` to PreferenceUtils** | Currently only supports `s`, `m`, `h`, `d`. Need `w` (weeks) for natural presets like `1w` |
| Bottom sheet type | **New `UpcomingTimeFilterBottomSheet`** | Separate from existing `TimeFilterBottomSheet` — completely different behavior and options |
| Clear on tab switch? | **No** | Unlike other filters, this persists to Settings. It represents the user's preferred view window |

## How It's Different From Other Time Filters

| Aspect | Active/Dismissed Time Filter | Upcoming Time Filter |
|--------|------------------------------|----------------------|
| What it does | Filters already-loaded events by start time | Controls which events get fetched (lookahead window) |
| Persistence | In-memory, clears on tab switch & restart | Persists to Settings |
| Options | Started Today / This Week / Past / etc. | Day Boundary / 4h / 8h / 1d / 3d / 1w |
| Implementation | `FilterState.timeFilter` enum | Writes to `Settings.upcomingEventsMode` + `upcomingEventsFixedLookaheadMillis` |
| Shared with Settings UI | No | Yes — pill and Settings UI modify the same values |

## UI Vision

### Upcoming Time Filter Chip

```
┌────────────────────────────────────────────────────────────┐
│  [ Calendar ▼ ]  [ Status ▼ ]  [ 8 hours ▼ ]              │
└────────────────────────────────────────────────────────────┘
```

Chip text shows the current lookahead mode:
- `"Day boundary"` when in day boundary mode
- `"4 hours"` / `"8 hours"` / `"1 day"` / `"1 week"` etc. when in fixed mode
- Uses `PreferenceUtils.formatSnoozePreset()` for display (human-readable)

### Bottom Sheet

```
┌─────────────────────────────────────────────────┐
│  Upcoming Window                           ✕   │
├─────────────────────────────────────────────────┤
│  ○  Day boundary (4 AM)                        │
│  ─────────────────────────────────              │
│  ○  4 hours                                    │
│  ●  8 hours                          ← current │
│  ○  1 day                                      │
│  ○  3 days                                     │
│  ○  1 week                                     │
├─────────────────────────────────────────────────┤
│              [ APPLY ]                          │
└─────────────────────────────────────────────────┘
```

- Day boundary option shows the configured boundary hour (from Settings)
- Fixed hour options come from configurable presets (like snooze presets)
- A divider separates the day boundary option from fixed hour options
- Current selection is pre-checked

## Prerequisite: Add Week Unit to PreferenceUtils

The current `PreferenceUtils.parseSnoozePresets()` supports `s`, `m`, `h`, `d` but not weeks. The parser uses `str.takeLast(1)` (single character only), so multi-character units like `wk` won't work.

**Add `w` (week) support:**

```kotlin
// In PreferenceUtils.parseSnoozePresets()
when (unit) {
    "s" -> num
    "m" -> num * Consts.MINUTE_IN_SECONDS
    "h" -> num * Consts.HOUR_IN_SECONDS
    "d" -> num * Consts.DAY_IN_SECONDS
    "w" -> num * Consts.DAY_IN_SECONDS * 7  // NEW
    else -> throw Exception("Unknown unit $unit")
}

// In PreferenceUtils.formatSnoozePreset() — add week formatting
internal fun formatSnoozePreset(value: Long): String {
    val seconds = value / 1000L
    if (seconds % (3600L * 24 * 7) == 0L) {  // NEW - check weeks first
        val weeks = seconds / (3600L * 24 * 7)
        return "${weeks}w"
    }
    if (seconds % (3600L * 24) == 0L) { ... }
    // ... rest unchanged
}
```

This benefits both snooze presets (users could configure `1w` snooze) and upcoming time presets. Existing presets are unaffected since `w` is a new unit.

**Also add `WEEK_IN_SECONDS` to Consts if not present:**
```kotlin
const val WEEK_IN_SECONDS: Long = DAY_IN_SECONDS * 7
```

## Lookahead Storage Change: Hours → Milliseconds

The current `upcomingEventsFixedHours: Int` (clamped to 1..48) is too restrictive for presets like `3d` or `1w`. Rather than trying to convert everything to hours, store the lookahead value in **milliseconds** (same as snooze presets internally).

```kotlin
// NEW: replaces upcomingEventsFixedHours for the preset-based lookahead
private const val UPCOMING_FIXED_LOOKAHEAD_MILLIS_KEY = "upcoming_fixed_lookahead_millis"

val upcomingEventsFixedLookaheadMillis: Long
    get() {
        val raw = getLong(UPCOMING_FIXED_LOOKAHEAD_MILLIS_KEY, -1L)
        if (raw > 0) return raw.coerceAtMost(MAX_LOOKAHEAD_MILLIS)
        // Migration: if old fixedHours setting exists, convert it
        val legacyHours = upcomingEventsFixedHours
        return (legacyHours.toLong() * Consts.HOUR_IN_MILLISECONDS).coerceAtMost(MAX_LOOKAHEAD_MILLIS)
    }
    set(value) = setLong(UPCOMING_FIXED_LOOKAHEAD_MILLIS_KEY, value.coerceAtMost(MAX_LOOKAHEAD_MILLIS))

// Scan window cap — MonitorStorage only has data this far ahead
internal const val MAX_LOOKAHEAD_MILLIS = 30L * Consts.DAY_IN_MILLISECONDS
```

`UpcomingEventsLookahead.calculateFixedEndTime()` then uses `upcomingEventsFixedLookaheadMillis` directly instead of multiplying hours.

**Migration note:** The old `upcomingEventsFixedHours` property is kept for backward compat — if the new millis key has no value, we convert from the legacy hours value. The old ListPreference in Settings UI for fixed hours can be deprecated once the preset-based pill is in place.

## Configurable Intervals (Snooze Preset Pattern)

### Settings Property

```kotlin
// In Settings.kt
private const val UPCOMING_TIME_PRESETS_KEY = "pref_upcoming_time_presets"
const val DEFAULT_UPCOMING_TIME_PRESETS = "4h, 8h, 1d, 3d, 1w"

val upcomingTimePresetsRaw: String
    get() = getString(UPCOMING_TIME_PRESETS_KEY, DEFAULT_UPCOMING_TIME_PRESETS)

val upcomingTimePresets: LongArray
    get() {
        var ret = PreferenceUtils.parseSnoozePresets(upcomingTimePresetsRaw)
        if (ret == null)
            ret = PreferenceUtils.parseSnoozePresets(DEFAULT_UPCOMING_TIME_PRESETS)
        if (ret == null || ret.isEmpty())
            ret = longArrayOf(
                4 * Consts.HOUR_IN_MILLISECONDS,
                8 * Consts.HOUR_IN_MILLISECONDS,
                1 * Consts.DAY_IN_MILLISECONDS,
                3 * Consts.DAY_IN_MILLISECONDS,
                7 * Consts.DAY_IN_MILLISECONDS
            )
        // Filter out negative values and cap at scan window
        return ret.filter { it > 0 && it <= MAX_LOOKAHEAD_MILLIS }.toLongArray()
    }
```

### Scan Window Constraint

`Settings.manualCalWatchScanWindow` = 30 days. MonitorStorage won't have alerts beyond this, so:
- Presets > 30 days are filtered out at display time
- `upcomingEventsFixedLookaheadMillis` is clamped to `MAX_LOOKAHEAD_MILLIS` (30 days)
- The dialog help text mentions the 30-day limit

### Settings UI

Reuse the `SnoozePresetPreferenceX` pattern — custom `DialogPreference` with EditText:

```xml
<!-- In navigation_preferences.xml, inside the upcoming events category -->
<com.github.quarck.calnotify.prefs.UpcomingTimePresetPreferenceX
    android:key="pref_upcoming_time_presets"
    android:title="@string/upcoming_time_presets_title"
    android:summary="@string/upcoming_time_presets_summary"
    android:defaultValue="4h, 8h, 1d, 3d, 1w" />
```

Either create a new `UpcomingTimePresetPreferenceX` (nearly identical to `SnoozePresetPreferenceX`), or make `SnoozePresetPreferenceX` more generic and reusable for both. The parsing is identical (`PreferenceUtils.parseSnoozePresets()`).

**Key difference from snooze preset dialog:** No negative values allowed. The help text should say:

```xml
<string name="dialog_upcoming_time_presets_label">Comma-separated list of lookahead intervals\n\nSupported values like \'4h\', \'1d\' or \'1w\'\n\nMaximum 30 days (calendar scan limit)\n\nLeave empty to use defaults</string>
```

## Implementation Plan

### Phase 0: Add Week Unit to PreferenceUtils

Small, self-contained change that benefits both features.

**Files to modify:**
- `PreferenceUtils.kt` — Add `"w"` case to `parseSnoozePresets()`, add week check to `formatSnoozePreset()`
- `Consts.kt` — Add `WEEK_IN_SECONDS` if not present

**Tests:**
- `parseSnoozePresets("1w")` returns 7 days in millis
- `parseSnoozePresets("2w")` returns 14 days in millis
- `formatSnoozePreset(7 * DAY_IN_MILLISECONDS)` returns `"1w"`
- Existing snooze preset tests still pass (no regression)

### Phase 1: Settings Infrastructure

Add the configurable time presets and milliseconds-based lookahead to `Settings.kt`.

**Files to modify:**
- `Settings.kt` — Add `upcomingTimePresetsRaw`, `upcomingTimePresets`, `upcomingEventsFixedLookaheadMillis`, `MAX_LOOKAHEAD_MILLIS`
- `UpcomingEventsLookahead.kt` — Use `upcomingEventsFixedLookaheadMillis` instead of `upcomingEventsFixedHours * HOUR_IN_MILLISECONDS`

**Tests:**
- Parse valid presets including `w` unit
- Fallback when presets are invalid
- Fallback when presets are empty
- Negative values filtered out
- Values > 30 days filtered out
- Legacy migration: old `fixedHours=8` converts to `8 * HOUR_IN_MILLIS`

### Phase 2: Bottom Sheet UI

Create `UpcomingTimeFilterBottomSheet` — a new bottom sheet that:
1. Shows "Day boundary (X AM)" as the first radio option
2. Shows configurable fixed interval options below a divider (from `upcomingTimePresets`)
3. Pre-selects the current lookahead mode
4. On Apply, writes to `Settings.upcomingEventsMode` and `Settings.upcomingEventsFixedLookaheadMillis`
5. Returns result via Fragment Result API (like existing `TimeFilterBottomSheet`)

**New files:**
- `UpcomingTimeFilterBottomSheet.kt` — The bottom sheet fragment
- `layout/bottom_sheet_upcoming_time_filter.xml` — Layout (or generate dynamically since options are configurable)

**Design note:** Since the fixed-hour options are configurable, the radio buttons should be generated dynamically rather than using a static XML layout. The layout needs:
- A header row ("Upcoming Window" + close button)
- A `RadioGroup` that gets populated programmatically
- An Apply button

### Phase 3: Wire Up the Chip

Add the time chip to the Upcoming tab in `MainActivityModern.updateFilterChipsForCurrentTab()`.

**Key behavior:**
- Chip text reflects current `Settings.upcomingEventsMode` and related values
- Tapping chip shows `UpcomingTimeFilterBottomSheet`
- On result: update Settings, refresh the Upcoming fragment
- Unlike other filter chips, this does NOT use `FilterState` — it writes directly to Settings

**Files to modify:**
- `MainActivityModern.kt` — Add `addUpcomingTimeChip()`, handle result, update `updateFilterChipsForCurrentTab()`

### Phase 4: Settings UI for Custom Presets

Add configurable preset preference to the settings screen.

**Files to modify/create:**
- `UpcomingTimePresetPreferenceX.kt` (or generalize `SnoozePresetPreferenceX`)
- `navigation_preferences.xml` — Add the preference entry
- `strings.xml` — Add string resources

### Phase 5: Integration with UpcomingEventsFragment

When the time filter changes (via pill or Settings UI), the Upcoming fragment needs to reload events with the new lookahead window. `UpcomingEventsProvider` already reads from `Settings` each time `getUpcomingEvents()` is called, so just triggering `loadEvents()` should work.

**Verify:**
- Changing the pill triggers `UpcomingEventsFragment.loadEvents()`
- Changing Settings UI triggers reload when returning to the Upcoming tab (already handled by `onResume`)
- Empty state message updates to reflect the new window

## String Resources

```xml
<!-- Upcoming time filter -->
<string name="upcoming_time_filter_title">Upcoming Window</string>
<string name="upcoming_time_day_boundary">Day boundary (%s)</string>
<string name="upcoming_time_presets_title">Lookahead interval presets</string>
<string name="upcoming_time_presets_summary">Comma-separated intervals (e.g., 4h, 8h, 1d, 3d, 1w)</string>
<string name="dialog_upcoming_time_presets_label">Comma-separated list of lookahead intervals\n\nSupported values like \'4h\', \'1d\' or \'1w\'\n\nMaximum 30 days (calendar scan limit)\n\nLeave empty to use defaults</string>
```

## Edge Cases

### Invalid Custom Presets
If user enters garbage in the Settings preset field, `PreferenceUtils.parseSnoozePresets()` returns null and we fall back to the default list. The bottom sheet always has valid options.

### Current Selection Not in Presets
If the user changes their presets and the currently active fixed hours value isn't in the new list:
- The bottom sheet shows all preset options, none pre-selected (or "Day boundary" selected as fallback)
- The current setting continues to work — `UpcomingEventsLookahead` doesn't care if the value came from a preset list

### Day Boundary Hour Changes
If the user changes their day boundary hour in Settings, the pill text and bottom sheet update automatically on next view (they read from Settings each time).

### Very Large Intervals
`upcomingEventsFixedLookaheadMillis` is clamped to `MAX_LOOKAHEAD_MILLIS` (30 days = `manualCalWatchScanWindow`). MonitorStorage has no data beyond this, so larger values would just show the same results as 30 days. The `upcomingTimePresets` getter filters out values > 30 days and negative values before they reach the bottom sheet.

### Negative Values
Unlike snooze presets (which support `-5m` for "5 minutes before event"), upcoming time presets must all be positive. Negative values are filtered out by the `upcomingTimePresets` getter. The dialog help text does not mention negative value support.

## Files Summary

### New Files

| File | Phase | Purpose |
|------|-------|---------|
| `UpcomingTimeFilterBottomSheet.kt` | 2 | Bottom sheet for upcoming time/lookahead selection |
| `bottom_sheet_upcoming_time_filter.xml` | 2 | Layout (minimal — dynamic radio buttons) |
| `UpcomingTimePresetPreferenceX.kt` | 4 | Custom preference for configuring presets (or generalize existing) |

### Modified Files

| File | Phase | Changes |
|------|-------|---------|
| `PreferenceUtils.kt` | 0 | Add `"w"` unit to parse/format |
| `Consts.kt` | 0 | Add `WEEK_IN_SECONDS` |
| `Settings.kt` | 1 | Add `upcomingTimePresetsRaw`, `upcomingTimePresets`, `upcomingEventsFixedLookaheadMillis`, `MAX_LOOKAHEAD_MILLIS` |
| `UpcomingEventsLookahead.kt` | 1 | Use `upcomingEventsFixedLookaheadMillis` instead of hours × millis |
| `MainActivityModern.kt` | 3 | Add upcoming time chip, handle result, update `updateFilterChipsForCurrentTab()` |
| `navigation_preferences.xml` | 4 | Add configurable preset preference |
| `strings.xml` | 0-4 | Add string resources |
| `event_lookahead_milestone3_filter_pills.md` | — | Already updated to link here |

### Not Modified (but involved)

| File | Reason |
|------|--------|
| `UpcomingEventsProvider.kt` | Already uses `UpcomingEventsLookahead` — no changes needed |
| `UpcomingEventsFragment.kt` | Already reloads via `loadEvents()` — no changes needed (just needs to be triggered) |
| `FilterState.kt` | Not used — this filter writes to Settings, not FilterState |
| `TimeFilterBottomSheet.kt` | Not used — separate bottom sheet for Active/Dismissed tabs |

## Testing Strategy

### Unit Tests (Robolectric)

```
PreferenceUtilsWeekUnitTest.kt (Phase 0):
- parseSnoozePresets "1w" returns 7 days in millis
- parseSnoozePresets "2w" returns 14 days in millis
- parseSnoozePresets mixed units "4h, 1d, 1w" parses correctly
- formatSnoozePreset(7 * DAY_IN_MILLIS) returns "1w"
- formatSnoozePreset(14 * DAY_IN_MILLIS) returns "2w"
- formatSnoozePreset(3 * DAY_IN_MILLIS) returns "3d" (not weeks)
- existing presets like "15m, 1h, 4h, 1d" still parse correctly

UpcomingTimeFilterTest.kt (Phase 1):
- upcomingTimePresets parses valid custom presets
- upcomingTimePresets falls back to defaults on invalid input
- upcomingTimePresets falls back to defaults on empty string
- upcomingTimePresets returns correct millisecond values
- upcomingTimePresets filters out negative values
- upcomingTimePresets filters out values > 30 days
- upcomingEventsFixedLookaheadMillis clamped to MAX_LOOKAHEAD_MILLIS
- legacy migration: old fixedHours=8 converts correctly
- selecting day boundary writes mode to Settings
- selecting fixed interval writes mode and millis to Settings
- chip text reflects day boundary mode
- chip text reflects fixed interval with correct human-readable text
```

### Manual Testing Checklist

- [ ] Upcoming tab shows time chip with current lookahead mode
- [ ] Tapping chip opens bottom sheet with Day Boundary + interval options
- [ ] Day boundary option shows configured boundary hour
- [ ] Selecting Day boundary and Apply switches lookahead mode
- [ ] Selecting a fixed interval and Apply switches mode + hours
- [ ] Event list refreshes after changing the lookahead window
- [ ] Chip text updates to reflect new selection
- [ ] Changing lookahead in Settings UI updates the chip on return
- [ ] Custom presets in Settings UI are reflected in the bottom sheet
- [ ] Invalid custom presets fall back to defaults
- [ ] Tab switch does NOT clear this filter (unlike other filters)
- [ ] Setting survives app restart

## Implementation Order

0. **Phase 0** — Add `w` (week) unit to `PreferenceUtils` (small, self-contained, benefits both features)
1. **Phase 1** — Settings infrastructure: presets, millis storage, lookahead update
2. **Phase 2** — Bottom sheet UI (core feature)
3. **Phase 3** — Wire up chip in MainActivityModern
4. **Phase 4** — Settings UI for custom presets (nice-to-have, can defer)
5. **Phase 5** — Verify integration (mostly verifying existing code paths work)

Phases 0-3 deliver the core feature. Phase 4 adds the "configurable like snooze presets" enhancement. Phase 5 is verification/polish.
