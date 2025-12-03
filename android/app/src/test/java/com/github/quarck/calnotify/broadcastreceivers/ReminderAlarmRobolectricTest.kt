package com.github.quarck.calnotify.broadcastreceivers

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.SettingsInterface
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.quiethours.QuietHoursManagerInterface
import com.github.quarck.calnotify.reminders.ReminderStateInterface
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
 * Comprehensive tests for ReminderAlarmGenericBroadcastReceiver.
 * 
 * This tests the core reminder logic that makes the app valuable:
 * - Reminders fire at configured intervals
 * - Quiet hours are respected (unless #alarm tag overrides)
 * - Max reminders limit is enforced
 * - One-time reminder after quiet hours works
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class ReminderAlarmRobolectricTest {

    private lateinit var context: Context
    private lateinit var testClock: CNPlusUnitTestClock
    private lateinit var mockSettings: SettingsInterface
    private lateinit var mockReminderState: ReminderStateInterface
    private lateinit var mockEventsStorage: EventsStorageInterface
    private lateinit var mockQuietHoursManager: QuietHoursManagerInterface

    private val baseTime = 1635768000000L // 2021-11-01 12:00:00 UTC

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testClock = CNPlusUnitTestClock(baseTime)

        // Create mocks
        mockSettings = mockk(relaxed = true)
        mockReminderState = mockk(relaxed = true)
        mockEventsStorage = mockk(relaxed = true)
        mockQuietHoursManager = mockk(relaxed = true)

        // Setup default mock behaviors
        every { mockSettings.remindersEnabled } returns true
        every { mockSettings.maxNumberOfReminders } returns 0 // unlimited
        every { mockSettings.useSetAlarmClock } returns true
        every { mockSettings.currentAndNextReminderIntervalsMillis(any()) } returns Pair(
            15 * 60 * 1000L, // 15 min current
            15 * 60 * 1000L  // 15 min next
        )

        every { mockReminderState.currentReminderPatternIndex } returns 0
        every { mockReminderState.quietHoursOneTimeReminderEnabled } returns false
        every { mockReminderState.reminderLastFireTime } returns 0L
        every { mockReminderState.numRemindersFired } returns 0
        every { mockReminderState.nextFireExpectedAt } returns 0L

        every { mockEventsStorage.events } returns emptyList()
        
        every { mockQuietHoursManager.getSilentUntil(any<SettingsInterface>()) } returns 0L

        // Mock ApplicationController
        mockkObject(ApplicationController)
        every { ApplicationController.hasActiveEventsToRemind(any()) } returns true
        every { ApplicationController.fireEventReminder(any(), any(), any()) } just Runs
        every { ApplicationController.onReminderAlarmLate(any(), any(), any()) } just Runs

        // Inject test providers
        ReminderAlarmGenericBroadcastReceiver.clockProvider = { testClock }
        ReminderAlarmGenericBroadcastReceiver.settingsProvider = { mockSettings }
        ReminderAlarmGenericBroadcastReceiver.reminderStateProvider = { mockReminderState }
        ReminderAlarmGenericBroadcastReceiver.eventsStorageProvider = { mockEventsStorage }
        ReminderAlarmGenericBroadcastReceiver.quietHoursManagerProvider = { mockQuietHoursManager }
    }

    @After
    fun cleanup() {
        ReminderAlarmGenericBroadcastReceiver.resetProviders()
        unmockkAll()
    }

    // === Basic flow tests ===

    @Test
    fun testReminderFiresWhenConditionsMet() {
        // Given - reminders enabled, no quiet hours, enough time since last fire
        val lastFireTime = baseTime - 20 * 60 * 1000L // 20 min ago
        every { mockReminderState.reminderLastFireTime } returns lastFireTime

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - reminder should fire
        verify(exactly = 1) { ApplicationController.fireEventReminder(context, false, false) }
        verify(exactly = 1) { mockReminderState.onReminderFired(any()) }
    }

    @Test
    fun testReminderDoesNotFireWhenRemindersDisabled() {
        // Given - reminders disabled
        every { mockSettings.remindersEnabled } returns false

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - reminder should NOT fire
        verify(exactly = 0) { ApplicationController.fireEventReminder(any(), any(), any()) }
    }

    @Test
    fun testReminderDoesNotFireWhenNoActiveEvents() {
        // Given - no active events to remind
        every { ApplicationController.hasActiveEventsToRemind(any()) } returns false

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - reminder should NOT fire
        verify(exactly = 0) { ApplicationController.fireEventReminder(any(), any(), any()) }
    }

    // === Interval timing tests ===

    @Test
    fun testReminderDoesNotFireTooEarly() {
        // Given - last fire was only 5 min ago, interval is 15 min
        val lastFireTime = baseTime - 5 * 60 * 1000L // 5 min ago
        every { mockReminderState.reminderLastFireTime } returns lastFireTime

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - reminder should NOT fire (too early)
        verify(exactly = 0) { ApplicationController.fireEventReminder(any(), any(), any()) }
    }

    @Test
    fun testReminderFiresAfterIntervalElapsed() {
        // Given - last fire was 16 min ago, interval is 15 min
        val lastFireTime = baseTime - 16 * 60 * 1000L // 16 min ago
        every { mockReminderState.reminderLastFireTime } returns lastFireTime

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - reminder should fire
        verify(exactly = 1) { ApplicationController.fireEventReminder(context, false, false) }
    }

    // === Max reminders tests ===

    @Test
    fun testReminderStopsAfterMaxFires() {
        // Given - max is 5, already fired 5 times
        every { mockSettings.maxNumberOfReminders } returns 5
        every { mockReminderState.numRemindersFired } returns 5
        every { mockReminderState.reminderLastFireTime } returns baseTime - 20 * 60 * 1000L

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - reminder should NOT fire (exceeded max)
        verify(exactly = 0) { ApplicationController.fireEventReminder(any(), any(), any()) }
    }

    @Test
    fun testReminderFiresBeforeMaxReached() {
        // Given - max is 5, only fired 4 times
        every { mockSettings.maxNumberOfReminders } returns 5
        every { mockReminderState.numRemindersFired } returns 4
        every { mockReminderState.reminderLastFireTime } returns baseTime - 20 * 60 * 1000L

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - reminder should fire
        verify(exactly = 1) { ApplicationController.fireEventReminder(context, false, false) }
    }

    @Test
    fun testUnlimitedRemindersWhenMaxIsZero() {
        // Given - max is 0 (unlimited), fired many times
        every { mockSettings.maxNumberOfReminders } returns 0
        every { mockReminderState.numRemindersFired } returns 100
        every { mockReminderState.reminderLastFireTime } returns baseTime - 20 * 60 * 1000L

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - reminder should fire (unlimited)
        verify(exactly = 1) { ApplicationController.fireEventReminder(context, false, false) }
    }

    // === Quiet hours tests ===

    @Test
    fun testReminderPostponedDuringQuietHours() {
        // Given - quiet hours active until 1 hour from now
        val quietUntil = baseTime + 60 * 60 * 1000L
        every { mockQuietHoursManager.getSilentUntil(any<SettingsInterface>()) } returns quietUntil
        every { mockReminderState.reminderLastFireTime } returns baseTime - 20 * 60 * 1000L

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - reminder should NOT fire (quiet hours)
        verify(exactly = 0) { ApplicationController.fireEventReminder(any(), any(), any()) }
    }

    @Test
    fun testAlarmTagOverridesQuietHours() {
        // Given - quiet hours active, but there's an event with #alarm tag
        val quietUntil = baseTime + 60 * 60 * 1000L
        every { mockQuietHoursManager.getSilentUntil(any<SettingsInterface>()) } returns quietUntil
        every { mockReminderState.reminderLastFireTime } returns baseTime - 20 * 60 * 1000L

        // Mock an event with isActiveAlarm = true
        val alarmEvent = mockk<EventAlertRecord>(relaxed = true)
        every { alarmEvent.isActiveAlarm } returns true
        every { alarmEvent.isMuted } returns false
        every { alarmEvent.isTask } returns false
        every { mockEventsStorage.events } returns listOf(alarmEvent)

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - reminder SHOULD fire (alarm overrides quiet hours)
        verify(exactly = 1) { ApplicationController.fireEventReminder(context, false, true) }
    }

    // === One-time reminder after quiet hours tests ===

    @Test
    fun testOneTimeReminderFiresAfterQuietHours() {
        // Given - one-time reminder enabled, not in quiet hours
        every { mockReminderState.quietHoursOneTimeReminderEnabled } returns true
        every { mockQuietHoursManager.getSilentUntil(any<SettingsInterface>()) } returns 0L

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - reminder should fire with itIsAfterQuietHoursReminder = true
        verify(exactly = 1) { ApplicationController.fireEventReminder(context, true, false) }
    }

    @Test
    fun testOneTimeReminderPostponedDuringQuietHours() {
        // Given - one-time reminder enabled, still in quiet hours
        every { mockReminderState.quietHoursOneTimeReminderEnabled } returns true
        val quietUntil = baseTime + 60 * 60 * 1000L
        every { mockQuietHoursManager.getSilentUntil(any<SettingsInterface>()) } returns quietUntil

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - reminder should NOT fire yet
        verify(exactly = 0) { ApplicationController.fireEventReminder(any(), any(), any()) }
    }

    // === Late alarm detection tests ===

    @Test
    fun testLateAlarmDetected() {
        // Given - alarm was expected 2 minutes ago (past threshold)
        val expectedAt = baseTime - 2 * 60 * 1000L // 2 min ago
        every { mockReminderState.nextFireExpectedAt } returns expectedAt
        every { mockReminderState.reminderLastFireTime } returns baseTime - 20 * 60 * 1000L

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - late alarm should be reported
        verify(exactly = 1) { ApplicationController.onReminderAlarmLate(context, baseTime, expectedAt) }
    }

    @Test
    fun testOnTimeAlarmNotReportedAsLate() {
        // Given - alarm arrived on time (within threshold)
        val expectedAt = baseTime - 5 * 1000L // 5 seconds ago (within ALARM_THRESHOLD)
        every { mockReminderState.nextFireExpectedAt } returns expectedAt
        every { mockReminderState.reminderLastFireTime } returns baseTime - 20 * 60 * 1000L

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - should NOT be reported as late
        verify(exactly = 0) { ApplicationController.onReminderAlarmLate(any(), any(), any()) }
    }

    // === ReminderState update tests ===

    @Test
    fun testReminderStateUpdatedOnFire() {
        // Given
        every { mockReminderState.reminderLastFireTime } returns baseTime - 20 * 60 * 1000L

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - reminder state should be updated
        verify(exactly = 1) { mockReminderState.onReminderFired(any()) }
    }

    @Test
    fun testNextFireExpectedAtUpdated() {
        // Given
        every { mockReminderState.reminderLastFireTime } returns baseTime - 20 * 60 * 1000L

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then - next fire expected time should be set
        verify { mockReminderState.nextFireExpectedAt = any() }
    }
}

