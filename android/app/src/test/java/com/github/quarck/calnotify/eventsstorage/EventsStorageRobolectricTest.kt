package com.github.quarck.calnotify.eventsstorage

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
 * Robolectric tests for EventsStorage.
 *
 * These tests verify the actual SQLite storage operations for event alerts
 * using Robolectric's in-memory database support.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class EventsStorageRobolectricTest {
    private val LOG_TAG = "EventsStorageRoboTest"

    private lateinit var context: Context
    private lateinit var storage: EventsStorage
    private lateinit var mockTimeProvider: MockTimeProvider

    @Before
    fun setup() {
        // Enable Robolectric logging
        ShadowLog.stream = System.out

        // Configure Robolectric SQLite to use in-memory database
        ShadowSQLiteConnection.setUseInMemoryDatabase(true)

        DevLog.info(LOG_TAG, "Setting up EventsStorageRobolectricTest")

        // Get Robolectric context
        context = ApplicationProvider.getApplicationContext()

        // Setup mock time provider
        mockTimeProvider = MockTimeProvider(1635724800000) // 2021-11-01 00:00:00 UTC
        mockTimeProvider.setup()

        // Create storage
        storage = EventsStorage(context)
    }

    @After
    fun tearDown() {
        DevLog.info(LOG_TAG, "Cleaning up EventsStorageRobolectricTest")
        storage.close()
        mockTimeProvider.cleanup()
    }

    @Test
    fun testAddAndRetrieveSingleEvent() {
        // Given
        val event = createTestEvent(1)

        // When
        val added = storage.addEvent(event)
        val events = storage.events

        // Then
        assertTrue("Event should be added successfully", added)
        assertEquals("Should have exactly one event", 1, events.size)
        
        val storedEvent = events[0]
        assertEquals("Event ID should match", event.eventId, storedEvent.eventId)
        assertEquals("Calendar ID should match", event.calendarId, storedEvent.calendarId)
        assertEquals("Title should match", event.title, storedEvent.title)
    }

    @Test
    fun testAddMultipleEvents() {
        // Given
        val event1 = createTestEvent(1)
        val event2 = createTestEvent(2)
        val event3 = createTestEvent(3)
        val events = listOf(event1, event2, event3)

        // When
        val added = storage.addEvents(events)
        val storedEvents = storage.events

        // Then
        assertTrue("Events should be added successfully", added)
        assertEquals("Should have exactly three events", 3, storedEvents.size)
        
        val eventIds = storedEvents.map { it.eventId }
        assertTrue("Should contain event 1", eventIds.contains(1L))
        assertTrue("Should contain event 2", eventIds.contains(2L))
        assertTrue("Should contain event 3", eventIds.contains(3L))
    }

    @Test
    fun testGetEventByIdAndInstanceTime() {
        // Given
        val event = createTestEvent(1)
        storage.addEvent(event)

        // When
        val retrievedEvent = storage.getEvent(event.eventId, event.instanceStartTime)

        // Then
        assertNotNull("Event should be found", retrievedEvent)
        assertEquals("Event ID should match", event.eventId, retrievedEvent?.eventId)
        assertEquals("Instance start time should match", event.instanceStartTime, retrievedEvent?.instanceStartTime)
    }

    @Test
    fun testGetEventInstances() {
        // Given - create multiple instances of the same event
        val baseTime = mockTimeProvider.testClock.currentTimeMillis()
        val event1 = createTestEvent(1, instanceStartTime = baseTime)
        val event2 = createTestEvent(1, instanceStartTime = baseTime + 86400000) // Next day
        val event3 = createTestEvent(1, instanceStartTime = baseTime + 172800000) // 2 days later
        
        storage.addEvent(event1)
        storage.addEvent(event2)
        storage.addEvent(event3)

        // When
        val instances = storage.getEventInstances(1)

        // Then
        assertEquals("Should have exactly three instances", 3, instances.size)
        assertTrue("All instances should have the same event ID", instances.all { it.eventId == 1L })
    }

    @Test
    fun testDeleteEvent() {
        // Given
        val event = createTestEvent(1)
        storage.addEvent(event)
        
        // Verify event was added
        assertEquals("Should have one event before deletion", 1, storage.events.size)

        // When
        val deleted = storage.deleteEvent(event.eventId, event.instanceStartTime)
        val events = storage.events

        // Then
        assertTrue("Event should be deleted successfully", deleted)
        assertEquals("Should have no events after deletion", 0, events.size)
    }

    @Test
    fun testDeleteEvents() {
        // Given
        val event1 = createTestEvent(1)
        val event2 = createTestEvent(2)
        val event3 = createTestEvent(3)
        val eventsToDelete = listOf(event1, event2)
        
        storage.addEvent(event1)
        storage.addEvent(event2)
        storage.addEvent(event3)
        
        // Verify events were added
        assertEquals("Should have three events before deletion", 3, storage.events.size)

        // When
        val deletedCount = storage.deleteEvents(eventsToDelete)
        val remainingEvents = storage.events

        // Then
        assertEquals("Should have deleted 2 events", 2, deletedCount)
        assertEquals("Should have one event remaining", 1, remainingEvents.size)
        assertEquals("Remaining event should be event 3", 3L, remainingEvents[0].eventId)
    }

    @Test
    fun testDeleteAllEvents() {
        // Given
        storage.addEvent(createTestEvent(1))
        storage.addEvent(createTestEvent(2))
        storage.addEvent(createTestEvent(3))
        
        // Verify events were added
        assertEquals("Should have three events before clearing", 3, storage.events.size)

        // When
        val deleted = storage.deleteAllEvents()
        val events = storage.events

        // Then
        assertTrue("Should delete all events successfully", deleted)
        assertEquals("Should have no events after clearing", 0, events.size)
    }

    @Test
    fun testUpdateEvent() {
        // Given
        val event = createTestEvent(1)
        storage.addEvent(event)

        // When - update the event
        val updatedEvent = event.copy(
            title = "Updated Title",
            location = "New Location",
            snoozedUntil = mockTimeProvider.testClock.currentTimeMillis() + 3600000
        )
        val success = storage.updateEvent(updatedEvent)
        val retrievedEvent = storage.getEvent(event.eventId, event.instanceStartTime)

        // Then
        assertTrue("Event should be updated successfully", success)
        assertNotNull("Updated event should be found", retrievedEvent)
        assertEquals("Title should be updated", "Updated Title", retrievedEvent?.title)
        assertEquals("Location should be updated", "New Location", retrievedEvent?.location)
    }

    @Test
    fun testUpdateEventWithParameters() {
        // Given
        val event = createTestEvent(1)
        storage.addEvent(event)
        val newTitle = "Parameter Updated Title"
        val newLocation = "Parameter New Location"
        val newSnoozedUntil = mockTimeProvider.testClock.currentTimeMillis() + 7200000

        // When
        val (success, newEvent) = storage.updateEvent(
            event,
            title = newTitle,
            location = newLocation,
            snoozedUntil = newSnoozedUntil
        )
        val retrievedEvent = storage.getEvent(event.eventId, event.instanceStartTime)

        // Then
        assertTrue("Event should be updated successfully", success)
        assertEquals("Title should be updated in returned event", newTitle, newEvent.title)
        assertEquals("Location should be updated in returned event", newLocation, newEvent.location)
        assertEquals("SnoozedUntil should be updated in returned event", newSnoozedUntil, newEvent.snoozedUntil)
        
        assertNotNull("Updated event should be found", retrievedEvent)
        assertEquals("Title should be updated in storage", newTitle, retrievedEvent?.title)
    }

    @Test
    fun testUpdateEvents() {
        // Given
        val event1 = createTestEvent(1)
        val event2 = createTestEvent(2)
        storage.addEvent(event1)
        storage.addEvent(event2)

        // When - update both events
        val updatedEvents = listOf(
            event1.copy(title = "Updated Event 1"),
            event2.copy(title = "Updated Event 2")
        )
        val success = storage.updateEvents(updatedEvents)
        val storedEvents = storage.events

        // Then
        assertTrue("Events should be updated successfully", success)
        assertEquals("Should still have two events", 2, storedEvents.size)
        
        val titles = storedEvents.map { it.title }
        assertTrue("Should contain updated title 1", titles.contains("Updated Event 1"))
        assertTrue("Should contain updated title 2", titles.contains("Updated Event 2"))
    }

    @Test
    fun testEventDisplayStatus() {
        // Given
        val event = createTestEvent(1).copy(displayStatus = EventDisplayStatus.Hidden)
        storage.addEvent(event)

        // When - update display status
        val (success, _) = storage.updateEvent(event, displayStatus = EventDisplayStatus.DisplayedNormal)
        val retrievedEvent = storage.getEvent(event.eventId, event.instanceStartTime)

        // Then
        assertTrue("Event should be updated successfully", success)
        assertNotNull("Updated event should be found", retrievedEvent)
        assertEquals("Display status should be updated", EventDisplayStatus.DisplayedNormal, retrievedEvent?.displayStatus)
    }

    @Test
    fun testRepeatingEventFlag() {
        // Given
        val event = createTestEvent(1).copy(isRepeating = false)
        storage.addEvent(event)

        // When - update repeating flag
        val (success, _) = storage.updateEvent(event, isRepeating = true)
        val retrievedEvent = storage.getEvent(event.eventId, event.instanceStartTime)

        // Then
        assertTrue("Event should be updated successfully", success)
        assertNotNull("Updated event should be found", retrievedEvent)
        assertEquals("Repeating flag should be updated", true, retrievedEvent?.isRepeating)
    }

    @Test
    fun testEventDetailsPreserved() {
        // Given
        val event = createTestEvent(1).copy(
            title = "Special Event",
            desc = "Detailed Description",
            location = "Test Location",
            isAllDay = true,
            color = 0xFF00FF00.toInt()
        )

        // When
        storage.addEvent(event)
        val retrievedEvent = storage.getEvent(event.eventId, event.instanceStartTime)

        // Then
        assertNotNull("Event should be found", retrievedEvent)
        assertEquals("Title should be preserved", "Special Event", retrievedEvent?.title)
        assertEquals("Description should be preserved", "Detailed Description", retrievedEvent?.desc)
        assertEquals("Location should be preserved", "Test Location", retrievedEvent?.location)
        assertEquals("All-day flag should be preserved", true, retrievedEvent?.isAllDay)
        assertEquals("Color should be preserved", 0xFF00FF00.toInt(), retrievedEvent?.color)
    }

    @Test
    fun testGetNonExistentEvent() {
        // Given - no events added

        // When
        val retrievedEvent = storage.getEvent(999L, 0L)

        // Then
        assertNull("Should return null for non-existent event", retrievedEvent)
    }

    @Test
    fun testDeleteNonExistentEvent() {
        // Given - no events added

        // When
        val deleted = storage.deleteEvent(999L, 0L)

        // Then
        assertFalse("Should return false when deleting non-existent event", deleted)
    }

    @Test
    fun testEmptyEventsList() {
        // Given - no events added

        // When
        val events = storage.events

        // Then
        assertTrue("Events list should be empty", events.isEmpty())
    }

    private fun createTestEvent(
        id: Long, 
        instanceStartTime: Long? = null
    ): EventAlertRecord {
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val startTime = instanceStartTime ?: currentTime
        
        return EventAlertRecord(
            calendarId = 1L,
            eventId = id,
            isAllDay = false,
            isRepeating = false,
            alertTime = currentTime,
            notificationId = (id % Int.MAX_VALUE).toInt(),
            title = "Test Event $id",
            desc = "Test Description",
            startTime = startTime,
            endTime = startTime + 3600000,
            instanceStartTime = startTime,
            instanceEndTime = startTime + 3600000,
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
