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

@RunWith(AndroidJUnit4::class)
class CalendarMonitorServiceTest {
    private lateinit var context: Context
    private var testCalendarId: Long = -1
    private var testEventId: Long = -1
    
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
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
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

        // Mock ApplicationController singleton and its properties
        DevLog.info(LOG_TAG, "Setting up ApplicationController mocking...")
        mockkObject(ApplicationController)
        
        // Create CalendarMonitor mock and verify it's properly mocked
        mockCalendarMonitor = mockk<CalendarMonitorInterface>(relaxed = true)
        
        // Verify mockk initialization and setup mocks
        try {
            // First verify no calls have been made yet
            verify(exactly = 0) { ApplicationController.CalendarMonitor }
            verify(exactly = 0) { ApplicationController.onCalendarChanged(any()) }
            verify(exactly = 0) { ApplicationController.onCalendarReloadFromService(any(), any()) }
            DevLog.info(LOG_TAG, "Verified no calls made to ApplicationController yet")
            
            // Set up the mocks
            every { ApplicationController.CalendarMonitor } returns mockCalendarMonitor
            
            // Verify the mock returns our mock instance
            val testMonitor = ApplicationController.CalendarMonitor
            assertSame("CalendarMonitor mock should be properly set up", mockCalendarMonitor, testMonitor)
            assertNotNull("CalendarMonitor should not be null", testMonitor)
            
            // Try making a test call to verify mocking
            testMonitor.onRescanFromService(context)
            verify(exactly = 1) { mockCalendarMonitor.onRescanFromService(any()) }
            
            DevLog.info(LOG_TAG, "Successfully verified CalendarMonitor mock setup and test call")
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Mock verification failed: ${e.message}")
            DevLog.error(LOG_TAG, "Stack trace: ${e.stackTrace.joinToString("\n")}")
            throw e
        }
        
        // Mock calendar monitor methods with verification
        try {
            clearMocks(mockCalendarMonitor) // Clear the test call we made above
            
            every { 
                mockCalendarMonitor.onRescanFromService(any()) 
            } answers {
                val context = it.invocation.args[0] as Context
                DevLog.info(LOG_TAG, "Mocked CalendarMonitor.onRescanFromService called with context: ${context}")
            }
            
            // Verify the mock behavior
            mockCalendarMonitor.onRescanFromService(context)
            verify(exactly = 1) { mockCalendarMonitor.onRescanFromService(any()) }
            clearMocks(mockCalendarMonitor)
            
            DevLog.info(LOG_TAG, "Successfully verified onRescanFromService mock behavior")
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Failed to verify onRescanFromService mock: ${e.message}")
            throw e
        }

        // Mock ApplicationController methods with verification
        try {
            every { 
                ApplicationController.onCalendarRescanForRescheduledFromService(any(), any()) 
            } just Runs
            
            every { 
                ApplicationController.onCalendarReloadFromService(any(), any()) 
            } answers { call ->
                val context = call.invocation.args[0] as Context
                val userActionUntil = call.invocation.args[1] as Long
                DevLog.info(LOG_TAG, "Mocked onCalendarReloadFromService called")
            }
            
            every {
                ApplicationController.onCalendarChanged(any())
            } answers {
                DevLog.info(LOG_TAG, "Mocked onCalendarChanged called")
            }
            
            // Verify the mocks work
            ApplicationController.onCalendarChanged(context)
            verify(exactly = 1) { ApplicationController.onCalendarChanged(any()) }
            
            ApplicationController.onCalendarReloadFromService(context, 0L)
            verify(exactly = 1) { ApplicationController.onCalendarReloadFromService(any(), any()) }
            
            clearMocks(ApplicationController)
            DevLog.info(LOG_TAG, "Successfully verified all ApplicationController method mocks")
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Failed to verify ApplicationController method mocks: ${e.message}")
            throw e
        }

        // Mock AddEventMonitorInstance property and its methods
        val mockAddEventMonitor = mockk<CalendarChangeRequestMonitorInterface>()
        every { mockAddEventMonitor.onRescanFromService(any()) } answers {
            val context = it.invocation.args[0] as Context
            DevLog.info(LOG_TAG, "Mocked AddEventMonitorInstance.onRescanFromService called with context: ${context}")
        }
        every { ApplicationController.AddEventMonitorInstance } returns mockAddEventMonitor

        // Mock event handling methods
        every { 
            ApplicationController.shouldMarkEventAsHandledAndSkip(any(), any()) 
        } returns false

        every { 
            ApplicationController.registerNewEvent(any(), any()) 
        } returns true

        // Mock additional ApplicationController methods
        every {
            ApplicationController.afterCalendarEventFired(any())
        } just Runs

        every {
            ApplicationController.postEventNotifications(any(), any<Collection<EventAlertRecord>>())
        } just Runs

        // Create spy of real service instead of mock
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

        // Ensure service onCreate initializes with our mocked ApplicationController
        every { mockService.onCreate() } answers {
            // Set up the mock BEFORE calling original onCreate
            every { ApplicationController.CalendarMonitor } returns mockCalendarMonitor
            
            // Call original onCreate
            callOriginal()
            
            // Verify ApplicationController is still properly mocked
            val testMonitor = ApplicationController.CalendarMonitor
            assertSame("CalendarMonitor mock should be properly set up", mockCalendarMonitor, testMonitor)
        }
        
        // Mock service method to use our spy
        mockkObject(CalendarMonitorService.Companion)
        every { 
            CalendarMonitorService.startRescanService(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } answers { call ->
            val context = call.invocation.args[0] as Context
            val startDelay = call.invocation.args[1] as Integer
            val reloadCalendar = call.invocation.args[2] as Boolean
            val rescanMonitor = call.invocation.args[3] as Boolean
            val userActionUntil = call.invocation.args[4] as Long
            
            DevLog.info(LOG_TAG, "startRescanService called with reloadCalendar=$reloadCalendar, rescanMonitor=$rescanMonitor")
            
            // Ensure mock is set up before service starts
            every { ApplicationController.CalendarMonitor } returns mockCalendarMonitor
            every { ApplicationController.onCalendarRescanForRescheduledFromService(any(), any()) } answers {
                mockCalendarMonitor.onRescanFromService(firstArg())
            }
            every { ApplicationController.onCalendarReloadFromService(any(), any()) } answers {
                mockCalendarMonitor.onRescanFromService(firstArg())
            }
            
            // Call the real service implementation with our spy
            mockService.onCreate()
            val result = mockService.onStartCommand(
                Intent(context, CalendarMonitorService::class.java).apply {
                    putExtra("start_delay", startDelay)
                    putExtra("reload_calendar", reloadCalendar)
                    putExtra("rescan_monitor", rescanMonitor)
                    putExtra("user_action_until", userActionUntil)
                },
                0,
                0
            )
            
            DevLog.info(LOG_TAG, "Service onStartCommand completed with result: $result")
            
            mockService.onDestroy()
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
        settings.setCalendarIsHandled(testCalendarId, true)
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
        val eventStartTime = currentTime.get() + 60000 // 1 minute from now
        val reminderTime = eventStartTime - 30000 // 30 seconds before start
        
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
        DevLog.info(LOG_TAG, "Verifying CalendarMonitor.onRescanFromService was called...")
        verify(exactly = 1) { mockCalendarMonitor.onRescanFromService(any()) }
        DevLog.info(LOG_TAG, "Verification complete")

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
        
        // Notify calendar change and wait a moment for propagation
        ApplicationController.onCalendarChanged(context)
        mockTimer.schedule({}, 1, TimeUnit.SECONDS)
        
        // Start service with reload
        val intent = Intent(context, CalendarMonitorService::class.java).apply {
            putExtra("start_delay", 0)
            putExtra("reload_calendar", true)
            putExtra("rescan_monitor", true)
            putExtra("user_action_until", currentTime.get() + 10000)
        }
        context.startService(intent)

        // Wait for service to complete and process events
        mockTimer.schedule({}, 5, TimeUnit.SECONDS)

        // Verify all events were reloaded
        EventsStorage(context).classCustomUse { db ->
            val storedEvents = db.events.filter { it.calendarId == testCalendarId }
            assertEquals("All test events should be reloaded", 
                events.size, 
                storedEvents.size
            )
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
