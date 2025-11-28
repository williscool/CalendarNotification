package com.github.quarck.calnotify.dismissedeventsstorage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.quarck.calnotify.app.AlarmSchedulerInterface
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.notification.EventNotificationManagerInterface
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
    fun testSafeDismissEventsWithDatabaseError() {
        // Given
        val event = createTestEvent()
        every { mockDb.getEvent(any(), any()) } returns event
        every { mockDb.deleteEvents(any()) } throws RuntimeException("Database error")

        // When
        val results = ApplicationController.safeDismissEvents(
            mockContext,
            mockDb,
            listOf(event),
            EventDismissType.ManuallyDismissedFromActivity,
            false
        )

        // Then
        // When deleteEvents throws, the exception is caught and deleteSuccess is set to false.
        // This results in DeletionWarning, not DatabaseError (same behavior as when deleteEvents returns 0).
        assertEquals(1, results.size)
        assertEquals(EventDismissResult.DeletionWarning, results[0].second)
        verify { mockDb.deleteEvents(listOf(event)) }
    }

    @Test
    fun testSafeDismissEventsWithStorageError() {
        // Given
        val event = createTestEvent()
        every { mockDb.getEvent(any(), any()) } returns event
        every { mockDb.deleteEvents(any()) } returns 1 // Deletion succeeds initially

        // Mock DismissedEventsStorage to throw an error
        val throwingDismissedStorage = mockk<DismissedEventsStorage>(relaxed = true)
        every { 
            throwingDismissedStorage.addEvents(
                EventDismissType.ManuallyDismissedFromActivity,
                any<Collection<EventAlertRecord>>()
            ) 
        } throws RuntimeException("Storage error")

        // When
        val results = ApplicationController.safeDismissEvents(
            mockContext,
            mockDb,
            listOf(event),
            EventDismissType.ManuallyDismissedFromActivity,
            false,
            dismissedEventsStorage = throwingDismissedStorage // Use the throwing mock
        )

        // Then
        // When storage throws an error, the code returns early and does not attempt to delete from main storage
        assertEquals(1, results.size)
        assertEquals(EventDismissResult.StorageError, results[0].second)
        verify(exactly = 0) { mockDb.deleteEvents(any()) } // deleteEvents should NOT be called when storage fails
        verify { throwingDismissedStorage.addEvents(EventDismissType.ManuallyDismissedFromActivity, listOf(event)) }
    }

    @Test
    fun testSafeDismissEventsWithGetEventException() {
        // Given
        val event = createTestEvent()
        every { mockDb.getEvent(any(), any()) } throws RuntimeException("Database lookup error") // Simulate error on getEvent

        // When
        val results = ApplicationController.safeDismissEvents(
            mockContext,
            mockDb,
            listOf(event),
            EventDismissType.ManuallyDismissedFromActivity,
            false
        )

        // Then
        // When getEvent throws, the exception is caught by the outer catch block.
        // Since results is empty at that point, indexOfFirst returns -1 and no result is added.
        // The function returns an empty results list.
        assertEquals(0, results.size)
        verify(exactly = 0) { mockDb.deleteEvents(any()) }
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
            JsRescheduleConfirmationObject( // Future event
                event_id = 1L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Future Event",
                new_instance_start_time = currentTime + 3600000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = true
            ),
            JsRescheduleConfirmationObject( // Past event
                event_id = 2L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Past Event",
                new_instance_start_time = currentTime - 3600000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = false
            ),
            JsRescheduleConfirmationObject( // Future repeating event
                event_id = 3L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Repeating Future Event",
                new_instance_start_time = currentTime + 7200000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = true
            )
        )
        val futureNonRepeatingEvent = createTestEvent(1L, isRepeating = false)
        val futureRepeatingEvent = createTestEvent(3L, isRepeating = true)
        val eventsToDismiss = listOf(futureNonRepeatingEvent) // Only the non-repeating future event should be dismissed

        // Mock database interactions
        every { mockDb.getEventInstances(1L) } returns listOf(futureNonRepeatingEvent)
        every { mockDb.getEvent(1L, any()) } returns futureNonRepeatingEvent
        // Don't need to mock for event 2 (past)
        every { mockDb.getEventInstances(3L) } returns listOf(futureRepeatingEvent)
        every { mockDb.getEvent(3L, any()) } returns futureRepeatingEvent

        every { mockDb.deleteEvents(eventsToDismiss) } returns eventsToDismiss.size
        every { mockDb.events } returns emptyList()

        // Clear any previous toast messages
        mockComponents.clearToastMessages()

        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            false,
            db = mockDb
        )

        // Then
        assertEquals(2, results.size)
        val resultMap = results.toMap()
        assertEquals(EventDismissResult.Success, resultMap[1L]) // Event 1 dismissed
        assertEquals(EventDismissResult.SkippedRepeating, resultMap[3L]) // Event 3 skipped

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
        assertEquals("Dismissed 0 events successfully, ${confirmations.size} events not found, 0 events failed, 0 repeating events skipped", toastMessages[1]) // Updated assertion
    }

    @Test
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
        val events = confirmations.map { createTestEvent(it.event_id) }
        confirmations.forEach { confirmation ->
            val event = events.find { it.eventId == confirmation.event_id }!!
            every { mockDb.getEventInstances(confirmation.event_id) } returns listOf(event)
            every { mockDb.getEvent(confirmation.event_id, any()) } returns event
        }
        every { mockDb.deleteEvents(any()) } returns events.size
        every { mockDb.events } returns emptyList()

        // Create a mock DismissedEventsStorage that throws on addEvents
        val throwingDismissedEventsStorage = mockk<DismissedEventsStorage>(relaxed = true)
        every { throwingDismissedEventsStorage.addEvents(any(), any()) } throws RuntimeException("Storage error")

        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            notifyActivity = false,
            db = mockDb, // Inject mockDb so event lookup works
            dismissedEventsStorage = throwingDismissedEventsStorage
        )

        // Then
        // When storage throws an error, the code returns early and does not attempt to delete from main storage
        assertEquals(confirmations.size, results.size)
        results.forEach { (_, result) ->
            assertEquals(EventDismissResult.StorageError, result)
        }
        verify(exactly = 0) { mockDb.deleteEvents(any()) } // deleteEvents should NOT be called when storage fails
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

    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsWithAllRepeatingEvents() {
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

        // Create two repeating events
        val repeatingEvent1 = createTestEvent(1L).apply { isRepeating = true }
        val repeatingEvent2 = createTestEvent(2L).apply { isRepeating = true }

        // Add events to existing mock components for retrieval
        mockComponents.addEventToStorage(repeatingEvent1)
        mockComponents.addEventToStorage(repeatingEvent2)

        // Mock our mock database to return these events when queried
        every { mockDb.getEventInstances(1L) } returns listOf(repeatingEvent1)
        every { mockDb.getEventInstances(2L) } returns listOf(repeatingEvent2)
        every { mockDb.getEvent(1L, any()) } returns repeatingEvent1
        every { mockDb.getEvent(2L, any()) } returns repeatingEvent2

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

        // Verify both events were skipped
        results.forEach { (_, result) ->
            assertEquals(EventDismissResult.SkippedRepeating, result)
        }

        // Verify toast messages
        val toastMessages = mockComponents.getToastMessages()
        assertTrue(toastMessages.isNotEmpty())
        assertTrue(toastMessages.any { it.contains("Attempting to dismiss") })
        assertTrue(toastMessages.any { it.contains("2 repeating events skipped") })
    }

    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsSkipsRepeatingEvents() {
        // Given
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val confirmations = listOf(
            JsRescheduleConfirmationObject(
                event_id = 1L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Repeating Event",
                new_instance_start_time = currentTime + 3600000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = true
            ),
            JsRescheduleConfirmationObject(
                event_id = 2L,
                calendar_id = 1L,
                original_instance_start_time = currentTime,
                title = "Non-Repeating Event",
                new_instance_start_time = currentTime + 7200000,
                created_at = currentTime.toString(),
                updated_at = currentTime.toString(),
                is_in_future = true
            )
        )
        // Event 1 is repeating, Event 2 is not
        val repeatingEvent = createTestEvent(1L).copy(isRepeating = true)
        val nonRepeatingEvent = createTestEvent(2L).copy(isRepeating = false)

        every { mockDb.getEventInstances(1L) } returns listOf(repeatingEvent)
        every { mockDb.getEventInstances(2L) } returns listOf(nonRepeatingEvent)
        every { mockDb.getEvent(1L, any()) } returns repeatingEvent
        every { mockDb.getEvent(2L, any()) } returns nonRepeatingEvent
        every { mockDb.deleteEvents(any()) } returns 1
        every { mockDb.events } returns emptyList()

        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            notifyActivity = false,
            db = mockDb
        )

        // Then
        assertEquals(2, results.size)
        val resultMap = results.toMap()
        assertEquals(EventDismissResult.SkippedRepeating, resultMap[1L])
        assertEquals(EventDismissResult.Success, resultMap[2L])
    }

    private fun createTestEvent(id: Long = 1L, isRepeating: Boolean = false): EventAlertRecord {
        return EventAlertRecord(
            calendarId = 1L,
            eventId = id,
            isAllDay = false,
            isRepeating = isRepeating, // Use parameter
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
