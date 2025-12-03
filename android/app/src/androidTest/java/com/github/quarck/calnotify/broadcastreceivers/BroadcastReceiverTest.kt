package com.github.quarck.calnotify.broadcastreceivers

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorInterface
import com.github.quarck.calnotify.logs.DevLog
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for broadcast receivers
 */
@RunWith(AndroidJUnit4::class)
class BroadcastReceiverTest {
    private val LOG_TAG = "BroadcastReceiverTest"

    private lateinit var context: Context
    private lateinit var mockCalendarMonitor: CalendarMonitorInterface

    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up BroadcastReceiverTest")
        context = InstrumentationRegistry.getInstrumentation().targetContext
        mockCalendarMonitor = mockk(relaxed = true)

        // Mock ApplicationController to verify calls
        mockkObject(ApplicationController)
        every { ApplicationController.onBootComplete(any()) } just Runs
        every { ApplicationController.onEventAlarm(any()) } just Runs
        every { ApplicationController.onAppUpdated(any()) } just Runs
        every { ApplicationController.onTimeChanged(any()) } just Runs
        every { ApplicationController.onReceivedRescheduleConfirmations(any(), any()) } just Runs
        every { ApplicationController.CalendarMonitor } returns mockCalendarMonitor
    }

    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up BroadcastReceiverTest")
        unmockkAll()
    }

    // === BootCompleteBroadcastReceiver tests ===

    @Test
    fun testBootCompleteBroadcastReceiverCallsOnBootComplete() {
        DevLog.info(LOG_TAG, "Running testBootCompleteBroadcastReceiverCallsOnBootComplete")

        val receiver = BootCompleteBroadcastReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        receiver.onReceive(context, intent)

        verify(exactly = 1) { ApplicationController.onBootComplete(context) }
    }

    @Test
    fun testBootCompleteBroadcastReceiverWithNullContext() {
        DevLog.info(LOG_TAG, "Running testBootCompleteBroadcastReceiverWithNullContext")

        val receiver = BootCompleteBroadcastReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        receiver.onReceive(null, intent)

        verify(exactly = 0) { ApplicationController.onBootComplete(any()) }
    }

    @Test
    fun testBootCompleteBroadcastReceiverWithNullIntent() {
        DevLog.info(LOG_TAG, "Running testBootCompleteBroadcastReceiverWithNullIntent")

        val receiver = BootCompleteBroadcastReceiver()

        receiver.onReceive(context, null)

        verify(exactly = 1) { ApplicationController.onBootComplete(context) }
    }

    // === SnoozeAlarmBroadcastReceiver tests ===

    @Test
    fun testSnoozeAlarmBroadcastReceiverCallsOnEventAlarm() {
        DevLog.info(LOG_TAG, "Running testSnoozeAlarmBroadcastReceiverCallsOnEventAlarm")

        val receiver = SnoozeAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 1) { ApplicationController.onEventAlarm(context) }
    }

    @Test
    fun testSnoozeAlarmBroadcastReceiverWithNullContext() {
        DevLog.info(LOG_TAG, "Running testSnoozeAlarmBroadcastReceiverWithNullContext")

        val receiver = SnoozeAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(null, intent)

        verify(exactly = 0) { ApplicationController.onEventAlarm(any()) }
    }

    // === SnoozeExactAlarmBroadcastReceiver tests ===

    @Test
    fun testSnoozeExactAlarmBroadcastReceiverCallsOnEventAlarm() {
        DevLog.info(LOG_TAG, "Running testSnoozeExactAlarmBroadcastReceiverCallsOnEventAlarm")

        val receiver = SnoozeExactAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 1) { ApplicationController.onEventAlarm(context) }
    }

    @Test
    fun testSnoozeExactAlarmBroadcastReceiverWithNullContext() {
        DevLog.info(LOG_TAG, "Running testSnoozeExactAlarmBroadcastReceiverWithNullContext")

        val receiver = SnoozeExactAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(null, intent)

        verify(exactly = 0) { ApplicationController.onEventAlarm(any()) }
    }

    // === ReminderAlarmBroadcastReceiver tests ===

    @Test
    fun testReminderAlarmBroadcastReceiverInstantiation() {
        DevLog.info(LOG_TAG, "Running testReminderAlarmBroadcastReceiverInstantiation")

        val receiver = ReminderAlarmBroadcastReceiver()
        assert(receiver is android.content.BroadcastReceiver)
    }

    @Test
    fun testReminderExactAlarmBroadcastReceiverInstantiation() {
        DevLog.info(LOG_TAG, "Running testReminderExactAlarmBroadcastReceiverInstantiation")

        val receiver = ReminderExactAlarmBroadcastReceiver()
        assert(receiver is android.content.BroadcastReceiver)
    }

    // === AppUpdatedBroadcastReceiver tests ===

    @Test
    fun testAppUpdatedBroadcastReceiverCallsOnAppUpdated() {
        DevLog.info(LOG_TAG, "Running testAppUpdatedBroadcastReceiverCallsOnAppUpdated")

        val receiver = AppUpdatedBroadcastReceiver()
        val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)

        receiver.onReceive(context, intent)

        verify(exactly = 1) { ApplicationController.onAppUpdated(context) }
    }

    @Test
    fun testAppUpdatedBroadcastReceiverWithNullContext() {
        DevLog.info(LOG_TAG, "Running testAppUpdatedBroadcastReceiverWithNullContext")

        val receiver = AppUpdatedBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(null, intent)

        verify(exactly = 0) { ApplicationController.onAppUpdated(any()) }
    }

    // === TimeSetBroadcastReceiver tests ===

    @Test
    fun testTimeSetBroadcastReceiverCallsOnTimeChanged() {
        DevLog.info(LOG_TAG, "Running testTimeSetBroadcastReceiverCallsOnTimeChanged")

        val receiver = TimeSetBroadcastReceiver()
        val intent = Intent(Intent.ACTION_TIME_CHANGED)

        receiver.onReceive(context, intent)

        verify(exactly = 1) { ApplicationController.onTimeChanged(context) }
    }

    @Test
    fun testTimeSetBroadcastReceiverWithNullContext() {
        DevLog.info(LOG_TAG, "Running testTimeSetBroadcastReceiverWithNullContext")

        val receiver = TimeSetBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(null, intent)

        verify(exactly = 0) { ApplicationController.onTimeChanged(any()) }
    }

    // === ManualEventAlarmBroadcastReceiver tests ===

    @Test
    fun testManualEventAlarmBroadcastReceiverCallsCalendarMonitor() {
        DevLog.info(LOG_TAG, "Running testManualEventAlarmBroadcastReceiverCallsCalendarMonitor")

        val receiver = ManualEventAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 1) { mockCalendarMonitor.onAlarmBroadcast(context, intent) }
    }

    @Test
    fun testManualEventAlarmBroadcastReceiverWithNullContext() {
        DevLog.info(LOG_TAG, "Running testManualEventAlarmBroadcastReceiverWithNullContext")

        val receiver = ManualEventAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(null, intent)

        verify(exactly = 0) { mockCalendarMonitor.onAlarmBroadcast(any(), any()) }
    }

    // === ManualEventExactAlarmBroadcastReceiver tests ===

    @Test
    fun testManualEventExactAlarmBroadcastReceiverCallsCalendarMonitor() {
        DevLog.info(LOG_TAG, "Running testManualEventExactAlarmBroadcastReceiverCallsCalendarMonitor")

        val receiver = ManualEventExactAlarmBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 1) { mockCalendarMonitor.onAlarmBroadcast(context, intent) }
    }

    // === ManualEventAlarmPeriodicRescanBroadcastReceiver tests ===

    @Test
    fun testManualEventAlarmPeriodicRescanBroadcastReceiverCallsCalendarMonitor() {
        DevLog.info(LOG_TAG, "Running testManualEventAlarmPeriodicRescanBroadcastReceiverCallsCalendarMonitor")

        val receiver = ManualEventAlarmPeriodicRescanBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(context, intent)

        verify(exactly = 1) { mockCalendarMonitor.onPeriodicRescanBroadcast(context, intent) }
    }

    @Test
    fun testManualEventAlarmPeriodicRescanBroadcastReceiverWithNullContext() {
        DevLog.info(LOG_TAG, "Running testManualEventAlarmPeriodicRescanBroadcastReceiverWithNullContext")

        val receiver = ManualEventAlarmPeriodicRescanBroadcastReceiver()
        val intent = Intent()

        receiver.onReceive(null, intent)

        verify(exactly = 0) { mockCalendarMonitor.onPeriodicRescanBroadcast(any(), any()) }
    }

    // === RescheduleConfirmationsBroadcastReceiver tests ===

    @Test
    fun testRescheduleConfirmationsBroadcastReceiverCallsApplicationController() {
        DevLog.info(LOG_TAG, "Running testRescheduleConfirmationsBroadcastReceiverCallsApplicationController")

        val receiver = RescheduleConfirmationsBroadcastReceiver()
        val intent = Intent().apply {
            putExtra("reschedule_confirmations", "test_value")
        }

        receiver.onReceive(context, intent)

        verify(exactly = 1) { ApplicationController.onReceivedRescheduleConfirmations(context, "test_value") }
    }

    @Test
    fun testRescheduleConfirmationsBroadcastReceiverWithNullContext() {
        DevLog.info(LOG_TAG, "Running testRescheduleConfirmationsBroadcastReceiverWithNullContext")

        val receiver = RescheduleConfirmationsBroadcastReceiver()
        val intent = Intent().apply {
            putExtra("reschedule_confirmations", "test_value")
        }

        receiver.onReceive(null, intent)

        verify(exactly = 0) { ApplicationController.onReceivedRescheduleConfirmations(any(), any()) }
    }

    @Test
    fun testRescheduleConfirmationsBroadcastReceiverWithMissingExtra() {
        DevLog.info(LOG_TAG, "Running testRescheduleConfirmationsBroadcastReceiverWithMissingExtra")

        val receiver = RescheduleConfirmationsBroadcastReceiver()
        val intent = Intent() // No extra

        receiver.onReceive(context, intent)

        verify(exactly = 0) { ApplicationController.onReceivedRescheduleConfirmations(any(), any()) }
    }
}

