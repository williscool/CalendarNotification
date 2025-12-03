package com.github.quarck.calnotify.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.notification.EventNotificationManagerInterface
import com.github.quarck.calnotify.testutils.MockEventsStorage
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
 * Tests for core ApplicationController methods:
 * - hasActiveEventsToRemind
 * - toggleMuteForEvent
 * - dismissAllButRecentAndSnoozed
 * - muteAllVisibleEvents
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class ApplicationControllerCoreRobolectricTest {

    private lateinit var context: Context
    private lateinit var testClock: CNPlusUnitTestClock
    private lateinit var mockEventsStorage: MockEventsStorage
    private lateinit var mockNotificationManager: EventNotificationManagerInterface
    private lateinit var mockAlarmScheduler: AlarmSchedulerInterface

    private val baseTime = 1635724800000L // 2021-11-01 00:00:00 UTC

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testClock = CNPlusUnitTestClock(baseTime)
        mockEventsStorage = MockEventsStorage()

        mockNotificationManager = mockk(relaxed = true)
        mockAlarmScheduler = mockk(relaxed = true)

        mockkObject(ApplicationController)
        every { ApplicationController.clock } returns testClock

        // Inject mock storage
        ApplicationController.eventsStorageProvider = { mockEventsStorage }
    }

    @After
    fun cleanup() {
        ApplicationController.eventsStorageProvider = null
        unmockkAll()
    }

    private fun createTestEvent(
        eventId: Long = 1L,
        instanceStartTime: Long = baseTime,
        snoozedUntil: Long = 0L,
        isMuted: Boolean = false,
        isTask: Boolean = false,
        lastStatusChangeTime: Long = baseTime
    ): EventAlertRecord {
        return EventAlertRecord(
            calendarId = 1L,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = baseTime,
            notificationId = eventId.toInt(),
            title = "Test Event $eventId",
            desc = "",
            startTime = instanceStartTime,
            endTime = instanceStartTime + 3600000L,
            instanceStartTime = instanceStartTime,
            instanceEndTime = instanceStartTime + 3600000L,
            location = "",
            lastStatusChangeTime = lastStatusChangeTime,
            snoozedUntil = snoozedUntil,
            displayStatus = com.github.quarck.calnotify.calendar.EventDisplayStatus.Hidden,
            color = 0,
            isTask = isTask,
            isMuted = isMuted,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = baseTime,
            eventStatus = com.github.quarck.calnotify.calendar.EventStatus.Confirmed,
            attendanceStatus = com.github.quarck.calnotify.calendar.AttendanceStatus.None
        )
    }

    // === hasActiveEventsToRemind tests ===

    @Test
    fun testHasActiveEventsToRemind_withActiveEvent() {
        val event = createTestEvent(snoozedUntil = 0L, isMuted = false, isTask = false)
        mockEventsStorage.addEvent(event)

        val result = ApplicationController.hasActiveEventsToRemind(context)

        assertTrue("Should have active events to remind", result)
    }

    @Test
    fun testHasActiveEventsToRemind_withNoEvents() {
        // No events added
        val result = ApplicationController.hasActiveEventsToRemind(context)

        assertFalse("Should not have active events when storage is empty", result)
    }

    @Test
    fun testHasActiveEventsToRemind_withSnoozedEvent() {
        val event = createTestEvent(snoozedUntil = baseTime + 3600000L) // Snoozed 1 hour
        mockEventsStorage.addEvent(event)

        val result = ApplicationController.hasActiveEventsToRemind(context)

        assertFalse("Should not remind for snoozed events", result)
    }

    @Test
    fun testHasActiveEventsToRemind_withMutedEvent() {
        val event = createTestEvent(isMuted = true)
        mockEventsStorage.addEvent(event)

        val result = ApplicationController.hasActiveEventsToRemind(context)

        assertFalse("Should not remind for muted events", result)
    }

    @Test
    fun testHasActiveEventsToRemind_withTaskEvent() {
        val event = createTestEvent(isTask = true)
        mockEventsStorage.addEvent(event)

        val result = ApplicationController.hasActiveEventsToRemind(context)

        assertFalse("Should not remind for task events", result)
    }

    @Test
    fun testHasActiveEventsToRemind_mixedEvents() {
        // Add snoozed, muted, task, and one active event
        mockEventsStorage.addEvent(createTestEvent(eventId = 1, snoozedUntil = baseTime + 3600000L))
        mockEventsStorage.addEvent(createTestEvent(eventId = 2, isMuted = true))
        mockEventsStorage.addEvent(createTestEvent(eventId = 3, isTask = true))
        mockEventsStorage.addEvent(createTestEvent(eventId = 4)) // Active event

        val result = ApplicationController.hasActiveEventsToRemind(context)

        assertTrue("Should have active events when at least one is active", result)
    }

    // === toggleMuteForEvent tests ===

    @Test
    fun testToggleMuteForEvent_muteEvent() {
        val event = createTestEvent(eventId = 1, isMuted = false)
        mockEventsStorage.addEvent(event)

        // muteAction = 0 means mute the event
        val result = ApplicationController.toggleMuteForEvent(context, 1L, baseTime, 0)

        assertTrue("Should return true on success", result)
        val updatedEvent = mockEventsStorage.getEvent(1L, baseTime)
        assertTrue("Event should be muted", updatedEvent?.isMuted == true)
    }

    @Test
    fun testToggleMuteForEvent_unmuteEvent() {
        val event = createTestEvent(eventId = 1, isMuted = true)
        mockEventsStorage.addEvent(event)

        // muteAction = 1 means unmute the event
        val result = ApplicationController.toggleMuteForEvent(context, 1L, baseTime, 1)

        assertTrue("Should return true on success", result)
        val updatedEvent = mockEventsStorage.getEvent(1L, baseTime)
        assertFalse("Event should be unmuted", updatedEvent?.isMuted == true)
    }

    @Test
    fun testToggleMuteForEvent_nonexistentEvent() {
        // No events added
        val result = ApplicationController.toggleMuteForEvent(context, 999L, baseTime, 0)

        assertFalse("Should return false for non-existent event", result)
    }

    // === dismissAllButRecentAndSnoozed tests ===

    @Test
    fun testDismissAllButRecentAndSnoozed_dismissesOldEvents() {
        // Old event (beyond threshold)
        val oldEvent = createTestEvent(
            eventId = 1,
            lastStatusChangeTime = baseTime - Consts.DISMISS_ALL_THRESHOLD - 1000L
        )
        mockEventsStorage.addEvent(oldEvent)

        ApplicationController.dismissAllButRecentAndSnoozed(context, EventDismissType.ManuallyDismissedFromNotification)

        val remainingEvents = mockEventsStorage.events
        assertTrue("Old event should be dismissed", remainingEvents.isEmpty())
    }

    @Test
    fun testDismissAllButRecentAndSnoozed_keepsRecentEvents() {
        // Recent event (within threshold)
        val recentEvent = createTestEvent(
            eventId = 1,
            lastStatusChangeTime = baseTime - 1000L // Just 1 second ago
        )
        mockEventsStorage.addEvent(recentEvent)

        ApplicationController.dismissAllButRecentAndSnoozed(context, EventDismissType.ManuallyDismissedFromNotification)

        val remainingEvents = mockEventsStorage.events
        assertEquals("Recent event should be kept", 1, remainingEvents.size)
    }

    @Test
    fun testDismissAllButRecentAndSnoozed_keepsSnoozedEvents() {
        // Snoozed old event
        val snoozedEvent = createTestEvent(
            eventId = 1,
            lastStatusChangeTime = baseTime - Consts.DISMISS_ALL_THRESHOLD - 1000L,
            snoozedUntil = baseTime + 3600000L
        )
        mockEventsStorage.addEvent(snoozedEvent)

        ApplicationController.dismissAllButRecentAndSnoozed(context, EventDismissType.ManuallyDismissedFromNotification)

        val remainingEvents = mockEventsStorage.events
        assertEquals("Snoozed event should be kept", 1, remainingEvents.size)
    }

    @Test
    fun testDismissAllButRecentAndSnoozed_mixedEvents() {
        // Old event to dismiss
        mockEventsStorage.addEvent(createTestEvent(
            eventId = 1,
            lastStatusChangeTime = baseTime - Consts.DISMISS_ALL_THRESHOLD - 1000L
        ))
        // Recent event to keep
        mockEventsStorage.addEvent(createTestEvent(
            eventId = 2,
            lastStatusChangeTime = baseTime - 1000L
        ))
        // Snoozed old event to keep
        mockEventsStorage.addEvent(createTestEvent(
            eventId = 3,
            lastStatusChangeTime = baseTime - Consts.DISMISS_ALL_THRESHOLD - 1000L,
            snoozedUntil = baseTime + 3600000L
        ))

        ApplicationController.dismissAllButRecentAndSnoozed(context, EventDismissType.ManuallyDismissedFromNotification)

        val remainingEvents = mockEventsStorage.events
        assertEquals("Should keep 2 events (recent and snoozed)", 2, remainingEvents.size)
        assertTrue("Should keep event 2", remainingEvents.any { it.eventId == 2L })
        assertTrue("Should keep event 3", remainingEvents.any { it.eventId == 3L })
    }

    // === muteAllVisibleEvents tests ===

    @Test
    fun testMuteAllVisibleEvents_mutesVisibleEvents() {
        mockEventsStorage.addEvent(createTestEvent(eventId = 1, isMuted = false))
        mockEventsStorage.addEvent(createTestEvent(eventId = 2, isMuted = false))

        ApplicationController.muteAllVisibleEvents(context)

        val events = mockEventsStorage.events
        assertTrue("All events should be muted", events.all { it.isMuted })
    }

    @Test
    fun testMuteAllVisibleEvents_skipsSnoozedEvents() {
        mockEventsStorage.addEvent(createTestEvent(eventId = 1, snoozedUntil = baseTime + 3600000L, isMuted = false))
        mockEventsStorage.addEvent(createTestEvent(eventId = 2, isMuted = false))

        ApplicationController.muteAllVisibleEvents(context)

        val events = mockEventsStorage.events
        val snoozedEvent = events.find { it.eventId == 1L }
        val visibleEvent = events.find { it.eventId == 2L }
        
        assertFalse("Snoozed event should NOT be muted", snoozedEvent?.isMuted == true)
        assertTrue("Visible event should be muted", visibleEvent?.isMuted == true)
    }

    @Test
    fun testMuteAllVisibleEvents_skipsTaskEvents() {
        mockEventsStorage.addEvent(createTestEvent(eventId = 1, isTask = true, isMuted = false))
        mockEventsStorage.addEvent(createTestEvent(eventId = 2, isMuted = false))

        ApplicationController.muteAllVisibleEvents(context)

        val events = mockEventsStorage.events
        val taskEvent = events.find { it.eventId == 1L }
        val normalEvent = events.find { it.eventId == 2L }
        
        assertFalse("Task event should NOT be muted", taskEvent?.isMuted == true)
        assertTrue("Normal event should be muted", normalEvent?.isMuted == true)
    }

    @Test
    fun testMuteAllVisibleEvents_noEventsToMute() {
        // Only snoozed and task events
        mockEventsStorage.addEvent(createTestEvent(eventId = 1, snoozedUntil = baseTime + 3600000L))
        mockEventsStorage.addEvent(createTestEvent(eventId = 2, isTask = true))

        // Should not throw, just do nothing
        ApplicationController.muteAllVisibleEvents(context)

        val events = mockEventsStorage.events
        assertFalse("No events should be muted", events.any { it.isMuted })
    }
}

