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
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.logs.DevLog
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class CalendarMonitorServiceTest {
    private lateinit var context: Context
    private var testCalendarId: Long = -1
    private var testEventId: Long = -1
    private lateinit var mockTimer: ScheduledExecutorService
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

    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Create mock timer
        mockTimer = mock(ScheduledExecutorService::class.java)
        val mockFuture = mock(ScheduledFuture::class.java)
        `when`(mockTimer.schedule(any(), anyLong(), any())).thenAnswer { invocation ->
            val delay = invocation.getArgument<Long>(1)
            val unit = invocation.getArgument<TimeUnit>(2)
            val task = invocation.getArgument<Runnable>(0)
            currentTime.addAndGet(unit.toMillis(delay))
            task.run()
            mockFuture
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
        ApplicationController.onCalendarChanged(context)
        mockTimer.schedule({}, 1, TimeUnit.SECONDS)

        // Create a mock service and configure it to use our mock timer
        val service = mock(CalendarMonitorService::class.java)
        `when`(service.timer).thenReturn(mockTimer)

        // Start the monitor service directly
        CalendarMonitorService.startRescanService(
            context = context,
            startDelay = 0,
            reloadCalendar = true,
            rescanMonitor = true,
            userActionUntil = currentTime.get() + 10000
        )

        // Wait for service to complete and process events
        mockTimer.schedule({}, 5, TimeUnit.SECONDS)

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
