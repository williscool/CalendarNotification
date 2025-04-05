package com.github.quarck.calnotify.testutils

import android.content.Context
import com.github.quarck.calnotify.Consts
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
    
    /**
     * Sets up all application components
     */
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up MockApplicationComponents")
        
        // Set default locale for date formatting
        Locale.setDefault(Locale.US)
        
        setupMockFormatter()
        setupMockNotificationManager()
        setupMockAlarmScheduler()
        setupApplicationController()
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
        
        mockNotificationManager = mockk<EventNotificationManagerInterface> {
            every { postEventNotifications(any(), any(), any(), any()) } just Runs
            every { onEventAdded(any(), any(), any()) } just Runs
            every { onEventDismissing(any(), any(), any()) } just Runs
            every { onEventsDismissing(any(), any()) } just Runs
            every { onEventDismissed(any(), any(), any(), any()) } just Runs
            every { onEventsDismissed(any(), any(), any(), any(), any()) } just Runs
            every { onEventSnoozed(any(), any(), any(), any()) } just Runs
            every { onEventMuteToggled(any(), any(), any()) } just Runs
            every { onAllEventsSnoozed(any()) } just Runs
            every { postEventNotifications(any(), any(), any(), any()) } just Runs
            every { fireEventReminder(any(), any(), any()) } just Runs
            every { cleanupEventReminder(any()) } just Runs
            every { onEventRestored(any(), any(), any()) } just Runs
            every { postNotificationsAutoDismissedDebugMessage(any()) } just Runs
            every { postNearlyMissedNotificationDebugMessage(any()) } just Runs
            every { postNotificationsAlarmDelayDebugMessage(any(), any(), any()) } just Runs
            every { postNotificationsSnoozeAlarmDelayDebugMessage(any(), any(), any()) } just Runs
        }
    }
    
    /**
     * Sets up a mock alarm scheduler
     */
    private fun setupMockAlarmScheduler() {
        DevLog.info(LOG_TAG, "Setting up mock alarm scheduler")
        
        mockAlarmScheduler = mockk<AlarmSchedulerInterface>(relaxed = true)
    }
    
    /**
     * Sets up the ApplicationController mock
     */
    private fun setupApplicationController() {
        DevLog.info(LOG_TAG, "Setting up ApplicationController mocks")
        
        mockkObject(ApplicationController)
        
        // Mock key properties
        every { ApplicationController.notificationManager } returns mockNotificationManager
        every { ApplicationController.alarmScheduler } returns mockAlarmScheduler
        every { ApplicationController.CalendarMonitor } returns calendarProvider.mockCalendarMonitor
        
        // Mock UINotifier
        mockkObject(UINotifier)
        every { UINotifier.notify(any(), any()) } just Runs
        
        // Mock ReminderState
        setupReminderState()
        
        // Mock key methods
        mockRegisterNewEvent()
        mockPostEventNotifications()
        mockAfterCalendarEventFired()
        mockCalendarReloadMethods()
        mockShouldMarkEventAsHandledAndSkip()
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
     * Mocks the registerNewEvent method
     */
    private fun mockRegisterNewEvent() {
        DevLog.info(LOG_TAG, "Mocking registerNewEvent")
        
        every { ApplicationController.registerNewEvent(any(), any<EventAlertRecord>()) } answers {
            val context = firstArg<Context>()
            val event = secondArg<EventAlertRecord>()
            
            DevLog.info(LOG_TAG, "Mock registerNewEvent called for event ${event.eventId} (${event.title})")
            
            EventsStorage(context).classCustomUse { db ->
                db.addEvent(event)
            }
            
            true
        }
        
        every { ApplicationController.registerNewEvents(any(), any<List<Pair<MonitorEventAlertEntry, EventAlertRecord>>>()) } answers {
            val context = firstArg<Context>()
            val eventPairs = secondArg<List<Pair<MonitorEventAlertEntry, EventAlertRecord>>>()
            
            DevLog.info(LOG_TAG, "Mock registerNewEvents called for ${eventPairs.size} events")
            
            EventsStorage(context).classCustomUse { db ->
                for ((_, event) in eventPairs) {
                    db.addEvent(event)
                }
            }
            
            // Convert the list to ArrayList to match the expected return type
            ArrayList(eventPairs)
        }
    }
    
    /**
     * Mocks the shouldMarkEventAsHandledAndSkip method with behavior similar to the original
     */
    private fun mockShouldMarkEventAsHandledAndSkip() {
        DevLog.info(LOG_TAG, "Mocking shouldMarkEventAsHandledAndSkip")
        
        every { ApplicationController.shouldMarkEventAsHandledAndSkip(any(), any()) } answers {
            val context = firstArg<Context>()
            val event = secondArg<EventAlertRecord>()
            
            DevLog.info(LOG_TAG, "Mock shouldMarkEventAsHandledAndSkip called for eventId=${event.eventId}, title=${event.title}, isAllDay=${event.isAllDay}, eventStatus=${event.eventStatus}, attendanceStatus=${event.attendanceStatus}")
            
            // In testing, we default to false to allow events to be processed
            val result = false
            
            DevLog.info(LOG_TAG, "Mock shouldMarkEventAsHandledAndSkip result=$result")
            result
        }
    }
    
    /**
     * Mocks the postEventNotifications method
     */
    private fun mockPostEventNotifications() {
        DevLog.info(LOG_TAG, "Mocking postEventNotifications")
        
        every { ApplicationController.postEventNotifications(any(), any<Collection<EventAlertRecord>>()) } answers {
            val context = firstArg<Context>()
            val events = secondArg<Collection<EventAlertRecord>>()
            
            DevLog.info(LOG_TAG, "Mock postEventNotifications called for ${events.size} events")
            
            if (events.size == 1) {
                mockNotificationManager.onEventAdded(context, mockFormatter, events.first())
            } else {
                mockNotificationManager.postEventNotifications(context, mockFormatter)
            }
        }
    }
    
    /**
     * Mocks the afterCalendarEventFired method
     */
    private fun mockAfterCalendarEventFired() {
        DevLog.info(LOG_TAG, "Mocking afterCalendarEventFired")
        
        every { ApplicationController.afterCalendarEventFired(any()) } answers {
            val context = firstArg<Context>()
            DevLog.info(LOG_TAG, "Mock afterCalendarEventFired called for context=${context.hashCode()}")
            
            // This method normally triggers UI notifications and reschedules alarms
            // We just log it for now
        }
    }
    
    /**
     * Mocks the calendar reload methods to match the original test implementation
     */
    private fun mockCalendarReloadMethods() {
        DevLog.info(LOG_TAG, "Mocking calendar reload methods")
        
        every { ApplicationController.onCalendarReloadFromService(any(), any()) } answers {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 2) stackTrace[2].methodName else "unknown"
            val callerClass = if (stackTrace.size > 2) stackTrace[2].className else "unknown"
            
            DevLog.info(LOG_TAG, "Mock onCalendarReloadFromService Reload attempt from: $callerClass.$caller")
            
            val context = firstArg<Context>()
            val userActionUntil = secondArg<Long>()
            DevLog.info(LOG_TAG, "Mock onCalendarReloadFromService called with userActionUntil=$userActionUntil")
            
            // Call original if available (might not be needed)
            //callOriginal()
            
            DevLog.info(LOG_TAG, "Mock onCalendarReloadFromService completed")
            
            true // Indicate success
        }
        
        every { ApplicationController.onCalendarRescanForRescheduledFromService(any(), any()) } answers {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 2) stackTrace[2].methodName else "unknown"
            val callerClass = if (stackTrace.size > 2) stackTrace[2].className else "unknown"
            
            DevLog.info(LOG_TAG, "Mock onCalendarRescanForRescheduledFromService Rescan attempt from: $callerClass.$caller")
            
            val context = firstArg<Context>()
            val userActionUntil = secondArg<Long>()
            DevLog.info(LOG_TAG, "Mock onCalendarRescanForRescheduledFromService called with userActionUntil=$userActionUntil")
            
            // Call original if available (might not be needed)
            //callOriginal()
        }
        
        every { ApplicationController.onCalendarChanged(any()) } answers {
            val context = firstArg<Context>()
            DevLog.info(LOG_TAG, "Mock onCalendarChanged called")
            
            // This would normally trigger the CalendarMonitor.launchRescanService method
            calendarProvider.mockCalendarMonitor.launchRescanService(
                context,
              (Consts.ALARM_THRESHOLD / 2).toInt(),
                true, // reloadCalendar
                true,  // rescanMonitor
                0L     // userActionUntil
            )
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
            DevLog.info(LOG_TAG, "Found ${events.size} events in storage")
            
            events.forEach { event ->
                DevLog.info(LOG_TAG, "Event in storage: id=${event.eventId}, timeFirstSeen=${event.timeFirstSeen}, startTime=${event.startTime}, title=${event.title}")
            }
            
            // Find event by ID
            val processedEvent = events.firstOrNull { it.eventId == eventId }
            
            if (processedEvent != null) {
                eventFound = true
                DevLog.info(LOG_TAG, "Found processed event: id=${processedEvent.eventId}, title=${processedEvent.title}, startTime=${processedEvent.startTime}")
                
                if (title != null && processedEvent.title != title) {
                    DevLog.error(LOG_TAG, "Event title mismatch: expected '$title' but was '${processedEvent.title}'")
                    eventFound = false
                }
                
                if (processedEvent.startTime != startTime) {
                    DevLog.error(LOG_TAG, "Event start time mismatch: expected $startTime but was ${processedEvent.startTime}")
                    eventFound = false
                } else {}
            } else {
                DevLog.error(LOG_TAG, "Event $eventId not found in storage!")
            }
        }
        
        return eventFound
    }
    
    /**
     * Verifies that no events are present in storage
     */
    fun verifyNoEvents(): Boolean {
        DevLog.info(LOG_TAG, "Verifying no events are in storage")

        var hasNoEvents = true

        EventsStorage(contextProvider.fakeContext).classCustomUse { db ->
            val events = db.events
            DevLog.info(LOG_TAG, "Found ${events.size} events in storage")
            
            if (events.isNotEmpty()) {
                events.forEach { event ->
                    DevLog.info(LOG_TAG, "Unexpected event found: id=${event.eventId}, title=${event.title}")
                }

              hasNoEvents = false
            }
        }
        
        return hasNoEvents
    }
    
    /**
     * Cleans up resources
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up MockApplicationComponents")
    }
} 
