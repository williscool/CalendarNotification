package com.github.quarck.calnotify.textutils

import android.content.Context
import android.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.CalendarEventDetails
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventRecord
import com.github.quarck.calnotify.calendar.EventReminderRecord
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.CNPlusTestClock
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for EventFormatter - core time/date formatting functionality.
 */
@RunWith(AndroidJUnit4::class)
class EventFormatterTest {

    private val LOG_TAG = "EventFormatterTest"

    private lateinit var context: Context
    private lateinit var testClock: CNPlusTestClock
    private lateinit var formatter: EventFormatter

    // Base time: 2021-11-01 12:00:00 UTC (noon)
    private val baseTime = 1635768000000L

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testClock = CNPlusTestClock(baseTime)
        formatter = EventFormatter(context, testClock)
        DevLog.info(LOG_TAG, "Setup complete with baseTime=$baseTime")
    }

    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up after test")
        // Reset the next alert settings to defaults
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove("pref_display_next_gcal_reminder")
            .remove("pref_display_next_app_alert")
            .commit()
        // Unmock CalendarProvider if it was mocked
        try {
            unmockkObject(CalendarProvider)
        } catch (e: Exception) {
            // Ignore if not mocked
        }
    }

    private fun createTestEvent(
        eventId: Long = 1L,
        title: String = "Test Event",
        startTime: Long = baseTime + Consts.HOUR_IN_MILLISECONDS,
        endTime: Long = baseTime + 2 * Consts.HOUR_IN_MILLISECONDS,
        snoozedUntil: Long = 0L,
        isAllDay: Boolean = false,
        location: String = ""
    ): EventAlertRecord {
        return EventAlertRecord(
            calendarId = 1L,
            eventId = eventId,
            isAllDay = isAllDay,
            isRepeating = false,
            alertTime = baseTime,
            notificationId = eventId.toInt(),
            title = title,
            desc = "Test Description",
            startTime = startTime,
            endTime = endTime,
            instanceStartTime = startTime,
            instanceEndTime = endTime,
            location = location,
            lastStatusChangeTime = baseTime,
            snoozedUntil = snoozedUntil,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = baseTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
    }

    // === formatTimeDuration tests ===

    @Test
    fun testFormatTimeDurationZero() {
        DevLog.info(LOG_TAG, "Running testFormatTimeDurationZero")
        val result = formatter.formatTimeDuration(0L)
        assertNotNull(result)
        assertTrue("Zero duration should indicate 'now'", result.isNotEmpty())
    }

    @Test
    fun testFormatTimeDurationSeconds() {
        DevLog.info(LOG_TAG, "Running testFormatTimeDurationSeconds")
        val result = formatter.formatTimeDuration(30 * 1000L)
        assertNotNull(result)
        assertTrue("Should contain '30'", result.contains("30"))
    }

    @Test
    fun testFormatTimeDurationMinutes() {
        DevLog.info(LOG_TAG, "Running testFormatTimeDurationMinutes")
        val result = formatter.formatTimeDuration(15 * 60 * 1000L)
        assertNotNull(result)
        assertTrue("Should contain '15'", result.contains("15"))
    }

    @Test
    fun testFormatTimeDurationHours() {
        DevLog.info(LOG_TAG, "Running testFormatTimeDurationHours")
        val result = formatter.formatTimeDuration(3 * Consts.HOUR_IN_MILLISECONDS)
        assertNotNull(result)
        assertTrue("Should contain '3'", result.contains("3"))
    }

    @Test
    fun testFormatTimeDurationDays() {
        DevLog.info(LOG_TAG, "Running testFormatTimeDurationDays")
        val result = formatter.formatTimeDuration(7 * Consts.DAY_IN_MILLISECONDS)
        assertNotNull(result)
        assertTrue("Should contain '7'", result.contains("7"))
    }

    @Test
    fun testFormatTimeDurationWithGranularity() {
        DevLog.info(LOG_TAG, "Running testFormatTimeDurationWithGranularity")
        val result = formatter.formatTimeDuration(90 * 1000L, 60)
        assertNotNull(result)
        assertTrue("Should contain '1'", result.contains("1"))
    }

    // === formatTimePoint tests ===

    @Test
    fun testFormatTimePointZero() {
        DevLog.info(LOG_TAG, "Running testFormatTimePointZero")
        val result = formatter.formatTimePoint(0L)
        assertEquals("Zero time should return empty string", "", result)
    }

    @Test
    fun testFormatTimePointToday() {
        DevLog.info(LOG_TAG, "Running testFormatTimePointToday")
        val todayTime = baseTime + 2 * Consts.HOUR_IN_MILLISECONDS
        val result = formatter.formatTimePoint(todayTime)
        assertNotNull(result)
        assertTrue("Today time should produce a non-empty result", result.isNotEmpty())
    }

    @Test
    fun testFormatTimePointTomorrow() {
        DevLog.info(LOG_TAG, "Running testFormatTimePointTomorrow")
        val tomorrowTime = baseTime + Consts.DAY_IN_MILLISECONDS + Consts.HOUR_IN_MILLISECONDS
        val result = formatter.formatTimePoint(tomorrowTime)
        assertNotNull(result)
        assertTrue("Tomorrow time should produce a non-empty result", result.isNotEmpty())
    }

    @Test
    fun testFormatTimePointFuture() {
        DevLog.info(LOG_TAG, "Running testFormatTimePointFuture")
        val futureTime = baseTime + 7 * Consts.DAY_IN_MILLISECONDS
        val result = formatter.formatTimePoint(futureTime)
        assertNotNull(result)
        assertTrue("Future time should produce a non-empty result", result.isNotEmpty())
    }

    // === formatSnoozedUntil tests ===

    @Test
    fun testFormatSnoozedUntilNotSnoozed() {
        DevLog.info(LOG_TAG, "Running testFormatSnoozedUntilNotSnoozed")
        val event = createTestEvent(snoozedUntil = 0L)
        val result = formatter.formatSnoozedUntil(event)
        assertEquals("Not snoozed should return empty string", "", result)
    }

    @Test
    fun testFormatSnoozedUntilSnoozed() {
        DevLog.info(LOG_TAG, "Running testFormatSnoozedUntilSnoozed")
        val snoozedUntilTime = baseTime + 15 * Consts.MINUTE_IN_MILLISECONDS
        val event = createTestEvent(snoozedUntil = snoozedUntilTime)
        val result = formatter.formatSnoozedUntil(event)
        assertNotNull(result)
        assertTrue("Snoozed event should have non-empty snooze display", result.isNotEmpty())
    }

    // === formatDateTimeOneLine tests ===

    @Test
    fun testFormatDateTimeOneLineRegular() {
        DevLog.info(LOG_TAG, "Running testFormatDateTimeOneLineRegular")
        val event = createTestEvent()
        val result = formatter.formatDateTimeOneLine(event)
        assertNotNull(result)
        assertTrue("Regular event should have date/time", result.isNotEmpty())
    }

    @Test
    fun testFormatDateTimeOneLineAllDay() {
        DevLog.info(LOG_TAG, "Running testFormatDateTimeOneLineAllDay")
        val event = createTestEvent(isAllDay = true)
        val result = formatter.formatDateTimeOneLine(event)
        assertNotNull(result)
        assertTrue("All-day event should have date", result.isNotEmpty())
    }

    // === formatDateTimeTwoLines tests ===

    @Test
    fun testFormatDateTimeTwoLinesRegular() {
        DevLog.info(LOG_TAG, "Running testFormatDateTimeTwoLinesRegular")
        val event = createTestEvent()
        val (line1, line2) = formatter.formatDateTimeTwoLines(event)
        assertNotNull(line1)
        assertNotNull(line2)
        assertTrue("Line 1 should have content", line1.isNotEmpty())
    }

    @Test
    fun testFormatDateTimeTwoLinesAllDay() {
        DevLog.info(LOG_TAG, "Running testFormatDateTimeTwoLinesAllDay")
        val event = createTestEvent(isAllDay = true)
        val (line1, line2) = formatter.formatDateTimeTwoLines(event)
        assertNotNull(line1)
        assertNotNull(line2)
        assertTrue("Line 1 should have content for all-day", line1.isNotEmpty())
    }

    // === formatNotificationSecondaryText tests ===

    @Test
    fun testFormatNotificationSecondaryTextNoLocation() {
        DevLog.info(LOG_TAG, "Running testFormatNotificationSecondaryTextNoLocation")
        val event = createTestEvent(location = "")
        val result = formatter.formatNotificationSecondaryText(event)
        assertNotNull(result)
        assertTrue("Should have date/time info", result.isNotEmpty())
    }

    @Test
    fun testFormatNotificationSecondaryTextWithLocation() {
        DevLog.info(LOG_TAG, "Running testFormatNotificationSecondaryTextWithLocation")
        val event = createTestEvent(location = "Conference Room A")
        val result = formatter.formatNotificationSecondaryText(event)
        assertNotNull(result)
        assertTrue("Should include location", result.contains("Conference Room A"))
    }

    // === encodedMinuteTimestamp tests ===

    @Test
    fun testEncodedMinuteTimestamp() {
        DevLog.info(LOG_TAG, "Running testEncodedMinuteTimestamp")
        val result = encodedMinuteTimestamp(clock = testClock)
        assertNotNull(result)
        assertTrue("Encoded timestamp should not be empty", result.isNotEmpty())
        result.forEach { char ->
            assertTrue("Should only contain valid literals", literals.contains(char))
        }
    }

    @Test
    fun testEncodedMinuteTimestampDifferentTimes() {
        DevLog.info(LOG_TAG, "Running testEncodedMinuteTimestampDifferentTimes")
        val result1 = encodedMinuteTimestamp(clock = testClock)

        testClock.advanceBy(Consts.MINUTE_IN_MILLISECONDS)
        val result2 = encodedMinuteTimestamp(clock = testClock)

        assertNotEquals("Different times should produce different timestamps", result1, result2)
    }

    // === Next Alert Time feature tests ===

    private fun setDisplayNextAlertTimeSetting(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean("pref_display_next_alert_time", enabled)
            .commit()
    }

    private fun createMockEventRecord(
        eventId: Long,
        startTime: Long,
        reminders: List<EventReminderRecord>
    ): EventRecord {
        return EventRecord(
            calendarId = 1L,
            eventId = eventId,
            details = CalendarEventDetails(
                title = "Test Event",
                desc = "Test Description",
                location = "",
                timezone = "UTC",
                startTime = startTime,
                endTime = startTime + Consts.HOUR_IN_MILLISECONDS,
                isAllDay = false,
                reminders = reminders,
                repeatingRule = "",
                repeatingRDate = "",
                repeatingExRule = "",
                repeatingExRDate = "",
                color = 0
            ),
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None
        )
    }

    @Test
    fun testFormatNotificationSecondaryTextNextAlertTimeDisabled() {
        DevLog.info(LOG_TAG, "Running testFormatNotificationSecondaryTextNextAlertTimeDisabled")
        
        // Ensure the setting is disabled (default)
        setDisplayNextAlertTimeSetting(false)
        
        // Create a new formatter to pick up the setting
        val testFormatter = EventFormatter(context, testClock)
        
        // Event starts 2 hours from now with a reminder 30 minutes before
        val eventStartTime = baseTime + 2 * Consts.HOUR_IN_MILLISECONDS
        val event = createTestEvent(
            eventId = 100L,
            startTime = eventStartTime,
            endTime = eventStartTime + Consts.HOUR_IN_MILLISECONDS
        )
        
        val result = testFormatter.formatNotificationSecondaryText(event)
        
        DevLog.info(LOG_TAG, "Result with setting disabled: $result")
        
        // Should NOT contain "reminder in" when setting is disabled
        assertFalse(
            "Should NOT contain 'reminder in' when setting is disabled",
            result.contains("reminder in", ignoreCase = true)
        )
    }

    @Test
    fun testFormatNotificationSecondaryTextNextAlertTimeEnabled() {
        DevLog.info(LOG_TAG, "Running testFormatNotificationSecondaryTextNextAlertTimeEnabled")
        
        // Enable the setting
        setDisplayNextAlertTimeSetting(true)
        
        // Event starts 2 hours from now
        val eventStartTime = baseTime + 2 * Consts.HOUR_IN_MILLISECONDS
        val eventId = 101L
        
        // Create reminders: one that already fired (60min before = baseTime + 1h) 
        // and one in the future (30min before = baseTime + 1.5h)
        val reminders = listOf(
            EventReminderRecord.minutes(60),  // fires at baseTime + 1h (future from baseTime)
            EventReminderRecord.minutes(30)   // fires at baseTime + 1.5h (future from baseTime)
        )
        
        // Mock CalendarProvider to return our event with reminders
        mockkObject(CalendarProvider)
        every { CalendarProvider.getEvent(any(), eq(eventId)) } returns createMockEventRecord(
            eventId = eventId,
            startTime = eventStartTime,
            reminders = reminders
        )
        
        // Create the test event (EventAlertRecord)
        val event = createTestEvent(
            eventId = eventId,
            startTime = eventStartTime,
            endTime = eventStartTime + Consts.HOUR_IN_MILLISECONDS
        )
        
        // Create a new formatter to pick up the setting
        val testFormatter = EventFormatter(context, testClock)
        
        val result = testFormatter.formatNotificationSecondaryText(event)
        
        DevLog.info(LOG_TAG, "Result with setting enabled: $result")
        DevLog.info(LOG_TAG, "Event start time: $eventStartTime, Base time: $baseTime")
        DevLog.info(LOG_TAG, "Reminder 1 fires at: ${eventStartTime - 60 * Consts.MINUTE_IN_MILLISECONDS}")
        DevLog.info(LOG_TAG, "Reminder 2 fires at: ${eventStartTime - 30 * Consts.MINUTE_IN_MILLISECONDS}")
        
        // Should contain "reminder in" when setting is enabled and there are future reminders
        assertTrue(
            "Should contain 'reminder in' when setting is enabled and future reminders exist. Result: $result",
            result.contains("reminder in", ignoreCase = true)
        )
    }
}

