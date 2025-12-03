package com.github.quarck.calnotify.broadcastreceivers

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorInterface
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for broadcast receivers - verifies they trigger the correct ApplicationController methods
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class BroadcastReceiverRobolectricTest {

    private lateinit var context: Context
    private lateinit var mockCalendarMonitor: CalendarMonitorInterface

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
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
        unmockkAll()
    }

    // === BootCompleteBroadcastReceiver tests ===

    @Test
    fun testBootCompleteBroadcastReceiverCallsOnBootComplete() {
        // Given
        val receiver = BootCompleteBroadcastReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // When
        receiver.onReceive(context, intent)

        // Then
        verify(exactly = 1) { ApplicationController.onBootComplete(context) }
    }

    @Test
    fun testBootCompleteBroadcastReceiverWithNullContext() {
        // Given
        val receiver = BootCompleteBroadcastReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // When - null context
        receiver.onReceive(null, intent)

        // Then - should not call ApplicationController
        verify(exactly = 0) { ApplicationController.onBootComplete(any()) }
    }

    @Test
    fun testBootCompleteBroadcastReceiverWithNullIntent() {
        // Given
        val receiver = BootCompleteBroadcastReceiver()

        // When - null intent (should still work, intent is not used)
        receiver.onReceive(context, null)

        // Then
        verify(exactly = 1) { ApplicationController.onBootComplete(context) }
    }

    // === SnoozeAlarmBroadcastReceiver tests ===

    @Test
    fun testSnoozeAlarmBroadcastReceiverCallsOnEventAlarm() {
        // Given
        val receiver = SnoozeAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then
        verify(exactly = 1) { ApplicationController.onEventAlarm(context) }
    }

    @Test
    fun testSnoozeAlarmBroadcastReceiverWithNullContext() {
        // Given
        val receiver = SnoozeAlarmBroadcastReceiver()
        val intent = Intent()

        // When - null context
        receiver.onReceive(null, intent)

        // Then - should not call ApplicationController
        verify(exactly = 0) { ApplicationController.onEventAlarm(any()) }
    }

    // === SnoozeExactAlarmBroadcastReceiver tests ===

    @Test
    fun testSnoozeExactAlarmBroadcastReceiverCallsOnEventAlarm() {
        // Given
        val receiver = SnoozeExactAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then
        verify(exactly = 1) { ApplicationController.onEventAlarm(context) }
    }

    @Test
    fun testSnoozeExactAlarmBroadcastReceiverWithNullContext() {
        // Given
        val receiver = SnoozeExactAlarmBroadcastReceiver()
        val intent = Intent()

        // When - null context
        receiver.onReceive(null, intent)

        // Then - should not call ApplicationController
        verify(exactly = 0) { ApplicationController.onEventAlarm(any()) }
    }

    // === ReminderAlarmBroadcastReceiver tests ===
    // Note: ReminderAlarmGenericBroadcastReceiver has more complex logic
    // but we test that it's instantiable and has the expected structure

    @Test
    fun testReminderAlarmBroadcastReceiverInstantiation() {
        // Just verify we can instantiate the receiver
        val receiver = ReminderAlarmBroadcastReceiver()
        // The receiver exists and is a BroadcastReceiver
        assert(receiver is android.content.BroadcastReceiver)
    }

    @Test
    fun testReminderExactAlarmBroadcastReceiverInstantiation() {
        val receiver = ReminderExactAlarmBroadcastReceiver()
        assert(receiver is android.content.BroadcastReceiver)
    }

    // === AppUpdatedBroadcastReceiver tests ===

    @Test
    fun testAppUpdatedBroadcastReceiverCallsOnAppUpdated() {
        // Given
        val receiver = AppUpdatedBroadcastReceiver()
        val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)

        // When
        receiver.onReceive(context, intent)

        // Then
        verify(exactly = 1) { ApplicationController.onAppUpdated(context) }
    }

    @Test
    fun testAppUpdatedBroadcastReceiverWithNullContext() {
        // Given
        val receiver = AppUpdatedBroadcastReceiver()
        val intent = Intent()

        // When - null context
        receiver.onReceive(null, intent)

        // Then - should not call ApplicationController
        verify(exactly = 0) { ApplicationController.onAppUpdated(any()) }
    }

    // === TimeSetBroadcastReceiver tests ===

    @Test
    fun testTimeSetBroadcastReceiverCallsOnTimeChanged() {
        // Given
        val receiver = TimeSetBroadcastReceiver()
        val intent = Intent(Intent.ACTION_TIME_CHANGED)

        // When
        receiver.onReceive(context, intent)

        // Then
        verify(exactly = 1) { ApplicationController.onTimeChanged(context) }
    }

    @Test
    fun testTimeSetBroadcastReceiverWithNullContext() {
        // Given
        val receiver = TimeSetBroadcastReceiver()
        val intent = Intent()

        // When - null context
        receiver.onReceive(null, intent)

        // Then - should not call ApplicationController
        verify(exactly = 0) { ApplicationController.onTimeChanged(any()) }
    }

    // === ManualEventAlarmBroadcastReceiver tests ===

    @Test
    fun testManualEventAlarmBroadcastReceiverCallsCalendarMonitor() {
        // Given
        val receiver = ManualEventAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then
        verify(exactly = 1) { mockCalendarMonitor.onAlarmBroadcast(context, intent) }
    }

    @Test
    fun testManualEventAlarmBroadcastReceiverWithNullContext() {
        // Given
        val receiver = ManualEventAlarmBroadcastReceiver()
        val intent = Intent()

        // When - null context
        receiver.onReceive(null, intent)

        // Then - should not call CalendarMonitor
        verify(exactly = 0) { mockCalendarMonitor.onAlarmBroadcast(any(), any()) }
    }

    @Test
    fun testManualEventAlarmBroadcastReceiverWithNullIntent() {
        // Given
        val receiver = ManualEventAlarmBroadcastReceiver()

        // When - null intent
        receiver.onReceive(context, null)

        // Then - should not call CalendarMonitor
        verify(exactly = 0) { mockCalendarMonitor.onAlarmBroadcast(any(), any()) }
    }

    // === ManualEventExactAlarmBroadcastReceiver tests ===

    @Test
    fun testManualEventExactAlarmBroadcastReceiverCallsCalendarMonitor() {
        // Given
        val receiver = ManualEventExactAlarmBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then
        verify(exactly = 1) { mockCalendarMonitor.onAlarmBroadcast(context, intent) }
    }

    // === ManualEventAlarmPeriodicRescanBroadcastReceiver tests ===

    @Test
    fun testManualEventAlarmPeriodicRescanBroadcastReceiverCallsCalendarMonitor() {
        // Given
        val receiver = ManualEventAlarmPeriodicRescanBroadcastReceiver()
        val intent = Intent()

        // When
        receiver.onReceive(context, intent)

        // Then
        verify(exactly = 1) { mockCalendarMonitor.onPeriodicRescanBroadcast(context, intent) }
    }

    @Test
    fun testManualEventAlarmPeriodicRescanBroadcastReceiverWithNullContext() {
        // Given
        val receiver = ManualEventAlarmPeriodicRescanBroadcastReceiver()
        val intent = Intent()

        // When - null context
        receiver.onReceive(null, intent)

        // Then - should not call CalendarMonitor
        verify(exactly = 0) { mockCalendarMonitor.onPeriodicRescanBroadcast(any(), any()) }
    }

    // === RescheduleConfirmationsBroadcastReceiver tests ===

    @Test
    fun testRescheduleConfirmationsBroadcastReceiverCallsApplicationController() {
        // Given
        val receiver = RescheduleConfirmationsBroadcastReceiver()
        val intent = Intent().apply {
            putExtra("reschedule_confirmations", "test_value")
        }

        // When
        receiver.onReceive(context, intent)

        // Then
        verify(exactly = 1) { ApplicationController.onReceivedRescheduleConfirmations(context, "test_value") }
    }

    @Test
    fun testRescheduleConfirmationsBroadcastReceiverWithNullContext() {
        // Given
        val receiver = RescheduleConfirmationsBroadcastReceiver()
        val intent = Intent().apply {
            putExtra("reschedule_confirmations", "test_value")
        }

        // When - null context
        receiver.onReceive(null, intent)

        // Then - should not call ApplicationController
        verify(exactly = 0) { ApplicationController.onReceivedRescheduleConfirmations(any(), any()) }
    }

    @Test
    fun testRescheduleConfirmationsBroadcastReceiverWithNullIntent() {
        // Given
        val receiver = RescheduleConfirmationsBroadcastReceiver()

        // When - null intent
        receiver.onReceive(context, null)

        // Then - should not call ApplicationController
        verify(exactly = 0) { ApplicationController.onReceivedRescheduleConfirmations(any(), any()) }
    }

    @Test
    fun testRescheduleConfirmationsBroadcastReceiverWithMissingExtra() {
        // Given
        val receiver = RescheduleConfirmationsBroadcastReceiver()
        val intent = Intent() // No extra

        // When
        receiver.onReceive(context, intent)

        // Then - should not call ApplicationController (value is null)
        verify(exactly = 0) { ApplicationController.onReceivedRescheduleConfirmations(any(), any()) }
    }
}

