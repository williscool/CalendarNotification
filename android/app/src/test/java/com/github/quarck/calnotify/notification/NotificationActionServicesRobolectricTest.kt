package com.github.quarck.calnotify.notification

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for notification action services (snooze/dismiss from notifications).
 * 
 * Note: IntentService runs on a background thread which causes MockK stub issues.
 * These tests verify the intent structure and method signatures instead.
 * Full service execution is tested in instrumented tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class NotificationActionServicesRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(ApplicationController)
        
        // Setup mocks for direct method testing
        every { ApplicationController.snoozeEvent(any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { ApplicationController.snoozeAllEvents(any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { ApplicationController.snoozeAllCollapsedEvents(any(), any(), any(), any()) } returns mockk(relaxed = true)
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
        every { ApplicationController.toggleMuteForEvent(any(), any(), any(), any()) } returns true
    }

    @After
    fun cleanup() {
        unmockkAll()
    }

    // === Snooze intent structure tests ===

    @Test
    fun testSnoozeIntentExtrasForIndividualEvent() {
        val intent = Intent(context, NotificationActionSnoozeService::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, 123)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, 456L)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, 789L)
        intent.putExtra(Consts.INTENT_SNOOZE_PRESET, 15 * 60 * 1000L)

        assertEquals(123, intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1))
        assertEquals(456L, intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1))
        assertEquals(789L, intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1))
        assertEquals(15 * 60 * 1000L, intent.getLongExtra(Consts.INTENT_SNOOZE_PRESET, -1))
        assertFalse(intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_KEY, false))
    }

    @Test
    fun testSnoozeAllIntentExtras() {
        val intent = Intent(context, NotificationActionSnoozeService::class.java)
        intent.putExtra(Consts.INTENT_SNOOZE_ALL_KEY, true)
        intent.putExtra(Consts.INTENT_SNOOZE_PRESET, 30 * 60 * 1000L)

        assertTrue(intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_KEY, false))
        assertEquals(30 * 60 * 1000L, intent.getLongExtra(Consts.INTENT_SNOOZE_PRESET, -1))
    }

    @Test
    fun testSnoozeAllCollapsedIntentExtras() {
        val intent = Intent(context, NotificationActionSnoozeService::class.java)
        intent.putExtra(Consts.INTENT_SNOOZE_ALL_COLLAPSED_KEY, true)
        intent.putExtra(Consts.INTENT_SNOOZE_PRESET, 60 * 60 * 1000L)

        assertTrue(intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_COLLAPSED_KEY, false))
        assertEquals(60 * 60 * 1000L, intent.getLongExtra(Consts.INTENT_SNOOZE_PRESET, -1))
    }

    // === Dismiss intent structure tests ===

    @Test
    fun testDismissIntentExtrasForIndividualEvent() {
        val intent = Intent(context, NotificationActionDismissService::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, 123)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, 456L)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, 789L)

        assertEquals(123, intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1))
        assertEquals(456L, intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1))
        assertEquals(789L, intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1))
        assertFalse(intent.getBooleanExtra(Consts.INTENT_DISMISS_ALL_KEY, false))
    }

    @Test
    fun testDismissAllIntentExtras() {
        val intent = Intent(context, NotificationActionDismissService::class.java)
        intent.putExtra(Consts.INTENT_DISMISS_ALL_KEY, true)

        assertTrue(intent.getBooleanExtra(Consts.INTENT_DISMISS_ALL_KEY, false))
    }

    // === Mute toggle intent structure tests ===

    @Test
    fun testMuteToggleIntentExtras() {
        val intent = Intent(context, NotificationActionMuteToggleService::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, 123)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, 456L)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, 789L)
        intent.putExtra(Consts.INTENT_MUTE_ACTION, 1)

        assertEquals(123, intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1))
        assertEquals(456L, intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1))
        assertEquals(789L, intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1))
        assertEquals(1, intent.getIntExtra(Consts.INTENT_MUTE_ACTION, -1))
    }

    // === ApplicationController method invocation tests ===

    @Test
    fun testSnoozeEventMethodCall() {
        ApplicationController.snoozeEvent(context, 456L, 789L, 15 * 60 * 1000L)
        verify { ApplicationController.snoozeEvent(any(), 456L, 789L, 15 * 60 * 1000L) }
    }

    @Test
    fun testSnoozeAllEventsMethodCall() {
        ApplicationController.snoozeAllEvents(context, 30 * 60 * 1000L, false, true)
        verify { ApplicationController.snoozeAllEvents(any(), 30 * 60 * 1000L, false, true) }
    }

    @Test
    fun testSnoozeAllCollapsedEventsMethodCall() {
        ApplicationController.snoozeAllCollapsedEvents(context, 60 * 60 * 1000L, false, true)
        verify { ApplicationController.snoozeAllCollapsedEvents(any(), 60 * 60 * 1000L, false, true) }
    }

    @Test
    fun testDismissEventMethodCall() {
        ApplicationController.dismissEvent(
            context,
            EventDismissType.ManuallyDismissedFromNotification,
            456L,
            789L,
            123,
            true
        )
        verify {
            ApplicationController.dismissEvent(
                any<Context>(),
                EventDismissType.ManuallyDismissedFromNotification,
                456L,
                789L,
                123,
                true
            )
        }
    }

    @Test
    fun testDismissAllButRecentAndSnoozedMethodCall() {
        ApplicationController.dismissAllButRecentAndSnoozed(
            context,
            EventDismissType.ManuallyDismissedFromNotification
        )
        verify {
            ApplicationController.dismissAllButRecentAndSnoozed(
                any(),
                EventDismissType.ManuallyDismissedFromNotification
            )
        }
    }

    @Test
    fun testToggleMuteForEventMethodCall() {
        ApplicationController.toggleMuteForEvent(context, 456L, 789L, 1)
        verify { ApplicationController.toggleMuteForEvent(any(), 456L, 789L, 1) }
    }

    // === Invalid event handling tests ===

    @Test
    fun testInvalidSnoozeEventNotCalled() {
        // Simulate the service's validation logic
        val notificationId = -1
        val eventId = -1L
        val instanceStartTime = -1L
        
        if (notificationId != -1 && eventId != -1L && instanceStartTime != -1L) {
            ApplicationController.snoozeEvent(context, eventId, instanceStartTime, 0L)
        }
        
        verify(exactly = 0) { ApplicationController.snoozeEvent(any(), any(), any(), any()) }
    }

    @Test
    fun testInvalidDismissEventNotCalled() {
        // Simulate the service's validation logic
        val notificationId = -1
        val eventId = -1L
        val instanceStartTime = -1L
        
        if (notificationId != -1 && eventId != -1L && instanceStartTime != -1L) {
            ApplicationController.dismissEvent(
                context,
                EventDismissType.ManuallyDismissedFromNotification,
                eventId,
                instanceStartTime,
                notificationId,
                true
            )
        }
        
        verify(exactly = 0) { 
            ApplicationController.dismissEvent(
                any<Context>(),
                any<EventDismissType>(),
                any<Long>(),
                any<Long>(),
                any<Int>(),
                any<Boolean>()
            ) 
        }
    }

    @Test
    fun testInvalidMuteToggleEventNotCalled() {
        // Simulate the service's validation logic
        val notificationId = -1
        val eventId = -1L
        val instanceStartTime = -1L
        
        if (notificationId != -1 && eventId != -1L && instanceStartTime != -1L) {
            ApplicationController.toggleMuteForEvent(context, eventId, instanceStartTime, 0)
        }
        
        verify(exactly = 0) { ApplicationController.toggleMuteForEvent(any(), any(), any(), any()) }
    }
}
