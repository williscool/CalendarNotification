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

## UI Vision

**Inspiration:** GitHub Android app (filter pills) + Twitter/X (icon-only bottom nav, collapsing UI on scroll)

### Bottom Navigation (Icon-Only)

```
┌─────────────────────────────────────────────────┐
│     🔔          ⏰          🗑️          ⚙️      │
│   Active     Upcoming    Dismissed   Settings   │
└─────────────────────────────────────────────────┘
```

- **Icon-only** - no text labels (icons are self-explanatory)
- **Always visible** - fixed at bottom
- **Material BottomNavigationView** - standard Android component
- Settings could alternatively stay in overflow menu (TBD)

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

### UI Approach: Bottom Navigation + Tabs

**Chosen approach:** Full navigation redesign with:
1. Bottom nav for primary views (Active, Upcoming, Dismissed)
2. Collapsing filter bar with calendar multi-select
3. Each "tab" is essentially a Fragment or view within MainActivity

## Implementation Plan

### Phase 0: Bottom Navigation Infrastructure

Add Material BottomNavigationView to MainActivity as foundation for tabbed views.

#### 0.1 Update `activity_main.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- Main content area -->
    <FrameLayout
        android:id="@+id/content_frame"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_marginBottom="56dp" />

    <!-- Bottom Navigation -->
    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottom_navigation"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:layout_gravity="bottom"
        app:labelVisibilityMode="unlabeled"
        app:menu="@menu/bottom_nav_menu" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

#### 0.2 Create `bottom_nav_menu.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/nav_active"
        android:icon="@drawable/ic_notifications_24dp"
        android:title="@string/nav_active" />
    <item
        android:id="@+id/nav_upcoming"
        android:icon="@drawable/ic_schedule_24dp"
        android:title="@string/nav_upcoming" />
    <item
        android:id="@+id/nav_dismissed"
        android:icon="@drawable/ic_delete_24dp"
        android:title="@string/nav_dismissed" />
    <item
        android:id="@+id/nav_settings"
        android:icon="@drawable/ic_settings_24dp"
        android:title="@string/nav_settings" />
</menu>
```

#### 0.3 Update MainActivity Navigation

```kotlin
private fun setupBottomNavigation() {
    val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
    bottomNav.setOnItemSelectedListener { item ->
        when (item.itemId) {
            R.id.nav_active -> showActiveEvents()
            R.id.nav_upcoming -> showUpcomingEvents()
            R.id.nav_dismissed -> showDismissedEvents()
            R.id.nav_settings -> openSettings()
        }
        true
    }
}
```

### Phase 1: Integrate Dismissed Events Tab

Move `DismissedEventsActivity` content into MainActivity as a tab.

#### 1.1 Create Fragment or ViewSwitcher Pattern

Option A: **Fragments** - More standard Android pattern
Option B: **ViewSwitcher/ViewFlipper** - Simpler, keeps existing code structure

Recommend starting with ViewSwitcher for minimal disruption:

```kotlin
private lateinit var viewSwitcher: ViewSwitcher
private var currentView: Int = VIEW_ACTIVE

companion object {
    const val VIEW_ACTIVE = 0
    const val VIEW_UPCOMING = 1
    const val VIEW_DISMISSED = 2
}

private fun showDismissedEvents() {
    if (currentView != VIEW_DISMISSED) {
        currentView = VIEW_DISMISSED
        reloadDismissedData()
        // Switch to dismissed RecyclerView
    }
}
```

#### 1.2 Share RecyclerView/Adapter Pattern

Both Active and Dismissed use similar card layouts. Consider:
- Shared base adapter
- Or: Single adapter with view type differentiation

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
private const val UPCOMING_EVENTS_FIXED_HOURS_KEY = "upcoming_events_fixed_hours"
private const val SELECTED_CALENDAR_IDS_KEY = "selected_calendar_ids"

// Defaults
internal const val DEFAULT_UPCOMING_EVENTS_CUTOFF_HOUR = 10
internal const val DEFAULT_UPCOMING_EVENTS_FIXED_HOURS = 8

/** Lookahead mode: "cutoff" = next morning cutoff, "fixed" = fixed hours */
var upcomingEventsMode: String
    get() = getString(UPCOMING_EVENTS_MODE_KEY, "cutoff")
    set(value) = setString(UPCOMING_EVENTS_MODE_KEY, value)

/** Hour of day for morning cutoff (0-12, default 10) */
var upcomingEventsCutoffHour: Int
    get() = getInt(UPCOMING_EVENTS_CUTOFF_HOUR_KEY, DEFAULT_UPCOMING_EVENTS_CUTOFF_HOUR)
    set(value) = setInt(UPCOMING_EVENTS_CUTOFF_HOUR_KEY, value)

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
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        // Set to cutoff hour, 0 minutes
        calendar.set(Calendar.HOUR_OF_DAY, cutoffHour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // If we're past the cutoff hour, move to tomorrow
        if (currentHour >= cutoffHour) {
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

### Phase 6: Pre-Snooze Action

#### 6.1 Handle Upcoming Event Tap

When user taps an upcoming event, show snooze dialog:

```kotlin
override fun onItemClick(v: View, position: Int, eventId: Long) {
    if (isUpcomingView) {
        val event = adapter.getEventAtPosition(position, eventId)
        if (event != null) {
            showPreSnoozeDialog(event)
        }
    } else {
        // Existing click handling
    }
}

private fun showPreSnoozeDialog(event: EventAlertRecord) {
    // Show snooze time picker
    // On selection: call handleUpcomingEventSnooze()
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
    
    <ListPreference
        android:key="upcoming_events_cutoff_hour"
        android:title="@string/upcoming_events_cutoff_hour"
        android:entries="@array/upcoming_events_cutoff_entries"
        android:entryValues="@array/upcoming_events_cutoff_values"
        android:defaultValue="10" />
    
    <ListPreference
        android:key="upcoming_events_fixed_hours"
        android:title="@string/upcoming_events_fixed_hours"
        android:entries="@array/upcoming_events_fixed_hours_entries"
        android:entryValues="@array/upcoming_events_fixed_hours_values"
        android:defaultValue="8" />

</PreferenceCategory>
```

#### 7.2 Add String Resources

```xml
<string name="upcoming_events_category">Upcoming Events</string>
<string name="upcoming_events_mode">Lookahead mode</string>
<string name="upcoming_events_cutoff_hour">Morning cutoff</string>
<string name="upcoming_events_fixed_hours">Hours ahead</string>
<string name="upcoming">Upcoming</string>
<string name="alert_at">Alert at %s</string>
<string name="nav_active">Active</string>
<string name="nav_upcoming">Upcoming</string>
<string name="nav_dismissed">Dismissed</string>
<string name="nav_settings">Settings</string>
<string name="select_calendars">Select Calendars</string>
<string name="all_calendars">All Calendars</string>

<string-array name="upcoming_events_mode_entries">
    <item>Until morning cutoff</item>
    <item>Fixed hours ahead</item>
</string-array>
<string-array name="upcoming_events_mode_values">
    <item>cutoff</item>
    <item>fixed</item>
</string-array>

<string-array name="upcoming_events_cutoff_entries">
    <item>6:00 AM</item>
    <item>7:00 AM</item>
    <item>8:00 AM</item>
    <item>9:00 AM</item>
    <item>10:00 AM</item>
    <item>11:00 AM</item>
    <item>12:00 PM</item>
</string-array>
<string-array name="upcoming_events_cutoff_values">
    <item>6</item>
    <item>7</item>
    <item>8</item>
    <item>9</item>
    <item>10</item>
    <item>11</item>
    <item>12</item>
</string-array>

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

## Files to Modify/Create

### New Files

| File | Purpose |
|------|---------|
| `android/app/src/main/res/menu/bottom_nav_menu.xml` | Bottom navigation menu items |
| `android/app/src/main/java/com/github/quarck/calnotify/ui/CalendarFilterBottomSheet.kt` | Calendar multi-select bottom sheet |
| `android/app/src/main/java/com/github/quarck/calnotify/upcoming/UpcomingEventsLookahead.kt` | Lookahead cutoff calculation |
| `android/app/src/main/java/com/github/quarck/calnotify/upcoming/UpcomingEventsProvider.kt` | Fetch and enrich upcoming events |
| `android/app/src/test/java/com/github/quarck/calnotify/upcoming/UpcomingEventsLookaheadTest.kt` | Unit tests for lookahead logic |
| `android/app/src/androidTest/java/com/github/quarck/calnotify/upcoming/UpcomingEventsProviderTest.kt` | Integration tests for provider |

### Modified Files

| File | Changes |
|------|---------|
| `activity_main.xml` | Add BottomNavigationView, AppBarLayout with collapsing filter chips |
| `MainActivity.kt` | Add bottom nav handling, view switching, filter chip setup, calendar filter |
| `Settings.kt` | Add lookahead settings + calendar filter selection |
| `EventListAdapter.kt` | Handle upcoming events display mode |
| `strings.xml` | Add new string resources for nav, filters, upcoming |
| Preferences XML | Add lookahead settings UI |

### Files to Eventually Deprecate

| File | Reason |
|------|--------|
| `DismissedEventsActivity.kt` | Functionality moves to MainActivity tab |
| `activity_dismissed_events.xml` | Layout merged into main activity |

## Testing Plan

### Unit Tests (Robolectric)

#### `UpcomingEventsLookaheadTest.kt`

```kotlin
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
```

### Integration Tests (AndroidTest)

#### `UpcomingEventsProviderTest.kt`

```kotlin
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
    // Given: Event in MonitorStorage
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
```

### UI Tests

#### Bottom Navigation Tests

```kotlin
@Test
fun `bottom nav shows all four items`() {
    // Given: MainActivity launched
    // Then: Bottom nav shows Active, Upcoming, Dismissed, Settings icons
}

@Test
fun `tapping Upcoming tab switches view`() {
    // Given: On Active tab
    // When: Tap Upcoming icon
    // Then: View switches to upcoming events
}

@Test
fun `tapping Dismissed tab shows bin content`() {
    // Given: Dismissed events exist
    // When: Tap Dismissed icon
    // Then: Shows dismissed events list
}
```

#### Filter Chip Tests

```kotlin
@Test
fun `calendar filter chip opens bottom sheet`() {
    // Given: MainActivity with filter chips visible
    // When: Tap Calendar chip
    // Then: Bottom sheet opens with calendar list
}

@Test
fun `selecting calendars filters event list`() {
    // Given: Events from multiple calendars
    // When: Select only one calendar in filter
    // Then: Only events from that calendar shown
}

@Test
fun `filter chips collapse on scroll down`() {
    // Given: Filter chips visible
    // When: Scroll event list down
    // Then: Filter chips animate out of view
}

@Test
fun `filter chips reappear on scroll up`() {
    // Given: Filter chips collapsed
    // When: Scroll event list up
    // Then: Filter chips animate back into view
}
```

#### Pre-Snooze Tests

```kotlin
@Test
fun `tapping upcoming event shows snooze dialog`() {
    // Given: On Upcoming tab with event
    // When: Tap event
    // Then: Snooze time picker dialog appears
}

@Test
fun `pre-snoozed event moves to Active as snoozed`() {
    // Given: Upcoming event
    // When: Pre-snooze to +2 hours
    // Then: Event disappears from Upcoming, appears in Active as snoozed
}
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

- The `MonitorStorage.getAlertsForAlertRange()` method already exists and is tested
- `CalendarProvider.getEvent()` may return null if the calendar event was deleted—handle gracefully
- Consider caching enriched events to avoid repeated CalendarProvider queries on scroll
- The enrichment step adds ~1-2ms per event; for 10 events this is negligible
- BottomNavigationView supports up to 5 items; we're using 4 (Active, Upcoming, Dismissed, Settings)
- `labelVisibilityMode="unlabeled"` hides text labels for icon-only nav
- CoordinatorLayout + AppBarLayout provides built-in scroll-to-collapse behavior

## Implementation Order Recommendation

Given the scope, consider this order for incremental delivery:

1. **Phase 0** - Bottom nav infrastructure (biggest change, enables everything else)
2. **Phase 1** - Dismissed tab (proves the pattern works, reuses existing code)
3. **Phase 4** - Upcoming data provider (can test independently)
4. **Phase 5** - Upcoming tab (wires it together)
5. **Phase 2** - Collapsing filter bar (polish)
6. **Phase 3** - Calendar multi-select (polish)
7. **Phase 6** - Pre-snooze (feature complete)
8. **Phase 7** - Settings UI (configuration)

Each phase is independently testable and deliverable.

## Related Work

- `docs/architecture/calendar_monitoring.md` - How MonitorStorage is populated
- `docs/dev_completed/constructor-mocking-android.md` - Testing patterns for CalendarProvider
- `docs/dev_todo/dismissed_events_long_storage.md` - Related bin/history work
