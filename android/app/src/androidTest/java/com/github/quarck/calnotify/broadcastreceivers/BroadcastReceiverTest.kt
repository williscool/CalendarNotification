package com.github.quarck.calnotify.broadcastreceivers

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.app.ApplicationController
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

    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up BroadcastReceiverTest")
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // Mock ApplicationController to verify calls
        mockkObject(ApplicationController)
        every { ApplicationController.onBootComplete(any()) } just Runs
        every { ApplicationController.onEventAlarm(any()) } just Runs
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
}

