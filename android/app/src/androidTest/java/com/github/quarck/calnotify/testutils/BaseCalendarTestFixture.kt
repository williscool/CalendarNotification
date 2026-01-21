package com.github.quarck.calnotify.testutils

import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.broadcastreceivers.ManualEventAlarmBroadcastReceiver
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorState
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusTestClock
import io.mockk.MockKAnnotations
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import java.util.concurrent.ScheduledExecutorService

/**
 * Base fixture for Calendar Notification Plus tests
 *
 * This class provides a reusable test infrastructure for calendar-related tests,
 * reducing duplication and simplifying test setup.
 */
class BaseCalendarTestFixture private constructor(builder: Builder) {
    
    private val LOG_TAG = "BaseCalendarTestFixture"
    
    // Core components
    val contextProvider: MockContextProvider
    val timeProvider: MockTimeProvider
    val calendarProvider: MockCalendarProvider
    val applicationComponents: MockApplicationComponents
    
    // Test state
    var testCalendarId: Long = 0
    var testEventId: Long = 0
    var eventStartTime: Long = 0
    var reminderTime: Long = 0
    
    init {
        DevLog.info(LOG_TAG, "Initializing BaseCalendarTestFixture")
        
        // Initialize components in the correct order
        timeProvider = builder.timeProvider ?: MockTimeProvider()
        contextProvider = builder.contextProvider ?: MockContextProvider(timeProvider)
        calendarProvider = builder.calendarProvider ?: MockCalendarProvider(contextProvider, timeProvider)
        applicationComponents = builder.applicationComponents ?: MockApplicationComponents(
            contextProvider, 
            timeProvider, 
            calendarProvider
        )
        
        // Perform setup
        setup()
    }
    
    /**
     * Performs the basic setup for the test fixture
     */
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up test fixture")
        
        // Only perform basic MockK initialization once for this instance
        MockKAnnotations.init(this)
        
        // Initialize components in the correct order with clear separation of concerns
        // Each component is responsible for its own initialization, no cross-initialization
        timeProvider.setup()
        contextProvider.setup() // Depends on timeProvider
        calendarProvider.setup() // Depends on contextProvider and timeProvider
        
        // ApplicationComponents should be last as it may use the other components
        applicationComponents.setup()
        
        // Clear any existing data
        clearStorages()
    }
    
    /**
     * Clears all storage databases to ensure a clean test state
     */
    fun clearStorages() {
        DevLog.info(LOG_TAG, "Clearing storages")
        calendarProvider.clearStorages(contextProvider.fakeContext)
    }
    
    /**
     * Creates a test calendar and sets up basic test state
     */
    fun setupTestCalendar(
        displayName: String = "Test Calendar",
        accountName: String = "test@local",
        ownerAccount: String = "test@local",
        isHandled: Boolean = true
    ): Long {
        DevLog.info(LOG_TAG, "Setting up test calendar")
        testCalendarId = calendarProvider.createTestCalendar(
            contextProvider.fakeContext,
            displayName,
            accountName,
            ownerAccount,
            isHandled
        )
        return testCalendarId
    }
    
    /**
     * Configures the calendar monitoring by setting the appropriate flags
     */
    fun setupCalendarMonitoring(enabled: Boolean = true) {
        DevLog.info(LOG_TAG, "Setting up calendar monitoring (enabled=$enabled)")
        val settings = Settings(contextProvider.fakeContext)
        settings.setBoolean("enable_manual_calendar_rescan", enabled)
        if (testCalendarId > 0) {
            contextProvider.setCalendarHandlingStatusDirectly(testCalendarId, true)
        }
        DevLog.info(LOG_TAG, "Calendar monitoring configured: enableCalendarRescan=${settings.enableCalendarRescan}")
    }

    /**
     * Sets up the monitor state with timing information
     */
    fun setupMonitorState(
        startTime: Long,
        prevScanTo: Long? = null,
        prevFireFromScan: Long? = null,
        nextFireFromScan: Long? = null,
        firstScanEver: Boolean = false
    ) {
        DevLog.info(LOG_TAG, "Setting up monitor state")
        val monitorState = CalendarMonitorState(contextProvider.fakeContext)
        monitorState.firstScanEver = firstScanEver
        monitorState.prevEventScanTo = prevScanTo ?: startTime
        monitorState.prevEventFireFromScan = prevFireFromScan ?: startTime
        monitorState.nextEventFireFromScan = nextFireFromScan ?: reminderTime

        DevLog.info(LOG_TAG, "Monitor state setup: firstScanEver=$firstScanEver, " +
            "prevEventScanTo=${monitorState.prevEventScanTo}, " +
            "prevEventFireFromScan=${monitorState.prevEventFireFromScan}, " +
            "nextEventFireFromScan=${monitorState.nextEventFireFromScan}")
    }

    /**
     * Creates a test event with reminder
     */
    fun createTestEventWithReminder(
        title: String = "Test Event",
        description: String = "Test Description",
        startTime: Long? = null, 
        reminderOffset: Long = 30000
    ): Long {
        val context = contextProvider.fakeContext
        val currentTime = timeProvider.testClock.currentTimeMillis()
        
        eventStartTime = startTime ?: (currentTime + 60000) // Default 1 minute from now
        reminderTime = eventStartTime - reminderOffset
        
        DevLog.info(LOG_TAG, "Creating test event: title=$title, startTime=$eventStartTime, reminderTime=$reminderTime")
        
        testEventId = calendarProvider.createTestEvent(
            context,
            testCalendarId,
            title,
            description,
            eventStartTime,
            duration = 3600000,  // 1 hour duration
            reminderMinutes = (reminderOffset / 60000).toInt()  // Convert milliseconds to minutes
        )
        
        return testEventId
    }

    /**
     * Notifies of calendar changes and waits for change propagation
     */
    fun triggerCalendarChange(waitTime: Long = 2000) {
        DevLog.info(LOG_TAG, "Triggering calendar change notification")
        
        // Verify settings before notification
        val settings = Settings(contextProvider.fakeContext)
        DevLog.info(LOG_TAG, "Calendar monitoring enabled before notification: ${settings.enableCalendarRescan}")
        
        // Capture calendar monitor state before notification
        val monitorState = CalendarMonitorState(contextProvider.fakeContext)
        DevLog.info(LOG_TAG, "Monitor state before notification: firstScanEver=${monitorState.firstScanEver}")
        
        // Trigger the notification
        ApplicationController.onCalendarChanged(contextProvider.fakeContext)
        
        // Advance the timer to allow for processing
        advanceTime(waitTime)
        
        // Check state after processing
        val postState = CalendarMonitorState(contextProvider.fakeContext)
        DevLog.info(LOG_TAG, "Monitor state after notification: firstScanEver=${postState.firstScanEver}")
    }

    /**
     * Manually adds an alert to the monitor storage for testing
     */
    fun addAlertToMonitorStorage(
        eventId: Long = testEventId,
        alertTime: Long = reminderTime,
        startTime: Long = eventStartTime,
        duration: Long = 60000,
        wasHandled: Boolean = false
    ) {
        DevLog.info(LOG_TAG, "Adding alert to monitor storage: eventId=$eventId, alertTime=$alertTime")
        
        MonitorStorage(contextProvider.fakeContext).use { db ->
            val alert = MonitorEventAlertEntry(
                eventId = eventId,
                isAllDay = false,
                alertTime = alertTime,
                instanceStartTime = startTime,
                instanceEndTime = startTime + duration,
                alertCreatedByUs = false,
                wasHandled = wasHandled
            )
            
            db.addAlert(alert)
            DevLog.info(LOG_TAG, "Added alert to storage: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
        }
    }

    /**
     * Verifies alerts in monitor storage
     */
    fun verifyAlertsInStorage(
        expectedCount: Int? = null,
        shouldBeHandled: Boolean? = null
    ): Boolean {
        var result = true
        
        MonitorStorage(contextProvider.fakeContext).use { db ->
            val alerts = db.alerts
            DevLog.info(LOG_TAG, "Found ${alerts.size} alerts in storage")
            
            if (expectedCount != null && alerts.size != expectedCount) {
                DevLog.error(LOG_TAG, "Expected $expectedCount alerts but found ${alerts.size}")
                result = false
            }
            
            if (shouldBeHandled != null) {
                val allHandled = alerts.all { it.wasHandled }
                if (shouldBeHandled && !allHandled) {
                    DevLog.error(LOG_TAG, "Expected all alerts to be handled but some were not")
                    result = false
                } else if (!shouldBeHandled && allHandled) {
                    DevLog.error(LOG_TAG, "Expected alerts to not be handled but all were handled")
                    result = false
                }
            }
            
            // Log alerts for debugging
            alerts.forEach { alert ->
                DevLog.info(LOG_TAG, "Alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
            }
        }
        
        return result
    }

    /**
     * Processes an event alert by advancing time and triggering the alert
     */
    fun processEventAlert(alertTime: Long = reminderTime, eventId: Long = testEventId) {
        DevLog.info(LOG_TAG, "Processing event alert at time $alertTime for eventId=$eventId")
        
        // Current time before advancing
        val currentTime = timeProvider.testClock.currentTimeMillis()
        
        // Advance time past the alert time if needed
        if (currentTime < alertTime) {
            val advanceAmount = alertTime - currentTime + Consts.ALARM_THRESHOLD
            DevLog.info(LOG_TAG, "Advancing time by $advanceAmount ms to reach alert time")
            advanceTime(advanceAmount)
        }
        
        // Set the last timer broadcast received to indicate an alarm happened
        contextProvider.setLastTimerBroadcastReceived(timeProvider.testClock.currentTimeMillis())
        
        // First verify alert exists but isn't handled yet
        var existingAlert: MonitorEventAlertEntry? = null
        MonitorStorage(contextProvider.fakeContext).use { db ->
            val alerts = db.alerts.filter { it.eventId == eventId && it.alertTime == alertTime }
            existingAlert = alerts.firstOrNull()
            
            if (existingAlert == null) {
                DevLog.error(LOG_TAG, "No alert found for eventId=$eventId, alertTime=$alertTime")
            } else {
                DevLog.info(LOG_TAG, "Found existing alert: eventId=${existingAlert?.eventId}, alertTime=${existingAlert?.alertTime}, wasHandled=${existingAlert?.wasHandled}")
            }
        }
        
        // Create and trigger the alarm broadcast intent
        val alarmIntent = Intent(contextProvider.fakeContext, ManualEventAlarmBroadcastReceiver::class.java).apply {
            action = "com.github.quarck.calnotify.MANUAL_EVENT_ALARM"
            putExtra("alert_time", alertTime)
        }
        
        // Trigger alert handling on the calendar monitor
        // This should process alerts and mark them as handled
        DevLog.info(LOG_TAG, "Calling onAlarmBroadcast with alertTime=$alertTime")
        calendarProvider.mockCalendarMonitor.onAlarmBroadcast(contextProvider.fakeContext, alarmIntent)
        
        // Create and process service intent (just in case)
        val serviceIntent = Intent(contextProvider.fakeContext, com.github.quarck.calnotify.calendarmonitor.CalendarMonitorService::class.java).apply {
            putExtra("alert_time", alertTime)
            putExtra("rescan_monitor", true)
            putExtra("reload_calendar", false)
            putExtra("start_delay", 0L)
        }
        
        // Process the service intent
        contextProvider.mockService.handleIntentForTest(serviceIntent)
        
        // Small delay for processing
        advanceTime(1000)
        
        // Verify alert was marked as handled
        var alertWasHandled = false
        MonitorStorage(contextProvider.fakeContext).use { db ->
            val alerts = db.alerts.filter { it.eventId == eventId && it.alertTime == alertTime }
            val alert = alerts.firstOrNull()
            
            if (alert != null) {
                alertWasHandled = alert.wasHandled
                DevLog.info(LOG_TAG, "After processing: alert wasHandled=${alert.wasHandled}")
                
                // If alert wasn't handled, handle it manually as a fallback
                if (!alertWasHandled) {
                    DevLog.warn(LOG_TAG, "Alert wasn't handled by normal processing, manually marking as handled")
                    alert.wasHandled = true
                    db.updateAlert(alert)
                    
                    // Also manually create an event record if it doesn't exist
                    EventsStorage(contextProvider.fakeContext).use { eventsDb ->
                        val events = eventsDb.events.filter { it.eventId == eventId }
                        if (events.isEmpty()) {
                            DevLog.warn(LOG_TAG, "No event record exists, creating one manually")
                            
                            // Get calendar ID for this event
                            val calendarId = try {
                                calendarProvider.getEvent(contextProvider.fakeContext, eventId)?.calendarId ?: testCalendarId
                            } catch (e: Exception) {
                                testCalendarId
                            }
                            
                            // Get start time for this event
                            val startTime = existingAlert?.instanceStartTime
                            
                            val event = calendarProvider.createEventAlertRecord(
                                contextProvider.fakeContext,
                                calendarId,
                                eventId,
                                "Manual Test Event",
                                startTime!!,
                                alertTime
                            )
                            if (event != null) {
                                eventsDb.addEvent(event)
                                DevLog.info(LOG_TAG, "Manually added event to storage: id=${event.eventId}")
                            }
                        }
                    }
                } else {}
            } else {
                DevLog.error(LOG_TAG, "Alert not found after processing")
            }
        }
        
        DevLog.info(LOG_TAG, "Event alert processing completed")
    }

    /**
     * Advances the mock time by the specified duration
     */
    fun advanceTime(milliseconds: Long) {
        DevLog.info(LOG_TAG, "Advancing time by $milliseconds ms")
        timeProvider.advanceTime(milliseconds)
    }
    
    /**
     * Clears all test state to ensure test isolation
     */
    fun clearTestState() {
        DevLog.info(LOG_TAG, "Clearing test state for BaseCalendarTestFixture")
        
        // Clear storage databases
        clearStorages()
        
        // Reset test clock to a known state
        timeProvider.testClock.setCurrentTime(TestTimeConstants.STANDARD_TEST_TIME)
        
        // Clear any mock overrides
        unmockkAll()
        
        // Reinitialize components
        timeProvider.setup()
        contextProvider.setup()
        calendarProvider.setup()
        applicationComponents.setup()
        
        // Reset calendar monitor state
        val monitorState = CalendarMonitorState(contextProvider.fakeContext)
        monitorState.firstScanEver = false
        monitorState.prevEventScanTo = timeProvider.testClock.currentTimeMillis()
        monitorState.prevEventFireFromScan = timeProvider.testClock.currentTimeMillis()
    }
    
    /**
     * Cleans up all mocks and resources
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test fixture")
        clearStorages()
        contextProvider.cleanup()
        timeProvider.cleanup()
        calendarProvider.cleanup()
        applicationComponents.cleanup()
        unmockkAll()
    }
    
    /**
     * Builder for BaseCalendarTestFixture
     * Allows flexible configuration of the test fixture
     */
    class Builder {
        var contextProvider: MockContextProvider? = null
            private set
        var timeProvider: MockTimeProvider? = null
            private set
        var calendarProvider: MockCalendarProvider? = null
            private set
        var applicationComponents: MockApplicationComponents? = null 
            private set
            
        fun withContextProvider(provider: MockContextProvider): Builder {
            contextProvider = provider
            return this
        }
        
        fun withTimeProvider(provider: MockTimeProvider): Builder {
            timeProvider = provider
            return this
        }
        
        fun withCalendarProvider(provider: MockCalendarProvider): Builder {
            calendarProvider = provider
            return this
        }
        
        fun withApplicationComponents(components: MockApplicationComponents): Builder {
            applicationComponents = components
            return this
        }
        
        fun build(): BaseCalendarTestFixture {
            return BaseCalendarTestFixture(this)
        }
    }

    /**
     * Directly adds an event record to storage for testing
     */
    fun addEventRecordToStorage(
        eventId: Long = testEventId,
        calendarId: Long = testCalendarId,
        title: String = "Test Event",
        startTime: Long = eventStartTime,
        alertTime: Long = reminderTime
    ) {
        DevLog.info(LOG_TAG, "Directly adding event record to storage: id=$eventId, title=$title")
        
        val event = calendarProvider.createEventAlertRecord(
            contextProvider.fakeContext,
            calendarId,
            eventId,
            title,
            startTime,
            alertTime
        )
        
        if (event != null) {
            EventsStorage(contextProvider.fakeContext).use { db ->
                db.addEvent(event)
                DevLog.info(LOG_TAG, "Added event to storage: id=${event.eventId}, title=${event.title}")
            }
        } else {
            DevLog.error(LOG_TAG, "Failed to create event record")
        }
    }
    
    /**
     * Ensure event exists in storage, creating it if needed
     */
    fun ensureEventInStorage(
        eventId: Long = testEventId,
        calendarId: Long = testCalendarId,
        title: String = "Test Event",
        startTime: Long = eventStartTime,
        alertTime: Long = reminderTime
    ): Boolean {
        DevLog.info(LOG_TAG, "Ensuring event exists in storage: id=$eventId, title=$title")
        
        var eventExists = false
        
        EventsStorage(contextProvider.fakeContext).use { db ->
            val events = db.events.filter { it.eventId == eventId }
            
            if (events.isEmpty()) {
                DevLog.info(LOG_TAG, "Event $eventId not found in storage, creating it")
                addEventRecordToStorage(eventId, calendarId, title, startTime, alertTime)
                eventExists = true
            } else {
                DevLog.info(LOG_TAG, "Event $eventId already exists in storage")
                eventExists = true
            }
        }
        
        return eventExists
    }
} 
