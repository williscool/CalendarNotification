# Snoozed Until Filter Pill / Chip

**Parent Doc:** [event_lookahead_milestone3_filter_pills.md](./event_lookahead_milestone3_filter_pills.md)  
**GitHub Issue:** [#255](https://github.com/williscool/CalendarNotification/issues/255)

## Overview

Add a "Snoozed Until" filter chip to the Active tab that works like the existing Time filter chip, but filters on `event.snoozedUntil` instead of `event.instanceStartTime`.

## Current State

| Feature | Status |
|---------|--------|
| Time filter chip (Active/Dismissed) | ✅ Filters on `instanceStartTime` / `instanceEndTime` |
| Status filter chip | ✅ Can filter to show only snoozed events |
| Snoozed Until filter chip | ❌ Not implemented (this feature) |

### Existing Pattern to Follow

The Time filter has a clean, well-tested pattern:

1. `TimeFilter` enum in `FilterState.kt` — filter options + `matches()` logic
2. `TimeFilterBottomSheet` — bottom sheet UI with radio buttons + Apply
3. `FilterState.timeFilter` field — holds the current selection
4. `FilterType.TIME` — enables/disables the filter in `filterEvents()`
5. `MainActivityModern.addTimeChip()` — creates the chip, wires up the bottom sheet
6. `FilterStateTest` — comprehensive tests for matching + serialization

The Snoozed Until filter follows this exact pattern.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Which tabs | **Active only** | Only active events can be snoozed (`snoozedUntil > 0`). Dismissed/Upcoming events don't have meaningful snooze times. |
| Non-snoozed events | **Excluded when filter active** | When filter is not ALL, events with `snoozedUntil == 0` don't match — they have no snooze time to filter on. |
| Tab-specific options | **No tab variation** | Unlike Time filter (which hides PAST on Dismissed, MONTH on Active), this only appears on Active so no tab logic needed. |
| Bottom sheet vs dropdown | **Bottom sheet** | Matches Time filter UX. Single-select with Apply button. |
| Interaction with Status filter | **Independent** | User can combine Status=Snoozed + SnoozedUntil=Today to see "events snoozed until today". Or use SnoozedUntil alone (implicitly shows only snoozed events since non-snoozed are excluded). |

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
│  ○  All                                         │
│  ○  Snoozed until today                         │
│  ○  Snoozed until this week                     │
│  ○  Snoozed until this month                    │
├─────────────────────────────────────────────────┤
│              [ APPLY ]                          │
└─────────────────────────────────────────────────┘
```

## Filter Definitions

### SnoozedUntilFilter Options

| Option | Logic | Notes |
|--------|-------|-------|
| ALL | No filter (default) | Shows all events regardless of snooze status |
| SNOOZED_UNTIL_TODAY | `snoozedUntil > 0 && isToday(snoozedUntil, now)` | Events waking up today |
| SNOOZED_UNTIL_THIS_WEEK | `snoozedUntil > 0 && isThisWeek(snoozedUntil, now)` | Events waking up this week |
| SNOOZED_UNTIL_THIS_MONTH | `snoozedUntil > 0 && isThisMonth(snoozedUntil, now)` | Events waking up this month |

All non-ALL options implicitly require `snoozedUntil > 0`, so non-snoozed events are excluded.

Uses the same `DateTimeUtils.isToday()`, `isThisWeek()`, `isThisMonth()` helpers that `TimeFilter` already uses.

---

## Implementation Phases

### Phase 1: SnoozedUntilFilter Enum + FilterState Integration

**Goal:** Add the filter logic and state management.

**Files to modify:**
- `FilterState.kt`

**Changes:**

1. Add `SNOOZED_UNTIL` to `FilterType` enum:

```kotlin
enum class FilterType {
    CALENDAR, STATUS, TIME, SNOOZED_UNTIL
}
```

2. Add `SnoozedUntilFilter` enum (parallel to `TimeFilter`):

```kotlin
enum class SnoozedUntilFilter {
    ALL,
    SNOOZED_UNTIL_TODAY,
    SNOOZED_UNTIL_THIS_WEEK,
    SNOOZED_UNTIL_THIS_MONTH;

    fun matches(event: EventAlertRecord, now: Long): Boolean {
        if (this == ALL) return true
        if (event.snoozedUntil == 0L) return false
        return when (this) {
            ALL -> true
            SNOOZED_UNTIL_TODAY -> DateTimeUtils.isToday(event.snoozedUntil, now)
            SNOOZED_UNTIL_THIS_WEEK -> DateTimeUtils.isThisWeek(event.snoozedUntil, now)
            SNOOZED_UNTIL_THIS_MONTH -> DateTimeUtils.isThisMonth(event.snoozedUntil, now)
        }
    }
}
```

3. Add `snoozedUntilFilter` field to `FilterState`:

```kotlin
data class FilterState(
    val selectedCalendarIds: Set<Long>? = null,
    val statusFilters: Set<StatusOption> = emptySet(),
    val timeFilter: TimeFilter = TimeFilter.ALL,
    val snoozedUntilFilter: SnoozedUntilFilter = SnoozedUntilFilter.ALL
)
```

4. Add `matchesSnoozedUntil()` method to `FilterState`:

```kotlin
fun matchesSnoozedUntil(event: EventAlertRecord, now: Long): Boolean {
    return snoozedUntilFilter.matches(event, now)
}
```

5. Update `filterEvents()` to include `SNOOZED_UNTIL`:

```kotlin
(FilterType.SNOOZED_UNTIL !in apply || matchesSnoozedUntil(event, now))
```

6. Update `hasActiveFilters()`:

```kotlin
fun hasActiveFilters(): Boolean {
    return selectedCalendarIds != null ||
           statusFilters.isNotEmpty() ||
           timeFilter != TimeFilter.ALL ||
           snoozedUntilFilter != SnoozedUntilFilter.ALL
}
```

7. Update `toDisplayString()` — add snoozed until section.

8. Update `toBundle()` / `fromBundle()` — add `BUNDLE_SNOOZED_UNTIL_FILTER` serialization (same pattern as `BUNDLE_TIME_FILTER`).

9. Update `filterEvents()` default `apply` set for active events to include `FilterType.SNOOZED_UNTIL`.

---

### Phase 2: Tests for SnoozedUntilFilter

**Goal:** Add tests before building the UI.

**Files to modify:**
- `FilterStateTest.kt`

**Tests to add** (mirroring existing `TimeFilter` tests):

```
SnoozedUntilFilter ALL matches all events
SnoozedUntilFilter ALL matches non-snoozed events
SnoozedUntilFilter SNOOZED_UNTIL_TODAY matches events snoozed until today
SnoozedUntilFilter SNOOZED_UNTIL_TODAY excludes non-snoozed events
SnoozedUntilFilter SNOOZED_UNTIL_THIS_WEEK matches events snoozed until this week
SnoozedUntilFilter SNOOZED_UNTIL_THIS_MONTH matches events snoozed until this month
FilterState matchesSnoozedUntil uses snoozedUntilFilter
FilterState default has ALL snoozedUntilFilter
toBundle and fromBundle round-trip snoozedUntilFilter
toBundle and fromBundle round-trip all snoozedUntilFilter values
hasActiveFilters returns true when snoozedUntilFilter is not ALL
toDisplayString shows snoozed until filter when not ALL
```

---

### Phase 3: Bottom Sheet UI

**Goal:** Create the bottom sheet for selecting snoozed until filter options.

**New files:**
- `SnoozedUntilFilterBottomSheet.kt` (parallel to `TimeFilterBottomSheet.kt`)
- `layout/bottom_sheet_snoozed_until_filter.xml` (parallel to `bottom_sheet_time_filter.xml`)

**Strings to add** (in `strings.xml`):

```xml
<string name="filter_snoozed_until">Snoozed Until</string>
<string name="filter_snoozed_until_all">All</string>
<string name="filter_snoozed_until_today">Snoozed until today</string>
<string name="filter_snoozed_until_this_week">Snoozed until this week</string>
<string name="filter_snoozed_until_this_month">Snoozed until this month</string>
```

The bottom sheet follows the exact same pattern as `TimeFilterBottomSheet`:
- `BottomSheetDialogFragment` with `RadioGroup`
- `Fragment Result API` for communicating selection back
- `REQUEST_KEY` / `RESULT_FILTER` constants

---

### Phase 4: Wire Up Chip in MainActivityModern

**Goal:** Add the chip to the Active tab and connect it to the bottom sheet.

**Files to modify:**
- `MainActivityModern.kt`

**Changes:**

1. Add `addSnoozedUntilChip()` method (parallel to `addTimeChip()`):
   - Creates chip with current filter text
   - Shows `SnoozedUntilFilterBottomSheet` on click

2. Add `getSnoozedUntilChipText()` method (parallel to `getTimeChipText()`).

3. Add `showSnoozedUntilFilterBottomSheet()` method.

4. Add fragment result listener in `setupFilterResultListeners()` for `SnoozedUntilFilterBottomSheet.REQUEST_KEY`.

5. Update `updateFilterChipsForCurrentTab()` — add `addSnoozedUntilChip()` to the Active tab case:

```kotlin
R.id.activeEventsFragment -> {
    addCalendarChip()
    addStatusChip()
    addTimeChip(TimeFilterBottomSheet.TabType.ACTIVE)
    addSnoozedUntilChip()  // NEW
}
```

---

### Phase 5: Apply Filter in Fragment

**Goal:** Active events fragment applies the snoozed until filter.

**Files to modify:**
- `ActiveEventsFragment.kt`

The fragment's `loadEvents()` already calls `filterState.filterEvents()` which applies all `FilterType`s in its `apply` set. Just need to add `FilterType.SNOOZED_UNTIL` to the active tab's default apply set (done in Phase 1).

---

## Files Summary

### New Files

| File | Phase | Purpose |
|------|-------|---------|
| `SnoozedUntilFilterBottomSheet.kt` | 3 | Bottom sheet for snoozed until filter |
| `bottom_sheet_snoozed_until_filter.xml` | 3 | Bottom sheet layout |

### Modified Files

| File | Phase | Changes |
|------|-------|---------|
| `FilterState.kt` | 1 | Add `SnoozedUntilFilter` enum, `FilterType.SNOOZED_UNTIL`, `FilterState.snoozedUntilFilter` field, matching/serialization |
| `FilterStateTest.kt` | 2 | Tests for SnoozedUntilFilter matching + serialization |
| `strings.xml` | 3 | Add snoozed until filter strings |
| `MainActivityModern.kt` | 4 | Add chip, bottom sheet wiring, result listener |
| `ActiveEventsFragment.kt` | 5 | Include `SNOOZED_UNTIL` in filter apply set (if not already default) |

### Unchanged Files

| File | Reason |
|------|--------|
| `TimeFilterBottomSheet.kt` | Separate filter, no changes needed |
| `DismissedEventsFragment.kt` | No snoozed until chip on Dismissed tab |
| `UpcomingEventsFragment.kt` | No snoozed until chip on Upcoming tab |
| `SnoozeAllActivity.kt` | Future work — see snooze_all_filter_pills.md |

---

## Testing Strategy

### Unit Tests (Phase 2)

All in `FilterStateTest.kt` — Robolectric tests matching the existing pattern:

- `SnoozedUntilFilter.matches()` for each enum value
- Non-snoozed event exclusion (snoozedUntil == 0)
- `FilterState.matchesSnoozedUntil()` delegation
- Bundle round-trip serialization
- `hasActiveFilters()` / `toDisplayString()` integration

### Manual Testing Checklist

- [ ] "Snoozed Until" chip appears only on Active tab
- [ ] Chip does NOT appear on Upcoming or Dismissed tabs
- [ ] Tapping chip opens bottom sheet with 4 radio options
- [ ] Selecting option + Apply filters the event list
- [ ] Non-snoozed events are hidden when filter is not "All"
- [ ] Chip text updates to reflect current selection
- [ ] Switching tabs clears the snoozed until filter
- [ ] Filter survives app backgrounding (via `onSaveInstanceState`)
- [ ] Filter clears on app restart
- [ ] Combines correctly with Status filter (e.g., Status=Snoozed + SnoozedUntil=Today)
- [ ] Combines correctly with Time filter and Calendar filter
- [ ] "Snoozed until today" correctly includes events snoozed until later today
- [ ] "Snoozed until this week" correctly uses locale-aware week boundaries

---

## Edge Cases

| Scenario | Expected Behavior |
|----------|-------------------|
| No snoozed events, filter set to "Today" | Empty list (all events filtered out) |
| Event snoozed until exactly midnight boundary | `isToday()` handles this correctly (same as Time filter) |
| Filter active, then event snooze expires | Event disappears from filtered list on next refresh |
| All filters combined (Calendar + Status + Time + SnoozedUntil) | AND logic across all filter types |
| Filter set, then switch to Dismissed tab and back | Filter cleared (same as all other filters) |

---

## Implementation Order

1. **Phase 1** — `SnoozedUntilFilter` enum + `FilterState` integration
2. **Phase 2** — Tests (run before UI work)
3. **Phase 3** — Bottom sheet UI + strings
4. **Phase 4** — Wire up chip in `MainActivityModern`
5. **Phase 5** — Verify fragment filtering works (may be zero changes if default apply set is updated in Phase 1)

Each phase is independently testable. Phases 1-2 are purely logic/tests with no UI changes.
