package com.github.quarck.calnotify.testutils

import android.content.ContentValues
import android.provider.CalendarContract
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.logs.DevLog

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
        // Set up basic calendar state
        baseFixture.setupTestCalendar()
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
        
        if (calendarId > 0 && isHandled) {
            val settings = Settings(context)
            settings.setBoolean("calendar_is_handled_$calendarId", true)
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
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            
            // Set up mocks for this event
            calendarProvider.mockEventDetails(eventId, startTime, title, duration)
            calendarProvider.mockEventReminders(eventId, reminderMinutes * 60000L)
            calendarProvider.mockEventAlerts(eventId, startTime, reminderMinutes * 60000L)
        }
        
        DevLog.info(LOG_TAG, "Created event: id=$eventId, title=$title, startTime=$startTime")
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
        expectedLocation: String? = null
    ) {
        val context = contextProvider.fakeContext
        val event = CalendarProvider.getEvent(context, eventId)
        
        DevLog.info(LOG_TAG, "Verifying event: id=$eventId")
        
        if (expectedTitle != null) {
            assert(event?.details?.title == expectedTitle) {
                "Event title mismatch. Expected: $expectedTitle, Got: ${event?.details?.title}"
            }
        }
        if (expectedDescription != null) {
            assert(event?.details?.desc == expectedDescription) {
                "Event description mismatch. Expected: $expectedDescription, Got: ${event?.details?.desc}"
            }
        }
        if (expectedStartTime != null) {
            assert(event?.details?.startTime == expectedStartTime) {
                "Event start time mismatch. Expected: $expectedStartTime, Got: ${event?.details?.startTime}"
            }
        }
        if (expectedDuration != null && event?.details?.startTime != null) {
            val actualDuration = event.details.endTime - event.details.startTime
            assert(actualDuration == expectedDuration) {
                "Event duration mismatch. Expected: $expectedDuration, Got: $actualDuration"
            }
        }
        if (expectedIsAllDay != null) {
            assert(event?.details?.isAllDay == expectedIsAllDay) {
                "Event all-day mismatch. Expected: $expectedIsAllDay, Got: ${event?.details?.isAllDay}"
            }
        }
        if (expectedLocation != null) {
            assert(event?.details?.location == expectedLocation) {
                "Event location mismatch. Expected: $expectedLocation, Got: ${event?.details?.location}"
            }
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
            assert(reminders.size == expectedReminderCount) {
                "Reminder count mismatch. Expected: $expectedReminderCount, Got: ${reminders.size}"
            }
        }
        if (expectedReminderMinutes != null) {
            val actualMinutes = reminders.map { (it.millisecondsBefore / 60000).toInt() }
            assert(actualMinutes.containsAll(expectedReminderMinutes)) {
                "Reminder minutes mismatch. Expected: $expectedReminderMinutes, Got: $actualMinutes"
            }
        }
        if (expectedMethods != null) {
            val actualMethods = reminders.map { it.method }
            assert(actualMethods.containsAll(expectedMethods)) {
                "Reminder methods mismatch. Expected: $expectedMethods, Got: $actualMethods"
            }
        }
    }

    /**
     * Cleans up test resources
     */
    fun cleanup() {
        baseFixture.cleanup()
    }
} 