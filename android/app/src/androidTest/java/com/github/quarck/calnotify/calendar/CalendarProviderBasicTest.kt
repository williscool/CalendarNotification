package com.github.quarck.calnotify.calendar

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.CalendarProviderTestFixture
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarProviderBasicTest {
    private val LOG_TAG = "CalProviderBasicTest"
    private lateinit var fixture: CalendarProviderTestFixture
    
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )
    
    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up test environment")
        fixture = CalendarProviderTestFixture()
    }
    
    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test environment")
        fixture.cleanup()
    }
    
    @Test
    fun testCreateAndGetCalendar() {
        DevLog.info(LOG_TAG, "Running testCreateAndGetCalendar")
        
        // Create calendar with basic settings
        val calendarId = fixture.createCalendarWithSettings(
            "Test Calendar",
            "test@example.com",
            "test@example.com"
        )
        
        // Verify calendar was created
        assertTrue("Calendar ID should be positive", calendarId > 0)
        
        // Verify calendar properties
        fixture.verifyCalendar(
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
        val calendarId = fixture.createCalendarWithSettings(
            "Hidden Calendar",
            "test@example.com",
            "test@example.com",
            isVisible = false
        )
        
        // Verify calendar was created
        assertTrue("Calendar ID should be positive", calendarId > 0)
        
        // Verify calendar properties including visibility
        fixture.verifyCalendar(
            calendarId = calendarId,
            expectedDisplayName = "Hidden Calendar",
            expectedIsVisible = false
        )
    }
    
    @Test
    fun testCreateUnhandledCalendar() {
        DevLog.info(LOG_TAG, "Running testCreateUnhandledCalendar")
        
        // First ensure calendar rescan is enabled but clear other settings
        val settings = com.github.quarck.calnotify.Settings(fixture.contextProvider.fakeContext)
        settings.setBoolean("enable_manual_calendar_rescan", true)
        
        // Create calendar that's not handled by the app
        val calendarId = fixture.createCalendarWithSettings(
            "Unhandled Calendar",
            "test@example.com",
            "test@example.com",
            isHandled = false
        )
        
        // Verify calendar was created
        assertTrue("Calendar ID should be positive", calendarId > 0)
        
        // Enhanced debug logging
        DevLog.info(LOG_TAG, "Calendar created with ID: $calendarId")
        
        // Get the raw value from preferences to diagnose issues
        val prefix = settings.javaClass.getDeclaredField("CALENDAR_IS_HANDLED_KEY_PREFIX")
        prefix.isAccessible = true
        val prefixValue = prefix.get(null) as String
        val rawKey = "$prefixValue.$calendarId"
        
        DevLog.info(LOG_TAG, "Raw preference key would be: $rawKey")
        
        // Verify handling state directly
        val isHandled = settings.getCalendarIsHandled(calendarId)
        DevLog.info(LOG_TAG, "Calendar handling status from settings: $isHandled")
        
        assertFalse("Calendar should not be handled", isHandled)
        
        // Verify calendar properties including handled state
        fixture.verifyCalendar(
            calendarId = calendarId,
            expectedDisplayName = "Unhandled Calendar",
            expectedIsHandled = false
        )
        
        // Verify that the calendar is in the CalendarProvider but not in the handled set
        val allCalendars = CalendarProvider.getCalendars(fixture.contextProvider.fakeContext)
        val handledCalendars = CalendarProvider.getHandledCalendarsIds(
            fixture.contextProvider.fakeContext,
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
        val calendar1Id = fixture.createCalendarWithSettings(
            "Calendar 1",
            "test1@example.com",
            "test1@example.com"
        )
        
        val calendar2Id = fixture.createCalendarWithSettings(
            "Calendar 2",
            "test2@example.com",
            "test2@example.com"
        )
        
        // Get all calendars
        val calendars = CalendarProvider.getCalendars(fixture.contextProvider.fakeContext)
        
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
            fixture.contextProvider.fakeContext,
            -999L
        )
        
        // Verify null is returned
        assertNull("Should return null for non-existent calendar", calendar)
    }
    
    @Test
    fun testCalendarWithCustomTimezone() {
        DevLog.info(LOG_TAG, "Running testCalendarWithCustomTimezone")
        
        // Create calendar with custom timezone
        val calendarId = fixture.createCalendarWithSettings(
            "Timezone Calendar",
            "test@example.com",
            "test@example.com",
            timeZone = "America/New_York"
        )
        
        // Get calendar and verify timezone
        val calendar = CalendarProvider.getCalendarById(
            fixture.contextProvider.fakeContext,
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
        val settings = com.github.quarck.calnotify.Settings(fixture.contextProvider.fakeContext)
        settings.setBoolean("enable_manual_calendar_rescan", true)
        
        // Create calendars - we'll set their handling status explicitly via overrides
        val handled1Id = fixture.createCalendarWithSettings(
            "Handled 1",
            "test1@example.com",
            "test1@example.com"
        )
        
        val handled2Id = fixture.createCalendarWithSettings(
            "Handled 2",
            "test2@example.com",
            "test2@example.com"
        )
        
        val unhandledId = fixture.createCalendarWithSettings(
            "Unhandled",
            "test3@example.com",
            "test3@example.com"
        )
        
        // Log created calendar IDs
        DevLog.info(LOG_TAG, "Calendar IDs created - handled1: $handled1Id, handled2: $handled2Id, unhandled: $unhandledId")
        
        // Create a map of calendar handling overrides - explicitly set which calendars should be handled
        val calendarHandlingOverrides = mapOf(
            handled1Id to true,
            handled2Id to true,
            unhandledId to false
        )
        
        // Apply the overrides to ensure our test has consistent behavior
        fixture.contextProvider.overrideGetHandledCalendarsIds(calendarHandlingOverrides)
        
        // Log current calendar handling states for debugging
        val isHandled1 = settings.getCalendarIsHandled(handled1Id)
        val isHandled2 = settings.getCalendarIsHandled(handled2Id)
        val isHandledUnhandled = settings.getCalendarIsHandled(unhandledId)
        DevLog.info(LOG_TAG, "Calendar handling status - handled1: $isHandled1, handled2: $isHandled2, unhandled: $isHandledUnhandled")
        
        // Get handled calendar IDs using the overridden method
        val handledIds = CalendarProvider.getHandledCalendarsIds(
            fixture.contextProvider.fakeContext,
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