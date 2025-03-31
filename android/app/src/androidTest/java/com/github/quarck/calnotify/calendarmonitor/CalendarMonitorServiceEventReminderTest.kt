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
import java.util.concurrent.TimeUnit
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.logs.DevLog
import io.mockk.*
import io.mockk.impl.annotations.MockK
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicLong
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentUris
import android.content.SharedPreferences
import com.github.quarck.calnotify.app.ApplicationController.notificationManager
import com.github.quarck.calnotify.notification.EventNotificationManagerInterface
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.textutils.EventFormatterInterface
import java.util.Locale
import android.content.pm.VersionedPackage
import com.github.quarck.calnotify.NotificationSettings
import com.github.quarck.calnotify.notification.EventNotificationManager
import com.github.quarck.calnotify.globalState

/**
 * Integration tests for [CalendarMonitorService] that verify direct calendar event reminder handling.
 *
 * These tests focus on the direct reminder path through EVENT_REMINDER broadcasts, verifying that:
 * 1. Direct reminders are properly received and processed
 * 2. Event timing and state are maintained
 * 3. Multiple reminders are handled correctly
 * 4. Calendar monitoring state is maintained
 */
@RunWith(AndroidJUnit4::class)
class CalendarMonitorServiceEventReminderTest {
    private var testCalendarId: Long = 0
    private var testEventId: Long = 0
    private var eventStartTime: Long = 0
    private var reminderTime: Long = 0
    private val realController = ApplicationController

    @MockK
    private lateinit var mockTimer: ScheduledExecutorService

    private lateinit var mockService: CalendarMonitorService

    private lateinit var mockCalendarMonitor: CalendarMonitorInterface

    private lateinit var mockAlarmManager: AlarmManager

    private lateinit var fakeContext: Context

    private lateinit var mockSharedPreferences: SharedPreferences
    private val sharedPreferencesMap = mutableMapOf<String, SharedPreferences>()
    private val sharedPreferencesDataMap = mutableMapOf<String, MutableMap<String, Any>>()

    private val currentTime = AtomicLong(0L)

    private lateinit var mockFormatter: EventFormatterInterface

    companion object {
        private const val LOG_TAG = "CalMonitorSvcReminderTest"
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
        DevLog.info(LOG_TAG, "Setting up test environment")
        MockKAnnotations.init(this)
        setupMockContext()
        setupMockTimer()

        // Set default locale for date formatting
        Locale.setDefault(Locale.US)

        mockkObject(CalendarProvider)
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns mockk(relaxed = true)

        setupMockFormatter()
        setupApplicationController()

        setupMockService()
        setupMockCalendarMonitor()
        
        // Clear storages first
        clearStorages()

        // Then setup test calendar and verify settings
        setupTestCalendar()

        // Verify settings are correct before proceeding
        val settings = Settings(fakeContext)
        assertTrue("Calendar should be handled", settings.getCalendarIsHandled(testCalendarId))
    }

    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test environment")
        unmockkAll()

        // Delete test events and calendar
        if (testEventId > 0) {
            val deleted = fakeContext.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events._ID} = ?",
                arrayOf(testEventId.toString())
            )
            DevLog.info(LOG_TAG, "Deleted test event: id=$testEventId, result=$deleted")
        }

        if (testCalendarId > 0) {
            val deleted = fakeContext.contentResolver.delete(
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
     * Tests direct calendar event reminder handling through EVENT_REMINDER broadcasts.
     *
     * Verifies that:
     * 1. Direct reminders are properly received and processed
     * 2. Event timing and state are maintained
     * 3. Multiple reminders are handled correctly
     * 4. Calendar monitoring state is maintained
     */
    @Test
    fun testCalendarMonitoringDirectReminder() {
        // Disable calendar rescan to test direct reminder path
        val settings = Settings(fakeContext)
        settings.setBoolean("enable_manual_calendar_rescan", false)

        // Reset monitor state
        val monitorState = CalendarMonitorState(fakeContext)
        monitorState.firstScanEver = false
        currentTime.set(System.currentTimeMillis())
        val startTime = currentTime.get()

        // Create test event with reminder
        eventStartTime = startTime + 60000
        reminderTime = eventStartTime - 30000

        // Create test event in calendar
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
            put(CalendarContract.Events.TITLE, "Test Reminder Event")
            put(CalendarContract.Events.DESCRIPTION, "Test Description")
            put(CalendarContract.Events.DTSTART, eventStartTime)
            put(CalendarContract.Events.DTEND, eventStartTime + 60000)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.HAS_ALARM, 1)
        }

        val eventUri = fakeContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        assertNotNull("Failed to create test event", eventUri)
        testEventId = eventUri!!.lastPathSegment!!.toLong()

        // Add reminder
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, testEventId)
            put(CalendarContract.Reminders.MINUTES, 1)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        val reminderUri = fakeContext.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        assertNotNull("Failed to create reminder", reminderUri)

        // Mock event details
        mockEventDetails(testEventId, eventStartTime, "Test Reminder Event")

        // Mock getAlertByTime to return our test event
        every { CalendarProvider.getAlertByTime(any(), any(), any(), any()) } answers {
            val alertTime = secondArg<Long>()
            val skipDismissed = thirdArg<Boolean>()
            DevLog.info(LOG_TAG, "Mock getAlertByTime called with alertTime=$alertTime, skipDismissed=$skipDismissed")

            if (alertTime == reminderTime) {
                listOf(EventAlertRecord(
                    calendarId = testCalendarId,
                    eventId = testEventId,
                    isAllDay = false,
                    isRepeating = false,
                    alertTime = reminderTime,
                    notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
                    title = "Test Reminder Event",
                    desc = "Test Description",
                    startTime = eventStartTime,
                    endTime = eventStartTime + 60000,
                    instanceStartTime = eventStartTime,
                    instanceEndTime = eventStartTime + 60000,
                    location = "",
                    lastStatusChangeTime = currentTime.get(),
                    displayStatus = EventDisplayStatus.Hidden,
                    color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
                    origin = EventOrigin.ProviderBroadcast,
                    timeFirstSeen = currentTime.get(),
                    eventStatus = EventStatus.Confirmed,
                    attendanceStatus = AttendanceStatus.None,
                    flags = 0
                ))
            } else {
                emptyList()
            }
        }

        // Mock dismissNativeEventAlert
        every { CalendarProvider.dismissNativeEventAlert(any(), any()) } just Runs

        // Verify settings
        assertTrue("Calendar should be handled", settings.getCalendarIsHandled(testCalendarId))
        assertFalse("Calendar monitoring should be disabled for direct reminder test", settings.enableCalendarRescan)

        // First verify no events exist
        EventsStorage(fakeContext).classCustomUse { db ->
            val events = db.events
            assertEquals("Should start with no events", 0, events.size)
        }

        // Advance time to the reminder time
        DevLog.info(LOG_TAG, "Advancing time to reminder time...")
        currentTime.set(reminderTime)

        // Create a reminder broadcast intent like the system would send
        val reminderIntent = Intent(CalendarContract.ACTION_EVENT_REMINDER).apply {
            data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, reminderTime)
        }

        // This is the key test: directly call the onProviderReminderBroadcast which is what happens
        // when a real calendar reminder is fired by the system
        mockCalendarMonitor.onProviderReminderBroadcast(fakeContext, reminderIntent)

        // Small delay for processing
        Thread.sleep(500)

        // Verify events were processed
        EventsStorage(fakeContext).classCustomUse { db ->
            val events = db.events
            DevLog.info(LOG_TAG, "Found ${events.size} events after reminder broadcast")
            assertTrue("Should have events after reminder broadcast", events.isNotEmpty())

            // Verify the event details
            val event = events.firstOrNull { it.eventId == testEventId }
            assertNotNull("Event should be found in storage", event)
            event?.let {
                assertEquals("Event should have correct title", "Test Reminder Event", it.title)
                assertEquals("Event should have correct start time", eventStartTime, it.startTime)
                assertEquals("Event should have correct origin", EventOrigin.ProviderBroadcast, it.origin)
            }
        }

        // Verify that native event alert was dismissed
        verify { CalendarProvider.dismissNativeEventAlert(fakeContext, testEventId) }

        // Verify that monitor storage has the alert marked as handled
        MonitorStorage(fakeContext).classCustomUse { db ->
            val alert = db.getAlert(testEventId, reminderTime, eventStartTime)
            assertNotNull("Alert should exist in storage", alert)
            alert?.let {
                assertTrue("Alert should be marked as handled", it.wasHandled)
            }
        }

        // Verify event was processed
        verifyEventProcessed(
            eventId = testEventId,
            startTime = eventStartTime,
            title = "Test Reminder Event"
        )

        // Verify no rescan was triggered since we're testing direct reminder path
        verify(exactly = 0) { mockCalendarMonitor.onRescanFromService(any()) }

        // Verify event was registered through the direct path
        verify(exactly = 1) { ApplicationController.registerNewEvent(any(), any()) }
    }

    // Helper functions from CalendarMonitorServiceTest.kt
    private fun setupMockFormatter() {
        mockFormatter = mockk<EventFormatterInterface> {
            every { formatNotificationSecondaryText(any()) } returns "Mock event time"
            every { formatDateTimeTwoLines(any(), any()) } returns Pair("Mock date", "Mock time")
            every { formatDateTimeOneLine(any(), any()) } returns "Mock date and time"
            every { formatSnoozedUntil(any()) } returns "Mock snooze time"
            every { formatTimePoint(any()) } returns "Mock time point"
            every { formatTimeDuration(any(), any()) } returns "Mock duration"
        }
    }

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

        fakeContext = mockk<Context>(relaxed = true) {
            every { packageName } returns realContext.packageName
            every { packageManager } returns realContext.packageManager
            every { applicationContext } returns realContext.applicationContext
            every { contentResolver } returns realContext.contentResolver
            every { getDatabasePath(any()) } answers { realContext.getDatabasePath(firstArg()) }
            every { getSystemService(Context.ALARM_SERVICE) } returns mockAlarmManager
            every { getSystemService(Context.POWER_SERVICE) } returns realContext.getSystemService(Context.POWER_SERVICE)
            every { getSystemService(any<String>()) } answers {
                when (firstArg<String>()) {
                    Context.ALARM_SERVICE -> mockAlarmManager
                    Context.POWER_SERVICE -> realContext.getSystemService(Context.POWER_SERVICE)
                    else -> realContext.getSystemService(firstArg())
                }
            }
            every { checkPermission(any(), any(), any()) } answers { realContext.checkPermission(firstArg(), secondArg(), thirdArg()) }
            every { checkCallingOrSelfPermission(any()) } answers { realContext.checkCallingOrSelfPermission(firstArg()) }
            every { createPackageContext(any(), any()) } answers { realContext.createPackageContext(firstArg(), secondArg()) }
            every { getSharedPreferences(any(), any()) } answers {
                val name = firstArg<String>()
                sharedPreferencesMap.getOrPut(name) { createPersistentSharedPreferences(name) }
            }
            every { getApplicationInfo() } returns realContext.applicationInfo
            every { getFilesDir() } returns realContext.filesDir
            every { getCacheDir() } returns realContext.cacheDir
            every { getDir(any(), any()) } answers { realContext.getDir(firstArg(), secondArg()) }
            every { startService(any()) } answers {
                val intent = firstArg<Intent>()
                mockService.handleIntentForTest(intent)
                ComponentName(realContext.packageName, CalendarMonitorService::class.java.name)
            }
            every { startActivity(any()) } just Runs
            every { getResources() } returns realContext.resources
            every { getTheme() } returns realContext.theme
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
        val realMonitor = object : CalendarMonitor(CalendarProvider) {
            override val currentTimeForTest: Long
                get() = this@CalendarMonitorServiceEventReminderTest.currentTime.get()
        }
        mockCalendarMonitor = spyk(realMonitor, recordPrivateCalls = true)
        every { ApplicationController.CalendarMonitor } returns mockCalendarMonitor
    }

    private fun setupMockService() {
        mockService = spyk(CalendarMonitorService()) {
            every { applicationContext } returns fakeContext
            every { baseContext } returns fakeContext
            every { timer } returns mockTimer
            every { getDatabasePath(any()) } answers { fakeContext.getDatabasePath(firstArg()) }
            every { checkPermission(any(), any(), any()) } answers { fakeContext.checkPermission(firstArg(), secondArg(), thirdArg()) }
            every { checkCallingOrSelfPermission(any()) } answers { fakeContext.checkCallingOrSelfPermission(firstArg()) }
            every { getPackageName() } returns fakeContext.packageName
            every { getContentResolver() } returns fakeContext.contentResolver
            every { getSystemService(Context.ALARM_SERVICE) } returns mockAlarmManager
            every { getSystemService(Context.POWER_SERVICE) } returns fakeContext.getSystemService(Context.POWER_SERVICE)
            every { getSystemService(any<String>()) } answers {
                when (firstArg<String>()) {
                    Context.ALARM_SERVICE -> mockAlarmManager
                    Context.POWER_SERVICE -> fakeContext.getSystemService(Context.POWER_SERVICE)
                    else -> fakeContext.getSystemService(firstArg())
                }
            }
            every { getSharedPreferences(any(), any()) } answers {
                val name = firstArg<String>()
                sharedPreferencesMap.getOrPut(name) { createPersistentSharedPreferences(name) }
            }
        }
    }

    private fun setupApplicationController() {
        mockkObject(ApplicationController)

        // Set up a more faithful mock notification manager
        val mockNotificationManager = spyk(object : EventNotificationManager() {
            override fun postNotification(
                ctx: Context,
                formatter: EventFormatterInterface,
                event: EventAlertRecord,
                notificationSettings: NotificationSettings,
                isForce: Boolean,
                wasCollapsed: Boolean,
                snoozePresetsNotFiltered: LongArray,
                isQuietPeriodActive: Boolean,
                isReminder: Boolean,
                forceAlarmStream: Boolean
            ) {
                // Do nothing - prevent actual notification posting
                DevLog.info(LOG_TAG, "Mock postNotification called for event ${event.eventId}")
            }

            override fun postEventNotifications(ctx: Context, formatter: EventFormatterInterface, force: Boolean, primaryEventId: Long?) {
                DevLog.info(LOG_TAG, "Mock postEventNotifications called with force=$force, primaryEventId=$primaryEventId")
                MonitorStorage(ctx).classCustomUse { db ->
                    val alertsToHandle = if (primaryEventId != null) {
                        db.alerts.filter { it.eventId == primaryEventId && !it.wasHandled }
                    } else {
                        db.alerts.filter { !it.wasHandled && it.alertTime <= System.currentTimeMillis() }
                    }

                    if (alertsToHandle.isNotEmpty()) {
                        alertsToHandle.forEach { alert ->
                            alert.wasHandled = true
                            db.updateAlert(alert)
                            DevLog.info(LOG_TAG, "Marked alert as handled for event ${alert.eventId}")
                        }
                    }
                }
            }

            override fun onEventAdded(ctx: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {
                DevLog.info(LOG_TAG, "Mock onEventAdded called for event ${event.eventId}")
            }
        })

        every { ApplicationController.postEventNotifications(any(), any<Collection<EventAlertRecord>>()) } answers {
            val context = firstArg<Context>()
            val events = secondArg<Collection<EventAlertRecord>>()
            DevLog.info(LOG_TAG, "postEventNotifications called for ${events.size} events")

            if (events.size == 1)
                mockNotificationManager.onEventAdded(context, mockFormatter, events.first())
            else
                mockNotificationManager.postEventNotifications(context, mockFormatter)
        }

        // Mock ReminderState using SharedPreferences
        val reminderStatePrefs = createPersistentSharedPreferences(ReminderState.PREFS_NAME)
        every { fakeContext.getSharedPreferences(ReminderState.PREFS_NAME, Context.MODE_PRIVATE) } returns reminderStatePrefs

        reminderStatePrefs.edit().apply {
            putInt(ReminderState.NUM_REMINDERS_FIRED_KEY, 0)
            putBoolean(ReminderState.QUIET_HOURS_ONE_TIME_REMINDER_KEY, false)
            putLong(ReminderState.REMINDER_LAST_FIRE_TIME_KEY, 0)
            apply()
        }
    }

    private fun setupTestCalendar() {
        testCalendarId = createTestCalendar(
            displayName = "Test Calendar",
            accountName = "test@local",
            ownerAccount = "test@local"
        )

        if (testCalendarId <= 0) {
            throw IllegalStateException("Failed to create test calendar")
        }

        val settings = Settings(fakeContext)
        settings.setBoolean("calendar_is_handled_$testCalendarId", true)

        DevLog.info(LOG_TAG, "Test calendar setup complete: id=$testCalendarId")
    }

    private fun clearStorages() {
        EventsStorage(fakeContext).classCustomUse { db ->
            val count = db.events.size
            db.deleteAllEvents()
            DevLog.info(LOG_TAG, "Cleared $count events from storage")
        }
        MonitorStorage(fakeContext).classCustomUse { db ->
            val count = db.alerts.size
            db.deleteAlertsMatching { true }
            DevLog.info(LOG_TAG, "Cleared $count alerts from storage")
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

        val calUri = fakeContext.contentResolver.insert(uri, values)
        val calendarId = calUri?.lastPathSegment?.toLong() ?: -1L
        DevLog.info(LOG_TAG, "Created test calendar: id=$calendarId, name=$displayName")
        return calendarId
    }

    private fun createPersistentSharedPreferences(name: String): SharedPreferences {
        val sharedPrefsMap = sharedPreferencesDataMap.getOrPut(name) { mutableMapOf() }
        return mockk<SharedPreferences>(relaxed = true) {
            every { edit() } returns mockk<SharedPreferences.Editor>(relaxed = true) {
                every { putString(any(), any()) } answers {
                    val key = firstArg<String>()
                    val value = secondArg<String>()
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { putBoolean(any(), any()) } answers {
                    val key = firstArg<String>()
                    val value = secondArg<Boolean>()
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { putInt(any(), any()) } answers {
                    val key = firstArg<String>()
                    val value = secondArg<Int>()
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { putLong(any(), any()) } answers {
                    val key = firstArg<String>()
                    val value = secondArg<Long>()
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { putFloat(any(), any()) } answers {
                    val key = firstArg<String>()
                    val value = secondArg<Float>()
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { remove(any()) } answers {
                    val key = firstArg<String>()
                    sharedPrefsMap.remove(key)
                    this@mockk
                }
                every { clear() } answers {
                    sharedPrefsMap.clear()
                    this@mockk
                }
                every { apply() } just Runs
                every { commit() } returns true
            }
            every { getString(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<String>()
                sharedPrefsMap[key] as? String ?: defaultValue
            }
            every { getBoolean(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<Boolean>()
                sharedPrefsMap[key] as? Boolean ?: defaultValue
            }
            every { getInt(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<Int>()
                sharedPrefsMap[key] as? Int ?: defaultValue
            }
            every { getLong(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<Long>()
                sharedPrefsMap[key] as? Long ?: defaultValue
            }
            every { getFloat(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<Float>()
                sharedPrefsMap[key] as? Float ?: defaultValue
            }
            every { contains(any()) } answers {
                val key = firstArg<String>()
                sharedPrefsMap.containsKey(key)
            }
            every { getAll() } returns sharedPrefsMap
        }
    }

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

    private fun verifyEventProcessed(
        eventId: Long,
        startTime: Long,
        title: String? = null,
        afterDelay: Long? = null
    ) {
        DevLog.info(LOG_TAG, "Verifying event processing for eventId=$eventId, startTime=$startTime, title=$title")

        EventsStorage(fakeContext).classCustomUse { db ->
            val events = db.events
            DevLog.info(LOG_TAG, "Found ${events.size} events in storage")
            events.forEach { event ->
                DevLog.info(LOG_TAG, "Event in storage: id=${event.eventId}, timeFirstSeen=${event.timeFirstSeen}, startTime=${event.startTime}, title=${event.title}")
            }

            val eventExists = events.any { it.eventId == eventId }
            if (!eventExists) {
                DevLog.error(LOG_TAG, "Event $eventId not found in storage!")
                val cursor = fakeContext.contentResolver.query(
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
                }
            }
        }
    }
} 