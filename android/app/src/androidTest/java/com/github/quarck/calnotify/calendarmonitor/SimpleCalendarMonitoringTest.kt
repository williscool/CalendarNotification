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
 * Simple Calendar Monitoring Test using the new test fixture system
 * 
 * This test demonstrates the simplified approach to testing
 * calendar monitoring using the specialized fixture.
 */
@RunWith(AndroidJUnit4::class)
class SimpleCalendarMonitoringTest {
    private val LOG_TAG = "SimpleCalMonTest"
    
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
        DevLog.info(LOG_TAG, "Setting up test with CalendarMonitorTestFixture")
        fixture = CalendarMonitorTestFixture()
        
        // Clear any existing test state
        fixture.clearTestState()
    }
    
    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test resources")
        if (::fixture.isInitialized) {
            fixture.cleanup()
        }
    }
    
    /**
     * Tests the complete monitoring flow using the specialized fixture
     * 
     * This test demonstrates the simplicity of the new fixture system
     * which handles all the complex setup and verification internally.
     */
    @Test
    fun testSimpleCalendarMonitoring() {
        DevLog.info(LOG_TAG, "Running testSimpleCalendarMonitoring")
        
        // Run the full monitoring sequence in a single call
        val eventProcessed = fixture.runFullMonitoringSequence(title = "Simple Test Event")
        
        // Simple assertion on the final result
        assertTrue("Event should be properly processed through monitoring", eventProcessed)
    }
    
    /**
     * Tests the monitoring flow step by step
     * 
     * This test demonstrates the more granular control available
     * when using the fixture's individual methods.
     */
    @Test
    fun testStepByStepMonitoring() {
        DevLog.info(LOG_TAG, "Running testStepByStepMonitoring")
        
        // Create test event with custom parameters
        fixture.withTestEvent(
            title = "Step-by-Step Test Event",
            startTimeOffset = 120000, // 2 minutes from now
            reminderOffset = 60000    // 1 minute before event
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
            fixture.verifyEventProcessed(title = "Step-by-Step Test Event"))
    }
} 