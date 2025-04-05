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
        
        // Set default locale for date formatting
        Locale.setDefault(Locale.US)
        
        // Set up components in order
        setupMockFormatter()
        setupMockNotificationManager()
        setupMockAlarmScheduler()
        setupReminderState()
        setupApplicationController()
        
        isInitialized = true
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
        
        // Only mock ApplicationController once
        mockkObject(ApplicationController)
        
        // Set up minimal mocks for ApplicationController properties
        every { ApplicationController.notificationManager } returns mockNotificationManager
        every { ApplicationController.alarmScheduler } returns mockAlarmScheduler
        every { ApplicationController.CalendarMonitor } returns calendarProvider.mockCalendarMonitor
        
        // Set up minimal mocks for UINotifier
        mockkObject(UINotifier)
        every { UINotifier.notify(any(), any()) } just Runs
        
        // Set up application controller methods with simple behaviors
        setupRegisterEventMocks()
        setupCalendarChangeMocks()
    }
    
    /**
     * Sets up mocks for event registration methods
     */
    private fun setupRegisterEventMocks() {
        DevLog.info(LOG_TAG, "Setting up event registration mocks")
        
        // Use simple implementation for registerNewEvent
        every { ApplicationController.registerNewEvent(any(), any<EventAlertRecord>()) } answers {
            val context = firstArg<Context>()
            val event = secondArg<EventAlertRecord>()
            
            // Simply store the event in EventsStorage
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
        
        // Simple implementation for shouldMarkEventAsHandledAndSkip that always returns false
        every { ApplicationController.shouldMarkEventAsHandledAndSkip(any(), any()) } returns false
        
        // Simple implementation for afterCalendarEventFired
        every { ApplicationController.afterCalendarEventFired(any()) } just Runs
    }
    
    /**
     * Sets up mocks for calendar change related methods
     */
    private fun setupCalendarChangeMocks() {
        DevLog.info(LOG_TAG, "Setting up calendar change mocks")
        
        // Simple implementation for onCalendarReloadFromService
//        every { ApplicationController.onCalendarReloadFromService(any(), any()) } returns true
        
        // Simple implementation for onCalendarRescanForRescheduledFromService
        every { ApplicationController.onCalendarRescanForRescheduledFromService(any(), any()) } just Runs
        
        // Simple implementation for onCalendarChanged
        every { ApplicationController.onCalendarChanged(any()) } answers {
            val context = firstArg<Context>()
            
            // Create and start a service intent directly
            val intent = Intent().apply {
                putExtra("reload_calendar", true)
                putExtra("rescan_monitor", true)
                putExtra("start_delay", 0L)
            }
            
            context.startService(intent)
        }
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
