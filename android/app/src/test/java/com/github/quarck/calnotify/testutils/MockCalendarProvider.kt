package com.github.quarck.calnotify.testutils

import android.content.Context
import android.provider.CalendarContract
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorInterface
import com.github.quarck.calnotify.logs.DevLog
import io.mockk.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Provides calendar-related mock functionality for Robolectric tests
 *
 * This class creates minimal calendar mocks needed for Robolectric tests
 */
class MockCalendarProvider(
    private val contextProvider: MockContextProvider,
    private val timeProvider: MockTimeProvider
) {
    private val LOG_TAG = "MockCalendarProvider"
    
    // Core components
    lateinit var mockCalendarMonitor: CalendarMonitorInterface
        private set
    
    // Track initialization state
    private var isInitialized = false
    
    // In-memory storage for calendars and events
    private val calendars = mutableMapOf<Long, CalendarRecord>()
    private val events = mutableMapOf<Long, EventRecord>()
    private val eventReminders = mutableMapOf<Long, MutableList<EventReminderRecord>>()
    private val nextCalendarId = AtomicLong(1)
    private val nextEventId = AtomicLong(1)
    
    /**
     * Sets up the mock calendar provider and related components
     */
    fun setup() {
        if (isInitialized) {
            DevLog.info(LOG_TAG, "MockCalendarProvider already initialized, skipping setup")
            return
        }
        
        DevLog.info(LOG_TAG, "Setting up MockCalendarProvider")
        
        // Create minimal mocks for Robolectric tests
        setupCalendarProvider()
        setupMockCalendarMonitor()
        
        isInitialized = true
    }
    
    /**
     * Creates and configures the mock calendar provider
     */
    private fun setupCalendarProvider() {
        DevLog.info(LOG_TAG, "Setting up CalendarProvider delegation")
        
        // Mock the CalendarProvider object
        mockkObject(CalendarProvider)
        
        // Stub getCalendarById
        every { 
            CalendarProvider.getCalendarById(any(), any<Long>())
        } answers {
            val calendarId = secondArg<Long>()
            calendars[calendarId]
        }
        
        // Stub getCalendars
        every {
            CalendarProvider.getCalendars(any())
        } answers {
            calendars.values.toList()
        }
        
        // Stub getHandledCalendarsIds
        every {
            CalendarProvider.getHandledCalendarsIds(any(), any())
        } answers {
            val context = firstArg<Context>()
            val settings = secondArg<Settings>()
            calendars.values
                .filter { settings.getCalendarIsHandled(it.calendarId) }
                .map { it.calendarId }
                .toSet()
        }
        
        // Stub getEvent
        every {
            CalendarProvider.getEvent(any(), any<Long>())
        } answers {
            val eventId = secondArg<Long>()
            events[eventId]
        }
        
        // Stub getEventReminders
        every {
            CalendarProvider.getEventReminders(any(), any<Long>())
        } answers {
            val eventId = secondArg<Long>()
            eventReminders[eventId] ?: emptyList()
        }
        
        // Stub isRepeatingEvent
        every {
            CalendarProvider.isRepeatingEvent(any(), any<Long>())
        } answers {
            val eventId = secondArg<Long>()
            val event = events[eventId]
            event?.details?.repeatingRule?.isNotEmpty() == true
        }
        
        every {
            CalendarProvider.isRepeatingEvent(any(), any<EventAlertRecord>())
        } answers {
            val event = secondArg<EventAlertRecord>()
            event.isRepeating
        }
        
        // Stub getNextEventReminderTime
        every {
            CalendarProvider.getNextEventReminderTime(any(), any<EventAlertRecord>())
        } answers {
            val event = secondArg<EventAlertRecord>()
            val reminders = eventReminders[event.eventId] ?: emptyList()
            if (reminders.isNotEmpty()) {
                event.startTime - reminders.minOfOrNull { it.millisecondsBefore }!!
            } else {
                0L
            }
        }
        
        every {
            CalendarProvider.getNextEventReminderTime(any(), any<Long>(), any<Long>())
        } answers {
            val eventId = secondArg<Long>()
            val instanceStartTime = thirdArg<Long>()
            val reminders = eventReminders[eventId] ?: emptyList()
            if (reminders.isNotEmpty()) {
                instanceStartTime - reminders.minOfOrNull { it.millisecondsBefore }!!
            } else {
                0L
            }
        }
        
        // Stub updateEvent
        every {
            CalendarProvider.updateEvent(any(), any<Long>(), any<Long>(), any(), any())
        } answers {
            val eventId = secondArg<Long>()
            val newDetails = arg<CalendarEventDetails>(4)
            val event = events[eventId]
            if (event != null) {
                events[eventId] = event.copy(details = newDetails)
                true
            } else {
                false
            }
        }
        
        every {
            CalendarProvider.updateEvent(any(), any<EventRecord>(), any())
        } answers {
            val event = secondArg<EventRecord>()
            val newDetails = thirdArg<CalendarEventDetails>()
            events[event.eventId] = event.copy(details = newDetails)
            true
        }
        
        // Stub deleteEvent
        every {
            CalendarProvider.deleteEvent(any(), any<Long>())
        } answers {
            val eventId = secondArg<Long>()
            val removed = events.remove(eventId) != null
            eventReminders.remove(eventId)
            removed
        }
        
        // Stub moveEvent
        every {
            CalendarProvider.moveEvent(any(), any<Long>(), any<Long>(), any<Long>())
        } answers {
            val eventId = secondArg<Long>()
            val newStartTime = thirdArg<Long>()
            val newEndTime = arg<Long>(3)
            val event = events[eventId]
            if (event != null) {
                val newDetails = event.details.copy(
                    startTime = newStartTime,
                    endTime = newEndTime
                )
                events[eventId] = event.copy(details = newDetails)
                true
            } else {
                false
            }
        }
        
        // Stub getCalendarBackupInfo
        every {
            CalendarProvider.getCalendarBackupInfo(any(), any<Long>())
        } answers {
            val calendarId = secondArg<Long>()
            val calendar = calendars[calendarId]
            calendar?.let {
                CalendarBackupInfo(
                    calendarId = it.calendarId,
                    accountName = it.accountName,
                    accountType = it.accountType,
                    ownerAccount = it.owner,
                    displayName = it.displayName,
                    name = it.name
                )
            }
        }
        
        // Stub findMatchingCalendarId
        every {
            CalendarProvider.findMatchingCalendarId(any(), any())
        } answers {
            val backupInfo = secondArg<CalendarBackupInfo>()
            // Try exact match first
            calendars.values.firstOrNull {
                it.accountName == backupInfo.accountName &&
                it.owner == backupInfo.ownerAccount &&
                it.displayName == backupInfo.displayName
            }?.calendarId ?: -1L
        }
        
        // Stub dismissNativeEventAlert - no-op for mocks
        every {
            CalendarProvider.dismissNativeEventAlert(any(), any<Long>())
        } just Runs
        
        // Stub other methods that might be called
        every {
            CalendarProvider.getAlertByTime(any(), any(), any(), any())
        } returns emptyList()
        
        every {
            CalendarProvider.getAlertByEventIdAndTime(any(), any<Long>(), any<Long>())
        } returns null
        
        every {
            CalendarProvider.getEventAlertsForInstancesInRange(any(), any<Long>(), any<Long>())
        } returns emptyList()
    }
    
    /**
     * Sets up a mock calendar monitor
     */
    private fun setupMockCalendarMonitor() {
        DevLog.info(LOG_TAG, "Setting up mock calendar monitor")
        
        // Create a minimal mock monitor
        mockCalendarMonitor = mockk<CalendarMonitorInterface>(relaxed = true)
    }
    
    /**
     * Creates a test calendar with specified settings
     */
    fun createTestCalendar(
        context: Context,
        displayName: String,
        accountName: String,
        ownerAccount: String,
        isHandled: Boolean = true,
        isVisible: Boolean = true,
        timeZone: String = "UTC"
    ): Long {
        DevLog.info(LOG_TAG, "Creating test calendar: name=$displayName")
        
        val calendarId = nextCalendarId.getAndIncrement()
        
        val calendar = CalendarRecord(
            calendarId = calendarId,
            owner = ownerAccount,
            displayName = displayName,
            name = displayName,
            accountName = accountName,
            accountType = CalendarContract.ACCOUNT_TYPE_LOCAL,
            timeZone = timeZone,
            color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
            isVisible = isVisible,
            isPrimary = false,
            isReadOnly = false,
            isSynced = true
        )
        
        calendars[calendarId] = calendar
        
        // Set calendar handling status
        contextProvider.setCalendarHandlingStatusDirectly(calendarId, isHandled)
        
        DevLog.info(LOG_TAG, "Created test calendar: id=$calendarId")
        return calendarId
    }
    
    /**
     * Creates a test event with the specified details
     */
    fun createTestEvent(
        context: Context,
        calendarId: Long,
        title: String = "Test Event",
        description: String = "Test Description",
        startTime: Long = timeProvider.testClock.currentTimeMillis() + 3600000,
        duration: Long = 3600000,
        location: String = "",
        isAllDay: Boolean = false,
        repeatingRule: String = "",
        timeZone: String = "UTC",
        reminderMinutes: Int = 15
    ): Long {
        DevLog.info(LOG_TAG, "Creating test event: title=$title, startTime=$startTime")
        
        val eventId = nextEventId.getAndIncrement()
        
        val reminders = if (reminderMinutes > 0) {
            listOf(EventReminderRecord(
                millisecondsBefore = reminderMinutes * 60 * 1000L,
                method = CalendarContract.Reminders.METHOD_ALERT
            ))
        } else {
            emptyList()
        }
        
        val details = CalendarEventDetails(
            title = title,
            desc = description,
            location = location,
            timezone = timeZone,
            startTime = startTime,
            endTime = startTime + duration,
            isAllDay = isAllDay,
            reminders = reminders,
            repeatingRule = repeatingRule,
            color = Consts.DEFAULT_CALENDAR_EVENT_COLOR
        )
        
        val event = EventRecord(
            calendarId = calendarId,
            eventId = eventId,
            details = details
        )
        
        events[eventId] = event
        eventReminders[eventId] = reminders.toMutableList()
        
        DevLog.info(LOG_TAG, "Created test event: id=$eventId")
        return eventId
    }
    
    /**
     * Clears all calendars and events from storage
     */
    fun clearAll() {
        DevLog.info(LOG_TAG, "Clearing all calendars and events")
        calendars.clear()
        events.clear()
        eventReminders.clear()
        nextCalendarId.set(1)
        nextEventId.set(1)
    }
    
    /**
     * Cleans up resources
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up MockCalendarProvider")
        clearAll()
        isInitialized = false
    }
} 
