package com.github.quarck.calnotify.broadcastreceivers

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.SettingsInterface
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.isActiveAlarm
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.quiethours.QuietHoursManagerInterface
import com.github.quarck.calnotify.reminders.ReminderStateInterface
import com.github.quarck.calnotify.utils.CNPlusTestClock
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for ReminderAlarmGenericBroadcastReceiver.
 * 
 * Tests the core reminder logic on a real device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class ReminderAlarmTest {

    private val LOG_TAG = "ReminderAlarmTest"

    private lateinit var context: Context
    private lateinit var testClock: CNPlusTestClock
    private lateinit var mockSettings: SettingsInterface
    private lateinit var mockReminderState: ReminderStateInterface
    private lateinit var mockEventsStorage: EventsStorageInterface
    private lateinit var mockQuietHoursManager: QuietHoursManagerInterface

    private val baseTime = 1635768000000L // 2021-11-01 12:00:00 UTC

    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up ReminderAlarmTest")
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testClock = CNPlusTestClock(baseTime)

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
        DevLog.info(LOG_TAG, "Cleaning up ReminderAlarmTest")
        ReminderAlarmGenericBroadcastReceiver.resetProviders()
        unmockkAll()
    }

    // === Basic flow tests ===

    @Test
    fun testReminderFiresWhenConditionsMet() {
        DevLog.info(LOG_TAG, "Running testReminderFiresWhenConditionsMet")

        val lastFireTime = baseTime - 20 * 60 * 1000L
        every { mockReminderState.reminderLastFireTime } returns lastFireTime

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 1) { ApplicationController.fireEventReminder(context, false, false) }
        verify(exactly = 1) { mockReminderState.onReminderFired(any()) }
    }

    @Test
    fun testReminderDoesNotFireWhenRemindersDisabled() {
        DevLog.info(LOG_TAG, "Running testReminderDoesNotFireWhenRemindersDisabled")

        every { mockSettings.remindersEnabled } returns false

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 0) { ApplicationController.fireEventReminder(any(), any(), any()) }
    }

    @Test
    fun testReminderDoesNotFireWhenNoActiveEvents() {
        DevLog.info(LOG_TAG, "Running testReminderDoesNotFireWhenNoActiveEvents")

        every { ApplicationController.hasActiveEventsToRemind(any()) } returns false

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 0) { ApplicationController.fireEventReminder(any(), any(), any()) }
    }

    // === Interval timing tests ===

    @Test
    fun testReminderDoesNotFireTooEarly() {
        DevLog.info(LOG_TAG, "Running testReminderDoesNotFireTooEarly")

        val lastFireTime = baseTime - 5 * 60 * 1000L // 5 min ago
        every { mockReminderState.reminderLastFireTime } returns lastFireTime

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 0) { ApplicationController.fireEventReminder(any(), any(), any()) }
    }

    @Test
    fun testReminderFiresAfterIntervalElapsed() {
        DevLog.info(LOG_TAG, "Running testReminderFiresAfterIntervalElapsed")

        val lastFireTime = baseTime - 16 * 60 * 1000L // 16 min ago
        every { mockReminderState.reminderLastFireTime } returns lastFireTime

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 1) { ApplicationController.fireEventReminder(context, false, false) }
    }

    // === Max reminders tests ===

    @Test
    fun testReminderStopsAfterMaxFires() {
        DevLog.info(LOG_TAG, "Running testReminderStopsAfterMaxFires")

        every { mockSettings.maxNumberOfReminders } returns 5
        every { mockReminderState.numRemindersFired } returns 5
        every { mockReminderState.reminderLastFireTime } returns baseTime - 20 * 60 * 1000L

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 0) { ApplicationController.fireEventReminder(any(), any(), any()) }
    }

    @Test
    fun testUnlimitedRemindersWhenMaxIsZero() {
        DevLog.info(LOG_TAG, "Running testUnlimitedRemindersWhenMaxIsZero")

        every { mockSettings.maxNumberOfReminders } returns 0
        every { mockReminderState.numRemindersFired } returns 100
        every { mockReminderState.reminderLastFireTime } returns baseTime - 20 * 60 * 1000L

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 1) { ApplicationController.fireEventReminder(context, false, false) }
    }

    // === Quiet hours tests ===

    @Test
    fun testReminderPostponedDuringQuietHours() {
        DevLog.info(LOG_TAG, "Running testReminderPostponedDuringQuietHours")

        val quietUntil = baseTime + 60 * 60 * 1000L
        every { mockQuietHoursManager.getSilentUntil(any<SettingsInterface>()) } returns quietUntil
        every { mockReminderState.reminderLastFireTime } returns baseTime - 20 * 60 * 1000L

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 0) { ApplicationController.fireEventReminder(any(), any(), any()) }
    }

    @Test
    fun testAlarmTagOverridesQuietHours() {
        DevLog.info(LOG_TAG, "Running testAlarmTagOverridesQuietHours")

        val quietUntil = baseTime + 60 * 60 * 1000L
        every { mockQuietHoursManager.getSilentUntil(any<SettingsInterface>()) } returns quietUntil
        every { mockReminderState.reminderLastFireTime } returns baseTime - 20 * 60 * 1000L

        val alarmEvent = mockk<EventAlertRecord>(relaxed = true)
        every { alarmEvent.isActiveAlarm } returns true
        every { alarmEvent.isMuted } returns false
        every { alarmEvent.isTask } returns false
        every { mockEventsStorage.events } returns listOf(alarmEvent)

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 1) { ApplicationController.fireEventReminder(context, false, true) }
    }

    // === One-time reminder after quiet hours tests ===

    @Test
    fun testOneTimeReminderFiresAfterQuietHours() {
        DevLog.info(LOG_TAG, "Running testOneTimeReminderFiresAfterQuietHours")

        every { mockReminderState.quietHoursOneTimeReminderEnabled } returns true
        every { mockQuietHoursManager.getSilentUntil(any<SettingsInterface>()) } returns 0L

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 1) { ApplicationController.fireEventReminder(context, true, false) }
    }

    @Test
    fun testOneTimeReminderPostponedDuringQuietHours() {
        DevLog.info(LOG_TAG, "Running testOneTimeReminderPostponedDuringQuietHours")

        every { mockReminderState.quietHoursOneTimeReminderEnabled } returns true
        val quietUntil = baseTime + 60 * 60 * 1000L
        every { mockQuietHoursManager.getSilentUntil(any<SettingsInterface>()) } returns quietUntil

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 0) { ApplicationController.fireEventReminder(any(), any(), any()) }
    }

    // === Late alarm detection tests ===

    @Test
    fun testLateAlarmDetected() {
        DevLog.info(LOG_TAG, "Running testLateAlarmDetected")

        val expectedAt = baseTime - 2 * 60 * 1000L // 2 min ago
        every { mockReminderState.nextFireExpectedAt } returns expectedAt
        every { mockReminderState.reminderLastFireTime } returns baseTime - 20 * 60 * 1000L

        val receiver = ReminderAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 1) { ApplicationController.onReminderAlarmLate(context, baseTime, expectedAt) }
    }
}

