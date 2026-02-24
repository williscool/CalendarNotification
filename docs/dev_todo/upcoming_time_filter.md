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
| Configurable intervals | **Snooze preset pattern** | Comma-separated string (e.g., `"4h, 8h, 12h, 24h"`) using `PreferenceUtils.parseSnoozePresets()` |
| Invalid config fallback | **Standard default list** | Same pattern as snooze presets: if parse fails, use defaults |
| Bottom sheet type | **New `UpcomingTimeFilterBottomSheet`** | Separate from existing `TimeFilterBottomSheet` — completely different behavior and options |
| Clear on tab switch? | **No** | Unlike other filters, this persists to Settings. It represents the user's preferred view window |

## How It's Different From Other Time Filters

| Aspect | Active/Dismissed Time Filter | Upcoming Time Filter |
|--------|------------------------------|----------------------|
| What it does | Filters already-loaded events by start time | Controls which events get fetched (lookahead window) |
| Persistence | In-memory, clears on tab switch & restart | Persists to Settings |
| Options | Started Today / This Week / Past / etc. | Day Boundary / 4h / 8h / 12h / 24h / 48h |
| Implementation | `FilterState.timeFilter` enum | Writes to `Settings.upcomingEventsMode` + `upcomingEventsFixedHours` |
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
- `"4 hours"` / `"8 hours"` / `"24 hours"` etc. when in fixed mode

### Bottom Sheet

```
┌─────────────────────────────────────────────────┐
│  Upcoming Window                           ✕   │
├─────────────────────────────────────────────────┤
│  ○  Day boundary (4 AM)                        │
│  ─────────────────────────────────              │
│  ○  4 hours                                    │
│  ●  8 hours                          ← current │
│  ○  12 hours                                   │
│  ○  24 hours                                   │
│  ○  48 hours                                   │
├─────────────────────────────────────────────────┤
│              [ APPLY ]                          │
└─────────────────────────────────────────────────┘
```

- Day boundary option shows the configured boundary hour (from Settings)
- Fixed hour options come from configurable presets (like snooze presets)
- A divider separates the day boundary option from fixed hour options
- Current selection is pre-checked

## Configurable Intervals (Snooze Preset Pattern)

### Settings Property

```kotlin
// In Settings.kt
private const val UPCOMING_TIME_PRESETS_KEY = "pref_upcoming_time_presets"
const val DEFAULT_UPCOMING_TIME_PRESETS = "4h, 8h, 12h, 24h, 48h"

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
                12 * Consts.HOUR_IN_MILLISECONDS,
                24 * Consts.HOUR_IN_MILLISECONDS,
                48 * Consts.HOUR_IN_MILLISECONDS
            )
        return ret
    }
```

### Settings UI

Reuse the `SnoozePresetPreferenceX` pattern — custom `DialogPreference` with EditText:

```xml
<!-- In navigation_preferences.xml, inside the upcoming events category -->
<com.github.quarck.calnotify.prefs.UpcomingTimePresetPreferenceX
    android:key="pref_upcoming_time_presets"
    android:title="@string/upcoming_time_presets_title"
    android:summary="@string/upcoming_time_presets_summary"
    android:defaultValue="4h, 8h, 12h, 24h, 48h" />
```

Either create a new `UpcomingTimePresetPreferenceX` (nearly identical to `SnoozePresetPreferenceX`), or make `SnoozePresetPreferenceX` more generic and reusable for both. The parsing is identical (`PreferenceUtils.parseSnoozePresets()`).

## Implementation Plan

### Phase 1: Settings Infrastructure

Add the configurable time presets to `Settings.kt`.

**Files to modify:**
- `Settings.kt` — Add `upcomingTimePresetsRaw`, `upcomingTimePresets`, and key/default constants

**Tests:**
- Parse valid presets (reuses existing `PreferenceUtils` tests, but verify integration)
- Fallback when presets are invalid
- Fallback when presets are empty

### Phase 2: Bottom Sheet UI

Create `UpcomingTimeFilterBottomSheet` — a new bottom sheet that:
1. Shows "Day boundary (X AM)" as the first radio option
2. Shows configurable fixed-hour interval options below a divider
3. Pre-selects the current lookahead mode
4. On Apply, writes to `Settings.upcomingEventsMode` and `Settings.upcomingEventsFixedHours`
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
<string name="upcoming_time_hours">%d hours</string>
<string name="upcoming_time_presets_title">Lookahead interval presets</string>
<string name="upcoming_time_presets_summary">Comma-separated intervals (e.g., 4h, 8h, 12h, 24h)</string>
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
The `upcomingEventsFixedHours` is already clamped to `1..48` by `Settings.kt`. If a preset would exceed this, it gets clamped. We should validate presets to ensure they make sense (e.g., filter out values > 48h or < 1h at display time, or clamp when writing to Settings).

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
| `Settings.kt` | 1 | Add `upcomingTimePresetsRaw`, `upcomingTimePresets` |
| `MainActivityModern.kt` | 3 | Add upcoming time chip, handle result, update chip text |
| `navigation_preferences.xml` | 4 | Add configurable preset preference |
| `strings.xml` | 2-4 | Add string resources |
| `event_lookahead_milestone3_filter_pills.md` | — | Move "Time filter for Upcoming tab" from Future to Planned/Done and link here |

### Not Modified (but involved)

| File | Reason |
|------|--------|
| `UpcomingEventsLookahead.kt` | Already reads from Settings — no changes needed |
| `UpcomingEventsProvider.kt` | Already uses `UpcomingEventsLookahead` — no changes needed |
| `UpcomingEventsFragment.kt` | Already reloads via `loadEvents()` — no changes needed (just needs to be triggered) |
| `FilterState.kt` | Not used — this filter writes to Settings, not FilterState |
| `TimeFilterBottomSheet.kt` | Not used — separate bottom sheet for Active/Dismissed tabs |

## Testing Strategy

### Unit Tests (Robolectric)

```
UpcomingTimeFilterTest.kt:
- upcomingTimePresets parses valid custom presets
- upcomingTimePresets falls back to defaults on invalid input
- upcomingTimePresets falls back to defaults on empty string
- upcomingTimePresets returns correct millisecond values
- selecting day boundary writes mode to Settings
- selecting fixed interval writes mode and hours to Settings
- chip text reflects day boundary mode
- chip text reflects fixed hours mode with correct value
- presets outside valid range are filtered/clamped
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

1. **Phase 1** — Settings infrastructure (smallest, enables testing)
2. **Phase 2** — Bottom sheet UI (core feature)
3. **Phase 3** — Wire up chip in MainActivityModern
4. **Phase 4** — Settings UI for custom presets (nice-to-have, can defer)
5. **Phase 5** — Verify integration (mostly verifying existing code paths work)

Phases 1-3 deliver the core feature. Phase 4 adds the "configurable like snooze presets" enhancement. Phase 5 is verification/polish.
