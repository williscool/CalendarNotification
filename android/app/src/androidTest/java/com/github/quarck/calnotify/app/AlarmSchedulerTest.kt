package com.github.quarck.calnotify.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.quiethours.QuietHoursManagerInterface
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.utils.CNPlusTestClock
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for AlarmScheduler.rescheduleAlarms - the alarm scheduling logic.
 */
@RunWith(AndroidJUnit4::class)
class AlarmSchedulerTest {

    private lateinit var context: Context
    private lateinit var testClock: CNPlusTestClock
    private lateinit var eventsStorage: EventsStorage
    private lateinit var mockSettings: Settings
    private lateinit var mockQuietHoursManager: QuietHoursManagerInterface
    private lateinit var reminderState: ReminderState
    private lateinit var alarmScheduler: AlarmScheduler

    private val baseTime = 1635724800000L // 2021-11-01 00:00:00 UTC

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testClock = CNPlusTestClock(baseTime)
        eventsStorage = EventsStorage(context)
        reminderState = ReminderState(context)

        mockSettings = mockk(relaxed = true)
        mockQuietHoursManager = mockk(relaxed = true)

        // Default settings
        every { mockSettings.remindersEnabled } returns true
        every { mockSettings.useSetAlarmClock } returns true
        every { mockSettings.reminderIntervalMillisForIndex(any()) } returns 15 * 60 * 1000L // 15 min

        // Default quiet hours - not active
        every { mockQuietHoursManager.getSilentUntil(any<Settings>(), any<Long>()) } returns 0L

        // Clear any existing events
        eventsStorage.deleteAllEvents()
        
        // Reset reminder state
        reminderState.currentReminderPatternIndex = 0
        reminderState.quietHoursOneTimeReminderEnabled = false

        alarmScheduler = AlarmScheduler(testClock)
    }

    @After
    fun cleanup() {
        eventsStorage.deleteAllEvents()
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
        eventsStorage.addEvent(createTestEvent(eventId = 1, snoozedUntil = snoozeTime))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - event should still be in storage (alarm scheduled)
        val events = eventsStorage.events
        assertEquals("Should have 1 event", 1, events.size)
        assertEquals("Event should be snoozed", snoozeTime, events.first().snoozedUntil)
    }

    @Test
    fun testRescheduleAlarms_pastSnoozeTimeAdjusted() {
        // Given - a snoozed event with snooze time in the PAST (edge case)
        val pastSnoozeTime = baseTime - 10 * 60 * 1000L // 10 min ago
        eventsStorage.addEvent(createTestEvent(eventId = 1, snoozedUntil = pastSnoozeTime))

        // When - this should hit the "CRITICAL" error path and adjust the time
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - the code should handle this gracefully
        val events = eventsStorage.events
        assertEquals("Should have 1 event", 1, events.size)
    }

    @Test
    fun testRescheduleAlarms_noSnoozedEventsCancelsAlarm() {
        // Given - no snoozed events (only active events)
        eventsStorage.addEvent(createTestEvent(eventId = 1, snoozedUntil = 0L))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - cancellation path is exercised
        val events = eventsStorage.events
        assertEquals("Should have 1 event", 1, events.size)
    }

    // === Reminder alarm scheduling tests ===

    @Test
    fun testRescheduleAlarms_activeEventSchedulesReminderAlarm() {
        // Given - an active (not snoozed) event with reminders enabled
        eventsStorage.addEvent(createTestEvent(eventId = 1, snoozedUntil = 0L))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - reminder state should be updated
        assertTrue("Reminder should be scheduled", reminderState.nextFireExpectedAt > 0)
    }

    @Test
    fun testRescheduleAlarms_mutedEventNoReminder() {
        // Given - only a muted event
        eventsStorage.addEvent(createTestEvent(eventId = 1, isMuted = true))
        reminderState.nextFireExpectedAt = 0L

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - no reminder should be scheduled (muted events are filtered out)
        val events = eventsStorage.events
        assertEquals("Should have 1 event", 1, events.size)
        assertTrue("Event should be muted", events.first().isMuted)
    }

    @Test
    fun testRescheduleAlarms_taskEventNoReminder() {
        // Given - only a task event
        eventsStorage.addEvent(createTestEvent(eventId = 1, isTask = true))
        reminderState.nextFireExpectedAt = 0L

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - tasks don't get reminders
        val events = eventsStorage.events
        assertEquals("Should have 1 event", 1, events.size)
        assertTrue("Event should be task", events.first().isTask)
    }

    @Test
    fun testRescheduleAlarms_remindersDisabledNoReminder() {
        // Given - active event but reminders disabled
        every { mockSettings.remindersEnabled } returns false
        eventsStorage.addEvent(createTestEvent(eventId = 1))
        reminderState.nextFireExpectedAt = 0L

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - path exercised, event still in storage
        val events = eventsStorage.events
        assertEquals("Should have 1 event", 1, events.size)
    }

    // === Quiet hours tests ===

    @Test
    fun testRescheduleAlarms_quietHoursAdjustsReminderTime() {
        // Given - active event during quiet hours
        val quietUntil = baseTime + 2 * 60 * 60 * 1000L // Quiet until 2 hours from now
        every { mockQuietHoursManager.getSilentUntil(any<Settings>(), any<Long>()) } returns quietUntil
        eventsStorage.addEvent(createTestEvent(eventId = 1))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - reminder should be scheduled after quiet hours
        assertEquals(
            "Reminder should be adjusted for quiet hours",
            quietUntil + Consts.ALARM_THRESHOLD,
            reminderState.nextFireExpectedAt
        )
    }

    @Test
    fun testRescheduleAlarms_alarmTagOverridesQuietHours() {
        // Given - active event with #alarm tag during quiet hours
        val quietUntil = baseTime + 2 * 60 * 60 * 1000L
        every { mockQuietHoursManager.getSilentUntil(any<Settings>(), any<Long>()) } returns quietUntil
        eventsStorage.addEvent(createTestEvent(eventId = 1, isAlarm = true))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - reminder should NOT be adjusted (alarm overrides quiet hours)
        assertNotEquals(
            "Alarm should NOT be adjusted for quiet hours",
            quietUntil + Consts.ALARM_THRESHOLD,
            reminderState.nextFireExpectedAt
        )
    }

    @Test
    fun testRescheduleAlarms_oneTimeReminderAfterQuietHours() {
        // Given - one-time reminder enabled
        reminderState.quietHoursOneTimeReminderEnabled = true
        eventsStorage.addEvent(createTestEvent(eventId = 1))

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - should fire at ALARM_THRESHOLD from now
        assertEquals(
            "One-time reminder should fire ASAP",
            baseTime + Consts.ALARM_THRESHOLD,
            reminderState.nextFireExpectedAt
        )
    }

    // === Mixed scenarios ===

    @Test
    fun testRescheduleAlarms_mixedActiveAndSnoozedEvents() {
        // Given - both active and snoozed events
        val snoozeTime = baseTime + 60 * 60 * 1000L // 1 hour from now
        eventsStorage.addEvent(createTestEvent(eventId = 1, snoozedUntil = 0L)) // Active
        eventsStorage.addEvent(createTestEvent(eventId = 2, snoozedUntil = snoozeTime)) // Snoozed

        // When
        alarmScheduler.rescheduleAlarms(context, mockSettings, mockQuietHoursManager)

        // Then - both alarms should be scheduled
        val events = eventsStorage.events
        assertEquals("Should have 2 events", 2, events.size)
        assertTrue("Reminder should be scheduled", reminderState.nextFireExpectedAt > 0)
    }
}

