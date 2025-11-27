package com.github.quarck.calnotify.dismissedeventsstorage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.MockTimeProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSQLiteConnection

/**
 * Robolectric tests for DismissedEventsStorage.
 *
 * These tests verify the actual SQLite storage operations for dismissed events
 * using Robolectric's in-memory database support.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class DismissedEventsStorageRobolectricTest {
    private val LOG_TAG = "DismissedStorageRoboTest"

    private lateinit var context: Context
    private lateinit var storage: DismissedEventsStorage
    private lateinit var mockTimeProvider: MockTimeProvider

    @Before
    fun setup() {
        // Enable Robolectric logging
        ShadowLog.stream = System.out

        // Configure Robolectric SQLite to use in-memory database
        ShadowSQLiteConnection.setUseInMemoryDatabase(true)

        DevLog.info(LOG_TAG, "Setting up DismissedEventsStorageRobolectricTest")

        // Get Robolectric context
        context = ApplicationProvider.getApplicationContext()

        // Setup mock time provider
        mockTimeProvider = MockTimeProvider(1635724800000) // 2021-11-01 00:00:00 UTC
        mockTimeProvider.setup()

        // Create storage with mock clock
        storage = DismissedEventsStorage(context, mockTimeProvider.testClock)
    }

    @After
    fun tearDown() {
        DevLog.info(LOG_TAG, "Cleaning up DismissedEventsStorageRobolectricTest")
        storage.close()
        mockTimeProvider.cleanup()
    }

    @Test
    fun testAddAndRetrieveSingleEvent() {
        // Given
        val event = createTestEvent(1)

        // When
        storage.addEvent(EventDismissType.ManuallyDismissedFromActivity, event)
        val events = storage.events

        // Then
        assertEquals("Should have exactly one event", 1, events.size)
        
        val storedEvent = events[0]
        assertEquals("Event ID should match", event.eventId, storedEvent.event.eventId)
        assertEquals("Calendar ID should match", event.calendarId, storedEvent.event.calendarId)
        assertEquals("Dismiss type should match", EventDismissType.ManuallyDismissedFromActivity, storedEvent.dismissType)
    }

    @Test
    fun testAddMultipleEvents() {
        // Given
        val event1 = createTestEvent(1)
        val event2 = createTestEvent(2)
        val event3 = createTestEvent(3)
        val events = listOf(event1, event2, event3)

        // When
        storage.addEvents(EventDismissType.ManuallyDismissedFromNotification, events)
        val storedEvents = storage.events

        // Then
        assertEquals("Should have exactly three events", 3, storedEvents.size)
        
        val eventIds = storedEvents.map { it.event.eventId }
        assertTrue("Should contain event 1", eventIds.contains(1L))
        assertTrue("Should contain event 2", eventIds.contains(2L))
        assertTrue("Should contain event 3", eventIds.contains(3L))
    }

    @Test
    fun testDeleteEvent() {
        // Given
        val event = createTestEvent(1)
        storage.addEvent(EventDismissType.ManuallyDismissedFromActivity, event)
        
        // Verify event was added
        assertEquals("Should have one event before deletion", 1, storage.events.size)

        // When
        storage.deleteEvent(event)
        val events = storage.events

        // Then
        assertEquals("Should have no events after deletion", 0, events.size)
    }

    @Test
    fun testClearHistory() {
        // Given
        val event1 = createTestEvent(1)
        val event2 = createTestEvent(2)
        storage.addEvent(EventDismissType.ManuallyDismissedFromActivity, event1)
        storage.addEvent(EventDismissType.ManuallyDismissedFromNotification, event2)
        
        // Verify events were added
        assertEquals("Should have two events before clearing", 2, storage.events.size)

        // When
        storage.clearHistory()
        val events = storage.events

        // Then
        assertEquals("Should have no events after clearing history", 0, events.size)
    }

    @Test
    fun testDifferentDismissTypes() {
        // Given
        val event1 = createTestEvent(1)
        val event2 = createTestEvent(2)
        val event3 = createTestEvent(3)
        val event4 = createTestEvent(4)

        // When - add events with different dismiss types
        storage.addEvent(EventDismissType.ManuallyDismissedFromActivity, event1)
        storage.addEvent(EventDismissType.ManuallyDismissedFromNotification, event2)
        storage.addEvent(EventDismissType.EventMovedInTheCalendar, event3)
        storage.addEvent(EventDismissType.AutoDismissedDueToRescheduleConfirmation, event4)

        val storedEvents = storage.events

        // Then
        assertEquals("Should have four events", 4, storedEvents.size)
        
        val eventByType = storedEvents.associateBy { it.event.eventId }
        assertEquals(EventDismissType.ManuallyDismissedFromActivity, eventByType[1L]?.dismissType)
        assertEquals(EventDismissType.ManuallyDismissedFromNotification, eventByType[2L]?.dismissType)
        assertEquals(EventDismissType.EventMovedInTheCalendar, eventByType[3L]?.dismissType)
        assertEquals(EventDismissType.AutoDismissedDueToRescheduleConfirmation, eventByType[4L]?.dismissType)
    }

    @Test
    fun testAddEventWithCustomTime() {
        // Given
        val event = createTestEvent(1)
        val customTime = 1635811200000L // 2021-11-02 00:00:00 UTC

        // When
        storage.addEvent(EventDismissType.ManuallyDismissedFromActivity, customTime, event)
        val storedEvents = storage.events

        // Then
        assertEquals("Should have exactly one event", 1, storedEvents.size)
        assertEquals("Dismiss time should match", customTime, storedEvents[0].dismissTime)
    }

    @Test
    fun testPurgeOldEvents() {
        // Given
        val oldTime = 1635724800000L // 2021-11-01 00:00:00 UTC
        val recentTime = 1636329600000L // 2021-11-08 00:00:00 UTC
        val currentTime = 1636416000000L // 2021-11-09 00:00:00 UTC
        
        val oldEvent = createTestEvent(1)
        val recentEvent = createTestEvent(2)
        
        // Add old event with old timestamp
        storage.addEvent(EventDismissType.ManuallyDismissedFromActivity, oldTime, oldEvent)
        // Add recent event with recent timestamp
        storage.addEvent(EventDismissType.ManuallyDismissedFromActivity, recentTime, recentEvent)
        
        // Verify both events were added
        assertEquals("Should have two events before purge", 2, storage.events.size)

        // When - purge events older than 3 days
        val maxLiveTime = 3 * 24 * 60 * 60 * 1000L // 3 days in milliseconds
        storage.purgeOld(currentTime, maxLiveTime)
        val remainingEvents = storage.events

        // Then
        assertEquals("Should have one event after purge", 1, remainingEvents.size)
        assertEquals("Remaining event should be the recent one", 2L, remainingEvents[0].event.eventId)
    }

    @Test
    fun testDismissTimeIsRecorded() {
        // Given
        val event = createTestEvent(1)
        val expectedTime = mockTimeProvider.testClock.currentTimeMillis()

        // When
        storage.addEvent(EventDismissType.ManuallyDismissedFromActivity, event)
        val storedEvents = storage.events

        // Then
        assertEquals("Should have exactly one event", 1, storedEvents.size)
        assertEquals("Dismiss time should be recorded correctly", expectedTime, storedEvents[0].dismissTime)
    }

    @Test
    fun testEventDetailsArePreserved() {
        // Given
        val event = createTestEvent(1).copy(
            title = "Special Event Title",
            desc = "Detailed Description",
            location = "Test Location",
            isAllDay = true,
            isRepeating = true
        )

        // When
        storage.addEvent(EventDismissType.ManuallyDismissedFromActivity, event)
        val storedEvents = storage.events

        // Then
        assertEquals("Should have exactly one event", 1, storedEvents.size)
        
        val storedEvent = storedEvents[0].event
        assertEquals("Title should be preserved", "Special Event Title", storedEvent.title)
        assertEquals("Description should be preserved", "Detailed Description", storedEvent.desc)
        assertEquals("Location should be preserved", "Test Location", storedEvent.location)
        assertEquals("All-day flag should be preserved", true, storedEvent.isAllDay)
        assertEquals("Repeating flag should be preserved", true, storedEvent.isRepeating)
    }

    @Test
    fun testDeleteNonExistentEvent() {
        // Given
        val event = createTestEvent(1)
        // Don't add the event to storage

        // When - deleting a non-existent event should not throw
        storage.deleteEvent(event)
        val events = storage.events

        // Then
        assertEquals("Should still have no events", 0, events.size)
    }

    @Test
    fun testAddSameEventMultipleTimes() {
        // Given
        val event = createTestEvent(1)

        // When - add the same event multiple times with different timestamps
        storage.addEvent(EventDismissType.ManuallyDismissedFromActivity, 1635724800000L, event)
        mockTimeProvider.advanceTime(60000)
        storage.addEvent(EventDismissType.ManuallyDismissedFromActivity, 1635724860000L, event)
        
        val storedEvents = storage.events

        // Then - behavior may vary based on implementation
        // Just verify we don't crash and have at least one event
        assertTrue("Should have at least one event", storedEvents.isNotEmpty())
    }

    private fun createTestEvent(id: Long): EventAlertRecord {
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        
        return EventAlertRecord(
            calendarId = 1L,
            eventId = id,
            isAllDay = false,
            isRepeating = false,
            alertTime = currentTime,
            notificationId = (id % Int.MAX_VALUE).toInt(),
            title = "Test Event $id",
            desc = "Test Description",
            startTime = currentTime,
            endTime = currentTime + 3600000,
            instanceStartTime = currentTime,
            instanceEndTime = currentTime + 3600000,
            location = "",
            lastStatusChangeTime = currentTime,
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0xffff0000.toInt(),
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = currentTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
    }
}
