package com.github.quarck.calnotify.testutils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendar.CalendarProvider.getEventAlertsForInstancesInRange
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitor
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorInterface
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorState
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import io.mockk.*

/**
 * Provides calendar-related mock functionality for tests
 *
 * This class creates and manages mock calendar data and providers
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
    
    // Reference to real provider for delegation
    private val realProvider = CalendarProvider
    
    /**
     * Sets up the mock calendar provider and related components
     */
    fun setup() {
        if (isInitialized) {
            DevLog.info(LOG_TAG, "MockCalendarProvider already initialized, skipping setup")
            return
        }
        
        DevLog.info(LOG_TAG, "Setting up MockCalendarProvider")
        
        // Create mocks in the correct order to avoid duplication and recursive calls
        setupCalendarProvider()
        setupMockCalendarMonitor()
        setupEventMocks() // Set up event-specific mocks
        
        isInitialized = true
    }
    
    /**
     * Creates and configures the mock calendar provider
     */
    private fun setupCalendarProvider() {
        DevLog.info(LOG_TAG, "Setting up CalendarProvider delegation")
        
        // Mock the CalendarProvider object but delegate to real implementation by default
        mockkObject(CalendarProvider)
        
        // Use real implementations for all methods
        every { CalendarProvider.getEventReminders(any(), any<Long>()) } answers {
            callOriginal()
        }
        
        every { CalendarProvider.isRepeatingEvent(any(), any<Long>()) } answers {
            callOriginal()
        }
        
        every { CalendarProvider.isRepeatingEvent(any(), any<EventAlertRecord>()) } answers {
            callOriginal()
        }
        
        every { CalendarProvider.dismissNativeEventAlert(any(), any()) } answers {
            callOriginal()
        }
        
        every { CalendarProvider.getAlertByTime(any(), any(), any(), any()) } answers {
            callOriginal()
        }
        
        every { CalendarProvider.getAlertByEventIdAndTime(any(), any(), any()) } answers {
            val context = firstArg<Context>()
            val eventId = secondArg<Long>()
            val time = thirdArg<Long>()
            
            DevLog.info(LOG_TAG, "Delegating getAlertByEventIdAndTime to real implementation: eventId=$eventId, time=$time")
            callOriginal()
        }
        
        every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
            val context = firstArg<Context>()
            val scanFrom = secondArg<Long>()
            val scanTo = thirdArg<Long>()
            
            DevLog.info(LOG_TAG, "Delegating getEventAlertsForInstancesInRange to real implementation: scanFrom=$scanFrom, scanTo=$scanTo")
            callOriginal()
        }

        // Add delegation for getEvent and updateEvent with proper parameter types
        every { CalendarProvider.getEvent(any(), any<Long>()) } answers {
            val context = firstArg<Context>()
            val eventId = secondArg<Long>()
            DevLog.info(LOG_TAG, "Delegating getEvent to real implementation: eventId=$eventId")
            callOriginal()
        }

        every { CalendarProvider.updateEvent(any(), any<Long>(), any<Long>(), any(), any()) } answers {
            val context = firstArg<Context>()
            val eventId = secondArg<Long>()
            val calendarId = thirdArg<Long>()
            val oldDetails = arg<CalendarEventDetails>(3)
            val newDetails = arg<CalendarEventDetails>(4)
            DevLog.info(LOG_TAG, "Delegating updateEvent to real implementation: eventId=$eventId, calendarId=$calendarId")
            callOriginal()
        }
        
        every { CalendarProvider.getUpcomingEventCountsByCalendar(any(), any()) } answers {
            callOriginal()
        }
    }
    
    /**
     * Sets up a mock calendar monitor
     */
    private fun setupMockCalendarMonitor() {
        DevLog.info(LOG_TAG, "Setting up mock calendar monitor")
        
        // Create a real calendar monitor with mock components
        val realMonitor = CalendarMonitor(CalendarProvider, timeProvider.testClock)
        
        // Create a spy to intercept key methods
        mockCalendarMonitor = spyk(realMonitor, recordPrivateCalls = true)
        
        // First ensure ApplicationController.CalendarMonitor returns our mock
        mockkObject(ApplicationController)
        every { ApplicationController.CalendarMonitor } returns mockCalendarMonitor
        
        // Mock onRescanFromService to use our event alerts implementation
        every { mockCalendarMonitor.onRescanFromService(any()) } answers {
            val context = firstArg<Context>()
            val monitorState = CalendarMonitorState(context)
            DevLog.info(LOG_TAG, "Mock onRescanFromService called, firstScanEver=${monitorState.firstScanEver}")
            
            // Call original implementation for side effects (scanning, etc.)
            callOriginal()
        }
        
        // The critical method - handle alarm broadcasts to process alerts
        every { mockCalendarMonitor.onAlarmBroadcast(any(), any()) } answers {
            val context = firstArg<Context>()
            val intent = secondArg<Intent>()
            
            DevLog.info(LOG_TAG, "Mock onAlarmBroadcast called with intent action: ${intent.action}")
            
            // Extract alert time from intent
            val alertTime = intent.getLongExtra("alert_time", 0)
            if (alertTime > 0) {
                DevLog.info(LOG_TAG, "Processing alerts for alertTime=$alertTime")
                
                // Get unhandled alerts for this alert time
                MonitorStorage(context).use { db ->
                    val alerts = db.alerts.filter { 
                        !it.wasHandled && it.alertTime <= alertTime 
                    }
                    
                    if (alerts.isNotEmpty()) {
                        DevLog.info(LOG_TAG, "Found ${alerts.size} unhandled alerts to process")
                        
                        alerts.forEach { alert ->
                            // Get the actual title for this event ID
                            val eventTitle = CalendarProvider.getEvent(context, alert.eventId)?.details?.title ?: "Test Event"
                            
                            // Create an event record for each alert
                            val eventRecord = createEventAlertRecord(
                                context,
                                1, // Default calendar ID, will be overridden if needed
                                alert.eventId,
                                eventTitle,
                                alert.instanceStartTime,
                                alert.alertTime
                            )
                            
                            if (eventRecord != null) {
                                // Add the event to storage
                                EventsStorage(context).use { eventsDb ->
                                    eventsDb.addEvent(eventRecord)
                                    DevLog.info(LOG_TAG, "Added event to storage: id=${eventRecord.eventId}, title=${eventRecord.title}")
                                }
                                
                                // Mark the alert as handled and update in database
                                alert.wasHandled = true
                                db.updateAlert(alert)
                                DevLog.info(LOG_TAG, "Marked alert as handled: eventId=${alert.eventId}, alertTime=${alert.alertTime}")
                                
                                // Verify the update was successful
                                val updatedAlert = db.alerts.firstOrNull { it.eventId == alert.eventId && it.alertTime == alert.alertTime }
                                if (updatedAlert?.wasHandled != true) {
                                    DevLog.error(LOG_TAG, "Failed to update alert in database: eventId=${alert.eventId}")
                                }
                            } else {
                                DevLog.error(LOG_TAG, "Failed to create event record for alert: eventId=${alert.eventId}")
                            }
                        }
                    } else {
                        DevLog.info(LOG_TAG, "No unhandled alerts found for alertTime=$alertTime")
                    }
                }
            } else {
                DevLog.info(LOG_TAG, "No alert time specified in intent, skipping alert processing")
            }
            
            // Don't call original to avoid potential recursion
        }
        
        // Prevent actual service launches which could create recursion
        every { mockCalendarMonitor.launchRescanService(any(), any(), any(), any(), any()) } answers {
            val context = firstArg<Context>()
            val delayed = invocation.args[1] as Int
            val reloadCalendar = invocation.args[2] as Boolean
            val rescanMonitor = invocation.args[3] as Boolean
            val startDelay = invocation.args[4] as Long
            
            DevLog.info(LOG_TAG, "Mock launchRescanService called with delayed=$delayed, " +
                "reloadCalendar=$reloadCalendar, rescanMonitor=$rescanMonitor, startDelay=$startDelay")
            
            // Don't call original to avoid actual service launch
        }
    }
    
    /**
     * Creates a test calendar
     */
    fun createTestCalendar(
        context: Context,
        displayName: String,
        accountName: String,
        ownerAccount: String,
        isHandled: Boolean = true
    ): Long {
        DevLog.info(LOG_TAG, "Creating test calendar: name=$displayName")
        
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
            put(CalendarContract.Calendars.NAME, displayName)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ownerAccount)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "UTC")
        }

        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()

        val calUri = context.contentResolver.insert(uri, values)
        val calendarId = calUri?.lastPathSegment?.toLongOrNull() ?: -1L
        
        if (calendarId <= 0) {
            DevLog.error(LOG_TAG, "Failed to create test calendar")
        } else {
            DevLog.info(LOG_TAG, "Created test calendar: id=$calendarId")
            
            // Use direct preference manipulation for more reliable testing
            contextProvider.setCalendarHandlingStatusDirectly(calendarId, isHandled)
            
            // Verify the setting was correctly applied
            val settings = Settings(context)
            val actualHandled = settings.getCalendarIsHandled(calendarId)
            DevLog.info(LOG_TAG, "Set calendar handling state: id=$calendarId, isHandled=$isHandled")
            
            if (actualHandled != isHandled) {
                DevLog.error(LOG_TAG, "Failed to set calendar handling state: expected=$isHandled, actual=$actualHandled")
            }
        }
        
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
        startTime: Long = timeProvider.testClock.currentTimeMillis() + 3600000, // 1 hour from now
        duration: Long = 3600000, // 1 hour duration
        location: String = "",
        isAllDay: Boolean = false,
        repeatingRule: String = "",
        timeZone: String = "UTC",
        reminderMinutes: Int = 15  // Default 15 minutes reminder
    ): Long {
        DevLog.info(LOG_TAG, "Creating test event: title=$title, startTime=$startTime")
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startTime)
            put(CalendarContract.Events.DTEND, startTime + duration)
            put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
            put(CalendarContract.Events.HAS_ALARM, 1)
            if (repeatingRule.isNotEmpty()) {
                put(CalendarContract.Events.RRULE, repeatingRule)
            }
        }

        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = eventUri?.lastPathSegment?.toLong() ?: -1L
        
        if (eventId > 0) {
            // Add a default reminder - pass the start time to avoid recursion
            addReminderToEvent(context, eventId, reminderMinutes, startTime)
            
            DevLog.info(LOG_TAG, "Created test event: id=$eventId")
        } else {
            DevLog.error(LOG_TAG, "Failed to create test event")
        }
        
        return eventId
    }
    
    /**
     * Adds a reminder to an event
     */
    private fun addReminderToEvent(
        context: Context,
        eventId: Long,
        minutesBefore: Int = 15,
        startTime: Long? = null
    ) {
        DevLog.info(LOG_TAG, "Adding reminder to event $eventId: $minutesBefore minutes before")
        
        // Add reminder
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        
        val reminderUri = context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        
        if (reminderUri == null) {
            DevLog.error(LOG_TAG, "Failed to add reminder to event $eventId")
            return
        }
        
        // Only add alert if we have a start time
        val eventStartTime = startTime ?: timeProvider.testClock.currentTimeMillis() + 3600000
        
        // Calculate alert time
        val alertTime = eventStartTime - (minutesBefore * 60 * 1000)
        
        // Add corresponding alert
        val alertValues = ContentValues().apply {
            put(CalendarContract.CalendarAlerts.EVENT_ID, eventId)
            put(CalendarContract.CalendarAlerts.BEGIN, eventStartTime)
            put(CalendarContract.CalendarAlerts.END, eventStartTime + 3600000)  // 1 hour duration
            put(CalendarContract.CalendarAlerts.ALARM_TIME, alertTime)
            put(CalendarContract.CalendarAlerts.STATE, CalendarContract.CalendarAlerts.STATE_SCHEDULED)
            put(CalendarContract.CalendarAlerts.MINUTES, minutesBefore)
        }
        
        val alertUri = context.contentResolver.insert(CalendarContract.CalendarAlerts.CONTENT_URI, alertValues)
        
        if (alertUri == null) {
            DevLog.error(LOG_TAG, "Failed to add alert for event $eventId")
        } else {
            DevLog.info(LOG_TAG, "Added reminder and alert to event $eventId: $minutesBefore minutes before, alertTime=$alertTime")
        }
    }
    
    /**
     * Clears all storage databases
     */
    fun clearStorages(context: Context) {
        DevLog.info(LOG_TAG, "Clearing storages")
        
        try {
            EventsStorage(context).use { db ->
                val count = db.events.size
                db.deleteAllEvents()
                DevLog.info(LOG_TAG, "Cleared $count events from storage")
            }
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Failed to clear events storage: ${e.message}")
        }
        
        try {
            MonitorStorage(context).use { db ->
                val count = db.alerts.size
                db.deleteAlertsMatching { true }
                DevLog.info(LOG_TAG, "Cleared $count alerts from storage")
            }
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Failed to clear monitor storage: ${e.message}")
        }
    }
    
    /**
     * Simulates a calendar change notification
     */
    fun notifyCalendarChange(context: Context) {
        // Create and start service intent directly to avoid going through ApplicationController
        val intent = Intent().apply {
            putExtra("reload_calendar", true)
            putExtra("rescan_monitor", true)
            putExtra("start_delay", 0L)
        }
        
        context.startService(intent)
        DevLog.info(LOG_TAG, "Simulated calendar change notification")
    }
    
    /**
     * Creates an EventAlertRecord for testing purposes
     */
    fun createEventAlertRecord(
        context: Context,
        calendarId: Long,
        eventId: Long,
        title: String,
        startTime: Long,
        alertTime: Long,
        description: String = "Test Description",
        location: String = "",
        duration: Long = 3600000,
        isAllDay: Boolean = false,
        isRepeating: Boolean = false
    ): EventAlertRecord? {
        DevLog.info(LOG_TAG, "Creating EventAlertRecord for eventId=$eventId, title=$title")
        
        // Use the current test clock time for timeFirstSeen to properly simulate
        // when the event was first detected by the system
        val timeFirstSeen = timeProvider.testClock.currentTimeMillis()
        
        DevLog.info(LOG_TAG, "Using timeFirstSeen=$timeFirstSeen for event with startTime=$startTime")
        
        return EventAlertRecord(
            calendarId = calendarId,
            eventId = eventId,
            isAllDay = isAllDay,
            isRepeating = isRepeating,
            alertTime = alertTime,
            notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
            title = title,
            desc = description,
            startTime = startTime,
            endTime = startTime + duration,
            instanceStartTime = startTime,
            instanceEndTime = startTime + duration,
            location = location,
            lastStatusChangeTime = timeProvider.testClock.currentTimeMillis(),
            displayStatus = EventDisplayStatus.Hidden,
            color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = timeFirstSeen,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None
        )
    }
    
    /**
     * Cleans up resources
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up MockCalendarProvider")
        isInitialized = false
    }
    
    /**
     * Gets the title of an event by its ID
     */
    fun getEventTitle(context: Context, eventId: Long): String? {
        DevLog.info(LOG_TAG, "Getting title for event: id=$eventId")
        
        val event = CalendarProvider.getEvent(context, eventId)
        return event?.details?.title
    }
    
    /**
     * Creates a delayed event alert that respects a specified delay period
     * Uses real calendar events and reminders instead of mocking
     */
    fun mockDelayedEventAlerts(eventId: Long, startTime: Long, delay: Long) {
        val context = contextProvider.fakeContext
        
        // Create a test calendar if needed
        val calendarId = createTestCalendar(
            context = context,
            displayName = "Test Calendar",
            accountName = "test@test.com",
            ownerAccount = "test@test.com"
        )
        
        // Calculate the actual event start time (after the delay)
        val eventStartTime = startTime + delay
        
        // Create the event with a reminder that will trigger after the delay
        val createdEventId = createTestEvent(
            context = context,
            calendarId = calendarId,
            title = "Delayed Test Event",
            startTime = eventStartTime,
            duration = 3600000, // 1 hour
            reminderMinutes = 15 // 15 minutes before event
        )
        
        // Set the test clock to control when alerts become visible
        // Important: Set the time to the start time, before the delay has elapsed
        timeProvider.testClock.setCurrentTime(startTime)
        
        DevLog.info(LOG_TAG, "Created delayed event: id=$createdEventId, startTime=$eventStartTime, delay=$delay")
        
        // Create the alert in monitor storage, but DO NOT add it to event storage yet
        MonitorStorage(context).use { db ->
            val alert = MonitorEventAlertEntry(
                eventId = createdEventId,
                isAllDay = false,
                alertTime = eventStartTime - (15 * 60 * 1000), // 15 minutes before
                instanceStartTime = eventStartTime,
                instanceEndTime = eventStartTime + 3600000, // 1 hour duration
                alertCreatedByUs = false,
                wasHandled = false
            )
            db.addAlert(alert)
            DevLog.info(LOG_TAG, "Added alert to storage: eventId=$createdEventId, alertTime=${alert.alertTime}")
            
            // CRITICAL: Don't add the event to events storage yet
            // The test should call processEventAlerts() after advancing the test clock
            // past the delay to add the event to storage, simulating proper delay behavior
            
            // Store event details for later processing if needed
            val eventRecord = createEventAlertRecord(
                context = context,
                calendarId = calendarId,
                eventId = createdEventId,
                title = "Delayed Test Event",
                startTime = eventStartTime,
                alertTime = alert.alertTime
            )
            
            // Log that we created the event record but are NOT storing it yet
            if (eventRecord != null) {
                DevLog.info(LOG_TAG, "Created event record for delayed event: id=$createdEventId, title=${eventRecord.title}, but NOT adding to storage yet")
            }
        }
    }
    
    /**
     * Mocks updating an event
     */
    fun mockUpdateEvent() {
        DevLog.info(LOG_TAG, "Setting up mock for updateEvent")
        
        every { CalendarProvider.updateEvent(any(), any(), any(), any(), any()) } answers {
            val context = firstArg<Context>()
            val eventId = secondArg<Long>()
            val calendarId = thirdArg<Long>()
            val oldDetails = arg<CalendarEventDetails>(3)
            val newDetails = arg<CalendarEventDetails>(4)
            
            DevLog.info(LOG_TAG, "Mock updateEvent called for eventId=$eventId, title=${newDetails.title}")
            
            // Call the real implementation
            callOriginal()
        }
    }
    
    /**
     * Mocks moving an event
     */
    fun mockMoveEvent() {
        DevLog.info(LOG_TAG, "Setting up mock for moveEvent")
        
        every { CalendarProvider.moveEvent(any(), any(), any(), any()) } answers {
            val context = firstArg<Context>()
            val eventId = secondArg<Long>()
            val newStartTime = thirdArg<Long>()
            val newEndTime = arg<Long>(3)
            
            DevLog.info(LOG_TAG, "Mock moveEvent called for eventId=$eventId, newStartTime=$newStartTime")
            
            // Call the real implementation
            callOriginal()
        }
    }
    
    /**
     * Mocks deleting an event
     */
    fun mockDeleteEvent() {
        DevLog.info(LOG_TAG, "Setting up mock for deleteEvent")
        
        every { CalendarProvider.deleteEvent(any(), any()) } answers {
            val context = firstArg<Context>()
            val eventId = secondArg<Long>()
            
            DevLog.info(LOG_TAG, "Mock deleteEvent called for eventId=$eventId")
            
            // Call the real implementation
            callOriginal()
        }
    }

    fun mockGetEvent() {
        DevLog.info(LOG_TAG, "Setting up mock for getEvent")
        
        every { CalendarProvider.getEvent(any(), any()) } answers {
            val context = firstArg<Context>()
            val eventId = secondArg<Long>()
            
            DevLog.info(LOG_TAG, "Mock getEvent called for eventId=$eventId")
            
            // Call the real implementation
            callOriginal()
        }
    }
    
    /**
     * Call this during setup to initialize all event-related mocks
     */
    fun setupEventMocks() {
        DevLog.info(LOG_TAG, "Setting up all event-related mocks")
        mockUpdateEvent()
        mockMoveEvent()
        mockDeleteEvent()
        mockGetEvent()
    }

    /**
     * Gets an event by its ID
     */
    fun getEvent(context: Context, eventId: Long): EventRecord? {
        DevLog.info(LOG_TAG, "Getting event: id=$eventId")
        return realProvider.getEvent(context, eventId)
    }

    /**
     * Updates an event with new details
     */
    fun updateEvent(
        context: Context,
        eventId: Long,
        calendarId: Long,
        oldDetails: CalendarEventDetails,
        newDetails: CalendarEventDetails
    ): Boolean {
        DevLog.info(LOG_TAG, "Updating event: id=$eventId, title=${newDetails.title}")
        return realProvider.updateEvent(context, eventId, calendarId, oldDetails, newDetails)
    }

    /**
     * Updates an event with new details using an EventRecord
     */
    fun updateEvent(context: Context, event: EventRecord, newDetails: CalendarEventDetails): Boolean {
        DevLog.info(LOG_TAG, "Updating event record: id=${event.eventId}, title=${newDetails.title}")
        return realProvider.updateEvent(context, event, newDetails)
    }
} 
