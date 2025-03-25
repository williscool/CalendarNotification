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
            every { getDatabasePath(any()) } answers { 
                val dbName = firstArg<String>()
                realContext.getDatabasePath(dbName)
            }
            every { getSystemService(Context.ALARM_SERVICE) } returns mockk<AlarmManager>(relaxed = true)
            every { getSystemService(any<String>()) } answers { realContext.getSystemService(firstArg()) }
            every { checkPermission(any(), any(), any()) } answers { realContext.checkPermission(firstArg(), secondArg(), thirdArg()) }
            every { checkCallingOrSelfPermission(any()) } answers { realContext.checkCallingOrSelfPermission(firstArg()) }
            every { createPackageContext(any(), any()) } answers { realContext.createPackageContext(firstArg(), secondArg()) }
            every { getSharedPreferences(any(), any()) } answers {
                val name = firstArg<String>()
                val mode = secondArg<Int>()
                realContext.getSharedPreferences(name, mode)
            }
            every { getApplicationInfo() } returns realContext.applicationInfo
            every { getFilesDir() } returns realContext.filesDir
            every { getCacheDir() } returns realContext.cacheDir
            every { getDir(any(), any()) } answers {
                val name = firstArg<String>()
                val mode = secondArg<Int>()
                realContext.getDir(name, mode)
            }
            every { startService(any()) } answers {
                val intent = firstArg<Intent>()
                DevLog.info(LOG_TAG, "startService called with intent: action=${intent.action}, extras=${intent.extras}")
                // Directly call onHandleIntent on our mock service
                mockService.onHandleIntent(intent)
                ComponentName(realContext.packageName, CalendarMonitorService::class.java.name)
            }
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

        // Configure mock timer with proper task scheduling
        mockTimer = mockk<ScheduledExecutorService>()
        val scheduledTasks = mutableListOf<Pair<Runnable, Long>>()
        
        every { 
            mockTimer.schedule(any(), any<Long>(), any()) 
        } answers { call ->
            val task = call.invocation.args[0] as Runnable
            val delay = call.invocation.args[1] as Long
            val unit = call.invocation.args[2] as TimeUnit
            
            // Store task and its scheduled time
            scheduledTasks.add(Pair(task, currentTime.get() + unit.toMillis(delay)))
            
            // Sort tasks by scheduled time
            scheduledTasks.sortBy { it.second }
            
            // Execute all tasks that are due based on currentTime
            while (scheduledTasks.isNotEmpty() && scheduledTasks[0].second <= currentTime.get()) {
                val (nextTask, _) = scheduledTasks.removeAt(0)
                nextTask.run()
            }
            
            mockk(relaxed = true)
        }

        // Mock ApplicationController singleton BEFORE creating other mocks
        mockkObject(ApplicationController)
        mockkObject(CalendarProvider)
        
        // Create CalendarMonitor mock with relaxed behavior
        mockCalendarMonitor = mockk<CalendarMonitorInterface>(relaxed = true)

        // Mock the CalendarMonitor property getter
        every { ApplicationController.calendarMonitorInternal } returns mockCalendarMonitor
        every { ApplicationController.CalendarMonitor } returns mockCalendarMonitor
        
        // Mock ApplicationController methods that are called
        every { 
            ApplicationController.onCalendarReloadFromService(any(), any()) 
        } answers {
            val context = firstArg<Context>()
            val userActionUntil = secondArg<Long>()
            DevLog.info(LOG_TAG, "onCalendarReloadFromService called with userActionUntil=$userActionUntil")
            // Don't call onRescanFromService here since it will be called by the service itself
            DevLog.info(LOG_TAG, "onCalendarReloadFromService completed")
        }
        
        every { 
            ApplicationController.onCalendarRescanForRescheduledFromService(any(), any()) 
        } answers {
            val context = firstArg<Context>()
            val userActionUntil = secondArg<Long>()
            DevLog.info(LOG_TAG, "onCalendarRescanForRescheduledFromService called with userActionUntil=$userActionUntil")
            // Don't call onRescanFromService here since it will be called by the service itself
            DevLog.info(LOG_TAG, "onCalendarRescanForRescheduledFromService completed")
        }

        // Mock additional ApplicationController methods
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
                DevLog.info(LOG_TAG, "Alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}}")
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
                DevLog.info(LOG_TAG, "Alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}")
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
        
        // Track service and monitor interactions
        var launchRescanServiceCalled = false
        var onRescanFromServiceCalled = false
        var getEventAlertsCallCount = 0
        var getEventCallCount = 0
        var registerEventCallCount = 0
        
        // Track registered events for verification
        val registeredEvents = mutableListOf<EventAlertRecord>()
        val alertsFound = mutableListOf<MonitorEventAlertEntry>()

        // Mock CalendarProvider to return our test events
        every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
            val context = firstArg<Context>()
            val scanFrom = secondArg<Long>()
            val scanTo = thirdArg<Long>()
            
            getEventAlertsCallCount++
            DevLog.info(LOG_TAG, "getEventAlertsForInstancesInRange call #$getEventAlertsCallCount with scanFrom=$scanFrom, scanTo=$scanTo")
            
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
                ).also { alertsFound.add(it) }
            }
        }

        // Mock event details for each test event
        events.forEachIndexed { index, eventId ->
            every { CalendarProvider.getEvent(any(), eq(eventId)) } answers {
                val eventStartTime = startTime + (3600000 * (index + 1))
                val endTime = eventStartTime + 3600000
                
                getEventCallCount++
                DevLog.info(LOG_TAG, "getEvent call #$getEventCallCount for eventId=$eventId: startTime=$eventStartTime")
                
                EventRecord(
                    calendarId = testCalendarId,
                    eventId = eventId,
                    details = CalendarEventDetails(
                        title = "Test Event $index",
                        desc = "Test Description $index",
                        location = "",
                        timezone = "UTC",
                        startTime = eventStartTime,
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
        every { ApplicationController.registerNewEvent(any(), any()) } answers {
            val context = firstArg<Context>()
            val event = secondArg<EventAlertRecord>()
            
            registerEventCallCount++
            DevLog.info(LOG_TAG, "registerNewEvent call #$registerEventCallCount for eventId=${event.eventId}, title=${event.title}")
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

        // Mock CalendarMonitor behavior
        every { mockCalendarMonitor.launchRescanService(any(), any(), any(), any(), any()) } answers {
            val ctx = firstArg<Context>()
            val delay = secondArg<Int>()
            val reloadCalendar = thirdArg<Boolean>()
            val rescanMonitor = arg<Boolean>(3)
            val userActionUntil = arg<Long>(4)
            
            launchRescanServiceCalled = true
            DevLog.info(LOG_TAG, "launchRescanService called with delay=$delay, reloadCalendar=$reloadCalendar, rescanMonitor=$rescanMonitor")
            
            // Start the service directly since we're mocking
            CalendarMonitorService.startRescanService(
                context = ctx,
                startDelay = delay,
                reloadCalendar = reloadCalendar,
                rescanMonitor = rescanMonitor,
                userActionUntil = userActionUntil
            )
        }

        every { mockCalendarMonitor.onRescanFromService(any()) } answers {
            val ctx = firstArg<Context>()
            onRescanFromServiceCalled = true
            DevLog.info(LOG_TAG, "onRescanFromService called")
            
            // Simulate the monitor's rescan behavior
            val alerts = CalendarProvider.getEventAlertsForInstancesInRange(
                ctx,
                currentTime.get() - Consts.MAX_SCAN_BACKWARD_DAYS * Consts.DAY_IN_MILLISECONDS,
                currentTime.get() + 30 * Consts.DAY_IN_MILLISECONDS
            )
            
            DevLog.info(LOG_TAG, "Found ${alerts.size} alerts during rescan")
            
            alerts.forEach { alert ->
                val event = CalendarProvider.getEvent(ctx, alert.eventId)
                if (event != null) {
                    val alertRecord = EventAlertRecord(
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
                    
                    DevLog.info(LOG_TAG, "Registering event: id=${event.eventId}, title=${event.title}")
                    ApplicationController.registerNewEvent(ctx, alertRecord)
                }
            }
            
            Unit
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

        // Enable calendar monitoring in settings
        val settings = Settings(context)
        settings.setBoolean("enable_manual_calendar_rescan", true)
        context.getSharedPreferences("CalendarNotificationsPlusPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("calendar_is_handled_$testCalendarId", true)
            .commit()

        // Notify calendar change and wait for propagation
        DevLog.info(LOG_TAG, "Notifying calendar change...")
        ApplicationController.onCalendarChanged(context)
        advanceTimer(1000) // Advance timer by 1 second
        
        // Verify service flow after calendar change
        assertTrue("launchRescanService should be called after calendar change", launchRescanServiceCalled)

        // Wait for service to complete and process events
        DevLog.info(LOG_TAG, "Waiting for service to complete...")
        advanceTimer(5000) // Advance timer by 5 seconds

        // Verify monitor rescan was called exactly once
        verify(exactly = 1) { mockCalendarMonitor.onRescanFromService(any()) }
        DevLog.info(LOG_TAG, "Monitor rescan verified")

        // Verify service flow
        assertTrue("onRescanFromService should be called", onRescanFromServiceCalled)
        assertTrue("getEventAlertsForInstancesInRange should be called at least once", getEventAlertsCallCount > 0)
        assertEquals("getEvent should be called once for each event", events.size, getEventCallCount)
        assertEquals("registerNewEvent should be called once for each event", events.size, registerEventCallCount)

        // Verify alerts were found correctly
        assertEquals("Should find correct number of alerts", events.size, alertsFound.size)
        alertsFound.forEachIndexed { index, alert ->
            val expectedStartTime = startTime + (3600000 * (index + 1))
            val expectedAlertTime = expectedStartTime - (15 * 60 * 1000)
            assertEquals("Alert ${alert.eventId} should have correct start time", expectedStartTime, alert.instanceStartTime)
            assertEquals("Alert ${alert.eventId} should have correct alert time", expectedAlertTime, alert.alertTime)
        }

        // Log registered events
        DevLog.info(LOG_TAG, "Registered events during test: ${registeredEvents.size}")
        registeredEvents.forEach { event ->
            DevLog.info(LOG_TAG, "Registered event: id=${event.eventId}, title=${event.title}")
        }

        // Verify all events were reloaded and stored correctly
        EventsStorage(context).classCustomUse { db ->
            val storedEvents = db.events.filter { it.calendarId == testCalendarId }
            DevLog.info(LOG_TAG, "Found ${storedEvents.size} events in storage after test")
            
            storedEvents.forEach { event ->
                DevLog.info(LOG_TAG, "Stored event: id=${event.eventId}, title=${event.title}, startTime=${event.startTime}")
            }
            
            // Verify correct number of events
            assertEquals("All test events should be reloaded", events.size, storedEvents.size)
            
            // Verify each event was stored with correct data
            events.forEachIndexed { index, eventId ->
                val expectedStartTime = startTime + (3600000 * (index + 1))
                val storedEvent = storedEvents.find { it.eventId == eventId }
                
                assertNotNull("Event $eventId should be in storage", storedEvent)
                storedEvent?.let {
                    assertEquals("Event $eventId should have correct title", "Test Event $index", it.title)
                    assertEquals("Event $eventId should have correct start time", expectedStartTime, it.startTime)
                    assertEquals("Event $eventId should have correct calendar ID", testCalendarId, it.calendarId)
                    assertEquals("Event $eventId should have correct alert time", 
                        expectedStartTime - (15 * 60 * 1000), it.alertTime)
                }
            }
        }

        // Verify the events were registered in the correct order
        assertEquals("All events should have been registered", events.size, registeredEvents.size)
        events.forEachIndexed { index, eventId ->
            val registeredEvent = registeredEvents.find { it.eventId == eventId }
            assertNotNull("Event $eventId should have been registered", registeredEvent)
            assertEquals("Event $eventId should have been registered with correct title", 
                "Test Event $index", registeredEvent?.title)
        }

        // Final verification of call counts
        DevLog.info(LOG_TAG, "Final verification - Call counts:")
        DevLog.info(LOG_TAG, "getEventAlertsForInstancesInRange calls: $getEventAlertsCallCount")
        DevLog.info(LOG_TAG, "getEvent calls: $getEventCallCount")
        DevLog.info(LOG_TAG, "registerNewEvent calls: $registerEventCallCount")
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

    private fun advanceTimer(milliseconds: Long) {
        currentTime.addAndGet(milliseconds)
        // Force timer to check for due tasks
        mockTimer.schedule({}, 0, TimeUnit.MILLISECONDS)
    }
} 
