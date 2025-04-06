package com.github.quarck.calnotify.testutils

import android.content.ContentValues
import android.provider.CalendarContract
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.logs.DevLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * Test fixture specifically for testing CalendarProvider functionality.
 * Builds on BaseCalendarTestFixture to provide calendar-specific test utilities.
 */
class CalendarProviderTestFixture {
    private val LOG_TAG = "CalProviderTestFixture"
    
    var baseFixture: BaseCalendarTestFixture
    
    // Properties exposed from the base fixture
    val contextProvider get() = baseFixture.contextProvider
    val timeProvider get() = baseFixture.timeProvider
    val calendarProvider get() = baseFixture.calendarProvider
    
    constructor() {
        baseFixture = BaseCalendarTestFixture.Builder().build()
        setupInitialState()
    }
    
    private fun setupInitialState() {
        // Clear any existing settings that might affect calendar handling
        val settings = Settings(contextProvider.fakeContext)
        settings.setBoolean("enable_manual_calendar_rescan", false)
        
        // Initialize event-related mocks
        calendarProvider.setupEventMocks()
    }
    
    /**
     * Creates a calendar with specified settings
     */
    fun createCalendarWithSettings(
        displayName: String,
        accountName: String,
        ownerAccount: String,
        isVisible: Boolean = true,
        isHandled: Boolean = true,
        timeZone: String = "UTC"
    ): Long {
        val context = contextProvider.fakeContext
        
        // First clear any existing calendar handling settings for test isolation
        val settings = Settings(context)
        settings.setBoolean("enable_manual_calendar_rescan", true)
        
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
            put(CalendarContract.Calendars.NAME, displayName)
            put(CalendarContract.Calendars.VISIBLE, if (isVisible) 1 else 0)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ownerAccount)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, timeZone)
        }

        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()

        val calUri = context.contentResolver.insert(uri, values)
        val calendarId = calUri?.lastPathSegment?.toLong() ?: -1L
        
        if (calendarId > 0) {
            // Use direct preference manipulation for more reliable testing
            contextProvider.setCalendarHandlingStatusDirectly(calendarId, isHandled)
            
            // Verify the setting was correctly applied
            val actualHandled = settings.getCalendarIsHandled(calendarId)
            DevLog.info(LOG_TAG, "Set calendar handling state: id=$calendarId, isHandled=$isHandled, actual=$actualHandled")
            
            // If the setting didn't take effect, try direct Settings class method
            if (actualHandled != isHandled) {
                DevLog.error(LOG_TAG, "Failed to set calendar handling state: expected=$isHandled, actual=$actualHandled")
                
                try {
                    // Try to use the Settings class method directly
                    settings.setCalendarIsHandled(calendarId, isHandled)
                    
                    // Check again after direct call
                    val updatedHandled = settings.getCalendarIsHandled(calendarId)
                    DevLog.info(LOG_TAG, "After direct Settings.setCalendarIsHandled call: $updatedHandled")
                    
                    if (updatedHandled != isHandled) {
                        DevLog.error(LOG_TAG, "Still failed to set calendar handling state. Using direct override.")
                        
                        // If all else fails, set up an override in the mock context
                        val overrides = mapOf(calendarId to isHandled)
                        contextProvider.overrideGetHandledCalendarsIds(overrides)
                    }
                } catch (e: Exception) {
                    DevLog.error(LOG_TAG, "Exception while setting calendar handling: ${e.message}")
                }
            }
        }
        
        DevLog.info(LOG_TAG, "Created calendar: id=$calendarId, name=$displayName, handled=$isHandled")
        return calendarId
    }

    /**
     * Creates an event with specified settings
     */
    fun createEventWithSettings(
        calendarId: Long,
        title: String,
        description: String = "Test Description",
        startTime: Long = timeProvider.testClock.currentTimeMillis() + 3600000,
        duration: Long = 3600000,
        isAllDay: Boolean = false,
        reminderMinutes: Int = 15,
        reminderMethod: Int = CalendarContract.Reminders.METHOD_ALERT,
        location: String = "",
        timeZone: String = "UTC",
        repeatingRule: String = ""
    ): Long {
        val context = contextProvider.fakeContext
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startTime)
            put(CalendarContract.Events.DTEND, startTime + duration)
            put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
            put(CalendarContract.Events.HAS_ALARM, 1)
            if (repeatingRule.isNotEmpty()) {
                put(CalendarContract.Events.RRULE, repeatingRule)
            }
        }

        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = eventUri?.lastPathSegment?.toLong() ?: -1L
        
        if (eventId > 0) {
            // Add reminder
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                put(CalendarContract.Reminders.METHOD, reminderMethod)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            
            // Set up more comprehensive mocks for this event
            calendarProvider.mockEventDetails(
                eventId = eventId, 
                startTime = startTime, 
                title = title, 
                duration = duration,
                description = description,
                location = location,
                isAllDay = isAllDay,
                repeatingRule = repeatingRule,
                timeZone = timeZone
            )
            
            // Mock the reminder with the correct method
            calendarProvider.mockEventReminders(
                eventId = eventId,
                millisecondsBefore = reminderMinutes * 60000L,
                method = reminderMethod
            )
            
            calendarProvider.mockEventAlerts(eventId, startTime, reminderMinutes * 60000L)
        }
        
        DevLog.info(LOG_TAG, "Created event: id=$eventId, title=$title, startTime=$startTime, isAllDay=$isAllDay, repeatingRule=$repeatingRule")
        return eventId
    }

    /**
     * Verifies calendar properties
     */
    fun verifyCalendar(
        calendarId: Long,
        expectedDisplayName: String? = null,
        expectedAccountName: String? = null,
        expectedOwnerAccount: String? = null,
        expectedIsVisible: Boolean? = null,
        expectedIsHandled: Boolean? = null
    ) {
        val context = contextProvider.fakeContext
        val calendar = CalendarProvider.getCalendarById(context, calendarId)
        
        DevLog.info(LOG_TAG, "Verifying calendar: id=$calendarId")
        
        if (expectedDisplayName != null) {
            assert(calendar?.displayName == expectedDisplayName) { 
                "Calendar display name mismatch. Expected: $expectedDisplayName, Got: ${calendar?.displayName}" 
            }
        }
        if (expectedAccountName != null) {
            assert(calendar?.accountName == expectedAccountName) {
                "Calendar account name mismatch. Expected: $expectedAccountName, Got: ${calendar?.accountName}"
            }
        }
        if (expectedOwnerAccount != null) {
            assert(calendar?.owner == expectedOwnerAccount) {
                "Calendar owner mismatch. Expected: $expectedOwnerAccount, Got: ${calendar?.owner}"
            }
        }
        if (expectedIsVisible != null) {
            assert(calendar?.isVisible == expectedIsVisible) {
                "Calendar visibility mismatch. Expected: $expectedIsVisible, Got: ${calendar?.isVisible}"
            }
        }
        if (expectedIsHandled != null) {
            val settings = Settings(context)
            val isHandled = settings.getCalendarIsHandled(calendarId)
            DevLog.info(LOG_TAG, "Checking calendar handled state: id=$calendarId, expected=$expectedIsHandled, actual=$isHandled")
            assert(isHandled == expectedIsHandled) {
                "Calendar handled state mismatch. Expected: $expectedIsHandled, Got: $isHandled"
            }
        }
    }

    /**
     * Verifies event properties
     */
    fun verifyEvent(
        eventId: Long,
        expectedTitle: String? = null,
        expectedDescription: String? = null,
        expectedStartTime: Long? = null,
        expectedDuration: Long? = null,
        expectedIsAllDay: Boolean? = null,
        expectedLocation: String? = null,
        expectedTimeZone: String? = null,
        expectedRepeatingRule: String? = null
    ) {
        val context = contextProvider.fakeContext
        val event = CalendarProvider.getEvent(context, eventId)
        
        DevLog.info(LOG_TAG, "Verifying event: id=$eventId")
        
        // First, ensure the event exists
        assertNotNull("Event should exist", event)
        
        // If event is null, stop verification to avoid NPEs
        if (event == null) {
            return
        }
        
        if (expectedTitle != null) {
            assertEquals("Event title mismatch", expectedTitle, event.details.title)
        }
        if (expectedDescription != null) {
            assertEquals("Event description mismatch", expectedDescription, event.details.desc)
        }
        if (expectedStartTime != null) {
            assertEquals("Event start time mismatch", expectedStartTime, event.details.startTime)
        }
        if (expectedDuration != null && event.details.startTime != null) {
            val actualDuration = event.details.endTime - event.details.startTime
            assertEquals("Event duration mismatch", expectedDuration, actualDuration)
        }
        if (expectedIsAllDay != null) {
            assertEquals("Event all-day mismatch", expectedIsAllDay, event.details.isAllDay)
        }
        if (expectedLocation != null) {
            assertEquals("Event location mismatch", expectedLocation, event.details.location)
        }
        if (expectedTimeZone != null) {
            assertEquals("Event timezone mismatch", expectedTimeZone, event.details.timezone)
        }
        if (expectedRepeatingRule != null) {
            assertEquals("Event repeating rule mismatch", expectedRepeatingRule, event.details.repeatingRule)
            
            // Also verify isRepeating flag if we're checking rules
            val isRepeating = CalendarProvider.isRepeatingEvent(context, eventId)
            assertEquals("Event repeating flag mismatch", 
                expectedRepeatingRule.isNotEmpty(), isRepeating == true)
        }
    }

    /**
     * Verifies reminder properties
     */
    fun verifyReminders(
        eventId: Long,
        expectedReminderCount: Int? = null,
        expectedReminderMinutes: List<Int>? = null,
        expectedMethods: List<Int>? = null
    ) {
        val context = contextProvider.fakeContext
        val reminders = CalendarProvider.getEventReminders(context, eventId)
        
        DevLog.info(LOG_TAG, "Verifying reminders for event: id=$eventId")
        
        if (expectedReminderCount != null) {
            assertEquals("Reminder count mismatch", expectedReminderCount, reminders.size)
        }
        
        if (expectedReminderMinutes != null) {
            val actualMinutes = reminders.map { (it.millisecondsBefore / 60000).toInt() }.sorted()
            val expectedMinutesSorted = expectedReminderMinutes.sorted()
            
            assertEquals("Reminder minutes mismatch", expectedMinutesSorted, actualMinutes)
            
            DevLog.info(LOG_TAG, "Reminder minutes match: expected=$expectedMinutesSorted, actual=$actualMinutes")
        }
        
        if (expectedMethods != null) {
            val actualMethods = reminders.map { it.method }
            
            // Either compare size and contents separately (more detailed error messages)
            assertEquals("Reminder methods count mismatch", expectedMethods.size, actualMethods.size)
            
            // Check each individual method
            expectedMethods.forEachIndexed { index, method ->
                if (index < actualMethods.size) {
                    assertEquals("Reminder method at index $index mismatch", method, actualMethods[index])
                }
            }
            
            DevLog.info(LOG_TAG, "Reminder methods match: expected=$expectedMethods, actual=$actualMethods")
        }
    }

    /**
     * Cleans up test resources
     */
    fun cleanup() {
        baseFixture.cleanup()
    }
} 