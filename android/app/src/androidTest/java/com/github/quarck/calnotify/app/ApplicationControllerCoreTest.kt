package com.github.quarck.calnotify.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.TestTimeConstants
import com.github.quarck.calnotify.utils.CNPlusTestClock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for core ApplicationController methods:
 * - hasActiveEventsToRemind
 * - toggleMuteForEvent
 * - dismissAllButRecentAndSnoozed
 * - muteAllVisibleEvents
 */
@RunWith(AndroidJUnit4::class)
class ApplicationControllerCoreTest {

    private val LOG_TAG = "ApplicationControllerCoreTest"
    private lateinit var context: Context
    private val baseTime = TestTimeConstants.STANDARD_TEST_TIME
    private lateinit var testClock: CNPlusTestClock

    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up test")
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Set up test clock to use the same time as our test events
        testClock = CNPlusTestClock(baseTime)
        ApplicationController.clockProvider = { testClock }
        
        // Clear any existing test events
        EventsStorage(context).use { db ->
            db.events.filter { it.title.startsWith("Test Event") }.forEach {
                db.deleteEvent(it.eventId, it.instanceStartTime)
            }
        }
    }

    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test")
        // Reset the clock provider to avoid test pollution
        ApplicationController.resetClockProvider()
        
        // Clean up test events
        EventsStorage(context).use { db ->
            db.events.filter { it.title.startsWith("Test Event") }.forEach {
                db.deleteEvent(it.eventId, it.instanceStartTime)
            }
        }
    }

    private fun createTestEvent(
        eventId: Long,
        instanceStartTime: Long = baseTime,
        snoozedUntil: Long = 0L,
        isMuted: Boolean = false,
        isTask: Boolean = false,
        lastStatusChangeTime: Long = baseTime
    ): EventAlertRecord {
        val event = EventAlertRecord(
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
            displayStatus = EventDisplayStatus.Hidden,
            color = 0,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = baseTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None
        )
        // isMuted and isTask are computed from flags, set via property setters
        event.isMuted = isMuted
        event.isTask = isTask
        return event
    }

    // === hasActiveEventsToRemind tests ===

    @Test
    fun testHasActiveEventsToRemind_withActiveEvent() {
        DevLog.info(LOG_TAG, "Running testHasActiveEventsToRemind_withActiveEvent")
        
        val event = createTestEvent(eventId = 100001L)
        EventsStorage(context).use { db ->
            db.addEvent(event)
        }

        val result = ApplicationController.hasActiveEventsToRemind(context)

        assertTrue("Should have active events to remind", result)
    }

    @Test
    fun testHasActiveEventsToRemind_withMutedEvent() {
        DevLog.info(LOG_TAG, "Running testHasActiveEventsToRemind_withMutedEvent")
        
        val event = createTestEvent(eventId = 100002L, isMuted = true)
        EventsStorage(context).use { db ->
            db.addEvent(event)
        }

        // Need to check if there are OTHER active events
        val hasOtherActive = EventsStorage(context).use { db ->
            db.events.any { it.eventId != 100002L && it.snoozedUntil == 0L && !it.isMuted && !it.isTask }
        }
        
        val result = ApplicationController.hasActiveEventsToRemind(context)
        
        if (!hasOtherActive) {
            assertFalse("Should not remind for only muted events", result)
        }
    }

    @Test
    fun testHasActiveEventsToRemind_withSnoozedEvent() {
        DevLog.info(LOG_TAG, "Running testHasActiveEventsToRemind_withSnoozedEvent")
        
        val event = createTestEvent(eventId = 100003L, snoozedUntil = baseTime + 3600000L)
        EventsStorage(context).use { db ->
            db.addEvent(event)
        }

        val hasOtherActive = EventsStorage(context).use { db ->
            db.events.any { it.eventId != 100003L && it.snoozedUntil == 0L && !it.isMuted && !it.isTask }
        }
        
        val result = ApplicationController.hasActiveEventsToRemind(context)
        
        if (!hasOtherActive) {
            assertFalse("Should not remind for only snoozed events", result)
        }
    }

    // === toggleMuteForEvent tests ===

    @Test
    fun testToggleMuteForEvent_muteEvent() {
        DevLog.info(LOG_TAG, "Running testToggleMuteForEvent_muteEvent")
        
        val eventId = 100010L
        val event = createTestEvent(eventId = eventId, isMuted = false)
        EventsStorage(context).use { db ->
            db.addEvent(event)
        }

        // muteAction = 0 means mute the event
        val result = ApplicationController.toggleMuteForEvent(context, eventId, baseTime, 0)

        assertTrue("Should return true on success", result)
        
        val updatedEvent = EventsStorage(context).use { db ->
            db.getEvent(eventId, baseTime)
        }
        assertTrue("Event should be muted", updatedEvent?.isMuted == true)
    }

    @Test
    fun testToggleMuteForEvent_unmuteEvent() {
        DevLog.info(LOG_TAG, "Running testToggleMuteForEvent_unmuteEvent")
        
        val eventId = 100011L
        val event = createTestEvent(eventId = eventId, isMuted = true)
        EventsStorage(context).use { db ->
            db.addEvent(event)
        }

        // muteAction = 1 means unmute the event
        val result = ApplicationController.toggleMuteForEvent(context, eventId, baseTime, 1)

        assertTrue("Should return true on success", result)
        
        val updatedEvent = EventsStorage(context).use { db ->
            db.getEvent(eventId, baseTime)
        }
        assertFalse("Event should be unmuted", updatedEvent?.isMuted == true)
    }

    @Test
    fun testToggleMuteForEvent_nonexistentEvent() {
        DevLog.info(LOG_TAG, "Running testToggleMuteForEvent_nonexistentEvent")
        
        val result = ApplicationController.toggleMuteForEvent(context, 999999L, baseTime, 0)

        assertFalse("Should return false for non-existent event", result)
    }

    // === dismissAllButRecentAndSnoozed tests ===

    @Test
    fun testDismissAllButRecentAndSnoozed_dismissesOldEvents() {
        DevLog.info(LOG_TAG, "Running testDismissAllButRecentAndSnoozed_dismissesOldEvents")
        
        val eventId = 100020L
        // Old event (beyond threshold)
        val oldEvent = createTestEvent(
            eventId = eventId,
            lastStatusChangeTime = baseTime - Consts.DISMISS_ALL_THRESHOLD - 60000L
        )
        EventsStorage(context).use { db ->
            db.addEvent(oldEvent)
        }

        ApplicationController.dismissAllButRecentAndSnoozed(context, EventDismissType.ManuallyDismissedFromNotification)

        val remainingEvent = EventsStorage(context).use { db ->
            db.getEvent(eventId, baseTime)
        }
        assertNull("Old event should be dismissed", remainingEvent)
    }

    @Test
    fun testDismissAllButRecentAndSnoozed_keepsRecentEvents() {
        DevLog.info(LOG_TAG, "Running testDismissAllButRecentAndSnoozed_keepsRecentEvents")
        
        val eventId = 100021L
        // Recent event
        val recentEvent = createTestEvent(
            eventId = eventId,
            lastStatusChangeTime = baseTime // Just now (relative to baseTime)
        )
        EventsStorage(context).use { db ->
            db.addEvent(recentEvent)
        }

        ApplicationController.dismissAllButRecentAndSnoozed(context, EventDismissType.ManuallyDismissedFromNotification)

        val remainingEvent = EventsStorage(context).use { db ->
            db.getEvent(eventId, baseTime)
        }
        assertNotNull("Recent event should be kept", remainingEvent)
    }

    @Test
    fun testDismissAllButRecentAndSnoozed_keepsSnoozedEvents() {
        DevLog.info(LOG_TAG, "Running testDismissAllButRecentAndSnoozed_keepsSnoozedEvents")
        
        val eventId = 100022L
        // Old but snoozed event
        val snoozedEvent = createTestEvent(
            eventId = eventId,
            lastStatusChangeTime = baseTime - Consts.DISMISS_ALL_THRESHOLD - 60000L,
            snoozedUntil = baseTime + 3600000L
        )
        EventsStorage(context).use { db ->
            db.addEvent(snoozedEvent)
        }

        ApplicationController.dismissAllButRecentAndSnoozed(context, EventDismissType.ManuallyDismissedFromNotification)

        val remainingEvent = EventsStorage(context).use { db ->
            db.getEvent(eventId, baseTime)
        }
        assertNotNull("Snoozed event should be kept", remainingEvent)
    }

    // === muteAllVisibleEvents tests ===

    @Test
    fun testMuteAllVisibleEvents_mutesVisibleEvents() {
        DevLog.info(LOG_TAG, "Running testMuteAllVisibleEvents_mutesVisibleEvents")
        
        val eventId = 100030L
        val event = createTestEvent(eventId = eventId, isMuted = false)
        EventsStorage(context).use { db ->
            db.addEvent(event)
        }

        ApplicationController.muteAllVisibleEvents(context)

        val updatedEvent = EventsStorage(context).use { db ->
            db.getEvent(eventId, baseTime)
        }
        assertTrue("Visible event should be muted", updatedEvent?.isMuted == true)
    }

    @Test
    fun testMuteAllVisibleEvents_skipsSnoozedEvents() {
        DevLog.info(LOG_TAG, "Running testMuteAllVisibleEvents_skipsSnoozedEvents")
        
        val eventId = 100031L
        val event = createTestEvent(
            eventId = eventId,
            snoozedUntil = baseTime + 3600000L,
            isMuted = false
        )
        EventsStorage(context).use { db ->
            db.addEvent(event)
        }

        ApplicationController.muteAllVisibleEvents(context)

        val updatedEvent = EventsStorage(context).use { db ->
            db.getEvent(eventId, baseTime)
        }
        assertFalse("Snoozed event should NOT be muted", updatedEvent?.isMuted == true)
    }

    @Test
    fun testMuteAllVisibleEvents_skipsTaskEvents() {
        DevLog.info(LOG_TAG, "Running testMuteAllVisibleEvents_skipsTaskEvents")
        
        val eventId = 100032L
        val event = createTestEvent(eventId = eventId, isTask = true, isMuted = false)
        EventsStorage(context).use { db ->
            db.addEvent(event)
        }

        ApplicationController.muteAllVisibleEvents(context)

        val updatedEvent = EventsStorage(context).use { db ->
            db.getEvent(eventId, baseTime)
        }
        assertFalse("Task event should NOT be muted", updatedEvent?.isMuted == true)
    }
}

