package com.github.quarck.calnotify.calendar

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.CalendarProviderTestFixture
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarProviderEventTest {
    private val LOG_TAG = "CalProviderEventTest"
    private lateinit var fixture: CalendarProviderTestFixture
    private var testCalendarId: Long = -1
    
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )
    
    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up test environment")
        fixture = CalendarProviderTestFixture()
        
        // Create a test calendar for all event tests
        testCalendarId = fixture.createCalendarWithSettings(
            "Test Calendar",
            "test@example.com",
            "test@example.com"
        )
        assertTrue("Test calendar should be created", testCalendarId > 0)
    }
    
    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test environment")
        fixture.cleanup()
    }
    
    @Test
    fun testCreateAndGetEvent() {
        DevLog.info(LOG_TAG, "Running testCreateAndGetEvent")
        
        // Create basic event
        val startTime = fixture.timeProvider.testClock.currentTimeMillis() + 3600000 // 1 hour from now
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "Test Event",
            "Test Description",
            startTime = startTime
        )
        
        // Verify event was created
        assertTrue("Event ID should be positive", eventId > 0)
        
        // Verify event properties
        fixture.verifyEvent(
            eventId = eventId,
            expectedTitle = "Test Event",
            expectedDescription = "Test Description",
            expectedStartTime = startTime,
            expectedDuration = 3600000 // 1 hour
        )
    }
    
    @Test
    fun testCreateRecurringEvent() {
        DevLog.info(LOG_TAG, "Running testCreateRecurringEvent")
        
        // Create recurring event
        val startTime = fixture.timeProvider.testClock.currentTimeMillis() + 3600000
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "Recurring Event",
            startTime = startTime,
            repeatingRule = "FREQ=DAILY;COUNT=5"
        )
        
        // Verify event was created
        assertTrue("Event ID should be positive", eventId > 0)
        
        // Get event and verify it's recurring
        val event = CalendarProvider.getEvent(fixture.contextProvider.fakeContext, eventId)
        assertNotNull("Event should exist", event)
        assertEquals("Event should have correct recurrence rule",
            "FREQ=DAILY;COUNT=5", event?.details?.repeatingRule)
        
        // Verify isRepeating flag
        val isRepeating = CalendarProvider.isRepeatingEvent(
            fixture.contextProvider.fakeContext,
            eventId
        )
        assertTrue("Event should be marked as repeating", isRepeating == true)
    }
    
    @Test
    fun testCreateAllDayEvent() {
        DevLog.info(LOG_TAG, "Running testCreateAllDayEvent")
        
        // Create all-day event
        val startTime = fixture.timeProvider.testClock.currentTimeMillis() + 86400000 // Tomorrow
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "All Day Event",
            startTime = startTime,
            duration = 86400000, // 24 hours
            isAllDay = true
        )
        
        // Verify event was created
        assertTrue("Event ID should be positive", eventId > 0)
        
        // Verify event properties
        fixture.verifyEvent(
            eventId = eventId,
            expectedTitle = "All Day Event",
            expectedStartTime = startTime,
            expectedDuration = 86400000,
            expectedIsAllDay = true
        )
    }
    
    @Test
    fun testUpdateEvent() {
        DevLog.info(LOG_TAG, "Running testUpdateEvent")
        
        // Create initial event
        val startTime = fixture.timeProvider.testClock.currentTimeMillis() + 3600000
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "Original Title",
            "Original Description",
            startTime = startTime
        )
        
        // Get original event
        val originalEvent = CalendarProvider.getEvent(fixture.contextProvider.fakeContext, eventId)
        assertNotNull("Original event should exist", originalEvent)
        
        // Create new details
        val newDetails = originalEvent!!.details.copy(
            title = "Updated Title",
            desc = "Updated Description",
            location = "New Location"
        )
        
        // Update event
        val updated = CalendarProvider.updateEvent(
            fixture.contextProvider.fakeContext,
            eventId,
            testCalendarId,
            originalEvent.details,
            newDetails
        )
        
        assertTrue("Event should be updated successfully", updated)
        
        // Verify updated properties
        fixture.verifyEvent(
            eventId = eventId,
            expectedTitle = "Updated Title",
            expectedDescription = "Updated Description",
            expectedLocation = "New Location"
        )
    }
    
    @Test
    fun testDeleteEvent() {
        DevLog.info(LOG_TAG, "Running testDeleteEvent")
        
        // Create event to delete
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "Event to Delete"
        )
        
        // Verify event exists
        val eventBefore = CalendarProvider.getEvent(fixture.contextProvider.fakeContext, eventId)
        assertNotNull("Event should exist before deletion", eventBefore)
        
        // Delete event
        val deleted = CalendarProvider.deleteEvent(fixture.contextProvider.fakeContext, eventId)
        assertTrue("Event should be deleted successfully", deleted)
        
        // Verify event no longer exists
        val eventAfter = CalendarProvider.getEvent(fixture.contextProvider.fakeContext, eventId)
        assertNull("Event should not exist after deletion", eventAfter)
    }
    
    @Test
    fun testMoveEvent() {
        DevLog.info(LOG_TAG, "Running testMoveEvent")
        
        // Create event
        val originalStartTime = fixture.timeProvider.testClock.currentTimeMillis() + 3600000
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "Event to Move",
            startTime = originalStartTime
        )
        
        // Move event forward by 1 hour
        val newStartTime = originalStartTime + 3600000
        val newEndTime = newStartTime + 3600000
        
        val moved = CalendarProvider.moveEvent(
            fixture.contextProvider.fakeContext,
            eventId,
            newStartTime,
            newEndTime
        )
        
        assertTrue("Event should be moved successfully", moved)
        
        // Verify new times
        fixture.verifyEvent(
            eventId = eventId,
            expectedStartTime = newStartTime,
            expectedDuration = 3600000
        )
    }
    
    @Test
    fun testEventWithLocation() {
        DevLog.info(LOG_TAG, "Running testEventWithLocation")
        
        // Create event with location
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "Event with Location",
            location = "Test Location"
        )
        
        // Verify event properties including location
        fixture.verifyEvent(
            eventId = eventId,
            expectedTitle = "Event with Location",
            expectedLocation = "Test Location"
        )
    }
    
    @Test
    fun testEventWithCustomTimezone() {
        DevLog.info(LOG_TAG, "Running testEventWithCustomTimezone")
        
        // Create event with custom timezone
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "Timezone Event",
            timeZone = "America/New_York"
        )
        
        // Get event and verify timezone
        val event = CalendarProvider.getEvent(fixture.contextProvider.fakeContext, eventId)
        assertNotNull("Event should exist", event)
        assertEquals("Event should have correct timezone",
            "America/New_York", event?.details?.timezone)
    }
} 