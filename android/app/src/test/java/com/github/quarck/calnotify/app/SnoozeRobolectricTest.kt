package com.github.quarck.calnotify.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.notification.EventNotificationManagerInterface
import com.github.quarck.calnotify.quiethours.QuietHoursManagerInterface
import com.github.quarck.calnotify.utils.CNPlusTestClock
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for snooze functionality in ApplicationController
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class SnoozeRobolectricTest {

    private lateinit var context: Context
    private lateinit var testClock: CNPlusTestClock
    private lateinit var mockNotificationManager: EventNotificationManagerInterface
    private lateinit var mockAlarmScheduler: AlarmSchedulerInterface
    private lateinit var mockQuietHoursManager: QuietHoursManagerInterface

    private val baseTime = 1635724800000L // 2021-11-01 00:00:00 UTC

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testClock = CNPlusTestClock(baseTime)

        // Create mocks
        mockNotificationManager = mockk(relaxed = true)
        mockAlarmScheduler = mockk(relaxed = true)
        mockQuietHoursManager = mockk(relaxed = true)

        // Mock QuietHoursManager to return 0 (no silent period)
        every { mockQuietHoursManager.getSilentUntil(any(), any<Long>()) } returns 0L

        // Mock ApplicationController
        mockkObject(ApplicationController)
        every { ApplicationController.clock } returns testClock
        every { ApplicationController.notificationManager } returns mockNotificationManager
        every { ApplicationController.alarmScheduler } returns mockAlarmScheduler
        every { ApplicationController.getQuietHoursManager(any()) } returns mockQuietHoursManager
        every { ApplicationController.getSettings(any()) } answers { Settings(firstArg()) }
    }

    @After
    fun cleanup() {
        unmockkAll()
        // Clean up any events from storage
        try {
            EventsStorage(context).use { db ->
                db.events.forEach { event ->
                    db.deleteEvent(event)
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    private fun createTestEvent(
        eventId: Long = 1L,
        title: String = "Test Event",
        snoozedUntil: Long = 0L,
        displayStatus: EventDisplayStatus = EventDisplayStatus.Hidden
    ): EventAlertRecord {
        val currentTime = testClock.currentTimeMillis()
        return EventAlertRecord(
            calendarId = 1L,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = currentTime,
            notificationId = eventId.toInt(),
            title = title,
            desc = "Test Description",
            startTime = currentTime + 3600000,
            endTime = currentTime + 7200000,
            instanceStartTime = currentTime + 3600000,
            instanceEndTime = currentTime + 7200000,
            location = "",
            lastStatusChangeTime = currentTime,
            snoozedUntil = snoozedUntil,
            displayStatus = displayStatus,
            color = 0,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = currentTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
    }

    private fun addEventToStorage(event: EventAlertRecord) {
        EventsStorage(context).use { db ->
            db.addEvent(event)
        }
    }

    // === snoozeEvent tests ===

    @Test
    fun testSnoozeEventBasic() {
        // Given
        val event = createTestEvent(eventId = 100L)
        addEventToStorage(event)

        val snoozeDelay = 15 * 60 * 1000L // 15 minutes

        // When
        val result = ApplicationController.snoozeEvent(
            context,
            event.eventId,
            event.instanceStartTime,
            snoozeDelay
        )

        // Then
        assertNotNull("Snooze result should not be null", result)
        assertEquals(SnoozeType.Snoozed, result?.type)

        // Verify the event was updated in storage
        EventsStorage(context).use { db ->
            val updatedEvent = db.getEvent(event.eventId, event.instanceStartTime)
            assertNotNull("Event should still exist", updatedEvent)
            assertTrue("snoozedUntil should be set",
                updatedEvent!!.snoozedUntil >= baseTime + snoozeDelay)
        }

        // Verify notification was updated
        verify { mockNotificationManager.onEventSnoozed(any(), any(), event.eventId, any()) }

        // Verify alarms were rescheduled
        verify { mockAlarmScheduler.rescheduleAlarms(any(), any(), any()) }
    }

    @Test
    fun testSnoozeEventNonExistent() {
        // When - try to snooze a non-existent event
        val result = ApplicationController.snoozeEvent(
            context,
            999L, // Non-existent event ID
            baseTime,
            15 * 60 * 1000L
        )

        // Then
        assertNull("Result should be null for non-existent event", result)
    }

    @Test
    fun testSnoozeEventPastTimeFallback() {
        // Given
        val event = createTestEvent(eventId = 101L)
        addEventToStorage(event)

        // Advance time
        testClock.advanceBy(60 * 60 * 1000L) // 1 hour

        // Snooze with a small negative delay that would result in past time
        val snoozeDelay = -2 * 60 * 60 * 1000L // -2 hours (way in the past)

        // When
        val result = ApplicationController.snoozeEvent(
            context,
            event.eventId,
            event.instanceStartTime,
            snoozeDelay
        )

        // Then - should use FAILBACK_SHORT_SNOOZE instead
        assertNotNull("Snooze result should not be null", result)

        EventsStorage(context).use { db ->
            val updatedEvent = db.getEvent(event.eventId, event.instanceStartTime)
            assertNotNull("Event should still exist", updatedEvent)
            // The snoozedUntil should be at least current time (not in the past)
            assertTrue("snoozedUntil should be >= current time",
                updatedEvent!!.snoozedUntil >= testClock.currentTimeMillis())
        }
    }

    // === snoozeAllEvents tests ===

    @Test
    fun testSnoozeAllEventsBasic() {
        // Given - add multiple events
        val event1 = createTestEvent(eventId = 200L, title = "Event 1")
        val event2 = createTestEvent(eventId = 201L, title = "Event 2")
        addEventToStorage(event1)
        addEventToStorage(event2)

        val snoozeDelay = 30 * 60 * 1000L // 30 minutes

        // When
        val result = ApplicationController.snoozeAllEvents(
            context,
            snoozeDelay,
            isChange = false,
            onlySnoozeVisible = false
        )

        // Then
        assertNotNull("Snooze result should not be null", result)
        assertEquals(SnoozeType.Snoozed, result?.type)

        // Verify both events were snoozed
        EventsStorage(context).use { db ->
            val updated1 = db.getEvent(event1.eventId, event1.instanceStartTime)
            val updated2 = db.getEvent(event2.eventId, event2.instanceStartTime)

            assertNotNull(updated1)
            assertNotNull(updated2)
            assertTrue("Event 1 should be snoozed", updated1!!.snoozedUntil > 0)
            assertTrue("Event 2 should be snoozed", updated2!!.snoozedUntil > 0)
        }
    }

    @Test
    fun testSnoozeAllEventsWithSearchQuery() {
        // Given - add events with different titles
        val matchingEvent = createTestEvent(eventId = 300L, title = "Important Meeting")
        val nonMatchingEvent = createTestEvent(eventId = 301L, title = "Lunch Break")
        addEventToStorage(matchingEvent)
        addEventToStorage(nonMatchingEvent)

        val snoozeDelay = 30 * 60 * 1000L

        // When - snooze only events matching "Important"
        val result = ApplicationController.snoozeAllEvents(
            context,
            snoozeDelay,
            isChange = false,
            onlySnoozeVisible = false,
            searchQuery = "Important"
        )

        // Then
        assertNotNull("Snooze result should not be null", result)

        EventsStorage(context).use { db ->
            val updatedMatching = db.getEvent(matchingEvent.eventId, matchingEvent.instanceStartTime)
            val updatedNonMatching = db.getEvent(nonMatchingEvent.eventId, nonMatchingEvent.instanceStartTime)

            assertNotNull(updatedMatching)
            assertNotNull(updatedNonMatching)
            assertTrue("Matching event should be snoozed", updatedMatching!!.snoozedUntil > 0)
            assertEquals("Non-matching event should NOT be snoozed", 0L, updatedNonMatching!!.snoozedUntil)
        }
    }

    @Test
    fun testSnoozeAllEventsEmpty() {
        // Given - no events in storage

        // When
        val result = ApplicationController.snoozeAllEvents(
            context,
            30 * 60 * 1000L,
            isChange = false,
            onlySnoozeVisible = false
        )

        // Then
        assertNull("Result should be null when no events to snooze", result)
    }

    // === snoozeAllCollapsedEvents tests ===

    @Test
    fun testSnoozeAllCollapsedEventsOnly() {
        // Given - events with different display statuses
        val collapsedEvent = createTestEvent(
            eventId = 400L,
            displayStatus = EventDisplayStatus.DisplayedCollapsed
        )
        val normalEvent = createTestEvent(
            eventId = 401L,
            displayStatus = EventDisplayStatus.DisplayedNormal
        )
        addEventToStorage(collapsedEvent)
        addEventToStorage(normalEvent)

        val snoozeDelay = 30 * 60 * 1000L

        // When
        val result = ApplicationController.snoozeAllCollapsedEvents(
            context,
            snoozeDelay,
            isChange = false,
            onlySnoozeVisible = false
        )

        // Then
        assertNotNull("Snooze result should not be null", result)

        EventsStorage(context).use { db ->
            val updatedCollapsed = db.getEvent(collapsedEvent.eventId, collapsedEvent.instanceStartTime)
            val updatedNormal = db.getEvent(normalEvent.eventId, normalEvent.instanceStartTime)

            assertNotNull(updatedCollapsed)
            assertNotNull(updatedNormal)
            assertTrue("Collapsed event should be snoozed", updatedCollapsed!!.snoozedUntil > 0)
            assertEquals("Normal event should NOT be snoozed", 0L, updatedNormal!!.snoozedUntil)
        }
    }

    // === onlySnoozeVisible tests ===

    @Test
    fun testSnoozeOnlyVisibleEvents() {
        // Given - one already snoozed, one not snoozed
        val alreadySnoozed = createTestEvent(
            eventId = 500L,
            snoozedUntil = baseTime + 60 * 60 * 1000L // Already snoozed for 1 hour
        )
        val notSnoozed = createTestEvent(eventId = 501L, snoozedUntil = 0L)
        addEventToStorage(alreadySnoozed)
        addEventToStorage(notSnoozed)

        val snoozeDelay = 30 * 60 * 1000L

        // When - onlySnoozeVisible = true means only snooze events that aren't already snoozed
        val result = ApplicationController.snoozeAllEvents(
            context,
            snoozeDelay,
            isChange = false,
            onlySnoozeVisible = true
        )

        // Then
        assertNotNull("Snooze result should not be null", result)

        EventsStorage(context).use { db ->
            val updatedAlready = db.getEvent(alreadySnoozed.eventId, alreadySnoozed.instanceStartTime)
            val updatedNot = db.getEvent(notSnoozed.eventId, notSnoozed.instanceStartTime)

            assertNotNull(updatedAlready)
            assertNotNull(updatedNot)
            // Already snoozed event should keep its original snooze time
            assertEquals("Already snoozed event should keep original snooze time",
                baseTime + 60 * 60 * 1000L, updatedAlready!!.snoozedUntil)
            // Not snoozed event should now be snoozed
            assertTrue("Not snoozed event should be snoozed", updatedNot!!.snoozedUntil > 0)
        }
    }
}

