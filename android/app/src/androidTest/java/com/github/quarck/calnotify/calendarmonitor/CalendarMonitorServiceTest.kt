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
import com.github.quarck.calnotify.broadcastreceivers.ManualEventAlarmBroadcastReceiver
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

  // Track last timer broadcast time for tests
  private var lastTimerBroadcastReceived: Long? = null

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

  private fun setupMockFormatter(){
    // Create mock EventFormatter
    mockFormatter = mockk<EventFormatterInterface> {
      every { formatNotificationSecondaryText(any()) } returns "Mock event time"
      every { formatDateTimeTwoLines(any(), any()) } returns Pair("Mock date", "Mock time")
      every { formatDateTimeOneLine(any(), any()) } returns "Mock date and time"
      every { formatSnoozedUntil(any()) } returns "Mock snooze time"
      every { formatTimePoint(any()) } returns "Mock time point"
      every { formatTimeDuration(any(), any()) } returns "Mock duration"
    }
  }

  // Helper functions for setting up mocks and test data
  private fun createPersistentSharedPreferences(name: String): SharedPreferences {
    val sharedPrefsMap = sharedPreferencesDataMap.getOrPut(name) { mutableMapOf() }
    return mockk<SharedPreferences>(relaxed = true) {
      every { edit() } returns mockk<SharedPreferences.Editor>(relaxed = true) {
        every { putString(any(), any()) } answers {
          val key = firstArg<String>()
          val value = secondArg<String>()
          DevLog.info(LOG_TAG, "SharedPreferences[$name].putString($key, $value)")
          sharedPrefsMap[key] = value
          this@mockk
        }
        every { putBoolean(any(), any()) } answers {
          val key = firstArg<String>()
          val value = secondArg<Boolean>()
          DevLog.info(LOG_TAG, "SharedPreferences[$name].putBoolean($key, $value)")
          sharedPrefsMap[key] = value
          this@mockk
        }
        every { putInt(any(), any()) } answers {
          val key = firstArg<String>()
          val value = secondArg<Int>()
          DevLog.info(LOG_TAG, "SharedPreferences[$name].putInt($key, $value)")
          sharedPrefsMap[key] = value
          this@mockk
        }
        every { putLong(any(), any()) } answers {
          val key = firstArg<String>()
          val value = secondArg<Long>()
          DevLog.info(LOG_TAG, "SharedPreferences[$name].putLong($key, $value)")
          sharedPrefsMap[key] = value
          this@mockk
        }
        every { putFloat(any(), any()) } answers {
          val key = firstArg<String>()
          val value = secondArg<Float>()
          DevLog.info(LOG_TAG, "SharedPreferences[$name].putFloat($key, $value)")
          sharedPrefsMap[key] = value
          this@mockk
        }
        every { remove(any()) } answers {
          val key = firstArg<String>()
          DevLog.info(LOG_TAG, "SharedPreferences[$name].remove($key)")
          sharedPrefsMap.remove(key)
          this@mockk
        }
        every { clear() } answers {
          DevLog.info(LOG_TAG, "SharedPreferences[$name].clear()")
          sharedPrefsMap.clear()
          this@mockk
        }
        every { apply() } just Runs
        every { commit() } returns true
      }
      every { getString(any(), any()) } answers {
        val key = firstArg<String>()
        val defaultValue = secondArg<String>()
        val value = sharedPrefsMap[key] as? String ?: defaultValue
        DevLog.info(LOG_TAG, "SharedPreferences[$name].getString($key) = $value")
        value
      }
      every { getBoolean(any(), any()) } answers {
        val key = firstArg<String>()
        val defaultValue = secondArg<Boolean>()
        val value = sharedPrefsMap[key] as? Boolean ?: defaultValue
        DevLog.info(LOG_TAG, "SharedPreferences[$name].getBoolean($key) = $value")
        value
      }
      every { getInt(any(), any()) } answers {
        val key = firstArg<String>()
        val defaultValue = secondArg<Int>()
        val value = sharedPrefsMap[key] as? Int ?: defaultValue
        DevLog.info(LOG_TAG, "SharedPreferences[$name].getInt($key) = $value")
        value
      }
      every { getLong(any(), any()) } answers {
        val key = firstArg<String>()
        val defaultValue = secondArg<Long>()
        val value = sharedPrefsMap[key] as? Long ?: defaultValue
        DevLog.info(LOG_TAG, "SharedPreferences[$name].getLong($key) = $value")
        value
      }
      every { getFloat(any(), any()) } answers {
        val key = firstArg<String>()
        val defaultValue = secondArg<Float>()
        val value = sharedPrefsMap[key] as? Float ?: defaultValue
        DevLog.info(LOG_TAG, "SharedPreferences[$name].getFloat($key) = $value")
        value
      }
      every { contains(any()) } answers {
        val key = firstArg<String>()
        val value = sharedPrefsMap.containsKey(key)
        DevLog.info(LOG_TAG, "SharedPreferences[$name].contains($key) = $value")
        value
      }
      every { getAll() } returns sharedPrefsMap
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

    // Create mock package manager with enhanced functionality
    val mockPackageManager = mockk<android.content.pm.PackageManager> {
      every { resolveActivity(any(), any<Int>()) } answers {
        val intent = firstArg<Intent>()
        val flags = secondArg<Int>()
        realContext.packageManager.resolveActivity(intent, flags)
      }
      every { queryIntentActivities(any(), any<Int>()) } answers {
        val intent = firstArg<Intent>()
        val flags = secondArg<Int>()
        realContext.packageManager.queryIntentActivities(intent, flags)
      }
      every { getApplicationInfo(any<String>(), any<Int>()) } answers {
        val packageName = firstArg<String>()
        val flags = secondArg<Int>()
        realContext.packageManager.getApplicationInfo(packageName, flags)
      }
      @Suppress("DEPRECATION")
      every { getApplicationInfo(any<String>(), any<android.content.pm.PackageManager.ApplicationInfoFlags>()) } answers {
        val packageName = firstArg<String>()
        val flags = secondArg<android.content.pm.PackageManager.ApplicationInfoFlags>()
        realContext.packageManager.getApplicationInfo(packageName, flags)
      }
      every { getActivityInfo(any<ComponentName>(), any<Int>()) } answers {
        val component = firstArg<ComponentName>()
        val flags = secondArg<Int>()
        realContext.packageManager.getActivityInfo(component, flags)
      }
      @Suppress("DEPRECATION")
      every { getActivityInfo(any<ComponentName>(), any<android.content.pm.PackageManager.ComponentInfoFlags>()) } answers {
        val component = firstArg<ComponentName>()
        val flags = secondArg<android.content.pm.PackageManager.ComponentInfoFlags>()
        realContext.packageManager.getActivityInfo(component, flags)
      }
      every { getPackageInfo(any<String>(), any<Int>()) } answers {
        val packageName = firstArg<String>()
        val flags = secondArg<Int>()
        realContext.packageManager.getPackageInfo(packageName, flags)
      }
      @Suppress("DEPRECATION")
      every { getPackageInfo(any<String>(), any<android.content.pm.PackageManager.PackageInfoFlags>()) } answers {
        val packageName = firstArg<String>()
        val flags = secondArg<android.content.pm.PackageManager.PackageInfoFlags>()
        realContext.packageManager.getPackageInfo(packageName, flags)
      }
      every { getPackageInfo(any<VersionedPackage>(), any<Int>()) } answers {
        val versionedPackage = firstArg<VersionedPackage>()
        val flags = secondArg<Int>()
        realContext.packageManager.getPackageInfo(versionedPackage, flags)
      }
      @Suppress("DEPRECATION")
      every { getPackageInfo(any<VersionedPackage>(), any<android.content.pm.PackageManager.PackageInfoFlags>()) } answers {
        val versionedPackage = firstArg<VersionedPackage>()
        val flags = secondArg<android.content.pm.PackageManager.PackageInfoFlags>()
        realContext.packageManager.getPackageInfo(versionedPackage, flags)
      }
    }

    fakeContext = mockk<Context>(relaxed = true)  {
      every { packageName } returns realContext.packageName
      every { packageManager } returns mockPackageManager
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
        DevLog.info(LOG_TAG, "Getting SharedPreferences for name: $name")
        sharedPreferencesMap.getOrPut(name) { createPersistentSharedPreferences(name) }
      }
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
        DevLog.info(LOG_TAG, "Executing scheduled task")
        nextTask.run()
      }
      mockk(relaxed = true)
    }
  }

  private fun setupMockCalendarMonitor() {
    val realMonitor = object : CalendarMonitor(CalendarProvider) {
      override val currentTimeForTest: Long
        get() = this@CalendarMonitorServiceTest.currentTime.get()
    }
    mockCalendarMonitor = spyk(realMonitor, recordPrivateCalls = true)

    // Set up mocks with simpler approach to avoid recursion
    every { mockCalendarMonitor.onRescanFromService(any()) } answers {
      DevLog.info(LOG_TAG, "mock onRescanFromService called")
      // Call original without additional mocking that might cause recursion
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

    // Add logging for handleIntentForTest
    every { mockService.handleIntentForTest(any()) } answers {
      val intent = firstArg<Intent>()
      DevLog.info(LOG_TAG, "handleIntentForTest called with intent: action=${intent.action}, extras=${intent.extras}")
      callOriginal()
    }
  }

  private fun setupApplicationController() {
    // Create a spy on the real controller
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

        // Only mark events as handled when explicitly processing alerts via alarm broadcast
        // This replicates the real workflow where:
        // 1. Events are discovered in monitor scan
        // 2. Later when alarm fires, events are processed and marked as handled

        // Only mark alerts as handled if this is triggered as part of alarm handling
        if (primaryEventId != null || lastTimerBroadcastReceived != null) {
          MonitorStorage(ctx).classCustomUse { db ->
            val alertsToHandle = if (primaryEventId != null) {
              db.alerts.filter { it.eventId == primaryEventId && !it.wasHandled }
            } else {
              // Handle all pending alerts if this is a broadcast-triggered notification
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
      }

      override fun onEventAdded(ctx: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {
        DevLog.info(LOG_TAG, "Mock onEventAdded called for event ${event.eventId}")

        // Don't automatically mark alerts as handled here
        // In the real app, alerts get marked as handled when the notification is later processed
        // after the alarm triggers, not immediately when discovered
      }
    })

    // Override post event notifications with our spied version
    every { ApplicationController.postEventNotifications(any(), any<Collection<EventAlertRecord>>()) } answers {
      val context = firstArg<Context>()
      val events = secondArg<Collection<EventAlertRecord>>()
      DevLog.info(LOG_TAG, "postEventNotifications called for ${events.size} events")

      if (events.size == 1)
        mockNotificationManager.onEventAdded(context, mockFormatter, events.first())
      else
        mockNotificationManager.postEventNotifications(context, mockFormatter)
    }

    // Mock ReminderState using SharedPreferences like CalendarMonitorState
    val reminderStatePrefs = createPersistentSharedPreferences(ReminderState.PREFS_NAME)
    every { fakeContext.getSharedPreferences(ReminderState.PREFS_NAME, Context.MODE_PRIVATE) } returns reminderStatePrefs

    // Set only the essential initial values for ReminderState that are needed for testCalendarReload
    reminderStatePrefs.edit().apply {
      putInt(ReminderState.NUM_REMINDERS_FIRED_KEY, 0)
      putBoolean(ReminderState.QUIET_HOURS_ONE_TIME_REMINDER_KEY, false)
      putLong(ReminderState.REMINDER_LAST_FIRE_TIME_KEY, 0)
      apply()
    }


    // Mock CalendarProvider.getAlertByTime
    every { CalendarProvider.getAlertByTime(any(), any(), any(), any()) } answers {
      val context = firstArg<Context>()
      val alertTime = secondArg<Long>()
      val skipDismissed = thirdArg<Boolean>()

      DevLog.info(LOG_TAG, "Mock getAlertByTime called with alertTime=$alertTime, skipDismissed=$skipDismissed")

      // Return our test event with proper IDs and times
      listOf(EventAlertRecord(
        calendarId = testCalendarId,
        eventId = testEventId, // Use the actual test event ID instead of 0
        isAllDay = false,
        isRepeating = false,
        alertTime = alertTime,
        notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM, // Use proper notification ID
        title = "Test Monitor Event",
        desc = "Test Description",
        startTime = eventStartTime,
        endTime = eventStartTime + 3600000, // 1 hour duration
        instanceStartTime = eventStartTime,
        instanceEndTime = eventStartTime + 3600000,
        location = "",
        lastStatusChangeTime = currentTime.get(), // Use current test time
        displayStatus = EventDisplayStatus.Hidden,
        color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
        origin = EventOrigin.ProviderBroadcast,
        timeFirstSeen = currentTime.get(), // Use current test time
        eventStatus = EventStatus.Confirmed,
        attendanceStatus = AttendanceStatus.None,
        flags = 0
      ))
    }

    // Mock CalendarProvider.getAlertByEventIdAndTime
    every { CalendarProvider.getAlertByEventIdAndTime(any(), any(), any()) } answers {
      val context = firstArg<Context>()
      val eventId = secondArg<Long>()
      val alertTime = thirdArg<Long>()

      DevLog.info(LOG_TAG, "Mock getAlertByEventIdAndTime called with eventId=$eventId, alertTime=$alertTime")

      if (eventId == testEventId && alertTime == reminderTime) {
        EventAlertRecord(
          calendarId = testCalendarId,
          eventId = testEventId,
          isAllDay = false,
          isRepeating = false,
          alertTime = reminderTime,
          notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
          title = "Test Monitor Event",
          desc = "Test Description",
          startTime = eventStartTime,
          endTime = eventStartTime + 3600000,
          instanceStartTime = eventStartTime,
          instanceEndTime = eventStartTime + 3600000,
          location = "",
          lastStatusChangeTime = currentTime.get(),
          displayStatus = EventDisplayStatus.Hidden,
          color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
          origin = EventOrigin.ProviderBroadcast,
          timeFirstSeen = currentTime.get(),
          eventStatus = EventStatus.Confirmed,
          attendanceStatus = AttendanceStatus.None,
          flags = 0
        )
      } else {
        null
      }
    }

    // Spy on shouldMarkEventAsHandledAndSkip
    every { ApplicationController.shouldMarkEventAsHandledAndSkip(any(), any()) } answers {
      val context = firstArg<Context>()
      val event = secondArg<EventAlertRecord>()
      DevLog.info(LOG_TAG, "Mock shouldMarkEventAsHandledAndSkip called for eventId=${event.eventId}, title=${event.title}, isAllDay=${event.isAllDay}, eventStatus=${event.eventStatus}, attendanceStatus=${event.attendanceStatus}")
      val result = callOriginal()
      DevLog.info(LOG_TAG, "Mock shouldMarkEventAsHandledAndSkip result=$result")
      result
    }

    // Spy on afterCalendarEventFired
    every { ApplicationController.afterCalendarEventFired(any()) } answers {
      val context = firstArg<Context>()
      DevLog.info(LOG_TAG, "Mock afterCalendarEventFired called for context=${context}")
      callOriginal()
    }


    every { ApplicationController.onCalendarReloadFromService(any(), any()) } answers {
      val stackTrace = Thread.currentThread().stackTrace
      val caller = if (stackTrace.size > 2) stackTrace[2].methodName else "unknown"
      val callerClass = if (stackTrace.size > 2) stackTrace[2].className else "unknown"

      DevLog.info(LOG_TAG, "Mock onCalendarReloadFromService Reload attempt from: $callerClass.$caller")

      val userActionUntil = secondArg<Long>()
      DevLog.info(LOG_TAG, "Mock onCalendarReloadFromService called with userActionUntil=$userActionUntil")

      val res = callOriginal()
      DevLog.info(LOG_TAG, "Mock onCalendarReloadFromService completed")

      res
    }

    every { ApplicationController.onCalendarRescanForRescheduledFromService(any(), any()) } answers {
      val stackTrace = Thread.currentThread().stackTrace
      val caller = if (stackTrace.size > 2) stackTrace[2].methodName else "unknown"
      val callerClass = if (stackTrace.size > 2) stackTrace[2].className else "unknown"

      DevLog.info(LOG_TAG, "Mock onCalendarRescanForRescheduledFromService Rescan attempt from: $callerClass.$caller")

      val userActionUntil = secondArg<Long>()
      DevLog.info(LOG_TAG, "Mock onCalendarRescanForRescheduledFromService called with userActionUntil=$userActionUntil")
      callOriginal()
    }


  }



  private fun setupTestCalendar() {
    testCalendarId = createTestCalendar(
      displayName = "Test Calendar",
      accountName = "test@local",
      ownerAccount = "test@local"
    )

    // Only proceed if we got a valid calendar ID
    if (testCalendarId <= 0) {
      throw IllegalStateException("Failed to create test calendar")
    }

    // Enable calendar monitoring in settings
    val settings = Settings(fakeContext)
    settings.setBoolean("enable_manual_calendar_rescan", true)
    settings.setBoolean("calendar_is_handled_$testCalendarId", true)

    DevLog.info(LOG_TAG, "Test calendar setup complete: id=$testCalendarId")
    DevLog.info(LOG_TAG, "fakeContext Settings after setup: enable_manual_calendar_rescan=${settings.enableCalendarRescan}")
    DevLog.info(LOG_TAG, "fakeContext Settings after setup: calendar_is_handled_$testCalendarId=${settings.getCalendarIsHandled(testCalendarId)}")

    // Create initial test event
    val values = ContentValues().apply {
      put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
      put(CalendarContract.Events.TITLE, "Test Monitor Event")
      put(CalendarContract.Events.DESCRIPTION, "Test Description")
      put(CalendarContract.Events.DTSTART, System.currentTimeMillis() + 3600000) // 1 hour from now
      put(CalendarContract.Events.DTEND, System.currentTimeMillis() + 7200000)   // 2 hours from now
      put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
      put(CalendarContract.Events.HAS_ALARM, 1)
    }

    val eventUri = fakeContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    testEventId = eventUri?.lastPathSegment?.toLong() ?: throw IllegalStateException("Failed to create test event")
    eventStartTime = values.getAsLong(CalendarContract.Events.DTSTART)

    // Add reminder
    val reminderValues = ContentValues().apply {
      put(CalendarContract.Reminders.EVENT_ID, testEventId)
      put(CalendarContract.Reminders.MINUTES, 15)
      put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
    }
    fakeContext.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)

    DevLog.info(LOG_TAG, "Created test event: id=$testEventId, startTime=$eventStartTime")
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

  @Before
  fun setup() {
    DevLog.info(LOG_TAG, "Setting up test environment")
    MockKAnnotations.init(this)
    setupMockContext()
    setupMockTimer()

    // Mock the globalState extension property
    mockkStatic("com.github.quarck.calnotify.GlobalStateKt")
    every { any<Context>().globalState } answers { 
      mockk {
        every { lastTimerBroadcastReceived } returns this@CalendarMonitorServiceTest.lastTimerBroadcastReceived!!
        every { lastTimerBroadcastReceived = any() } answers {
          this@CalendarMonitorServiceTest.lastTimerBroadcastReceived = firstArg()
        }
      }
    }
    
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
    assertTrue("Calendar monitoring should be enabled", settings.enableCalendarRescan)
    assertTrue("Calendar should be handled", settings.getCalendarIsHandled(testCalendarId))

    DevLog.info(LOG_TAG, "Test environment setup complete")
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
   * Tests calendar monitoring through manual rescan triggered by PROVIDER_CHANGED.
   */
  @Test
  fun testCalendarMonitoringManualRescan() {
    // Reset monitor state and ensure firstScanEver is false
    val monitorState = CalendarMonitorState(fakeContext)
    monitorState.firstScanEver = false
    currentTime.set(System.currentTimeMillis())
    val startTime = currentTime.get() // Capture initial time

    // Calculate event times first
    eventStartTime = startTime + 60000 // 1 minute from start time
    reminderTime = eventStartTime - 30000 // 30 seconds before start
    testEventId = 1555 // Set a consistent test event ID

    DevLog.info(LOG_TAG, "Test starting with currentTime=$startTime, eventStartTime=$eventStartTime, reminderTime=$reminderTime")

    // Set up monitor state with proper timing
    monitorState.prevEventScanTo = startTime
    monitorState.prevEventFireFromScan = startTime
    monitorState.nextEventFireFromScan = reminderTime

    // Create test event in calendar with correct ID and times
    val values = ContentValues().apply {
      put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
      put(CalendarContract.Events.TITLE, "Test Monitor Event")
      put(CalendarContract.Events.DESCRIPTION, "Test Description")
      put(CalendarContract.Events.DTSTART, eventStartTime)
      put(CalendarContract.Events.DTEND, eventStartTime + 60000)
      put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
      put(CalendarContract.Events.HAS_ALARM, 1)
    }

    val eventUri = fakeContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    assertNotNull("Failed to create test event", eventUri)

    // Set up mock event reminders
    mockEventReminders(testEventId)

    // Set up mock event alerts to return our test alert
    every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
      val scanFrom = secondArg<Long>()
      val scanTo = thirdArg<Long>()
      DevLog.info(LOG_TAG, "Mock getEventAlertsForInstancesInRange called with scanFrom=$scanFrom, scanTo=$scanTo")
      DevLog.info(LOG_TAG, "Current reminderTime=$reminderTime")

      if (reminderTime in scanFrom..scanTo) {
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

    // Set up mock event details
    mockEventDetails(testEventId, eventStartTime, "Test Monitor Event")

    // Verify settings are correct
    val settings = Settings(fakeContext)
    assertTrue("Calendar should be handled", settings.getCalendarIsHandled(testCalendarId))
    assertTrue("Calendar monitoring should be enabled", settings.enableCalendarRescan)

    // First verify no alerts exist
    MonitorStorage(fakeContext).classCustomUse { db ->
      val alerts = db.alerts
      assertEquals("Should start with no alerts", 0, alerts.size)
    }

    // Trigger initial calendar scan
    notifyCalendarChangeAndWait()

    // Verify alerts were added but not handled
    MonitorStorage(fakeContext).classCustomUse { db ->
      val alerts = db.alerts
      DevLog.info(LOG_TAG, "Found ${alerts.size} alerts after scan")
      alerts.forEach { alert ->
        DevLog.info(LOG_TAG, "Alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
      }
      assertTrue("Should have alerts after scan", alerts.isNotEmpty())
      assertFalse("Alerts should not be handled yet", alerts.any { it.wasHandled })
    }

    // Advance time past the reminder time
    DevLog.info(LOG_TAG, "Advancing time past reminder time...")
    val advanceAmount = reminderTime - startTime + Consts.ALARM_THRESHOLD
    advanceTimer(advanceAmount)

    val currentTimeAfterAdvance = currentTime.get()
    DevLog.info(LOG_TAG, "Current time after advance: $currentTimeAfterAdvance")
    DevLog.info(LOG_TAG, "Time check: currentTime=$currentTimeAfterAdvance, nextEventFireFromScan=${monitorState.nextEventFireFromScan}, " +
      "threshold=${currentTimeAfterAdvance + Consts.ALARM_THRESHOLD}")

    // Verify timing condition will be met
    assertTrue("Current time + threshold should be greater than nextEventFireFromScan",
      currentTimeAfterAdvance + Consts.ALARM_THRESHOLD > monitorState.nextEventFireFromScan)

    // Set the last timer broadcast received to indicate an alarm happened
    lastTimerBroadcastReceived = currentTimeAfterAdvance

    // Use mocked extension property to provide global state

    // First trigger the alarm broadcast receiver
    val alarmIntent = Intent(fakeContext, ManualEventAlarmBroadcastReceiver::class.java).apply {
      action = "com.github.quarck.calnotify.MANUAL_EVENT_ALARM"
      putExtra("alert_time", reminderTime)
    }
    mockCalendarMonitor.onAlarmBroadcast(fakeContext, alarmIntent)

    // Then let the service handle the intent that would have been created by the broadcast receiver
    val serviceIntent = Intent(fakeContext, CalendarMonitorService::class.java).apply {
      putExtra("alert_time", reminderTime)
      putExtra("rescan_monitor", true)
      putExtra("reload_calendar", false) // Important: don't reload calendar on alarm
      putExtra("start_delay", 0L)
    }

    mockService.handleIntentForTest(serviceIntent)

    // Small delay for processing
    Thread.sleep(1000)

    // Verify alerts were handled
    MonitorStorage(fakeContext).classCustomUse { db ->
      val alerts = db.alerts
      DevLog.info(LOG_TAG, "Found ${alerts.size} alerts after processing")
      alerts.forEach { alert ->
        DevLog.info(LOG_TAG, "Alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
      }
      assertTrue("Should have alerts after processing", alerts.isNotEmpty())
      assertTrue("Alerts should be marked as handled", alerts.all { it.wasHandled })
    }

    // Verify event was processed
    verifyEventProcessed(
      eventId = testEventId,
      startTime = eventStartTime,
      title = "Test Monitor Event"
    )
  }

  /**
   * Tests calendar monitoring through direct EVENT_REMINDER broadcasts.
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

    // Setup mocks for calendar provider
    every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
      val scanFrom = secondArg<Long>()
      val scanTo = thirdArg<Long>()
      DevLog.info(LOG_TAG, "Mock getEventAlertsForInstancesInRange called with scanFrom=$scanFrom, scanTo=$scanTo")
      DevLog.info(LOG_TAG, "Current reminderTime=$reminderTime")

      if (reminderTime in scanFrom..scanTo) {
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

    // Mock event reminders
    mockEventReminders(testEventId)
    mockEventDetails(testEventId, eventStartTime, "Test Reminder Event")

    // Mock getAlertByTime to return our test event
    every { CalendarProvider.getAlertByTime(any(), any(), any(), any()) } answers {
      val alertTime = secondArg<Long>()
      DevLog.info(LOG_TAG, "Mock getAlertByTime called with alertTime=$alertTime")

      if (alertTime == reminderTime) {
        listOf(EventAlertRecord(
          calendarId = testCalendarId,
          eventId = testEventId,
          isAllDay = false,
          isRepeating = false,
          alertTime = reminderTime,
          notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
          title = "Test Monitor Event",
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

    // Verify settings
    // Reuse the settings variable from above instead of creating a new one
    assertTrue("Calendar should be handled", settings.getCalendarIsHandled(testCalendarId))
    assertTrue("Calendar monitoring should be enabled", settings.enableCalendarRescan)

    // First verify no alerts exist
    MonitorStorage(fakeContext).classCustomUse { db ->
      val alerts = db.alerts
      assertEquals("Should start with no alerts", 0, alerts.size)
    }

    // Trigger initial calendar scan
    notifyCalendarChangeAndWait()

    // Verify alerts were added but not handled
    MonitorStorage(fakeContext).classCustomUse { db ->
      val alerts = db.alerts
      DevLog.info(LOG_TAG, "Found ${alerts.size} alerts after scan")
      alerts.forEach { alert ->
        DevLog.info(LOG_TAG, "Alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
      }
      assertTrue("Should have alerts after scan", alerts.isNotEmpty())
      assertFalse("Alerts should not be handled yet", alerts.any { it.wasHandled })
    }

    // Advance time past the reminder time
    DevLog.info(LOG_TAG, "Advancing time past reminder time...")
    val advanceAmount = reminderTime - startTime + Consts.ALARM_THRESHOLD
    advanceTimer(advanceAmount)

    val currentTimeAfterAdvance = currentTime.get()
    DevLog.info(LOG_TAG, "Current time after advance: $currentTimeAfterAdvance")
    DevLog.info(LOG_TAG, "Time check: currentTime=$currentTimeAfterAdvance, nextEventFireFromScan=${monitorState.nextEventFireFromScan}, " +
      "threshold=${currentTimeAfterAdvance + Consts.ALARM_THRESHOLD}")

    // Verify timing condition will be met
    assertTrue("Current time + threshold should be greater than nextEventFireFromScan",
      currentTimeAfterAdvance + Consts.ALARM_THRESHOLD > monitorState.nextEventFireFromScan)

    // Set the last timer broadcast received to indicate an alarm happened
    lastTimerBroadcastReceived = currentTimeAfterAdvance

    // Use mocked extension property to provide global state

    // First trigger the alarm broadcast receiver
    val alarmIntent = Intent(fakeContext, ManualEventAlarmBroadcastReceiver::class.java).apply {
      action = "com.github.quarck.calnotify.MANUAL_EVENT_ALARM"
      putExtra("alert_time", reminderTime)
    }
    mockCalendarMonitor.onAlarmBroadcast(fakeContext, alarmIntent)

    // Then let the service handle the intent that would have been created by the broadcast receiver
    val serviceIntent = Intent(fakeContext, CalendarMonitorService::class.java).apply {
      putExtra("alert_time", reminderTime)
      putExtra("rescan_monitor", true)
      putExtra("reload_calendar", false) // Important: don't reload calendar on alarm
      putExtra("start_delay", 0)
    }

    mockService.handleIntentForTest(serviceIntent)

    // Small delay for processing
    Thread.sleep(1000)

    // Verify alerts were handled
    MonitorStorage(fakeContext).classCustomUse { db ->
      val alerts = db.alerts
      DevLog.info(LOG_TAG, "Found ${alerts.size} alerts after processing")
      alerts.forEach { alert ->
        DevLog.info(LOG_TAG, "Alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
      }
      assertTrue("Should have alerts after processing", alerts.isNotEmpty())
      assertTrue("Alerts should be marked as handled", alerts.all { it.wasHandled })
    }

    // Verify event was processed
    verifyEventProcessed(
      eventId = testEventId,
      startTime = eventStartTime,
      title = "Test Reminder Event"
    )

    // Verify no rescan was triggered
    verify(exactly = 0) { mockCalendarMonitor.onRescanFromService(any()) }

    // Verify event was registered
    verify(exactly = 1) { ApplicationController.registerNewEvent(any(), any()) }
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
    val monitorState = CalendarMonitorState(fakeContext)
    monitorState.firstScanEver = false
    currentTime.set(System.currentTimeMillis())
    val startTime = currentTime.get()
    monitorState.prevEventScanTo = startTime
    monitorState.prevEventFireFromScan = startTime

    // Create and setup test events - each 1 hour apart
    val events = createMultipleTestEvents(3)
    DevLog.info(LOG_TAG, "Created ${events.size} test events: $events")

    // Verify events were created successfully
    assertTrue("Failed to create test events", events.isNotEmpty())
    events.forEach { eventId ->
      assertTrue("Invalid event ID: $eventId", eventId > 0)

      // Verify the event exists in calendar
      val cursor = fakeContext.contentResolver.query(
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
        null, null, null, null
      )
      assertNotNull("Event $eventId should exist in calendar", cursor)
      assertTrue("Event $eventId should exist in calendar", cursor!!.moveToFirst())
      cursor.close()
    }

    // Setup mocks for event handling
    setupMultipleEventDetails(events, startTime)
    mockMultipleEventAlerts(events, startTime)

    // Verify calendar settings
    val settings = Settings(fakeContext)
    assertTrue("Calendar should be handled", settings.getCalendarIsHandled(testCalendarId))
    assertTrue("Calendar monitoring should be enabled", settings.enableCalendarRescan)

    // Execute test
    notifyCalendarChangeAndWait()

    // For each event, simulate its alarm broadcast
    events.forEachIndexed { index, eventId ->
      val hourOffset = index + 1
      val eventStartTime = startTime + (hourOffset * Consts.HOUR_IN_MILLISECONDS)
      val alertTime = eventStartTime - (15 * Consts.MINUTE_IN_MILLISECONDS)

      DevLog.info(LOG_TAG, "Processing event $eventId: startTime=$eventStartTime, alertTime=$alertTime")

      // Advance time to just past this event's alert time
      DevLog.info(LOG_TAG, "Advancing time past alert time for event $eventId...")
      currentTime.set(alertTime + Consts.ALARM_THRESHOLD)

      // Verify event state before alarm
      MonitorStorage(fakeContext).classCustomUse { db ->
        val alerts = db.alerts
        val alert = alerts.find { it.eventId == eventId }
        assertNotNull("Alert for event $eventId should exist", alert)
        assertFalse("Alert should not be handled yet", alert!!.wasHandled)
      }

      // Simulate alarm broadcast for this event
      DevLog.info(LOG_TAG, "Simulating alarm broadcast for event $eventId...")
      val intent = Intent(CalendarContract.ACTION_EVENT_REMINDER).apply {
        data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, alertTime)
        putExtra("alertTime", alertTime)
      }
      mockCalendarMonitor.onProviderReminderBroadcast(fakeContext, intent)

      // Small delay to allow processing
      Thread.sleep(100)

      // Verify event was processed
      MonitorStorage(fakeContext).classCustomUse { db ->
        val alerts = db.alerts
        val alert = alerts.find { it.eventId == eventId }
        assertNotNull("Alert for event $eventId should still exist", alert)
        assertTrue("Alert should be marked as handled", alert!!.wasHandled)
      }

      EventsStorage(fakeContext).classCustomUse { db ->
        val event = db.getEvent(eventId, eventStartTime)
        assertNotNull("Event $eventId should be in storage", event)
        assertEquals("Event should have correct start time", eventStartTime, event!!.startTime)
        assertEquals("Event should have correct title", "Test Event $index", event.title)
      }
    }

    // Verify that CalendarMonitor.onRescanFromService was called at least once
    verify(atLeast = 1) { mockCalendarMonitor.onRescanFromService(any()) }

    // Verify that registerNewEvent was called for each event
    verify(exactly = events.size) { ApplicationController.registerNewEvent(any(), any()) }

    // Final verification that all events were processed in order
    EventsStorage(fakeContext).classCustomUse { db ->
      val storedEvents = db.events.sortedBy { it.startTime }
      assertEquals("Should have all events in storage", events.size, storedEvents.size)

      storedEvents.forEachIndexed { index, event ->
        val hourOffset = index + 1
        val expectedStartTime = startTime + (hourOffset * Consts.HOUR_IN_MILLISECONDS)
        assertEquals("Event $index should have correct start time", expectedStartTime, event.startTime)
        assertEquals("Event $index should have correct title", "Test Event $index", event.title)
      }
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
    val monitorState = CalendarMonitorState(fakeContext)
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
    mockCalendarMonitor.onRescanFromService(fakeContext)

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

    val calUri = fakeContext.contentResolver.insert(uri, values)
    val calendarId = calUri?.lastPathSegment?.toLong() ?: -1L
    DevLog.info(LOG_TAG, "Created test calendar: id=$calendarId, name=$displayName")
    return calendarId
  }

  /**
   * Creates a test event in the test calendar with default reminder settings.
   *
   * @return The ID of the created event
   * @throws IllegalStateException if event creation fails
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

    val eventUri = fakeContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    val eventId = eventUri?.lastPathSegment?.toLongOrNull() ?: 0L

    if (eventId <= 0) {
      throw IllegalStateException("Failed to create test event")
    }

    // Add a reminder
    val reminderValues = ContentValues().apply {
      put(CalendarContract.Reminders.EVENT_ID, eventId)
      put(CalendarContract.Reminders.MINUTES, 15)
      put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
    }
    val reminderUri = fakeContext.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
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
      try {
        val values = ContentValues().apply {
          put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
          put(CalendarContract.Events.TITLE, "Test Event $index")
          put(CalendarContract.Events.DESCRIPTION, "Test Description $index")
          put(CalendarContract.Events.DTSTART, currentTime + (3600000 * (index + 1)))
          put(CalendarContract.Events.DTEND, currentTime + (3600000 * (index + 2)))
          put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
          put(CalendarContract.Events.HAS_ALARM, 1)
        }

        val eventUri = fakeContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = eventUri?.lastPathSegment?.toLongOrNull() ?: -1L

        // Only add valid event IDs
        if (eventId > 0) {
          eventIds.add(eventId)
          // Add a reminder for each event
          val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 15)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
          }
          val reminderUri = fakeContext.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
          DevLog.info(LOG_TAG, "Created test event $index: id=$eventId, reminder=${reminderUri != null}")

          // Verify the event exists in calendar
          val cursor = fakeContext.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            null, null, null, null
          )
          if (cursor != null && cursor.moveToFirst()) {
            DevLog.info(LOG_TAG, "Verified event $eventId exists in calendar")
          } else {
            DevLog.error(LOG_TAG, "Failed to verify event $eventId in calendar")
          }
          cursor?.close()
        } else {
          DevLog.error(LOG_TAG, "Failed to create event $index - invalid event ID")
        }
      } catch (ex: Exception) {
        DevLog.error(LOG_TAG, "Error creating event $index: ${ex.message}")
      }
    }

    if (eventIds.isEmpty()) {
      DevLog.error(LOG_TAG, "Failed to create any test events!")
    } else {
      DevLog.info(LOG_TAG, "Created ${eventIds.size} test events with IDs: $eventIds")
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
    val monitorState = CalendarMonitorState(fakeContext)
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
   * This mock ensures there are always unhandled future alerts available by generating a continuous sequence of future alerts.
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
      DevLog.info(LOG_TAG, "Current test time: ${this@CalendarMonitorServiceTest.currentTime.get()}")

      // Generate alerts only if they fall within the scan range
      val alerts = mutableListOf<MonitorEventAlertEntry>()

      val alertTime = startTime - alertOffset
      DevLog.info(LOG_TAG, "Evaluating alert: eventId=$eventId, alertTime=$alertTime, startTime=$startTime")
      DevLog.info(LOG_TAG, "Range check: alertTime=$alertTime in range $scanFrom..$scanTo = ${alertTime in scanFrom..scanTo}")

      if (alertTime in scanFrom..scanTo) {
        val alert = MonitorEventAlertEntry(
          eventId = eventId,
          isAllDay = false,
          alertTime = alertTime,
          instanceStartTime = startTime,
          instanceEndTime = startTime + duration,
          alertCreatedByUs = false,
          wasHandled = false
        )
        alerts.add(alert)
        DevLog.info(LOG_TAG, "Created alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, startTime=${alert.instanceStartTime}")
      } else {
        DevLog.info(LOG_TAG, "Alert out of range: alertTime=$alertTime")
        DevLog.info(LOG_TAG, "Time deltas: from start=${alertTime - scanFrom}, to end=${scanTo - alertTime}")
      }

      DevLog.info(LOG_TAG, "Returning ${alerts.size} alerts")
      alerts.forEach { alert ->
        DevLog.info(LOG_TAG, "Alert in response: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
      }

      alerts
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
    EventsStorage(fakeContext).classCustomUse { db ->
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
  private fun verifyEventProcessed(
    eventId: Long,
    startTime: Long,
    title: String? = null,
    afterDelay: Long? = null
  ) {
    DevLog.info(LOG_TAG, "Verifying event processing for eventId=$eventId, startTime=$startTime, title=$title")

    // First verify event storage
    EventsStorage(fakeContext).classCustomUse { db ->
      val events = db.events
      DevLog.info(LOG_TAG, "Found ${events.size} events in storage")
      events.forEach { event ->
        DevLog.info(LOG_TAG, "Event in storage: id=${event.eventId}, timeFirstSeen=${event.timeFirstSeen}, startTime=${event.startTime}, title=${event.title}")
      }

      // Find event by ID, ignoring display status since notification manager may have changed it
      val eventExists = events.any { it.eventId == eventId }
      if (!eventExists) {
        DevLog.error(LOG_TAG, "Event $eventId not found in storage!")
        // Check if event exists in calendar
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
        } else {}
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
    ApplicationController.onCalendarChanged(fakeContext)
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

    val eventUri = fakeContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    val eventId = eventUri?.lastPathSegment?.toLong() ?: -1L

    // Add a reminder
    val reminderValues = ContentValues().apply {
      put(CalendarContract.Reminders.EVENT_ID, eventId)
      put(CalendarContract.Reminders.MINUTES, 15)
      put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
    }
    val reminderUri = fakeContext.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
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

    val eventUri = fakeContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    val eventId = eventUri?.lastPathSegment?.toLong() ?: -1L

    // Add a reminder
    val reminderValues = ContentValues().apply {
      put(CalendarContract.Reminders.EVENT_ID, eventId)
      put(CalendarContract.Reminders.MINUTES, 15)
      put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
    }
    val reminderUri = fakeContext.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
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
    val settings = Settings(fakeContext)
    settings.setBoolean("enable_manual_calendar_rescan", true)

    // Test 1: System Calendar Change Broadcast
    DevLog.info(LOG_TAG, "Testing calendar change broadcast handling")
    val intent = Intent(CalendarContract.ACTION_EVENT_REMINDER)
    mockService.handleIntentForTest(intent)
    verify(exactly = 1) { mockCalendarMonitor.onRescanFromService(any()) }

    // Test 2: System Time Change
    DevLog.info(LOG_TAG, "Testing system time change handling")
    mockCalendarMonitor.onSystemTimeChange(fakeContext)
    verify(exactly = 2) { mockCalendarMonitor.onRescanFromService(any()) }

    // Test 3: App Resume
    DevLog.info(LOG_TAG, "Testing app resume handling")
    mockCalendarMonitor.onAppResumed(fakeContext, false)
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
    EventsStorage(fakeContext).classCustomUse { db ->
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
    EventsStorage(fakeContext).classCustomUse { db ->
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
    val settings = Settings(fakeContext)
    settings.setBoolean("enable_manual_calendar_rescan", false)

    // Create test event
    createTestEvent()

    // Setup monitor state
    val monitorState = CalendarMonitorState(fakeContext)
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
    mockCalendarMonitor.onAppResumed(fakeContext, false)
    verify(exactly = 2) { mockCalendarMonitor.onRescanFromService(any()) }
  }
} 
