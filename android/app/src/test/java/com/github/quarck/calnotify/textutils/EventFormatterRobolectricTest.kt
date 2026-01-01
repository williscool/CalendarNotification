package com.github.quarck.calnotify.textutils

import android.content.Context
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.CalendarEventDetails
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarProviderInterface
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventRecord
import com.github.quarck.calnotify.calendar.EventReminderRecord
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.reminders.ReminderStateInterface
import com.github.quarck.calnotify.utils.CNPlusUnitTestClock
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
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

    @After
    fun cleanup() {
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

    // === Next Notification Indicator feature tests ===

    private fun setNextGCalReminderSetting(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean("pref_display_next_gcal_reminder", enabled)
            .commit()
    }

    private fun setNextAppAlertSetting(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean("pref_display_next_app_alert", enabled)
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
    fun testFormatNotificationSecondaryTextNextGCalDisabled() {
        // Disable the GCal setting
        setNextGCalReminderSetting(false)
        setNextAppAlertSetting(false)
        
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
        
        // Should NOT contain 📅 or 🔔 when settings are disabled
        assertFalse(
            "Should NOT contain calendar emoji when setting is disabled",
            result.contains("📅")
        )
        assertFalse(
            "Should NOT contain bell emoji when setting is disabled",
            result.contains("🔔")
        )
    }

    @Test
    fun testFormatNotificationSecondaryTextNextGCalEnabled() {
        // Enable the GCal setting (default is true, but explicit for test)
        setNextGCalReminderSetting(true)
        setNextAppAlertSetting(false)
        
        // Event starts 2 hours from now
        val eventStartTime = baseTime + 2 * Consts.HOUR_IN_MILLISECONDS
        val eventId = 101L
        
        // Create reminders: one in the future (60min before = baseTime + 1h)
        val reminders = listOf(
            EventReminderRecord.minutes(60)  // fires at baseTime + 1h (future from baseTime)
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
        
        // Should contain 📅 when setting is enabled and there are future reminders
        assertTrue(
            "Should contain 📅 when setting is enabled and future GCal reminders exist. Result: $result",
            result.contains("📅")
        )
    }

    @Test
    fun testFormatNotificationSecondaryTextNoFutureReminders() {
        // Enable the GCal setting
        setNextGCalReminderSetting(true)
        setNextAppAlertSetting(false)
        
        // Event started 30 minutes ago, reminder was 15 minutes before that (so already fired)
        val eventStartTime = baseTime - 30 * Consts.MINUTE_IN_MILLISECONDS
        val eventId = 102L
        
        // Reminder at 15 min before event = baseTime - 45min (which is in the PAST from baseTime)
        val reminders = listOf(
            EventReminderRecord.minutes(15)
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
        
        // Should NOT contain 📅 because the reminder already fired (is in the past)
        assertFalse(
            "Should NOT contain 📅 when all reminders are in the past. Result: $result",
            result.contains("📅")
        )
    }

    // === calculateNextNotification companion object tests ===

    @Test
    fun `calculateNextNotification - only gcal available`() {
        val currentTime = 1000L
        val nextGCalTime = 2000L  // 1 second later
        
        val result = EventFormatter.calculateNextNotification(
            nextGCalTime = nextGCalTime,
            nextAppTime = null,
            currentTime = currentTime,
            isMuted = false
        )
        
        assertNotNull(result)
        assertEquals(NextNotificationType.GCAL_REMINDER, result!!.type)
        assertEquals(1000L, result.timeUntilMillis)
        assertFalse(result.isMuted)
    }

    @Test
    fun `calculateNextNotification - only app available`() {
        val currentTime = 1000L
        val nextAppTime = 3000L  // 2 seconds later
        
        val result = EventFormatter.calculateNextNotification(
            nextGCalTime = null,
            nextAppTime = nextAppTime,
            currentTime = currentTime,
            isMuted = false
        )
        
        assertNotNull(result)
        assertEquals(NextNotificationType.APP_ALERT, result!!.type)
        assertEquals(2000L, result.timeUntilMillis)
        assertFalse(result.isMuted)
    }

    @Test
    fun `calculateNextNotification - both available, gcal sooner`() {
        val currentTime = 1000L
        val nextGCalTime = 2000L  // 1 second later
        val nextAppTime = 5000L   // 4 seconds later
        
        val result = EventFormatter.calculateNextNotification(
            nextGCalTime = nextGCalTime,
            nextAppTime = nextAppTime,
            currentTime = currentTime,
            isMuted = false
        )
        
        assertNotNull(result)
        assertEquals(NextNotificationType.GCAL_REMINDER, result!!.type)
        assertEquals(1000L, result.timeUntilMillis)
    }

    @Test
    fun `calculateNextNotification - both available, app sooner`() {
        val currentTime = 1000L
        val nextGCalTime = 10000L  // 9 seconds later
        val nextAppTime = 3000L    // 2 seconds later
        
        val result = EventFormatter.calculateNextNotification(
            nextGCalTime = nextGCalTime,
            nextAppTime = nextAppTime,
            currentTime = currentTime,
            isMuted = false
        )
        
        assertNotNull(result)
        assertEquals(NextNotificationType.APP_ALERT, result!!.type)
        assertEquals(2000L, result.timeUntilMillis)
    }

    @Test
    fun `calculateNextNotification - tie goes to gcal`() {
        val currentTime = 1000L
        val sameTime = 5000L  // Both 4 seconds later
        
        val result = EventFormatter.calculateNextNotification(
            nextGCalTime = sameTime,
            nextAppTime = sameTime,
            currentTime = currentTime,
            isMuted = false
        )
        
        assertNotNull(result)
        assertEquals("GCal should win ties", NextNotificationType.GCAL_REMINDER, result!!.type)
        assertEquals(4000L, result.timeUntilMillis)
    }

    @Test
    fun `calculateNextNotification - muted flag passed through`() {
        val currentTime = 1000L
        val nextGCalTime = 2000L
        
        val result = EventFormatter.calculateNextNotification(
            nextGCalTime = nextGCalTime,
            nextAppTime = null,
            currentTime = currentTime,
            isMuted = true
        )
        
        assertNotNull(result)
        assertTrue("Muted flag should be passed through", result!!.isMuted)
    }

    @Test
    fun `calculateNextNotification - neither available returns null`() {
        val currentTime = 1000L
        
        val result = EventFormatter.calculateNextNotification(
            nextGCalTime = null,
            nextAppTime = null,
            currentTime = currentTime,
            isMuted = false
        )
        
        assertNull(result)
    }

    @Test
    fun `calculateNextNotification - past times filtered out`() {
        val currentTime = 5000L
        val pastTime = 3000L  // In the past
        
        val result = EventFormatter.calculateNextNotification(
            nextGCalTime = pastTime,
            nextAppTime = null,
            currentTime = currentTime,
            isMuted = false
        )
        
        assertNull("Past times should be filtered out", result)
    }

    // === formatNextNotificationIndicator tests with mocked dependencies ===

    private fun createTestEventWithMuted(
        eventId: Long = 1L,
        isMuted: Boolean = false
    ): EventAlertRecord {
        return EventAlertRecord(
            calendarId = 1L,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = baseTime,
            notificationId = eventId.toInt(),
            title = "Test Event",
            desc = "Test Description",
            startTime = baseTime + Consts.HOUR_IN_MILLISECONDS,
            endTime = baseTime + 2 * Consts.HOUR_IN_MILLISECONDS,
            instanceStartTime = baseTime + Consts.HOUR_IN_MILLISECONDS,
            instanceEndTime = baseTime + 2 * Consts.HOUR_IN_MILLISECONDS,
            location = "",
            lastStatusChangeTime = baseTime,
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = baseTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = if (isMuted) 0x0100 else 0  // MUTED_FLAG = 0x0100
        )
    }

    @Test
    fun `formatNextNotificationIndicator - muted event shows muted prefix`() {
        val eventId = 200L
        val eventStartTime = baseTime + 2 * Consts.HOUR_IN_MILLISECONDS
        
        // Create reminders for GCal
        val reminders = listOf(EventReminderRecord.minutes(60))
        
        // Mock CalendarProvider
        val mockCalendarProvider = mockk<CalendarProviderInterface>()
        every { mockCalendarProvider.getEvent(any(), eq(eventId)) } returns createMockEventRecord(
            eventId = eventId,
            startTime = eventStartTime,
            reminders = reminders
        )
        
        // Create formatter with mocked calendar provider
        val testFormatter = EventFormatter(
            ctx = context,
            clock = testClock,
            calendarProvider = mockCalendarProvider
        )
        
        // Create muted event
        val event = EventAlertRecord(
            calendarId = 1L,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = baseTime,
            notificationId = eventId.toInt(),
            title = "Test Event",
            desc = "Test Description",
            startTime = eventStartTime,
            endTime = eventStartTime + Consts.HOUR_IN_MILLISECONDS,
            instanceStartTime = eventStartTime,
            instanceEndTime = eventStartTime + Consts.HOUR_IN_MILLISECONDS,
            location = "",
            lastStatusChangeTime = baseTime,
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = baseTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0x0100  // MUTED_FLAG
        )
        
        val result = testFormatter.formatNextNotificationIndicator(
            event = event,
            displayNextGCalReminder = true,
            displayNextAppAlert = false,
            remindersEnabled = false
        )
        
        assertNotNull(result)
        assertTrue("Muted event should show 🔇 prefix", result!!.contains("🔇"))
        assertTrue("Should still show 📅", result.contains("📅"))
    }

    @Test
    fun `formatNextNotificationIndicatorForCollapsed - finds soonest across events`() {
        val event1Id = 301L
        val event2Id = 302L
        
        // Event 1 has GCal reminder in 2 hours
        val event1StartTime = baseTime + 3 * Consts.HOUR_IN_MILLISECONDS
        val event1Reminders = listOf(EventReminderRecord.minutes(60))  // fires at baseTime + 2h
        
        // Event 2 has GCal reminder in 30 minutes (sooner!)
        val event2StartTime = baseTime + Consts.HOUR_IN_MILLISECONDS
        val event2Reminders = listOf(EventReminderRecord.minutes(30))  // fires at baseTime + 30m
        
        // Mock CalendarProvider
        val mockCalendarProvider = mockk<CalendarProviderInterface>()
        every { mockCalendarProvider.getEvent(any(), eq(event1Id)) } returns createMockEventRecord(
            eventId = event1Id,
            startTime = event1StartTime,
            reminders = event1Reminders
        )
        every { mockCalendarProvider.getEvent(any(), eq(event2Id)) } returns createMockEventRecord(
            eventId = event2Id,
            startTime = event2StartTime,
            reminders = event2Reminders
        )
        
        // Create formatter with mocked calendar provider
        val testFormatter = EventFormatter(
            ctx = context,
            clock = testClock,
            calendarProvider = mockCalendarProvider
        )
        
        val events = listOf(
            createTestEvent(eventId = event1Id, startTime = event1StartTime),
            createTestEvent(eventId = event2Id, startTime = event2StartTime)
        )
        
        val result = testFormatter.formatNextNotificationIndicatorForCollapsed(
            events = events,
            displayNextGCalReminder = true,
            displayNextAppAlert = false,
            remindersEnabled = false
        )
        
        assertNotNull(result)
        assertTrue("Should show 📅", result!!.contains("📅"))
        assertTrue("Should show 30m (the soonest)", result.contains("30m"))
    }
}

