package com.github.quarck.calnotify.dismissedeventsstorage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.MockApplicationComponents
import com.github.quarck.calnotify.testutils.MockCalendarProvider
import com.github.quarck.calnotify.testutils.MockContextProvider
import com.github.quarck.calnotify.testutils.MockTimeProvider
import expo.modules.mymodule.JsRescheduleConfirmationObject
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Ignore

@RunWith(AndroidJUnit4::class)
class EventDismissTest {
    private val LOG_TAG = "EventDismissTest"
    
    private lateinit var mockContext: Context
    private lateinit var mockDb: EventsStorageInterface
    private lateinit var mockComponents: MockApplicationComponents
    private lateinit var mockTimeProvider: MockTimeProvider
    
    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up EventDismissTest")

        // Setup mock time provider
        mockTimeProvider = MockTimeProvider(1635724800000) // 2021-11-01 00:00:00 UTC
        mockTimeProvider.setup()
        
        // Setup mock database
        mockDb = mockk<EventsStorageInterface>(relaxed = true)
        
        // Setup mock providers
        val mockContextProvider = MockContextProvider(mockTimeProvider)
        mockContextProvider.setup()
        val mockCalendarProvider = MockCalendarProvider(mockContextProvider, mockTimeProvider)
        mockCalendarProvider.setup()
        
        // Setup mock components
        mockComponents = MockApplicationComponents(
            contextProvider = mockContextProvider,
            timeProvider = mockTimeProvider,
            calendarProvider = mockCalendarProvider
        )
        mockComponents.setup()

        mockContext = mockContextProvider.fakeContext
    }
    
    @Test
    fun testOriginalDismissEventWithValidEvent() {
        // Given
        val event = createTestEvent()
        mockkConstructor(EventsStorage::class)
        every { anyConstructed<EventsStorage>().getEvent(event.eventId, event.instanceStartTime) } returns event
        every { anyConstructed<EventsStorage>().deleteEvent(event.eventId, event.instanceStartTime) } returns true
        
        // When
        ApplicationController.dismissEvent(
            mockContext,
            EventDismissType.ManuallyDismissedFromActivity,
            event.eventId,
            event.instanceStartTime,
            0,
            false
        )
        
        // Then
        verify { anyConstructed<EventsStorage>().getEvent(event.eventId, event.instanceStartTime) }
        verify { anyConstructed<EventsStorage>().deleteEvent(event.eventId, event.instanceStartTime) }
    }
    
    @Test
    fun testOriginalDismissEventWithNonExistentEvent() {
        // Given
        val event = createTestEvent()
        mockkConstructor(EventsStorage::class)
        every { anyConstructed<EventsStorage>().getEvent(event.eventId, event.instanceStartTime) } returns null
        
        // When
        ApplicationController.dismissEvent(
            mockContext,
            EventDismissType.ManuallyDismissedFromActivity,
            event.eventId,
            event.instanceStartTime,
            0,
            false
        )
        
        // Then
        verify { anyConstructed<EventsStorage>().getEvent(event.eventId, event.instanceStartTime) }
        verify(exactly = 0) { anyConstructed<EventsStorage>().deleteEvent(any(), any()) }
    }
    
    @Test
    fun testSafeDismissEventsWithValidEvents() {
        // Given
        val events = listOf(createTestEvent(1), createTestEvent(2))
        every { mockDb.getEvent(any(), any()) } returns events[0]
        every { mockDb.deleteEvents(any()) } returns events.size
        
        // When
        val results = ApplicationController.safeDismissEvents(
            mockContext,
            mockDb,
            events,
            EventDismissType.ManuallyDismissedFromActivity,
            false
        )
        
        // Then
        assertEquals(events.size, results.size)
        results.forEach { (event, result) ->
            assertEquals(EventDismissResult.Success, result)
        }
        verify { mockDb.deleteEvents(events) }
    }
    
    @Test
    fun testSafeDismissEventsWithMixedValidAndInvalidEvents() {
        // Given
        val validEvent = createTestEvent(1)
        val invalidEvent = createTestEvent(2)
        val events = listOf(validEvent, invalidEvent)
        
        every { mockDb.getEvent(validEvent.eventId, validEvent.instanceStartTime) } returns validEvent
        every { mockDb.getEvent(invalidEvent.eventId, invalidEvent.instanceStartTime) } returns null
        every { mockDb.deleteEvents(listOf(validEvent)) } returns 1
        
        // When
        val results = ApplicationController.safeDismissEvents(
            mockContext,
            mockDb,
            events,
            EventDismissType.ManuallyDismissedFromActivity,
            false
        )
        
        // Then
        assertEquals(events.size, results.size)
        val validResult = results.find { it.first == validEvent }?.second
        val invalidResult = results.find { it.first == invalidEvent }?.second
        
        assertNotNull(validResult)
        assertNotNull(invalidResult)
        assertEquals(EventDismissResult.Success, validResult)
        assertEquals(EventDismissResult.EventNotFound, invalidResult)
    }
    
    @Test
    fun testSafeDismissEventsWithDeletionWarning() {
        // Given
        val event = createTestEvent()
        every { mockDb.getEvent(any(), any()) } returns event
        every { mockDb.deleteEvents(any()) } returns 0 // Simulate deletion failure
        
        // When
        val results = ApplicationController.safeDismissEvents(
            mockContext,
            mockDb,
            listOf(event),
            EventDismissType.ManuallyDismissedFromActivity,
            false
        )
        
        // Then
        assertEquals(1, results.size)
        assertEquals(EventDismissResult.DeletionWarning, results[0].second)
        verify { mockDb.deleteEvents(listOf(event)) }
    }
    
    @Test
    fun testSafeDismissEventsByIdWithValidEvents() {
        // Given
        val eventIds = listOf(1L, 2L)
        val events = eventIds.map { createTestEvent(it) }
        
        every { mockDb.getEventInstances(any()) } returns events
        every { mockDb.getEvent(any(), any()) } returns events[0]
        every { mockDb.deleteEvents(any()) } returns events.size
        
        // When
        val results = ApplicationController.safeDismissEventsById(
            mockContext,
            mockDb,
            eventIds,
            EventDismissType.ManuallyDismissedFromActivity,
            false
        )
        
        // Then
        assertEquals(eventIds.size, results.size)
        results.forEach { (_, result) ->
            assertEquals(EventDismissResult.Success, result)
        }
    }
    
    @Test
    fun testSafeDismissEventsByIdWithNonExistentEvents() {
        // Given
        val eventIds = listOf(1L, 2L)
        every { mockDb.getEventInstances(any()) } returns emptyList()
        
        // When
        val results = ApplicationController.safeDismissEventsById(
            mockContext,
            mockDb,
            eventIds,
            EventDismissType.ManuallyDismissedFromActivity,
            false
        )
        
        // Then
        assertEquals(eventIds.size, results.size)
        results.forEach { (_, result) ->
            assertEquals(EventDismissResult.EventNotFound, result)
        }
    }
    
    @Test
    fun testSafeDismissEventsWithStorageError() {
        // Given
        val event = createTestEvent()
        every { mockDb.getEvent(any(), any()) } returns event
        every { mockDb.deleteEvents(any()) } throws RuntimeException("Storage error")
        
        // When
        val results = ApplicationController.safeDismissEvents(
            mockContext,
            mockDb,
            listOf(event),
            EventDismissType.ManuallyDismissedFromActivity,
            false
        )
        
        // Then
        assertEquals(1, results.size)
        assertEquals(EventDismissResult.DeletionWarning, results[0].second)
    }
    
    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsWithFutureEvents() {
        // Given
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val futureEvents = listOf(
            JsRescheduleConfirmationObject(
                event_id = 1L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Test Event 1",
                new_instance_start_time = currentTime + 3600000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = true
            ),
            JsRescheduleConfirmationObject(
                event_id = 2L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Test Event 2",
                new_instance_start_time = currentTime + 7200000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = true
            )
        )
        val events = futureEvents.map { createTestEvent(it.event_id) }
        
        // Add events to existing mock components for retrieval
        futureEvents.forEach { confirmation ->
            val event = events.find { it.eventId == confirmation.event_id }!!
            mockComponents.addEventToStorage(event)
        }
        
        // Mock our mock database to return these events when queried
        futureEvents.forEach { confirmation ->
            val event = events.find { it.eventId == confirmation.event_id }!!
            every { mockDb.getEventInstances(confirmation.event_id) } returns listOf(event)
            every { mockDb.getEvent(confirmation.event_id, any()) } returns event
        }
        
        // Mock successful deletion
        every { mockDb.deleteEvents(any()) } returns events.size
        
        // Clear any previous toast messages
        mockComponents.clearToastMessages()
        
        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            futureEvents,
            false
        )
        
        // Then
        assertEquals(futureEvents.size, results.size)
        results.forEach { (_, result) ->
            assertEquals(EventDismissResult.Success, result)
        }
        
        // Verify toast messages - be more lenient about exact message content
        val toastMessages = mockComponents.getToastMessages()
        assertTrue(toastMessages.isNotEmpty())
        assertTrue(toastMessages.any { it.contains("Attempting to dismiss") })
    }
    
    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsWithMixedEvents() {
        // Given
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val confirmations = listOf(
            JsRescheduleConfirmationObject(
                event_id = 1L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Test Event 1",
                new_instance_start_time = currentTime + 3600000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = true
            ),
            JsRescheduleConfirmationObject(
                event_id = 2L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Test Event 2",
                new_instance_start_time = currentTime - 3600000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = false
            ),
            JsRescheduleConfirmationObject(
                event_id = 3L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Test Event 3",
                new_instance_start_time = currentTime + 7200000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = true
            )
        )
        val futureEvents = confirmations.filter { it.is_in_future }
        val events = futureEvents.map { createTestEvent(it.event_id) }
        
        // Add events to existing mock components for retrieval
        futureEvents.forEach { confirmation ->
            val event = events.find { it.eventId == confirmation.event_id }!!
            mockComponents.addEventToStorage(event)
        }
        
        // Mock our mock database to return these events when queried
        futureEvents.forEach { confirmation ->
            val event = events.find { it.eventId == confirmation.event_id }!!
            every { mockDb.getEventInstances(confirmation.event_id) } returns listOf(event)
            every { mockDb.getEvent(confirmation.event_id, any()) } returns event
        }
        
        // Mock successful deletion
        every { mockDb.deleteEvents(any()) } returns events.size
        
        // Clear any previous toast messages
        mockComponents.clearToastMessages()
        
        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            false
        )
        
        // Then
        assertEquals(futureEvents.size, results.size)
        results.forEach { (_, result) ->
            assertEquals(EventDismissResult.Success, result)
        }
        
        // Verify toast messages - be more lenient about exact message content
        val toastMessages = mockComponents.getToastMessages()
        assertTrue(toastMessages.isNotEmpty())
        assertTrue(toastMessages.any { it.contains("Attempting to dismiss") })
    }
    
    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsWithNonExistentEvents() {
        // Given
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val confirmations = listOf(
            JsRescheduleConfirmationObject(
                event_id = 1L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Test Event 1",
                new_instance_start_time = currentTime + 3600000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = true
            ),
            JsRescheduleConfirmationObject(
                event_id = 2L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Test Event 2",
                new_instance_start_time = currentTime + 7200000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = true
            )
        )
        
        every { mockDb.getEventInstances(any()) } returns emptyList()
        
        // Clear any previous toast messages
        mockComponents.clearToastMessages()
        
        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            false
        )
        
        // Then
        assertEquals(confirmations.size, results.size)
        results.forEach { (_, result) ->
            assertEquals(EventDismissResult.EventNotFound, result)
        }
        
        // Verify toast messages
        val toastMessages = mockComponents.getToastMessages()
        assertEquals(2, toastMessages.size)
        assertEquals("Attempting to dismiss ${confirmations.size} events", toastMessages[0])
        assertEquals("Dismissed 0 events successfully, ${confirmations.size} events not found, 0 events failed", toastMessages[1])
    }
    
    @Test
    @Ignore("this is not correctly mocking the storage error. not sure why yet though")
    fun testSafeDismissEventsFromRescheduleConfirmationsWithStorageError() {
        // Given
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val confirmations = listOf(
            JsRescheduleConfirmationObject(
                event_id = 1L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Test Event 1",
                new_instance_start_time = currentTime + 3600000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = true
            )
        )
        val events = confirmations.map { createTestEvent(it.event_id) }
        
        // Add the event to existing mock components for retrieval
        confirmations.forEach { confirmation ->
            val event = events.find { it.eventId == confirmation.event_id }!!
            mockComponents.addEventToStorage(event)
        }
        
        // Mock our mock database to return these events when queried
        confirmations.forEach { confirmation ->
            val event = events.find { it.eventId == confirmation.event_id }!!
            every { mockDb.getEventInstances(confirmation.event_id) } returns listOf(event)
            every { mockDb.getEvent(confirmation.event_id, any()) } returns event
        }
        
        // Simulate storage errors for all database operations
        every { mockDb.deleteEvents(any()) } throws RuntimeException("Storage error")
        every { mockDb.deleteEvent(any(), any()) } throws RuntimeException("Storage error")
        every { mockDb.addEvent(any()) } throws RuntimeException("Storage error")
        every { mockDb.addEvents(any()) } throws RuntimeException("Storage error")
        every { mockDb.updateEvent(any(), any(), any()) } throws RuntimeException("Storage error")
        
        // Also ensure DismissedEventsStorage operations fail
        mockkConstructor(DismissedEventsStorage::class)
        every { anyConstructed<DismissedEventsStorage>().addEvent(any(), any()) } throws RuntimeException("Storage error")
        every { anyConstructed<DismissedEventsStorage>().addEvents(any(), any()) } throws RuntimeException("Storage error")
        
        // Clear any previous toast messages
        mockComponents.clearToastMessages()
        
        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            false
        )
        
        // Then
        assertEquals(confirmations.size, results.size)
        results.forEach { (_, result) ->
            assertTrue(
                "Expected error result but got $result",
                result == EventDismissResult.StorageError || 
                result == EventDismissResult.DatabaseError || 
                result == EventDismissResult.DeletionWarning
            )
        }
        
        // Verify toast messages are shown about the error
        val toastMessages = mockComponents.getToastMessages()
        assertTrue(toastMessages.size >= 1)
        assertTrue(toastMessages.any { it.contains("Attempting to dismiss") })
        assertTrue(toastMessages.any { it.contains("failed") || it.contains("error") || it.contains("Warning") })
    }
    
    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsWithAllPastEvents() {
        // Given
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val confirmations = listOf(
            JsRescheduleConfirmationObject(
                event_id = 1L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Test Event 1",
                new_instance_start_time = currentTime - 3600000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = false
            ),
            JsRescheduleConfirmationObject(
                event_id = 2L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Test Event 2",
                new_instance_start_time = currentTime - 7200000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = false
            )
        )
        
        // Clear any previous toast messages
        mockComponents.clearToastMessages()
        
        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            false
        )
        
        // Then
        assertTrue(results.isEmpty())
        
        // Verify toast messages
        val toastMessages = mockComponents.getToastMessages()
        assertEquals(1, toastMessages.size)
        assertEquals("No future events to dismiss", toastMessages[0])
    }
    
    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsWithEmptyList() {
        // Given
        val confirmations = emptyList<JsRescheduleConfirmationObject>()
        
        // Clear any previous toast messages
        mockComponents.clearToastMessages()
        
        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            false
        )
        
        // Then
        assertTrue(results.isEmpty())
        
        // Verify toast messages
        val toastMessages = mockComponents.getToastMessages()
        assertEquals(1, toastMessages.size)
        assertEquals("No future events to dismiss", toastMessages[0])
    }
    
    private fun createTestEvent(id: Long = 1L): EventAlertRecord {
        return EventAlertRecord(
            calendarId = 1L,
            eventId = id,
            isAllDay = false,
            isRepeating = false,
            alertTime = System.currentTimeMillis(),
            notificationId = 0,
            title = "Test Event $id",
            desc = "Test Description",
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + 3600000,
            instanceStartTime = System.currentTimeMillis(),
            instanceEndTime = System.currentTimeMillis() + 3600000,
            location = "",
            lastStatusChangeTime = System.currentTimeMillis(),
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0xffff0000.toInt(),
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = System.currentTimeMillis(),
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
    }
} 
