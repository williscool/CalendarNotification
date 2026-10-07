package com.github.quarck.calnotify.calendarmonitor

import android.content.Context
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.MockApplicationComponents
import com.github.quarck.calnotify.testutils.MockCalendarProvider
import com.github.quarck.calnotify.testutils.MockContextProvider
import com.github.quarck.calnotify.testutils.MockTimeProvider
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Robolectric version of ComponentIsolationTest
 * 
 * Tests each mock component in isolation to verify they work correctly in Robolectric environment.
 * 
 * **Key Differences from Instrumentation Test:**
 * 
 * 1. **EventsStorage Access**: The instrumentation version clears and verifies EventsStorage directly,
 *    but this Robolectric version does not. This is because:
 *    - EventsStorage uses the native SQLite library (`sqlite3x`) which is not available in Robolectric's JVM environment
 *    - Any attempt to access EventsStorage.writableDatabase triggers `UnsatisfiedLinkError: no sqlite3x in java.library.path`
 *    - The instrumentation test can use real SQLite because it runs on a device/emulator with native libraries
 * 
 * 2. **Test Isolation**: Robolectric provides a fresh Context and storage per test automatically, so explicit
 *    storage clearing is not necessary. Each test runs in isolation with clean state.
 * 
 * 3. **Storage Verification**: The instrumentation test verifies EventsStorage is empty using direct database queries
 *    and `verifyNoEvents()`. In Robolectric, we skip this verification because:
 *    - The test's primary purpose is verifying component initialization, not storage operations
 *    - Storage verification would require real database access which isn't available
 *    - Component isolation tests focus on mock component setup, not integration with storage
 * 
 * 4. **Test Faithfulness**: While we can't verify storage state, this test still faithfully verifies:
 *    - All mock components (TimeProvider, ContextProvider, CalendarProvider, ApplicationComponents) initialize correctly
 *    - Components can be created and configured in isolation
 *    - The component initialization sequence works as expected
 * 
 * These differences are acceptable adaptations for the Robolectric environment while maintaining the core
 * purpose of the test: verifying that mock components can be initialized and configured correctly.
 * 
 * @see ComponentIsolationTest for the instrumentation version that uses real EventsStorage
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest="AndroidManifest.xml", sdk = [24])
class ComponentIsolationRobolectricTest {
    private val LOG_TAG = "ComponentIsoRobolectricTest"
    
    // Components will be initialized only when needed by each test
    private lateinit var timeProvider: MockTimeProvider
    private lateinit var contextProvider: MockContextProvider
    private lateinit var calendarProvider: MockCalendarProvider
    private lateinit var applicationComponents: MockApplicationComponents
    
    @Before
    fun setup() {
        ShadowLog.stream = System.out
        DevLog.info(LOG_TAG, "Setting up base test environment")
        MockKAnnotations.init(this)
        
        // Clear any previous mock states
        unmockkAll()
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
        val prefs = contextProvider.fakeContext!!.getSharedPreferences("test_prefs", 0)
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
            contextProvider.fakeContext!!,
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
            
            // NOTE: The instrumentation version clears EventsStorage here using:
            //   EventsStorage(contextProvider.fakeContext).use { db -> db.deleteAllEvents() }
            // 
            // We skip this in Robolectric because:
            // 1. EventsStorage requires native SQLite library (sqlite3x) which isn't available in JVM environment
            // 2. Robolectric provides fresh Context per test, so storage is already clean
            // 3. This test focuses on component initialization, not storage operations
            // 
            // See class-level documentation for more details on Robolectric vs Instrumentation differences.
            
            DevLog.info(LOG_TAG, "Initializing MockCalendarProvider")
            calendarProvider = MockCalendarProvider(contextProvider, timeProvider)
            calendarProvider.setup() 
            DevLog.info(LOG_TAG, "MockCalendarProvider initialized successfully")
            
            // Create and initialize application components
            DevLog.info(LOG_TAG, "Initializing MockApplicationComponents - WATCH FOR RECURSION")
            applicationComponents = MockApplicationComponents(
                contextProvider,
                timeProvider,
                calendarProvider
            )
            
            // Mock CalendarMonitor to prevent automatic event scanning
            mockkObject(ApplicationController)
            every { ApplicationController.CalendarMonitor.onRescanFromService(any()) } just Runs
            
            DevLog.info(LOG_TAG, "Created MockApplicationComponents instance, now calling setup()")
            applicationComponents.setup()
            DevLog.info(LOG_TAG, "MockApplicationComponents setup completed successfully!")
            
            // Verify basic functionality
            assertNotNull("Formatter should be initialized", applicationComponents.mockFormatter)
            assertNotNull("NotificationManager should be initialized", applicationComponents.mockNotificationManager)
            assertNotNull("AlarmScheduler should be initialized", applicationComponents.mockAlarmScheduler)
            
            // NOTE: Difference from instrumentation test
            // The instrumentation version verifies EventsStorage state here:
            //   1. Direct verification: EventsStorage(context).use { db -> assertTrue(db.events.isEmpty()) }
            //   2. Indirect verification: applicationComponents.verifyNoEvents()
            //
            // We skip both in Robolectric because:
            // - EventsStorage requires native SQLite library (sqlite3x) not available in JVM
            // - Accessing writableDatabase property triggers UnsatisfiedLinkError
            // - verifyNoEvents() internally accesses EventsStorage, so it has the same issue
            //
            // This is acceptable because:
            // - Robolectric provides fresh Context per test = fresh storage automatically
            // - Test purpose is component initialization verification, which we still achieve
            // - Storage verification can be tested in integration-style tests that mock the storage interface
            //
            // The component initialization verification above is the core purpose of this test and still passes.
            
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
            contextProvider.fakeContext!!,
            "Test Calendar",
            "test@example.com",
            "test@example.com"
        )
        
        assertTrue("Should be able to create a test calendar", calendarId > 0)
        
        // Create a test event
        val eventId = calendarProvider.createTestEvent(
            contextProvider.fakeContext!!,
            calendarId,
            "Test Event"
        )
        
        assertTrue("Should be able to create a test event", eventId > 0)
        
        DevLog.info(LOG_TAG, "testAllComponentsSequentially completed successfully")
    }
}

