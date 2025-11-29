package com.github.quarck.calnotify.calendar

import android.content.Context
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.MockApplicationComponents
import com.github.quarck.calnotify.testutils.MockCalendarProvider
import com.github.quarck.calnotify.testutils.MockContextProvider
import com.github.quarck.calnotify.testutils.MockTimeProvider
import io.mockk.MockKAnnotations
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class CalendarProviderEventRobolectricTest {
    private val LOG_TAG = "CalProviderEventRobolectricTest"
    
    private lateinit var mockTimeProvider: MockTimeProvider
    private lateinit var mockContextProvider: MockContextProvider
    private lateinit var mockCalendarProvider: MockCalendarProvider
    private lateinit var mockComponents: MockApplicationComponents
    
    private val context: Context
        get() = mockContextProvider.fakeContext!!
    
    private var testCalendarId: Long = -1
    
    @Before
    fun setup() {
        ShadowLog.stream = System.out
        DevLog.info(LOG_TAG, "Setting up test environment")
        
        MockKAnnotations.init(this)
        unmockkAll()
        
        // Note: Permissions are granted automatically by MockContextProvider.setup()
        
        mockTimeProvider = MockTimeProvider()
        mockTimeProvider.setup()
        
        mockContextProvider = MockContextProvider(mockTimeProvider)
        mockContextProvider.setup()
        
        mockCalendarProvider = MockCalendarProvider(mockContextProvider, mockTimeProvider)
        mockCalendarProvider.setup()
        
        mockComponents = MockApplicationComponents(
            mockContextProvider,
            mockTimeProvider,
            mockCalendarProvider
        )
        mockComponents.setup()
        
        // Create a test calendar for all event tests
        testCalendarId = mockCalendarProvider.createTestCalendar(
            context,
            "Test Calendar",
            "test@example.com",
            "test@example.com"
        )
        assertTrue("Test calendar should be created", testCalendarId > 0)
    }
    
    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test environment")
        mockCalendarProvider.cleanup()
        mockContextProvider.cleanup()
        mockTimeProvider.cleanup()
        unmockkAll()
    }
    
    @Test
    fun testCreateAndGetEvent() {
        DevLog.info(LOG_TAG, "Running testCreateAndGetEvent")
        
        // Create basic event
        val startTime = mockTimeProvider.testClock.currentTimeMillis() + 3600000 // 1 hour from now
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Test Event",
            description = "Test Description",
            startTime = startTime
        )
        
        // Verify event was created
        assertTrue("Event ID should be positive", eventId > 0)
        
        // Get event and verify properties
        val event = CalendarProvider.getEvent(context, eventId)
        assertNotNull("Event should exist", event)
        assertEquals("Event title mismatch", "Test Event", event?.details?.title)
        assertEquals("Event description mismatch", "Test Description", event?.details?.desc)
        assertEquals("Event start time mismatch", startTime, event?.details?.startTime)
        
        // Verify duration (1 hour)
        val duration = (event?.details?.endTime ?: 0) - (event?.details?.startTime ?: 0)
        assertEquals("Event duration mismatch", 3600000, duration)
    }
    
    @Test
    fun testCreateRecurringEvent() {
        DevLog.info(LOG_TAG, "Running testCreateRecurringEvent")
        
        // Create recurring event
        val startTime = mockTimeProvider.testClock.currentTimeMillis() + 3600000
        val repeatingRule = "FREQ=DAILY;COUNT=5"
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Recurring Event",
            startTime = startTime,
            repeatingRule = repeatingRule
        )
        
        // Verify event was created
        assertTrue("Event ID should be positive", eventId > 0)
        
        // Get event and verify it's recurring
        val event = CalendarProvider.getEvent(context, eventId)
        assertNotNull("Event should exist", event)
        assertEquals("Event should have correct recurrence rule",
            repeatingRule, event?.details?.repeatingRule)
        
        // Verify isRepeating flag
        val isRepeating = CalendarProvider.isRepeatingEvent(context, eventId)
        assertTrue("Event should be marked as repeating", isRepeating == true)
    }
    
    @Test
    fun testCreateAllDayEvent() {
        DevLog.info(LOG_TAG, "Running testCreateAllDayEvent")
        
        // Create all-day event
        val startTime = mockTimeProvider.testClock.currentTimeMillis() + 86400000 // Tomorrow
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "All Day Event",
            startTime = startTime,
            duration = 86400000, // 24 hours
            isAllDay = true
        )
        
        // Verify event was created
        assertTrue("Event ID should be positive", eventId > 0)
        
        // Get the actual event to see what the Calendar Provider did with the time
        val event = CalendarProvider.getEvent(context, eventId)
        assertNotNull("Event should exist", event)
        
        // All-day events are normalized to midnight in UTC by the Calendar Provider
        // So we need to verify the event exists and is marked as all-day,
        // but we can't rely on exact time matching
        DevLog.info(LOG_TAG, "Expected start time: $startTime, Actual start time: ${event?.details?.startTime}")
        
        // Verify all-day flag and duration instead of exact start time
        assertTrue("Event should be marked as all-day", event?.details?.isAllDay == true)
        
        // The duration should still be roughly one day (allow for minor adjustments)
        val duration = (event?.details?.endTime ?: 0) - (event?.details?.startTime ?: 0)
        assertTrue("Event duration should be approximately 24 hours", 
                   duration >= 86000000 && duration <= 87000000)
    }
    
    @Test
    fun testUpdateEvent() {
        DevLog.info(LOG_TAG, "Running testUpdateEvent")
        
        // Create initial event
        val startTime = mockTimeProvider.testClock.currentTimeMillis() + 3600000
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Original Title",
            description = "Original Description",
            startTime = startTime
        )
        
        // Get original event
        val originalEvent = CalendarProvider.getEvent(context, eventId)
        assertNotNull("Original event should exist", originalEvent)
        
        // Create new details
        val newDetails = originalEvent!!.details.copy(
            title = "Updated Title",
            desc = "Updated Description",
            location = "New Location"
        )
        
        // Update event
        val updated = CalendarProvider.updateEvent(
            context,
            eventId,
            testCalendarId,
            originalEvent.details,
            newDetails
        )
        
        assertTrue("Event should be updated successfully", updated)
        
        // Verify updated properties
        val updatedEvent = CalendarProvider.getEvent(context, eventId)
        assertNotNull("Updated event should exist", updatedEvent)
        assertEquals("Event title mismatch", "Updated Title", updatedEvent?.details?.title)
        assertEquals("Event description mismatch", "Updated Description", updatedEvent?.details?.desc)
        assertEquals("Event location mismatch", "New Location", updatedEvent?.details?.location)
    }
    
    @Test
    fun testDeleteEvent() {
        DevLog.info(LOG_TAG, "Running testDeleteEvent")
        
        // Create event to delete
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Event to Delete"
        )
        
        // Verify event exists
        val eventBefore = CalendarProvider.getEvent(context, eventId)
        assertNotNull("Event should exist before deletion", eventBefore)
        
        // Delete event
        val deleted = CalendarProvider.deleteEvent(context, eventId)
        assertTrue("Event should be deleted successfully", deleted)
        
        // Verify event no longer exists
        val eventAfter = CalendarProvider.getEvent(context, eventId)
        assertNull("Event should not exist after deletion", eventAfter)
    }
    
    @Test
    fun testMoveEvent() {
        DevLog.info(LOG_TAG, "Running testMoveEvent")
        
        // Create event
        val originalStartTime = mockTimeProvider.testClock.currentTimeMillis() + 3600000
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Event to Move",
            startTime = originalStartTime
        )
        
        // Move event forward by 1 hour
        val newStartTime = originalStartTime + 3600000
        val newEndTime = newStartTime + 3600000
        
        val moved = CalendarProvider.moveEvent(
            context,
            eventId,
            newStartTime,
            newEndTime
        )
        
        assertTrue("Event should be moved successfully", moved)
        
        // Verify new times
        val movedEvent = CalendarProvider.getEvent(context, eventId)
        assertNotNull("Moved event should exist", movedEvent)
        assertEquals("Event start time mismatch", newStartTime, movedEvent?.details?.startTime)
        val duration = (movedEvent?.details?.endTime ?: 0) - (movedEvent?.details?.startTime ?: 0)
        assertEquals("Event duration mismatch", 3600000, duration)
    }
    
    @Test
    fun testEventWithLocation() {
        DevLog.info(LOG_TAG, "Running testEventWithLocation")
        
        // Create event with location
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Event with Location",
            location = "Test Location"
        )
        
        // Verify event properties including location
        val event = CalendarProvider.getEvent(context, eventId)
        assertNotNull("Event should exist", event)
        assertEquals("Event title mismatch", "Event with Location", event?.details?.title)
        assertEquals("Event location mismatch", "Test Location", event?.details?.location)
    }
    
    @Test
    fun testEventWithCustomTimezone() {
        DevLog.info(LOG_TAG, "Running testEventWithCustomTimezone")
        
        // Create event with custom timezone
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Timezone Event",
            timeZone = "America/New_York"
        )
        
        // Get event and verify timezone
        val event = CalendarProvider.getEvent(context, eventId)
        assertNotNull("Event should exist", event)
        assertEquals("Event should have correct timezone",
            "America/New_York", event?.details?.timezone)
    }
}

