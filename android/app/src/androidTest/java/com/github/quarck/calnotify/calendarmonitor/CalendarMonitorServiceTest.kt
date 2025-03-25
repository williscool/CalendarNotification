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
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.Manifest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.github.quarck.calnotify.Consts
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
    
    @MockK
    private lateinit var mockTimer: ScheduledExecutorService
    
    private lateinit var mockService: CalendarMonitorService
    
    private lateinit var mockCalendarMonitor: CalendarMonitorInterface
    
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
        context = mockk<Context>(relaxed = true) {
            every { packageName } returns realContext.packageName
            every { contentResolver } returns realContext.contentResolver
            every { getDatabasePath(any()) } answers { realContext.getDatabasePath(firstArg()) }
            every { getSystemService(Context.ALARM_SERVICE) } returns mockk<AlarmManager>(relaxed = true)
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
                mockService.onHandleIntent(intent)
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
                nextTask.run()
            }
            mockk(relaxed = true)
        }
    }

    private fun setupMockCalendarMonitor() {
        mockCalendarMonitor = mockk<CalendarMonitorInterface>(relaxed = true)
        every { ApplicationController.calendarMonitorInternal } returns mockCalendarMonitor
        every { ApplicationController.CalendarMonitor } returns mockCalendarMonitor
        
        every { mockCalendarMonitor.onRescanFromService(any()) } answers {
            val ctx = firstArg<Context>()
            DevLog.info(LOG_TAG, "onRescanFromService called")
            
            val alerts = CalendarProvider.getEventAlertsForInstancesInRange(
                ctx,
                currentTime.get() - Consts.MAX_SCAN_BACKWARD_DAYS * Consts.DAY_IN_MILLISECONDS,
                currentTime.get() + 30 * Consts.DAY_IN_MILLISECONDS
            )
            
            DevLog.info(LOG_TAG, "Found ${alerts.size} alerts during rescan")
            
            alerts.forEach { alert ->
                val event = CalendarProvider.getEvent(ctx, alert.eventId)
                if (event != null) {
                    val alertRecord = createEventAlertRecord(event, alert)
                    DevLog.info(LOG_TAG, "Registering event: id=${event.eventId}, title=${event.title}")
                    ApplicationController.registerNewEvent(ctx, alertRecord)
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
    }

    private fun setupApplicationController() {
        every { ApplicationController.shouldMarkEventAsHandledAndSkip(any(), any()) } returns false
        every { ApplicationController.registerNewEvent(any(), any()) } answers {
            val context = firstArg<Context>()
            val event = secondArg<EventAlertRecord>()
            
            DevLog.info(LOG_TAG, "registerNewEvent called for eventId=${event.eventId}, title=${event.title}")
            
            EventsStorage(context).classCustomUse { db ->
                db.addEvent(event)
                val savedEvent = db.events.find { it.eventId == event.eventId }
                assertNotNull("Event ${event.eventId} should be saved to storage", savedEvent)
                DevLog.info(LOG_TAG, "Event ${event.eventId} saved to storage: ${savedEvent?.title}")
            }
            true
        }
        every { ApplicationController.afterCalendarEventFired(any()) } just Runs
        every { ApplicationController.postEventNotifications(any(), any<Collection<EventAlertRecord>>()) } just Runs
    }

    private fun setupCalendarServiceMocks() {
        every { ApplicationController.onCalendarReloadFromService(any(), any()) } answers {
            val userActionUntil = secondArg<Long>()
            DevLog.info(LOG_TAG, "onCalendarReloadFromService called with userActionUntil=$userActionUntil")
            DevLog.info(LOG_TAG, "onCalendarReloadFromService completed")
        }
        
        every { ApplicationController.onCalendarRescanForRescheduledFromService(any(), any()) } answers {
            val userActionUntil = secondArg<Long>()
            DevLog.info(LOG_TAG, "onCalendarRescanForRescheduledFromService called with userActionUntil=$userActionUntil")
            DevLog.info(LOG_TAG, "onCalendarRescanForRescheduledFromService completed")
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
    }

    private fun clearStorages() {
        EventsStorage(context).classCustomUse { db -> db.deleteAllEvents() }
        MonitorStorage(context).classCustomUse { db -> db.deleteAlertsMatching { true } }
    }

    private fun createEventAlertRecord(event: EventRecord, alert: MonitorEventAlertEntry): EventAlertRecord {
        return EventAlertRecord(
            calendarId = event.calendarId,
            eventId = event.eventId,
            isAllDay = event.isAllDay,
            isRepeating = false,
            alertTime = alert.alertTime,
            notificationId = 0,
            title = event.title,
            desc = event.desc ?: "",
            startTime = event.startTime,
            endTime = event.endTime,
            instanceStartTime = alert.instanceStartTime,
            instanceEndTime = alert.instanceEndTime,
            location = event.location ?: "",
            lastStatusChangeTime = 0L,
            color = event.color,
            displayStatus = EventDisplayStatus.Hidden,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = System.currentTimeMillis(),
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0L
        )
    }

    @Before
    fun setup() {
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
        setupCalendarServiceMocks()
        setupTestCalendar()
        clearStorages()
    }

    @After
    fun cleanup() {
        unmockkAll()
        
        // Delete test events and calendar
        if (testEventId != -1L) {
            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events._ID} = ?",
                arrayOf(testEventId.toString())
            )
        }
        
        if (testCalendarId != -1L) {
            context.contentResolver.delete(
                CalendarContract.Calendars.CONTENT_URI,
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(testCalendarId.toString())
            )
        }

        clearStorages()
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

        // Add reminder for the event
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, testEventId)
            put(CalendarContract.Reminders.MINUTES, 1) // 1 minute before
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)

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
        startServiceAndWait()

        // Verify that CalendarMonitor.onRescanFromService was called
        verify(exactly = 1) { mockCalendarMonitor.onRescanFromService(any()) }

        // Verify the event was detected and processed
        verifyEventProcessed(
            eventId = testEventId,
            startTime = eventStartTime,
            title = "Test Monitor Event"
        )
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
        val startTime = currentTime.get() // Capture initial time
        monitorState.prevEventScanTo = startTime
        monitorState.prevEventFireFromScan = startTime

        // Create multiple test events with proper calendar setup
        val events = createMultipleTestEvents(3)
        DevLog.info(LOG_TAG, "Created ${events.size} test events: $events")
        
        // Mock event details for each test event
        events.forEachIndexed { index, eventId ->
            val eventStartTime = startTime + (3600000 * (index + 1))
            mockEventDetails(
                eventId = eventId,
                startTime = eventStartTime,
                title = "Test Event $index"
            )
        }

        // Mock alerts for all events
        every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
            val scanFrom = secondArg<Long>()
            val scanTo = thirdArg<Long>()
            
            events.mapIndexed { index, eventId ->
                val alertTime = startTime + (3600000 * (index + 1)) - (15 * 60 * 1000)
                val eventStartTime = startTime + (3600000 * (index + 1))
                val endTime = eventStartTime + 3600000
                
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

        // Verify the calendar is handled and settings
        val settings = Settings(context)
        assertTrue("Calendar should be handled", settings.getCalendarIsHandled(testCalendarId))
        assertTrue("Calendar monitoring should be enabled", settings.enableCalendarRescan)

        // Notify calendar change and start service
        notifyCalendarChangeAndWait()
        startServiceAndWait()

        // Verify that CalendarMonitor.onRescanFromService was called
        verify(exactly = 1) { mockCalendarMonitor.onRescanFromService(any()) }

        // Verify all events were processed correctly
        events.forEachIndexed { index, eventId ->
            val expectedStartTime = startTime + (3600000 * (index + 1))
            verifyEventProcessed(
                eventId = eventId,
                startTime = expectedStartTime,
                title = "Test Event $index"
            )
        }
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
        startServiceAndWait(startDelay = startDelay)

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
        return calUri?.lastPathSegment?.toLong() ?: -1L
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
        testEventId = eventUri?.lastPathSegment?.toLong() ?: -1L

        // Add a reminder
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, testEventId)
            put(CalendarContract.Reminders.MINUTES, 15)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)

        return testEventId
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
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }
        }
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
    }

    /**
     * Mocks event reminder behavior for a specific event.
     * 
     * @param eventId The ID of the event to mock reminders for
     * @param millisecondsBefore Time before event to trigger reminder
     */
    private fun mockEventReminders(eventId: Long, millisecondsBefore: Long = 30000) {
        every { CalendarProvider.getEventReminders(any(), eq(eventId)) } returns listOf(
            EventReminderRecord(millisecondsBefore = millisecondsBefore)
        )
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
            
            if (startTime in scanFrom..scanTo) {
                listOf(MonitorEventAlertEntry(
                    eventId = eventId,
                    isAllDay = false,
                    alertTime = startTime - alertOffset,
                    instanceStartTime = startTime,
                    instanceEndTime = startTime + duration,
                    alertCreatedByUs = false,
                    wasHandled = false
                ))
            } else {
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
        every { CalendarProvider.getEvent(any(), eq(eventId)) } returns EventRecord(
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
    }

    /**
     * Verifies that no events are present in storage.
     * Fails the test if any events are found.
     */
    private fun verifyNoEvents() {
        EventsStorage(context).classCustomUse { db ->
            val events = db.events
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
        EventsStorage(context).classCustomUse { db ->
            val events = db.events
            DevLog.info(LOG_TAG, "Found ${events.size} events in storage")
            events.forEach { event ->
                DevLog.info(LOG_TAG, "Event: id=${event.eventId}, timeFirstSeen=${event.timeFirstSeen}, startTime=${event.startTime}")
            }
            
            assertTrue("Event should be processed", events.any { it.eventId == eventId })
            
            val processedEvent = events.firstOrNull { it.eventId == eventId }
            assertNotNull("Event should exist in storage", processedEvent)
            
            processedEvent?.let {
                if (title != null) {
                    assertEquals("Event should have correct title", title, it.title)
                }
                assertEquals("Event should have correct start time", startTime, it.startTime)
                if (afterDelay != null) {
                    assertTrue("Event should be processed after the delay",
                        it.timeFirstSeen >= afterDelay
                    )
                }
            }
        }
    }

    /**
     * Starts the monitor service and waits for processing to complete.
     * 
     * @param startDelay Optional delay before service should start processing
     * @param waitTime Time to wait for service processing to complete
     * @param userActionOffset Time offset for user action window
     */
    private fun startServiceAndWait(startDelay: Int = 0, waitTime: Long = 5000, userActionOffset: Long = 10000) {
        DevLog.info(LOG_TAG, "Starting service with delay $startDelay...")
        CalendarMonitorService.startRescanService(
            context = context,
            startDelay = startDelay,
            reloadCalendar = true,
            rescanMonitor = true,
            userActionUntil = currentTime.get() + userActionOffset
        )
        DevLog.info(LOG_TAG, "Service started, waiting $waitTime ms...")
        advanceTimer(waitTime)
        DevLog.info(LOG_TAG, "Service wait complete")
    }

    /**
     * Notifies of calendar changes and waits for change propagation.
     * 
     * @param waitTime Time to wait for change propagation
     */
    private fun notifyCalendarChangeAndWait(waitTime: Long = 1000) {
        DevLog.info(LOG_TAG, "Notifying calendar change...")
        ApplicationController.onCalendarChanged(context)
        advanceTimer(waitTime)
        DevLog.info(LOG_TAG, "Calendar change notification complete")
    }
} 
