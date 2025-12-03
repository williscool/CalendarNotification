package com.github.quarck.calnotify.notification

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for notification action services (snooze/dismiss from notifications).
 * 
 * These are thin wrappers around ApplicationController but are key entry points
 * for user actions from notifications.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class NotificationActionServicesRobolectricTest {

    @Before
    fun setup() {
        mockkObject(ApplicationController)
        every { ApplicationController.snoozeEvent(any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { ApplicationController.snoozeAllEvents(any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { ApplicationController.snoozeAllCollapsedEvents(any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { ApplicationController.dismissEvent(any(), any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { ApplicationController.dismissAllButRecentAndSnoozed(any(), any()) } returns Unit
        every { ApplicationController.cleanupEventReminder(any()) } returns Unit
        every { ApplicationController.toggleMuteForEvent(any(), any(), any(), any()) } returns true
    }

    @After
    fun cleanup() {
        unmockkAll()
    }

    // === NotificationActionSnoozeService tests ===

    @Test
    fun testSnoozeServiceSnoozesIndividualEvent() {
        val intent = Intent()
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, 123)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, 456L)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, 789L)
        intent.putExtra(Consts.INTENT_SNOOZE_PRESET, 15 * 60 * 1000L)

        val service = Robolectric.buildService(NotificationActionSnoozeService::class.java, intent)
            .create()
            .startCommand(0, 0)
            .get()

        verify { ApplicationController.snoozeEvent(any(), 456L, 789L, 15 * 60 * 1000L) }
    }

    @Test
    fun testSnoozeServiceSnoozesAllEvents() {
        val intent = Intent()
        intent.putExtra(Consts.INTENT_SNOOZE_ALL_KEY, true)
        intent.putExtra(Consts.INTENT_SNOOZE_PRESET, 30 * 60 * 1000L)

        Robolectric.buildService(NotificationActionSnoozeService::class.java, intent)
            .create()
            .startCommand(0, 0)

        verify { ApplicationController.snoozeAllEvents(any(), 30 * 60 * 1000L, false, true) }
    }

    @Test
    fun testSnoozeServiceSnoozesAllCollapsedEvents() {
        val intent = Intent()
        intent.putExtra(Consts.INTENT_SNOOZE_ALL_COLLAPSED_KEY, true)
        intent.putExtra(Consts.INTENT_SNOOZE_PRESET, 60 * 60 * 1000L)

        Robolectric.buildService(NotificationActionSnoozeService::class.java, intent)
            .create()
            .startCommand(0, 0)

        verify { ApplicationController.snoozeAllCollapsedEvents(any(), 60 * 60 * 1000L, false, true) }
    }

    @Test
    fun testSnoozeServiceIgnoresInvalidEvent() {
        val intent = Intent()
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, -1L)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1L)

        Robolectric.buildService(NotificationActionSnoozeService::class.java, intent)
            .create()
            .startCommand(0, 0)

        verify(exactly = 0) { ApplicationController.snoozeEvent(any(), any(), any(), any()) }
    }

    @Test
    fun testSnoozeServiceCleansUpReminder() {
        val intent = Intent()
        intent.putExtra(Consts.INTENT_SNOOZE_ALL_KEY, true)

        Robolectric.buildService(NotificationActionSnoozeService::class.java, intent)
            .create()
            .startCommand(0, 0)

        verify { ApplicationController.cleanupEventReminder(any()) }
    }

    // === NotificationActionDismissService tests ===

    @Test
    fun testDismissServiceDismissesIndividualEvent() {
        val intent = Intent()
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, 123)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, 456L)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, 789L)

        Robolectric.buildService(NotificationActionDismissService::class.java, intent)
            .create()
            .startCommand(0, 0)

        verify {
            ApplicationController.dismissEvent(
                any(),
                EventDismissType.ManuallyDismissedFromNotification,
                456L,
                789L,
                123,
                true
            )
        }
    }

    @Test
    fun testDismissServiceDismissesAllEvents() {
        val intent = Intent()
        intent.putExtra(Consts.INTENT_DISMISS_ALL_KEY, true)

        Robolectric.buildService(NotificationActionDismissService::class.java, intent)
            .create()
            .startCommand(0, 0)

        verify {
            ApplicationController.dismissAllButRecentAndSnoozed(
                any(),
                EventDismissType.ManuallyDismissedFromNotification
            )
        }
    }

    @Test
    fun testDismissServiceIgnoresInvalidEvent() {
        val intent = Intent()
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, -1L)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1L)

        Robolectric.buildService(NotificationActionDismissService::class.java, intent)
            .create()
            .startCommand(0, 0)

        verify(exactly = 0) { ApplicationController.dismissEvent(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun testDismissServiceCleansUpReminder() {
        val intent = Intent()
        intent.putExtra(Consts.INTENT_DISMISS_ALL_KEY, true)

        Robolectric.buildService(NotificationActionDismissService::class.java, intent)
            .create()
            .startCommand(0, 0)

        verify { ApplicationController.cleanupEventReminder(any()) }
    }

    // === NotificationActionMuteToggleService tests ===

    @Test
    fun testMuteToggleServiceTogglesEvent() {
        val intent = Intent()
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, 123)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, 456L)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, 789L)
        intent.putExtra(Consts.INTENT_MUTE_ACTION, 1)

        Robolectric.buildService(NotificationActionMuteToggleService::class.java, intent)
            .create()
            .startCommand(0, 0)

        verify { ApplicationController.toggleMuteForEvent(any(), 456L, 789L, 1) }
    }

    @Test
    fun testMuteToggleServiceIgnoresInvalidEvent() {
        val intent = Intent()
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, -1L)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1L)

        Robolectric.buildService(NotificationActionMuteToggleService::class.java, intent)
            .create()
            .startCommand(0, 0)

        verify(exactly = 0) { ApplicationController.toggleMuteForEvent(any(), any(), any(), any()) }
    }
}

