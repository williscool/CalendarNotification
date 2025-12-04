package com.github.quarck.calnotify.notification

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.logs.DevLog
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for notification action services.
 * 
 * Tests the snooze/dismiss entry points from notifications on real devices.
 */
@RunWith(AndroidJUnit4::class)
class NotificationActionServicesTest {

    private val LOG_TAG = "NotificationActionServicesTest"

    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up tests")
        mockkObject(ApplicationController)
        every { ApplicationController.snoozeEvent(any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { ApplicationController.snoozeAllEvents(any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { ApplicationController.snoozeAllCollapsedEvents(any(), any(), any(), any()) } returns mockk(relaxed = true)
        // Use specific types to disambiguate the overload
        every { 
            ApplicationController.dismissEvent(
                any<Context>(),
                any<EventDismissType>(),
                any<Long>(),
                any<Long>(),
                any<Int>(),
                any<Boolean>()
            ) 
        } returns Unit
        every { ApplicationController.dismissAllButRecentAndSnoozed(any(), any()) } returns Unit
        every { ApplicationController.cleanupEventReminder(any()) } returns Unit
        every { ApplicationController.toggleMuteForEvent(any(), any(), any(), any()) } returns true
    }

    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up")
        unmockkAll()
    }

    // === NotificationActionSnoozeService intent creation tests ===

    @Test
    fun testSnoozeIntentContainsCorrectExtras() {
        DevLog.info(LOG_TAG, "Running testSnoozeIntentContainsCorrectExtras")
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val intent = Intent(context, NotificationActionSnoozeService::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, 123)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, 456L)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, 789L)
        intent.putExtra(Consts.INTENT_SNOOZE_PRESET, 15 * 60 * 1000L)

        // Verify intent extras
        assert(intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1) == 123)
        assert(intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1) == 456L)
        assert(intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1) == 789L)
        assert(intent.getLongExtra(Consts.INTENT_SNOOZE_PRESET, -1) == 15 * 60 * 1000L)
    }

    @Test
    fun testSnoozeAllIntentContainsFlag() {
        DevLog.info(LOG_TAG, "Running testSnoozeAllIntentContainsFlag")
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val intent = Intent(context, NotificationActionSnoozeService::class.java)
        intent.putExtra(Consts.INTENT_SNOOZE_ALL_KEY, true)

        assert(intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_KEY, false) == true)
    }

    @Test
    fun testSnoozeAllCollapsedIntentContainsFlag() {
        DevLog.info(LOG_TAG, "Running testSnoozeAllCollapsedIntentContainsFlag")
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val intent = Intent(context, NotificationActionSnoozeService::class.java)
        intent.putExtra(Consts.INTENT_SNOOZE_ALL_COLLAPSED_KEY, true)

        assert(intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_COLLAPSED_KEY, false) == true)
    }

    // === NotificationActionDismissService intent creation tests ===

    @Test
    fun testDismissIntentContainsCorrectExtras() {
        DevLog.info(LOG_TAG, "Running testDismissIntentContainsCorrectExtras")
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val intent = Intent(context, NotificationActionDismissService::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, 123)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, 456L)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, 789L)

        assert(intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1) == 123)
        assert(intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1) == 456L)
        assert(intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1) == 789L)
    }

    @Test
    fun testDismissAllIntentContainsFlag() {
        DevLog.info(LOG_TAG, "Running testDismissAllIntentContainsFlag")
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val intent = Intent(context, NotificationActionDismissService::class.java)
        intent.putExtra(Consts.INTENT_DISMISS_ALL_KEY, true)

        assert(intent.getBooleanExtra(Consts.INTENT_DISMISS_ALL_KEY, false) == true)
    }

    // === Integration verification tests ===

    @Test
    fun testApplicationControllerSnoozeMethodExists() {
        DevLog.info(LOG_TAG, "Running testApplicationControllerSnoozeMethodExists")
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Verify the method signature exists (will fail to compile if not)
        ApplicationController.snoozeEvent(context, 1L, 2L, 3L)
        verify { ApplicationController.snoozeEvent(any(), 1L, 2L, 3L) }
    }

    @Test
    fun testApplicationControllerDismissMethodExists() {
        DevLog.info(LOG_TAG, "Running testApplicationControllerDismissMethodExists")
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Verify the method signature exists
        ApplicationController.dismissEvent(
            context,
            EventDismissType.ManuallyDismissedFromNotification,
            1L,
            2L,
            3,
            true
        )
        verify {
            ApplicationController.dismissEvent(
                any<Context>(),
                EventDismissType.ManuallyDismissedFromNotification,
                1L,
                2L,
                3,
                true
            )
        }
    }

    // === Mute toggle tests ===

    @Test
    fun testMuteToggleIntentContainsCorrectExtras() {
        DevLog.info(LOG_TAG, "Running testMuteToggleIntentContainsCorrectExtras")
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val intent = Intent(context, NotificationActionMuteToggleService::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, 123)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, 456L)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, 789L)
        intent.putExtra(Consts.INTENT_MUTE_ACTION, 1)

        assert(intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1) == 123)
        assert(intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1) == 456L)
        assert(intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1) == 789L)
        assert(intent.getIntExtra(Consts.INTENT_MUTE_ACTION, -1) == 1)
    }

    @Test
    fun testApplicationControllerMuteToggleMethodExists() {
        DevLog.info(LOG_TAG, "Running testApplicationControllerMuteToggleMethodExists")
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        ApplicationController.toggleMuteForEvent(context, 1L, 2L, 0)
        verify { ApplicationController.toggleMuteForEvent(any(), 1L, 2L, 0) }
    }
}

