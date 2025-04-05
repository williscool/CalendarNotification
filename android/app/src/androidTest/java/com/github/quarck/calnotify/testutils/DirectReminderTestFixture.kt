package com.github.quarck.calnotify.testutils

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.logs.DevLog

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
        DevLog.info(LOG_TAG, "Disabled calendar rescan")
        
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
        
        // Create the test event through the calendar provider
        baseFixture.testEventId = baseFixture.calendarProvider.createTestEvent(
            context,
            baseFixture.testCalendarId,
            eventTitle,
            eventDescription,
            baseFixture.eventStartTime
        )
        
        // Mock event details and event alert behavior
        baseFixture.calendarProvider.mockEventDetails(
            baseFixture.testEventId,
            baseFixture.eventStartTime,
            eventTitle
        )
        
        baseFixture.calendarProvider.mockEventAlerts(
            baseFixture.testEventId,
            baseFixture.eventStartTime,
            30000 // Default 30 seconds alert offset
        )
        
        DevLog.info(LOG_TAG, "Test event setup complete: id=${baseFixture.testEventId}, startTime=${baseFixture.eventStartTime}, reminderTime=${baseFixture.reminderTime}")
    }
    
    /**
     * Simulates a direct reminder broadcast from the Calendar Provider
     */
    fun simulateReminderBroadcast() {
        DevLog.info(LOG_TAG, "Simulating direct reminder broadcast for event ${baseFixture.testEventId}")
        
        // Get the context from the base fixture
        val context = baseFixture.contextProvider.fakeContext
        
        // Advance time to the reminder time
        baseFixture.timeProvider.setCurrentTime(baseFixture.reminderTime)
        
        // Create a reminder broadcast intent
        val reminderIntent = Intent(CalendarContract.ACTION_EVENT_REMINDER).apply {
            data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, baseFixture.reminderTime)
        }
        
        // Simulate the broadcast being received
        baseFixture.calendarProvider.mockCalendarMonitor.onProviderReminderBroadcast(context, reminderIntent)
        
        // Small delay for processing
        baseFixture.advanceTime(500)
    }
    
    /**
     * Verifies that the event was processed through the direct reminder path
     */
    fun verifyDirectReminderProcessed(): Boolean {
        DevLog.info(LOG_TAG, "Verifying direct reminder processing for event ${baseFixture.testEventId}")
        
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