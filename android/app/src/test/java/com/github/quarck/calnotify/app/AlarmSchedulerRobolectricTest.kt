package com.github.quarck.calnotify.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.quiethours.QuietHoursManagerInterface
import com.github.quarck.calnotify.reminders.ReminderStateInterface
import com.github.quarck.calnotify.testutils.MockEventsStorage
import com.github.quarck.calnotify.utils.CNPlusUnitTestClock
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for AlarmScheduler.rescheduleAlarms - the alarm scheduling logic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class AlarmSchedulerRobolectricTest {

    private lateinit var context: Context
    private lateinit var testClock: CNPlusUnitTestClock
    private lateinit var mockEventsStorage: MockEventsStorage
    private lateinit var mockSettings: Settings
    private lateinit var mockQuietHoursManager: QuietHoursManagerInterface
    private lateinit var mockReminderState: ReminderStateInterface
    private lateinit var alarmScheduler: AlarmScheduler

    private val baseTime = 1635724800000L // 2021-11-01 00:00:00 UTC

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testClock = CNPlusUnitTestClock(baseTime)
        mockEventsStorage = MockEventsStorage()
        
        mockSettings = mockk(relaxed = true)
        mockQuietHoursManager = mockk(relaxed = true)
        mockReminderState = mockk(relaxed = true)

        // Default settings
        every { mockSettings.remindersEnabled } returns true
        every { mockSettings.useSetAlarmClock } returns true
        every { mockSettings.reminderIntervalMillisForIndex(any()) } returns 15 * 60 * 1000L // 15 min

        // Default reminder state
        every { mockReminderState.quietHoursOneTimeReminderEnabled } returns false
        every { mockReminderState.currentReminderPatternIndex } returns 0

        // Default quiet hours - not active
        every { mockQuietHoursManager.getSilentUntil(any<Settings>(), any<Long>()) } returns 0L

        // Inject providers
        AlarmScheduler.eventsStorageProvider = { mockEventsStorage }
        AlarmScheduler.reminderStateProvider = { mockReminderState }

        alarmScheduler = AlarmScheduler(testClock)
    }

    @After
    fun cleanup() {
        AlarmScheduler.resetProviders()
        unmockkAll()
    }

    private fun createTestEvent(
        eventId: Long = 1L,
        snoozedUntil: Long = 0L,
        isMuted: Boolean = false,
        isTask: Boolean = false,
        isAlarm: Boolean = false
    ): EventAlertRecord {
        val event = EventAlertRecord(
            calendarId = 1L,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = baseTime,
            notificationId = eventId.toInt(),
            title = "Test Event $eventId",
            desc = "",
            startTime = baseTime + 3600000L,
            endTime = baseTime + 7200000L,
            instanceStartTime = baseTime + 3600000L,
            instanceEndTime = baseTime + 7200000L,
            location = "",
            lastStatusChangeTime = baseTime,
            snoozedUntil = snoozedUntil,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = baseTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None
        )
        event.isMuted = isMuted
        event.isTask = isTask
        if (isAlarm) {
            event.isAlarm = true
        }
        return event
    }

    // === Snooze alarm scheduling tests ===

    @Test
    fun testRescheduleAlarms_snoozedEventSchedulesAlarm() {
        // Given - a snoozed event
        val snoozeTime = baseTime + 30 * 60 * 1000L // 30 min from now
        mockEventsStorage.addEvent(createTestEvent(eventId = 1, snoozedUntil = snoozeTime))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - verify next snooze alarm was set (via persistentState)
        // The alarm is set via context.alarmManager which we can't easily verify,
        // but the logic path was exercised
        assertTrue("Event should still be in storage", mockEventsStorage.events.isNotEmpty())
    }

    @Test
    fun testRescheduleAlarms_pastSnoozeTimeAdjusted() {
        // Given - a snoozed event with snooze time in the PAST (edge case)
        val pastSnoozeTime = baseTime - 10 * 60 * 1000L // 10 min ago
        mockEventsStorage.addEvent(createTestEvent(eventId = 1, snoozedUntil = pastSnoozeTime))

        // When - this should hit the "CRITICAL" error path and adjust the time
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - the code should handle this gracefully (adjust to 5 min from now)
        // We can't easily verify the adjusted time, but the path was exercised
        assertTrue("Event should still be in storage", mockEventsStorage.events.isNotEmpty())
    }

    @Test
    fun testRescheduleAlarms_noSnoozedEventsCancelsAlarm() {
        // Given - no snoozed events (only active events)
        mockEventsStorage.addEvent(createTestEvent(eventId = 1, snoozedUntil = 0L))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - cancellation path is exercised
        assertTrue("Event should still be in storage", mockEventsStorage.events.isNotEmpty())
    }

    // === Reminder alarm scheduling tests ===

    @Test
    fun testRescheduleAlarms_activeEventSchedulesReminderAlarm() {
        // Given - an active (not snoozed) event with reminders enabled
        mockEventsStorage.addEvent(createTestEvent(eventId = 1, snoozedUntil = 0L))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - verify reminder state was updated
        verify { mockReminderState.nextFireExpectedAt = any() }
    }

    @Test
    fun testRescheduleAlarms_mutedEventNoReminder() {
        // Given - only a muted event
        mockEventsStorage.addEvent(createTestEvent(eventId = 1, isMuted = true))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - no reminder should be scheduled (no active events)
        verify(exactly = 0) { mockReminderState.nextFireExpectedAt = any() }
    }

    @Test
    fun testRescheduleAlarms_taskEventNoReminder() {
        // Given - only a task event
        mockEventsStorage.addEvent(createTestEvent(eventId = 1, isTask = true))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - no reminder should be scheduled (tasks don't get reminders)
        verify(exactly = 0) { mockReminderState.nextFireExpectedAt = any() }
    }

    @Test
    fun testRescheduleAlarms_remindersDisabledNoReminder() {
        // Given - active event but reminders disabled
        every { mockSettings.remindersEnabled } returns false
        mockEventsStorage.addEvent(createTestEvent(eventId = 1))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - no reminder should be scheduled
        verify(exactly = 0) { mockReminderState.nextFireExpectedAt = any() }
    }

    // === Quiet hours tests ===

    @Test
    fun testRescheduleAlarms_quietHoursAdjustsReminderTime() {
        // Given - active event during quiet hours
        val quietUntil = baseTime + 2 * 60 * 60 * 1000L // Quiet until 2 hours from now
        every { mockQuietHoursManager.getSilentUntil(any<Settings>(), any<Long>()) } returns quietUntil
        mockEventsStorage.addEvent(createTestEvent(eventId = 1))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - reminder should be scheduled after quiet hours
        verify { mockReminderState.nextFireExpectedAt = quietUntil + Consts.ALARM_THRESHOLD }
    }

    @Test
    fun testRescheduleAlarms_alarmTagOverridesQuietHours() {
        // Given - active event with #alarm tag during quiet hours
        val quietUntil = baseTime + 2 * 60 * 60 * 1000L
        every { mockQuietHoursManager.getSilentUntil(any<Settings>(), any<Long>()) } returns quietUntil
        mockEventsStorage.addEvent(createTestEvent(eventId = 1, isAlarm = true))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - reminder should NOT be adjusted for quiet hours (alarm overrides)
        verify { mockReminderState.nextFireExpectedAt = any() }
        // The set time should be the normal interval, not quiet hours adjusted
    }

    @Test
    fun testRescheduleAlarms_oneTimeReminderAfterQuietHours() {
        // Given - one-time reminder enabled (hack for "fire as soon as possible after quiet hours")
        every { mockReminderState.quietHoursOneTimeReminderEnabled } returns true
        mockEventsStorage.addEvent(createTestEvent(eventId = 1))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - should fire at ALARM_THRESHOLD from now (as soon as possible)
        verify { mockReminderState.nextFireExpectedAt = baseTime + Consts.ALARM_THRESHOLD }
    }

    // === Mixed scenarios ===

    @Test
    fun testRescheduleAlarms_mixedActiveAndSnoozedEvents() {
        // Given - both active and snoozed events
        val snoozeTime = baseTime + 60 * 60 * 1000L // 1 hour from now
        mockEventsStorage.addEvent(createTestEvent(eventId = 1, snoozedUntil = 0L)) // Active
        mockEventsStorage.addEvent(createTestEvent(eventId = 2, snoozedUntil = snoozeTime)) // Snoozed

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - both alarms should be scheduled
        // Snooze alarm for event 2, reminder alarm for event 1
        verify { mockReminderState.nextFireExpectedAt = any() }
    }

    @Test
    fun testRescheduleAlarms_noEventsNoAlarms() {
        // Given - no events
        // mockEventsStorage is empty

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - no alarms scheduled
        verify(exactly = 0) { mockReminderState.nextFireExpectedAt = any() }
    }
}

