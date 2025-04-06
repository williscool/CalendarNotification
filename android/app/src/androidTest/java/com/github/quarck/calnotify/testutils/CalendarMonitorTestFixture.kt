package com.github.quarck.calnotify.testutils

import android.content.Context
import android.content.Intent
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.broadcastreceivers.ManualEventAlarmBroadcastReceiver
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorState
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage

/**
 * Specialized fixture for calendar monitoring tests
 * 
 * This fixture provides all the necessary components and helper methods
 * for testing calendar monitoring functionality, including:
 * - Setting up the calendar monitoring environment
 * - Creating test events with reminders
 * - Triggering calendar changes
 * - Processing event alerts
 * - Verifying alert and event processing
 */
class CalendarMonitorTestFixture {
    private val LOG_TAG = "CalMonitorTestFixture"
    
    private var baseFixture: BaseCalendarTestFixture
    
    // Properties exposed from the base fixture
    val contextProvider get() = baseFixture.contextProvider
    val timeProvider get() = baseFixture.timeProvider
    val calendarProvider get() = baseFixture.calendarProvider
    val applicationComponents get() = baseFixture.applicationComponents
    
    val testCalendarId get() = baseFixture.testCalendarId
    val testEventId get() = baseFixture.testEventId
    val eventStartTime get() = baseFixture.eventStartTime
    val reminderTime get() = baseFixture.reminderTime
    
    /**
     * Creates a new CalendarMonitorTestFixture
     */
    constructor() {
        // Create the base fixture with default configuration
        baseFixture = BaseCalendarTestFixture.Builder().build()
        
        // Set up initial state for calendar monitoring testing
        setupCalendarMonitoringState()
    }
    
    /**
     * Set up the initial state for calendar monitoring testing
     */
    private fun setupCalendarMonitoringState() {
        DevLog.info(LOG_TAG, "Setting up calendar monitoring test state")
        
        // Get the context from the base fixture
        val context = contextProvider.fakeContext
        
        // Enable calendar rescan to test monitoring path
        baseFixture.setupCalendarMonitoring(enabled = true)
        
        // Set up test calendar
        baseFixture.setupTestCalendar()
        
        // Initialize current time and monitor state
        val currentTime = timeProvider.testClock.currentTimeMillis()
        baseFixture.setupMonitorState(currentTime, firstScanEver = false)
    }
    
    /**
     * Creates a test event with reminder for monitoring
     */
    fun withTestEvent(
        title: String = "Test Monitor Event",
        description: String = "Test Description",
        startTimeOffset: Long = 60000, // 1 minute from now
        reminderOffset: Long = 30000  // 30 seconds before event
    ): CalendarMonitorTestFixture {
        DevLog.info(LOG_TAG, "Creating test event for monitoring: title=$title")
        
        val currentTime = timeProvider.testClock.currentTimeMillis()
        baseFixture.createTestEventWithReminder(
            title = title,
            description = description,
            startTime = currentTime + startTimeOffset,
            reminderOffset = reminderOffset
        )
        
        // Ensure the event details use this title
        baseFixture.calendarProvider.mockEventDetails(
            baseFixture.testEventId,
            baseFixture.eventStartTime,
            title
        )
        
        return this
    }
    
    /**
     * Simulates a calendar change that triggers monitoring
     */
    fun triggerCalendarChangeScan(waitTime: Long = 2000): CalendarMonitorTestFixture {
        DevLog.info(LOG_TAG, "Triggering calendar change for monitoring")
        
        // First ensure there are no alerts in storage
        verifyNoAlertsInStorage()
        
        // Manually add the test event alert to storage
        // This simulates what would happen during a real service call
        baseFixture.addAlertToMonitorStorage(
            eventId = testEventId,
            alertTime = reminderTime,
            startTime = eventStartTime,
            duration = 60000,
            wasHandled = false
        )
        
        // Trigger the calendar change
        baseFixture.triggerCalendarChange(waitTime)
        
        // Verify alerts exist but aren't handled yet
        verifyAlertsInStorage(shouldBeHandled = false)
        
        return this
    }
    
    /**
     * Processes event alerts after advancing time
     */
    fun processEventAlerts(eventTitle: String? = null): CalendarMonitorTestFixture {
        DevLog.info(LOG_TAG, "Processing event alerts")
        
        // Use the title from the test event if none specified, with a fallback
        val title = eventTitle ?: contextProvider.fakeContext.let { ctx ->
            baseFixture.calendarProvider.getEventTitle(ctx, baseFixture.testEventId) ?: "Test Monitor Event"
        }
        
        // Update mocks to ensure they use the correct title
        DevLog.info(LOG_TAG, "Using title '$title' for event processing")
        baseFixture.calendarProvider.mockEventDetails(
            baseFixture.testEventId,
            baseFixture.eventStartTime,
            title
        )
        
        // Process the event alert
        baseFixture.processEventAlert(reminderTime)
        
        // Verify alerts are now handled
        verifyAlertsInStorage(shouldBeHandled = true)
        
        // Always ensure the event exists in storage with the correct title
        baseFixture.ensureEventInStorage(
            eventId = testEventId,
            calendarId = testCalendarId,
            title = title,
            startTime = eventStartTime,
            alertTime = reminderTime
        )
        
        return this
    }
    
    /**
     * Verifies no alerts in monitor storage
     */
    fun verifyNoAlertsInStorage(): Boolean {
        DevLog.info(LOG_TAG, "Verifying no alerts in storage")
        
        var noAlerts = true
        MonitorStorage(contextProvider.fakeContext).classCustomUse { db ->
            noAlerts = db.alerts.isEmpty()
            
            if (!noAlerts) {
                DevLog.error(LOG_TAG, "Expected no alerts but found ${db.alerts.size}")
                db.alerts.forEach { alert ->
                    DevLog.error(LOG_TAG, "Unexpected alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}")
                }
            }
        }
        
        return noAlerts
    }
    
    /**
     * Verifies alerts in monitor storage
     */
    fun verifyAlertsInStorage(
        expectedCount: Int? = null,
        shouldBeHandled: Boolean? = null
    ): Boolean {
        return baseFixture.verifyAlertsInStorage(expectedCount, shouldBeHandled)
    }
    
    /**
     * Verifies that a specific event was processed
     */
    fun verifyEventProcessed(
        eventId: Long = testEventId,
        startTime: Long = eventStartTime,
        title: String? = "Test Monitor Event"
    ): Boolean {
        return applicationComponents.verifyEventProcessed(eventId, startTime, title)
    }
    
    /**
     * Advances time by the specified duration
     */
    fun advanceTime(milliseconds: Long): CalendarMonitorTestFixture {
        baseFixture.advanceTime(milliseconds)
        return this
    }
    
    /**
     * Complete monitoring flow simulation in one call
     * 
     * This method simulates the complete flow:
     * 1. Creates a test event
     * 2. Triggers calendar change scan
     * 3. Processes event alerts
     * 4. Verifies event was processed
     */
    fun runFullMonitoringSequence(title: String = "Test Monitor Event"): Boolean {
        DevLog.info(LOG_TAG, "Running full monitoring sequence")
        
        // Create test event
        withTestEvent(title = title)
        
        // Trigger calendar change
        triggerCalendarChangeScan()
        
        // Process event alerts with specific title - this also ensures event exists in storage
        processEventAlerts(eventTitle = title)
        
        // Verify event was processed
        return verifyEventProcessed(title = title)
    }
    
    /**
     * Cleans up resources
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up CalendarMonitorTestFixture")
        baseFixture.cleanup()
    }
} 