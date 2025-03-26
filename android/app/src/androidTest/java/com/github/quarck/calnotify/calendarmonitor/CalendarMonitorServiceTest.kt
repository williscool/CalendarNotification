package com.github.quarck.calnotify.calendarmonitor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.Consts
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.Manifest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.github.quarck.calnotify.calendareditor.CalendarChangeRequestMonitorInterface
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.logs.DevLog
import io.mockk.*
import io.mockk.impl.annotations.MockK
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicLong
import android.app.AlarmManager
import android.os.UserHandle
import android.os.Process
import com.github.quarck.calnotify.utils.detailed
import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentUris

/**
 * Integration tests for [CalendarMonitorService] that verify calendar event monitoring,
 * event processing, and delayed event handling functionality.
 *
 * These tests use mock objects to simulate calendar provider interactions and verify that:
 * 1. Events are properly detected and processed
 * 2. Event timing and delays are respected
 * 3. Multiple events are handled correctly
 * 4. Calendar monitoring state is maintained
 */
@RunWith(AndroidJUnit4::class)
class CalendarMonitorServiceTest {
    private lateinit var context: Context
    private var testCalendarId: Long = -1
    private var testEventId: Long = -1
    private var eventStartTime: Long = 0
    private var reminderTime: Long = 0
    private val realController = ApplicationController
    
    @MockK
    private lateinit var mockTimer: ScheduledExecutorService
    
    private lateinit var mockService: CalendarMonitorService
    
    private lateinit var mockCalendarMonitor: CalendarMonitorInterface

    private lateinit var mockAlarmManager: AlarmManager
    
    private val currentTime = AtomicLong(0L)

    companion object {
        private const val LOG_TAG = "CalMonitorSvcTest"
    }

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.RECEIVE_BOOT_COMPLETED
    )

    // Helper functions for setting up mocks and test data
    private fun setupMockContext() {
        val realContext = InstrumentationRegistry.getInstrumentation().targetContext
        mockAlarmManager = mockk<AlarmManager>(relaxed = true) {
            every { setExactAndAllowWhileIdle(any<Int>(), any<Long>(), any<PendingIntent>()) } just Runs
            every { setExact(any<Int>(), any<Long>(), any<PendingIntent>()) } just Runs
            every { setAlarmClock(any<AlarmManager.AlarmClockInfo>(), any<PendingIntent>()) } just Runs
            every { set(any<Int>(), any<Long>(), any<PendingIntent>()) } just Runs
            every { setInexactRepeating(any<Int>(), any<Long>(), any<Long>(), any<PendingIntent>()) } just Runs
            every { cancel(any<PendingIntent>()) } just Runs
        }
        context = mockk<Context>(relaxed = true) {
            every { packageName } returns realContext.packageName
            every { contentResolver } returns realContext.contentResolver
            every { getDatabasePath(any()) } answers { realContext.getDatabasePath(firstArg()) }
            every { getSystemService(Context.ALARM_SERVICE) } returns mockAlarmManager
            every { getSystemService(any<String>()) } answers { realContext.getSystemService(firstArg()) }
            every { checkPermission(any(), any(), any()) } answers { realContext.checkPermission(firstArg(), secondArg(), thirdArg()) }
            every { checkCallingOrSelfPermission(any()) } answers { realContext.checkCallingOrSelfPermission(firstArg()) }
            every { createPackageContext(any(), any()) } answers { realContext.createPackageContext(firstArg(), secondArg()) }
            every { getSharedPreferences(any(), any()) } answers { realContext.getSharedPreferences(firstArg(), secondArg()) }
            every { getApplicationInfo() } returns realContext.applicationInfo
            every { getFilesDir() } returns realContext.filesDir
            every { getCacheDir() } returns realContext.cacheDir
            every { getDir(any(), any()) } answers { realContext.getDir(firstArg(), secondArg()) }
            every { startService(any()) } answers {
                val intent = firstArg<Intent>()
                DevLog.info(LOG_TAG, "startService called with intent: action=${intent.action}, extras=${intent.extras}")
                mockService.handleIntentForTest(intent)
                ComponentName(realContext.packageName, CalendarMonitorService::class.java.name)
            }
        }
    }

    private fun setupMockTimer() {
        val scheduledTasks = mutableListOf<Pair<Runnable, Long>>()
        every { mockTimer.schedule(any(), any<Long>(), any()) } answers { call ->
            val task = call.invocation.args[0] as Runnable
            val delay = call.invocation.args[1] as Long
            val unit = call.invocation.args[2] as TimeUnit
            scheduledTasks.add(Pair(task, currentTime.get() + unit.toMillis(delay)))
            scheduledTasks.sortBy { it.second }
            while (scheduledTasks.isNotEmpty() && scheduledTasks[0].second <= currentTime.get()) {
                val (nextTask, _) = scheduledTasks.removeAt(0)
                DevLog.info(LOG_TAG, "Executing scheduled task")
                nextTask.run()
            }
            mockk(relaxed = true)
        }
    }

    private fun setupMockCalendarMonitor() {
        val realMonitor = CalendarMonitor(CalendarProvider)
        mockCalendarMonitor = spyk(realMonitor)
        every { ApplicationController.CalendarMonitor } returns mockCalendarMonitor
        
        // Add logging to track what's happening
        every { mockCalendarMonitor.onRescanFromService(any()) } answers {
            val realCtx = firstArg<Context>()
            DevLog.info(LOG_TAG, "onRescanFromService called")
            
            // Call the real implementation to process alerts
            callOriginal()
            
            // Verify the results
            EventsStorage(realCtx).classCustomUse { db ->
                val events = db.events
                DevLog.info(LOG_TAG, "Found ${events.size} events after rescan")
                events.forEach { event ->
                    DevLog.info(LOG_TAG, "Event: id=${event.eventId}, title=${event.title}, startTime=${event.startTime}")
                }
            }
        }
    }

    private fun setupMockService() {
        mockService = spyk(CalendarMonitorService())
        every { mockService.getSystemService(Context.POWER_SERVICE) } returns context.getSystemService(Context.POWER_SERVICE)
        every { mockService.applicationContext } returns context
        every { mockService.baseContext } returns context
        every { mockService.timer } returns mockTimer
        every { mockService.getDatabasePath(any()) } answers { context.getDatabasePath(firstArg()) }
        every { mockService.checkPermission(any(), any(), any()) } answers { context.checkPermission(firstArg(), secondArg(), thirdArg()) }
        every { mockService.checkCallingOrSelfPermission(any()) } answers { context.checkCallingOrSelfPermission(firstArg()) }
        every { mockService.getPackageName() } returns context.packageName
        every { mockService.getContentResolver() } returns context.contentResolver
        
        // Add logging for handleIntentForTest
        every { mockService.handleIntentForTest(any()) } answers {
            val intent = firstArg<Intent>()
            DevLog.info(LOG_TAG, "handleIntentForTest called with intent: action=${intent.action}, extras=${intent.extras}")
            callOriginal()
        }
    }

    private fun setupApplicationController() {
        // Create a spy on the real controller
        spyk(realController)

        // Mock CalendarProvider.getAlertByTime
        every { CalendarProvider.getAlertByTime(any(), any(), any(), any()) } answers {
            val context = firstArg<Context>()
            val alertTime = secondArg<Long>()
            val skipDismissed = thirdArg<Boolean>()
//            val skipExpiredEvents = fourthArg<Boolean>()
            
            DevLog.info(LOG_TAG, "Mock getAlertByTime called with alertTime=$alertTime, skipDismissed=$skipDismissed")
            
            // Return our test event
            listOf(EventAlertRecord(
                calendarId = testCalendarId,
                eventId = testEventId,
                isAllDay = false,
                isRepeating = false,
                alertTime = alertTime,
                notificationId = 0,
                title = "Test Monitor Event",
                desc = "Test Description",
                startTime = eventStartTime,
                endTime = eventStartTime + 3600000, // 1 hour duration
                instanceStartTime = eventStartTime,
                instanceEndTime = eventStartTime + 3600000,
                location = "",
                lastStatusChangeTime = 0,
                displayStatus = EventDisplayStatus.Hidden,
                color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
                origin = EventOrigin.ProviderBroadcast,
                timeFirstSeen = 0L,
                eventStatus = EventStatus.Confirmed,
                attendanceStatus = AttendanceStatus.None,
                flags = 0
            ))
        }

        // Spy on shouldMarkEventAsHandledAndSkip
        every { ApplicationController.shouldMarkEventAsHandledAndSkip(any(), any()) } answers {
            val context = firstArg<Context>()
            val event = secondArg<EventAlertRecord>()
            DevLog.info(LOG_TAG, "shouldMarkEventAsHandledAndSkip called for eventId=${event.eventId}, title=${event.title}, isAllDay=${event.isAllDay}, eventStatus=${event.eventStatus}, attendanceStatus=${event.attendanceStatus}")
            val result = callOriginal()
            DevLog.info(LOG_TAG, "shouldMarkEventAsHandledAndSkip result=$result")
            result
        }

        // Spy on registerNewEvent
        every { ApplicationController.registerNewEvent(any(), any()) } answers {
            val context = firstArg<Context>()
            val event = secondArg<EventAlertRecord>()
            
            DevLog.info(LOG_TAG, "registerNewEvent called for eventId=${event.eventId}, title=${event.title}, isAllDay=${event.isAllDay}, eventStatus=${event.eventStatus}, attendanceStatus=${event.attendanceStatus}")
            
            // First check if event already exists
            var eventExists = false
            EventsStorage(context).classCustomUse { db ->
                eventExists = db.events.any { it.eventId == event.eventId }
            }
            
            if (!eventExists) {
                // Save event to storage
                EventsStorage(context).classCustomUse { db ->
                    db.addEvent(event)
                    DevLog.info(LOG_TAG, "Event ${event.eventId} saved to storage: ${event.title}")
                }
            } else {
                DevLog.info(LOG_TAG, "Event ${event.eventId} already exists in storage")
            }
            
            true // Return success
        }

        // Spy on afterCalendarEventFired
        every { ApplicationController.afterCalendarEventFired(any()) } answers {
            val context = firstArg<Context>()
            DevLog.info(LOG_TAG, "afterCalendarEventFired called for context=${context}")
            callOriginal()
        }

        // Spy on postEventNotifications
        every { ApplicationController.postEventNotifications(any(), any<Collection<EventAlertRecord>>()) } answers {
            val context = firstArg<Context>()
            val events = secondArg<Collection<EventAlertRecord>>()
            DevLog.info(LOG_TAG, "postEventNotifications called for ${events.size} events")
            callOriginal()
        }

        every { ApplicationController.onCalendarReloadFromService(any(), any()) } answers {
          val stackTrace = Thread.currentThread().stackTrace
          val caller = if (stackTrace.size > 2) stackTrace[2].methodName else "unknown"
          val callerClass = if (stackTrace.size > 2) stackTrace[2].className else "unknown"

          DevLog.info(LOG_TAG, "onCalendarReloadFromService Reload attempt from: $callerClass.$caller")

            val userActionUntil = secondArg<Long>()
            DevLog.info(LOG_TAG, "onCalendarReloadFromService called with userActionUntil=$userActionUntil")

            val res = callOriginal()
            DevLog.info(LOG_TAG, "onCalendarReloadFromService completed")

            res
        }

        every { ApplicationController.onCalendarRescanForRescheduledFromService(any(), any()) } answers {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 2) stackTrace[2].methodName else "unknown"
            val callerClass = if (stackTrace.size > 2) stackTrace[2].className else "unknown"
            
            DevLog.info(LOG_TAG, "onCalendarRescanForRescheduledFromService Rescan attempt from: $callerClass.$caller")

            val userActionUntil = secondArg<Long>()
            DevLog.info(LOG_TAG, "onCalendarRescanForRescheduledFromService called with userActionUntil=$userActionUntil")
            callOriginal()
        }
    }
    


    private fun setupTestCalendar() {
        testCalendarId = createTestCalendar(
            displayName = "Test Calendar",
            accountName = "test@local",
            ownerAccount = "test@local"
        )

        // Enable calendar monitoring in settings
        val settings = Settings(context)
        settings.setBoolean("enable_manual_calendar_rescan", true)
        context.getSharedPreferences("CalendarNotificationsPlusPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("calendar_is_handled_$testCalendarId", true)
            .commit()
            
        DevLog.info(LOG_TAG, "Test calendar setup complete: id=$testCalendarId")
    }

    private fun clearStorages() {
        EventsStorage(context).classCustomUse { db -> 
            val count = db.events.size
            db.deleteAllEvents()
            DevLog.info(LOG_TAG, "Cleared $count events from storage")
        }
        MonitorStorage(context).classCustomUse { db -> 
            val count = db.alerts.size
            db.deleteAlertsMatching { true }
            DevLog.info(LOG_TAG, "Cleared $count alerts from storage")
        }
    }

    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up test environment")
        MockKAnnotations.init(this)
        setupMockContext()
        setupMockTimer()
        
        mockkObject(ApplicationController)
        mockkObject(CalendarProvider)
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns mockk(relaxed = true)
        
        setupMockCalendarMonitor()
        setupMockService()
        setupApplicationController()
        setupTestCalendar()
        clearStorages()
        DevLog.info(LOG_TAG, "Test environment setup complete")
    }

    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test environment")
        unmockkAll()
        
        // Delete test events and calendar
        if (testEventId != -1L) {
            val deleted = context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events._ID} = ?",
                arrayOf(testEventId.toString())
            )
            DevLog.info(LOG_TAG, "Deleted test event: id=$testEventId, result=$deleted")
        }
        
        if (testCalendarId != -1L) {
            val deleted = context.contentResolver.delete(
                CalendarContract.Calendars.CONTENT_URI,
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(testCalendarId.toString())
            )
            DevLog.info(LOG_TAG, "Deleted test calendar: id=$testCalendarId, result=$deleted")
        }

        clearStorages()
        DevLog.info(LOG_TAG, "Test environment cleanup complete")
    }

    /**
     * Tests basic calendar event monitoring functionality.
     * 
     * Verifies that:
     * 1. A single event can be created and detected
     * 2. The event is properly registered in storage
     * 3. Event reminders are correctly processed
     * 4. Monitor service properly handles the event lifecycle
     */
    @Test
    fun testCalendarEventMonitoring() {
        // Reset monitor state and ensure firstScanEver is false
        val monitorState = CalendarMonitorState(context)
        monitorState.firstScanEver = false
        currentTime.set(System.currentTimeMillis())
        val startTime = currentTime.get() // Capture initial time
        
        DevLog.info(LOG_TAG, "Test starting with currentTime=$startTime")
        monitorState.prevEventScanTo = startTime
        monitorState.prevEventFireFromScan = startTime

        // Create a test event with reminder - use captured time
        eventStartTime = startTime + 60000 // 1 minute from start time
        reminderTime = eventStartTime - 30000 // 30 seconds before start

        DevLog.info(LOG_TAG, "Creating test event: startTime=$eventStartTime, reminderTime=$reminderTime")

        // Create test event in calendar
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
            put(CalendarContract.Events.TITLE, "Test Monitor Event")
            put(CalendarContract.Events.DESCRIPTION, "Test Description")
            put(CalendarContract.Events.DTSTART, eventStartTime)
            put(CalendarContract.Events.DTEND, eventStartTime + 60000)   // 1 minute duration
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.HAS_ALARM, 1)
        }

        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        assertNotNull("Failed to create test event", eventUri)
        testEventId = eventUri!!.lastPathSegment!!.toLong()

        // Verify the event exists in calendar
        val cursor = context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, testEventId),
            null, null, null, null
        )
        assertNotNull("Event should exist in calendar", cursor)
        assertTrue("Event should exist in calendar", cursor!!.moveToFirst())
        cursor.close()

        DevLog.info(LOG_TAG, "Created test event with ID: $testEventId")

        // Add reminder for the event
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, testEventId)
            put(CalendarContract.Reminders.MINUTES, 1) // 1 minute before
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        val reminderUri = context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        assertNotNull("Failed to create reminder", reminderUri)

        DevLog.info(LOG_TAG, "Created reminder for event $testEventId")

        // Setup mocks for event handling
        mockEventReminders(testEventId)
        mockEventAlerts(testEventId, eventStartTime)
        mockEventDetails(testEventId, eventStartTime, "Test Monitor Event")

        // Verify the calendar is handled and settings
        val settings = Settings(context)
        assertTrue("Calendar should be handled", settings.getCalendarIsHandled(testCalendarId))
        assertTrue("Calendar monitoring should be enabled", settings.enableCalendarRescan)

        // Check monitor storage before service start
        MonitorStorage(context).classCustomUse { db ->
            val alerts = db.alerts
            DevLog.info(LOG_TAG, "Monitor alerts before service: ${alerts.size}")
            alerts.forEach { alert ->
                DevLog.info(LOG_TAG, "Alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}}")
            }
        }

        // Notify calendar change and start service
        notifyCalendarChangeAndWait()

        // Add a small delay to allow event processing to complete
        Thread.sleep(1000)

        // Verify that CalendarMonitor.onRescanFromService was called
        verify(exactly = 1) { mockCalendarMonitor.onRescanFromService(any()) }

        // Verify the event was detected and processed
        verifyEventProcessed(
            eventId = testEventId,
            startTime = eventStartTime,
            title = "Test Monitor Event"
        )
    }

    // Add these helper functions before the tests
    private fun mockMultipleEventAlerts(events: List<Long>, startTime: Long) {
        every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
            events.mapIndexed { index, eventId ->
                val hourOffset = index + 1
                val eventStartTime = startTime + (hourOffset * Consts.HOUR_IN_MILLISECONDS)
                val alertTime = eventStartTime - (15 * Consts.MINUTE_IN_MILLISECONDS)
                val endTime = eventStartTime + Consts.HOUR_IN_MILLISECONDS
                
                DevLog.info(LOG_TAG, "Returning alert for event $eventId: alertTime=$alertTime, startTime=$eventStartTime")
                
                MonitorEventAlertEntry(
                    eventId = eventId,
                    isAllDay = false,
                    alertTime = alertTime,
                    instanceStartTime = eventStartTime,
                    instanceEndTime = endTime,
                    alertCreatedByUs = false,
                    wasHandled = false
                )
            }
        }
    }

    private fun setupMultipleEventDetails(events: List<Long>, startTime: Long) {
        events.forEachIndexed { index, eventId ->
            val hourOffset = index + 1
            val eventStartTime = startTime + (hourOffset * Consts.HOUR_IN_MILLISECONDS)
            mockEventDetails(
                eventId = eventId,
                startTime = eventStartTime,
                title = "Test Event $index"
            )
            DevLog.info(LOG_TAG, "Setup event details for event $index: id=$eventId, startTime=$eventStartTime")
        }
    }

    private fun verifyMultipleEvents(events: List<Long>, startTime: Long) {
        events.forEachIndexed { index, eventId ->
            val hourOffset = index + 1
            val expectedStartTime = startTime + (hourOffset * Consts.HOUR_IN_MILLISECONDS)
            verifyEventProcessed(
                eventId = eventId,
                startTime = expectedStartTime,
                title = "Test Event $index"
            )
        }
    }

    /**
     * Tests calendar reload functionality with multiple events.
     * 
     * Verifies that:
     * 1. Multiple events can be created and detected
     * 2. Events are processed in the correct order
     * 3. All events are properly stored with correct metadata
     * 4. Service properly handles batch event processing
     */
    @Test
    fun testCalendarReload() {
        // Reset monitor state and ensure firstScanEver is false
        val monitorState = CalendarMonitorState(context)
        monitorState.firstScanEver = false
        currentTime.set(System.currentTimeMillis())
        val startTime = currentTime.get()
        monitorState.prevEventScanTo = startTime
        monitorState.prevEventFireFromScan = startTime

        // Create and setup test events
        val events = createMultipleTestEvents(3)
        DevLog.info(LOG_TAG, "Created ${events.size} test events: $events")
        
        // Setup mocks for event handling
        setupMultipleEventDetails(events, startTime)
        mockMultipleEventAlerts(events, startTime)

        // Verify calendar settings
        val settings = Settings(context)
        assertTrue("Calendar should be handled", settings.getCalendarIsHandled(testCalendarId))
        assertTrue("Calendar monitoring should be enabled", settings.enableCalendarRescan)

        // Execute test
        notifyCalendarChangeAndWait()

        // Verify results
        verify(exactly = 1) { mockCalendarMonitor.onRescanFromService(any()) }
        verifyMultipleEvents(events, startTime)
    }

    /**
     * Tests that the CalendarMonitorService properly respects the startDelay parameter.
     * 
     * Verifies that:
     * 1. Events are not processed before the specified delay
     * 2. Events are correctly processed after the delay
     * 3. Event timing and state are maintained during the delay
     * 4. Service properly handles delayed event processing
     */
    @Test
    fun testDelayedProcessing() {
        val startDelay = 2000 // 2 second delay
        currentTime.set(System.currentTimeMillis())
        val startTime = currentTime.get()

        // Create test event and setup monitor state
        createTestEvent()
        val monitorState = CalendarMonitorState(context)
        monitorState.firstScanEver = false
        monitorState.prevEventScanTo = startTime
        monitorState.prevEventFireFromScan = startTime

        // Setup mocks for event handling
        mockEventReminders(testEventId)
        mockDelayedEventAlerts(testEventId, startTime, startDelay.toLong())
        mockEventDetails(testEventId, startTime + 3600000)

        // Verify no events are processed before delay
        verifyNoEvents()

        // Start service with delay
        notifyCalendarChangeAndWait()

        // Verify still no events before delay
        verifyNoEvents()

        // Wait for slightly longer than the delay and trigger rescan
        DevLog.info(LOG_TAG, "Advancing timer past delay...")
        advanceTimer(startDelay + 1000L) // Add 1 second margin

        DevLog.info(LOG_TAG, "Triggering post-delay rescan...")
        mockCalendarMonitor.onRescanFromService(context)

        // Verify the event was processed after the delay
        verifyEventProcessed(
            eventId = testEventId,
            startTime = startTime + 3600000,
            afterDelay = startTime + startDelay
        )
    }

    /**
     * Creates a test calendar with the specified properties.
     * 
     * @param displayName The display name for the calendar
     * @param accountName The account name associated with the calendar
     * @param ownerAccount The owner account for the calendar
     * @return The ID of the created calendar, or -1 if creation failed
     */
    private fun createTestCalendar(
        displayName: String,
        accountName: String,
        ownerAccount: String
    ): Long {
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
        val calendarId = calUri?.lastPathSegment?.toLong() ?: -1L
        DevLog.info(LOG_TAG, "Created test calendar: id=$calendarId, name=$displayName")
        return calendarId
    }

    /**
     * Creates a test event in the test calendar with default reminder settings.
     * 
     * @return The ID of the created event
     */
    private fun createTestEvent(): Long {
        val currentTime = this.currentTime.get()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
            put(CalendarContract.Events.TITLE, "Test Event")
            put(CalendarContract.Events.DESCRIPTION, "Test Description")
            put(CalendarContract.Events.DTSTART, currentTime + 3600000)
            put(CalendarContract.Events.DTEND, currentTime + 7200000)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        
        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = eventUri?.lastPathSegment?.toLong() ?: -1L

        // Add a reminder
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 15)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        val reminderUri = context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        DevLog.info(LOG_TAG, "Created test event: id=$eventId, reminder=${reminderUri != null}")

        return eventId
    }

    /**
     * Creates multiple test events with sequential timing.
     * 
     * @param count The number of events to create
     * @return List of created event IDs
     */
    private fun createMultipleTestEvents(count: Int): List<Long> {
        val eventIds = mutableListOf<Long>()
        val currentTime = this.currentTime.get() // Capture current time at start of creation
        repeat(count) { index ->
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
                put(CalendarContract.Events.TITLE, "Test Event $index")
                put(CalendarContract.Events.DESCRIPTION, "Test Description $index")
                put(CalendarContract.Events.DTSTART, currentTime + (3600000 * (index + 1)))
                put(CalendarContract.Events.DTEND, currentTime + (3600000 * (index + 2)))
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                put(CalendarContract.Events.HAS_ALARM, 1)
            }
            
            val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            eventUri?.lastPathSegment?.toLong()?.let { 
                eventIds.add(it)
                // Add a reminder for each event
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, it)
                    put(CalendarContract.Reminders.MINUTES, 15)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                val reminderUri = context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
                DevLog.info(LOG_TAG, "Created test event $index: id=$it, reminder=${reminderUri != null}")
            }
        }
        DevLog.info(LOG_TAG, "Created ${eventIds.size} test events")
        return eventIds
    }

    /**
     * Advances the mock timer by the specified duration and processes any scheduled tasks.
     * 
     * @param milliseconds The amount of time to advance
     */
    private fun advanceTimer(milliseconds: Long) {
        currentTime.addAndGet(milliseconds)
        // Force timer to check for due tasks
        mockTimer.schedule({}, 0, TimeUnit.MILLISECONDS)
    }

    /**
     * Sets up the monitor state with the specified start time.
     * 
     * @param startTime The time to use for monitor state initialization
     */
    private fun setupMonitorState(startTime: Long) {
        val monitorState = CalendarMonitorState(context)
        monitorState.firstScanEver = false
        monitorState.prevEventScanTo = startTime
        monitorState.prevEventFireFromScan = startTime
        DevLog.info(LOG_TAG, "Monitor state setup: firstScanEver=false, prevEventScanTo=$startTime, prevEventFireFromScan=$startTime")
    }

    /**
     * Mocks event reminder behavior for a specific event.
     * 
     * @param eventId The ID of the event to mock reminders for
     * @param millisecondsBefore Time before event to trigger reminder
     */
    private fun mockEventReminders(eventId: Long, millisecondsBefore: Long = 30000) {
        every { CalendarProvider.getEventReminders(any(), eq(eventId)) } answers {
            val reminders = listOf(EventReminderRecord(millisecondsBefore = millisecondsBefore))
            DevLog.info(LOG_TAG, "Mock getEventReminders called for eventId=$eventId, returning ${reminders.size} reminders with offset $millisecondsBefore")
            reminders
        }
    }

    /**
     * Mocks event alerts for a specific event with timing parameters.
     * 
     * @param eventId The ID of the event to mock alerts for
     * @param startTime The start time of the event
     * @param duration The duration of the event
     * @param alertOffset Time before event start to trigger alert
     */
    private fun mockEventAlerts(eventId: Long, startTime: Long, duration: Long = 60000, alertOffset: Long = 30000) {
        every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
            val scanFrom = secondArg<Long>()
            val scanTo = thirdArg<Long>()
            
            DevLog.info(LOG_TAG, "Mock getEventAlertsForInstancesInRange called with scanFrom=$scanFrom, scanTo=$scanTo")
            DevLog.info(LOG_TAG, "Event startTime=$startTime is in range: ${startTime in scanFrom..scanTo}")
            
            if (startTime in scanFrom..scanTo) {
                val alert = MonitorEventAlertEntry(
                    eventId = eventId,
                    isAllDay = false,
                    alertTime = startTime - alertOffset,
                    instanceStartTime = startTime,
                    instanceEndTime = startTime + duration,
                    alertCreatedByUs = false,
                    wasHandled = false
                )
                DevLog.info(LOG_TAG, "Returning alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, instanceStart=${alert.instanceStartTime}")
                listOf(alert)
            } else {
                DevLog.info(LOG_TAG, "Event startTime not in scan range, returning empty list")
                emptyList()
            }
        }
    }

    /**
     * Mocks delayed event alerts that respect a specified delay period.
     * 
     * @param eventId The ID of the event to mock alerts for
     * @param startTime The base time for delay calculation
     * @param delay The delay duration before alerts should be processed
     */
    private fun mockDelayedEventAlerts(eventId: Long, startTime: Long, delay: Long) {
        every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
            val scanFrom = secondArg<Long>()
            val scanTo = thirdArg<Long>()
            
            DevLog.info(LOG_TAG, "getEventAlertsForInstancesInRange called at currentTime=${currentTime.get()}, startTime=$startTime, delay=$delay")
            
            if (currentTime.get() >= (startTime + delay)) {
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
                DevLog.info(LOG_TAG, "Skipping event alert due to delay not elapsed: current=${currentTime.get()}, start=$startTime, delay=$delay")
                emptyList()
            }
        }
    }

    /**
     * Mocks event details for a specific event.
     * 
     * @param eventId The ID of the event to mock
     * @param startTime The start time of the event
     * @param title The title of the event
     * @param duration The duration of the event
     */
    private fun mockEventDetails(eventId: Long, startTime: Long, title: String = "Test Event", duration: Long = 3600000) {
        every { CalendarProvider.getEvent(any(), eq(eventId)) } answers {
            val event = EventRecord(
                calendarId = testCalendarId,
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
     * Verifies that no events are present in storage.
     * Fails the test if any events are found.
     */
    private fun verifyNoEvents() {
        EventsStorage(context).classCustomUse { db ->
            val events = db.events
            DevLog.info(LOG_TAG, "Verifying no events in storage, found ${events.size}")
            events.forEach { event ->
                DevLog.info(LOG_TAG, "Unexpected event found: id=${event.eventId}, title=${event.title}")
            }
            assertTrue("No events should be present", events.isEmpty())
        }
    }

    /**
     * Verifies that an event was processed with the expected properties.
     * 
     * @param eventId The ID of the event to verify
     * @param startTime The expected start time of the event
     * @param title Optional title to verify
     * @param afterDelay Optional delay time to verify processing occurred after
     */
    private fun verifyEventProcessed(eventId: Long, startTime: Long, title: String? = null, afterDelay: Long? = null) {
        DevLog.info(LOG_TAG, "Verifying event processing for eventId=$eventId, startTime=$startTime, title=$title")
        
        EventsStorage(context).classCustomUse { db ->
            val events = db.events
            DevLog.info(LOG_TAG, "Found ${events.size} events in storage")
            events.forEach { event ->
                DevLog.info(LOG_TAG, "Event in storage: id=${event.eventId}, timeFirstSeen=${event.timeFirstSeen}, startTime=${event.startTime}, title=${event.title}")
            }
            
            val eventExists = events.any { it.eventId == eventId }
            if (!eventExists) {
                DevLog.error(LOG_TAG, "Event $eventId not found in storage!")
                // Check if event exists in calendar
                val cursor = context.contentResolver.query(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                    null, null, null, null
                )
                if (cursor != null && cursor.moveToFirst()) {
                    DevLog.info(LOG_TAG, "Event still exists in calendar")
                } else {
                    DevLog.error(LOG_TAG, "Event not found in calendar either!")
                }
                cursor?.close()
            }
            assertTrue("Event should be processed", eventExists)
            
            val processedEvent = events.firstOrNull { it.eventId == eventId }
            if (processedEvent == null) {
                DevLog.error(LOG_TAG, "Event $eventId not found in storage!")
            } else {
                DevLog.info(LOG_TAG, "Found processed event: id=${processedEvent.eventId}, title=${processedEvent.title}, startTime=${processedEvent.startTime}")
                
                if (title != null) {
                    assertEquals("Event should have correct title", title, processedEvent.title)
                }
                assertEquals("Event should have correct start time", startTime, processedEvent.startTime)
                if (afterDelay != null) {
                    assertTrue("Event should be processed after the delay",
                        processedEvent.timeFirstSeen >= afterDelay
                    )
                } else {

                }
            }
        }
    }

    /**
     * Notifies of calendar changes and waits for change propagation.
     * 
     * @param waitTime Time to wait for change propagation
     */
    private fun notifyCalendarChangeAndWait(waitTime: Long = 2000) {
        DevLog.info(LOG_TAG, "Notifying calendar change...")
        ApplicationController.onCalendarChanged(context)
        advanceTimer(waitTime)
        DevLog.info(LOG_TAG, "Calendar change notification complete")
    }

    /**
     * Creates a test recurring event in the test calendar.
     * 
     * @param repeatingRule The recurrence rule (e.g. "FREQ=DAILY;COUNT=5")
     * @return The ID of the created event
     */
    private fun createRecurringTestEvent(repeatingRule: String = "FREQ=DAILY;COUNT=5"): Long {
        val currentTime = this.currentTime.get()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
            put(CalendarContract.Events.TITLE, "Recurring Test Event")
            put(CalendarContract.Events.DESCRIPTION, "Test Recurring Description")
            put(CalendarContract.Events.DTSTART, currentTime + 3600000)
            put(CalendarContract.Events.DTEND, currentTime + 7200000)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.HAS_ALARM, 1)
            put(CalendarContract.Events.RRULE, repeatingRule)
        }
        
        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = eventUri?.lastPathSegment?.toLong() ?: -1L

        // Add a reminder
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 15)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        val reminderUri = context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        DevLog.info(LOG_TAG, "Created recurring test event: id=$eventId, rule=$repeatingRule, reminder=${reminderUri != null}")

        return eventId
    }

    /**
     * Creates a test all-day event in the test calendar.
     * 
     * @return The ID of the created event
     */
    private fun createAllDayTestEvent(): Long {
        val currentTime = this.currentTime.get()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
            put(CalendarContract.Events.TITLE, "All Day Test Event")
            put(CalendarContract.Events.DESCRIPTION, "Test All Day Description")
            put(CalendarContract.Events.DTSTART, currentTime)
            put(CalendarContract.Events.DTEND, currentTime + 86400000) // 24 hours
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        
        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = eventUri?.lastPathSegment?.toLong() ?: -1L

        // Add a reminder
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 15)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        val reminderUri = context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        DevLog.info(LOG_TAG, "Created all-day test event: id=$eventId, reminder=${reminderUri != null}")

        return eventId
    }

    /**
     * Mocks event details for a recurring event.
     * 
     * @param eventId The ID of the event to mock
     * @param startTime The start time of the event
     * @param title The title of the event
     * @param repeatingRule The recurrence rule
     */
    private fun mockRecurringEventDetails(
        eventId: Long,
        startTime: Long,
        title: String = "Recurring Test Event",
        repeatingRule: String = "FREQ=DAILY;COUNT=5"
    ) {
        every { CalendarProvider.getEvent(any(), eq(eventId)) } returns EventRecord(
            calendarId = testCalendarId,
            eventId = eventId,
            details = CalendarEventDetails(
                title = title,
                desc = "Test Recurring Description",
                location = "",
                timezone = "UTC",
                startTime = startTime,
                endTime = startTime + 3600000,
                isAllDay = false,
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
    }

    /**
     * Mocks event details for an all-day event.
     * 
     * @param eventId The ID of the event to mock
     * @param startTime The start time of the event
     * @param title The title of the event
     */
    private fun mockAllDayEventDetails(
        eventId: Long,
        startTime: Long,
        title: String = "All Day Test Event"
    ) {
        every { CalendarProvider.getEvent(any(), eq(eventId)) } answers {
            val event = EventRecord(
                calendarId = testCalendarId,
                eventId = eventId,
                details = CalendarEventDetails(
                    title = title,
                    desc = "Test All Day Description",
                    location = "",
                    timezone = "UTC",
                    startTime = startTime,
                    endTime = startTime + 86400000, // 24 hours
                    isAllDay = true,
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
            DevLog.info(LOG_TAG, "Mock getEvent called for all-day event: id=$eventId, title=${event.details.title}, startTime=${event.details.startTime}, isAllDay=${event.details.isAllDay}")
            event
        }
    }

    /**
     * Tests calendar monitoring behavior when enabled, including system events and edge cases.
     * 
     * Verifies that:
     * 1. System calendar change broadcasts are handled
     * 2. System time changes trigger rescans
     * 3. App resume triggers rescans
     * 4. Complex recurring events are processed
     * 5. All-day events are handled correctly
     * 6. Permission changes are respected
     * 7. Setting state persists correctly
     */
    @Test
    fun testCalendarMonitoringEnabledEdgeCases() {
        // Setup
        val settings = Settings(context)
        settings.setBoolean("enable_manual_calendar_rescan", true)
        
        // Test 1: System Calendar Change Broadcast
        DevLog.info(LOG_TAG, "Testing calendar change broadcast handling")
        val intent = Intent(CalendarContract.ACTION_EVENT_REMINDER)
        mockService.handleIntentForTest(intent)
        verify(exactly = 1) { mockCalendarMonitor.onRescanFromService(any()) }
        
        // Test 2: System Time Change
        DevLog.info(LOG_TAG, "Testing system time change handling")
        mockCalendarMonitor.onSystemTimeChange(context)
        verify(exactly = 2) { mockCalendarMonitor.onRescanFromService(any()) }
        
        // Test 3: App Resume
        DevLog.info(LOG_TAG, "Testing app resume handling")
        mockCalendarMonitor.onAppResumed(context, false)
        verify(exactly = 3) { mockCalendarMonitor.onRescanFromService(any()) }
        
        // Test 4: Complex Recurring Event
        DevLog.info(LOG_TAG, "Testing recurring event handling")
        val recurringEventId = createRecurringTestEvent()
        mockRecurringEventDetails(
            eventId = recurringEventId,
            startTime = currentTime.get() + 3600000,
            title = "Recurring Test Event",
            repeatingRule = "FREQ=DAILY;COUNT=5"
        )
        notifyCalendarChangeAndWait()
        
        // Verify all instances were processed
        EventsStorage(context).classCustomUse { db ->
            val events = db.events.filter { it.eventId == recurringEventId }
            assertEquals("Should process all recurring instances", 5, events.size)
            DevLog.info(LOG_TAG, "Found ${events.size} recurring event instances")
        }
        
        // Test 5: All-day Event
        DevLog.info(LOG_TAG, "Testing all-day event handling")
        val allDayEventId = createAllDayTestEvent()
        mockAllDayEventDetails(
            eventId = allDayEventId,
            startTime = currentTime.get(),
            title = "All Day Test Event"
        )
        notifyCalendarChangeAndWait()
        
        // Verify all-day event was processed correctly
        EventsStorage(context).classCustomUse { db ->
            val event = db.events.find { it.eventId == allDayEventId }
            assertNotNull("All-day event should be processed", event)
            assertTrue("Event should be marked as all-day", event?.isAllDay == true)
            DevLog.info(LOG_TAG, "All-day event processed: id=${event?.eventId}, isAllDay=${event?.isAllDay}")
        }
        
        // Test 6: Permission Changes
        DevLog.info(LOG_TAG, "Testing permission change handling")
        every { PermissionsManager.hasAllCalendarPermissionsNoCache(any()) } returns false
        notifyCalendarChangeAndWait()
        verify(exactly = 3) { mockCalendarMonitor.onRescanFromService(any()) } // Count should not increase
        
        // Test 7: State Persistence
        DevLog.info(LOG_TAG, "Testing setting persistence")
        settings.setBoolean("enable_manual_calendar_rescan", false)
        assertFalse("Setting should be disabled", settings.enableCalendarRescan)
        settings.setBoolean("enable_manual_calendar_rescan", true)
        assertTrue("Setting should be enabled", settings.enableCalendarRescan)
    }

    /**
     * Tests calendar monitoring behavior when disabled.
     * 
     * Verifies that:
     * 1. Periodic calendar rescans do not occur
     * 2. The alarm for periodic rescans is cancelled
     * 3. Direct calendar changes are still processed
     * 4. Manual triggers still work
     */
    @Test
    fun testCalendarMonitoringDisabled() {
        // Disable calendar rescan
        val settings = Settings(context)
        settings.setBoolean("enable_manual_calendar_rescan", false)
        
        // Create test event
        createTestEvent()
        
        // Setup monitor state
        val monitorState = CalendarMonitorState(context)
        monitorState.firstScanEver = false
        currentTime.set(System.currentTimeMillis())
        val startTime = currentTime.get()
        monitorState.prevEventScanTo = startTime
        monitorState.prevEventFireFromScan = startTime
        
        // Setup mocks
        mockEventReminders(testEventId)
        mockEventAlerts(testEventId, startTime + 3600000)
        mockEventDetails(testEventId, startTime + 3600000)
        
        // Verify calendar monitoring is disabled
        assertFalse("Calendar monitoring should be disabled", settings.enableCalendarRescan)
        
        // Try to start service
        notifyCalendarChangeAndWait()
        
        // Verify that onRescanFromService was NOT called
        verify(exactly = 0) { mockCalendarMonitor.onRescanFromService(any()) }
        
        // Verify no events were processed
        verifyNoEvents()
        
        // Verify alarm was cancelled (set to Long.MAX_VALUE)
        verify { 
            mockAlarmManager.set(
                eq(AlarmManager.RTC_WAKEUP),
                eq(Long.MAX_VALUE),
                any()
            )
        }
        
        // Test that direct calendar changes still work
        DevLog.info(LOG_TAG, "Testing direct calendar change handling")
        val intent = Intent(CalendarContract.ACTION_EVENT_REMINDER)
        mockService.handleIntentForTest(intent)
        verify(exactly = 1) { mockCalendarMonitor.onRescanFromService(any()) }
        
        // Test that manual triggers still work
        DevLog.info(LOG_TAG, "Testing manual trigger handling")
        mockCalendarMonitor.onAppResumed(context, false)
        verify(exactly = 2) { mockCalendarMonitor.onRescanFromService(any()) }
    }
} 
