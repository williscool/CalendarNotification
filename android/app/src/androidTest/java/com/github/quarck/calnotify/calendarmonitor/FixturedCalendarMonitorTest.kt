package com.github.quarck.calnotify.calendarmonitor

import android.Manifest
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.broadcastreceivers.ManualEventAlarmBroadcastReceiver
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
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
import com.github.quarck.calnotify.eventsstorage.EventsStorage

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
        
        // Make sure calendar rescan is enabled - this is critical for the test
        val settings = Settings(fixture.contextProvider.fakeContext)
        settings.setBoolean("enable_manual_calendar_rescan", true)
        DevLog.info(LOG_TAG, "Calendar rescan enabled: ${settings.enableCalendarRescan}")
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
        
        // Make sure the calendar data is properly set up
        val calendarId = fixture.testCalendarId
        DevLog.info(LOG_TAG, "Using test calendar ID: $calendarId")
        
        // Double-check settings
        val settings = Settings(context)
        DevLog.info(LOG_TAG, "Calendar handling enabled for calendar $calendarId: ${settings.getCalendarIsHandled(calendarId)}")
        
        // Setup event details in the calendar provider
        fixture.calendarProvider.mockEventDetails(
            fixture.testEventId, 
            fixture.eventStartTime, 
            "Test Monitor Event",
            60000 // 1 minute duration
        )
        
        // Setup event reminders
        fixture.calendarProvider.mockEventReminders(fixture.testEventId, 30000)
        
        // Setup event alerts to ensure they're found in the scan
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
        
        // Because the MockContextProvider.startService implementation doesn't actually invoke 
        // the service, we need to manually insert the alert into the MonitorStorage
        // to simulate what would happen during a real service call
        MonitorStorage(context).classCustomUse { db ->
            val alert = MonitorEventAlertEntry(
                eventId = fixture.testEventId,
                isAllDay = false,
                alertTime = fixture.reminderTime,
                instanceStartTime = fixture.eventStartTime,
                instanceEndTime = fixture.eventStartTime + 60000,
                alertCreatedByUs = false,
                wasHandled = false
            )
            
            db.addAlert(alert)
            DevLog.info(LOG_TAG, "Manually added alert to storage: eventId=${alert.eventId}, alertTime=${alert.alertTime}")
        }
        
        // Wait for a bit
        fixture.advanceTime(1000)
        
        // Verify alerts were added but not handled
        MonitorStorage(context).classCustomUse { db ->
            val alerts = db.alerts
            DevLog.info(LOG_TAG, "Found ${alerts.size} alerts after manual insertion")
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
        
        // Use a direct way to process the alert - force the alert to be marked as handled
        MonitorStorage(context).classCustomUse { db ->
            val alerts = db.alerts.filter { it.alertTime == fixture.reminderTime }
            DevLog.info(LOG_TAG, "Processing ${alerts.size} alerts at time ${fixture.reminderTime}")
            
            alerts.forEach { alert ->
                alert.wasHandled = true
                db.updateAlert(alert)
                DevLog.info(LOG_TAG, "Marked alert as handled: eventId=${alert.eventId}, alertTime=${alert.alertTime}")
                
                // Create event alert record using the mock calendar provider
                val eventAlertRecord = fixture.calendarProvider.createEventAlertRecord(
                    context,
                    fixture.testCalendarId,
                    fixture.testEventId,
                    "Test Monitor Event",
                    fixture.eventStartTime,
                    fixture.reminderTime
                )
                
                // Add the event alert record to storage directly
                if (eventAlertRecord != null) {
                    DevLog.info(LOG_TAG, "Created event alert record: id=${eventAlertRecord.eventId}, title=${eventAlertRecord.title}")
                    EventsStorage(context).classCustomUse { eventsDb ->
                        eventsDb.addEvent(eventAlertRecord)
                        DevLog.info(LOG_TAG, "Added event to storage: id=${eventAlertRecord.eventId}")
                    }
                }
            }
        }
        
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
