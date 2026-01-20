# Snooze All with Chip/Pill Filters

**Parent Doc:** [event_lookahead_milestone3_filter_pills.md](./event_lookahead_milestone3_filter_pills.md)  
**GitHub Issue:** [#215](https://github.com/williscool/CalendarNotification/issues/215)

## Overview

Make "Snooze All" and "Change All" actions respect the currently active filter pills, the same way they already respect search query.

## Current State

| Feature | Status |
|---------|--------|
| Filter Pills (Milestone 3) | ✅ Merged - Calendar, Status, Time filters via `FilterState` |
| Search + Snooze All | ✅ Works via `INTENT_SEARCH_QUERY` |
| Filter + Snooze All | ❌ Not implemented (this feature) |
| Dismiss All filtering | ❌ Not supported (hardcoded criteria only) |
| Mute All filtering | ❌ Not supported (hardcoded criteria only) |

### Key Insight: Event Count is Already Correct

The count passed to `SnoozeAllActivity` already reflects both FilterState AND search:
1. Fragment applies `FilterState` filters → passes to adapter as `allEvents`
2. Adapter applies search filter → stores as `events`
3. `getDisplayedEventCount()` → `adapter.itemCount` → `events.size`

So we just need to make the actual snooze operation use the same filters.

## Goal

When filters are active, "Snooze All" should:
1. Only snooze events matching the current FilterState + search query
2. Show clear messaging about what will be snoozed:
   - "5 events matching Snoozed filter will be snoozed"
   - "3 events matching 'meeting', Snoozed, Muted filters will be snoozed"

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Scope | **Snooze All only** | Dismiss All and Mute All don't support any filtering currently |
| Legacy UI | **Skip** | Legacy UI in maintenance mode; new features modern-only |
| Serialization | **Bundle** | Same pattern used for savedInstanceState in MainActivityModern |

---

## Implementation Phases

### Phase 1: FilterState Bundle Serialization

**Goal:** Enable passing `FilterState` through Intent extras.

**File:** `FilterState.kt`

Add companion object methods:

```kotlin
companion object {
    private const val BUNDLE_CALENDAR_IDS = "filter_calendar_ids"
    private const val BUNDLE_CALENDAR_NULL = "filter_calendar_null"
    private const val BUNDLE_STATUS_FILTERS = "filter_status"
    private const val BUNDLE_TIME_FILTER = "filter_time"
    
    fun fromBundle(bundle: Bundle?): FilterState {
        if (bundle == null) return FilterState()
        
        val calendarIds: Set<Long>? = if (bundle.getBoolean(BUNDLE_CALENDAR_NULL, false)) {
            null
        } else {
            bundle.getLongArray(BUNDLE_CALENDAR_IDS)?.toSet()
        }
        
        val statusFilters = bundle.getIntArray(BUNDLE_STATUS_FILTERS)
            ?.mapNotNull { StatusOption.entries.getOrNull(it) }
            ?.toSet() ?: emptySet()
        
        val timeFilter = TimeFilter.entries.getOrNull(
            bundle.getInt(BUNDLE_TIME_FILTER, 0)
        ) ?: TimeFilter.ALL
        
        return FilterState(
            selectedCalendarIds = calendarIds,
            statusFilters = statusFilters,
            timeFilter = timeFilter
        )
    }
}

fun toBundle(): Bundle = Bundle().apply {
    selectedCalendarIds?.let { 
        putLongArray(BUNDLE_CALENDAR_IDS, it.toLongArray())
    } ?: putBoolean(BUNDLE_CALENDAR_NULL, true)
    
    putIntArray(BUNDLE_STATUS_FILTERS, statusFilters.map { it.ordinal }.toIntArray())
    putInt(BUNDLE_TIME_FILTER, timeFilter.ordinal)
}
```

### Phase 2: Filter Description Helper

**Goal:** Generate human-readable filter description for UI.

**File:** `FilterState.kt`

Add extension function:

```kotlin
/**
 * Generate human-readable description of active filters for UI display.
 * Returns null if no filters are active.
 */
fun FilterState.toDisplayString(context: Context): String? {
    val parts = mutableListOf<String>()
    
    // Calendar filter
    if (selectedCalendarIds != null) {
        val count = selectedCalendarIds.size
        if (count > 0) {
            parts.add(context.resources.getQuantityString(
                R.plurals.filter_calendar_summary, count, count
            ))
        }
    }
    
    // Status filters (show individual names)
    if (statusFilters.isNotEmpty()) {
        val names = statusFilters.map { option ->
            when (option) {
                StatusOption.SNOOZED -> context.getString(R.string.filter_status_snoozed)
                StatusOption.ACTIVE -> context.getString(R.string.filter_status_active)
                StatusOption.MUTED -> context.getString(R.string.filter_status_muted)
                StatusOption.RECURRING -> context.getString(R.string.filter_status_recurring)
            }
        }
        parts.add(names.joinToString(", "))
    }
    
    // Time filter
    if (timeFilter != TimeFilter.ALL) {
        val timeStr = when (timeFilter) {
            TimeFilter.STARTED_TODAY -> context.getString(R.string.filter_time_started_today)
            TimeFilter.STARTED_THIS_WEEK -> context.getString(R.string.filter_time_started_this_week)
            TimeFilter.PAST -> context.getString(R.string.filter_time_past)
            TimeFilter.STARTED_THIS_MONTH -> context.getString(R.string.filter_time_started_this_month)
            else -> null
        }
        timeStr?.let { parts.add(it) }
    }
    
    return if (parts.isEmpty()) null else parts.joinToString(", ")
}

/** Check if any filters are active */
fun FilterState.hasActiveFilters(): Boolean {
    return selectedCalendarIds != null || 
           statusFilters.isNotEmpty() || 
           timeFilter != TimeFilter.ALL
}
```

### Phase 3: Extend ApplicationController

**Goal:** Accept `FilterState` in snooze operations.

**File:** `ApplicationController.kt`

Update `snoozeAllEvents()`:

```kotlin
fun snoozeAllEvents(
    context: Context, 
    snoozeDelay: Long, 
    isChange: Boolean, 
    onlySnoozeVisible: Boolean, 
    searchQuery: String? = null,
    filterState: FilterState? = null
): SnoozeResult? {
    val now = clock.currentTimeMillis()
    return snoozeEvents(context, { event ->
        // Search query filter (existing behavior)
        val matchesSearch = searchQuery?.let { query ->
            event.title.contains(query, ignoreCase = true) ||
            event.desc.contains(query, ignoreCase = true)
        } ?: true
        
        // FilterState filters (new)
        val matchesFilter = filterState?.let { filter ->
            filter.matchesCalendar(event) &&
            filter.matchesStatus(event) &&
            filter.matchesTime(event, now)
        } ?: true
        
        matchesSearch && matchesFilter
    }, snoozeDelay, isChange, onlySnoozeVisible)
}
```

### Phase 4: Add Intent Constant

**File:** `Consts.kt`

```kotlin
const val INTENT_FILTER_STATE = "filter_state"
```

### Phase 5: Update SnoozeAllActivity

**Goal:** Accept FilterState from Intent and display filter info.

**File:** `SnoozeAllActivity.kt`

Changes:

1. Add field:
```kotlin
private var filterState: FilterState? = null
```

2. In `onCreate()`, read filter state:
```kotlin
filterState = FilterState.fromBundle(intent.getBundleExtra(Consts.INTENT_FILTER_STATE))
```

3. Update `snooze_count_text` display logic:
```kotlin
val snoozeCountTextView = findViewById<TextView>(R.id.snooze_count_text)

val filterDescription = filterState?.toDisplayString(this)
val hasSearch = !searchQuery.isNullOrEmpty()
val hasFilter = filterDescription != null

when {
    hasSearch && hasFilter -> {
        // Both search and filters: "3 events matching 'query', Filter1, Filter2 will be snoozed"
        val combined = getString(R.string.filter_description_search_and_filters, searchQuery, filterDescription)
        snoozeCountTextView.visibility = View.VISIBLE
        snoozeCountTextView.text = resources.getQuantityString(
            R.plurals.snooze_count_text_filtered, count, count, combined
        )
    }
    hasSearch -> {
        // Search only (existing behavior)
        snoozeCountTextView.visibility = View.VISIBLE
        snoozeCountTextView.text = resources.getQuantityString(
            R.plurals.snooze_count_text, count, count, searchQuery
        )
    }
    hasFilter -> {
        // Filters only
        snoozeCountTextView.visibility = View.VISIBLE
        snoozeCountTextView.text = resources.getQuantityString(
            R.plurals.snooze_count_text_filtered, count, count, filterDescription
        )
    }
    else -> {
        snoozeCountTextView.visibility = View.GONE
    }
}
```

4. Update `snoozeEvent()` to pass filterState:
```kotlin
private fun snoozeEvent(snoozeDelay: Long) {
    AlertDialog.Builder(this)
        .setMessage(getConfirmationMessage())
        .setCancelable(false)
        .setPositiveButton(android.R.string.yes) { _, _ ->
            val result = ApplicationController.snoozeAllEvents(
                this, 
                snoozeDelay, 
                snoozeAllIsChange, 
                false, 
                searchQuery,
                filterState  // NEW
            )
            result?.toast(this)
            finish()
        }
        .setNegativeButton(R.string.cancel) { _, _ -> }
        .create()
        .show()
}

private fun getConfirmationMessage(): String {
    val filterDescription = filterState?.toDisplayString(this)
    val hasSearch = !searchQuery.isNullOrEmpty()
    val hasFilter = filterDescription != null
    
    return when {
        hasSearch || hasFilter -> {
            val combined = when {
                hasSearch && hasFilter -> getString(R.string.filter_description_search_and_filters, searchQuery, filterDescription)
                hasSearch -> "\"$searchQuery\""
                else -> filterDescription!!
            }
            if (snoozeAllIsChange)
                getString(R.string.change_filtered_with_filter_confirmation, combined)
            else
                getString(R.string.snooze_filtered_with_filter_confirmation, combined)
        }
        snoozeAllIsChange -> getString(R.string.change_all_notification)
        else -> getString(R.string.snooze_all_confirmation)
    }
}
```

### Phase 6: Update MainActivityModern

**Goal:** Pass current `FilterState` when launching SnoozeAllActivity.

**File:** `MainActivityModern.kt`

Update `onOptionsItemSelected()`:

```kotlin
R.id.action_snooze_all -> {
    val fragment = getCurrentSearchableFragment()
    val isChange = fragment?.hasActiveEvents() != true
    val searchQuery = fragment?.getSearchQuery()
    val eventCount = fragment?.getDisplayedEventCount() ?: 0
    val currentFilterState = getCurrentFilterState()

    startActivity(
        Intent(this, SnoozeAllActivity::class.java)
            .putExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, isChange)
            .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
            .putExtra(Consts.INTENT_SEARCH_QUERY, searchQuery)
            .putExtra(Consts.INTENT_SEARCH_QUERY_EVENT_COUNT, eventCount)
            .putExtra(Consts.INTENT_FILTER_STATE, currentFilterState.toBundle())  // NEW
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    )
}
```

### Phase 7: String Resources

**File:** `strings.xml`

```xml
<!-- Filter description for snooze count (new - supports any filter combination) -->
<plurals name="snooze_count_text_filtered">
    <item quantity="one">%1$d event matching %2$s will be snoozed</item>
    <item quantity="other">%1$d events matching %2$s will be snoozed</item>
</plurals>

<!-- Filter combination descriptions -->
<string name="filter_description_search_and_filters">\"%1$s\", %2$s</string>

<!-- Calendar filter summary for snooze message -->
<plurals name="filter_calendar_summary">
    <item quantity="one">%1$d calendar</item>
    <item quantity="other">%1$d calendars</item>
</plurals>

<!-- Confirmation dialogs with filters -->
<string name="snooze_filtered_with_filter_confirmation">Snooze all events matching %1$s?</string>
<string name="change_filtered_with_filter_confirmation">Change snooze time for all events matching %1$s?\nAlready snoozed would also change unless snoozed to longer period</string>
```

---

## Files Summary

### Modified Files

| File | Changes |
|------|---------|
| `FilterState.kt` | Add `toBundle()`, `fromBundle()`, `toDisplayString()`, `hasActiveFilters()` |
| `ApplicationController.kt` | Add `filterState` parameter to `snoozeAllEvents()` |
| `SnoozeAllActivity.kt` | Accept FilterState, update UI messages and confirmation |
| `MainActivityModern.kt` | Pass FilterState when launching SnoozeAllActivity |
| `Consts.kt` | Add `INTENT_FILTER_STATE` constant |
| `strings.xml` | Add filter description strings |

### Unchanged Files

| File | Reason |
|------|--------|
| `MainActivityLegacy.kt` | Legacy UI in maintenance mode, skip new features |
| Dismiss All / Mute All | Don't support any filtering, out of scope |

---

## Testing Strategy

### Unit Tests (Robolectric)

```kotlin
// FilterStateBundleTest.kt
@Test fun `toBundle and fromBundle round-trip preserves all fields`()
@Test fun `fromBundle with null returns default FilterState`()
@Test fun `fromBundle with partial data uses defaults for missing`()

// FilterStateDisplayStringTest.kt
@Test fun `toDisplayString returns null when no filters active`()
@Test fun `toDisplayString shows calendar count when calendars filtered`()
@Test fun `toDisplayString shows status names when status filtered`()
@Test fun `toDisplayString shows time filter when time filtered`()
@Test fun `toDisplayString combines multiple filter types with commas`()

// ApplicationControllerSnoozeFilterTest.kt
@Test fun `snoozeAllEvents with filterState only snoozes matching events`()
@Test fun `snoozeAllEvents with search and filter combines both predicates`()
@Test fun `snoozeAllEvents with null filterState snoozes all (existing behavior)`()
```

### Manual Testing Checklist

- [ ] Apply Calendar filter → Snooze All shows "N calendars" in count text
- [ ] Apply single Status filter → Snooze All shows filter name
- [ ] Apply multiple Status filters → Combined names shown
- [ ] Apply Time filter → Snooze All shows time filter name
- [ ] Apply multiple filter types → All shown comma-separated
- [ ] Search + Filter → Both search and filters shown
- [ ] No filter/search → Count text hidden (original behavior)
- [ ] Confirmation dialog shows correct filter description
- [ ] **Actual snooze only affects filtered events** (verify in storage)
- [ ] Filter count matches what gets snoozed
- [ ] Legacy UI unaffected (still works without filters)

---

## Implementation Order

1. **Phase 1** - FilterState `toBundle()`/`fromBundle()` (enables data passing)
2. **Phase 2** - `toDisplayString()` helper (needed for UI)
3. **Phase 4** - Add `INTENT_FILTER_STATE` constant
4. **Phase 3** - ApplicationController changes (core logic)
5. **Phase 7** - String resources (needed before UI changes)
6. **Phase 5** - SnoozeAllActivity changes (consume filter)
7. **Phase 6** - MainActivityModern changes (pass filter)

Each phase is independently testable.

---

## Edge Cases

| Scenario | Expected Behavior |
|----------|-------------------|
| FilterState with empty calendar set | Show "0 calendars" or treat as no filter? (empty = none selected = show nothing) |
| All filters active | Show all filter descriptions comma-separated |
| Very long filter description | May need truncation for UI (consider later if needed) |
| Filter applied but 0 events match | Count shows 0, snooze does nothing |
