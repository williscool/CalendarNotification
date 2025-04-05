package com.github.quarck.calnotify.calendarmonitor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.MockTimeProvider
import com.github.quarck.calnotify.testutils.MockContextProvider
import com.github.quarck.calnotify.testutils.MockCalendarProvider
import com.github.quarck.calnotify.testutils.MockApplicationComponents
import io.mockk.MockKAnnotations
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.Manifest
import com.github.quarck.calnotify.app.ApplicationController
import org.junit.Ignore
import io.mockk.mockkObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkObject

/**
 * Test class that tests each mock component in isolation
 * to identify which one is causing the infinite looping issue.
 */
@RunWith(AndroidJUnit4::class)
class ComponentIsolationTest {
    private val LOG_TAG = "ComponentIsoTest"
    
    // Components will be initialized only when needed by each test
    private lateinit var timeProvider: MockTimeProvider
    private lateinit var contextProvider: MockContextProvider
    private lateinit var calendarProvider: MockCalendarProvider
    private lateinit var applicationComponents: MockApplicationComponents
    
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.RECEIVE_BOOT_COMPLETED
    )
    
    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up base test environment")
        MockKAnnotations.init(this)
    }
    
    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test environment")
        
        // Clean up only components that were actually initialized
        if (::applicationComponents.isInitialized) {
            applicationComponents.cleanup()
        }
        if (::calendarProvider.isInitialized) {
            calendarProvider.cleanup()
        }
        if (::contextProvider.isInitialized) {
            contextProvider.cleanup()
        }
        if (::timeProvider.isInitialized) {
            timeProvider.cleanup()
        }
        
        unmockkAll()
    }
    
    /**
     * Test 1: Initialize and test just MockTimeProvider
     */
    @Test
    fun testTimeProvider() {
        DevLog.info(LOG_TAG, "Running testTimeProvider")
        
        // Create and initialize time provider
        timeProvider = MockTimeProvider()
        timeProvider.setup()
        
        // Verify basic functionality
        val initialTime = timeProvider.testClock.currentTimeMillis()
        timeProvider.advanceTime(1000)
        val newTime = timeProvider.testClock.currentTimeMillis()
        
        // Verify time was advanced correctly
        assertEquals("Time should be advanced by 1000ms", initialTime + 1000, newTime)
        
        DevLog.info(LOG_TAG, "testTimeProvider completed successfully")
    }
    
    /**
     * Test 2: Initialize and test MockContextProvider with MockTimeProvider
     */
    @Test
    fun testContextProvider() {
        DevLog.info(LOG_TAG, "Running testContextProvider")
        
        // Create and initialize time provider first
        timeProvider = MockTimeProvider()
        timeProvider.setup()
        
        // Create and initialize context provider
        contextProvider = MockContextProvider(timeProvider)
        contextProvider.setup()
        
        // Verify basic functionality
        assertNotNull("Context should be initialized", contextProvider.fakeContext)
        assertNotNull("AlarmManager should be initialized", contextProvider.mockAlarmManager)
        
        // Test accessing mock shared preferences
        val prefs = contextProvider.fakeContext.getSharedPreferences("test_prefs", 0)
        prefs.edit().putString("test_key", "test_value").apply()
        val value = prefs.getString("test_key", "")
        
        // Verify shared preferences mock works
        assertEquals("Should be able to store and retrieve preferences", "test_value", value)
        
        DevLog.info(LOG_TAG, "testContextProvider completed successfully")
    }
    
    /**
     * Test 3: Initialize and test MockCalendarProvider with dependencies
     */
    @Test
    fun testCalendarProvider() {
        DevLog.info(LOG_TAG, "Running testCalendarProvider")
        
        // Initialize dependencies first
        timeProvider = MockTimeProvider()
        timeProvider.setup()
        
        contextProvider = MockContextProvider(timeProvider)
        contextProvider.setup()
        
        // Create and initialize calendar provider
        calendarProvider = MockCalendarProvider(contextProvider, timeProvider)
        calendarProvider.setup()
        
        // Verify basic functionality
        assertNotNull("CalendarMonitor should be initialized", calendarProvider.mockCalendarMonitor)
        
        // Create a test calendar and verify it works
        val calendarId = calendarProvider.createTestCalendar(
            contextProvider.fakeContext,
            "Test Calendar",
            "test@example.com",
            "test@example.com"
        )
        
        assertTrue("Should be able to create a test calendar", calendarId > 0)
        
        DevLog.info(LOG_TAG, "testCalendarProvider completed successfully")
    }
    
    /**
     * Test 4: Initialize and test MockApplicationComponents with dependencies
     */
    @Test(timeout = 100000) // Add timeout of 10 seconds to prevent infinite loops
    fun testApplicationComponents() {
        DevLog.info(LOG_TAG, "Running testApplicationComponents")
        
        try {
            // Initialize dependencies first
            DevLog.info(LOG_TAG, "Initializing MockTimeProvider")
            timeProvider = MockTimeProvider()
            timeProvider.setup()
            DevLog.info(LOG_TAG, "MockTimeProvider initialized successfully")
            
            DevLog.info(LOG_TAG, "Initializing MockContextProvider")
            contextProvider = MockContextProvider(timeProvider)
            contextProvider.setup()
            DevLog.info(LOG_TAG, "MockContextProvider initialized successfully")
            
            DevLog.info(LOG_TAG, "Initializing MockCalendarProvider")
            calendarProvider = MockCalendarProvider(contextProvider, timeProvider)
            calendarProvider.setup() 
            DevLog.info(LOG_TAG, "MockCalendarProvider initialized successfully")
            
            // Create and initialize application components - THIS IS WHERE THE LOOP HAPPENS
            DevLog.info(LOG_TAG, "Initializing MockApplicationComponents - WATCH FOR RECURSION")
            applicationComponents = MockApplicationComponents(
                contextProvider,
                timeProvider,
                calendarProvider
            )
            DevLog.info(LOG_TAG, "Created MockApplicationComponents instance, now calling setup()")
            applicationComponents.setup()
            DevLog.info(LOG_TAG, "MockApplicationComponents setup completed successfully!")
            
            // Verify basic functionality
            assertNotNull("Formatter should be initialized", applicationComponents.mockFormatter)
            assertNotNull("NotificationManager should be initialized", applicationComponents.mockNotificationManager)
            assertNotNull("AlarmScheduler should be initialized", applicationComponents.mockAlarmScheduler)
            
            // Verify event verification methods work
            assertTrue("Should report no events initially", applicationComponents.verifyNoEvents())
            
            DevLog.info(LOG_TAG, "testApplicationComponents completed successfully")
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "ERROR in testApplicationComponents: ${e.message}")
            e.printStackTrace()
            throw e // Re-throw to fail the test
        }
    }
    
    /**
     * Test 5: Test all components initialized in sequence (full fixture)
     */
    @Test
    fun testAllComponentsSequentially() {
        DevLog.info(LOG_TAG, "Running testAllComponentsSequentially")
        
        // Initialize each component sequentially with logging
        DevLog.info(LOG_TAG, "Initializing MockTimeProvider")
        timeProvider = MockTimeProvider()
        timeProvider.setup()
        DevLog.info(LOG_TAG, "MockTimeProvider successfully initialized")
        
        DevLog.info(LOG_TAG, "Initializing MockContextProvider")
        contextProvider = MockContextProvider(timeProvider)
        contextProvider.setup()
        DevLog.info(LOG_TAG, "MockContextProvider successfully initialized")
        
        DevLog.info(LOG_TAG, "Initializing MockCalendarProvider")
        calendarProvider = MockCalendarProvider(contextProvider, timeProvider)
        calendarProvider.setup()
        DevLog.info(LOG_TAG, "MockCalendarProvider successfully initialized")
        
        DevLog.info(LOG_TAG, "Initializing MockApplicationComponents")
        applicationComponents = MockApplicationComponents(
            contextProvider,
            timeProvider,
            calendarProvider
        )
        applicationComponents.setup()
        DevLog.info(LOG_TAG, "MockApplicationComponents successfully initialized")
        
        // Create a test calendar
        val calendarId = calendarProvider.createTestCalendar(
            contextProvider.fakeContext,
            "Test Calendar",
            "test@example.com",
            "test@example.com"
        )
        
        assertTrue("Should be able to create a test calendar", calendarId > 0)
        
        // Create a test event
        val eventId = calendarProvider.createTestEvent(
            contextProvider.fakeContext,
            calendarId,
            "Test Event"
        )
        
        assertTrue("Should be able to create a test event", eventId > 0)
        
        DevLog.info(LOG_TAG, "testAllComponentsSequentially completed successfully")
    }

} 
