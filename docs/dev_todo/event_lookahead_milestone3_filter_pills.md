# Milestone 3: Filter Pills Implementation Plan

**Parent Doc:** [events_view_lookahead.md](./events_view_lookahead.md)  
**GitHub Issue:** [#92](https://github.com/williscool/CalendarNotification/issues/92)

## Overview

Milestone 3 adds filter pills (chips) to the event list tabs, allowing users to quickly filter events by status, time, and calendar. Inspired by the GitHub Android app's filter UI.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Filter persistence | **In-memory only** | Clear on app restart; survive foreground/background switching |
| Clear on nav switch | **Yes** | Same behavior as search - switching tabs clears filters |
| Interaction patterns | **Dropdown for simple, Bottom sheet for complex** | Popup menus for 3-5 options, bottom sheets for longer lists |
| Implementation order | **Simple filters first** | Status dropdown → Time bottom sheet → Calendar bottom sheet |

## UI Vision

### Filter Chip Row

Horizontally scrollable chip row below the toolbar, collapses on scroll:

```
┌────────────────────────────────────────────────────────────┐
│  [ Calendar ▼ ]  [ Status ▼ ]  [ Time ▼ ]     →           │
└────────────────────────────────────────────────────────────┘
```

- Chips scroll horizontally (ready for future expansion)
- Each chip shows dropdown arrow (▼)
- Active filters show filled chip style
- Collapses on scroll with toolbar via `AppBarLayout`

### Tab-Specific Filters

| Tab | Filter Chips |
|-----|--------------|
| **Active** | Calendar ▼, Status ▼, Time ▼ |
| **Upcoming** | Calendar ▼, Status ▼ |
| **Dismissed** | Calendar ▼, Time ▼ |

Chips change based on which tab is selected.

> **Note:** Time filter for Upcoming is deferred - it needs thoughtful integration with the existing lookahead settings (fixed hours vs. day boundary). See Future Enhancements.

---

## Filter Definitions

### Status Filter (Dropdown - Multi-select)

**Multi-select** popup menu with checkboxes (OR logic - event matches if it matches ANY selected option):

```
┌─────────────────────────┐
│  All                 ☑  │
│  Snoozed             ☐  │
│  Active              ☐  │
│  Muted               ☐  │
│  Recurring           ☐  │
└─────────────────────────┘
```

| Option | Logic |
|--------|-------|
| All | No filter (empty set = show all) |
| Snoozed | `event.snoozedUntil > 0` |
| Active | `event.snoozedUntil == 0` (not snoozed) |
| Muted | `event.isMuted == true` |
| Recurring | `event.isRepeating == true` |

**Tab-specific options:**
- **Active tab**: All options available
- **Upcoming tab**: Only Muted, Recurring (Snoozed/Active don't apply to unfired events)
- **Dismissed tab**: No Status filter

**Interaction:** `PopupMenu` anchored to chip, instant apply on each toggle. Selecting "All" clears other selections.

**Chip text:** Shows "Status" when no filter, single option name when one selected, "N selected" when multiple.

### Time Filter (Bottom Sheet)

**Single-select** filter by event start time. Options differ by tab:

**Active Tab:**
| Option | Logic |
|--------|-------|
| All | No filter (default) |
| Started today | `instanceStartTime` is today |
| Started this week | `instanceStartTime` within current calendar week |
| Past | `instanceEndTime < now` (event already ended) |

**Dismissed Tab:**
| Option | Logic |
|--------|-------|
| All | No filter (default) |
| Started today | `instanceStartTime` is today |
| Started this week | `instanceStartTime` within current calendar week |
| Started this month | `instanceStartTime` within current calendar month |

```
┌─────────────────────────────────────────────────┐
│  Filter by Time                            ✕   │
├─────────────────────────────────────────────────┤
│  ○  All                                         │
│  ○  Started today                               │
│  ○  Started this week                           │
│  ○  Past (ended)           ← Active only        │
│  ○  Started this month     ← Dismissed only     │
├─────────────────────────────────────────────────┤
│              [ APPLY ]                          │
└─────────────────────────────────────────────────┘
```

**Interaction:** `BottomSheetDialogFragment` with radio buttons, Apply button to confirm.

> **Note:** Time filter for Upcoming tab is deferred. See Future Enhancements.

### Calendar Filter (Bottom Sheet)

Multi-select bottom sheet with calendar colors:

```
┌─────────────────────────────────────────────────┐
│  Select Calendars                          ✕   │
│  🔍 Search calendars                           │
├─────────────────────────────────────────────────┤
│  ☑️  All Calendars                              │
├─────────────────────────────────────────────────┤
│  🟦 ☑️  Work Calendar                           │
│  🟩 ☑️  Personal                                │
│  🟧 ☐  Holidays (US)                           │
│  🟪 ☑️  Family Shared                           │
├─────────────────────────────────────────────────┤
│              [ APPLY ]                          │
└─────────────────────────────────────────────────┘
```

- Color indicator matching calendar colors
- Multi-select checkboxes
- "All Calendars" master toggle
- Search bar for users with many calendars
- Apply button to confirm

**Interaction:** `BottomSheetDialogFragment` with checkboxes.

---

## Filter State Management

### In-Memory Storage

Filters are stored in the Activity (not Settings/SharedPreferences):

```kotlin
// In MainActivityModern.kt
data class FilterState(
    val selectedCalendarIds: Set<Long> = emptySet(),  // empty = all calendars
    val statusFilters: Set<StatusOption> = emptySet(), // empty = show all (multi-select, OR logic)
    val timeFilter: TimeFilter = TimeFilter.ALL
) {
    /** Check if an event matches current status filters (empty set = match all) */
    fun matchesStatus(event: EventAlertRecord): Boolean {
        if (statusFilters.isEmpty()) return true
        return statusFilters.any { it.matches(event) }
    }
}

/** Individual status filter options. Multiple can be selected (OR logic). */
enum class StatusOption {
    SNOOZED, ACTIVE, MUTED, RECURRING;
    
    fun matches(event: EventAlertRecord): Boolean = when (this) {
        SNOOZED -> event.snoozedUntil > 0
        ACTIVE -> event.snoozedUntil == 0L
        MUTED -> event.isMuted
        RECURRING -> event.isRepeating
    }
}

enum class TimeFilter {
    ALL, STARTED_TODAY, STARTED_THIS_WEEK, PAST, STARTED_THIS_MONTH
}

// Current filter state - cleared on Activity recreation
private var filterState = FilterState()
```

### Clear on Tab Switch

When navigation destination changes, clear filters (same as search):

```kotlin
// In MainActivityModern.setupUI()
navController?.addOnDestinationChangedListener { _, destination, _ ->
    // ... existing title update code ...
    
    // Clear search when switching tabs
    searchView?.setQuery("", false)
    searchMenuItem?.collapseActionView()
    
    // Clear filters when switching tabs
    filterState = FilterState()
    updateFilterChips()
    
    // ... rest of existing code ...
}
```

### Communicate Filter to Fragments

Fragments get current filter state from activity:

```kotlin
// In MainActivityModern.kt
fun getCurrentFilterState(): FilterState = filterState

// In fragments
private fun getFilterState(): FilterState {
    return (activity as? MainActivityModern)?.getCurrentFilterState() ?: FilterState()
}
```

---

## Implementation Phases

### Phase 3.1: Filter Infrastructure + Chip Row Layout

**Goal:** Set up the chip row UI and filter state management.

#### 3.1.1 Add FilterState to MainActivityModern

```kotlin
// Add to MainActivityModern.kt

data class FilterState(
    val selectedCalendarIds: Set<Long> = emptySet(),
    val statusFilter: StatusFilter = StatusFilter.ALL,
    val timeFilter: TimeFilter = TimeFilter.ALL
)

enum class StatusFilter {
    ALL, SNOOZED, ACTIVE, MUTED, RECURRING;
    
    fun matches(event: EventAlertRecord): Boolean = when (this) {
        ALL -> true
        SNOOZED -> event.snoozedUntil > 0
        ACTIVE -> event.snoozedUntil == 0L
        MUTED -> event.isMuted
        RECURRING -> event.isRepeating
    }
}

enum class TimeFilter {
    ALL, TODAY, TOMORROW, NEXT_7_DAYS, THIS_WEEK, NEXT_30_DAYS;
    
    fun matches(event: EventAlertRecord, clock: CNPlusClockInterface): Boolean {
        if (this == ALL) return true
        val eventTime = event.instanceStartTime
        val now = clock.currentTimeMillis()
        // ... time range logic
    }
}
```

#### 3.1.2 Update activity_main.xml

Add filter chips container inside AppBarLayout:

```xml
<!-- After Toolbar, inside AppBarLayout -->
<HorizontalScrollView
    android:id="@+id/filter_chips_container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:scrollbars="none"
    android:background="?attr/colorPrimary"
    app:layout_scrollFlags="scroll|enterAlways|snap">

    <com.google.android.material.chip.ChipGroup
        android:id="@+id/filter_chips"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:paddingStart="16dp"
        android:paddingEnd="16dp"
        android:paddingTop="4dp"
        android:paddingBottom="8dp"
        app:singleLine="true"
        app:chipSpacingHorizontal="8dp" />

</HorizontalScrollView>
```

#### 3.1.3 Add String Resources

```xml
<!-- Filter chip labels -->
<string name="filter_calendar">Calendar</string>
<string name="filter_status">Status</string>
<string name="filter_time">Time</string>

<!-- Status filter options -->
<string name="filter_status_all">All</string>
<string name="filter_status_snoozed">Snoozed</string>
<string name="filter_status_active">Active</string>
<string name="filter_status_muted">Muted</string>
<string name="filter_status_recurring">Recurring</string>

<!-- Time filter options -->
<string name="filter_time_all">All</string>
<string name="filter_time_today">Today</string>
<string name="filter_time_tomorrow">Tomorrow</string>
<string name="filter_time_next_7_days">Next 7 days</string>
<string name="filter_time_this_week">This week</string>
<string name="filter_time_next_30_days">Next 30 days</string>

<!-- Calendar filter -->
<string name="filter_all_calendars">All Calendars</string>
<string name="filter_calendars_count">%d calendars</string>
<string name="select_calendars">Select Calendars</string>
<string name="search_calendars">Search calendars</string>
```

#### 3.1.4 Setup Filter Chips in MainActivityModern

```kotlin
private var filterState = FilterState()
private lateinit var chipGroup: ChipGroup

private fun setupFilterChips() {
    chipGroup = findViewById(R.id.filter_chips)
    updateFilterChipsForCurrentTab()
}

private fun updateFilterChipsForCurrentTab() {
    chipGroup.removeAllViews()
    
    val currentDestination = navController?.currentDestination?.id
    
    when (currentDestination) {
        R.id.activeEventsFragment -> {
            addCalendarChip()
            addStatusChip()
            addTimeChip()
        }
        R.id.upcomingEventsFragment -> {
            addCalendarChip()
            addStatusChip()
            addTimeChip()
        }
        R.id.dismissedEventsFragment -> {
            addCalendarChip()
        }
    }
}
```

**Files to modify:**
- `MainActivityModern.kt` - Add FilterState, chip setup
- `activity_main.xml` - Add chip row
- `strings.xml` - Add filter strings

---

### Phase 3.2: Status Filter (Dropdown)

**Goal:** Implement the Status filter as a simple dropdown popup.

#### 3.2.1 Create Status Chip with Popup

```kotlin
private fun addStatusChip() {
    val chip = Chip(this).apply {
        text = getStatusChipText()
        isCheckable = false
        chipIcon = null
        closeIcon = getDrawable(R.drawable.ic_arrow_drop_down)
        isCloseIconVisible = true
        setOnClickListener { showStatusFilterPopup(it) }
    }
    chipGroup.addView(chip)
}

private fun getStatusChipText(): String {
    return when (filterState.statusFilter) {
        StatusFilter.ALL -> getString(R.string.filter_status)
        StatusFilter.SNOOZED -> getString(R.string.filter_status_snoozed)
        StatusFilter.ACTIVE -> getString(R.string.filter_status_active)
        StatusFilter.MUTED -> getString(R.string.filter_status_muted)
        StatusFilter.RECURRING -> getString(R.string.filter_status_recurring)
    }
}

private fun showStatusFilterPopup(anchor: View) {
    PopupMenu(this, anchor).apply {
        menu.add(0, 0, 0, R.string.filter_status_all)
        menu.add(0, 1, 1, R.string.filter_status_snoozed)
        menu.add(0, 2, 2, R.string.filter_status_active)
        menu.add(0, 3, 3, R.string.filter_status_muted)
        menu.add(0, 4, 4, R.string.filter_status_recurring)
        
        // Check current selection
        menu.findItem(filterState.statusFilter.ordinal)?.isChecked = true
        menu.setGroupCheckable(0, true, true)
        
        setOnMenuItemClickListener { item ->
            filterState = filterState.copy(statusFilter = StatusFilter.entries[item.itemId])
            updateFilterChipsForCurrentTab()
            notifyCurrentFragmentFilterChanged()
            true
        }
        show()
    }
}
```

#### 3.2.2 Apply Filter in Fragments

Add to `SearchableFragment` interface:

```kotlin
interface SearchableFragment {
    // ... existing methods ...
    
    /** Called when filter state changes */
    fun onFilterChanged()
}
```

In `ActiveEventsFragment.loadEvents()`:

```kotlin
private fun loadEvents() {
    val ctx = context ?: return
    val filterState = getFilterState()
    
    background {
        val events = getEventsStorage(ctx).use { db ->
            db.eventsForDisplay
                .filter { filterState.statusFilter.matches(it) }
                .toTypedArray()
        }
        
        activity?.runOnUiThread {
            adapter.setEventsToDisplay(events)
            updateEmptyState()
            refreshLayout.isRefreshing = false
            activity?.invalidateOptionsMenu()
        }
    }
}

private fun getFilterState(): FilterState {
    return (activity as? MainActivityModern)?.getCurrentFilterState() ?: FilterState()
}

override fun onFilterChanged() {
    loadEvents()
}
```

**Files to modify:**
- `MainActivityModern.kt` - Add status chip and popup
- `SearchableFragment.kt` - Add `onFilterChanged()`
- `ActiveEventsFragment.kt` - Apply status filter
- `UpcomingEventsFragment.kt` - Apply status filter

---

### Phase 3.3: Time Filter (Bottom Sheet)

**Goal:** Implement the Time filter as a bottom sheet with radio options.

#### 3.3.1 Create TimeFilterBottomSheet

```kotlin
class TimeFilterBottomSheet : BottomSheetDialogFragment() {
    
    private var currentSelection: TimeFilter = TimeFilter.ALL
    private var onApply: ((TimeFilter) -> Unit)? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_time_filter, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val radioGroup = view.findViewById<RadioGroup>(R.id.time_filter_options)
        val applyButton = view.findViewById<Button>(R.id.btn_apply)
        val closeButton = view.findViewById<ImageButton>(R.id.btn_close)
        
        // Set current selection
        val selectedId = when (currentSelection) {
            TimeFilter.ALL -> R.id.option_all
            TimeFilter.TODAY -> R.id.option_today
            TimeFilter.TOMORROW -> R.id.option_tomorrow
            TimeFilter.NEXT_7_DAYS -> R.id.option_next_7_days
            TimeFilter.THIS_WEEK -> R.id.option_this_week
            TimeFilter.NEXT_30_DAYS -> R.id.option_next_30_days
        }
        radioGroup.check(selectedId)
        
        applyButton.setOnClickListener {
            val selected = when (radioGroup.checkedRadioButtonId) {
                R.id.option_all -> TimeFilter.ALL
                R.id.option_today -> TimeFilter.TODAY
                R.id.option_tomorrow -> TimeFilter.TOMORROW
                R.id.option_next_7_days -> TimeFilter.NEXT_7_DAYS
                R.id.option_this_week -> TimeFilter.THIS_WEEK
                R.id.option_next_30_days -> TimeFilter.NEXT_30_DAYS
                else -> TimeFilter.ALL
            }
            onApply?.invoke(selected)
            dismiss()
        }
        
        closeButton.setOnClickListener { dismiss() }
    }
    
    companion object {
        fun newInstance(
            current: TimeFilter,
            onApply: (TimeFilter) -> Unit
        ): TimeFilterBottomSheet {
            return TimeFilterBottomSheet().apply {
                this.currentSelection = current
                this.onApply = onApply
            }
        }
    }
}
```

#### 3.3.2 Create bottom_sheet_time_filter.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <!-- Header -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/filter_time"
            android:textAppearance="@style/TextAppearance.AppCompat.Title" />

        <ImageButton
            android:id="@+id/btn_close"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:src="@drawable/ic_clear_white_24dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/close" />

    </LinearLayout>

    <!-- Radio options -->
    <RadioGroup
        android:id="@+id/time_filter_options"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp">

        <RadioButton
            android:id="@+id/option_all"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="@string/filter_time_all" />

        <RadioButton
            android:id="@+id/option_today"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="@string/filter_time_today" />

        <RadioButton
            android:id="@+id/option_tomorrow"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="@string/filter_time_tomorrow" />

        <RadioButton
            android:id="@+id/option_next_7_days"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="@string/filter_time_next_7_days" />

        <RadioButton
            android:id="@+id/option_this_week"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="@string/filter_time_this_week" />

        <RadioButton
            android:id="@+id/option_next_30_days"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="@string/filter_time_next_30_days" />

    </RadioGroup>

    <!-- Apply button -->
    <Button
        android:id="@+id/btn_apply"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:text="@string/apply" />

</LinearLayout>
```

**New files:**
- `TimeFilterBottomSheet.kt`
- `layout/bottom_sheet_time_filter.xml`

**Files to modify:**
- `MainActivityModern.kt` - Add time chip and show bottom sheet
- `ActiveEventsFragment.kt` - Apply time filter
- `UpcomingEventsFragment.kt` - Apply time filter

---

### Phase 3.4: Calendar Filter (Bottom Sheet)

**Goal:** Implement the Calendar multi-select bottom sheet.

#### 3.4.1 Create CalendarFilterBottomSheet

```kotlin
class CalendarFilterBottomSheet : BottomSheetDialogFragment() {
    
    private lateinit var adapter: CalendarFilterAdapter
    private var selectedIds: Set<Long> = emptySet()
    private var onApply: ((Set<Long>) -> Unit)? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_calendar_filter, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.calendar_list)
        val searchView = view.findViewById<SearchView>(R.id.search_calendars)
        val allCalendarsCheckbox = view.findViewById<CheckBox>(R.id.checkbox_all_calendars)
        val applyButton = view.findViewById<Button>(R.id.btn_apply)
        val closeButton = view.findViewById<ImageButton>(R.id.btn_close)
        
        adapter = CalendarFilterAdapter(selectedIds) { newSelectedIds ->
            selectedIds = newSelectedIds
            updateAllCalendarsCheckbox(allCalendarsCheckbox)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        // Load calendars
        loadCalendars()
        
        // All calendars toggle
        allCalendarsCheckbox.isChecked = selectedIds.isEmpty()
        allCalendarsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedIds = emptySet()
                adapter.selectAll()
            }
        }
        
        // Search
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText)
                return true
            }
        })
        
        applyButton.setOnClickListener {
            onApply?.invoke(selectedIds)
            dismiss()
        }
        
        closeButton.setOnClickListener { dismiss() }
    }
    
    private fun loadCalendars() {
        val calendars = CalendarProvider.getCalendars(requireContext())
        adapter.setCalendars(calendars, selectedIds)
    }
    
    private fun updateAllCalendarsCheckbox(checkbox: CheckBox) {
        checkbox.isChecked = selectedIds.isEmpty()
    }
    
    companion object {
        fun newInstance(
            currentSelection: Set<Long>,
            onApply: (Set<Long>) -> Unit
        ): CalendarFilterBottomSheet {
            return CalendarFilterBottomSheet().apply {
                this.selectedIds = currentSelection
                this.onApply = onApply
            }
        }
    }
}
```

#### 3.4.2 Create CalendarFilterAdapter

```kotlin
class CalendarFilterAdapter(
    private var selectedIds: Set<Long>,
    private val onSelectionChanged: (Set<Long>) -> Unit
) : RecyclerView.Adapter<CalendarFilterAdapter.ViewHolder>() {
    
    private var allCalendars: List<CalendarRecord> = emptyList()
    private var filteredCalendars: List<CalendarRecord> = emptyList()
    
    fun setCalendars(calendars: List<CalendarRecord>, selected: Set<Long>) {
        allCalendars = calendars
        filteredCalendars = calendars
        selectedIds = selected
        notifyDataSetChanged()
    }
    
    fun filter(query: String?) {
        filteredCalendars = if (query.isNullOrBlank()) {
            allCalendars
        } else {
            allCalendars.filter { it.displayName.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }
    
    fun selectAll() {
        selectedIds = emptySet()  // empty = all selected
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_filter, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val calendar = filteredCalendars[position]
        holder.bind(calendar, isSelected(calendar.calendarId))
    }
    
    override fun getItemCount() = filteredCalendars.size
    
    private fun isSelected(calendarId: Long): Boolean {
        // Empty set means all calendars are selected
        return selectedIds.isEmpty() || calendarId in selectedIds
    }
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorIndicator: View = itemView.findViewById(R.id.color_indicator)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        private val nameText: TextView = itemView.findViewById(R.id.calendar_name)
        
        fun bind(calendar: CalendarRecord, isSelected: Boolean) {
            colorIndicator.backgroundTintList = ColorStateList.valueOf(calendar.color)
            nameText.text = calendar.displayName
            checkbox.isChecked = isSelected
            
            itemView.setOnClickListener {
                toggleSelection(calendar.calendarId)
            }
            checkbox.setOnClickListener {
                toggleSelection(calendar.calendarId)
            }
        }
        
        private fun toggleSelection(calendarId: Long) {
            selectedIds = if (selectedIds.isEmpty()) {
                // Was "all" - now deselect this one (select all others)
                allCalendars.map { it.calendarId }.toSet() - calendarId
            } else if (calendarId in selectedIds) {
                selectedIds - calendarId
            } else {
                selectedIds + calendarId
            }
            
            // If all calendars are now selected, switch back to empty set
            if (selectedIds.size == allCalendars.size) {
                selectedIds = emptySet()
            }
            
            onSelectionChanged(selectedIds)
            notifyDataSetChanged()
        }
    }
}
```

**New files:**
- `CalendarFilterBottomSheet.kt`
- `CalendarFilterAdapter.kt`
- `layout/bottom_sheet_calendar_filter.xml`
- `layout/item_calendar_filter.xml`

**Files to modify:**
- `MainActivityModern.kt` - Add calendar chip and show bottom sheet
- All fragments - Apply calendar filter

---

## Files Summary

### New Files

| File | Phase | Purpose |
|------|-------|---------|
| `TimeFilterBottomSheet.kt` | 3.3 | Bottom sheet for time filter |
| `CalendarFilterBottomSheet.kt` | 3.4 | Bottom sheet for calendar multi-select |
| `CalendarFilterAdapter.kt` | 3.4 | RecyclerView adapter for calendar list |
| `bottom_sheet_time_filter.xml` | 3.3 | Time filter bottom sheet layout |
| `bottom_sheet_calendar_filter.xml` | 3.4 | Calendar filter bottom sheet layout |
| `item_calendar_filter.xml` | 3.4 | Calendar list item layout |

### Modified Files

| File | Phase | Changes |
|------|-------|---------|
| `activity_main.xml` | 3.1 | Add filter chips container |
| `MainActivityModern.kt` | 3.1-3.4 | Add FilterState, chip setup, popup/sheet handling |
| `SearchableFragment.kt` | 3.2 | Add `onFilterChanged()` method |
| `ActiveEventsFragment.kt` | 3.2-3.4 | Apply all filters in `loadEvents()` |
| `UpcomingEventsFragment.kt` | 3.2-3.4 | Apply all filters in `loadEvents()` |
| `DismissedEventsFragment.kt` | 3.4 | Apply calendar filter in `loadEvents()` |
| `strings.xml` | 3.1 | Add filter-related strings |

---

## Testing Strategy

### Robolectric Tests

```kotlin
// FilterStateTest.kt
@Test fun `status filter ALL matches all events`()
@Test fun `status filter SNOOZED matches only snoozed events`()
@Test fun `status filter ACTIVE matches only non-snoozed events`()
@Test fun `status filter MUTED matches only muted events`()
@Test fun `status filter RECURRING matches only repeating events`()

@Test fun `time filter TODAY matches events starting today`()
@Test fun `time filter TOMORROW matches events starting tomorrow`()
// ... etc

@Test fun `calendar filter empty set matches all events`()
@Test fun `calendar filter with IDs matches only those calendars`()
```

### Manual Testing Checklist

- [ ] Filter chips appear below toolbar
- [ ] Chips scroll horizontally
- [ ] Chips collapse on scroll with toolbar
- [ ] Tapping Status chip shows dropdown popup
- [ ] Selecting status option updates chip text and filters list
- [ ] Tapping Time chip shows bottom sheet
- [ ] Selecting time option and Apply filters list
- [ ] Tapping Calendar chip shows bottom sheet with colors
- [ ] Multi-select works, Apply filters list
- [ ] "All Calendars" toggle works
- [ ] Search in calendar list works
- [ ] Switching tabs clears all filters
- [ ] Switching tabs updates available chips
- [ ] Filters survive app backgrounding
- [ ] Filters clear on app restart (cold boot)

---

## Implementation Order

1. **Phase 3.1** - Filter infrastructure + Chip row layout
2. **Phase 3.2** - Status filter (dropdown popup) ← Start here, quickest win
3. **Phase 3.3** - Time filter (bottom sheet)
4. **Phase 3.4** - Calendar filter (bottom sheet with multi-select)

Each phase is independently testable and deliverable.

---

## Future Enhancements

### planned
- **Snooze All / Dismiss All with filters** - Make bulk actions respect active filters (like they already do with search). When a filter is active, "Snooze All" should only snooze filtered events. Requires:
  - Passing FilterState to SnoozeAllActivity via Intent extras
  - Extending `ApplicationController.snoozeAllEvents()` to accept filter parameters
  - Updating `snooze_count_text` strings to show active filters, e.g.:
    - "5 events matching Snoozed, Muted filters will be snoozed"
    - "3 events matching 'meeting', Snoozed filter will be snoozed" (search + filter)
    - Pattern: `X event(s) matching [search], [filter1, filter2, ...] will be snoozed`
- **Time filter for Upcoming tab** - Needs thoughtful integration with existing lookahead settings (fixed hours vs. day boundary mode). Could filter within existing lookahead or override/extend it. Implement after Calendar filter to learn from those patterns.
- **Fix test activity calendar creation** - The test activity (`TestActivityCalendarEvents` or similar) creates a new calendar each time it runs instead of reusing an existing "Test Calendar". This leads to hundreds of duplicate calendars over time (observed 718 "Test Calendar" entries). Should check for existing test calendar by name before creating a new one.

### potential
- ***Configurable Time Filter** - the same way snooze presets work could allow configure these
- **Event match counts on chips** - Show how many events match the current filter combination on each chip (e.g., "Status (47)"). Requires fragment-to-activity communication pattern (callback, LiveData, or SharedFlow) since fragments own the filtered event list.
- **Save filter presets** - Remember commonly used filter combinations
- **Additional filters** - Event type, attendee, location
- **Quick clear** - "Clear all filters" chip or button