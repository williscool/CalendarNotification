package com.github.quarck.calnotify.testutils

import android.content.Context
import android.content.Intent
import com.github.quarck.calnotify.app.AlarmSchedulerInterface
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.notification.EventNotificationManagerInterface
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.textutils.EventFormatterInterface
import com.github.quarck.calnotify.ui.UINotifier
import io.mockk.*
import java.util.Locale
import java.util.ArrayList

/**
 * Provides application component mock functionality for tests
 *
 * This class sets up mock implementations of application components
 * like ApplicationController, EventNotificationManager, etc.
 */
class MockApplicationComponents(
    private val contextProvider: MockContextProvider,
    private val timeProvider: MockTimeProvider,
    private val calendarProvider: MockCalendarProvider
) {
    private val LOG_TAG = "MockApplicationComponents"
    
    // Core components
    lateinit var mockFormatter: EventFormatterInterface
        private set
        
    lateinit var mockNotificationManager: EventNotificationManagerInterface
        private set
        
    lateinit var mockAlarmScheduler: AlarmSchedulerInterface
        private set
    
    // Track initialization state
    private var isInitialized = false
    
    /**
     * Sets up all application components
     */
    fun setup() {
        if (isInitialized) {
            DevLog.info(LOG_TAG, "MockApplicationComponents already initialized, skipping setup")
            return
        }
        
        DevLog.info(LOG_TAG, "Setting up MockApplicationComponents")
        
        try {
            // Set default locale for date formatting
            Locale.setDefault(Locale.US)
            
            // Log thread information to help identify recursion issues
            val currentThread = Thread.currentThread()
            DevLog.info(LOG_TAG, "Setup running in thread: ${currentThread.name} (id: ${currentThread.id})")
            
            // Create a call stack trace to help identify recursion points
            DevLog.info(LOG_TAG, "Current call stack:")
            val stackTrace = Thread.currentThread().stackTrace
            stackTrace.drop(1).take(10).forEachIndexed { index, element ->
                DevLog.info(LOG_TAG, "  ${index}: ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})")
            }
            
            // Set up components in order with better logging
            DevLog.info(LOG_TAG, "Setting up mock formatter - START")
            setupMockFormatter()
            DevLog.info(LOG_TAG, "Setting up mock formatter - COMPLETED")
            
            DevLog.info(LOG_TAG, "Setting up mock notification manager - START")
            setupMockNotificationManager()
            DevLog.info(LOG_TAG, "Setting up mock notification manager - COMPLETED")
            
            DevLog.info(LOG_TAG, "Setting up mock alarm scheduler - START")
            setupMockAlarmScheduler()
            DevLog.info(LOG_TAG, "Setting up mock alarm scheduler - COMPLETED")
            
            DevLog.info(LOG_TAG, "Setting up reminder state - START")
            setupReminderState()
            DevLog.info(LOG_TAG, "Setting up reminder state - COMPLETED")
            
            DevLog.info(LOG_TAG, "Setting up application controller - START")
            setupApplicationController()
            DevLog.info(LOG_TAG, "Setting up application controller - COMPLETED")
            
            isInitialized = true
            DevLog.info(LOG_TAG, "MockApplicationComponents setup complete!")
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Exception during MockApplicationComponents setup: ${e.message}")
            e.printStackTrace()
            throw e  // Re-throw to fail the test
        }
    }
    
    /**
     * Sets up a mock text formatter
     */
    private fun setupMockFormatter() {
        DevLog.info(LOG_TAG, "Setting up mock formatter")
        
        mockFormatter = mockk<EventFormatterInterface> {
            every { formatNotificationSecondaryText(any()) } returns "Mock event time"
            every { formatDateTimeTwoLines(any(), any()) } returns Pair("Mock date", "Mock time")
            every { formatDateTimeOneLine(any(), any()) } returns "Mock date and time"
            every { formatSnoozedUntil(any()) } returns "Mock snooze time"
            every { formatTimePoint(any()) } returns "Mock time point"
            every { formatTimeDuration(any(), any()) } returns "Mock duration"
        }
    }
    
    /**
     * Sets up a mock notification manager
     */
    private fun setupMockNotificationManager() {
        DevLog.info(LOG_TAG, "Setting up mock notification manager")
        
        // Create a basic notification manager that doesn't do anything real
        mockNotificationManager = mockk<EventNotificationManagerInterface>(relaxed = true)
    }
    
    /**
     * Sets up a mock alarm scheduler
     */
    private fun setupMockAlarmScheduler() {
        DevLog.info(LOG_TAG, "Setting up mock alarm scheduler")
        
        mockAlarmScheduler = mockk<AlarmSchedulerInterface>(relaxed = true)
    }
    
    /**
     * Sets up the ReminderState using SharedPreferences
     */
    private fun setupReminderState() {
        DevLog.info(LOG_TAG, "Setting up ReminderState")
        
        val context = contextProvider.fakeContext
        val reminderStatePrefs = context.getSharedPreferences(ReminderState.PREFS_NAME, Context.MODE_PRIVATE)
        
        reminderStatePrefs.edit().apply {
            putInt(ReminderState.NUM_REMINDERS_FIRED_KEY, 0)
            putBoolean(ReminderState.QUIET_HOURS_ONE_TIME_REMINDER_KEY, false)
            putLong(ReminderState.REMINDER_LAST_FIRE_TIME_KEY, 0)
            apply()
        }
    }
    
    /**
     * Sets up the ApplicationController mock
     */
    private fun setupApplicationController() {
        DevLog.info(LOG_TAG, "Setting up ApplicationController mocks")
        
        // First, unmock any potential previous mocks to avoid duplicates
        try {
            unmockkObject(ApplicationController)
        } catch (e: Exception) {
            // Ignore if not mocked yet
        }
        
        // Then mock ApplicationController again
        mockkObject(ApplicationController)
        
        // Set up minimal mocks for ApplicationController properties with VERY clear logging
        every { ApplicationController.notificationManager } returns mockNotificationManager
        every { ApplicationController.alarmScheduler } returns mockAlarmScheduler
        every { ApplicationController.CalendarMonitor } returns calendarProvider.mockCalendarMonitor
        
        // CRITICAL: Mock clock to prevent time-related recursion
        every { ApplicationController.clock } returns timeProvider.testClock
        
        // Set up ALL ApplicationController.on* methods to prevent ANY recursion possibility
        every { ApplicationController.onBootComplete(any()) } just Runs
        every { ApplicationController.onAppUpdated(any()) } just Runs
        every { ApplicationController.onEventAlarm(any()) } just Runs
        every { ApplicationController.onCalendarChanged(any()) } just Runs
        every { ApplicationController.onCalendarReloadFromService(any(), any()) } just Runs
        every { ApplicationController.onCalendarRescanForRescheduledFromService(any(), any()) } just Runs
        every { ApplicationController.onCalendarEventMovedWithinApp(any(), any(), any()) } just Runs
        every { ApplicationController.afterCalendarEventFired(any()) } just Runs
        every { ApplicationController.onMainActivityCreate(any()) } just Runs
        every { ApplicationController.onMainActivityStarted(any()) } just Runs
        every { ApplicationController.onMainActivityResumed(any(), any(), any()) } just Runs
        every { ApplicationController.onTimeChanged(any()) } just Runs
        
        // Add thorough mocking for UINotifier to prevent callbacks
        try {
            unmockkObject(UINotifier)
        } catch (e: Exception) {
            // Ignore if not mocked yet
        }
        mockkObject(UINotifier)
        every { UINotifier.notify(any(), any()) } just Runs
        
        // Finally, set up event registration mocks
        // WARNING: THIS CAUSES INFINITE RECURSION
        // setupRegisterEventMocks()
    }
    
    /**
     * Sets up mocks for event registration methods
     */
    private fun setupRegisterEventMocks() {
        DevLog.info(LOG_TAG, "Setting up event registration mocks with STRICT isolation")
        
        // Use simple implementation for registerNewEvent
        every { ApplicationController.registerNewEvent(any(), any<EventAlertRecord>()) } answers {
            val context = firstArg<Context>()
            val event = secondArg<EventAlertRecord>()
            
            // Simply store the event in EventsStorage
            DevLog.info(LOG_TAG, "Mock registerNewEvent: storing event ${event.eventId}")
            EventsStorage(context).classCustomUse { db ->
                db.addEvent(event)
            }
            
            true
        }
        
        // Use simple implementation for registerNewEvents
        every { ApplicationController.registerNewEvents(any(), any<List<Pair<MonitorEventAlertEntry, EventAlertRecord>>>()) } answers {
            val context = firstArg<Context>()
            val eventPairs = secondArg<List<Pair<MonitorEventAlertEntry, EventAlertRecord>>>()
            
            // Store all events in EventsStorage
            DevLog.info(LOG_TAG, "Mock registerNewEvents: storing ${eventPairs.size} events")
            EventsStorage(context).classCustomUse { db ->
                for ((_, event) in eventPairs) {
                    db.addEvent(event)
                }
            }
            
            // Return ArrayList to match expected return type
            ArrayList(eventPairs)
        }
        
        // Simple implementation for postEventNotifications
        every { ApplicationController.postEventNotifications(any(), any<Collection<EventAlertRecord>>()) } just Runs
        
        // Mock other critical methods
        every { ApplicationController.shouldMarkEventAsHandledAndSkip(any(), any()) } returns false
        every { ApplicationController.hasActiveEventsToRemind(any()) } returns false
        every { ApplicationController.isCustomQuietHoursActive(any()) } returns false
        
        // Mock all possible dismiss methods to avoid recursion through these paths
        every { ApplicationController.dismissEvent(any(), any(), any<EventAlertRecord>()) } just Runs
        every { ApplicationController.dismissEvent(any(), any(), any<Long>(), any(), any(), any()) } just Runs
        every { ApplicationController.dismissEvents(any(), any(), any(), any(), any()) } just Runs
        every { ApplicationController.dismissAndDeleteEvent(any(), any(), any()) } returns true
        every { ApplicationController.restoreEvent(any(), any()) } just Runs
        
        // Mock move methods to avoid calendar integrations
        every { ApplicationController.moveEvent(any(), any(), any()) } returns true
        every { ApplicationController.moveAsCopy(any(), any(), any(), any()) } returns 42L
    }
    
    /**
     * Verifies that an event was processed
     */
    fun verifyEventProcessed(
        eventId: Long,
        startTime: Long,
        title: String? = null
    ): Boolean {
        DevLog.info(LOG_TAG, "Verifying event processing for eventId=$eventId, startTime=$startTime, title=$title")
        
        var eventFound = false
        
        EventsStorage(contextProvider.fakeContext).classCustomUse { db ->
            val events = db.events
            
            // Find event by ID
            val processedEvent = events.firstOrNull { it.eventId == eventId }
            
            if (processedEvent != null) {
                eventFound = true
                
                if (title != null && processedEvent.title != title) {
                    DevLog.error(LOG_TAG, "Event title mismatch: expected '$title' but was '${processedEvent.title}'")
                    eventFound = false
                }
                
                if (processedEvent.startTime != startTime) {
                    DevLog.error(LOG_TAG, "Event start time mismatch: expected $startTime but was ${processedEvent.startTime}")
                    eventFound = false
                }
            }
        }
        
        return eventFound
    }
    
    /**
     * Verifies that no events are present in storage
     */
    fun verifyNoEvents(): Boolean {
        var hasNoEvents = true

        EventsStorage(contextProvider.fakeContext).classCustomUse { db ->
            hasNoEvents = db.events.isEmpty()
        }
        
        return hasNoEvents
    }
    
    /**
     * Cleans up resources
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up MockApplicationComponents")
        isInitialized = false
    }
} 
