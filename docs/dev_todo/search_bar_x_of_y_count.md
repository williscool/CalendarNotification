# Feature: "X of Y" Event Count in Search Bar

**GitHub Issue:** [#241](https://github.com/williscool/CalendarNotification/issues/241)  
**Parent Doc:** [event_lookahead_milestone3_filter_pills.md](./event_lookahead_milestone3_filter_pills.md)

## Overview

When filter pills (Calendar, Status, Time) reduce the event list, the search bar hint should show "X of Y" so the user knows they're viewing a subset.

## Current State

| Feature | Status |
|---------|--------|
| Search hint shows event count | ✅ `"Search 70 Active events..."` |
| Count reflects filter pills | ✅ Count already updates after filtering |
| User can tell it's a subset | ❌ No indication that 70 is a filtered view of a larger list |

### Current Flow

1. Fragment calls `filterState.filterEvents(db.eventsForDisplay, now)` → filtered events
2. Adapter stores filtered events as `allEvents`
3. `getEventCount()` returns `allEvents.size` (post-filter count)
4. `MainActivityModern.onCreateOptionsMenu()` uses that count for the hint
5. Hint: `"Search %d %s events..."` → `"Search 70 Active events..."`

The total (unfiltered) count is never tracked — it's discarded during filtering.

## Goal

When filter pills are active and reduce the count:

```
Search 70 of 200 Active events...
```

When no filters are active (or filters match everything):

```
Search 200 Active events...
```

The hint is only visible when the search box is empty (Android replaces it with typed text), so text-search filtering is irrelevant.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Text format | `"Search X of Y Tab events..."` | Clear, concise, standard "X of Y" pattern |
| When to show "of Y" | Only when `hasActiveFilters() && filtered != total` | Don't show "70 of 70" when filters are active but match everything |
| What counts as "total" | Unfiltered DB count for the tab | The number before any filter pills are applied |
| Text search interaction | N/A | Hint is not visible while typing — only filter pills matter |
| Plural key | Keys off filtered count (`X`) | "Search 1 of 200 Active event..." vs "Search 5 of 200 Active events..." |

## Implementation

### Phase 1: Track Total (Unfiltered) Event Count

**Goal:** Each fragment remembers the total event count before filtering.

**Files:**
- `SearchableFragment.kt` — add `getTotalEventCount()` with default impl
- `ActiveEventsFragment.kt` — track and expose total count
- `UpcomingEventsFragment.kt` — track and expose total count  
- `DismissedEventsFragment.kt` — track and expose total count

**SearchableFragment.kt** — new method:

```kotlin
/** Get total event count before filter pills (for "X of Y" hint) */
fun getTotalEventCount(): Int = getEventCount()
```

**Each fragment's `loadEvents()`** — capture total before filtering:

```kotlin
// ActiveEventsFragment.loadEvents() example
val allDbEvents = db.eventsForDisplay
totalEventCount = allDbEvents.size  // new field
val events = filterState.filterEvents(allDbEvents, now)
```

Add a `private var totalEventCount: Int = 0` field to each fragment and override `getTotalEventCount()`.

### Phase 2: Conditional Search Hint

**Goal:** Show "X of Y" format when filters reduce the count.

**File:** `MainActivityModern.kt` (lines ~256-263)

Replace:

```kotlin
val count = currentFragment?.getEventCount() ?: 0
searchView?.queryHint = resources.getQuantityString(
    R.plurals.search_placeholder, count, count, tabName)
```

With:

```kotlin
val count = currentFragment?.getEventCount() ?: 0
val totalCount = currentFragment?.getTotalEventCount() ?: count
val hasActiveFilters = getCurrentFilterState().hasActiveFilters()

searchView?.queryHint = if (hasActiveFilters && count != totalCount) {
    resources.getQuantityString(
        R.plurals.search_placeholder_filtered, count, count, totalCount, tabName)
} else {
    resources.getQuantityString(
        R.plurals.search_placeholder, count, count, tabName)
}
```

### Phase 3: String Resources

**File:** `values/strings.xml`

```xml
<plurals name="search_placeholder_filtered">
    <item quantity="one">Search %1$d of %3$d %4$s event...</item>
    <item quantity="other">Search %1$d of %3$d %4$s events...</item>
</plurals>
```

Note: `%2$d` is skipped intentionally — `getQuantityString(id, quantity, arg1, arg2, arg3)` uses `quantity` for plural selection and `%1$d`/`%3$d`/`%4$s` for formatting. Keeping arg order consistent: filtered count, total count, tab name.

**File:** `values-fr/strings.xml`

```xml
<plurals name="search_placeholder_filtered">
    <item quantity="one">Rechercher %1$d sur %3$d événement %4$s…</item>
    <item quantity="other">Rechercher %1$d sur %3$d événements %4$s…</item>
</plurals>
```

## Testing

### Unit/Instrumentation Tests

- Search hint shows standard format when no filters active
- Search hint shows "X of Y" format when filter pills reduce count
- Search hint shows standard format when filters are active but match all events (count == totalCount)
- `getTotalEventCount()` returns correct unfiltered count
- `getEventCount()` returns correct filtered count (existing behavior, regression check)

## Files Changed Summary

| File | Change |
|------|--------|
| `SearchableFragment.kt` | Add `getTotalEventCount()` default method |
| `ActiveEventsFragment.kt` | Track `totalEventCount`, override `getTotalEventCount()` |
| `UpcomingEventsFragment.kt` | Track `totalEventCount`, override `getTotalEventCount()` |
| `DismissedEventsFragment.kt` | Track `totalEventCount`, override `getTotalEventCount()` |
| `MainActivityModern.kt` | Conditional hint text in `onCreateOptionsMenu()` |
| `values/strings.xml` | Add `search_placeholder_filtered` plural |
| `values-fr/strings.xml` | Add French translation |
