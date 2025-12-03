package com.github.quarck.calnotify.textutils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.utils.CNPlusUnitTestClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for EventFormatter - core time/date formatting functionality.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class EventFormatterRobolectricTest {

    private lateinit var context: Context
    private lateinit var testClock: CNPlusUnitTestClock
    private lateinit var formatter: EventFormatter

    // Base time: 2021-11-01 12:00:00 UTC (noon)
    private val baseTime = 1635768000000L

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testClock = CNPlusUnitTestClock(baseTime)
        formatter = EventFormatter(context, testClock)
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
        val result = formatter.formatTimeDuration(0L)
        // Should return "now" for zero duration
        assertNotNull(result)
        assertTrue("Zero duration should indicate 'now'", result.isNotEmpty())
    }

    @Test
    fun testFormatTimeDurationSeconds() {
        // 30 seconds
        val result = formatter.formatTimeDuration(30 * 1000L)
        assertNotNull(result)
        assertTrue("Should contain '30'", result.contains("30"))
    }

    @Test
    fun testFormatTimeDurationOneMinute() {
        // 1 minute
        val result = formatter.formatTimeDuration(60 * 1000L)
        assertNotNull(result)
        assertTrue("Should contain '1'", result.contains("1"))
    }

    @Test
    fun testFormatTimeDurationMinutes() {
        // 15 minutes
        val result = formatter.formatTimeDuration(15 * 60 * 1000L)
        assertNotNull(result)
        assertTrue("Should contain '15'", result.contains("15"))
    }

    @Test
    fun testFormatTimeDurationOneHour() {
        // 1 hour
        val result = formatter.formatTimeDuration(Consts.HOUR_IN_MILLISECONDS)
        assertNotNull(result)
        assertTrue("Should contain '1'", result.contains("1"))
    }

    @Test
    fun testFormatTimeDurationHours() {
        // 3 hours
        val result = formatter.formatTimeDuration(3 * Consts.HOUR_IN_MILLISECONDS)
        assertNotNull(result)
        assertTrue("Should contain '3'", result.contains("3"))
    }

    @Test
    fun testFormatTimeDurationOneDay() {
        // 1 day
        val result = formatter.formatTimeDuration(Consts.DAY_IN_MILLISECONDS)
        assertNotNull(result)
        assertTrue("Should contain '1'", result.contains("1"))
    }

    @Test
    fun testFormatTimeDurationDays() {
        // 7 days
        val result = formatter.formatTimeDuration(7 * Consts.DAY_IN_MILLISECONDS)
        assertNotNull(result)
        assertTrue("Should contain '7'", result.contains("7"))
    }

    @Test
    fun testFormatTimeDurationWithGranularity() {
        // 90 seconds with granularity of 60 (should round to 60 seconds = 1 minute)
        val result = formatter.formatTimeDuration(90 * 1000L, 60)
        assertNotNull(result)
        // With granularity 60, 90 - (90 % 60) = 60 seconds = 1 minute
        assertTrue("Should contain '1'", result.contains("1"))
    }

    // === formatTimePoint tests ===

    @Test
    fun testFormatTimePointZero() {
        val result = formatter.formatTimePoint(0L)
        assertEquals("Zero time should return empty string", "", result)
    }

    @Test
    fun testFormatTimePointToday() {
        // Time point is today (same day as baseTime)
        val todayTime = baseTime + 2 * Consts.HOUR_IN_MILLISECONDS
        val result = formatter.formatTimePoint(todayTime)
        assertNotNull(result)
        assertTrue("Today time should produce a non-empty result", result.isNotEmpty())
        // Should show time only for today events
    }

    @Test
    fun testFormatTimePointTomorrow() {
        // Time point is tomorrow
        val tomorrowTime = baseTime + Consts.DAY_IN_MILLISECONDS + Consts.HOUR_IN_MILLISECONDS
        val result = formatter.formatTimePoint(tomorrowTime)
        assertNotNull(result)
        assertTrue("Tomorrow time should produce a non-empty result", result.isNotEmpty())
    }

    @Test
    fun testFormatTimePointFuture() {
        // Time point is far in the future (next week)
        val futureTime = baseTime + 7 * Consts.DAY_IN_MILLISECONDS
        val result = formatter.formatTimePoint(futureTime)
        assertNotNull(result)
        assertTrue("Future time should produce a non-empty result", result.isNotEmpty())
    }

    @Test
    fun testFormatTimePointFarFuture() {
        // Time point is very far in the future (4 months) - should show year
        val farFutureTime = baseTime + 120 * Consts.DAY_IN_MILLISECONDS
        val result = formatter.formatTimePoint(farFutureTime)
        assertNotNull(result)
        assertTrue("Far future time should produce a non-empty result", result.isNotEmpty())
    }

    // === formatSnoozedUntil tests ===

    @Test
    fun testFormatSnoozedUntilNotSnoozed() {
        val event = createTestEvent(snoozedUntil = 0L)
        val result = formatter.formatSnoozedUntil(event)
        assertEquals("Not snoozed should return empty string", "", result)
    }

    @Test
    fun testFormatSnoozedUntilSnoozed() {
        val snoozedUntilTime = baseTime + 15 * Consts.MINUTE_IN_MILLISECONDS
        val event = createTestEvent(snoozedUntil = snoozedUntilTime)
        val result = formatter.formatSnoozedUntil(event)
        assertNotNull(result)
        assertTrue("Snoozed event should have non-empty snooze display", result.isNotEmpty())
    }

    // === formatDateTimeOneLine tests ===

    @Test
    fun testFormatDateTimeOneLineRegular() {
        val event = createTestEvent()
        val result = formatter.formatDateTimeOneLine(event)
        assertNotNull(result)
        assertTrue("Regular event should have date/time", result.isNotEmpty())
    }

    @Test
    fun testFormatDateTimeOneLineAllDay() {
        val event = createTestEvent(isAllDay = true)
        val result = formatter.formatDateTimeOneLine(event)
        assertNotNull(result)
        assertTrue("All-day event should have date", result.isNotEmpty())
    }

    @Test
    fun testFormatDateTimeOneLineWithWeekDay() {
        val event = createTestEvent()
        val result = formatter.formatDateTimeOneLine(event, showWeekDay = true)
        assertNotNull(result)
        assertTrue("Should include some date info", result.isNotEmpty())
    }

    // === formatDateTimeTwoLines tests ===

    @Test
    fun testFormatDateTimeTwoLinesRegular() {
        val event = createTestEvent()
        val (line1, line2) = formatter.formatDateTimeTwoLines(event)
        assertNotNull(line1)
        assertNotNull(line2)
        assertTrue("Line 1 should have content", line1.isNotEmpty())
    }

    @Test
    fun testFormatDateTimeTwoLinesAllDay() {
        val event = createTestEvent(isAllDay = true)
        val (line1, line2) = formatter.formatDateTimeTwoLines(event)
        assertNotNull(line1)
        assertNotNull(line2)
        assertTrue("Line 1 should have content for all-day", line1.isNotEmpty())
    }

    // === formatNotificationSecondaryText tests ===

    @Test
    fun testFormatNotificationSecondaryTextNoLocation() {
        val event = createTestEvent(location = "")
        val result = formatter.formatNotificationSecondaryText(event)
        assertNotNull(result)
        assertTrue("Should have date/time info", result.isNotEmpty())
        assertFalse("Should not have location", result.contains("Location"))
    }

    @Test
    fun testFormatNotificationSecondaryTextWithLocation() {
        val event = createTestEvent(location = "Conference Room A")
        val result = formatter.formatNotificationSecondaryText(event)
        assertNotNull(result)
        assertTrue("Should include location", result.contains("Conference Room A"))
    }

    // === encodedMinuteTimestamp tests ===

    @Test
    fun testEncodedMinuteTimestamp() {
        val result = encodedMinuteTimestamp(clock = testClock)
        assertNotNull(result)
        assertTrue("Encoded timestamp should not be empty", result.isNotEmpty())
        // Should only contain valid characters
        result.forEach { char ->
            assertTrue("Should only contain valid literals", literals.contains(char))
        }
    }

    @Test
    fun testEncodedMinuteTimestampDifferentTimes() {
        val result1 = encodedMinuteTimestamp(clock = testClock)

        // Advance clock by 1 minute
        testClock.advanceBy(Consts.MINUTE_IN_MILLISECONDS)
        val result2 = encodedMinuteTimestamp(clock = testClock)

        assertNotEquals("Different times should produce different timestamps", result1, result2)
    }

    @Test
    fun testEncodedMinuteTimestampWithModulo() {
        val result = encodedMinuteTimestamp(modulo = 60L, clock = testClock)
        assertNotNull(result)
        assertTrue("Encoded timestamp with modulo should not be empty", result.isNotEmpty())
    }
}

