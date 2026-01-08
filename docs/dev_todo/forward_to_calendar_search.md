# Forward to Calendar Search When Event Not Found

> **GitHub Issue**: [#66](https://github.com/williscool/CalendarNotification/issues/66)

## Summary

| Item | Details |
|------|---------|
| **Problem** | "Open in Calendar" silently fails when event was deleted from system calendar |
| **Solution** | Fallback chain: check if exists → open event OR open calendar at event time + toast |
| **Call sites** | 8 total (6 in ViewEventActivityNoRecents, 1 in notification PendingIntent, 1 ViewEventById) |
| **New tests** | `CalendarIntentsRobolectricTest.kt` (new), updates to `ViewEventActivityRobolectricTest.kt` |
| **Complexity** | Low - mostly routing through a new helper method |

## Problem Statement

When a user tries to open an event in the calendar app, but the event no longer exists in the system calendar (e.g., after phone migration, backup restore, or manual deletion), the calendar app shows an unhelpful error or nothing at all.

**Common scenarios**:
- Phone backup/restore where calendar events didn't sync properly
- Event was deleted from calendar but notification still exists
- Calendar account removed/re-added
- Sync issues between devices

## Current Behavior

Currently, `CalendarIntents.viewCalendarEvent()` opens the calendar with a specific event ID URI:
```kotlin
content://com.android.calendar/events/{eventId}
```

If the event doesn't exist, Google Calendar silently fails or shows a generic error.

## Proposed Solution: Fallback Chain

Since **Google Calendar doesn't have a public search intent**, we'll implement a fallback chain:

1. **First**: Check if event exists in system calendar via `CalendarProvider.getEvent()`
2. **If exists**: Open event directly (current behavior)
3. **If not found**: Open calendar at the event's scheduled time + show toast
   - URI: `content://com.android.calendar/time/{instanceStartTimeMillis}`
   - Toast: "Event not found in calendar - showing calendar at event time"

## Locations to Update

### All `viewCalendarEvent` Call Sites (8 total)

| # | File | Line | Context | Action |
|---|------|------|---------|--------|
| 1 | `ViewEventActivityNoRecents.kt` | 101 | `ViewEventById.run()` | Update - only has eventId, need to pass title/time |
| 2 | `ViewEventActivityNoRecents.kt` | 107 | `ViewEventByEvent.run()` | Update - has full event, easy |
| 3 | `ViewEventActivityNoRecents.kt` | 379 | FAB click for repeating events | Update - has `event` |
| 4 | `ViewEventActivityNoRecents.kt` | 500 | Menu "Open in Calendar" | Update - has `event` |
| 5 | `ViewEventActivityNoRecents.kt` | 558 | `OnButtonEventDetailsClick` | Update - has `event` |
| 6 | `ViewEventActivityNoRecents.kt` | 855 | After edit, viewAfterEdit setting | Update - has `event` |
| 7 | `ViewEventActivityNoRecents.kt` | 875 | After edit with new event ID | Tricky - new event, might not need fallback |
| 8 | `EventNotificationManager.kt` | 1085 | Notification content intent | **Note**: This is a PendingIntent, special handling needed |

### Notification Intent (Special Case)

`EventNotificationManager.kt` line 1085 creates a `PendingIntent` for when users tap notifications. This can't do a pre-check since the intent is created ahead of time. Options:

**Option A**: Route notification tap through an intermediate activity that does the check
**Option B**: Leave notification tap as-is (current behavior) - user can use "Open in Calendar" from snooze screen
**Option C**: Create a `ViewCalendarActivity` that wraps the logic

**Recommendation**: Start with Option B (leave notifications as-is), tackle separately if needed.

## Implementation Plan

### Phase 1: Add Infrastructure to `CalendarIntents.kt`

```kotlin
object CalendarIntents {
    // ... existing code ...

    /**
     * Opens calendar at a specific time (when event not found)
     */
    fun viewCalendarAtTime(context: Context, timeMillis: Long) {
        val uri = Uri.parse("content://com.android.calendar/time/$timeMillis")
        val intent = Intent(Intent.ACTION_VIEW).setData(uri)
        context.startActivity(intent)
    }

    /**
     * Attempts to view event, falls back to time-based view if event not found.
     * @return true if event was found, false if fallback was used
     */
    fun viewCalendarEventWithFallback(
        context: Context,
        calendarProvider: CalendarProviderInterface,
        event: EventAlertRecord
    ): Boolean {
        // Check if event exists in system calendar
        val calendarEvent = calendarProvider.getEvent(context, event.eventId)
        
        if (calendarEvent != null) {
            // Event exists, open normally
            viewCalendarEvent(context, event)
            return true
        } else {
            // Event not found, fallback to time-based view
            DevLog.info(LOG_TAG, "Event ${event.eventId} not found in calendar, " +
                "falling back to time view at ${event.instanceStartTime}")
            viewCalendarAtTime(context, event.instanceStartTime)
            return false
        }
    }
}
```

### Phase 2: Add String Resources

```xml
<!-- strings.xml -->
<string name="event_not_found_opening_calendar_at_time">Event not found in calendar - showing calendar at event time</string>
```

### Phase 3: Update Call Sites

Create a helper in `ViewEventActivityNoRecents.kt`:

```kotlin
private fun openEventInCalendar(event: EventAlertRecord) {
    val found = CalendarIntents.viewCalendarEventWithFallback(this, calendarProvider, event)
    if (!found) {
        Toast.makeText(this, R.string.event_not_found_opening_calendar_at_time, Toast.LENGTH_LONG).show()
    }
}
```

Then update all 6 direct call sites in `ViewEventActivityNoRecents.kt` to use this helper.

### Phase 4: Handle `ViewEventById` Case

The `ViewEventById` class only has `eventId`, not the full event. Options:
1. Query the event from EventsStorage first
2. Pass the full event instead of just ID
3. Add a method that takes eventId + fallbackTime

## Pros and Cons of Pre-Check Approach

### Pros ✅
1. **Better UX**: User sees calendar at the right time instead of an error
2. **Informative**: Toast tells user what happened
3. **Graceful degradation**: App handles edge cases instead of failing silently
4. **Debugging**: Logs help diagnose sync issues
5. **No extra network**: Query is local to device

### Cons ⚠️
1. **Extra query**: One additional `ContentResolver.query()` per open
   - **Mitigation**: Query is fast (single row by ID, indexed)
   - **Mitigation**: Only happens on user action (button tap), not in loops
2. **Slight code complexity**: Need to pass `calendarProvider` to `CalendarIntents`
   - **Mitigation**: Already available in all call sites via `calendarProvider` property
3. **Edge case**: User might be confused why calendar opens to "wrong" view
   - **Mitigation**: Toast explains what happened

### Verdict
The pre-check is a **pure win** for UX. The query cost is negligible (single indexed lookup on user action). Current behavior of silently failing is much worse.

## Testing Strategy

### Robolectric Tests (Preferred for Intent Testing)

Robolectric is **well-suited** for testing intent creation and launching because:
- `ShadowActivity.getNextStartedActivity()` captures intents started by the activity
- `CalendarProvider` is already mocked in `UITestFixtureRobolectric`
- Can control mock responses for `getEvent()` to simulate found/not-found scenarios

#### New Test File: `CalendarIntentsRobolectricTest.kt`

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class CalendarIntentsRobolectricTest {

    @Test
    fun viewCalendarAtTime_creates_correct_intent() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).create().get()
        val testTime = 1704067200000L // Jan 1, 2024 00:00:00 UTC
        
        CalendarIntents.viewCalendarAtTime(activity, testTime)
        
        val shadow = shadowOf(activity)
        val intent = shadow.nextStartedActivity
        
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("content://com.android.calendar/time/$testTime", intent.data.toString())
    }
    
    @Test
    fun viewCalendarEventWithFallback_returns_true_when_event_exists() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).create().get()
        val mockProvider = mockk<CalendarProviderInterface>()
        val event = createTestEvent()
        
        every { mockProvider.getEvent(any(), event.eventId) } returns mockk<EventRecord>()
        
        val result = CalendarIntents.viewCalendarEventWithFallback(activity, mockProvider, event)
        
        assertTrue(result)
        val shadow = shadowOf(activity)
        val intent = shadow.nextStartedActivity
        assertTrue(intent.data.toString().contains("/events/${event.eventId}"))
    }
    
    @Test
    fun viewCalendarEventWithFallback_returns_false_and_opens_time_view_when_event_missing() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).create().get()
        val mockProvider = mockk<CalendarProviderInterface>()
        val event = createTestEvent(instanceStartTime = 1704067200000L)
        
        every { mockProvider.getEvent(any(), event.eventId) } returns null
        
        val result = CalendarIntents.viewCalendarEventWithFallback(activity, mockProvider, event)
        
        assertFalse(result)
        val shadow = shadowOf(activity)
        val intent = shadow.nextStartedActivity
        assertEquals("content://com.android.calendar/time/1704067200000", intent.data.toString())
    }
}
```

#### Update Existing: `ViewEventActivityRobolectricTest.kt`

Add tests for the "Open in Calendar" behavior:

```kotlin
@Test
fun openEventInCalendar_shows_toast_when_event_not_found_in_system_calendar() {
    val event = fixture.createEvent(title = "Orphaned Event")
    
    // Mock CalendarProvider.getEvent to return null (event not in system calendar)
    every { CalendarProvider.getEvent(any(), event.eventId) } returns null
    
    val scenario = fixture.launchViewEventActivity(event)
    
    scenario.onActivity { activity ->
        // Trigger "Open in Calendar" action
        activity.OnButtonEventDetailsClick(null)
        
        // Verify fallback intent was started
        val shadow = shadowOf(activity)
        val intent = shadow.nextStartedActivity
        assertTrue(intent.data.toString().contains("/time/"))
    }
    
    // Verify toast was shown (can use ShadowToast)
    val latestToast = ShadowToast.getLatestToast()
    assertNotNull(latestToast)
    
    scenario.close()
}

@Test
fun openEventInCalendar_opens_event_directly_when_found_in_system_calendar() {
    val event = fixture.createEvent(title = "Normal Event")
    
    // Mock CalendarProvider.getEvent to return a valid event
    every { CalendarProvider.getEvent(any(), event.eventId) } returns mockk<EventRecord>()
    
    val scenario = fixture.launchViewEventActivity(event)
    
    scenario.onActivity { activity ->
        activity.OnButtonEventDetailsClick(null)
        
        val shadow = shadowOf(activity)
        val intent = shadow.nextStartedActivity
        assertTrue(intent.data.toString().contains("/events/${event.eventId}"))
    }
    
    scenario.close()
}
```

### Instrumentation Tests (For Real Calendar Integration)

These test with the actual Android calendar provider on a real/emulator device.

#### Update Existing: `ViewEventActivityTest.kt`

```kotlin
@Test
fun openInCalendar_menu_action_opens_calendar() {
    val event = fixture.createEvent(title = "Test Event")
    val scenario = fixture.launchViewEventActivity(event)
    
    // Open menu and click "Open in Calendar"
    withId(R.id.snooze_view_menu).click()
    withText(R.string.open_in_calendar).click()
    
    // Activity should finish after opening calendar
    scenario.onActivity { activity ->
        assertTrue(activity.isFinishing)
    }
    
    scenario.close()
}

@Test
fun openInCalendar_shows_toast_for_deleted_event() {
    // Create event, then delete from calendar (but keep in app storage)
    val event = fixture.createEvent(title = "Soon To Be Deleted")
    fixture.deleteEventFromSystemCalendar(event.eventId) // Helper to delete from calendar only
    
    val scenario = fixture.launchViewEventActivity(event)
    
    withId(R.id.snooze_view_menu).click()
    withText(R.string.open_in_calendar).click()
    
    // Should show toast about event not found
    // Note: Toast verification in Espresso requires ToastMatcher or similar
    
    scenario.close()
}
```

### Manual Testing Checklist

| Scenario | Steps | Expected Behavior |
|----------|-------|-------------------|
| Normal event | Click "Open in Calendar" | Opens event in calendar app |
| Deleted event | Delete event in Google Calendar, then click "Open in Calendar" | Toast + opens calendar at event time |
| Backup restore | Restore phone from backup, open old notification | Toast + opens calendar at event time |
| Account removed | Remove Google account, click "Open in Calendar" | Toast + opens calendar at event time |
| Repeating event instance | For a repeating event, delete one instance | Should still work (tests instance handling) |

## Files to Modify

### Production Code
1. `CalendarIntents.kt` - Add `viewCalendarAtTime()` and `viewCalendarEventWithFallback()`
2. `ViewEventActivityNoRecents.kt` - Add `openEventInCalendar()` helper, update 6 call sites
3. `strings.xml` - Add `event_not_found_opening_calendar_at_time`
4. Translation files (`values-ru`, `values-uk`, `values-de`, `values-pl`) - Add translated string

### Test Code
| File | Action | Notes |
|------|--------|-------|
| `CalendarIntentsRobolectricTest.kt` | **New** | Unit tests for intent creation and fallback logic |
| `ViewEventActivityRobolectricTest.kt` | **Update** | Add tests for open calendar with fallback |
| `ViewEventActivityTest.kt` | **Update** | Add instrumentation tests for real calendar integration |
| `UITestFixtureRobolectric.kt` | **Update** (maybe) | May need to expose CalendarProvider mock control |
| `UITestFixture.kt` | **Update** (maybe) | May need helper to delete from system calendar |

### Robolectric Testing Notes

**What works well:**
- Testing intent creation (`ShadowActivity.getNextStartedActivity()`)
- Mocking `CalendarProvider.getEvent()` responses
- Testing toast display (`ShadowToast`)
- Testing activity state changes

**Limitations:**
- Cannot test actual Google Calendar app behavior
- Cannot verify calendar app receives and handles the intent correctly
- Mock setup required for `CalendarProvider` (already done in `UITestFixtureRobolectric`)

## Open Questions

1. **Should we handle the notification PendingIntent case?** (Recommendation: defer to separate issue)
2. **Should the fallback also try to search Google Calendar web?** (Recommendation: no, keep it simple)
3. **Should we add a setting to disable the pre-check?** (Recommendation: no, it's always better)

## Related Issues

- This complements the dismissed events long storage feature (`docs/dev_todo/dismissed_events_long_storage.md`)
- Related to event deletion cleanup (`docs/dev_todo/event_deletion_issues.md`)
