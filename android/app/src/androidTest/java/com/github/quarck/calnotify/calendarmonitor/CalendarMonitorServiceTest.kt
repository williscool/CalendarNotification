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

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val realContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Create mock context instead of spying on ContextImpl
        context = mockk<Context>(relaxed = true) {
            every { packageName } returns realContext.packageName
            every { contentResolver } returns realContext.contentResolver
            every { getDatabasePath(any()) } answers { realContext.getDatabasePath(firstArg()) }
            every { getSystemService(Context.ALARM_SERVICE) } returns mockk<AlarmManager>(relaxed = true)
            every { getSystemService(any<String>()) } answers { realContext.getSystemService(firstArg()) }
            every { checkPermission(any(), any(), any()) } answers { realContext.checkPermission(firstArg(), secondArg(), thirdArg()) }
            every { checkCallingOrSelfPermission(any()) } answers { realContext.checkCallingOrSelfPermission(firstArg()) }
            every { createPackageContext(any(), any()) } answers { realContext.createPackageContext(firstArg(), secondArg()) }
//            every { startService(any()) } answers {
//                val intent = firstArg<Intent>()
//                DevLog.info(LOG_TAG, "startService called with intent: action=${intent.action}, extras=${intent.extras}")
//                mockService.onHandleIntent(intent)
//                ComponentName(realContext.packageName, CalendarMonitorService::class.java.name)
//            }

            // Mock shared preferences for settings
            val sharedPrefs = realContext.getSharedPreferences("CalendarNotificationsPlusPrefs", Context.MODE_PRIVATE)
            every { getSharedPreferences(any(), any()) } returns sharedPrefs
        }
        
        // Mock alarm manager with PendingIntent handling
        val mockAlarmManager = mockk<AlarmManager>(relaxed = true)
        every { context.getSystemService(Context.ALARM_SERVICE) } returns mockAlarmManager
        every { mockAlarmManager.setInexactRepeating(any(), any(), any(), any()) } just Runs

        // Mock PendingIntent.getBroadcast directly
        mockkStatic(PendingIntent::class)
        every { 
            PendingIntent.getBroadcast(any(), any(), any(), any()) 
        } returns mockk(relaxed = true)

        // Configure mock timer
        val mockFuture = mockk<ScheduledFuture<*>>()
        every { 
            mockTimer.schedule(any(), any<Long>(), any()) 
        } answers { call ->
            val delay = call.invocation.args[1] as Long
            val unit = call.invocation.args[2] as TimeUnit
            val task = call.invocation.args[0] as Runnable
            currentTime.addAndGet(unit.toMillis(delay))
            task.run()
            mockFuture
        }

        // Mock ApplicationController singleton BEFORE creating other mocks
        mockkObject(ApplicationController)
        
        // Create CalendarMonitor mock with relaxed behavior
        mockCalendarMonitor = mockk<CalendarMonitorInterface>(relaxed = true)
        

        // Mock the CalendarMonitor property getter
        every { ApplicationController.calendarMonitorInternal } returns mockCalendarMonitor
        every { ApplicationController.CalendarMonitor } returns mockCalendarMonitor
        
        // Mock other ApplicationController methods that are called
        every { 
            ApplicationController.onCalendarReloadFromService(any(), any()) 
        } answers {
            val context = firstArg<Context>()
            val userActionUntil = secondArg<Long>()
            DevLog.info(LOG_TAG, "onCalendarReloadFromService called with userActionUntil=$userActionUntil")
            mockCalendarMonitor.onRescanFromService(context)
            DevLog.info(LOG_TAG, "onCalendarReloadFromService completed")
        }
        
        every { 
            ApplicationController.onCalendarRescanForRescheduledFromService(any(), any()) 
        } answers {
            val context = firstArg<Context>()
            val userActionUntil = secondArg<Long>()
            DevLog.info(LOG_TAG, "onCalendarRescanForRescheduledFromService called with userActionUntil=$userActionUntil")
            mockCalendarMonitor.onRescanFromService(context)
            DevLog.info(LOG_TAG, "onCalendarRescanForRescheduledFromService completed")
        }

        // Mock AddEventMonitor
        val mockAddEventMonitor = mockk<CalendarChangeRequestMonitorInterface>(relaxed = true)
        every { ApplicationController.AddEventMonitorInstance } returns mockAddEventMonitor
        every { mockAddEventMonitor.onRescanFromService(any()) } just Runs

        // Additional ApplicationController mocks needed
        every { ApplicationController.shouldMarkEventAsHandledAndSkip(any(), any()) } returns false
        every { ApplicationController.registerNewEvent(any(), any()) } returns true
        every { ApplicationController.afterCalendarEventFired(any()) } just Runs
        every { ApplicationController.postEventNotifications(any(), any<Collection<EventAlertRecord>>()) } just Runs

        // Create spy of real service
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
//        every { mockService.onHandleIntent(any()) } answers {
//            val intent = firstArg<Intent>()
//            DevLog.info(LOG_TAG, "Service.onHandleIntent called with intent: extras=${intent.extras}")
//            callOriginal()
//        }

        // Mock CalendarProvider more comprehensively
        mockkObject(CalendarProvider)
        
        // Mock getEventReminders to return reminders for test events
        every { CalendarProvider.getEventReminders(any(), any()) } answers {
            val eventId = secondArg<Long>()
            listOf(EventReminderRecord(millisecondsBefore = 15 * 60 * 1000)) // 15 minutes before
        }

        every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
            val context = firstArg<Context>()
            val scanFrom = secondArg<Long>()
            val scanTo = thirdArg<Long>()
            
            if (testEventId != -1L && eventStartTime >= scanFrom && eventStartTime <= scanTo) {
                listOf(MonitorEventAlertEntry(
                    eventId = testEventId,
                    isAllDay = false,
                    alertTime = reminderTime,
                    instanceStartTime = eventStartTime,
                    instanceEndTime = eventStartTime + 60000,
                    alertCreatedByUs = false,
                    wasHandled = false
                ))
            } else {
                emptyList()
            }
        }

        every { CalendarProvider.getEvent(any(), any()) } answers {
            val context = firstArg<Context>()
            val eventId = secondArg<Long>()
            
            if (eventId == testEventId) {
                EventRecord(
                    calendarId = testCalendarId,
                    eventId = eventId,
                    details = CalendarEventDetails(
                        title = "Test Monitor Event",
                        desc = "Test Description",
                        location = "",
                        timezone = "UTC",
                        startTime = eventStartTime,
                        endTime = eventStartTime + 60000,
                        isAllDay = false,
                        reminders = listOf(),
                        repeatingRule = "",
                        repeatingRDate = "",
                        repeatingExRule = "",
                        repeatingExRDate = "",
                        color = Consts.DEFAULT_CALENDAR_EVENT_COLOR
                    ),
                    eventStatus = EventStatus.Confirmed,
                    attendanceStatus = AttendanceStatus.None
                )
            } else null
        }

        // Create test calendar
        testCalendarId = createTestCalendar(
            displayName = "Test Calendar",
            accountName = "test@local",
            ownerAccount = "test@local"
        )

        // Clear any existing events
        EventsStorage(context).classCustomUse { db ->
            db.deleteAllEvents()
        }

        // Clear monitor storage
        MonitorStorage(context).classCustomUse { db ->
            db.deleteAlertsMatching { true } // Delete all alerts
        }

        // Enable calendar monitoring in settings
        val settings = Settings(context)
        settings.setBoolean("enable_manual_calendar_rescan", true)
        context.getSharedPreferences("CalendarNotificationsPlusPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("calendar_is_handled_$testCalendarId", true)
            .commit()
    }

  @After
    fun cleanup() {
        unmockkAll()
        
        // Delete test events
        if (testEventId != -1L) {
            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events._ID} = ?",
                arrayOf(testEventId.toString())
            )
        }
        
        // Delete test calendar
        if (testCalendarId != -1L) {
            context.contentResolver.delete(
                CalendarContract.Calendars.CONTENT_URI,
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(testCalendarId.toString())
            )
        }

        // Clear any notifications
        EventsStorage(context).classCustomUse { db ->
            db.deleteAllEvents()
        }

        // Clear monitor storage
        MonitorStorage(context).classCustomUse { db ->
            db.deleteAlertsMatching { true } // Delete all alerts
        }
    }

    @Test
    fun testCalendarEventMonitoring() {
        // Reset monitor state and ensure firstScanEver is false
        val monitorState = CalendarMonitorState(context)
        monitorState.firstScanEver = false
        currentTime.set(System.currentTimeMillis())
        monitorState.prevEventScanTo = currentTime.get()
        monitorState.prevEventFireFromScan = currentTime.get()
        
        // Create a test event with reminder - use current time
        eventStartTime = currentTime.get() + 60000 // 1 minute from now
        reminderTime = eventStartTime - 30000 // 30 seconds before start
        
        DevLog.info(LOG_TAG, "Creating test event: startTime=$eventStartTime, reminderTime=$reminderTime")
        
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

        // Verify event was created
        val createdEvent = CalendarProvider.getEvent(context, testEventId)
        assertNotNull("Event should exist in calendar", createdEvent)
        assertEquals("Event start time should match", eventStartTime, createdEvent?.startTime)

        // Add a reminder for 30 seconds before
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, testEventId)
            put(CalendarContract.Reminders.MINUTES, 1) // 1 minute before
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)

        // Verify reminder was created
        val reminders = CalendarProvider.getEventReminders(context, testEventId)
        assertTrue("Event should have a reminder", reminders.isNotEmpty())
        DevLog.info(LOG_TAG, "Created reminder: ${reminders.first()}")
        
        // Verify the calendar is handled and settings
        val settings = Settings(context)
        assertTrue("Calendar should be handled", settings.getCalendarIsHandled(testCalendarId))
        assertTrue("Calendar monitoring should be enabled", settings.enableCalendarRescan)

        // Check monitor storage before service start
        MonitorStorage(context).classCustomUse { db ->
            val alerts = db.alerts
            DevLog.info(LOG_TAG, "Monitor alerts before service: ${alerts.size}")
            alerts.forEach { alert ->
                DevLog.info(LOG_TAG, "Alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
            }
        }

        // Notify calendar change and wait for propagation
        DevLog.info(LOG_TAG, "Notifying calendar change...")
        ApplicationController.onCalendarChanged(context)
        mockTimer.schedule({}, 1, TimeUnit.SECONDS)
        DevLog.info(LOG_TAG, "Calendar change notification complete")

        // Start the monitor service
        DevLog.info(LOG_TAG, "Starting monitor service...")
        CalendarMonitorService.startRescanService(
            context = context,
            startDelay = 0,
            reloadCalendar = true,
            rescanMonitor = true,
            userActionUntil = currentTime.get() + 10000
        )
        DevLog.info(LOG_TAG, "Monitor service started")

        // Wait for service to complete and process events
        DevLog.info(LOG_TAG, "Waiting for service to complete...")
        mockTimer.schedule({}, 5, TimeUnit.SECONDS)
        DevLog.info(LOG_TAG, "Service wait complete")

        // Verify that CalendarMonitor.onRescanFromService was called
        verify(exactly = 1) { mockCalendarMonitor.onRescanFromService(any()) }

        // Check monitor storage after service
        MonitorStorage(context).classCustomUse { db ->
            val alerts = db.alerts
            DevLog.info(LOG_TAG, "Monitor alerts after service: ${alerts.size}")
            alerts.forEach { alert ->
                DevLog.info(LOG_TAG, "Alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
            }
        }

        // Verify the event was detected
        EventsStorage(context).classCustomUse { db ->
            val events = db.events
            DevLog.info(LOG_TAG, "Events in storage: ${events.size}")
            events.forEach { event ->
                DevLog.info(LOG_TAG, "Event: id=${event.eventId}, title=${event.title}, start=${event.startTime}, alert=${event.alertTime}")
            }

            assertTrue("Monitor should detect the new event", events.any { 
                it.eventId == testEventId && 
                it.title == "Test Monitor Event" &&
                it.startTime == eventStartTime
            })
        }
    }

    @Test
    fun testCalendarReload() {
        // Create multiple test events
        val events = createMultipleTestEvents(3)
        DevLog.info(LOG_TAG, "Created ${events.size} test events: $events")
        
        // Mock CalendarProvider to return our test events
        every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
            val context = firstArg<Context>()
            val scanFrom = secondArg<Long>()
            val scanTo = thirdArg<Long>()
            
            DevLog.info(LOG_TAG, "getEventAlertsForInstancesInRange called with scanFrom=$scanFrom, scanTo=$scanTo")
            
            events.mapIndexed { index, eventId ->
                val alertTime = currentTime.get() + (3600000 * (index + 1)) - (15 * 60 * 1000)
                val startTime = currentTime.get() + (3600000 * (index + 1))
                val endTime = currentTime.get() + (3600000 * (index + 2))
                
                DevLog.info(LOG_TAG, "Returning alert for event $eventId: alertTime=$alertTime, startTime=$startTime")
                
                MonitorEventAlertEntry(
                    eventId = eventId,
                    isAllDay = false,
                    alertTime = alertTime,
                    instanceStartTime = startTime,
                    instanceEndTime = endTime,
                    alertCreatedByUs = false,
                    wasHandled = false
                )
            }
        }

        // Mock event details for each test event
        events.forEachIndexed { index, eventId ->
            every { CalendarProvider.getEvent(any(), eq(eventId)) } answers {
                val startTime = currentTime.get() + (3600000 * (index + 1))
                val endTime = currentTime.get() + (3600000 * (index + 2))
                
                DevLog.info(LOG_TAG, "getEvent called for eventId=$eventId: startTime=$startTime")
                
                EventRecord(
                    calendarId = testCalendarId,
                    eventId = eventId,
                    details = CalendarEventDetails(
                        title = "Test Event $index",
                        desc = "Test Description $index",
                        location = "",
                        timezone = "UTC",
                        startTime = startTime,
                        endTime = endTime,
                        isAllDay = false,
                        reminders = listOf(EventReminderRecord(millisecondsBefore = 15 * 60 * 1000)),
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
        }

        // Mock ApplicationController behavior for event registration
        var registeredEvents = mutableListOf<EventAlertRecord>()
        every { ApplicationController.registerNewEvent(any(), any()) } answers {
            val context = firstArg<Context>()
            val event = secondArg<EventAlertRecord>()
            
            DevLog.info(LOG_TAG, "registerNewEvent called for eventId=${event.eventId}, title=${event.title}")
            registeredEvents.add(event)
            
            // Actually save the event to storage
            EventsStorage(context).classCustomUse { db ->
                db.addEvent(event)
            }
            
            // Verify event was actually saved
            EventsStorage(context).classCustomUse { db ->
                val savedEvent = db.events.find { it.eventId == event.eventId }
                assertNotNull("Event ${event.eventId} should be saved to storage", savedEvent)
                DevLog.info(LOG_TAG, "Event ${event.eventId} saved to storage: ${savedEvent?.title}")
            }
            
            true
        }

        // Log initial storage state
        DevLog.info(LOG_TAG, "Initial storage state:")
        EventsStorage(context).classCustomUse { db ->
            val initialEvents = db.events
            DevLog.info(LOG_TAG, "Events in storage before test: ${initialEvents.size}")
            initialEvents.forEach { event ->
                DevLog.info(LOG_TAG, "Initial event: id=${event.eventId}, title=${event.title}")
            }
        }

        // Notify calendar change and wait for propagation
        DevLog.info(LOG_TAG, "Notifying calendar change...")
        ApplicationController.onCalendarChanged(context)
        mockTimer.schedule({}, 1, TimeUnit.SECONDS)
        
        // Start service with reload
        DevLog.info(LOG_TAG, "Starting service with reload...")
        val intent = Intent(context, CalendarMonitorService::class.java).apply {
            putExtra("start_delay", 0)
            putExtra("reload_calendar", true)
            putExtra("rescan_monitor", true)
            putExtra("user_action_until", currentTime.get() + 10000)
        }
        context.startService(intent)

        // Wait for service to complete and process events
        DevLog.info(LOG_TAG, "Waiting for service to complete...")
        mockTimer.schedule({}, 5, TimeUnit.SECONDS)

        // Verify monitor rescan was called
        verify(exactly = 1) { mockCalendarMonitor.onRescanFromService(any()) }
        DevLog.info(LOG_TAG, "Monitor rescan verified")

        // Log registered events
        DevLog.info(LOG_TAG, "Registered events during test: ${registeredEvents.size}")
        registeredEvents.forEach { event ->
            DevLog.info(LOG_TAG, "Registered event: id=${event.eventId}, title=${event.title}")
        }

        // Verify all events were reloaded
        EventsStorage(context).classCustomUse { db ->
            val storedEvents = db.events.filter { it.calendarId == testCalendarId }
            DevLog.info(LOG_TAG, "Found ${storedEvents.size} events in storage after test")
            
            storedEvents.forEach { event ->
                DevLog.info(LOG_TAG, "Stored event: id=${event.eventId}, title=${event.title}, startTime=${event.startTime}")
            }
            
            assertEquals("All test events should be reloaded", events.size, storedEvents.size)
            
            // Verify each event was stored correctly
            events.forEachIndexed { index, eventId ->
                val expectedStartTime = currentTime.get() + (3600000 * (index + 1))
                val storedEvent = storedEvents.find { it.eventId == eventId }
                
                assertNotNull("Event $eventId should be in storage", storedEvent)
                storedEvent?.let {
                    assertEquals("Event $eventId should have correct title", "Test Event $index", it.title)
                    assertEquals("Event $eventId should have correct start time", expectedStartTime, it.startTime)
                }
            }
        }
    }

    /**
     * Tests that the CalendarMonitorService properly respects the startDelay parameter when processing events.
     * 
     * This test verifies that:
     * 1. When a service is started with a delay parameter (e.g. 2000ms)
     * 2. And a calendar event is created with a reminder
     * 3. Then the event should not be processed until after the specified delay has elapsed
     * 
     * This ensures that delayed event processing works correctly for scenarios where immediate 
     * processing is not desired, such as waiting for calendar sync or other system events.
     */
    @Test
    fun testDelayedProcessing() {
        val startDelay = 2000 // 2 second delay
        val startTime = currentTime.get()

        // Create test event with reminder
        createTestEvent()

        // Notify calendar change and wait a moment for propagation
        ApplicationController.onCalendarChanged(context)
        mockTimer.schedule({}, 1, TimeUnit.SECONDS)

        // Start service with delay
        val intent = Intent(context, CalendarMonitorService::class.java).apply {
            putExtra("start_delay", startDelay)
            putExtra("reload_calendar", true)
            putExtra("rescan_monitor", true)
            putExtra("user_action_until", currentTime.get() + 10000)
        }
        context.startService(intent)

        // Wait for slightly longer than the delay
        mockTimer.schedule({}, startDelay + 3000L, TimeUnit.MILLISECONDS)

        // Verify the event was processed after the delay
        EventsStorage(context).classCustomUse { db ->
            val events = db.events
            assertTrue("Event should be processed after delay",
                events.any { it.eventId == testEventId }
            )
            
            val processTime = events.firstOrNull { it.eventId == testEventId }?.timeFirstSeen ?: 0L
            assertTrue("Event should be processed after the delay",
                processTime >= startTime + startDelay
            )
        }
    }

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

    private fun createMultipleTestEvents(count: Int): List<Long> {
        val eventIds = mutableListOf<Long>()
        repeat(count) { index ->
            val currentTime = this.currentTime.get()
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
} 
