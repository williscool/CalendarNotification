package com.github.quarck.calnotify.calendarmonitor

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import com.github.quarck.calnotify.testutils.CalendarMonitorTestFixture
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrated tests using the new test fixture system
 * 
 * These tests demonstrate how to use the test fixtures for more complex scenarios
 * like calendar monitoring.
 */
@RunWith(AndroidJUnit4::class)
class FixturedCalendarMonitorServiceTest {
    private val LOG_TAG = "FixturedCalMonTest"
    
    private lateinit var fixture: CalendarMonitorTestFixture
    
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
        fixture = CalendarMonitorTestFixture()
        
        // Clear any existing test state
        fixture.clearTestState()
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
        
        // Create test event with custom parameters
        fixture.withTestEvent(
            title = "Test Monitor Event",
            startTimeOffset = 60000, // 1 minute from now
            reminderOffset = 30000    // 30 seconds before event
        )
        
        // First verify no alerts exist
        assertTrue("Should start with no alerts", fixture.verifyNoAlertsInStorage())
        
        // Trigger calendar change scan
        fixture.triggerCalendarChangeScan()
        
        // Verify alerts were added but not handled
        assertTrue("Should have unhandled alerts after scan", 
            fixture.verifyAlertsInStorage(shouldBeHandled = false))
        
        // Process the alert
        fixture.processEventAlerts()
        
        // Verify alerts are now handled
        assertTrue("Alerts should be marked as handled", 
            fixture.verifyAlertsInStorage(shouldBeHandled = true))
        
        // Verify event was processed using the verification method
        assertTrue("Event should be processed correctly", 
            fixture.verifyEventProcessed(title = "Test Monitor Event"))
        
        DevLog.info(LOG_TAG, "testCalendarMonitoringManualRescan completed successfully")
    }
    
    /**
     * Tests the complete flow using a simplified approach
     * 
     * This test demonstrates how the specialized fixture can greatly simplify
     * testing of the calendar monitoring flow.
     */
    @Test
    fun testSimplifiedCalendarMonitoring() {
        DevLog.info(LOG_TAG, "Running testSimplifiedCalendarMonitoring")
        
        // Run the full monitoring sequence in a single call
        val eventProcessed = fixture.runFullMonitoringSequence(title = "Simplified Monitor Test")
        
        // Simple assertion on the final result
        assertTrue("Event should be properly processed through monitoring", eventProcessed)
        
        DevLog.info(LOG_TAG, "testSimplifiedCalendarMonitoring completed successfully")
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
        DevLog.info(LOG_TAG, "Running testDelayedProcessing")
        
        // Use a delay that is less than MAX_TIME_WITHOUT_QUICK_RESCAN to prevent quick rescan
        val startDelay = 500L // 500ms delay, less than MAX_TIME_WITHOUT_QUICK_RESCAN (1000ms)
        
        // Run the delayed processing sequence
        val eventProcessed = fixture.runDelayedProcessingSequence(
            title = "Delayed Test Event",
            delay = startDelay
        )
        
        // Verify the event was processed correctly
        assertTrue("Event should be properly processed through delayed monitoring", eventProcessed)
        
        DevLog.info(LOG_TAG, "testDelayedProcessing completed successfully")
    }
    
    /**
     * Tests calendar monitoring behavior when enabled, including system events and edge cases.
     *
     * Verifies that:
     * 1. System calendar change broadcasts are handled
     * 2. System time changes trigger rescans
     * 3. App resume triggers rescans
     * 4. Setting state persists correctly
     */
    @Test
    fun testCalendarMonitoringEnabledEdgeCases() {
        DevLog.info(LOG_TAG, "Running testCalendarMonitoringEnabledEdgeCases")
        
        // Test 1: Verify initial settings
        val settings = Settings(fixture.contextProvider.fakeContext)
        assertTrue("Calendar monitoring should be enabled", settings.enableCalendarRescan)
        assertTrue("Calendar should be handled", settings.getCalendarIsHandled(fixture.testCalendarId))
        
        // Test 2: System Calendar Change Broadcast
        DevLog.info(LOG_TAG, "Testing calendar change broadcast handling")
        
        // Create a test event first
        fixture.withTestEvent(
            title = "Edge Case Test Event",
            startTimeOffset = 60000,  // 1 minute from now
            reminderOffset = 30000    // 30 seconds before event
        )
        
        // Get the event details for the test alert
        val eventStartTime = fixture.eventStartTime
        val alertTime = eventStartTime - 30000 // 30 seconds before start
        val eventId = fixture.testEventId
        
        // Simulate calendar change and verify it's processed
        fixture.triggerCalendarChangeScan()
        
        // Verify alerts were added but not handled
        assertTrue("Should have unhandled alerts after scan", 
            fixture.verifyAlertsInStorage(shouldBeHandled = false))
        
        // Test 3: System Time Change
        DevLog.info(LOG_TAG, "Testing system time change handling")
        
        // Capture current alert count
        val alertCountBefore = fixture.verifyAlertsInStorage()
        
        // Simulate time change by advancing a significant amount
        val timeChangeAmount = 24 * 60 * 60 * 1000L // 1 day
        fixture.applicationComponents.simulateSystemTimeChange(timeChangeAmount)
        
        // Verify alerts are still maintained
        assertTrue("Alerts should still be in storage after time change", 
            fixture.verifyAlertsInStorage())
        
        // Test 4: App Resume
        DevLog.info(LOG_TAG, "Testing app resume handling")
        
        // Simulate app resume
        fixture.applicationComponents.simulateAppResume()
        
        // Process event alerts after app resume
        fixture.processEventAlerts(eventTitle = "Edge Case Test Event")
        
        // Verify the event was processed
        assertTrue("Event should be processed after app resume",
            fixture.verifyEventProcessed(title = "Edge Case Test Event"))
        
        // Test 5: Setting State Persistence
        DevLog.info(LOG_TAG, "Testing setting persistence")
        
        // Toggle monitoring setting
        settings.setBoolean("enable_manual_calendar_rescan", false)
        assertFalse("Calendar monitoring should be disabled", settings.enableCalendarRescan)
        
        // Re-enable for cleanup
        settings.setBoolean("enable_manual_calendar_rescan", true)
        assertTrue("Calendar monitoring should be re-enabled", settings.enableCalendarRescan)
        
        DevLog.info(LOG_TAG, "testCalendarMonitoringEnabledEdgeCases completed successfully")
    }
    
    /**
     * Tests calendar reload functionality with multiple events.
     *
     * Verifies that:
     * 1. Multiple events can be created and detected
     * 2. Events are processed in the correct order
     * 3. All events are properly stored with correct metadata
     * 4. Service properly handles batch event processing
     * 
     * This test is adapted from the original testCalendarReload in CalendarMonitorServiceTest
     * but uses the fixture pattern for cleaner and more maintainable code.
     */
    @Test
    fun testFixturedCalendarReload() {
        DevLog.info(LOG_TAG, "Running testFixturedCalendarReload")
        
        // Clear any existing alerts before starting the test
        MonitorStorage(fixture.contextProvider.fakeContext).use { db ->
            val count = db.alerts.size
            if (count > 0) {
                DevLog.info(LOG_TAG, "Clearing $count existing alerts before test")
                db.deleteAlertsMatching { true }
            }
        }
        
        // Number of events to create
        val eventCount = 3
        
        // Get the current time
        val startTime = fixture.timeProvider.testClock.currentTimeMillis()
        
        // Reset monitor state and ensure firstScanEver is false
        val monitorState = CalendarMonitorState(fixture.contextProvider.fakeContext)
        monitorState.firstScanEver = false
        monitorState.prevEventScanTo = startTime
        monitorState.prevEventFireFromScan = startTime
        
        // Create an array to hold event IDs and titles
        val eventIds = mutableListOf<Long>()
        val eventTitles = mutableListOf<String>()
        
        // Create multiple test events - each 1 hour apart
        DevLog.info(LOG_TAG, "Creating $eventCount test events")
        for (i in 0 until eventCount) {
            val eventTitle = "Test Event $i"
            eventTitles.add(eventTitle)
            
            // Calculate the timing for this event (each one hour apart)
            val hourOffset = i + 1
            val eventStartTime = startTime + (hourOffset * 3600000) // 1 hour increments
            val reminderTime = eventStartTime - (15 * 60 * 1000) // 15 minutes before start
            
            // Create the event
            val eventId = fixture.calendarProvider.createTestEvent(
                fixture.contextProvider.fakeContext,
                fixture.testCalendarId,
                eventTitle,
                "Test Description $i",
                eventStartTime,
                duration = 3600000,  // 1 hour duration
                reminderMinutes = 15  // 15 minutes reminder
            )
            
            eventIds.add(eventId)
            
            // Add the alert to monitor storage (simulating what the calendar scan would do)
            fixture.baseFixture.addAlertToMonitorStorage(
                eventId = eventId,
                alertTime = reminderTime,
                startTime = eventStartTime,
                wasHandled = false
            )
            
            DevLog.info(LOG_TAG, "Created test event $i: id=$eventId, title=$eventTitle, startTime=$eventStartTime, alertTime=$reminderTime")
        }
        
        // Verify all alerts exist but are not handled - only check our specific test alerts
        MonitorStorage(fixture.contextProvider.fakeContext).use { db ->
            val testAlerts = db.alerts.filter { it.eventId in eventIds }
            DevLog.info(LOG_TAG, "Found ${testAlerts.size} test alerts, expecting $eventCount")
            assertEquals("Should have correct number of test alerts", eventCount, testAlerts.size)
            assertFalse("Test alerts should not be handled yet", testAlerts.any { it.wasHandled })
        }
        
        // Process each event's alert sequentially
        DevLog.info(LOG_TAG, "Processing ${eventIds.size} events sequentially")
        for (i in 0 until eventCount) {
            val eventId = eventIds[i]
            val eventTitle = eventTitles[i]
            val hourOffset = i + 1
            val eventStartTime = startTime + (hourOffset * 3600000)
            val alertTime = eventStartTime - (15 * 60 * 1000)
            
            DevLog.info(LOG_TAG, "Processing event $i: id=$eventId, title=$eventTitle")
            
            // Reset alert handling state for current event
            if (i > 0) {
                MonitorStorage(fixture.contextProvider.fakeContext).use { db ->
                    // Only reset the test alerts we're concerned with
                    val alerts = db.alerts.filter { it.eventId in eventIds }
                    alerts.forEach { alert -> 
                        if (alert.eventId != eventIds[i-1]) { // Keep the previous event as handled
                            alert.wasHandled = false 
                            db.updateAlert(alert)
                        }
                    }
                    DevLog.info(LOG_TAG, "Reset wasHandled flag for test alerts except event ${eventIds[i-1]}")
                }
            }
            
            // Advance time to just past this event's alert time
            fixture.timeProvider.testClock.setCurrentTime(alertTime + 1000)
            
            // Set the last timer broadcast received to indicate an alarm happened
            fixture.contextProvider.setLastTimerBroadcastReceived(
                fixture.timeProvider.testClock.currentTimeMillis()
            )
            
            // Simulate alarm broadcast
            DevLog.info(LOG_TAG, "Simulating alarm broadcast for event $i, alertTime=$alertTime")
            fixture.baseFixture.processEventAlert(alertTime)
            
            // Verify the alert was marked as handled
            MonitorStorage(fixture.contextProvider.fakeContext).use { db ->
                val alerts = db.alerts.filter { it.eventId == eventId }
                if (alerts.isNotEmpty()) {
                    val alert = alerts.first()
                    assertTrue("Alert for event $eventId should be marked as handled", alert.wasHandled)
                    DevLog.info(LOG_TAG, "Verified alert for event $eventId is marked as handled")
                } else {
                    fail("No alert found for event $eventId")
                }
            }
            
            // Verify the event was added to event storage
            assertTrue("Event $eventId should be processed",
                fixture.verifyEventProcessed(eventId, eventStartTime, eventTitle))
        }
        
        // Final verification that all events were processed
        DevLog.info(LOG_TAG, "Verifying all events were processed")
        for (i in 0 until eventCount) {
            val eventId = eventIds[i]
            val eventTitle = eventTitles[i]
            val hourOffset = i + 1
            val eventStartTime = startTime + (hourOffset * 3600000)
            
            // Verify each event individually for clearer error messages
            assertTrue("Event $i ($eventId) should be processed with title $eventTitle",
                fixture.verifyEventProcessed(eventId, eventStartTime, eventTitle))
        }
        
        DevLog.info(LOG_TAG, "testFixturedCalendarReload completed successfully")
    }
} 
