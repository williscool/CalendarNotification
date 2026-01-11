# Events View Lookahead

## Background

**GitHub Issues:** [#92](https://github.com/williscool/CalendarNotification/issues/92), [#11](https://github.com/williscool/CalendarNotification/issues/11)

Users want to see upcoming calendar events in the main events list before their notifications fire, allowing them to:
- Pre-snooze events to a later time
- Plan their day by seeing what's coming
- Not be surprised by imminent events

Currently, events only appear in the list after their reminder fires. The app already scans up to 30 days ahead for scheduling purposes (`MonitorStorage`), so the data exists—it's just not exposed in the UI.

## Goal

Display upcoming (not yet fired) events in the main events view with:
- Clear visual distinction from active/snoozed events
- Ability to pre-snooze them
- Configurable lookahead window

## Current Architecture

### Existing Components

| Component | Purpose | Data Structure |
|-----------|---------|----------------|
| `EventsStorage` | Stores fired/active events (shown in UI) | `EventAlertRecord` (full details) |
| `MonitorStorage` | Stores upcoming alerts for scheduling | `MonitorEventAlertEntry` (minimal: eventId, alertTime, instanceStartTime, instanceEndTime, isAllDay, wasHandled) |
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

### UI Approach: Inline Display (MVP)

**Chosen approach:** Display upcoming events in the same list as active events, distinguished by:
- The `snoozedUntil` text area showing "Upcoming" or the alert time (e.g., "Alert at 2:30 PM")
- Possibly a subtle background tint or icon

**Future consideration:** Tabs could provide better separation but adds complexity. Start inline, evaluate UX.

## Implementation Plan

### Phase 1: Settings & Lookahead Calculation

#### 1.1 Add Settings to `Settings.kt`

```kotlin
// In companion object - Keys
private const val SHOW_UPCOMING_EVENTS_KEY = "show_upcoming_events"
private const val UPCOMING_EVENTS_MODE_KEY = "upcoming_events_mode"
private const val UPCOMING_EVENTS_CUTOFF_HOUR_KEY = "upcoming_events_cutoff_hour"
private const val UPCOMING_EVENTS_FIXED_HOURS_KEY = "upcoming_events_fixed_hours"

// Defaults
internal const val DEFAULT_UPCOMING_EVENTS_CUTOFF_HOUR = 10
internal const val DEFAULT_UPCOMING_EVENTS_FIXED_HOURS = 8

// Properties
var showUpcomingEvents: Boolean
    get() = getBoolean(SHOW_UPCOMING_EVENTS_KEY, true)
    set(value) = setBoolean(SHOW_UPCOMING_EVENTS_KEY, value)

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

#### 1.2 Create `UpcomingEventsLookahead.kt`

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

### Phase 2: Data Provider

#### 2.1 Create `UpcomingEventsProvider.kt`

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
     * @return List of EventAlertRecord representing upcoming events
     */
    fun getUpcomingEvents(cutoffTime: Long): List<EventAlertRecord> {
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

### Phase 3: UI Integration

#### 3.1 Add Upcoming Events Display Mode Indicator

Create a sealed class or enum to distinguish event types in the adapter:

```kotlin
// In EventListAdapter.kt or new file
sealed class DisplayableEvent {
    data class Active(val event: EventAlertRecord) : DisplayableEvent()
    data class Snoozed(val event: EventAlertRecord) : DisplayableEvent()
    data class Upcoming(val event: EventAlertRecord) : DisplayableEvent()
}
```

#### 3.2 Modify `EventListAdapter.kt`

Update to accept and display upcoming events:

```kotlin
// Add field for upcoming events
private var upcomingEvents = arrayOf<EventAlertRecord>()

fun setEventsToDisplay(
    newEvents: Array<EventAlertRecord>? = null,
    newUpcomingEvents: Array<EventAlertRecord>? = null
) = synchronized(this) {
    if (newEvents != null) {
        allEvents = newEvents
    }
    if (newUpcomingEvents != null) {
        upcomingEvents = newUpcomingEvents
    }
    // Combine and filter...
    applySearchFilter()
}

override fun getItemCount(): Int = events.size + upcomingEvents.size

private fun isUpcomingPosition(position: Int): Boolean = position >= events.size

private fun getUpcomingEvent(position: Int): EventAlertRecord = 
    upcomingEvents[position - events.size]
```

#### 3.3 Update ViewHolder Binding

In `onBindViewHolder`, handle upcoming events:

```kotlin
if (isUpcomingPosition(position)) {
    val upcomingEvent = getUpcomingEvent(position)
    // Bind event data...
    
    // Show "Upcoming" or alert time instead of snoozed time
    holder.snoozedUntilText?.apply {
        visibility = View.VISIBLE
        text = formatUpcomingTime(upcomingEvent.alertTime)
        // Could use different color: setTextColor(...)
    }
    
    // Disable dismiss action for upcoming events
    // They can still be clicked to view or pre-snooze
}
```

#### 3.4 Update `MainActivity.reloadData()`

```kotlin
private fun reloadData() {
    background {
        // Existing event loading...
        val events = getEventsStorage(this).classCustomUse { db ->
            db.events.sortedWith(...)
        }
        
        // Load upcoming events if enabled
        val upcomingEvents = if (settings.showUpcomingEvents) {
            val lookahead = UpcomingEventsLookahead(settings, clock)
            val provider = UpcomingEventsProvider(this, CalendarProvider, clock)
            provider.getUpcomingEvents(lookahead.getCutoffTime()).toTypedArray()
        } else {
            emptyArray()
        }
        
        runOnUiThread {
            adapter.setEventsToDisplay(events, upcomingEvents)
            // ...
        }
    }
}
```

### Phase 4: Pre-Snooze Action

When user taps an upcoming event, allow snoozing to a time after it would normally fire:

```kotlin
// In SnoozeActivity or similar
fun handleUpcomingEventSnooze(event: EventAlertRecord, snoozeUntil: Long) {
    // 1. Remove from MonitorStorage (mark as handled early)
    // 2. Add to EventsStorage with snoozedUntil set
    // 3. The event will appear as "snoozed" in the main list
}
```

### Phase 5: Settings UI

#### 5.1 Add Preferences XML

```xml
<!-- In appropriate preferences XML -->
<PreferenceCategory android:title="@string/upcoming_events_category">
    
    <SwitchPreferenceCompat
        android:key="show_upcoming_events"
        android:title="@string/show_upcoming_events"
        android:summary="@string/show_upcoming_events_summary"
        android:defaultValue="true" />
    
    <ListPreference
        android:key="upcoming_events_mode"
        android:title="@string/upcoming_events_mode"
        android:entries="@array/upcoming_events_mode_entries"
        android:entryValues="@array/upcoming_events_mode_values"
        android:defaultValue="cutoff"
        android:dependency="show_upcoming_events" />
    
    <ListPreference
        android:key="upcoming_events_cutoff_hour"
        android:title="@string/upcoming_events_cutoff_hour"
        android:entries="@array/upcoming_events_cutoff_entries"
        android:entryValues="@array/upcoming_events_cutoff_values"
        android:defaultValue="10"
        android:dependency="show_upcoming_events" />
        <!-- Only show when mode = "cutoff" -->
    
    <ListPreference
        android:key="upcoming_events_fixed_hours"
        android:title="@string/upcoming_events_fixed_hours"
        android:entries="@array/upcoming_events_fixed_hours_entries"
        android:entryValues="@array/upcoming_events_fixed_hours_values"
        android:defaultValue="8"
        android:dependency="show_upcoming_events" />
        <!-- Only show when mode = "fixed" -->

</PreferenceCategory>
```

#### 5.2 Add String Resources

```xml
<string name="upcoming_events_category">Upcoming Events</string>
<string name="show_upcoming_events">Show upcoming events</string>
<string name="show_upcoming_events_summary">Display events before their reminders fire</string>
<string name="upcoming_events_mode">Lookahead mode</string>
<string name="upcoming_events_cutoff_hour">Morning cutoff</string>
<string name="upcoming_events_fixed_hours">Hours ahead</string>
<string name="upcoming">Upcoming</string>
<string name="alert_at">Alert at %s</string>

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
| `android/app/src/main/java/com/github/quarck/calnotify/upcoming/UpcomingEventsLookahead.kt` | Lookahead cutoff calculation |
| `android/app/src/main/java/com/github/quarck/calnotify/upcoming/UpcomingEventsProvider.kt` | Fetch and enrich upcoming events |
| `android/app/src/test/java/com/github/quarck/calnotify/upcoming/UpcomingEventsLookaheadTest.kt` | Unit tests for lookahead logic |
| `android/app/src/androidTest/java/com/github/quarck/calnotify/upcoming/UpcomingEventsProviderTest.kt` | Integration tests for provider |

### Modified Files

| File | Changes |
|------|---------|
| `Settings.kt` | Add 4 new settings properties |
| `EventListAdapter.kt` | Handle upcoming events in display |
| `MainActivity.kt` | Load and pass upcoming events to adapter |
| `strings.xml` | Add new string resources |
| Preferences XML | Add settings UI |

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
```

### UI Tests

```kotlin
@Test
fun `upcoming events display with 'Upcoming' label`() {
    // Given: Upcoming event exists
    // When: MainActivity loads
    // Then: Event card shows "Upcoming" or alert time
}

@Test
fun `upcoming events hidden when setting disabled`() {
    // Given: showUpcomingEvents = false
    // When: MainActivity loads
    // Then: No upcoming events shown
}
```

## Future Enhancements

### Phase 6: Tabs UI (Optional)

If inline display feels crowded, add tabs:
- Tab 1: "Active" (current behavior)
- Tab 2: "Upcoming"

### Phase 7: Visual Distinction

- Subtle background tint for upcoming events
- Different icon or badge
- Collapsible "Upcoming" section header

### Phase 8: Smart Lookahead

- Automatically extend lookahead for days with many events
- "Show more" button to extend window on demand

## Notes

- The `MonitorStorage.getAlertsForAlertRange()` method already exists and is tested
- `CalendarProvider.getEvent()` may return null if the calendar event was deleted—handle gracefully
- Consider caching enriched events to avoid repeated CalendarProvider queries on scroll
- The enrichment step adds ~1-2ms per event; for 10 events this is negligible

## Related Work

- `docs/architecture/calendar_monitoring.md` - How MonitorStorage is populated
- `docs/dev_completed/constructor-mocking-android.md` - Testing patterns for CalendarProvider
