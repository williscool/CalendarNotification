# Events View Lookahead

## Background

**GitHub Issues:** [#92](https://github.com/williscool/CalendarNotification/issues/92), [#11](https://github.com/williscool/CalendarNotification/issues/11)

Users want to see upcoming calendar events in the main events list before their notifications fire, allowing them to:
- Pre-snooze events to a later time
- Plan their day by seeing what's coming
- Not be surprised by imminent events

Currently, events only appear in the list after their reminder fires. The app already scans up to 30 days ahead for scheduling purposes (`MonitorStorage`), so the data exists—it's just not exposed in the UI.

## Goal

Display upcoming (not yet fired) events with a modern, intuitive UI that includes:
- Bottom navigation for switching between Active, Upcoming, and Dismissed views
- Collapsing filter bar with calendar multi-select
- Clear visual distinction between event states
- Ability to pre-snooze upcoming events
- Configurable lookahead window

## Key Decisions Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Navigation pattern | **Navigation Component + Fragments** | Modern Android pattern, built-in back stack, type-safe args, testable |
| Bottom nav items | **3 items** (Active, Upcoming, Dismissed) | Settings in overflow menu; cleaner UI |
| Legacy UI support | **Feature flag toggle** | `Settings.useNewNavigationUI` allows rollback |
| Pre-muted storage | **`preMuted` column in MonitorStorage** | Atomic updates, no cleanup needed, survives restarts |
| Data source for upcoming | **MonitorStorage + CalendarProvider enrichment** | Reuse existing data, no duplication |
| Enrichment strategy | **Lazy loading with placeholders** | Fast initial render, progressive enhancement |
| Testing approach | **Robolectric primary, instrumentation for Calendar Provider** | Fast tests, real API tests where needed |

## UI Vision

**Inspiration:** GitHub Android app (filter pills) + Twitter/X (icon-only bottom nav, collapsing UI on scroll)

### Bottom Navigation (Icon-Only, Collapses on Scroll)

```
┌─────────────────────────────────────────────────┐
│       🔔            ⏰            🗑️            │
│     Active       Upcoming      Dismissed        │
└─────────────────────────────────────────────────┘
```

- **3 items only** - Active, Upcoming, Dismissed (Settings stays in overflow menu ⋮)
- **Icon-only** - no text labels (icons are self-explanatory)
- **Collapses on scroll** - hides when scrolling down, reappears on scroll up (like top bar)
- **Material BottomNavigationView** - standard Android component
- Use `HideBottomViewOnScrollBehavior` or custom `CoordinatorLayout.Behavior`

### Legacy UI Toggle

A settings preference allows users to switch between the new tabbed UI and the legacy single-list view:
- **Default:** New UI (bottom nav with tabs)
- **Legacy mode:** Original single RecyclerView, DismissedEventsActivity as separate screen
- Stored in `Settings.useNewNavigationUI` (boolean, default `true`)
- Allows smooth rollout and escape hatch if issues arise

### Top Filter Bar (Collapses on Scroll)

```
┌─────────────────────────────────────────────────┐
│  🔍   [ Calendar ▼ ]   [ other filters... ]    │
└─────────────────────────────────────────────────┘
```

- **Search icon** - always accessible
- **Filter chips** - horizontally scrollable pills
- **Collapses** when scrolling down, reappears on scroll up
- **CoordinatorLayout + AppBarLayout** for collapse behavior

### Calendar Multi-Select Dropdown

Tapping "Calendar ▼" chip opens a bottom sheet:

```
┌─────────────────────────────────────────────────┐
│  Select Calendars                          ✕   │
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

- **Color indicators** matching calendar colors
- **Multi-select** with checkboxes
- **"All Calendars"** master toggle
- **Persisted** in settings

### Dismissed Events as Tab

Moving `DismissedEventsActivity` into the main navigation:
- Becomes the 🗑️ tab in bottom nav
- Same data, just integrated
- Can eventually deprecate the separate activity

## Current Architecture

### Existing Components

| Component | Purpose | Data Structure |
|-----------|---------|----------------|
| `EventsStorage` | Stores fired/active events (shown in UI) | `EventAlertRecord` (full details) |
| `MonitorStorage` | Stores upcoming alerts for scheduling | `MonitorEventAlertEntry` (minimal: eventId, alertTime, instanceStartTime, instanceEndTime, isAllDay, wasHandled) |
| `DismissedEventsStorage` | Stores dismissed events (the "Bin") | `DismissedEventAlertRecord` |
| `CalendarProvider.getEvent()` | Fetches full event details from calendar | `EventRecord` |
| `CalendarProvider.getEventAlertsForInstancesInRange()` | Scans calendar for upcoming alerts | `List<MonitorEventAlertEntry>` |

### Key Insight

`MonitorStorage` already has upcoming events! We just need to:
1. Query it for events in the lookahead window
2. Enrich each with `CalendarProvider.getEvent()` for title, description, color, location
3. Display in the UI

## Design Decisions

### Storage Approach: Option A - Leverage MonitorStorage + Enrich

**Chosen approach:** No new storage. Query `MonitorStorage.getAlertsForAlertRange(now, cutoffTime)` and enrich on-the-fly with `CalendarProvider.getEvent()`.

**Rationale:**
- Reuses existing infrastructure
- Data already being scanned and stored
- Avoids duplication
- Can revisit with dedicated storage (Option B) if performance becomes an issue

### Lookahead Logic: "Next Morning Cutoff" with Rollover

Users want to see events happening "today" including late-night events that are technically tomorrow. The cutoff should be a morning time (default: 10am) that rolls over at midnight.

**Algorithm:**
```
if currentHour < cutoffHour:
    # After midnight but before cutoff - look until cutoff today
    cutoffTime = today at cutoffHour
else:
    # After cutoff - look until cutoff tomorrow
    cutoffTime = tomorrow at cutoffHour
```

**Examples (cutoff = 10am):**
| Current Time | Lookahead Until | Window |
|--------------|-----------------|--------|
| Mon 6:00 PM | Tue 10:00 AM | ~16 hours |
| Mon 11:00 PM | Tue 10:00 AM | ~11 hours |
| Tue 12:01 AM | Tue 10:00 AM | ~10 hours |
| Tue 9:00 AM | Tue 10:00 AM | ~1 hour |
| Tue 11:00 AM | Wed 10:00 AM | ~23 hours |

**Alternative mode:** Fixed hours (e.g., 8 hours from now) for users who prefer simpler behavior.

### UI Approach: Single-Activity with Navigation Component

**Chosen approach:** Modern Android navigation pattern with:
1. **Single-Activity Architecture** - MainActivity hosts all content via Fragments
2. **Navigation Component** - Centralized navigation graph (`nav_graph.xml`) manages all transitions
3. **BottomNavigationView + NavController** - `NavigationUI.setupWithNavController()` for seamless tab switching
4. **3-item bottom nav** - Active, Upcoming, Dismissed (Settings in overflow menu)
5. **Collapsing filter bar** with calendar multi-select via CoordinatorLayout + AppBarLayout
6. **Legacy UI toggle** - Feature flag to revert to old UI if needed

**Why Navigation Component over ViewSwitcher:**
- Standard modern Android pattern (recommended by Google)
- Built-in back stack management
- Type-safe argument passing with Safe Args
- Better lifecycle handling
- Easier to test with `TestNavHostController`

## Implementation Plan

### Phase 0: Navigation Infrastructure + Legacy UI Toggle

Add Navigation Component with BottomNavigationView and a feature flag to toggle between new and legacy UI.

#### 0.1 Add Navigation Dependencies

```kotlin
// In build.gradle (app)
dependencies {
    implementation "androidx.navigation:navigation-fragment-ktx:2.7.7"
    implementation "androidx.navigation:navigation-ui-ktx:2.7.7"
}
```

#### 0.2 Add Legacy UI Toggle to `Settings.kt`

```kotlin
// In companion object - Keys
private const val USE_NEW_NAVIGATION_UI_KEY = "use_new_navigation_ui"

/** Feature flag: Use new tabbed navigation UI (true) or legacy single-list (false) */
var useNewNavigationUI: Boolean
    get() = getBoolean(USE_NEW_NAVIGATION_UI_KEY, true)
    set(value) = setBoolean(USE_NEW_NAVIGATION_UI_KEY, value)
```

#### 0.3 Create Navigation Graph `res/navigation/nav_graph.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/activeEventsFragment">

    <fragment
        android:id="@+id/activeEventsFragment"
        android:name="com.github.quarck.calnotify.ui.ActiveEventsFragment"
        android:label="@string/nav_active" />

    <fragment
        android:id="@+id/upcomingEventsFragment"
        android:name="com.github.quarck.calnotify.ui.UpcomingEventsFragment"
        android:label="@string/nav_upcoming" />

    <fragment
        android:id="@+id/dismissedEventsFragment"
        android:name="com.github.quarck.calnotify.ui.DismissedEventsFragment"
        android:label="@string/nav_dismissed" />

</navigation>
```

#### 0.4 Update `activity_main.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/coordinator_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- NavHostFragment - hosts all tab content -->
    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/nav_host_fragment"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:defaultNavHost="true"
        app:navGraph="@navigation/nav_graph"
        app:layout_behavior="@string/appbar_scrolling_view_behavior" />

    <!-- Bottom Navigation - 3 items, collapses on scroll -->
    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottom_navigation"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        app:labelVisibilityMode="unlabeled"
        app:layout_behavior="com.google.android.material.behavior.HideBottomViewOnScrollBehavior"
        app:menu="@menu/bottom_nav_menu" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

**Key points:**
- `NavHostFragment` replaces FrameLayout - Navigation Component manages Fragment transactions
- `app:layout_behavior="...HideBottomViewOnScrollBehavior"` auto-hides bottom nav on scroll

#### 0.5 Create `bottom_nav_menu.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- 3 items only - Settings stays in overflow menu -->
    <item
        android:id="@+id/activeEventsFragment"
        android:icon="@drawable/ic_notifications_24dp"
        android:title="@string/nav_active" />
    <item
        android:id="@+id/upcomingEventsFragment"
        android:icon="@drawable/ic_schedule_24dp"
        android:title="@string/nav_upcoming" />
    <item
        android:id="@+id/dismissedEventsFragment"
        android:icon="@drawable/ic_delete_24dp"
        android:title="@string/nav_dismissed" />
</menu>
```

**Note:** Menu item IDs must match Fragment IDs in nav_graph.xml for `setupWithNavController()` to work.

#### 0.6 Update MainActivity Navigation Setup

```kotlin
class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!settings.useNewNavigationUI) {
            // Legacy mode: use old layout and behavior
            setContentView(R.layout.activity_main_legacy)
            setupLegacyUI()
            return
        }
        
        setContentView(R.layout.activity_main)
        setupNavigation()
    }
    
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        
        // Wire up bottom nav with NavController - handles all tab switching
        NavigationUI.setupWithNavController(bottomNav, navController)
    }
    
    // Settings accessed via overflow menu (toolbar)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
```

### Phase 1: Create Event List Fragments

Extract existing MainActivity list logic into reusable Fragments.

#### 1.1 Create Base EventListFragment

Shared logic for Active, Upcoming, and Dismissed fragments:

```kotlin
/**
 * Base fragment for displaying event lists with swipe-to-dismiss and pull-to-refresh.
 * Subclasses implement loadEvents() to provide data.
 */
abstract class EventListFragment : Fragment(), EventListCallback {
    
    protected lateinit var recyclerView: RecyclerView
    protected lateinit var adapter: EventListAdapter
    protected lateinit var refreshLayout: SwipeRefreshLayout
    protected lateinit var emptyView: View
    
    protected val settings: Settings by lazy { Settings(requireContext()) }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_event_list, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView(view)
        setupSwipeRefresh(view)
        loadEvents()
    }
    
    /** Subclasses implement to load their specific event data */
    abstract fun loadEvents()
    
    /** Subclasses implement to define event display mode */
    abstract val eventDisplayMode: EventDisplayMode
    
    protected fun updateEmptyViewVisibility() {
        emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }
}

enum class EventDisplayMode { ACTIVE, UPCOMING, DISMISSED }
```

#### 1.2 Create ActiveEventsFragment

```kotlin
class ActiveEventsFragment : EventListFragment() {
    
    override val eventDisplayMode = EventDisplayMode.ACTIVE
    
    override fun loadEvents() {
        background {
            val events = EventsStorage(requireContext()).use { it.events }
                .filter { settings.isCalendarVisible(it.calendarId) }
                .toTypedArray()
            
            requireActivity().runOnUiThread {
                adapter.setEventsToDisplay(events, eventDisplayMode)
                updateEmptyViewVisibility()
                refreshLayout.isRefreshing = false
            }
        }
    }
}
```

#### 1.3 Create DismissedEventsFragment

```kotlin
class DismissedEventsFragment : EventListFragment() {
    
    override val eventDisplayMode = EventDisplayMode.DISMISSED
    
    override fun loadEvents() {
        background {
            val events = DismissedEventsStorage(requireContext()).use { it.events }
                .filter { settings.isCalendarVisible(it.calendarId) }
                .toTypedArray()
            
            requireActivity().runOnUiThread {
                adapter.setEventsToDisplay(events, eventDisplayMode)
                updateEmptyViewVisibility()
                refreshLayout.isRefreshing = false
            }
        }
    }
}
```

#### 1.4 Create `fragment_event_list.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/refresh_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recycler_view"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:paddingBottom="72dp" />

        <!-- Empty state -->
        <include
            android:id="@+id/empty_view"
            layout="@layout/empty_state"
            android:visibility="gone" />

    </FrameLayout>

</androidx.swiperefreshlayout.widget.SwipeRefreshLayout>
```

#### 1.5 Update EventListAdapter for Display Modes

```kotlin
class EventListAdapter(...) {
    
    private var displayMode: EventDisplayMode = EventDisplayMode.ACTIVE
    
    fun setEventsToDisplay(events: Array<EventAlertRecord>, mode: EventDisplayMode) {
        this.displayMode = mode
        // ... existing logic
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        
        when (displayMode) {
            EventDisplayMode.ACTIVE -> bindActiveEvent(holder, event)
            EventDisplayMode.UPCOMING -> bindUpcomingEvent(holder, event)
            EventDisplayMode.DISMISSED -> bindDismissedEvent(holder, event)
        }
    }
}
```

### Phase 2: Collapsing Filter Bar

Add filter chips that hide on scroll.

#### 2.1 Update Layout with AppBarLayout

```xml
<androidx.coordinatorlayout.widget.CoordinatorLayout>

    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/app_bar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <!-- Toolbar with search -->
        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:layout_scrollFlags="scroll|enterAlways|snap" />

        <!-- Filter chips row -->
        <HorizontalScrollView
            android:id="@+id/filter_chips_container"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:layout_scrollFlags="scroll|enterAlways|snap"
            android:scrollbars="none">

            <com.google.android.material.chip.ChipGroup
                android:id="@+id/filter_chips"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:padding="8dp"
                app:singleLine="true" />

        </HorizontalScrollView>

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/list_events"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior" />

    <BottomNavigationView ... />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

#### 2.2 Add Filter Chips Programmatically

```kotlin
private fun setupFilterChips() {
    val chipGroup = findViewById<ChipGroup>(R.id.filter_chips)
    
    // Calendar filter chip (dropdown)
    val calendarChip = Chip(this).apply {
        text = "All Calendars"
        isCloseIconVisible = true
        closeIcon = getDrawable(R.drawable.ic_arrow_drop_down)
        setOnClickListener { showCalendarFilterBottomSheet() }
    }
    chipGroup.addView(calendarChip)
    
    // Could add more filter chips here
}
```

### Phase 3: Calendar Multi-Select Filter

#### 3.1 Create Calendar Filter Bottom Sheet

```kotlin
class CalendarFilterBottomSheet : BottomSheetDialogFragment() {
    
    private lateinit var adapter: CalendarFilterAdapter
    private var onApply: ((Set<Long>) -> Unit)? = null
    
    override fun onCreateView(...): View {
        // Inflate layout with RecyclerView of checkboxes
    }
    
    private fun loadCalendars() {
        val calendars = CalendarProvider.getCalendars(requireContext())
        val currentlySelected = Settings(requireContext()).selectedCalendarIds
        adapter.setCalendars(calendars, currentlySelected)
    }
    
    private fun applySelection() {
        val selected = adapter.getSelectedIds()
        Settings(requireContext()).selectedCalendarIds = selected
        onApply?.invoke(selected)
        dismiss()
    }
}
```

#### 3.2 Add Settings for Calendar Selection

```kotlin
// In Settings.kt
var selectedCalendarIds: Set<Long>
    get() {
        val raw = getString(SELECTED_CALENDAR_IDS_KEY, "")
        return if (raw.isEmpty()) emptySet()
        else raw.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }
    set(value) {
        setString(SELECTED_CALENDAR_IDS_KEY, value.joinToString(","))
    }

/** Returns true if calendar should be shown (empty set = all calendars) */
fun isCalendarVisible(calendarId: Long): Boolean {
    val selected = selectedCalendarIds
    return selected.isEmpty() || calendarId in selected
}
```

#### 3.3 Filter Events by Calendar

```kotlin
// In data loading
val filteredEvents = events.filter { event ->
    settings.isCalendarVisible(event.calendarId)
}
```

### Phase 4: Upcoming Events Data Provider

#### 4.1 Add Lookahead Settings to `Settings.kt`

```kotlin
// In companion object - Keys
private const val UPCOMING_EVENTS_MODE_KEY = "upcoming_events_mode"
private const val UPCOMING_EVENTS_CUTOFF_HOUR_KEY = "upcoming_events_cutoff_hour"
private const val UPCOMING_EVENTS_CUTOFF_MINUTE_KEY = "upcoming_events_cutoff_minute"
private const val UPCOMING_EVENTS_FIXED_HOURS_KEY = "upcoming_events_fixed_hours"
private const val SELECTED_CALENDAR_IDS_KEY = "selected_calendar_ids"

// Defaults
internal const val DEFAULT_UPCOMING_EVENTS_CUTOFF_HOUR = 10
internal const val DEFAULT_UPCOMING_EVENTS_CUTOFF_MINUTE = 0
internal const val DEFAULT_UPCOMING_EVENTS_FIXED_HOURS = 8

// Valid range for cutoff time: 6:00 AM to 12:00 PM
internal const val MIN_CUTOFF_HOUR = 6
internal const val MAX_CUTOFF_HOUR = 12

/** Lookahead mode: "cutoff" = next morning cutoff, "fixed" = fixed hours */
var upcomingEventsMode: String
    get() = getString(UPCOMING_EVENTS_MODE_KEY, "cutoff")
    set(value) = setString(UPCOMING_EVENTS_MODE_KEY, value)

/** Hour of day for morning cutoff (6-12, default 10) */
var upcomingEventsCutoffHour: Int
    get() = getInt(UPCOMING_EVENTS_CUTOFF_HOUR_KEY, DEFAULT_UPCOMING_EVENTS_CUTOFF_HOUR)
    set(value) = setInt(UPCOMING_EVENTS_CUTOFF_HOUR_KEY, value.coerceIn(MIN_CUTOFF_HOUR, MAX_CUTOFF_HOUR))

/** Minute for morning cutoff (0-59, default 0) */
var upcomingEventsCutoffMinute: Int
    get() = getInt(UPCOMING_EVENTS_CUTOFF_MINUTE_KEY, DEFAULT_UPCOMING_EVENTS_CUTOFF_MINUTE)
    set(value) = setInt(UPCOMING_EVENTS_CUTOFF_MINUTE_KEY, value.coerceIn(0, 59))

/** Fixed hours lookahead (default 8) */
var upcomingEventsFixedHours: Int
    get() = getInt(UPCOMING_EVENTS_FIXED_HOURS_KEY, DEFAULT_UPCOMING_EVENTS_FIXED_HOURS)
    set(value) = setInt(UPCOMING_EVENTS_FIXED_HOURS_KEY, value)
```

#### 4.2 Create `UpcomingEventsLookahead.kt`

```kotlin
package com.github.quarck.calnotify.upcoming

/**
 * Calculates the lookahead cutoff time based on user settings.
 */
class UpcomingEventsLookahead(
    private val settings: Settings,
    private val clock: CNPlusClockInterface = CNPlusSystemClock()
) {
    /**
     * Returns the timestamp until which we should show upcoming events.
     */
    fun getCutoffTime(): Long {
        val now = clock.currentTimeMillis()
        
        return when (settings.upcomingEventsMode) {
            "fixed" -> now + (settings.upcomingEventsFixedHours * Consts.HOUR_IN_MILLISECONDS)
            else -> calculateMorningCutoff(now)
        }
    }
    
    private fun calculateMorningCutoff(now: Long): Long {
        val cutoffHour = settings.upcomingEventsCutoffHour
        val cutoffMinute = settings.upcomingEventsCutoffMinute
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        
        // Set to cutoff time
        calendar.set(Calendar.HOUR_OF_DAY, cutoffHour)
        calendar.set(Calendar.MINUTE, cutoffMinute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // If we're past the cutoff time, move to tomorrow
        val isPastCutoff = (currentHour > cutoffHour) || 
                           (currentHour == cutoffHour && currentMinute >= cutoffMinute)
        if (isPastCutoff) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        return calendar.timeInMillis
    }
}
```

#### 4.3 Create `UpcomingEventsProvider.kt`

```kotlin
package com.github.quarck.calnotify.upcoming

/**
 * Provides upcoming events by querying MonitorStorage and enriching with CalendarProvider.
 */
class UpcomingEventsProvider(
    private val context: Context,
    private val calendarProvider: CalendarProviderInterface = CalendarProvider,
    private val clock: CNPlusClockInterface = CNPlusSystemClock()
) {
    /**
     * Returns upcoming events that haven't fired yet, enriched with full details.
     * 
     * @param cutoffTime Show events with alertTime up to this timestamp
     * @param calendarFilter Set of calendar IDs to include (empty = all)
     * @return List of EventAlertRecord representing upcoming events
     */
    fun getUpcomingEvents(cutoffTime: Long, calendarFilter: Set<Long> = emptySet()): List<EventAlertRecord> {
        val now = clock.currentTimeMillis()
        
        // Get alerts from MonitorStorage
        val upcomingAlerts = MonitorStorage(context).use { storage ->
            storage.getAlertsForAlertRange(now, cutoffTime)
                .filter { !it.wasHandled }
                .sortedBy { it.alertTime }
        }
        
        // Enrich each with full event details
        return upcomingAlerts.mapNotNull { alert ->
            enrichAlert(alert)
        }.filter { event ->
            calendarFilter.isEmpty() || event.calendarId in calendarFilter
        }
    }
    
    private fun enrichAlert(alert: MonitorEventAlertEntry): EventAlertRecord? {
        val eventRecord = calendarProvider.getEvent(context, alert.eventId) ?: return null
        
        return EventAlertRecord(
            calendarId = eventRecord.calendarId,
            eventId = alert.eventId,
            isAllDay = alert.isAllDay,
            isRepeating = eventRecord.rRule?.isNotEmpty() == true,
            alertTime = alert.alertTime,
            notificationId = 0, // Not used for upcoming
            title = eventRecord.title,
            desc = eventRecord.desc,
            startTime = eventRecord.startTime,
            endTime = eventRecord.endTime,
            instanceStartTime = alert.instanceStartTime,
            instanceEndTime = alert.instanceEndTime,
            location = eventRecord.location,
            lastStatusChangeTime = clock.currentTimeMillis(),
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = eventRecord.color,
            origin = EventOrigin.ProviderManual,
            timeFirstSeen = 0L,
            eventStatus = eventRecord.eventStatus,
            attendanceStatus = eventRecord.attendanceStatus
        )
    }
}
```

### Phase 5: Upcoming Events Tab UI

#### 5.1 Add Upcoming Tab Content

Wire up the "Upcoming" bottom nav item to show upcoming events:

```kotlin
private fun showUpcomingEvents() {
    currentView = VIEW_UPCOMING
    background {
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val provider = UpcomingEventsProvider(this, CalendarProvider, clock)
        val upcomingEvents = provider.getUpcomingEvents(
            lookahead.getCutoffTime(),
            settings.selectedCalendarIds
        ).toTypedArray()
        
        runOnUiThread {
            adapter.setEventsToDisplay(upcomingEvents, isUpcoming = true)
            updateEmptyViewVisibility()
        }
    }
}
```

#### 5.2 Update Event Card for Upcoming Display

In the adapter, when displaying upcoming events:

```kotlin
// Show alert time instead of snooze time
holder.snoozedUntilText?.apply {
    visibility = View.VISIBLE
    text = context.getString(R.string.alert_at, formatTime(event.alertTime))
    setTextColor(ContextCompat.getColor(context, R.color.upcoming_text))
}

// Disable swipe-to-dismiss for upcoming events
// Allow tap to open pre-snooze dialog
```

### Phase 6: Pre-Actions (Snooze, Mute, Dismiss)

Users should be able to act on upcoming events before they fire—basically anything you can do with active events:
- **Pre-snooze**: Snooze to a later time (event moves to Active as snoozed)
- **Pre-mute**: Mute the notification when it fires (event fires silently)
- **Pre-dismiss**: Skip the event entirely (goes straight to Dismissed/Bin)

#### 6.1 Handle Upcoming Event Tap

When user taps an upcoming event, show action dialog with options:

```kotlin
override fun onItemClick(v: View, position: Int, eventId: Long) {
    if (isUpcomingView) {
        val event = adapter.getEventAtPosition(position, eventId)
        if (event != null) {
            showUpcomingEventActionDialog(event)
        }
    } else {
        // Existing click handling
    }
}

private fun showUpcomingEventActionDialog(event: EventAlertRecord) {
    AlertDialog.Builder(this)
        .setTitle(event.title)
        .setItems(arrayOf(
            getString(R.string.pre_snooze),
            getString(R.string.pre_mute),
            getString(R.string.pre_dismiss),
            getString(R.string.view_in_calendar)
        )) { _, which ->
            when (which) {
                0 -> showPreSnoozeTimePicker(event)
                1 -> handlePreMute(event)
                2 -> handlePreDismiss(event)
                3 -> openEventInCalendar(event)
            }
        }
        .show()
}
```

#### 6.2 Implement Pre-Snooze Logic

```kotlin
fun handleUpcomingEventSnooze(event: EventAlertRecord, snoozeUntil: Long) {
    background {
        // 1. Mark as handled in MonitorStorage
        MonitorStorage(this).use { storage ->
            storage.updateAlert(MonitorEventAlertEntry(
                eventId = event.eventId,
                isAllDay = event.isAllDay,
                alertTime = event.alertTime,
                instanceStartTime = event.instanceStartTime,
                instanceEndTime = event.instanceEndTime,
                alertCreatedByUs = false,
                wasHandled = true
            ))
        }
        
        // 2. Add to EventsStorage as snoozed
        val snoozedEvent = event.copy(snoozedUntil = snoozeUntil)
        EventsStorage(this).classCustomUse { db ->
            db.addEvent(snoozedEvent)
        }
        
        // 3. Schedule snooze alarm
        alarmScheduler.rescheduleAlarms(this, settings, quietHoursManager)
        
        // 4. Refresh UI
        runOnUiThread {
            showUpcomingEvents() // Refresh to remove the pre-snoozed event
            Snackbar.make(coordinatorLayout, R.string.event_snoozed, Snackbar.LENGTH_SHORT).show()
        }
    }
}
```

#### 6.3 Implement Pre-Mute Logic

Pre-mute marks the event so when it fires, it won't make sound/vibration. The `preMuted` flag is stored directly in `MonitorEventAlertEntry` for reliability.

```kotlin
fun handlePreMute(event: EventAlertRecord) {
    background {
        // Set preMuted flag in MonitorStorage
        // The event stays unhandled so it fires normally, but with mute flag
        MonitorStorage(requireContext()).use { storage ->
            val alert = storage.getAlert(event.eventId, event.alertTime, event.instanceStartTime)
            if (alert != null) {
                storage.updateAlert(alert.copy(preMuted = true))
            }
        }
        
        requireActivity().runOnUiThread {
            loadEvents() // Refresh to show mute indicator
            Snackbar.make(
                requireView(), 
                R.string.event_will_be_muted, 
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }
}

fun handleUnPreMute(event: EventAlertRecord) {
    background {
        MonitorStorage(requireContext()).use { storage ->
            val alert = storage.getAlert(event.eventId, event.alertTime, event.instanceStartTime)
            if (alert != null) {
                storage.updateAlert(alert.copy(preMuted = false))
            }
        }
        
        requireActivity().runOnUiThread {
            loadEvents()
            Snackbar.make(requireView(), R.string.event_unmuted, Snackbar.LENGTH_SHORT).show()
        }
    }
}
```

#### 6.4 Add `preMuted` Column to MonitorEventAlertEntry

Update the data class and database schema:

```kotlin
// In MonitorEventAlertEntry.kt
data class MonitorEventAlertEntry(
    val eventId: Long,
    val isAllDay: Boolean,
    val alertTime: Long,
    val instanceStartTime: Long,
    val instanceEndTime: Long,
    val alertCreatedByUs: Boolean = false,
    val wasHandled: Boolean = false,
    val preMuted: Boolean = false  // NEW: user pre-muted before alert fired
)

// Room entity (if using Room)
@Entity(tableName = "monitor_alerts", primaryKeys = ["eventId", "alertTime", "instanceStartTime"])
data class MonitorAlertEntity(
    val eventId: Long,
    val isAllDay: Boolean,
    val alertTime: Long,
    val instanceStartTime: Long,
    val instanceEndTime: Long,
    val alertCreatedByUs: Boolean = false,
    val wasHandled: Boolean = false,
    val preMuted: Boolean = false  // NEW column
)
```

**Why database over SharedPreferences:**
- Atomic updates with the alert record
- No cleanup needed (deleted with alert)
- Survives app restarts reliably
- Can query for all pre-muted alerts easily
- Consistent with existing MonitorStorage patterns

#### 6.5 Implement Pre-Dismiss Logic

Pre-dismiss skips the event entirely—it goes straight to the Dismissed storage:

```kotlin
fun handlePreDismiss(event: EventAlertRecord) {
    background {
        // 1. Mark as handled in MonitorStorage (so it won't fire)
        MonitorStorage(this).use { storage ->
            storage.updateAlert(MonitorEventAlertEntry(
                eventId = event.eventId,
                isAllDay = event.isAllDay,
                alertTime = event.alertTime,
                instanceStartTime = event.instanceStartTime,
                instanceEndTime = event.instanceEndTime,
                alertCreatedByUs = false,
                wasHandled = true
            ))
        }
        
        // 2. Add directly to DismissedEventsStorage
        DismissedEventsStorage(this).classCustomUse { db ->
            db.addEvent(
                EventDismissType.ManuallyDismissedFromActivity,
                event
            )
        }
        
        // 3. Dismiss native calendar alert if any
        CalendarProvider.dismissNativeEventAlert(this, event.eventId)
        
        // 4. Refresh UI
        runOnUiThread {
            showUpcomingEvents() // Refresh to remove the dismissed event
            Snackbar.make(
                coordinatorLayout, 
                R.string.event_dismissed, 
                Snackbar.LENGTH_LONG
            ).setAction(R.string.undo) {
                undoPreDismiss(event)
            }.show()
        }
    }
}

fun undoPreDismiss(event: EventAlertRecord) {
    background {
        // 1. Remove from DismissedEventsStorage
        DismissedEventsStorage(this).classCustomUse { db ->
            db.deleteEvent(event.eventId, event.instanceStartTime)
        }
        
        // 2. Unmark as handled in MonitorStorage
        MonitorStorage(this).use { storage ->
            storage.updateAlert(MonitorEventAlertEntry(
                eventId = event.eventId,
                isAllDay = event.isAllDay,
                alertTime = event.alertTime,
                instanceStartTime = event.instanceStartTime,
                instanceEndTime = event.instanceEndTime,
                alertCreatedByUs = false,
                wasHandled = false  // Back to unhandled
            ))
        }
        
        runOnUiThread {
            showUpcomingEvents() // Event reappears
        }
    }
}
```

#### 6.6 Update ApplicationController.registerNewEvent

When an event fires, check if it was pre-muted in MonitorStorage:

```kotlin
// In registerNewEvent or handleAlerts
MonitorStorage(context).use { storage ->
    val alert = storage.getAlert(event.eventId, event.alertTime, event.instanceStartTime)
    if (alert?.preMuted == true) {
        event.isMuted = true
        // Clear the preMuted flag now that it's been applied
        storage.updateAlert(alert.copy(preMuted = false, wasHandled = true))
    }
}
```

**Note:** The `preMuted` flag is automatically cleaned up when the alert is marked as handled or when the alert entry is deleted during normal MonitorStorage cleanup.

### Phase 7: Settings UI for Lookahead

#### 7.1 Add Preferences

```xml
<PreferenceCategory android:title="@string/upcoming_events_category">
    
    <ListPreference
        android:key="upcoming_events_mode"
        android:title="@string/upcoming_events_mode"
        android:entries="@array/upcoming_events_mode_entries"
        android:entryValues="@array/upcoming_events_mode_values"
        android:defaultValue="cutoff" />
    
    <!-- Custom preference that opens a time picker dialog -->
    <Preference
        android:key="upcoming_events_cutoff_time"
        android:title="@string/upcoming_events_cutoff_time"
        android:summary="@string/upcoming_events_cutoff_time_summary" />
    
    <ListPreference
        android:key="upcoming_events_fixed_hours"
        android:title="@string/upcoming_events_fixed_hours"
        android:entries="@array/upcoming_events_fixed_hours_entries"
        android:entryValues="@array/upcoming_events_fixed_hours_values"
        android:defaultValue="8" />

</PreferenceCategory>
```

#### 7.2 Time Picker Preference Handler

```kotlin
// In preferences fragment
findPreference<Preference>("upcoming_events_cutoff_time")?.apply {
    // Update summary to show current value
    updateCutoffTimeSummary(this)
    
    setOnPreferenceClickListener {
        showCutoffTimePicker()
        true
    }
}

private fun showCutoffTimePicker() {
    val currentHour = settings.upcomingEventsCutoffHour
    val currentMinute = settings.upcomingEventsCutoffMinute
    
    // Use MaterialTimePicker for modern look
    val picker = MaterialTimePicker.Builder()
        .setTimeFormat(TimeFormat.CLOCK_12H)
        .setHour(currentHour)
        .setMinute(currentMinute)
        .setTitleText(R.string.select_cutoff_time)
        .build()
    
    picker.addOnPositiveButtonClickListener {
        val selectedHour = picker.hour
        val selectedMinute = picker.minute
        
        // Validate: must be between 6:00 AM and 12:00 PM
        if (selectedHour < 6 || selectedHour > 12 || (selectedHour == 12 && selectedMinute > 0)) {
            Toast.makeText(
                requireContext(),
                R.string.cutoff_time_invalid,
                Toast.LENGTH_SHORT
            ).show()
            return@addOnPositiveButtonClickListener
        }
        
        settings.upcomingEventsCutoffHour = selectedHour
        settings.upcomingEventsCutoffMinute = selectedMinute
        updateCutoffTimeSummary(findPreference("upcoming_events_cutoff_time"))
    }
    
    picker.show(childFragmentManager, "cutoff_time_picker")
}

private fun updateCutoffTimeSummary(pref: Preference?) {
    val hour = settings.upcomingEventsCutoffHour
    val minute = settings.upcomingEventsCutoffMinute
    val time = String.format(
        "%d:%02d %s",
        if (hour > 12) hour - 12 else if (hour == 0) 12 else hour,
        minute,
        if (hour >= 12) "PM" else "AM"
    )
    pref?.summary = getString(R.string.upcoming_events_cutoff_time_summary_format, time)
}
```

#### 7.2 Add String Resources

```xml
<string name="upcoming_events_category">Upcoming Events</string>
<string name="upcoming_events_mode">Lookahead mode</string>
<string name="upcoming_events_cutoff_time">Morning cutoff time</string>
<string name="upcoming_events_cutoff_time_summary">Show events until this time the next morning</string>
<string name="upcoming_events_cutoff_time_summary_format">Show events until %s the next morning</string>
<string name="select_cutoff_time">Select cutoff time</string>
<string name="cutoff_time_invalid">Cutoff time must be between 6:00 AM and 12:00 PM</string>
<string name="upcoming_events_fixed_hours">Hours ahead</string>
<string name="upcoming">Upcoming</string>
<string name="alert_at">Alert at %s</string>
<string name="nav_active">Active</string>
<string name="nav_upcoming">Upcoming</string>
<string name="nav_dismissed">Dismissed</string>
<string name="nav_settings">Settings</string>
<string name="select_calendars">Select Calendars</string>
<string name="all_calendars">All Calendars</string>
<string name="pre_snooze">Snooze until…</string>
<string name="pre_mute">Mute when it fires</string>
<string name="pre_dismiss">Dismiss</string>
<string name="view_in_calendar">View in Calendar</string>
<string name="event_snoozed">Event snoozed</string>
<string name="event_will_be_muted">Event will be muted when it fires</string>
<string name="event_unmuted">Event will no longer be muted</string>
<string name="event_dismissed">Event dismissed</string>
<string name="empty_active">No active notifications</string>
<string name="empty_upcoming">No upcoming events in the next %s</string>
<string name="empty_dismissed">No dismissed events</string>
<string name="error_loading_events">Error loading events</string>
<string name="use_new_navigation_ui">Use new navigation</string>
<string name="use_new_navigation_ui_summary">Tabbed interface with bottom navigation</string>

<string-array name="upcoming_events_mode_entries">
    <item>Until morning cutoff</item>
    <item>Fixed hours ahead</item>
</string-array>
<string-array name="upcoming_events_mode_values">
    <item>cutoff</item>
    <item>fixed</item>
</string-array>

<!-- Cutoff time uses MaterialTimePicker dialog, constrained to 6:00 AM - 12:00 PM -->

<string-array name="upcoming_events_fixed_hours_entries">
    <item>4 hours</item>
    <item>8 hours</item>
    <item>12 hours</item>
    <item>24 hours</item>
</string-array>
<string-array name="upcoming_events_fixed_hours_values">
    <item>4</item>
    <item>8</item>
    <item>12</item>
    <item>24</item>
</string-array>
```

## Edge Cases & Error Handling

### Empty States

Each tab should show a helpful empty state when there's no data:

| Tab | Empty State Message | Icon |
|-----|---------------------|------|
| Active | "No active notifications" | 🔔 (grayed) |
| Upcoming | "No upcoming events in the next X hours" | ⏰ (grayed) |
| Dismissed | "No dismissed events" | 🗑️ (grayed) |

```kotlin
// In EventListFragment
protected fun updateEmptyViewVisibility() {
    val isEmpty = adapter.itemCount == 0
    recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
    
    // Update empty message based on display mode
    emptyView.findViewById<TextView>(R.id.empty_message)?.text = when (eventDisplayMode) {
        EventDisplayMode.ACTIVE -> getString(R.string.empty_active)
        EventDisplayMode.UPCOMING -> getString(R.string.empty_upcoming, getUpcomingWindowDescription())
        EventDisplayMode.DISMISSED -> getString(R.string.empty_dismissed)
    }
}
```

### Refresh Triggers

The event list should refresh automatically when:

1. **Data changes** - BroadcastReceiver for `Consts.DATA_UPDATED_BROADCAST`
2. **Tab switch** - `onResume()` in each Fragment
3. **Pull-to-refresh** - SwipeRefreshLayout
4. **Calendar sync** - After MonitorStorage rescan

```kotlin
// In EventListFragment
private val dataUpdateReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        loadEvents()
    }
}

override fun onResume() {
    super.onResume()
    LocalBroadcastManager.getInstance(requireContext())
        .registerReceiver(dataUpdateReceiver, IntentFilter(Consts.DATA_UPDATED_BROADCAST))
    loadEvents() // Refresh on tab switch
}

override fun onPause() {
    super.onPause()
    LocalBroadcastManager.getInstance(requireContext())
        .unregisterReceiver(dataUpdateReceiver)
}
```

### Migration Path (Legacy UI → New UI)

1. **Default behavior:** New UI is default (`useNewNavigationUI = true`)
2. **Toggle in Settings:** "Use new navigation" switch under Appearance
3. **Graceful fallback:** If new UI crashes, catch in `onCreate` and fall back to legacy
4. **DismissedEventsActivity retained:** Keep it functional during transition for deep links

```kotlin
// In MainActivity.onCreate()
try {
    if (settings.useNewNavigationUI) {
        setContentView(R.layout.activity_main)
        setupNavigation()
    } else {
        setContentView(R.layout.activity_main_legacy)
        setupLegacyUI()
    }
} catch (e: Exception) {
    DevLog.error(LOG_TAG, "New UI failed, falling back to legacy: ${e.message}")
    settings.useNewNavigationUI = false
    setContentView(R.layout.activity_main_legacy)
    setupLegacyUI()
}
```

### Error Handling

#### CalendarProvider.getEvent() Returns Null

Event was deleted from calendar after being scanned:

```kotlin
private fun enrichAlert(alert: MonitorEventAlertEntry): EventAlertRecord? {
    val eventRecord = calendarProvider.getEvent(context, alert.eventId)
    if (eventRecord == null) {
        DevLog.debug(LOG_TAG, "Event ${alert.eventId} no longer exists in calendar, skipping")
        return null
    }
    // ... enrichment logic
}
```

#### Database Errors

```kotlin
fun loadEvents() {
    background {
        try {
            val events = fetchEvents()
            requireActivity().runOnUiThread {
                adapter.setEventsToDisplay(events, eventDisplayMode)
                updateEmptyViewVisibility()
            }
        } catch (e: SQLException) {
            DevLog.error(LOG_TAG, "Database error loading events: ${e.message}")
            requireActivity().runOnUiThread {
                showErrorState(getString(R.string.error_loading_events))
            }
        }
    }
}
```

## Lazy Enrichment Pattern

For better perceived performance, show event cards immediately with placeholders, then enrich asynchronously.

### Implementation

```kotlin
class UpcomingEventsFragment : EventListFragment() {
    
    override fun loadEvents() {
        background {
            val now = clock.currentTimeMillis()
            val lookahead = UpcomingEventsLookahead(settings, clock)
            
            // Step 1: Get alerts quickly (no CalendarProvider calls)
            val alerts = MonitorStorage(requireContext()).use { storage ->
                storage.getAlertsForAlertRange(now, lookahead.getCutoffTime())
                    .filter { !it.wasHandled }
                    .sortedBy { it.alertTime }
            }
            
            // Step 2: Show placeholder cards immediately
            val placeholders = alerts.map { alert ->
                EventAlertRecord.placeholder(
                    eventId = alert.eventId,
                    alertTime = alert.alertTime,
                    instanceStartTime = alert.instanceStartTime,
                    instanceEndTime = alert.instanceEndTime,
                    isAllDay = alert.isAllDay,
                    preMuted = alert.preMuted
                )
            }.toTypedArray()
            
            requireActivity().runOnUiThread {
                adapter.setEventsToDisplay(placeholders, eventDisplayMode)
                updateEmptyViewVisibility()
            }
            
            // Step 3: Enrich in batches, update UI progressively
            alerts.chunked(5).forEach { batch ->
                val enriched = batch.mapNotNull { alert ->
                    enrichAlert(alert)
                }
                
                requireActivity().runOnUiThread {
                    adapter.updateEvents(enriched)
                }
            }
        }
    }
}
```

### Placeholder Card Display

```kotlin
// In EventListAdapter
override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val event = events[position]
    
    if (event.isPlaceholder) {
        // Show skeleton UI
        holder.titleView.text = "Loading..."
        holder.titleView.setBackgroundResource(R.drawable.skeleton_shimmer)
        holder.timeView.text = formatTime(event.alertTime) // We have this from MonitorStorage
        holder.locationView.visibility = View.GONE
    } else {
        // Normal display
        holder.titleView.text = event.title
        holder.titleView.background = null
        // ... rest of binding
    }
}
```

### EventAlertRecord Placeholder Factory

```kotlin
// In EventAlertRecord companion object
companion object {
    fun placeholder(
        eventId: Long,
        alertTime: Long,
        instanceStartTime: Long,
        instanceEndTime: Long,
        isAllDay: Boolean,
        preMuted: Boolean = false
    ) = EventAlertRecord(
        calendarId = 0,
        eventId = eventId,
        isAllDay = isAllDay,
        alertTime = alertTime,
        instanceStartTime = instanceStartTime,
        instanceEndTime = instanceEndTime,
        title = "",  // Empty = placeholder
        isPlaceholder = true,
        // ... other fields with defaults
    )
}
```

## Files to Modify/Create

### New Files

| File | Purpose |
|------|---------|
| `android/app/src/main/res/navigation/nav_graph.xml` | Navigation Component graph defining all destinations |
| `android/app/src/main/res/menu/bottom_nav_menu.xml` | Bottom navigation menu items (3 items) |
| `android/app/src/main/res/layout/fragment_event_list.xml` | Shared layout for event list fragments |
| `android/app/src/main/res/layout/activity_main_legacy.xml` | Copy of old layout for legacy mode |
| `android/app/src/main/java/com/github/quarck/calnotify/ui/EventListFragment.kt` | Base fragment for event lists |
| `android/app/src/main/java/com/github/quarck/calnotify/ui/ActiveEventsFragment.kt` | Active events tab |
| `android/app/src/main/java/com/github/quarck/calnotify/ui/UpcomingEventsFragment.kt` | Upcoming events tab |
| `android/app/src/main/java/com/github/quarck/calnotify/ui/DismissedEventsFragment.kt` | Dismissed events tab |
| `android/app/src/main/java/com/github/quarck/calnotify/ui/CalendarFilterBottomSheet.kt` | Calendar multi-select bottom sheet |
| `android/app/src/main/java/com/github/quarck/calnotify/upcoming/UpcomingEventsLookahead.kt` | Lookahead cutoff calculation |
| `android/app/src/main/java/com/github/quarck/calnotify/upcoming/UpcomingEventsProvider.kt` | Fetch and enrich upcoming events |
| `android/app/src/test/java/com/github/quarck/calnotify/upcoming/UpcomingEventsLookaheadRobolectricTest.kt` | Unit tests for lookahead logic |
| `android/app/src/test/java/com/github/quarck/calnotify/upcoming/UpcomingEventsProviderRobolectricTest.kt` | Unit tests for provider (mocked deps) |
| `android/app/src/test/java/com/github/quarck/calnotify/ui/EventListFragmentRobolectricTest.kt` | Unit tests for fragment logic |
| `android/app/src/androidTest/java/com/github/quarck/calnotify/upcoming/PreSnoozeIntegrationTest.kt` | Integration tests for pre-snooze |
| `android/app/src/androidTest/java/com/github/quarck/calnotify/upcoming/PreMuteIntegrationTest.kt` | Integration tests for pre-mute |
| `android/app/src/androidTest/java/com/github/quarck/calnotify/upcoming/PreDismissIntegrationTest.kt` | Integration tests for pre-dismiss |

### Modified Files

| File | Changes |
|------|---------|
| `build.gradle` (app) | Add Navigation Component dependencies |
| `activity_main.xml` | Replace FrameLayout with NavHostFragment, add BottomNavigationView |
| `MainActivity.kt` | Setup NavController, legacy UI toggle, move list logic to fragments |
| `Settings.kt` | Add `useNewNavigationUI`, lookahead settings, calendar filter selection |
| `MonitorEventAlertEntry.kt` | Add `preMuted` boolean field |
| `MonitorStorage*.kt` | Handle `preMuted` column in DB operations |
| `EventListAdapter.kt` | Handle display modes (active/upcoming/dismissed), placeholder support |
| `EventAlertRecord.kt` | Add `isPlaceholder` field, placeholder factory method |
| `ApplicationController.kt` | Check `preMuted` flag when event fires |
| `strings.xml` | Add new string resources for nav, filters, upcoming, empty states |
| Preferences XML | Add lookahead settings UI, legacy UI toggle |

### Files to Eventually Deprecate

| File | Reason |
|------|--------|
| `DismissedEventsActivity.kt` | Functionality moves to MainActivity tab |
| `activity_dismissed_events.xml` | Layout merged into main activity |

## Testing Plan

### Testing Philosophy

**Prefer Robolectric over Instrumentation tests:**
- Robolectric tests run on JVM (fast, no emulator needed)
- Use instrumentation tests only for things that can't be tested in Robolectric:
  - Real Android system interactions (notifications, alarms)
  - Real Calendar Provider queries
  - Complex UI interactions that Robolectric can't simulate

### Unit Tests (Robolectric) - `src/test/`

#### `UpcomingEventsLookaheadRobolectricTest.kt`

```kotlin
@RunWith(RobolectricTestRunner::class)
class UpcomingEventsLookaheadRobolectricTest {
    
    @Test
    fun `cutoff mode - before cutoff hour returns today`() {
        // Given: 8:00 AM, cutoff = 10
        // When: getCutoffTime()
        // Then: Returns today 10:00 AM
    }

    @Test
    fun `cutoff mode - after cutoff hour returns tomorrow`() {
        // Given: 11:00 AM, cutoff = 10
        // When: getCutoffTime()
        // Then: Returns tomorrow 10:00 AM
    }

    @Test
    fun `cutoff mode - just before midnight returns next day`() {
        // Given: 11:59 PM, cutoff = 10
        // When: getCutoffTime()
        // Then: Returns tomorrow 10:00 AM
    }

    @Test
    fun `cutoff mode - just after midnight returns same day`() {
        // Given: 12:01 AM, cutoff = 10
        // When: getCutoffTime()
        // Then: Returns today 10:00 AM
    }

    @Test
    fun `fixed mode - returns now plus configured hours`() {
        // Given: mode = "fixed", hours = 8
        // When: getCutoffTime()
        // Then: Returns now + 8 hours
    }
}
```

#### `UpcomingEventsProviderRobolectricTest.kt`

Use mocked MonitorStorage and CalendarProvider:

```kotlin
@RunWith(RobolectricTestRunner::class)
class UpcomingEventsProviderRobolectricTest {

    private lateinit var mockMonitorStorage: MockMonitorStorage
    private lateinit var mockCalendarProvider: CalendarProviderInterface
    private lateinit var testClock: TestClock
    
    @Test
    fun `returns events within lookahead window`() {
        // Given: Events at +1h, +5h, +12h, cutoff = +8h
        // When: getUpcomingEvents(cutoff)
        // Then: Returns events at +1h, +5h only
    }

    @Test
    fun `excludes already handled events`() {
        // Given: Event in MonitorStorage with wasHandled = true
        // When: getUpcomingEvents(cutoff)
        // Then: Event not returned
    }

    @Test
    fun `enriches with full event details`() {
        // Given: Event in MonitorStorage, CalendarProvider returns EventRecord
        // When: getUpcomingEvents(cutoff)
        // Then: Returned EventAlertRecord has title, description, color, location
    }

    @Test
    fun `returns empty list when no upcoming events`() {
        // Given: No events in lookahead window
        // When: getUpcomingEvents(cutoff)
        // Then: Returns empty list
    }

    @Test
    fun `filters by calendar when filter set`() {
        // Given: Events from calendar A and B, filter = {A}
        // When: getUpcomingEvents(cutoff, filter)
        // Then: Only returns events from calendar A
    }
    
    @Test
    fun `handles CalendarProvider returning null gracefully`() {
        // Given: Event in MonitorStorage, but CalendarProvider.getEvent returns null
        // When: getUpcomingEvents(cutoff)
        // Then: That event is skipped, no crash
    }
}
```

#### `MonitorStoragePreMutedRobolectricTest.kt`

```kotlin
@RunWith(RobolectricTestRunner::class)
class MonitorStoragePreMutedRobolectricTest {

    @Test
    fun `updateAlert with preMuted true persists flag`() {
        // Given: Alert in storage with preMuted = false
        // When: updateAlert(alert.copy(preMuted = true))
        // Then: getAlert returns alert with preMuted = true
    }
    
    @Test
    fun `new alerts default to preMuted false`() {
        // Given: New MonitorEventAlertEntry without preMuted specified
        // When: addAlert(entry)
        // Then: getAlert returns entry with preMuted = false
    }
    
    @Test
    fun `preMuted flag survives storage round-trip`() {
        // Given: Alert with preMuted = true
        // When: Close and reopen storage
        // Then: getAlert returns entry with preMuted = true
    }
    
    @Test
    fun `different instances of same event have independent preMuted flags`() {
        // Given: Event 1 instance A with preMuted = true
        // And: Event 1 instance B with preMuted = false
        // Then: Each instance retains its own flag
    }
}
```

#### `CalendarFilterRobolectricTest.kt`

```kotlin
@RunWith(RobolectricTestRunner::class)
class CalendarFilterRobolectricTest {

    @Test
    fun `empty filter means all calendars visible`() {
        // Given: selectedCalendarIds is empty
        // When: isCalendarVisible(anyCalendarId)
        // Then: Returns true
    }
    
    @Test
    fun `filter includes only selected calendars`() {
        // Given: selectedCalendarIds = {1, 2}
        // When: isCalendarVisible(1), isCalendarVisible(3)
        // Then: Returns true for 1, false for 3
    }
    
    @Test
    fun `filter persists across settings reload`() {
        // Given: Set selectedCalendarIds = {1, 2}
        // When: Create new Settings instance
        // Then: selectedCalendarIds still equals {1, 2}
    }
}
```

#### UI Logic Tests (Robolectric)

```kotlin
@RunWith(RobolectricTestRunner::class)
class MainActivityNavigationRobolectricTest {
    
    private lateinit var navController: TestNavHostController
    
    @Before
    fun setup() {
        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        navController.setGraph(R.navigation.nav_graph)
    }
    
    @Test
    fun `bottom nav initial state is Active tab`() {
        // Given: MainActivity launched with new UI
        // Then: navController.currentDestination.id == R.id.activeEventsFragment
    }
    
    @Test
    fun `switching tabs navigates to correct fragment`() {
        // Given: On Active tab
        // When: Navigate to Upcoming
        navController.navigate(R.id.upcomingEventsFragment)
        // Then: navController.currentDestination.id == R.id.upcomingEventsFragment
    }
    
    @Test
    fun `legacy UI toggle uses legacy layout`() {
        // Given: settings.useNewNavigationUI = false
        // When: MainActivity launches
        // Then: contentView is activity_main_legacy
    }
}

@RunWith(RobolectricTestRunner::class)
class EventListFragmentRobolectricTest {
    
    @Test
    fun `empty state shown when no events`() {
        // Given: ActiveEventsFragment with empty EventsStorage
        // When: loadEvents() completes
        // Then: empty_view is VISIBLE, recycler_view is GONE
    }
    
    @Test
    fun `refresh triggers loadEvents`() {
        // Given: ActiveEventsFragment displayed
        // When: SwipeRefreshLayout triggers refresh
        // Then: loadEvents() is called
    }
    
    @Test
    fun `data update broadcast triggers refresh`() {
        // Given: ActiveEventsFragment in resumed state
        // When: DATA_UPDATED_BROADCAST received
        // Then: loadEvents() is called
    }
}
```

### Instrumentation Tests (AndroidTest) - `src/androidTest/`

Only for things that require real Android APIs:

#### `UpcomingEventsProviderIntegrationTest.kt`

Test with real Calendar Provider (requires test calendar setup):

```kotlin
@RunWith(AndroidJUnit4::class)
class UpcomingEventsProviderIntegrationTest {
    
    @Test
    fun `enriches from real Calendar Provider`() {
        // Given: Real event created in test calendar
        // When: getUpcomingEvents with real CalendarProvider
        // Then: Returns enriched EventAlertRecord with real data
    }
}
```

#### `PreSnoozeIntegrationTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class PreSnoozeIntegrationTest {
    
    @Test
    fun `pre-snoozed event appears in EventsStorage`() {
        // Given: Upcoming event
        // When: handleUpcomingEventSnooze(event, snoozeTime)
        // Then: Event exists in real EventsStorage with snoozedUntil set
    }
    
    @Test
    fun `pre-snoozed event marked as handled in MonitorStorage`() {
        // Given: Upcoming event in MonitorStorage
        // When: handleUpcomingEventSnooze(event, snoozeTime)
        // Then: MonitorStorage entry has wasHandled = true
    }
}
```

#### `PreMuteIntegrationTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class PreMuteIntegrationTest {
    
    @Test
    fun `pre-muted event fires with mute flag set`() {
        // Given: Alert in MonitorStorage with preMuted = true
        // When: Event fires via registerNewEvent
        // Then: EventAlertRecord.isMuted == true
    }
    
    @Test
    fun `preMuted flag cleared after event fires`() {
        // Given: Alert with preMuted = true
        // When: Event fires and is processed
        // Then: MonitorStorage alert has preMuted = false (or is deleted)
    }
    
    @Test
    fun `un-pre-mute clears flag before event fires`() {
        // Given: Alert with preMuted = true
        // When: User un-pre-mutes via handleUnPreMute
        // Then: MonitorStorage alert has preMuted = false
        // And: Event fires without mute
    }
}
```

#### `PreDismissIntegrationTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class PreDismissIntegrationTest {
    
    @Test
    fun `pre-dismissed event goes to DismissedEventsStorage`() {
        // Given: Upcoming event
        // When: handlePreDismiss(event)
        // Then: Event exists in DismissedEventsStorage
    }
    
    @Test
    fun `pre-dismissed event marked as handled in MonitorStorage`() {
        // Given: Upcoming event in MonitorStorage
        // When: handlePreDismiss(event)
        // Then: MonitorStorage entry has wasHandled = true
    }
    
    @Test
    fun `pre-dismissed event does not fire notification`() {
        // Given: Event was pre-dismissed
        // When: Alert time passes
        // Then: No notification posted (wasHandled = true skips it)
    }
    
    @Test
    fun `undo pre-dismiss restores event to upcoming`() {
        // Given: Event was pre-dismissed
        // When: undoPreDismiss(event)
        // Then: Event removed from DismissedEventsStorage
        // And: MonitorStorage entry has wasHandled = false
        // And: Event reappears in Upcoming tab
    }
}
```

#### UI Integration Tests (AndroidTest)

Only for complex UI flows that can't be tested in Robolectric:

```kotlin
@RunWith(AndroidJUnit4::class)
class MainActivityUITest {
    
    @Test
    fun `filter chips and bottom nav collapse on scroll`() {
        // Requires real CoordinatorLayout behavior
        // Given: Filter chips and bottom nav visible
        // When: Scroll RecyclerView down significantly
        // Then: Both collapse out of view
    }
    
    @Test
    fun `calendar filter bottom sheet shows real calendars`() {
        // Requires real Calendar Provider
        // Given: User has multiple calendars
        // When: Tap Calendar filter chip
        // Then: Bottom sheet lists actual calendar names with colors
    }
}
```

### Test File Organization

```
src/test/java/.../upcoming/
├── UpcomingEventsLookaheadRobolectricTest.kt
├── UpcomingEventsProviderRobolectricTest.kt
└── CalendarFilterRobolectricTest.kt

src/test/java/.../monitorstorage/
└── MonitorStoragePreMutedRobolectricTest.kt

src/test/java/.../ui/
├── MainActivityNavigationRobolectricTest.kt
└── EventListFragmentRobolectricTest.kt

src/androidTest/java/.../upcoming/
├── UpcomingEventsProviderIntegrationTest.kt
├── PreSnoozeIntegrationTest.kt
├── PreMuteIntegrationTest.kt
└── PreDismissIntegrationTest.kt

src/androidTest/java/.../ui/
└── MainActivityUITest.kt
```

## Future Enhancements

### Phase 8: Visual Polish

- Animated transitions between tabs
- Badge counts on bottom nav icons (e.g., "3" on Active icon)
- Subtle background tint for upcoming events
- Different icons for event types (meeting, reminder, all-day)

### Phase 9: Smart Lookahead

- Automatically extend lookahead for days with many events
- "Show more" button to extend window on demand
- Push notification preview for imminent events

### Phase 10: Additional Filters

- Status filters (Active/Snoozed toggle within Active tab)
- Time filters (Today, Tomorrow, This Week)
- Search across all tabs

### Phase 11: Gesture Navigation

- Swipe between tabs (like Twitter)
- Pull-to-refresh with haptic feedback
- Long-press for quick actions

## Notes

### Design Principle: Parity Between Active and Upcoming

**Any action available on active events should also work on upcoming events:**
- ✅ Snooze → Pre-snooze
- ✅ Mute → Pre-mute  
- ✅ Dismiss → Pre-dismiss
- ✅ View in Calendar → View in Calendar

This keeps the UX consistent and predictable.

### Technical Notes

**Data Layer:**
- The `MonitorStorage.getAlertsForAlertRange()` method already exists and is tested
- `CalendarProvider.getEvent()` may return null if the calendar event was deleted—handle gracefully
- Pre-muted state stored in `MonitorEventAlertEntry.preMuted` column (not SharedPreferences) for reliability
- Lazy enrichment: show placeholder cards immediately, enrich in background batches

**Navigation Component:**
- Menu item IDs in `bottom_nav_menu.xml` must match Fragment IDs in `nav_graph.xml`
- Use `NavigationUI.setupWithNavController()` for automatic tab switching
- Each Fragment should register for `DATA_UPDATED_BROADCAST` to refresh on data changes
- `TestNavHostController` available for testing navigation logic

**UI Patterns:**
- BottomNavigationView supports up to 5 items; we're using 3 (Active, Upcoming, Dismissed)
- Settings accessed via overflow menu (⋮) in toolbar, not bottom nav
- `labelVisibilityMode="unlabeled"` hides text labels for icon-only nav
- CoordinatorLayout + AppBarLayout provides built-in scroll-to-collapse behavior for top bar
- For bottom nav collapse: use `app:layout_behavior="com.google.android.material.behavior.HideBottomViewOnScrollBehavior"`

**Legacy UI Support:**
- `Settings.useNewNavigationUI` feature flag controls which UI loads
- Both `activity_main.xml` (new) and `activity_main_legacy.xml` (old) layouts maintained
- Graceful fallback if new UI crashes on startup

**Testing:**
- Prefer Robolectric tests for all logic; use instrumentation only for real Calendar Provider
- Use `TestNavHostController` for navigation tests in Robolectric
- Fragment lifecycle can be tested with `FragmentScenario`

## Implementation Order Recommendation

Given the scope, consider this order for incremental delivery:

1. **Phase 0** - Navigation infrastructure + legacy UI toggle (biggest change, enables everything else)
2. **Phase 1** - Event list fragments (Active, Dismissed - proves pattern works)
3. **Phase 4** - Upcoming data provider (can test independently)
4. **Phase 5** - Upcoming tab (wires it together)
5. **Phase 2** - Collapsing filter bar (polish)
6. **Phase 3** - Calendar multi-select (polish)
7. **Phase 6** - Pre-actions (snooze, mute, dismiss - feature complete)
8. **Phase 7** - Settings UI (configuration)

**Test-Driven Approach:**
- Write tests for each phase before/during implementation
- Don't merge until tests pass and manual testing confirms functionality
- Legacy UI toggle provides safety net during development

Each phase is independently testable and deliverable.

## Related Work

- `docs/architecture/calendar_monitoring.md` - How MonitorStorage is populated
- `docs/dev_completed/constructor-mocking-android.md` - Testing patterns for CalendarProvider
- `docs/dev_todo/dismissed_events_long_storage.md` - Related bin/history work
