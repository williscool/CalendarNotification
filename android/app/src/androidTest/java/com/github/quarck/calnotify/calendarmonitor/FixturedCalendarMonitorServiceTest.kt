package com.github.quarck.calnotify.calendarmonitor

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.quarck.calnotify.logs.DevLog
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
            startDelay = startDelay
        )
        
        // Verify the event was processed correctly
        assertTrue("Event should be properly processed through delayed monitoring", eventProcessed)
        
        DevLog.info(LOG_TAG, "testDelayedProcessing completed successfully")
    }
} 
