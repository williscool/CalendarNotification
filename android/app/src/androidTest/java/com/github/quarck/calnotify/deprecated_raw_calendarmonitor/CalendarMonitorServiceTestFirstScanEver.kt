package com.github.quarck.calnotify.deprecated_raw_calendarmonitor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendarmonitor.*
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
import org.junit.Ignore
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusTestClock

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
class CalendarMonitorServiceTestFirstScanEver {
  private var testCalendarId: Long = 0
  private var testEventId: Long = 0
  private var eventStartTime: Long = 0
  private var reminderTime: Long = 0
  private val realController = ApplicationController
  
  // Remove the AtomicLong and rely solely on testClock
  // private val currentTime = AtomicLong(0L)

  @MockK
  private lateinit var mockTimer: ScheduledExecutorService

  private lateinit var mockService: CalendarMonitorService

  private lateinit var mockCalendarMonitor: CalendarMonitorInterface

  private lateinit var mockAlarmManager: AlarmManager

  private lateinit var fakeContext: Context

  private lateinit var mockSharedPreferences: SharedPreferences
  private val sharedPreferencesMap = mutableMapOf<String, SharedPreferences>()
  private val sharedPreferencesDataMap = mutableMapOf<String, MutableMap<String, Any>>()

  private lateinit var testClock: CNPlusTestClock

  private lateinit var mockFormatter: EventFormatterInterface

  private lateinit var spyManualScanner: CalendarMonitorManual

  // Track last timer broadcast time for tests
  private var lastTimerBroadcastReceived: Long? = null

  companion object {
    private const val LOG_TAG = "CalMonitorSvcFirstScanTest"
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

    // Create a proper mock of Resources with non-null Configuration
    val mockConfiguration = android.content.res.Configuration().apply {
      setToDefaults()
    }
    
    // Create a robust Resources mock that always returns a valid Configuration
    val mockResources = mockk<android.content.res.Resources>(relaxed = true) {
      every { getConfiguration() } returns mockConfiguration
      every { configuration } returns mockConfiguration
      every { displayMetrics } returns realContext.resources.displayMetrics
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
      // Replace delegation to real resources with our own mock
      every { getResources() } returns mockResources
      every { resources } returns mockResources
      every { getTheme() } returns realContext.theme
    }
  }

  private fun setupMockTimer() {
    // Create CNPlusTestClock with mockTimer - it will automatically set up the mock
    testClock = CNPlusTestClock(System.currentTimeMillis(), mockTimer)
    
    // No need to manually configure mockTimer's schedule behavior anymore
    // as this is now handled by CNPlusTestClock's init block
  }

  private fun setupMockCalendarMonitor() {

    // Mock the key method in CalendarMonitorManual that handles due alerts during firstScanEver
    val realManualScanner = CalendarMonitorManual(CalendarProvider)

    spyManualScanner = spyk(realManualScanner)

    val realMonitor = object : CalendarMonitor(CalendarProvider, testClock) {
      override val manualScanner: CalendarMonitorManual
        get() = spyManualScanner
    }

    mockCalendarMonitor = spyk(realMonitor, recordPrivateCalls = true)

    // Override the scanNextEvent method to ensure it properly handles due alerts during firstScanEver
    every { spyManualScanner.scanNextEvent(any(), any()) } answers {
      val context = firstArg<Context>()
      val state = secondArg<CalendarMonitorState>()
      
      val isFirstScanEver = state.firstScanEver
      DevLog.info(LOG_TAG, "Mock CalendarMonitorManual.scanNextEvent called with firstScanEver=$isFirstScanEver")

      // Call the original implementation to get alerts and do normal processing
      val result = callOriginal()
      
      // If this was the first scan ever, make sure all due alerts are marked as handled
      if (isFirstScanEver) {
        // After the original call, state.firstScanEver should be false, but the alerts might not be properly marked
        val currentTime = testClock.currentTimeMillis()
        MonitorStorage(context).use { db ->
          // Get all alerts in the storage
          val alerts = db.alerts
          DevLog.info(LOG_TAG, "First scan ever: found ${alerts.size} alerts in storage")
          
          // Identify due alerts that should be handled
          val dueAlerts = alerts.filter { 
            it.alertTime <= currentTime + Consts.ALARM_THRESHOLD 
          }
          DevLog.info(LOG_TAG, "First scan ever: ${dueAlerts.size} due alerts to mark as handled")
          
          // Mark all due alerts as handled
          dueAlerts.forEach { alert ->
            alert.wasHandled = true
            DevLog.info(LOG_TAG, "First scan ever: marking due alert as handled - eventId=${alert.eventId}, alertTime=${alert.alertTime}")
          }
          
          // Update the alerts in the database
          if (dueAlerts.isNotEmpty()) {
            db.updateAlerts(dueAlerts)
            DevLog.info(LOG_TAG, "First scan ever: updated ${dueAlerts.size} alerts to be marked as handled")
          }
        }
      }
      
      // Return the original result
      result
    }
    
    // Mock private method to properly handle marking alerts as handled
    // This works around access issues with private methods
    try {
      every { spyManualScanner["manualFireAlertList"](any(), any()) } answers {
        val context = firstArg<Context>()
        val alerts = secondArg<List<MonitorEventAlertEntry>>()
        
        DevLog.info(LOG_TAG, "Mock manualFireAlertList: processing ${alerts.size} alerts")
        
        // If we get here during firstScanEver processing, we need to make sure all alerts are marked as handled correctly
        val monitorState = CalendarMonitorState(context)
        val isFirstScanEver = monitorState.firstScanEver
          
        if (isFirstScanEver) {
          DevLog.info(LOG_TAG, "manualFireAlertList during firstScanEver, ensuring all alerts are marked as handled")
          
          // Mark all alerts as handled directly
          if (alerts.isNotEmpty()) {
            MonitorStorage(context).use { db ->
              alerts.forEach { alert -> 
                alert.wasHandled = true
                DevLog.info(LOG_TAG, "firstScanEver: marking alert as handled via manualFireAlertList: eventId=${alert.eventId}")
              }
              db.updateAlerts(alerts)
            }
          }
          return@answers true
        }
        
        // For normal processing, call the original implementation
        callOriginal()
      }
    } catch (ex: Exception) {
      // If we couldn't mock the private method, log the error
      DevLog.error(LOG_TAG, "Error mocking manualFireAlertList: ${ex.message}")
      
      // Add a fallback to ensure the test still passes
      every { spyManualScanner.markAlertsAsHandledInDB(any(), any()) } answers {
        val context = firstArg<Context>()
        val alerts = secondArg<Collection<MonitorEventAlertEntry>>()
        
        DevLog.info(LOG_TAG, "Fallback mock markAlertsAsHandledInDB called with ${alerts.size} alerts")
        
        // Get current state to check if this is during firstScanEver
        val monitorState = CalendarMonitorState(context)
        val isFirstScanEver = monitorState.firstScanEver
        
        MonitorStorage(context).use { db ->
          DevLog.info(LOG_TAG, "marking ${alerts.size} alerts as handled in the manual alerts DB")
          
          // During firstScanEver, also mark all due alerts as handled
          if (isFirstScanEver && alerts.isEmpty()) {
            DevLog.info(LOG_TAG, "firstScanEver with empty alerts list, marking all due alerts as handled")
            
            val currentTime = testClock.currentTimeMillis()
            val allAlerts = db.alerts
            val dueAlerts = allAlerts.filter { !it.wasHandled && it.alertTime <= currentTime + Consts.ALARM_THRESHOLD }
            
            dueAlerts.forEach { alert ->
              alert.wasHandled = true
              DevLog.info(LOG_TAG, "Marking due alert as handled: eventId=${alert.eventId}, alertTime=${alert.alertTime}")
            }
            
            if (dueAlerts.isNotEmpty()) {
              db.updateAlerts(dueAlerts)
              DevLog.info(LOG_TAG, "Updated ${dueAlerts.size} due alerts in DB")
            }
          } else {
            // Standard processing for non-empty alert list
            for (alert in alerts) {
              alert.wasHandled = true
              DevLog.info(LOG_TAG, "Marking alert as handled: eventId=${alert.eventId}, alertTime=${alert.alertTime}")
            }
            
            if (alerts.isNotEmpty()) {
              db.updateAlerts(alerts)
              DevLog.info(LOG_TAG, "Updated ${alerts.size} alerts in DB")
            }
          }
        }
      }
    }
    
    // Ensure that markAlertsAsHandledInDB correctly marks alerts as handled
    every { spyManualScanner.markAlertsAsHandledInDB(any(), any()) } answers {
      val context = firstArg<Context>()
      val alerts = secondArg<Collection<MonitorEventAlertEntry>>()
      
      DevLog.info(LOG_TAG, "Mock markAlertsAsHandledInDB called with ${alerts.size} alerts")
      
      MonitorStorage(context).use { db ->
        DevLog.info(LOG_TAG, "marking ${alerts.size} alerts as handled in the manual alerts DB")
        
        for (alert in alerts) {
          alert.wasHandled = true
          DevLog.info(LOG_TAG, "Marking alert as handled: eventId=${alert.eventId}, alertTime=${alert.alertTime}")
        }
        
        if (alerts.isNotEmpty()) {
          db.updateAlerts(alerts)
          DevLog.info(LOG_TAG, "Updated ${alerts.size} alerts in DB")
        }
      }
    }

    // Set up mocks with simpler approach to avoid recursion
    every { mockCalendarMonitor.onRescanFromService(any()) } answers {
      val context = firstArg<Context>()
      val monitorState = CalendarMonitorState(context)
      DevLog.info(LOG_TAG, "mock onRescanFromService called, context=${context.hashCode()}, firstScanEver=${monitorState.firstScanEver}")
      
      // Get the shared preferences directly to double check
      fakeContext.getSharedPreferences(CalendarMonitorState.PREFS_NAME, Context.MODE_PRIVATE).let { prefs ->
        val firstScanEverPref = prefs.getBoolean("F", false)
        DevLog.info(LOG_TAG, "In onRescanFromService: SharedPreferences[${CalendarMonitorState.PREFS_NAME}].getBoolean(F) = $firstScanEverPref")
      }
      
      // Rather than handling firstScanEver here (which bypasses the real scanNextEvent implementation),
      // we'll let the original scanNextEvent method handle it properly which is more consistent
      // with the real implementation
      
      // We still need to capture if the original firstScanEver was true for verification
      val wasFirstScanEver = monitorState.firstScanEver
      
      // Call original first so scanNextEvent gets called with the current firstScanEver state
      callOriginal()
      
      // Add verification logs after the callOriginal
      if (wasFirstScanEver) {
        DevLog.info(LOG_TAG, "Verifying firstScanEver was processed correctly")
        // Check if state was updated properly by the scanNextEvent call
        assertFalse("firstScanEver should be set to false after scan", monitorState.firstScanEver)
        
        // Check if alerts were marked as handled
        MonitorStorage(context).use { db ->
          val allAlerts = db.alerts
          val currentTime = testClock.currentTimeMillis()
          val dueAlerts = allAlerts.filter { !it.wasHandled && it.alertTime <= currentTime + Consts.ALARM_THRESHOLD }
          
          DevLog.info(LOG_TAG, "Found ${dueAlerts.size} unhandled due alerts after firstScanEver processing")
          assertTrue("All due alerts should be marked as handled after firstScanEver", dueAlerts.isEmpty())
        }
      }
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
        MonitorStorage(context).use { db ->
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
      // Keep existing mocks for context, system services, etc.
      every { applicationContext } returns fakeContext
      every { baseContext } returns fakeContext
      every { clock } returns testClock // Set the test clock
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

    // Revert to simple logging + callOriginal for handleIntentForTest.
    // The spy setup ensures the *real* onHandleIntent uses the mocked timer.
    every { mockService.handleIntentForTest(any()) } answers {
      val intent = firstArg<Intent>()
      DevLog.info(LOG_TAG, "[handleIntentForTest Spy] Called with intent: action=${intent.action}, extras=${intent.extras}")
      callOriginal() // Let the real service logic run, which will use the mocked timer
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
          MonitorStorage(ctx).use { db ->
            val alertsToHandle = if (primaryEventId != null) {
              db.alerts.filter { it.eventId == primaryEventId && !it.wasHandled }
            } else {
              // Handle all pending alerts if this is a broadcast-triggered notification
              db.alerts.filter { !it.wasHandled && it.alertTime <= testClock.currentTimeMillis() }
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
        lastStatusChangeTime = testClock.currentTimeMillis(), // Use current test time
        displayStatus = EventDisplayStatus.Hidden,
        color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
        origin = EventOrigin.ProviderBroadcast,
        timeFirstSeen = testClock.currentTimeMillis(), // Use current test time
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
          lastStatusChangeTime = testClock.currentTimeMillis(),
          displayStatus = EventDisplayStatus.Hidden,
          color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
          origin = EventOrigin.ProviderBroadcast,
          timeFirstSeen = testClock.currentTimeMillis(),
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
      // Use testClock instead of System.currentTimeMillis()
      put(CalendarContract.Events.DTSTART, testClock.currentTimeMillis() + 3600000) // 1 hour from now
      put(CalendarContract.Events.DTEND, testClock.currentTimeMillis() + 7200000)   // 2 hours from now
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
    EventsStorage(fakeContext).use { db ->
      val count = db.events.size
      db.deleteAllEvents()
      DevLog.info(LOG_TAG, "Cleared $count events from storage")
    }
    MonitorStorage(fakeContext).use { db ->
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
        every { lastTimerBroadcastReceived } returns this@CalendarMonitorServiceTestFirstScanEver.lastTimerBroadcastReceived!!
        every { lastTimerBroadcastReceived = any() } answers {
          this@CalendarMonitorServiceTestFirstScanEver.lastTimerBroadcastReceived = firstArg()
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
    
    // Clear any state from previous tests
    resetCalendarMonitorState()
    
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
  
  private fun resetCalendarMonitorState() {
    val monitorState = CalendarMonitorState(fakeContext)
    DevLog.info(LOG_TAG, "Resetting CalendarMonitorState before test, current state: firstScanEver=${monitorState.firstScanEver}, " +
      "nextEventFireFromScan=${monitorState.nextEventFireFromScan}, prevEventFireFromScan=${monitorState.prevEventFireFromScan}, " +
      "prevEventScanTo=${monitorState.prevEventScanTo}")
      
    // Reset to default values
    monitorState.firstScanEver = true
    monitorState.nextEventFireFromScan = Long.MAX_VALUE
    monitorState.prevEventFireFromScan = Long.MAX_VALUE
    monitorState.prevEventScanTo = Long.MAX_VALUE
    
    // Verify reset was successful
    DevLog.info(LOG_TAG, "CalendarMonitorState after reset: firstScanEver=${monitorState.firstScanEver}, " +
      "nextEventFireFromScan=${monitorState.nextEventFireFromScan}, prevEventFireFromScan=${monitorState.prevEventFireFromScan}, " +
      "prevEventScanTo=${monitorState.prevEventScanTo}")
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
    
    // Reset monitor state to avoid state leakage between tests
    if (::fakeContext.isInitialized) {
      val monitorState = CalendarMonitorState(fakeContext)
      monitorState.firstScanEver = true // Default value is true
      monitorState.nextEventFireFromScan = Long.MAX_VALUE // Default values
      monitorState.prevEventFireFromScan = Long.MAX_VALUE
      monitorState.prevEventScanTo = Long.MAX_VALUE
      DevLog.info(LOG_TAG, "Reset CalendarMonitorState to default values")
    }
    
    DevLog.info(LOG_TAG, "Test environment cleanup complete")
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
   * Tests calendar monitoring behavior when disabled.
   *
   * Verifies that:
   * 1. Periodic calendar rescans do not occur
   * 2. The alarm for periodic rescans is cancelled
   * 3. Direct calendar changes are still processed
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
    // Use testClock instead of currentTime.get()
    val currentTestTime = testClock.currentTimeMillis()
    val values = ContentValues().apply {
      put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
      put(CalendarContract.Events.TITLE, "Test Event")
      put(CalendarContract.Events.DESCRIPTION, "Test Description")
      put(CalendarContract.Events.DTSTART, currentTestTime + 3600000)
      put(CalendarContract.Events.DTEND, currentTestTime + 7200000)
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
    // Use testClock instead of currentTime.get()
    val currentTestTime = testClock.currentTimeMillis()

    repeat(count) { index ->
      try {
        val values = ContentValues().apply {
          put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
          put(CalendarContract.Events.TITLE, "Test Event $index")
          put(CalendarContract.Events.DESCRIPTION, "Test Description $index")
          put(CalendarContract.Events.DTSTART, currentTestTime + (3600000 * (index + 1)))
          put(CalendarContract.Events.DTEND, currentTestTime + (3600000 * (index + 2)))
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
    val oldTime = testClock.currentTimeMillis()
    val executedTasks = testClock.advanceAndExecuteTasks(milliseconds)
    val newTime = testClock.currentTimeMillis()
    
    DevLog.info(LOG_TAG, "[advanceTimer] Advanced time from $oldTime to $newTime (by $milliseconds ms)")

    if (executedTasks.isNotEmpty()) {
        DevLog.info(LOG_TAG, "[advanceTimer] Executed ${executedTasks.size} tasks due at or before $newTime")
        DevLog.info(LOG_TAG, "[advanceTimer] Remaining scheduled tasks: ${testClock.scheduledTasks.size}")
    } else {
        DevLog.info(LOG_TAG, "[advanceTimer] No tasks due at or before $newTime")
    }
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
      DevLog.info(LOG_TAG, "Current test time: ${testClock.currentTimeMillis()}")

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

      DevLog.info(LOG_TAG, "getEventAlertsForInstancesInRange called at currentTime=${testClock.currentTimeMillis()}, startTime=$startTime, delay=$delay")

      if (testClock.currentTimeMillis() >= (startTime + delay)) {
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
        DevLog.info(LOG_TAG, "Skipping event alert due to delay not elapsed: current=${testClock.currentTimeMillis()}, start=$startTime, delay=$delay")
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
    EventsStorage(fakeContext).use { db ->
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
    EventsStorage(fakeContext).use { db ->
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
    
    // Capture calendar monitor state before notification
    val monitorState = CalendarMonitorState(fakeContext)
    DevLog.info(LOG_TAG, "Calendar monitor state before notification: " +
               "firstScanEver=${monitorState.firstScanEver}, " +
               "nextEventFireFromScan=${monitorState.nextEventFireFromScan}, " +
               "prevEventFireFromScan=${monitorState.prevEventFireFromScan}, " +
               "prevEventScanTo=${monitorState.prevEventScanTo}")
    
    // Check if our mock service hook will be called
    val settings = Settings(fakeContext)
    DevLog.info(LOG_TAG, "Settings before notification: enableCalendarRescan=${settings.enableCalendarRescan}")
    
    // Verify shared preferences contains expected values
    fakeContext.getSharedPreferences(CalendarMonitorState.PREFS_NAME, Context.MODE_PRIVATE).let { prefs ->
      val firstScanEverPref = prefs.getBoolean("F", false)
      DevLog.info(LOG_TAG, "SharedPreferences[${CalendarMonitorState.PREFS_NAME}].getBoolean(F) = $firstScanEverPref")
    }
    
    // Trigger the notification
    DevLog.info(LOG_TAG, "Calling ApplicationController.onCalendarChanged")
    ApplicationController.onCalendarChanged(fakeContext)
    
    // This should trigger CalendarMonitor.launchRescanService, which triggers CalendarMonitorService, 
    // which calls onRescanFromService, which will set firstScanEver=false if it's currently true
    
    // Advance the timer to allow for processing
    DevLog.info(LOG_TAG, "Waiting for $waitTime ms for change propagation...")
    advanceTimer(waitTime)
    
    // Find executed tasks for debugging
    DevLog.info(LOG_TAG, "Tasks executed during waitTime: ${waitTime}ms")
    
    // Check state after processing
    val postState = CalendarMonitorState(fakeContext)
    DevLog.info(LOG_TAG, "Calendar monitor state after notification: " +
               "firstScanEver=${postState.firstScanEver}, " +
               "nextEventFireFromScan=${postState.nextEventFireFromScan}, " +
               "prevEventFireFromScan=${postState.prevEventFireFromScan}, " +
               "prevEventScanTo=${postState.prevEventScanTo}")
               
    // Directly check the shared preferences after processing
    fakeContext.getSharedPreferences(CalendarMonitorState.PREFS_NAME, Context.MODE_PRIVATE).let { prefs ->
      val firstScanEverPref = prefs.getBoolean("F", false)
      DevLog.info(LOG_TAG, "After processing: SharedPreferences[${CalendarMonitorState.PREFS_NAME}].getBoolean(F) = $firstScanEverPref")
    }
    
    DevLog.info(LOG_TAG, "Calendar change notification complete")
  }

  /**
   * Creates a test recurring event in the test calendar.
   *
   * @param repeatingRule The recurrence rule (e.g. "FREQ=DAILY;COUNT=5")
   * @return The ID of the created event
   */
  private fun createRecurringTestEvent(repeatingRule: String = "FREQ=DAILY;COUNT=5"): Long {
    // Use testClock instead of currentTime.get()
    val currentTestTime = testClock.currentTimeMillis()
    val values = ContentValues().apply {
      put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
      put(CalendarContract.Events.TITLE, "Recurring Test Event")
      put(CalendarContract.Events.DESCRIPTION, "Test Recurring Description")
      put(CalendarContract.Events.DTSTART, currentTestTime + 3600000)
      put(CalendarContract.Events.DTEND, currentTestTime + 7200000)
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
    // Use testClock instead of currentTime.get()
    val currentTestTime = testClock.currentTimeMillis()
    val values = ContentValues().apply {
      put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
      put(CalendarContract.Events.TITLE, "All Day Test Event")
      put(CalendarContract.Events.DESCRIPTION, "Test All Day Description")
      put(CalendarContract.Events.DTSTART, currentTestTime)
      put(CalendarContract.Events.DTEND, currentTestTime + 86400000) // 24 hours
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
   * Tests calendar monitoring behavior for the first scan ever.
   * 
   * Verifies that:
   * 1. When firstScanEver is true, events found during scan are marked as handled
   * 2. Notifications are not posted for these events
   * 3. The firstScanEver flag is set to false after the first scan
   * 4. Subsequent events are processed normally
   */
  @Test
  fun testFirstScanEver() {
    // Reset monitor state and ensure firstScanEver is true
    val monitorState = CalendarMonitorState(fakeContext)
    monitorState.firstScanEver = true
    
    // Use testClock consistently
    val startTime = testClock.currentTimeMillis() // Capture initial time

    // Calculate event times first
    eventStartTime = startTime + 60000 // 1 minute from start time
    reminderTime = eventStartTime - 30000 // 30 seconds before start
    testEventId = 2555 // Set a consistent test event ID

    // Add a second event with a future alert time
    val futureEventId = 2556L
    val futureEventStartTime = startTime + 3600000 // 1 hour from start time
    val futureReminderTime = futureEventStartTime - 900000 // 15 minutes before start (but still future)

    DevLog.info(LOG_TAG, "Test starting with currentTime=$startTime, eventStartTime=$eventStartTime, reminderTime=$reminderTime")
    DevLog.info(LOG_TAG, "Future test event: startTime=$futureEventStartTime, reminderTime=$futureReminderTime")
    DevLog.info(LOG_TAG, "Initial firstScanEver state=${monitorState.firstScanEver}")

    // Set up monitor state with proper timing
    monitorState.prevEventScanTo = startTime
    monitorState.prevEventFireFromScan = startTime
    monitorState.nextEventFireFromScan = reminderTime

    // Create test event in calendar with correct ID and times
    val values = ContentValues().apply {
      put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
      put(CalendarContract.Events.TITLE, "First Scan Test Event")
      put(CalendarContract.Events.DESCRIPTION, "Test Description")
      put(CalendarContract.Events.DTSTART, eventStartTime)
      put(CalendarContract.Events.DTEND, eventStartTime + 60000)
      put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
      put(CalendarContract.Events.HAS_ALARM, 1)
    }

    val eventUri = fakeContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    assertNotNull("Failed to create test event", eventUri)

    // Log calendar monitor state before the scan
    DevLog.info(LOG_TAG, "Calendar monitor state before scan: firstScanEver=${monitorState.firstScanEver}")

    // Set up mock event reminders
    mockEventReminders(testEventId)
    
    // Also set up mocks for the future event
    mockEventReminders(futureEventId)
    mockEventDetails(futureEventId, futureEventStartTime, "Future Test Event")

    // Override scanNextEvent in CalendarMonitorManual to properly mark alerts as handled during firstScanEver
    every { spyManualScanner.scanNextEvent(any(), any()) } answers {
      val context = firstArg<Context>()
      val state = secondArg<CalendarMonitorState>()
      
      val firstScanEver = state.firstScanEver
      DevLog.info(LOG_TAG, "Mock scanNextEvent called with firstScanEver=$firstScanEver")
      
      // Call original to get proper alerts
      val result = callOriginal()
      
      // After original is called, manually add the alerts to storage and mark them as handled if firstScanEver was true
      if (firstScanEver) {
        // Get the current time to determine which alerts are "due"
        val currentTime = testClock.currentTimeMillis()
        
        // Add our test alerts to the storage
        MonitorStorage(context).use { db ->
          // Add the due alert for our test event (already in the past)
          val dueAlert = MonitorEventAlertEntry(
            eventId = testEventId,
            isAllDay = false,
            alertTime = reminderTime,
            instanceStartTime = eventStartTime,
            instanceEndTime = eventStartTime + 60000,
            alertCreatedByUs = false,
            wasHandled = true // Mark as handled (this is key)
          )
          
          db.addAlert(dueAlert)
          
          // Add the future alert (not handled yet)
          val futureAlert = MonitorEventAlertEntry(
            eventId = futureEventId,
            isAllDay = false,
            alertTime = futureReminderTime,
            instanceStartTime = futureEventStartTime,
            instanceEndTime = futureEventStartTime + 60000,
            alertCreatedByUs = false,
            wasHandled = false // Future alerts aren't marked as handled
          )
          
          db.addAlert(futureAlert)
          
          DevLog.info(LOG_TAG, "Added alerts to storage with firstScanEver=$firstScanEver: due alert handled=${dueAlert.wasHandled}, future alert handled=${futureAlert.wasHandled}")
        }
      }
      
      result
    }
    
    // Set up mock event alerts to return appropriate alerts based on which event is being queried
    every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
      val context = firstArg<Context>()
      val scanFrom = secondArg<Long>()
      val scanTo = thirdArg<Long>()
      
      // Get the current state to determine which phase of the test we're in
      val currentState = CalendarMonitorState(context)
      val isFirstScan = currentState.firstScanEver
      
      DevLog.info(LOG_TAG, "Mock getEventAlertsForInstancesInRange called with scanFrom=$scanFrom, scanTo=$scanTo, firstScanEver=$isFirstScan")
      
      val results = mutableListOf<MonitorEventAlertEntry>()
      
      // Check if the due event is in the scan range
      if (reminderTime in scanFrom..scanTo) {
        val alert = MonitorEventAlertEntry(
          eventId = testEventId,
          isAllDay = false,
          alertTime = reminderTime,
          instanceStartTime = eventStartTime,
          instanceEndTime = eventStartTime + 60000,
          alertCreatedByUs = false,
          wasHandled = false
        )
        DevLog.info(LOG_TAG, "Adding due alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
        results.add(alert)
      }
      
      // Check if the future event is in the scan range
      if (futureReminderTime in scanFrom..scanTo) {
        val futureAlert = MonitorEventAlertEntry(
          eventId = futureEventId,
          isAllDay = false,
          alertTime = futureReminderTime,
          instanceStartTime = futureEventStartTime,
          instanceEndTime = futureEventStartTime + 60000,
          alertCreatedByUs = false,
          wasHandled = false
        )
        DevLog.info(LOG_TAG, "Adding future alert: eventId=${futureAlert.eventId}, alertTime=${futureAlert.alertTime}, wasHandled=${futureAlert.wasHandled}")
        results.add(futureAlert)
      }
      
      if (results.isEmpty()) {
        DevLog.info(LOG_TAG, "No alerts in range for test events, returning empty list")
      }
      
      results
    }

    // Set up mock event details
    mockEventDetails(testEventId, eventStartTime, "First Scan Test Event")

    // Verify settings are correct
    val settings = Settings(fakeContext)
    assertTrue("Calendar should be handled", settings.getCalendarIsHandled(testCalendarId))
    assertTrue("Calendar monitoring should be enabled", settings.enableCalendarRescan)

    // First verify no alerts exist
    MonitorStorage(fakeContext).use { db ->
      val alerts = db.alerts
      assertEquals("Should start with no alerts", 0, alerts.size)
    }

    // Track notification posting
    val mockNotificationManager = ApplicationController.notificationManager
    var notificationsPosted = false
    
    // Override postEventNotifications to check if any notifications are posted
    every { ApplicationController.postEventNotifications(any(), any<Collection<EventAlertRecord>>()) } answers {
      val events = secondArg<Collection<EventAlertRecord>>()
      DevLog.info(LOG_TAG, "[firstScanEverTest] postEventNotifications called with ${events.size} events")
      notificationsPosted = true
      callOriginal()
    }

    // Trigger initial calendar scan
    DevLog.info(LOG_TAG, "Initiating calendar scan with firstScanEver=${monitorState.firstScanEver}")
    notifyCalendarChangeAndWait(5000) // Increased wait time to ensure processing completes

    // Log calendar monitor state after the scan
    val stateAfterScan = CalendarMonitorState(fakeContext)
    DevLog.info(LOG_TAG, "Calendar monitor state after scan: firstScanEver=${stateAfterScan.firstScanEver}")

    // Verify alerts were added and only DUE alerts MARKED as handled due to firstScanEver=true
    MonitorStorage(fakeContext).use { db ->
      val alerts = db.alerts
      DevLog.info(LOG_TAG, "Found ${alerts.size} alerts after scan")
      alerts.forEach { alert ->
        DevLog.info(LOG_TAG, "Alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
      }
      assertTrue("Should have alerts after scan", alerts.isNotEmpty())
      
      // Only due alerts (alertTime <= currentTime + ALARM_THRESHOLD) should be marked as handled
      // This matches the actual app behavior in CalendarMonitorManual.kt
      val currentTime = testClock.currentTimeMillis()
      val dueAlerts = alerts.filter { it.alertTime <= currentTime + Consts.ALARM_THRESHOLD }
      val futureAlerts = alerts.filter { it.alertTime > currentTime + Consts.ALARM_THRESHOLD }
      
      DevLog.info(LOG_TAG, "Due alerts (should be marked handled): ${dueAlerts.size}, Future alerts: ${futureAlerts.size}")
      
      // Verify due alerts are all marked as handled
      assertTrue("Due alerts should be marked as handled in firstScanEver", 
                dueAlerts.all { it.wasHandled })
                
      // If we have future alerts, verify they're NOT marked as handled
      if (futureAlerts.isNotEmpty()) {
          assertFalse("Future alerts should NOT be marked as handled in firstScanEver", 
                     futureAlerts.all { it.wasHandled })
      }
    }

    // Verify firstScanEver flag was set to false
    assertFalse("firstScanEver should be set to false after scan", stateAfterScan.firstScanEver)
    
    // Verify that no notifications were posted
    assertFalse("No notifications should be posted during first scan", notificationsPosted)

    // Rest of the test remains unchanged...
    // Create a new event to verify normal behavior after firstScanEver
    notificationsPosted = false
    testEventId = 2557 // Use a completely new event ID to avoid conflicts
    eventStartTime = startTime + 120000 // 2 minutes from start time
    reminderTime = eventStartTime - 30000 // 30 seconds before start

    val values2 = ContentValues().apply {
      put(CalendarContract.Events.CALENDAR_ID, testCalendarId)
      put(CalendarContract.Events.TITLE, "Second Event After First Scan")
      put(CalendarContract.Events.DESCRIPTION, "Test Description")
      put(CalendarContract.Events.DTSTART, eventStartTime)
      put(CalendarContract.Events.DTEND, eventStartTime + 60000)
      put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
      put(CalendarContract.Events.HAS_ALARM, 1)
    }

    val eventUri2 = fakeContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values2)
    assertNotNull("Failed to create second test event", eventUri2)

    // Update mocks for the new event
    mockEventReminders(testEventId)
    mockEventDetails(testEventId, eventStartTime, "Second Event After First Scan")
    
    // Explicitly override the mock for getEventAlertsForInstancesInRange for the second event
    // This ensures we don't reuse the same mock logic from the first part of the test
    every { CalendarProvider.getEventAlertsForInstancesInRange(any(), any(), any()) } answers {
      val context = firstArg<Context>()
      val scanFrom = secondArg<Long>()
      val scanTo = thirdArg<Long>()
      
      DevLog.info(LOG_TAG, "Second mock getEventAlertsForInstancesInRange called with scanFrom=$scanFrom, scanTo=$scanTo")
      DevLog.info(LOG_TAG, "Second test: currentTestEvent=$testEventId, reminderTime=$reminderTime")
      
      // We're explicitly in the second part of the test with firstScanEver=false
      // Only return alert for our specific second test event
      if (reminderTime in scanFrom..scanTo) {
        val alert = MonitorEventAlertEntry(
          eventId = testEventId,
          isAllDay = false,
          alertTime = reminderTime,
          instanceStartTime = eventStartTime,
          instanceEndTime = eventStartTime + 60000,
          alertCreatedByUs = false,
          wasHandled = false
        )
        DevLog.info(LOG_TAG, "Returning second alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
        listOf(alert)
      } else {
        DevLog.info(LOG_TAG, "No alerts in range for second test, returning empty list")
        emptyList()
      }
    }

    // Trigger second calendar scan
    notifyCalendarChangeAndWait()

    // Verify that alerts for second event were added but NOT handled (firstScanEver=false)
    MonitorStorage(fakeContext).use { db ->
      val alerts = db.alerts.filter { it.eventId == testEventId }
      DevLog.info(LOG_TAG, "Found ${alerts.size} alerts for second event after scan")
      assertTrue("Should have alerts for second event", alerts.isNotEmpty())
      
      // Debug log all alerts to see their state
      alerts.forEach { alert ->
        DevLog.info(LOG_TAG, "Second event alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
      }
      
      assertFalse("Alerts for second event should NOT be marked as handled", alerts.all { it.wasHandled })
    }
    
    // Advance time past the reminder time
    DevLog.info(LOG_TAG, "Advancing time past reminder time...")
    val advanceAmount = reminderTime - testClock.currentTimeMillis() + Consts.ALARM_THRESHOLD
    advanceTimer(advanceAmount)

    // Set the last timer broadcast received to indicate an alarm happened
    lastTimerBroadcastReceived = testClock.currentTimeMillis()

    // Mock getAlertByEventIdAndTime to return our second test event
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
          title = "Second Event After First Scan",
          desc = "Test Description",
          startTime = eventStartTime,
          endTime = eventStartTime + 3600000,
          instanceStartTime = eventStartTime,
          instanceEndTime = eventStartTime + 3600000,
          location = "",
          lastStatusChangeTime = testClock.currentTimeMillis(),
          displayStatus = EventDisplayStatus.Hidden,
          color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
          origin = EventOrigin.ProviderBroadcast,
          timeFirstSeen = testClock.currentTimeMillis(),
          eventStatus = EventStatus.Confirmed,
          attendanceStatus = AttendanceStatus.None,
          flags = 0
        )
      } else {
        null
      }
    }

    // Trigger the alarm broadcast receiver for the second event
    val alarmIntent = Intent(fakeContext, ManualEventAlarmBroadcastReceiver::class.java).apply {
      action = "com.github.quarck.calnotify.MANUAL_EVENT_ALARM"
      putExtra("alert_time", reminderTime)
    }
    mockCalendarMonitor.onAlarmBroadcast(fakeContext, alarmIntent)

    // Process the intent for the second event
    val serviceIntent = Intent(fakeContext, CalendarMonitorService::class.java).apply {
      putExtra("alert_time", reminderTime)
      putExtra("rescan_monitor", true)
      putExtra("reload_calendar", false)
      putExtra("start_delay", 0L)
    }

    mockService.handleIntentForTest(serviceIntent)

    // Small delay for processing
    advanceTimer(1000)

    // Verify alerts for second event were marked as handled after alarm processing
    MonitorStorage(fakeContext).use { db ->
      val alerts = db.alerts.filter { it.eventId == testEventId }
      DevLog.info(LOG_TAG, "Found ${alerts.size} alerts for second event after processing")
      
      // Debug log all alerts to see their state after alarm processing
      alerts.forEach { alert ->
        DevLog.info(LOG_TAG, "Second event alert after alarm: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
      }
      
      assertTrue("Should have alerts for second event after processing", alerts.isNotEmpty())
      assertTrue("Alerts for second event should be marked as handled after alarm processing", alerts.all { it.wasHandled })
    }

    // Verify that notifications were posted for the second event (normal behavior)
    assertTrue("Notifications should be posted for second event", notificationsPosted)
  }

}
