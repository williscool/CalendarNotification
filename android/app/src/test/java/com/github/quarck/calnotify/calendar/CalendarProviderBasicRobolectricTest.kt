package com.github.quarck.calnotify.calendar

import android.content.Context
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.MockApplicationComponents
import com.github.quarck.calnotify.testutils.MockCalendarProvider
import com.github.quarck.calnotify.testutils.MockContextProvider
import com.github.quarck.calnotify.testutils.MockTimeProvider
import io.mockk.MockKAnnotations
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
 * Robolectric version of CalendarProviderBasicTest
 * 
 * Tests basic calendar provider operations using mocked calendar data
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest="AndroidManifest.xml", sdk = [24])
class CalendarProviderBasicRobolectricTest {
    private val LOG_TAG = "CalProviderBasicRobolectricTest"
    
    private lateinit var mockTimeProvider: MockTimeProvider
    private lateinit var mockContextProvider: MockContextProvider
    private lateinit var mockCalendarProvider: MockCalendarProvider
    private lateinit var mockComponents: MockApplicationComponents
    
    // Non-null context helper
    private val context: Context
        get() = mockContextProvider.fakeContext!!
    
    @Before
    fun setup() {
        ShadowLog.stream = System.out
        DevLog.info(LOG_TAG, "Setting up test environment")
        
        MockKAnnotations.init(this)
        unmockkAll()
        
        // Note: Permissions are granted automatically by MockContextProvider.setup()
        
        // Setup mock time provider
        mockTimeProvider = MockTimeProvider(1635724800000) // 2021-11-01 00:00:00 UTC
        mockTimeProvider.setup()
        
        // Setup mock context provider
        mockContextProvider = MockContextProvider(mockTimeProvider)
        mockContextProvider.setup()
        
        // Setup mock calendar provider
        mockCalendarProvider = MockCalendarProvider(mockContextProvider, mockTimeProvider)
        mockCalendarProvider.setup()
        
        // Setup mock components
        mockComponents = MockApplicationComponents(
            mockContextProvider,
            mockTimeProvider,
            mockCalendarProvider
        )
        mockComponents.setup()
        
        // Clear all calendar data
        mockCalendarProvider.clearAll()
    }
    
    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test environment")
        mockCalendarProvider.clearAll()
        unmockkAll()
    }
    
    /**
     * Helper method to verify calendar properties
     */
    private fun verifyCalendar(
        calendarId: Long,
        expectedDisplayName: String? = null,
        expectedAccountName: String? = null,
        expectedOwnerAccount: String? = null,
        expectedIsVisible: Boolean? = null,
        expectedIsHandled: Boolean? = null
    ) {
        val calendar = CalendarProvider.getCalendarById(context, calendarId)
        
        DevLog.info(LOG_TAG, "Verifying calendar: id=$calendarId")
        
        if (expectedDisplayName != null) {
            assertEquals("Calendar display name mismatch", expectedDisplayName, calendar?.displayName)
        }
        if (expectedAccountName != null) {
            assertEquals("Calendar account name mismatch", expectedAccountName, calendar?.accountName)
        }
        if (expectedOwnerAccount != null) {
            assertEquals("Calendar owner mismatch", expectedOwnerAccount, calendar?.owner)
        }
        if (expectedIsVisible != null) {
            assertEquals("Calendar visibility mismatch", expectedIsVisible, calendar?.isVisible)
        }
        if (expectedIsHandled != null) {
            val settings = Settings(context)
            val isHandled = settings.getCalendarIsHandled(calendarId)
            DevLog.info(LOG_TAG, "Checking calendar handled state: id=$calendarId, expected=$expectedIsHandled, actual=$isHandled")
            assertEquals("Calendar handled state mismatch", expectedIsHandled, isHandled)
        }
    }
    
    @Test
    fun testCreateAndGetCalendar() {
        DevLog.info(LOG_TAG, "Running testCreateAndGetCalendar")
        
        // Create calendar with basic settings
        val calendarId = mockCalendarProvider.createTestCalendar(
            context,
            "Test Calendar",
            "test@example.com",
            "test@example.com"
        )
        
        // Verify calendar was created
        assertTrue("Calendar ID should be positive", calendarId > 0)
        
        // Verify calendar properties
        verifyCalendar(
            calendarId = calendarId,
            expectedDisplayName = "Test Calendar",
            expectedAccountName = "test@example.com",
            expectedOwnerAccount = "test@example.com",
            expectedIsVisible = true,
            expectedIsHandled = true
        )
    }
    
    @Test
    fun testCreateInvisibleCalendar() {
        DevLog.info(LOG_TAG, "Running testCreateInvisibleCalendar")
        
        // Create calendar that's not visible
        val calendarId = mockCalendarProvider.createTestCalendar(
            context,
            "Hidden Calendar",
            "test@example.com",
            "test@example.com",
            isVisible = false
        )
        
        // Verify calendar was created
        assertTrue("Calendar ID should be positive", calendarId > 0)
        
        // Verify calendar properties including visibility
        verifyCalendar(
            calendarId = calendarId,
            expectedDisplayName = "Hidden Calendar",
            expectedIsVisible = false
        )
    }
    
    @Test
    fun testCreateUnhandledCalendar() {
        DevLog.info(LOG_TAG, "Running testCreateUnhandledCalendar")
        
        // First ensure calendar rescan is enabled but clear other settings
        val settings = Settings(context)
        settings.setBoolean("enable_manual_calendar_rescan", true)
        
        // Create calendar that's not handled by the app
        val calendarId = mockCalendarProvider.createTestCalendar(
            context,
            "Unhandled Calendar",
            "test@example.com",
            "test@example.com",
            isHandled = false
        )
        
        // Verify calendar was created
        assertTrue("Calendar ID should be positive", calendarId > 0)
        
        // Enhanced debug logging
        DevLog.info(LOG_TAG, "Calendar created with ID: $calendarId")
        
        // Verify handling state directly
        val isHandled = settings.getCalendarIsHandled(calendarId)
        DevLog.info(LOG_TAG, "Calendar handling status from settings: $isHandled")
        
        assertFalse("Calendar should not be handled", isHandled)
        
        // Verify calendar properties including handled state
        verifyCalendar(
            calendarId = calendarId,
            expectedDisplayName = "Unhandled Calendar",
            expectedIsHandled = false
        )
        
        // Verify that the calendar is in the CalendarProvider but not in the handled set
        val allCalendars = CalendarProvider.getCalendars(context)
        val handledCalendars = CalendarProvider.getHandledCalendarsIds(
            context,
            settings
        )
        
        DevLog.info(LOG_TAG, "All calendars: ${allCalendars.map { it.calendarId }}")
        DevLog.info(LOG_TAG, "Handled calendars: $handledCalendars")
        
        // Verify calendar exists but is not handled
        assertTrue("Calendar should exist in all calendars", 
            allCalendars.any { it.calendarId == calendarId })
        assertFalse("Calendar should not be in handled calendars set", 
            handledCalendars.contains(calendarId))
    }
    
    @Test
    fun testGetCalendars() {
        DevLog.info(LOG_TAG, "Running testGetCalendars")
        
        // Create multiple calendars
        val calendar1Id = mockCalendarProvider.createTestCalendar(
            context,
            "Calendar 1",
            "test1@example.com",
            "test1@example.com"
        )
        
        val calendar2Id = mockCalendarProvider.createTestCalendar(
            context,
            "Calendar 2",
            "test2@example.com",
            "test2@example.com"
        )
        
        // Get all calendars
        val calendars = CalendarProvider.getCalendars(context)
        
        // Verify both calendars are present
        assertTrue("Should have at least 2 calendars", calendars.size >= 2)
        assertTrue("Calendar 1 should be present", 
            calendars.any { it.calendarId == calendar1Id })
        assertTrue("Calendar 2 should be present",
            calendars.any { it.calendarId == calendar2Id })
    }
    
    @Test
    fun testGetNonExistentCalendar() {
        DevLog.info(LOG_TAG, "Running testGetNonExistentCalendar")
        
        // Try to get a calendar with an invalid ID
        val calendar = CalendarProvider.getCalendarById(
            context,
            -999L
        )
        
        // Verify null is returned
        assertNull("Should return null for non-existent calendar", calendar)
    }
    
    @Test
    fun testCalendarWithCustomTimezone() {
        DevLog.info(LOG_TAG, "Running testCalendarWithCustomTimezone")
        
        // Create calendar with custom timezone
        val calendarId = mockCalendarProvider.createTestCalendar(
            context,
            "Timezone Calendar",
            "test@example.com",
            "test@example.com",
            timeZone = "America/New_York"
        )
        
        // Get calendar and verify timezone
        val calendar = CalendarProvider.getCalendarById(
            context,
            calendarId
        )
        
        assertNotNull("Calendar should exist", calendar)
        assertEquals("Calendar should have correct timezone",
            "America/New_York", calendar?.timeZone)
    }
    
    @Test
    fun testGetHandledCalendarsIds() {
        DevLog.info(LOG_TAG, "Running testGetHandledCalendarsIds")
        
        // First ensure calendar rescan is enabled
        val settings = Settings(context)
        settings.setBoolean("enable_manual_calendar_rescan", true)
        
        // Create calendars - we'll set their handling status explicitly via overrides
        val handled1Id = mockCalendarProvider.createTestCalendar(
            context,
            "Handled 1",
            "test1@example.com",
            "test1@example.com"
        )
        
        val handled2Id = mockCalendarProvider.createTestCalendar(
            context,
            "Handled 2",
            "test2@example.com",
            "test2@example.com"
        )
        
        val unhandledId = mockCalendarProvider.createTestCalendar(
            context,
            "Unhandled",
            "test3@example.com",
            "test3@example.com",
            isHandled = false
        )
        
        // Log created calendar IDs
        DevLog.info(LOG_TAG, "Calendar IDs created - handled1: $handled1Id, handled2: $handled2Id, unhandled: $unhandledId")
        
        // Note: In Robolectric, we don't need overrideGetHandledCalendarsIds because:
        // 1. Calendar handling status is already set via setCalendarHandlingStatusDirectly in createTestCalendar
        // 2. MockCalendarProvider stubs getHandledCalendarsIds to use Settings.getCalendarIsHandled()
        // 3. Settings reads from SharedPreferences which we've already populated
        // This is simpler than the instrumentation test which needs complex overrides due to real ContentProvider
        
        // Log current calendar handling states for debugging
        val isHandled1 = settings.getCalendarIsHandled(handled1Id)
        val isHandled2 = settings.getCalendarIsHandled(handled2Id)
        val isHandledUnhandled = settings.getCalendarIsHandled(unhandledId)
        DevLog.info(LOG_TAG, "Calendar handling status - handled1: $isHandled1, handled2: $isHandled2, unhandled: $isHandledUnhandled")
        
        // Get handled calendar IDs using the overridden method
        val handledIds = CalendarProvider.getHandledCalendarsIds(
            context,
            settings
        )
        
        // Log all handled calendar IDs for debugging
        DevLog.info(LOG_TAG, "Found ${handledIds.size} handled calendars: $handledIds")
        
        // Verify correct calendars are marked as handled
        assertTrue("Handled calendar 1 should be present", handledIds.contains(handled1Id))
        assertTrue("Handled calendar 2 should be present", handledIds.contains(handled2Id))
        assertFalse("Unhandled calendar should not be present", handledIds.contains(unhandledId))
    }
}

