package com.github.quarck.calnotify.broadcastreceivers

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.app.ApplicationController
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

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Mock ApplicationController to verify calls
        mockkObject(ApplicationController)
        every { ApplicationController.onBootComplete(any()) } just Runs
        every { ApplicationController.onEventAlarm(any()) } just Runs
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
}

