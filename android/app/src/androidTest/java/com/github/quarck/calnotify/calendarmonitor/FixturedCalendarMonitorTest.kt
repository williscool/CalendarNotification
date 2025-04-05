package com.github.quarck.calnotify.calendarmonitor

import android.Manifest
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.broadcastreceivers.ManualEventAlarmBroadcastReceiver
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import com.github.quarck.calnotify.testutils.BaseCalendarTestFixture
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse

/**
 * Migrated tests using the new test fixture system
 * 
 * These tests demonstrate how to use the test fixtures for more complex scenarios
 * like calendar monitoring.
 */
@RunWith(AndroidJUnit4::class)
class FixturedCalendarMonitorTest {
    private val LOG_TAG = "FixturedCalMonTest"
    
    private lateinit var fixture: BaseCalendarTestFixture
    
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.RECEIVE_BOOT_COMPLETED
    )
    
    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up fixture for calendar monitor tests")
        fixture = BaseCalendarTestFixture.Builder().build()
        
        // Setup test calendar
        fixture.setupTestCalendar()
    }
    
    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up calendar monitor test fixture")
        if (::fixture.isInitialized) {
            fixture.cleanup()
        }
    }
    
    /**
     * Tests calendar monitoring through manual rescan triggered by PROVIDER_CHANGED.
     * 
     * This test verifies that:
     * 1. Events are detected during calendar scanning
     * 2. Alerts are added to monitor storage
     * 3. After time passes, alarms trigger event processing
     * 4. Alerts are marked as handled
     */
    @Test
    fun testCalendarMonitoringManualRescan() {
        DevLog.info(LOG_TAG, "Running testCalendarMonitoringManualRescan")
        
        // Get context and time provider from fixture
        val context = fixture.contextProvider.fakeContext
        val timeProvider = fixture.timeProvider
        
        // Reset monitor state and ensure firstScanEver is false
        val monitorState = CalendarMonitorState(context)
        monitorState.firstScanEver = false
        
        // Use consistent time method
        val startTime = timeProvider.testClock.currentTimeMillis()
        
        // Calculate event times
        fixture.eventStartTime = startTime + 60000 // 1 minute from start time
        fixture.reminderTime = fixture.eventStartTime - 30000 // 30 seconds before start
        fixture.testEventId = 1555 // Set a consistent test event ID
        
        DevLog.info(LOG_TAG, "Test starting with currentTime=$startTime, eventStartTime=${fixture.eventStartTime}, reminderTime=${fixture.reminderTime}")
        
        // Set up monitor state with proper timing
        monitorState.prevEventScanTo = startTime
        monitorState.prevEventFireFromScan = startTime
        monitorState.nextEventFireFromScan = fixture.reminderTime
        
        // Setup event details in the calendar provider
        fixture.calendarProvider.mockEventDetails(
            fixture.testEventId, 
            fixture.eventStartTime, 
            "Test Monitor Event",
            60000 // 1 minute duration
        )
        
        // Setup event reminders
        fixture.calendarProvider.mockEventReminders(fixture.testEventId, 30000)
        
        // Setup event alerts
        fixture.calendarProvider.mockEventAlerts(
            fixture.testEventId,
            fixture.eventStartTime,
            30000 // 30 seconds before event
        )
        
        // First verify no alerts exist
        MonitorStorage(context).classCustomUse { db ->
            val alerts = db.alerts
            assertEquals("Should start with no alerts", 0, alerts.size)
        }
        
        // Trigger initial calendar scan via ApplicationController
        ApplicationController.onCalendarChanged(context)
        
        // Wait for processing
        fixture.advanceTime(2000)
        
        // Verify alerts were added but not handled
        MonitorStorage(context).classCustomUse { db ->
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
        val advanceAmount = fixture.reminderTime - startTime + Consts.ALARM_THRESHOLD
        fixture.advanceTime(advanceAmount)
        
        val currentTimeAfterAdvance = timeProvider.testClock.currentTimeMillis()
        DevLog.info(LOG_TAG, "Current time after advance: $currentTimeAfterAdvance")
        
        // Verify timing condition will be met
        assertTrue("Current time + threshold should be greater than nextEventFireFromScan",
            currentTimeAfterAdvance + Consts.ALARM_THRESHOLD > monitorState.nextEventFireFromScan)
        
        // Set the last timer broadcast received to indicate an alarm happened
        fixture.contextProvider.setLastTimerBroadcastReceived(currentTimeAfterAdvance)
        
        // First trigger the alarm broadcast receiver
        val alarmIntent = Intent(context, ManualEventAlarmBroadcastReceiver::class.java).apply {
            action = "com.github.quarck.calnotify.MANUAL_EVENT_ALARM"
            putExtra("alert_time", fixture.reminderTime)
        }
        fixture.calendarProvider.mockCalendarMonitor.onAlarmBroadcast(context, alarmIntent)
        
        // Then let the service handle the intent that would have been created by the broadcast receiver
        val serviceIntent = Intent(context, CalendarMonitorService::class.java).apply {
            putExtra("alert_time", fixture.reminderTime)
            putExtra("rescan_monitor", true)
            putExtra("reload_calendar", false) // Important: don't reload calendar on alarm
            putExtra("start_delay", 0L)
        }
        
        // Send to mock service through context's startService
        context.startService(serviceIntent)
        
        // Small delay for processing
        fixture.advanceTime(1000)
        
        // Verify alerts were handled
        MonitorStorage(context).classCustomUse { db ->
            val alerts = db.alerts
            DevLog.info(LOG_TAG, "Found ${alerts.size} alerts after processing")
            alerts.forEach { alert ->
                DevLog.info(LOG_TAG, "Alert: eventId=${alert.eventId}, alertTime=${alert.alertTime}, wasHandled=${alert.wasHandled}")
            }
            assertTrue("Should have alerts after processing", alerts.isNotEmpty())
            assertTrue("Alerts should be marked as handled", alerts.all { it.wasHandled })
        }
        
        // Verify event was processed using the fixture's verification method
        assertTrue("Event should be processed", 
            fixture.applicationComponents.verifyEventProcessed(
                fixture.testEventId,
                fixture.eventStartTime,
                "Test Monitor Event"
            )
        )
        
        DevLog.info(LOG_TAG, "testCalendarMonitoringManualRescan completed successfully")
    }
} 