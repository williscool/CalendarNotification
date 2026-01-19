package com.github.quarck.calnotify.testutils

import android.content.Context
import android.content.Intent
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.AlarmSchedulerInterface
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.notification.EventNotificationManager
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
        
        // Create a notification manager that handles the key methods without triggering infinite loops
        mockNotificationManager = spyk(object : EventNotificationManager() {
            override fun postNotification(
                ctx: Context,
                formatter: EventFormatterInterface,
                event: EventAlertRecord,
                notificationSettings: com.github.quarck.calnotify.NotificationSettings,
                isForce: Boolean,
                wasCollapsed: Boolean,
                snoozePresetsNotFiltered: LongArray,
                isQuietPeriodActive: Boolean,
                isReminder: Boolean,
                forceAlarmStream: Boolean
            ) {
                // Prevent actual notification posting
                DevLog.info(LOG_TAG, "Mock postNotification called for event ${event.eventId}")
            }
            
            override fun postEventNotifications(
                ctx: Context,
                formatter: EventFormatterInterface,
                force: Boolean,
                primaryEventId: Long?
            ) {
                DevLog.info(LOG_TAG, "Mock postEventNotifications called with force=$force, primaryEventId=$primaryEventId")
                
                // Only mark events as handled when explicitly processing
                if (primaryEventId != null) {
                    com.github.quarck.calnotify.monitorstorage.MonitorStorage(ctx).use { db ->
                        val alertsToHandle = db.alerts.filter { it.eventId == primaryEventId && !it.wasHandled }
                        if (alertsToHandle.isNotEmpty()) {
                            alertsToHandle.forEach { alert ->
                                alert.wasHandled = true
                                db.updateAlert(alert)
                                DevLog.info(LOG_TAG, "Marked alert as handled for event ${alert.eventId}")
                            }
                        }
                    }
                }
            }
            
            override fun onEventAdded(ctx: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {
                DevLog.info(LOG_TAG, "Mock onEventAdded called for event ${event.eventId}")
                // Don't automatically mark alerts as handled here
            }
            
            // Implement all other required methods with empty implementations
            fun onEventDismissing(ctx: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {
                DevLog.info(LOG_TAG, "Mock onEventDismissing called for event ${event.eventId}")
            }
            
            fun onEventsDismissing(ctx: Context, formatter: EventFormatterInterface) {
                DevLog.info(LOG_TAG, "Mock onEventsDismissing called")
            }
            
            fun onEventDismissed(ctx: Context, formatter: EventFormatterInterface, event: EventAlertRecord, doNotCreateReplacement: Boolean) {
                DevLog.info(LOG_TAG, "Mock onEventDismissed called for event ${event.eventId}")
            }
            
            fun onEventsDismissed(ctx: Context, formatter: EventFormatterInterface, eventId: Long, instanceStartTime: Long, doNotCreateReplacement: Boolean) {
                DevLog.info(LOG_TAG, "Mock onEventsDismissed called for event $eventId")
            }
            
            fun onEventSnoozed(ctx: Context, formatter: EventFormatterInterface, event: EventAlertRecord, doNotCreateReplacement: Boolean) {
                DevLog.info(LOG_TAG, "Mock onEventSnoozed called for event ${event.eventId}")
            }
            
            override fun onEventMuteToggled(ctx: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {
                DevLog.info(LOG_TAG, "Mock onEventMuteToggled called for event ${event.eventId}")
            }
            
            override fun onAllEventsSnoozed(ctx: Context) {
                DevLog.info(LOG_TAG, "Mock onAllEventsSnoozed called")
            }
            
            override fun onEventRestored(ctx: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {
                DevLog.info(LOG_TAG, "Mock onEventRestored called for event ${event.eventId}")
            }
            
            fun fireEventReminder(ctx: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {
                DevLog.info(LOG_TAG, "Mock fireEventReminder called for event ${event.eventId}")
            }
            
            override fun cleanupEventReminder(ctx: Context) {
                DevLog.info(LOG_TAG, "Mock cleanupEventReminder called")
            }
            
            override fun postNotificationsAutoDismissedDebugMessage(ctx: Context) {
                DevLog.info(LOG_TAG, "Mock postNotificationsAutoDismissedDebugMessage called")
            }
            
            override fun postNearlyMissedNotificationDebugMessage(ctx: Context) {
                DevLog.info(LOG_TAG, "Mock postNearlyMissedNotificationDebugMessage called")
            }
            
          fun postNotificationsAlarmDelayDebugMessage(ctx: Context, prevFire: Long, nextFire: Long) {
                DevLog.info(LOG_TAG, "Mock postNotificationsAlarmDelayDebugMessage called")
            }
            
            fun postNotificationsSnoozeAlarmDelayDebugMessage(ctx: Context, prevFire: Long, nextFire: Long) {
                DevLog.info(LOG_TAG, "Mock postNotificationsSnoozeAlarmDelayDebugMessage called")
            }
        })
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
        
        // CRITICAL: Mock clock FIRST to prevent time-related recursion
        // The order here is important - clock must be mocked before any other properties
        DevLog.info(LOG_TAG, "Setting up ApplicationController.clock mock")
        every { ApplicationController.clock } returns timeProvider.testClock
        
        // Then set up other basic properties
        DevLog.info(LOG_TAG, "Setting up ApplicationController property mocks")
        every { ApplicationController.notificationManager } returns mockNotificationManager
        every { ApplicationController.alarmScheduler } returns mockAlarmScheduler
        every { ApplicationController.CalendarMonitor } returns calendarProvider.mockCalendarMonitor
        
        // Set up ALL ApplicationController.on* methods to prevent ANY recursion possibility
        DevLog.info(LOG_TAG, "Setting up ApplicationController method mocks")
        every { ApplicationController.onBootComplete(any()) } just Runs
        every { ApplicationController.onAppUpdated(any()) } just Runs
        every { ApplicationController.onEventAlarm(any()) } just Runs
        every { ApplicationController.onCalendarChanged(any()) } just Runs
        every { ApplicationController.onCalendarReloadFromService(any(), any()) } answers {
            val context = firstArg<Context>()
            val userActionUntil = secondArg<Long>()
            DevLog.info(LOG_TAG, "Mock onCalendarReloadFromService called with userActionUntil=$userActionUntil")
            
            // Return true to indicate success
            true
        }
        every { ApplicationController.onCalendarRescanForRescheduledFromService(any(), any()) } just Runs
        every { ApplicationController.onCalendarEventMovedWithinApp(any(), any(), any()) } just Runs
        
        // Fix for intermittent failures - explicitly mock afterCalendarEventFired
        // with logging and safely running the alarm scheduler
        every { ApplicationController.afterCalendarEventFired(any()) } answers {
            val context = firstArg<Context>()
            DevLog.info(LOG_TAG, "Mock afterCalendarEventFired called")
            
            // Safely run just the core functionality without recursion
            mockAlarmScheduler.rescheduleAlarms(context, Settings(context), com.github.quarck.calnotify.quiethours.QuietHoursManager(context))
            
            // Do not call UINotifier.notify to prevent potential recursion
            // The real method does: alarmScheduler.rescheduleAlarms() and UINotifier.notify()
        }
        
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
        DevLog.info(LOG_TAG, "Setting up event registration mocks")
        setupRegisterEventMocks()
        
        DevLog.info(LOG_TAG, "ApplicationController mocking completed")
    }
    
    /**
     * Sets up mocks for event registration methods
     */
    private fun setupRegisterEventMocks() {
        DevLog.info(LOG_TAG, "Setting up event registration mocks with focus on postEventNotifications only")
        
        // CRITICAL CHANGE: Don't mock registerNewEvent and registerNewEvents
        // Instead, let them use their real implementations, but with safeguards
        
        // Protect registerNewEvent to log properly
        every { ApplicationController.registerNewEvent(any(), any()) } answers {
            val context = firstArg<Context>()
            val event = secondArg<EventAlertRecord>()
            
            DevLog.info(LOG_TAG, "Mock passthrough registerNewEvent called for event id=${event.eventId}")
            
            try {
                // Call the real implementation
                callOriginal()
            } catch (e: Exception) {
                DevLog.error(LOG_TAG, "Error in registerNewEvent: ${e.message}")
                // Return a default value in case of error
                false
            }
        }
        
        // Only mock postEventNotifications - this approach is used in the working tests
        every { ApplicationController.postEventNotifications(any(), any<Collection<EventAlertRecord>>()) } answers {
            val context = firstArg<Context>()
            val events = secondArg<Collection<EventAlertRecord>>()
            
            DevLog.info(LOG_TAG, "Mock postEventNotifications called for ${events.size} events")
            
            // Use mockNotificationManager to process the events
            if (events.size == 1) {
                mockNotificationManager.onEventAdded(context, mockFormatter, events.first())
            } else {
                mockNotificationManager.postEventNotifications(context, mockFormatter)
            }
        }
        
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
        title: String? = null,
        afterDelay: Long? = null
    ): Boolean {
        DevLog.info(LOG_TAG, "Verifying event processing for eventId=$eventId, startTime=$startTime, title=$title")
        
        var eventFound = false
        
        EventsStorage(contextProvider.fakeContext).use { db ->
            val events = db.events
            DevLog.info(LOG_TAG, "Found ${events.size} events in storage")
            
            // Log all events for debugging
            events.forEach { event ->
                DevLog.info(LOG_TAG, "Event in storage: id=${event.eventId}, title=${event.title}, startTime=${event.startTime}, timeFirstSeen=${event.timeFirstSeen}")
            }
            
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
                
                if (afterDelay != null && processedEvent.timeFirstSeen < afterDelay) {
                    DevLog.error(LOG_TAG, "Event was processed too early: expected after $afterDelay but was ${processedEvent.timeFirstSeen}")
                    eventFound = false
                } else {}
            } else {
                DevLog.error(LOG_TAG, "Event $eventId not found in storage")
            }
        }
        
        return eventFound
    }
    
    /**
     * Verifies that no events are present in storage
     */
    fun verifyNoEvents(): Boolean {
        var hasNoEvents = true

        EventsStorage(contextProvider.fakeContext).use { db ->
            val events = db.events
            hasNoEvents = events.isEmpty()
            
            if (!hasNoEvents) {
                DevLog.error(LOG_TAG, "Expected no events but found ${events.size}")
                events.forEach { event ->
                    DevLog.error(LOG_TAG, "Unexpected event: id=${event.eventId}, title=${event.title}")
                }
            }
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
    
    /**
     * Directly adds an event to the storage for testing
     */
    fun addEventToStorage(
        event: EventAlertRecord
    ) {
        DevLog.info(LOG_TAG, "Directly adding event to storage: id=${event.eventId}, title=${event.title}")
        
        EventsStorage(contextProvider.fakeContext).use { db ->
            db.addEvent(event)
        }
    }
    
    /**
     * Creates a test event and adds it to storage for testing
     */
    fun createAndAddTestEvent(
        eventId: Long,
        calendarId: Long,
        title: String,
        startTime: Long,
        alertTime: Long
    ): EventAlertRecord {
        DevLog.info(LOG_TAG, "Creating and adding test event: id=$eventId, title=$title")
        
        val event = EventAlertRecord(
            calendarId = calendarId,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = alertTime,
            notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
            title = title,
            desc = "Test Description",
            startTime = startTime,
            endTime = startTime + 3600000,
            instanceStartTime = startTime,
            instanceEndTime = startTime + 3600000,
            location = "",
            lastStatusChangeTime = timeProvider.testClock.currentTimeMillis(),
            displayStatus = EventDisplayStatus.Hidden,
            color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = timeProvider.testClock.currentTimeMillis(),
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
        
        addEventToStorage(event)
        return event
    }
    
    /**
     * Simulates a system time change
     * 
     * This method:
     * 1. Advances the test clock
     * 2. Triggers the CalendarMonitor's time change handler
     */
    fun simulateSystemTimeChange(timeChangeAmount: Long = 3600000) {
        DevLog.info(LOG_TAG, "Simulating system time change by $timeChangeAmount ms")
        
        // Advance clock
        timeProvider.testClock.setCurrentTime(timeProvider.testClock.currentTimeMillis() + timeChangeAmount)
        
        // Trigger time change handler
        ApplicationController.CalendarMonitor.onSystemTimeChange(contextProvider.fakeContext)
        
        // Allow time for processing
        timeProvider.testClock.advanceAndExecuteTasks(2000)
        
        DevLog.info(LOG_TAG, "System time change simulated")
    }
    
    /**
     * Simulates app resume
     * 
     * This method triggers the CalendarMonitor's app resume handler
     */
    fun simulateAppResume() {
        DevLog.info(LOG_TAG, "Simulating app resume")
        
        // Trigger app resume handler with forceReload=false
        ApplicationController.CalendarMonitor.onAppResumed(contextProvider.fakeContext, false)
        
        // Allow time for processing
        timeProvider.testClock.advanceAndExecuteTasks(2000)
        
        DevLog.info(LOG_TAG, "App resume simulated")
    }
    
    /**
     * Gets the list of Toast messages that would have been shown
     */
    fun getToastMessages(): List<String> = contextProvider.getToastMessages()
    
    /**
     * Clears the list of Toast messages
     */
    fun clearToastMessages() {
        contextProvider.clearToastMessages()
    }
} 
