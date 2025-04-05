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
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitor
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorInterface
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorState
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import io.mockk.*
import org.mockito.Mockito.spy

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
    
    // Track last timer broadcast time for tests
    private var lastTimerBroadcastReceived: Long? = null
    
    /**
     * Sets up the mock calendar provider and related components
     */
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up MockCalendarProvider")
        setupCalendarProvider()
        setupMockCalendarMonitor() // Initialize mockCalendarMonitor early
    }
    
    /**
     * Creates and configures the mock calendar provider
     */
    private fun setupCalendarProvider() {
        DevLog.info(LOG_TAG, "Setting up mock CalendarProvider")
        
        // Mock the CalendarProvider object
        mockkObject(CalendarProvider)
        
        // Setup default responses for key methods
        every { CalendarProvider.getEventReminders(any(), any<Long>()) } answers {
            val eventId = secondArg<Long>()
            DevLog.info(LOG_TAG, "Mock getEventReminders called for eventId=$eventId")
            listOf(EventReminderRecord(millisecondsBefore = 30000))
        }
        
        every { CalendarProvider.isRepeatingEvent(any(), any<Long>()) } returns false
        
        // Mock dismissNativeEventAlert
        every { CalendarProvider.dismissNativeEventAlert(any(), any()) } just Runs
    }
    
    /**
     * Sets up a mock calendar monitor with behaviors from the original tests
     */
    fun setupMockCalendarMonitor() {
        DevLog.info(LOG_TAG, "Setting up mock calendar monitor")
        
        val realMonitor = CalendarMonitor(CalendarProvider, timeProvider.testClock)
        mockCalendarMonitor = spyk(realMonitor, recordPrivateCalls = true)
        
        // Set up mocks with simpler approach to avoid recursion
        every { mockCalendarMonitor.onRescanFromService(any()) } answers {
            val context = firstArg<Context>()
            val monitorState = CalendarMonitorState(context)
            DevLog.info(LOG_TAG, "mock onRescanFromService called, context=${context.hashCode()}, firstScanEver=${monitorState.firstScanEver}")
            
            // Get the shared preferences directly to double check
//            fakeContext.getSharedPreferences(CalendarMonitorState.PREFS_NAME, Context.MODE_PRIVATE).let { prefs ->
//                val firstScanEverPref = prefs.getBoolean("F", false)
//                DevLog.info(LOG_TAG, "In onRescanFromService: SharedPreferences[${CalendarMonitorState.PREFS_NAME}].getBoolean(F) = $firstScanEverPref")
//            }
            
            DevLog.info(LOG_TAG, "Not a first scan ever, proceeding with normal processing")
            
            // Call original to handle other functionality
            callOriginal()
        }
        
        every { mockCalendarMonitor.onAlarmBroadcast(any(), any()) } answers {
            DevLog.info(LOG_TAG, "Mock onAlarmBroadcast called")
            val context = firstArg<Context>()
            val intent = secondArg<Intent>()

            // When an alarm broadcast is received, this should trigger the alert firing process
            // The intent will contain the alert time that needs to be processed
            val alertTime = intent.getLongExtra("alert_time", 0)
            DevLog.info(LOG_TAG, "Processing alarm broadcast for alertTime=$alertTime")

            if (alertTime > 0) {
                // Simulate firing the events at this alert time
                MonitorStorage(context).classCustomUse { db ->
                val alerts = db.getAlertsAt(alertTime).filter { !it.wasHandled }
                if (alerts.isNotEmpty()) {
                    DevLog.info(LOG_TAG, "Found ${alerts.size} alerts to process for alertTime=$alertTime")

                    // This will trigger ApplicationController.registerNewEvents which will post notifications
                    val events = alerts.mapNotNull { alert ->
                    CalendarProvider.getAlertByEventIdAndTime(context, alert.eventId, alert.alertTime)
                    }

                    if (events.isNotEmpty()) {
                    // This call should trigger notification posting which will mark alerts as handled
                    ApplicationController.postEventNotifications(context, events)
                    }
                }
                }
            }

            callOriginal()
        }
        
        every { mockCalendarMonitor.onProviderReminderBroadcast(any(), any()) } answers {
            val context = firstArg<Context>()
            val intent = secondArg<Intent>()
            
            DevLog.info(LOG_TAG, "Mock onProviderReminderBroadcast called with intent: ${intent.action}")
            
            val uri = intent.data
            val alertTime = uri?.lastPathSegment?.toLongOrNull()
            
            if (alertTime == null) {
                DevLog.error(LOG_TAG, "ERROR alertTime is null!")
                return@answers
            }
            
            // Store the timestamp of this reminder
            lastTimerBroadcastReceived = timeProvider.testClock.currentTimeMillis()
        }
            
        every { mockCalendarMonitor.launchRescanService(any(), any(), any(), any(), any()) } answers {
        val delayed = invocation.args[1] as Int
        val reloadCalendar = invocation.args[2] as Boolean
        val rescanMonitor = invocation.args[3] as Boolean
        val startDelay = invocation.args[4] as Long

        DevLog.info(LOG_TAG, "Mock launchRescanService called with delayed=$delayed, reloadCalendar=$reloadCalendar, rescanMonitor=$rescanMonitor, startDelay=$startDelay")
        callOriginal()
        }

        every { ApplicationController.CalendarMonitor } returns mockCalendarMonitor
    }
    
    /**
     * Creates a test calendar with the specified properties
     */
    fun createTestCalendar(
        context: Context,
        displayName: String,
        accountName: String,
        ownerAccount: String
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
            
            // Enable calendar monitoring in settings
            val settings = Settings(context)
            settings.setBoolean("calendar_is_handled_$calendarId", true)
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
        duration: Long = 3600000
    ) {
        DevLog.info(LOG_TAG, "Mocking event details for eventId=$eventId, title=$title, startTime=$startTime")
        
        every { CalendarProvider.getEvent(any(), eq(eventId)) } answers {
            val event = EventRecord(
                calendarId = 1, // This will be replaced by the actual calendar ID in real tests
                eventId = eventId,
                details = CalendarEventDetails(
                    title = title,
                    desc = "Test Description",
                    location = "",
                    timezone = "UTC",
                    startTime = startTime,
                    endTime = startTime + duration,
                    isAllDay = false,
                    reminders = listOf(EventReminderRecord(millisecondsBefore = 30000)),
                    repeatingRule = "",
                    repeatingRDate = "",
                    repeatingExRule = "",
                    repeatingExRDate = "",
                    color = Consts.DEFAULT_CALENDAR_EVENT_COLOR
                ),
                eventStatus = EventStatus.Confirmed,
                attendanceStatus = AttendanceStatus.None
            )
            DevLog.info(LOG_TAG, "Mock getEvent called for eventId=$eventId, returning event with title=${event.details.title}, startTime=${event.details.startTime}")
            event
        }
    }
    
    /**
     * Mocks event reminder behavior
     */
    fun mockEventReminders(
        eventId: Long,
        millisecondsBefore: Long = 30000
    ) {
        DevLog.info(LOG_TAG, "Mocking event reminders for eventId=$eventId, offset=$millisecondsBefore")
        
        every { CalendarProvider.getEventReminders(any(), eq(eventId)) } answers {
            val reminders = listOf(EventReminderRecord(millisecondsBefore = millisecondsBefore))
            DevLog.info(LOG_TAG, "Mock getEventReminders called for eventId=$eventId, returning ${reminders.size} reminders with offset $millisecondsBefore")
            reminders
        }
    }
    
    /**
     * Mocks event alerts for a specific time range
     */
    fun mockEventAlerts(
        eventId: Long,
        startTime: Long,
        alertOffset: Long = 30000
    ) {
        DevLog.info(LOG_TAG, "Mocking event alerts for eventId=$eventId, startTime=$startTime, alertOffset=$alertOffset")
        
        // Set alert time to be alertOffset before startTime
        val alertTime = startTime - alertOffset
        
        every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
            val scanFrom = secondArg<Long>()
            val scanTo = thirdArg<Long>()
            
            DevLog.info(LOG_TAG, "Mock getEventAlertsForInstancesInRange called with scanFrom=$scanFrom, scanTo=$scanTo")
            
            if (alertTime in scanFrom..scanTo) {
                listOf(MonitorEventAlertEntry(
                    eventId = eventId,
                    isAllDay = false,
                    alertTime = alertTime,
                    instanceStartTime = startTime,
                    instanceEndTime = startTime + 3600000, // 1 hour duration
                    alertCreatedByUs = false,
                    wasHandled = false
                ))
            } else {
                emptyList()
            }
        }
        
        every { CalendarProvider.getAlertByTime(any(), eq(alertTime), any(), any()) } answers {
            val skipDismissed = thirdArg<Boolean>()
            
            DevLog.info(LOG_TAG, "Mock getAlertByTime called with alertTime=$alertTime, skipDismissed=$skipDismissed")
            
            listOf(EventAlertRecord(
                calendarId = 1, // Will be overridden in actual tests
                eventId = eventId,
                isAllDay = false,
                isRepeating = false,
                alertTime = alertTime,
                notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
                title = "title", // Use the provided title parameter
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
            ))
        }
        
        every { CalendarProvider.getAlertByEventIdAndTime(any(), eq(eventId), eq(alertTime)) } answers {
            DevLog.info(LOG_TAG, "Mock getAlertByEventIdAndTime called with eventId=$eventId, alertTime=$alertTime")
            
            EventAlertRecord(
                calendarId = 1, // Will be overridden in actual tests
                eventId = eventId,
                isAllDay = false,
                isRepeating = false,
                alertTime = alertTime,
                notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
                title = "title", // Use the provided title parameter
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
    }
    
    /**
     * Clears all storage databases
     */
    fun clearStorages(context: Context) {
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
        // Instead of calling ApplicationController.onCalendarChanged which calls mockCalendarMonitor,
        // directly create and start the service intent to avoid recursion
        val intent = Intent().apply {
            putExtra("reload_calendar", true)
            putExtra("rescan_monitor", true)
            putExtra("start_delay", 0L)
        }
        
        context.startService(intent)
        DevLog.info(LOG_TAG, "Simulated calendar change notification")
    }
    
    /**
     * Cleans up resources
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up MockCalendarProvider")
        // Nothing to clean up specifically, mocks will be cleaned up by unmockkAll()
    }
} 
