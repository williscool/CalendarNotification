package com.github.quarck.calnotify.calendarmonitor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.BaseCalendarTestFixture
import com.github.quarck.calnotify.testutils.DirectReminderTestFixture
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.Manifest

/**
 * Example test using the new test fixture system
 *
 * This test demonstrates how to use the test fixtures to create
 * simple, readable tests for calendar notification functionality.
 */
@RunWith(AndroidJUnit4::class)
class CalendarTestFixtureExampleTest {
    private val LOG_TAG = "FixtureExampleTest"
    
    private lateinit var baseFixture: BaseCalendarTestFixture
    
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.RECEIVE_BOOT_COMPLETED
    )
    
    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up example test")
        // Create the base fixture
        baseFixture = BaseCalendarTestFixture.Builder().build()
        
        // Set up a test calendar
        baseFixture.setupTestCalendar("Example Test Calendar")
    }
    
    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up example test")
        baseFixture.cleanup()
    }
    
    /**
     * Example test using the base fixture
     *
     * This test demonstrates creating a test event and verifying
     * it was processed correctly using the base fixture.
     */
    @Test
    fun testBaseFixtureEventCreation() {
        DevLog.info(LOG_TAG, "Running testBaseFixtureEventCreation")
        
        // Get the context
        val context = baseFixture.contextProvider.fakeContext
        
        // Set current time
        val currentTime = baseFixture.timeProvider.testClock.currentTimeMillis()
        
        // Create a test event
        baseFixture.testEventId = baseFixture.calendarProvider.createTestEvent(
            context,
            baseFixture.testCalendarId,
            "Test Base Fixture Event",
            "Test Description",
            currentTime + 60000 // 1 minute from now
        )
        
        // Save event start time
        baseFixture.eventStartTime = currentTime + 60000
        
        // Verify the event was created
        assertTrue("Event should have been created with valid ID", baseFixture.testEventId > 0)
        
        // Verify event details
        val eventExists = baseFixture.applicationComponents.verifyEventProcessed(
            baseFixture.testEventId,
            baseFixture.eventStartTime,
            "Test Base Fixture Event"
        )
        
        assertFalse("Event should not have been processed yet", eventExists)
    }
    
    /**
     * Example test using the DirectReminderTestFixture
     *
     * This test demonstrates how the specialized fixture simplifies
     * testing the direct reminder flow.
     */
    @Test
    fun testDirectReminderFixture() {
        DevLog.info(LOG_TAG, "Running testDirectReminderFixture")
        
        // Create and configure the specialized fixture
        val directReminderFixture = DirectReminderTestFixture()
            .withTestEvent("Direct Reminder Test Event")
        
        try {
            // Simulate a direct reminder broadcast
            directReminderFixture.simulateReminderBroadcast()
            
            // Verify the event was processed
            val eventProcessed = directReminderFixture.verifyDirectReminderProcessed()
            
            assertTrue("Event should have been processed through direct reminder", eventProcessed)
        } finally {
            // Clean up resources
            directReminderFixture.cleanup()
        }
    }
} 