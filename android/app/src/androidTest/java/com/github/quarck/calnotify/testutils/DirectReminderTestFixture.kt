package com.github.quarck.calnotify.testutils

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.provider.CalendarContract
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull

/**
 * Specialized fixture for testing the direct reminder path
 * 
 * This fixture is configured to test the direct reminder flow where
 * calendar events are processed through direct EVENT_REMINDER broadcasts
 * instead of through the manual calendar monitor.
 */
class DirectReminderTestFixture {
    private val LOG_TAG = "DirectReminderFixture"
    
    private var baseFixture: BaseCalendarTestFixture
    private var eventTitle: String = "Test Direct Reminder Event"
    private var eventDescription: String = "Test Description"
    
    // Properties exposed from the base fixture
    val testCalendarId: Long get() = baseFixture.testCalendarId
    val testEventId: Long get() = baseFixture.testEventId
    val eventStartTime: Long get() = baseFixture.eventStartTime
    val reminderTime: Long get() = baseFixture.reminderTime
    
    /**
     * Creates a new DirectReminderTestFixture
     */
    constructor() {
        // Create the base fixture with default configuration
        baseFixture = BaseCalendarTestFixture.Builder().build()
        
        // Set up initial state for direct reminder testing
        setupDirectReminderState()
    }
    
    /**
     * Set up the initial state for direct reminder testing
     * - Calendar monitoring disabled (to test direct path)
     * - Test calendar and event created
     */
    private fun setupDirectReminderState() {
        DevLog.info(LOG_TAG, "Setting up direct reminder test state")
        
        // Get the context from the base fixture
        val context = baseFixture.contextProvider.fakeContext
        
        // Disable calendar rescan to test direct reminder path
        val settings = Settings(context)
        settings.setBoolean("enable_manual_calendar_rescan", false)
        
        // Set up test calendar
        baseFixture.setupTestCalendar()
        
        // Set up test event with calendar ID and default values
        setupTestEvent()
    }
    
    /**
     * Sets up a test event for the direct reminder test
     */
    private fun setupTestEvent() {
        DevLog.info(LOG_TAG, "Setting up test event for direct reminder test")
        
        // Get the context from the base fixture
        val context = baseFixture.contextProvider.fakeContext
        
        // Set current time to a known value
        val currentTime = baseFixture.timeProvider.testClock.currentTimeMillis()
        
        // Create event with a start time 1 minute in the future
        baseFixture.eventStartTime = currentTime + 60000
        baseFixture.reminderTime = baseFixture.eventStartTime - 30000
        
        DevLog.info(LOG_TAG, "Creating test event: startTime=${baseFixture.eventStartTime}, reminderTime=${baseFixture.reminderTime}")
        
        // Create the test event through the calendar provider
        baseFixture.testEventId = baseFixture.calendarProvider.createTestEvent(
            context,
            baseFixture.testCalendarId,
            eventTitle,
            eventDescription,
            baseFixture.eventStartTime,
            duration = 3600000,  // 1 hour duration
            reminderMinutes = 0  // Set reminder to 0 minutes since we're manually setting the alert time
        )
        
        // Verify the event was created
        assertTrue("Test event should be created", baseFixture.testEventId > 0)
        
        // Get the event details for verification
        val event = baseFixture.calendarProvider.getEvent(context, baseFixture.testEventId)
        assertNotNull("Event should exist in calendar provider", event)
        DevLog.info(LOG_TAG, "Event details: id=${event?.eventId}, title=${event?.details?.title}, " +
            "startTime=${event?.details?.startTime}, endTime=${event?.details?.endTime}")
        
        // Add the alert directly with our desired timing
        val alertValues = ContentValues().apply {
            put(CalendarContract.CalendarAlerts.EVENT_ID, baseFixture.testEventId)
            put(CalendarContract.CalendarAlerts.BEGIN, baseFixture.eventStartTime)
            put(CalendarContract.CalendarAlerts.END, baseFixture.eventStartTime + 3600000)  // 1 hour duration
            put(CalendarContract.CalendarAlerts.ALARM_TIME, baseFixture.reminderTime)
            put(CalendarContract.CalendarAlerts.STATE, CalendarContract.CalendarAlerts.STATE_SCHEDULED)
            put(CalendarContract.CalendarAlerts.MINUTES, 0)  // 0 minutes since we set ALARM_TIME directly
        }
        
        val alertUri = context.contentResolver.insert(CalendarContract.CalendarAlerts.CONTENT_URI, alertValues)
        assertNotNull("Alert should be created", alertUri)
        DevLog.info(LOG_TAG, "Created alert for event ${baseFixture.testEventId} at time ${baseFixture.reminderTime}")
        
        // Verify the alert exists in the calendar provider
        val alerts = CalendarProvider.getAlertByTime(
            context,
            baseFixture.reminderTime,
            skipDismissed = false,
            skipExpiredEvents = false
        )
        
        DevLog.info(LOG_TAG, "Found ${alerts.size} alerts at time ${baseFixture.reminderTime}")
        alerts.forEach { alert ->
            DevLog.info(LOG_TAG, "Alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, " +
                "startTime=${alert.startTime}, endTime=${alert.endTime}")
        }
        
        assertTrue("Alert should exist for the test event", 
            alerts.any { it.eventId == baseFixture.testEventId })
        
        DevLog.info(LOG_TAG, "Test event setup complete: eventId=${baseFixture.testEventId}, " +
            "startTime=${baseFixture.eventStartTime}, reminderTime=${baseFixture.reminderTime}")
    }
    
    /**
     * Simulates a direct reminder broadcast from the Calendar Provider
     * but with a simplified implementation that directly adds the event
     */
    fun simulateReminderBroadcast() {
        DevLog.info(LOG_TAG, "Simulating direct reminder broadcast")
        
        // Get the context from the base fixture
        val context = baseFixture.contextProvider.fakeContext
        
        // Advance time to the reminder time to ensure accurate timing
        baseFixture.timeProvider.setCurrentTime(baseFixture.reminderTime)
            
        // Create a direct reminder broadcast intent like the system would send
        val reminderIntent = Intent(CalendarContract.ACTION_EVENT_REMINDER).apply {
            data = ContentUris.withAppendedId(CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE, baseFixture.reminderTime)
        }

        // Simulate the broadcast by calling the CalendarMonitor directly
        // This mirrors what happens when a real reminder is broadcast by the system
        DevLog.info(LOG_TAG, "Calling CalendarMonitor.onProviderReminderBroadcast with alertTime=${baseFixture.reminderTime}")
        baseFixture.calendarProvider.mockCalendarMonitor.onProviderReminderBroadcast(context, reminderIntent)
        
        // Small delay for processing
        baseFixture.advanceTime(500)
    }
    
    /**
     * Verifies that the event was processed through the direct reminder path
     */
    fun verifyDirectReminderProcessed(): Boolean {
        return baseFixture.applicationComponents.verifyEventProcessed(
            baseFixture.testEventId,
            baseFixture.eventStartTime,
            eventTitle
        )
    }
    
    /**
     * Customize the test event
     */
    fun withTestEvent(title: String, description: String = "Test Description"): DirectReminderTestFixture {
        this.eventTitle = title
        this.eventDescription = description
        
        // Recreate the test event with the new title
        setupTestEvent()
        
        return this
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        baseFixture.cleanup()
    }
} 
