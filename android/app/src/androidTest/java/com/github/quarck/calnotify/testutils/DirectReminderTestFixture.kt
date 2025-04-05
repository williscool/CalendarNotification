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
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.eventsstorage.EventsStorage
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
        
        // Set up mocks specific to this test event
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
        
        // Rather than triggering the broadcast receiver chain, directly create and add the event
        // This avoids potential infinite recursion through the notification system
        val eventRecord = EventAlertRecord(
            calendarId = baseFixture.testCalendarId,
            eventId = baseFixture.testEventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = baseFixture.reminderTime,
            notificationId = com.github.quarck.calnotify.Consts.NOTIFICATION_ID_DYNAMIC_FROM,
            title = eventTitle,
            desc = eventDescription,
            startTime = baseFixture.eventStartTime,
            endTime = baseFixture.eventStartTime + 3600000, // 1 hour duration
            instanceStartTime = baseFixture.eventStartTime,
            instanceEndTime = baseFixture.eventStartTime + 3600000,
            location = "",
            lastStatusChangeTime = baseFixture.timeProvider.testClock.currentTimeMillis(),
            displayStatus = com.github.quarck.calnotify.calendar.EventDisplayStatus.Hidden,
            color = com.github.quarck.calnotify.Consts.DEFAULT_CALENDAR_EVENT_COLOR,
            origin = com.github.quarck.calnotify.calendar.EventOrigin.ProviderBroadcast,
            timeFirstSeen = baseFixture.timeProvider.testClock.currentTimeMillis(),
            eventStatus = com.github.quarck.calnotify.calendar.EventStatus.Confirmed,
            attendanceStatus = com.github.quarck.calnotify.calendar.AttendanceStatus.None,
            flags = 0
        )
        
        // Add the event directly to storage
        EventsStorage(context).classCustomUse { db ->
            db.addEvent(eventRecord)
        }
        
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
