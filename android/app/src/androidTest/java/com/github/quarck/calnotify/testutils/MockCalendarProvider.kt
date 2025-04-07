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
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
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
        DevLog.info(LOG_TAG, "Setting up mock CalendarProvider")
        
        // Mock the CalendarProvider object with minimal implementations
        mockkObject(CalendarProvider)
        
        // Default behaviors for common methods, more specific behaviors will be added as needed
        every { CalendarProvider.getEventReminders(any(), any<Long>()) } answers {
            listOf(EventReminderRecord(millisecondsBefore = 30000))
        }
        
        // Default implementation for isRepeatingEvent
        every { CalendarProvider.isRepeatingEvent(any(), any<Long>()) } returns false
        every { CalendarProvider.isRepeatingEvent(any(), any<EventAlertRecord>()) } answers {
            val event = secondArg<EventAlertRecord>()
            event.isRepeating
        }
        
        every { CalendarProvider.dismissNativeEventAlert(any(), any()) } just Runs
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
                MonitorStorage(context).classCustomUse { db ->
                    val alerts = db.getAlertsAt(alertTime).filter { !it.wasHandled }
                    
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
                                EventsStorage(context).classCustomUse { eventsDb ->
                                    eventsDb.addEvent(eventRecord)
                                    DevLog.info(LOG_TAG, "Added event to storage: id=${eventRecord.eventId}, title=${eventRecord.title}")
                                }
                                
                                // Mark the alert as handled
                                alert.wasHandled = true
                                db.updateAlert(alert)
                                DevLog.info(LOG_TAG, "Marked alert as handled: eventId=${alert.eventId}, alertTime=${alert.alertTime}")
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
     * Creates a test event in the specified calendar
     */
    fun createTestEvent(
        context: Context,
        calendarId: Long,
        title: String = "Test Event",
        description: String = "Test Description",
        startTime: Long = timeProvider.testClock.currentTimeMillis() + 3600000, // 1 hour from now
        duration: Long = 3600000 // 1 hour duration
    ): Long {
        DevLog.info(LOG_TAG, "Creating test event: title=$title, startTime=$startTime")
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startTime)
            put(CalendarContract.Events.DTEND, startTime + duration)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.HAS_ALARM, 1)
        }

        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = eventUri?.lastPathSegment?.toLongOrNull() ?: -1L
        
        if (eventId <= 0) {
            DevLog.error(LOG_TAG, "Failed to create test event")
        } else {
            DevLog.info(LOG_TAG, "Created test event: id=$eventId")
            
            // Add a default reminder
            addReminderToEvent(context, eventId)
            
            // Mock event details
            mockEventDetails(eventId, startTime, title, duration)
        }
        
        return eventId
    }
    
    /**
     * Adds a reminder to an event
     */
    private fun addReminderToEvent(
        context: Context,
        eventId: Long,
        minutesBefore: Int = 15
    ) {
        DevLog.info(LOG_TAG, "Adding reminder to event $eventId: $minutesBefore minutes before")
        
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        
        val reminderUri = context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        
        if (reminderUri == null) {
            DevLog.error(LOG_TAG, "Failed to add reminder to event $eventId")
        } else {
            DevLog.info(LOG_TAG, "Added reminder to event $eventId: $minutesBefore minutes before")
        }
    }
    
    /**
     * Mocks event details for a specific event
     */
    fun mockEventDetails(
        eventId: Long,
        startTime: Long,
        title: String = "Test Event",
        duration: Long = 3600000,
        description: String = "Test Description",
        location: String = "",
        isAllDay: Boolean = false,
        repeatingRule: String = "",
        timeZone: String = "UTC"
    ) {
        DevLog.info(LOG_TAG, "Mocking event details for eventId=$eventId, title=$title, startTime=$startTime, repeatingRule=$repeatingRule, timeZone=$timeZone")
        
        // Use a more specific mock to avoid potential conflicts with other mocks
        every { CalendarProvider.getEvent(any(), eq(eventId)) } returns EventRecord(
            calendarId = 1, // This will be replaced by the actual calendar ID in real tests
            eventId = eventId,
            details = CalendarEventDetails(
                title = title,
                desc = description,
                location = location,
                timezone = timeZone,
                startTime = startTime,
                endTime = startTime + duration,
                isAllDay = isAllDay,
                reminders = listOf(EventReminderRecord(millisecondsBefore = 30000)),
                repeatingRule = repeatingRule,
                repeatingRDate = "",
                repeatingExRule = "",
                repeatingExRDate = "",
                color = Consts.DEFAULT_CALENDAR_EVENT_COLOR
            ),
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None
        )
        
        // If this is a recurring event, also mock the isRepeatingEvent method
        if (repeatingRule.isNotEmpty()) {
            every { CalendarProvider.isRepeatingEvent(any(), eq(eventId)) } returns true
            
            // Also mock the same result for isRepeatingEvent with the event parameter
            every { CalendarProvider.isRepeatingEvent(any(), any<EventAlertRecord>()) } answers {
                val event = secondArg<EventAlertRecord>()
                event.eventId == eventId
            }
        }
    }
    
    /**
     * Mocks event reminders for a specific event
     */
    fun mockEventReminders(
        eventId: Long,
        millisecondsBefore: Long = 30000,
        method: Int = CalendarContract.Reminders.METHOD_ALERT,
        appendMode: Boolean = false
    ) {
        DevLog.info(LOG_TAG, "Mocking event reminders for eventId=$eventId, offset=$millisecondsBefore, method=$method, appendMode=$appendMode")
        
        if (appendMode) {
            // In append mode, get existing reminders and add to them
            val existingReminders = try {
                CalendarProvider.getEventReminders(contextProvider.fakeContext, eventId)
            } catch (e: Exception) {
                DevLog.error(LOG_TAG, "Error getting existing reminders: ${e.message}")
                emptyList()
            }
            
            DevLog.info(LOG_TAG, "Found ${existingReminders.size} existing reminders for event $eventId")
            
            val updatedReminders = existingReminders + EventReminderRecord(
                millisecondsBefore = millisecondsBefore,
                method = method
            )
            
            // Use specific mock for this exact event ID
            every { CalendarProvider.getEventReminders(any(), eq(eventId)) } returns updatedReminders
            DevLog.info(LOG_TAG, "Appended reminder, now have ${updatedReminders.size} reminders for event $eventId")
            
            // Log the configured reminders
            updatedReminders.forEachIndexed { index, reminder ->
                DevLog.info(LOG_TAG, "Reminder $index: milliseconds=${reminder.millisecondsBefore}, method=${reminder.method}")
            }
        } else {
            // In replace mode, just set the single reminder
            val reminder = EventReminderRecord(
                millisecondsBefore = millisecondsBefore,
                method = method
            )
            
            every { CalendarProvider.getEventReminders(any(), eq(eventId)) } returns listOf(reminder)
            
            DevLog.info(LOG_TAG, "Set single reminder for event $eventId: milliseconds=$millisecondsBefore, method=$method")
        }
    }
    
    /**
     * Mocks multiple event reminders for a specific event
     */
    fun mockMultipleEventReminders(
        eventId: Long,
        remindersList: List<Pair<Long, Int>>  // List of (millisecondsBefore, method) pairs
    ) {
        DevLog.info(LOG_TAG, "Mocking multiple event reminders for eventId=$eventId, count=${remindersList.size}")
        
        val reminders = remindersList.map { (milliseconds, method) ->
            EventReminderRecord(
                millisecondsBefore = milliseconds,
                method = method
            )
        }
        
        every { CalendarProvider.getEventReminders(any(), eq(eventId)) } returns reminders
        
        // Log the configured reminders
        reminders.forEachIndexed { index, reminder ->
            DevLog.info(LOG_TAG, "Reminder $index: milliseconds=${reminder.millisecondsBefore}, method=${reminder.method}")
        }
    }
    
    /**
     * Mocks event alerts for a specific event and time range
     */
    fun mockEventAlerts(
        eventId: Long,
        startTime: Long,
        alertOffset: Long = 30000
    ) {
        DevLog.info(LOG_TAG, "Mocking event alerts for eventId=$eventId, startTime=$startTime, alertOffset=$alertOffset")
        
        // Set alert time to be alertOffset before startTime
        val alertTime = startTime - alertOffset
        
        // Mock getEventAlertsForInstancesInRange to return this one alert if in range
        every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
            val scanFrom = secondArg<Long>()
            val scanTo = thirdArg<Long>()
            
            if (alertTime in scanFrom..scanTo) {
                listOf(MonitorEventAlertEntry(
                    eventId = eventId,
                    isAllDay = false,
                    alertTime = alertTime,
                    instanceStartTime = startTime,
                    instanceEndTime = startTime + 3600000,
                    alertCreatedByUs = false,
                    wasHandled = false
                ))
            } else {
                emptyList()
            }
        }
        
        // Mock getAlertByEventIdAndTime specifically for this event and alert time
        every { CalendarProvider.getAlertByEventIdAndTime(any(), eq(eventId), eq(alertTime)) } returns
            EventAlertRecord(
                calendarId = 1,
                eventId = eventId,
                isAllDay = false,
                isRepeating = false,
                alertTime = alertTime,
                notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
                title = "title",
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
    }
    
    /**
     * Mocks event alerts for multiple events
     * 
     * This method configures the mock calendar provider to handle multiple events
     * when getEventAlertsForInstancesInRange is called.
     */
    fun mockMultipleEventAlerts(
        eventIds: List<Long>,
        eventTitles: List<String>? = null,
        startTime: Long
    ) {
        DevLog.info(LOG_TAG, "Mocking alerts for ${eventIds.size} events")
        
        // Mock getEventAlertsForInstancesInRange to return alerts for all events
        every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
            val scanFrom = secondArg<Long>()
            val scanTo = thirdArg<Long>()
            
            val alerts = mutableListOf<MonitorEventAlertEntry>()
            
            eventIds.forEachIndexed { index, eventId ->
                val hourOffset = index + 1
                val eventStartTime = startTime + (hourOffset * 3600000)
                val alertTime = eventStartTime - (15 * 60 * 1000)
                
                if (alertTime in scanFrom..scanTo) {
                    alerts.add(
                        MonitorEventAlertEntry(
                            eventId = eventId,
                            isAllDay = false,
                            alertTime = alertTime,
                            instanceStartTime = eventStartTime,
                            instanceEndTime = eventStartTime + 3600000,
                            alertCreatedByUs = false,
                            wasHandled = false
                        )
                    )
                    DevLog.info(LOG_TAG, "Added alert for event $eventId (startTime=$eventStartTime, alertTime=$alertTime)")
                }
            }
            
            DevLog.info(LOG_TAG, "Returning ${alerts.size} alerts for scan range $scanFrom to $scanTo")
            alerts
        }
        
        // Mock getAlertByEventIdAndTime for each event
        eventIds.forEachIndexed { index, eventId ->
            val hourOffset = index + 1
            val eventStartTime = startTime + (hourOffset * 3600000)
            val alertTime = eventStartTime - (15 * 60 * 1000)
            val title = eventTitles?.getOrNull(index) ?: "Test Event $index"
            
            every { CalendarProvider.getAlertByEventIdAndTime(any(), eq(eventId), eq(alertTime)) } returns
                EventAlertRecord(
                    calendarId = 1,
                    eventId = eventId,
                    isAllDay = false,
                    isRepeating = false,
                    alertTime = alertTime,
                    notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
                    title = title,
                    desc = "Test Description $index",
                    startTime = eventStartTime,
                    endTime = eventStartTime + 3600000,
                    instanceStartTime = eventStartTime,
                    instanceEndTime = eventStartTime + 3600000,
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
            
            DevLog.info(LOG_TAG, "Mocked getAlertByEventIdAndTime for event $eventId with title $title")
        }
    }
    
    /**
     * Clears all storage databases
     */
    fun clearStorages(context: Context) {
        DevLog.info(LOG_TAG, "Clearing storages")
        
        try {
            EventsStorage(context).classCustomUse { db ->
                val count = db.events.size
                db.deleteAllEvents()
                DevLog.info(LOG_TAG, "Cleared $count events from storage")
            }
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Failed to clear events storage: ${e.message}")
        }
        
        try {
            MonitorStorage(context).classCustomUse { db ->
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
            timeFirstSeen = timeProvider.testClock.currentTimeMillis(),
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
     * Mocks delayed event alerts that respect a specified delay period
     */
    fun mockDelayedEventAlerts(eventId: Long, startTime: Long, delay: Long) {
        every { getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
            val scanFrom = secondArg<Long>()
            val scanTo = thirdArg<Long>()

            DevLog.info(LOG_TAG, "getEventAlertsForInstancesInRange called at currentTime=${timeProvider.testClock.currentTimeMillis()}, startTime=$startTime, delay=$delay")

            if (timeProvider.testClock.currentTimeMillis() >= (startTime + delay)) {
                DevLog.info(LOG_TAG, "Returning event alert after delay")
                listOf(MonitorEventAlertEntry(
                    eventId = eventId,
                    isAllDay = false,
                    alertTime = startTime + 3600000 - 30000,
                    instanceStartTime = startTime + 3600000,
                    instanceEndTime = startTime + 7200000,
                    alertCreatedByUs = false,
                    wasHandled = false
                ))
            } else {
                DevLog.info(LOG_TAG, "Skipping event alert due to delay not elapsed: current=${timeProvider.testClock.currentTimeMillis()}, start=$startTime, delay=$delay")
                emptyList()
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
            
            // Update our mocks with the new details
            mockEventDetails(
                eventId = eventId,
                startTime = newDetails.startTime,
                title = newDetails.title,
                duration = newDetails.endTime - newDetails.startTime,
                description = newDetails.desc,
                location = newDetails.location,
                isAllDay = newDetails.isAllDay,
                repeatingRule = newDetails.repeatingRule,
                timeZone = newDetails.timezone
            )
            
            true
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
            val newEndTime = invocation.args[3] as Long
            
            DevLog.info(LOG_TAG, "Mock moveEvent called for eventId=$eventId, newStartTime=$newStartTime")
            
            // Get the current details
            val event = CalendarProvider.getEvent(context, eventId)
            
            if (event != null) {
                // Update our mocks with the new times
                mockEventDetails(
                    eventId = eventId,
                    startTime = newStartTime,
                    title = event.details.title,
                    duration = newEndTime - newStartTime,
                    description = event.details.desc,
                    location = event.details.location,
                    isAllDay = event.details.isAllDay,
                    repeatingRule = event.details.repeatingRule,
                    timeZone = event.details.timezone
                )
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Mocks deleting an event
     */
    fun mockDeleteEvent() {
        DevLog.info(LOG_TAG, "Setting up mock for deleteEvent")
        
        val deletedEvents = mutableSetOf<Long>()
        
        every { CalendarProvider.deleteEvent(any(), any()) } answers {
            val context = firstArg<Context>()
            val eventId = secondArg<Long>()
            
            DevLog.info(LOG_TAG, "Mock deleteEvent called for eventId=$eventId")
            
            // Add this event to our deleted set
            deletedEvents.add(eventId)
            
            // For deleted events, return null when getEvent is called
            every { CalendarProvider.getEvent(any(), eq(eventId)) } returns null
            
            true
        }
    }
    
    /**
     * Mocks CalendarProvider.getAlertByTime for direct reminder tests
     * 
     * This method is critical for tests that simulate direct reminder broadcasts
     * It ensures the CalendarMonitor gets the expected test event
     */
    fun mockGetAlertByTime(
        eventId: Long, 
        alertTime: Long, 
        startTime: Long, 
        title: String, 
        description: String = "Test Description",
        isAllDay: Boolean = false,
        isRepeating: Boolean = false
    ) {
        DevLog.info(LOG_TAG, "Setting up mock for getAlertByTime with eventId=$eventId, alertTime=$alertTime, title='$title'")
        
        // Set up a specific mock for this exact alertTime
        every { CalendarProvider.getAlertByTime(any(), eq(alertTime), any(), any()) } answers {
            DevLog.info(LOG_TAG, "Mock getAlertByTime called for alertTime=$alertTime")
            
            // Return a list containing just our test event
            listOf(EventAlertRecord(
                calendarId = 1, // Will be overridden in real tests
                eventId = eventId, 
                isAllDay = isAllDay,
                isRepeating = isRepeating,
                alertTime = alertTime,
                notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
                title = title,
                desc = description,
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
            ))
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
    }
} 
