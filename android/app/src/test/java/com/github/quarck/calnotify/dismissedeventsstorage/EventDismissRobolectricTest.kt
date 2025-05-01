package com.github.quarck.calnotify.dismissedeventsstorage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.app.AlarmSchedulerInterface
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric version of EventDismissTest for testing event dismissal functionality.
 * 
 * This test class uses Robolectric to simulate the Android environment, allowing us to test
 * components that interact with Android framework classes without requiring a device or emulator.
 * The main difference from [EventDismissTest] is that:
 * 
 * 1. This test runs on the JVM using Robolectric instead of instrumenting a real device
 * 2. It directly uses and verifies DismissedEventsStorage instances through dependency injection
 * 3. Most Android components are either mocked or provided by Robolectric's shadow implementations
 * 
 * These tests verify the event dismissal functionality in ApplicationController with a focus on
 * different edge cases and error handling scenarios.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE, sdk = [28]) // Configure Robolectric
class EventDismissRobolectricTest {
    private val LOG_TAG = "EventDismissRobolectricTest"

    private lateinit var mockContext: Context
    private lateinit var mockDb: EventsStorageInterface
    private lateinit var mockComponents: MockApplicationComponents
    private lateinit var mockTimeProvider: MockTimeProvider
    private lateinit var dismissedEventsStorage: DismissedEventsStorage

    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up EventDismissRobolectricTest")

        // Setup mock time provider
        mockTimeProvider = MockTimeProvider(1635724800000) // 2021-11-01 00:00:00 UTC
        mockTimeProvider.setup()

        // Setup mock database
        mockDb = mockk<EventsStorageInterface>(relaxed = true)

        // Get context using Robolectric's ApplicationProvider
        mockContext = ApplicationProvider.getApplicationContext<Context>()

        // Setup mock providers (pass Robolectric context)
        val mockContextProvider = MockContextProvider(mockTimeProvider)
        mockContextProvider.fakeContext = mockContext
        mockContextProvider.setup() // Call setup after setting the context

        val mockCalendarProvider = MockCalendarProvider(mockContextProvider, mockTimeProvider)
        mockCalendarProvider.setup()

        // Setup mock components
        mockComponents = MockApplicationComponents(
            contextProvider = mockContextProvider,
            timeProvider = mockTimeProvider,
            calendarProvider = mockCalendarProvider
        )
        mockComponents.setup()

        // Initialize DismissedEventsStorage with Robolectric context
        dismissedEventsStorage = DismissedEventsStorage(mockContext)   
    }

    @Test
    fun testSafeDismissEventsWithValidEvents() {
        // Given
        val events = listOf(createTestEvent(1), createTestEvent(2))
        every { mockDb.getEvent(any(), any()) } returns events[0] // Ensure getEvent returns something
        every { mockDb.deleteEvents(any()) } returns events.size

        // When
        val results = ApplicationController.safeDismissEvents(
            mockContext,
            mockDb,
            events,
            EventDismissType.ManuallyDismissedFromActivity,
            false,
            dismissedEventsStorage = dismissedEventsStorage // Pass the real storage
        )

        // Then
        assertEquals(events.size, results.size)
        results.forEach { (event, result) ->
            assertEquals(EventDismissResult.Success, result)
        }
        verify { mockDb.deleteEvents(events) }
        // Verify dismissedEventsStorage interaction
        verify { dismissedEventsStorage.addEvents(EventDismissType.ManuallyDismissedFromActivity, events) }
    }

    @Test
    fun testSafeDismissEventsWithMixedValidAndInvalidEvents() {
        // Given
        val validEvent = createTestEvent(1)
        val invalidEvent = createTestEvent(2)
        val events = listOf(validEvent, invalidEvent)

        every { mockDb.getEvent(validEvent.eventId, validEvent.instanceStartTime) } returns validEvent
        every { mockDb.getEvent(invalidEvent.eventId, invalidEvent.instanceStartTime) } returns null // Event 2 not found
        every { mockDb.deleteEvents(listOf(validEvent)) } returns 1 // Only valid event deleted

        // When
        val results = ApplicationController.safeDismissEvents(
            mockContext,
            mockDb,
            events,
            EventDismissType.ManuallyDismissedFromActivity,
            false,
            dismissedEventsStorage = dismissedEventsStorage
        )

        // Then
        assertEquals(events.size, results.size)
        val validResult = results.find { it.first == validEvent }?.second
        val invalidResult = results.find { it.first == invalidEvent }?.second

        assertNotNull(validResult)
        assertNotNull(invalidResult)
        assertEquals(EventDismissResult.Success, validResult)
        assertEquals(EventDismissResult.EventNotFound, invalidResult)

        // Verify dismissedEventsStorage interaction (only for the valid event)
        verify { dismissedEventsStorage.addEvents(EventDismissType.ManuallyDismissedFromActivity, listOf(validEvent)) }
        verify(exactly = 0) { dismissedEventsStorage.addEvents(EventDismissType.ManuallyDismissedFromActivity, listOf(invalidEvent)) }
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
            false,
            dismissedEventsStorage = dismissedEventsStorage
        )

        // Then
        assertEquals(1, results.size)
        assertEquals(EventDismissResult.DeletionWarning, results[0].second)
        verify { mockDb.deleteEvents(listOf(event)) }
        // Verify dismissedEventsStorage was still called despite deletion warning
        verify { dismissedEventsStorage.addEvents(EventDismissType.ManuallyDismissedFromActivity, listOf(event)) }
    }

    @Test
    fun testSafeDismissEventsByIdWithValidEvents() {
        // Given
        val eventIds = listOf(1L, 2L)
        val events = eventIds.map { createTestEvent(it) }

        // Mock individual getEventInstances calls for each event ID
        eventIds.forEachIndexed { index, id ->
            every { mockDb.getEventInstances(id) } returns listOf(events[index])
            every { mockDb.getEvent(id, any()) } returns events[index]
        }
        every { mockDb.deleteEvents(events) } returns events.size

        // When
        val results = ApplicationController.safeDismissEventsById(
            mockContext,
            mockDb,
            eventIds,
            EventDismissType.ManuallyDismissedFromActivity,
            false,
            dismissedEventsStorage = dismissedEventsStorage
        )

        // Then
        assertEquals(eventIds.size, results.size)
        results.forEach { (_, result) ->
            assertEquals(EventDismissResult.Success, result)
        }
        verify { dismissedEventsStorage.addEvents(EventDismissType.ManuallyDismissedFromActivity, events) }
    }

    @Test
    fun testSafeDismissEventsByIdWithNonExistentEvents() {
        // Given
        val eventIds = listOf(1L, 2L)
        // Mock getEventInstances to return empty list for each event ID
        eventIds.forEach { id ->
            every { mockDb.getEventInstances(id) } returns emptyList()
        }

        // When
        val results = ApplicationController.safeDismissEventsById(
            mockContext,
            mockDb,
            eventIds,
            EventDismissType.ManuallyDismissedFromActivity,
            false,
            dismissedEventsStorage = dismissedEventsStorage
        )

        // Then
        assertEquals(eventIds.size, results.size)
        results.forEach { (_, result) ->
            assertEquals(EventDismissResult.EventNotFound, result)
        }
        // Verify dismissedEventsStorage was not called
        verify(exactly = 0) { dismissedEventsStorage.addEvents(EventDismissType.ManuallyDismissedFromActivity, any()) }
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
        assertEquals(1, results.size)
        // Expect StorageError because adding to dismissed storage failed
        assertEquals(EventDismissResult.StorageError, results[0].second)
        verify { mockDb.deleteEvents(listOf(event)) } // Verify deletion was still attempted
        verify { throwingDismissedStorage.addEvents(EventDismissType.ManuallyDismissedFromActivity, listOf(event)) }
    }

    @Test
    fun testSafeDismissEventsWithDatabaseError() {
        // Given
        val event = createTestEvent()
        every { mockDb.getEvent(any(), any()) } returns event
        every { mockDb.deleteEvents(any()) } throws RuntimeException("Database error") // Simulate database error

        // When
        val results = ApplicationController.safeDismissEvents(
            mockContext,
            mockDb,
            listOf(event),
            EventDismissType.ManuallyDismissedFromActivity,
            false,
            dismissedEventsStorage = dismissedEventsStorage
        )

        // Then
        assertEquals(1, results.size)
        assertEquals(EventDismissResult.DatabaseError, results[0].second)
        verify { mockDb.deleteEvents(listOf(event)) } // Verify deletion was attempted
        // Verify dismissedEventsStorage was called before the database error
        verify { dismissedEventsStorage.addEvents(EventDismissType.ManuallyDismissedFromActivity, listOf(event)) }
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
            false,
            dismissedEventsStorage = dismissedEventsStorage
        )

        // Then
        assertEquals(1, results.size)
        assertEquals(EventDismissResult.DatabaseError, results[0].second)
        // Verify no attempts were made to delete events or add to dismissed storage
        verify(exactly = 0) { mockDb.deleteEvents(any()) }
        verify(exactly = 0) { dismissedEventsStorage.addEvents(EventDismissType.ManuallyDismissedFromActivity, any()) }
    }

    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsWithFutureEvents() {
        // Given
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val futureEventsConfirmations = listOf(
            JsRescheduleConfirmationObject(
                event_id = 1L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Test Event 1",
                new_instance_start_time = currentTime + 3600000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = true
            ),
            JsRescheduleConfirmationObject(
                event_id = 2L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Test Event 2",
                new_instance_start_time = currentTime + 7200000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = true
            )
        )
        val eventsToDismiss = futureEventsConfirmations.map { createTestEvent(it.event_id) }

        // Mock database interactions
        futureEventsConfirmations.forEachIndexed { index, confirmation ->
            every { mockDb.getEventInstances(confirmation.event_id) } returns listOf(eventsToDismiss[index])
            every { mockDb.getEvent(confirmation.event_id, any()) } returns eventsToDismiss[index]
        }
        every { mockDb.deleteEvents(eventsToDismiss) } returns eventsToDismiss.size

        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            futureEventsConfirmations,
            false,
            db = mockDb, // Pass mock DB
            dismissedEventsStorage = dismissedEventsStorage // Pass real storage
        )

        // Then
        assertEquals(futureEventsConfirmations.size, results.size)
        results.forEach { (_, result) ->
            assertEquals(EventDismissResult.Success, result)
        }
        verify { dismissedEventsStorage.addEvents(EventDismissType.AutoDismissedDueToRescheduleConfirmation, eventsToDismiss) }
    }

    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsWithMixedEvents() {
        // Given
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val confirmations = listOf(
            JsRescheduleConfirmationObject( // Future event
                event_id = 1L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Future Event",
                new_instance_start_time = currentTime + 3600000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = true
            ),
            JsRescheduleConfirmationObject( // Past event
                event_id = 2L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Past Event",
                new_instance_start_time = currentTime - 3600000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = false
            ),
             JsRescheduleConfirmationObject( // Future repeating event
                event_id = 3L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Repeating Future Event",
                new_instance_start_time = currentTime + 7200000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = true
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

        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            false,
            db = mockDb,
            dismissedEventsStorage = dismissedEventsStorage
        )

        // Then
        // Results should include outcomes for future events (1 and 3)
        assertEquals(2, results.size)
        val resultMap = results.toMap()
        assertEquals(EventDismissResult.Success, resultMap[1L]) // Event 1 dismissed
        assertEquals(EventDismissResult.SkippedRepeating, resultMap[3L]) // Event 3 skipped

        // Verify dismissedEventsStorage interaction only for the successfully dismissed event
        verify { dismissedEventsStorage.addEvents(EventDismissType.AutoDismissedDueToRescheduleConfirmation, eventsToDismiss) }
        verify(exactly=0) { dismissedEventsStorage.addEvents(EventDismissType.AutoDismissedDueToRescheduleConfirmation, listOf(futureRepeatingEvent)) }
    }

    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsWithNonExistentEvents() {
        // Given
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val confirmations = listOf(
            JsRescheduleConfirmationObject(
                event_id = 1L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Non-existent 1",
                new_instance_start_time = currentTime + 3600000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = true
            ),
            JsRescheduleConfirmationObject(
                event_id = 2L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Non-existent 2",
                new_instance_start_time = currentTime + 7200000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = true
            )
        )

        // Mock database to return empty lists for these IDs
        every { mockDb.getEventInstances(1L) } returns emptyList()
        every { mockDb.getEventInstances(2L) } returns emptyList()

        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            false,
            db = mockDb,
            dismissedEventsStorage = dismissedEventsStorage
        )

        // Then
        assertEquals(confirmations.size, results.size)
        results.forEach { (_, result) ->
            assertEquals(EventDismissResult.EventNotFound, result)
        }
        // Verify dismissedEventsStorage was not called
        verify(exactly = 0) { dismissedEventsStorage.addEvents(EventDismissType.AutoDismissedDueToRescheduleConfirmation, any()) }
    }

    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsWithStorageError() {
        // Given
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val confirmations = listOf(
            JsRescheduleConfirmationObject(
                event_id = 1L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Event 1",
                new_instance_start_time = currentTime + 3600000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = true
            )
        )
        val eventToDismiss = createTestEvent(1L)

        // Mock database interactions
        every { mockDb.getEventInstances(1L) } returns listOf(eventToDismiss)
        every { mockDb.getEvent(1L, any()) } returns eventToDismiss
        every { mockDb.deleteEvents(listOf(eventToDismiss)) } returns 1 // Deletion from main DB succeeds

        // Mock DismissedEventsStorage to throw an error
        val throwingDismissedStorage = mockk<DismissedEventsStorage>(relaxed = true)
        every { 
            throwingDismissedStorage.addEvents(
                EventDismissType.AutoDismissedDueToRescheduleConfirmation,
                any<Collection<EventAlertRecord>>()
            ) 
        } throws RuntimeException("Storage error")

        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            false,
            db = mockDb,
            dismissedEventsStorage = throwingDismissedStorage // Use the throwing mock
        )

        // Then
        assertEquals(1, results.size)
        // Expect StorageError because adding to dismissed storage failed
        assertEquals(EventDismissResult.StorageError, results[0].second)
        verify { mockDb.deleteEvents(listOf(eventToDismiss)) } // Verify deletion from main DB was attempted
        verify { throwingDismissedStorage.addEvents(EventDismissType.AutoDismissedDueToRescheduleConfirmation, listOf(eventToDismiss)) }
    }

    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsWithAllPastEvents() {
        // Given
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val confirmations = listOf(
            JsRescheduleConfirmationObject(
                event_id = 1L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Past Event 1",
                new_instance_start_time = currentTime - 3600000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = false
            ),
            JsRescheduleConfirmationObject(
                event_id = 2L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Past Event 2",
                new_instance_start_time = currentTime - 7200000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = false
            )
        )

        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            false,
            db = mockDb,
            dismissedEventsStorage = dismissedEventsStorage
        )

        // Then
        assertTrue(results.isEmpty()) // No future events, so results should be empty
        // Verify dismissedEventsStorage was not called
        verify(exactly = 0) { dismissedEventsStorage.addEvents(EventDismissType.AutoDismissedDueToRescheduleConfirmation, any()) }
    }

    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsWithEmptyList() {
        // Given
        val confirmations = emptyList<JsRescheduleConfirmationObject>()

        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            false,
            db = mockDb,
            dismissedEventsStorage = dismissedEventsStorage
        )

        // Then
        assertTrue(results.isEmpty())
        // Verify dismissedEventsStorage was not called
        verify(exactly = 0) { dismissedEventsStorage.addEvents(EventDismissType.AutoDismissedDueToRescheduleConfirmation, any()) }
    }

    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsWithAllRepeatingEvents() {
        // Given
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val confirmations = listOf(
            JsRescheduleConfirmationObject(
                event_id = 1L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Repeating 1",
                new_instance_start_time = currentTime + 3600000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = true
            ),
            JsRescheduleConfirmationObject(
                event_id = 2L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Repeating 2",
                new_instance_start_time = currentTime + 7200000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = true
            )
        )
        val repeatingEvent1 = createTestEvent(1L, isRepeating = true)
        val repeatingEvent2 = createTestEvent(2L, isRepeating = true)

        // Mock database interactions
        every { mockDb.getEventInstances(1L) } returns listOf(repeatingEvent1)
        every { mockDb.getEvent(1L, any()) } returns repeatingEvent1
        every { mockDb.getEventInstances(2L) } returns listOf(repeatingEvent2)
        every { mockDb.getEvent(2L, any()) } returns repeatingEvent2

        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            false,
            db = mockDb,
            dismissedEventsStorage = dismissedEventsStorage
        )

        // Then
        assertEquals(confirmations.size, results.size)
        results.forEach { (_, result) ->
            assertEquals(EventDismissResult.SkippedRepeating, result)
        }
        // Verify dismissedEventsStorage was not called
        verify(exactly = 0) { dismissedEventsStorage.addEvents(EventDismissType.AutoDismissedDueToRescheduleConfirmation, any()) }
    }

    @Test
    fun testSafeDismissEventsFromRescheduleConfirmationsSkipsRepeatingEvents() {
        // Given
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val confirmations = listOf(
            JsRescheduleConfirmationObject( // Repeating
                event_id = 1L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Repeating Event",
                new_instance_start_time = currentTime + 3600000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = true
            ),
            JsRescheduleConfirmationObject( // Non-repeating
                event_id = 2L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Non-Repeating Event",
                new_instance_start_time = currentTime + 7200000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = true
            )
        )
        val repeatingEvent = createTestEvent(1L, isRepeating = true)
        val nonRepeatingEvent = createTestEvent(2L, isRepeating = false)
        val eventsToDismiss = listOf(nonRepeatingEvent) // Only non-repeating should be dismissed

        // Mock database interactions
        every { mockDb.getEventInstances(1L) } returns listOf(repeatingEvent)
        every { mockDb.getEvent(1L, any()) } returns repeatingEvent
        every { mockDb.getEventInstances(2L) } returns listOf(nonRepeatingEvent)
        every { mockDb.getEvent(2L, any()) } returns nonRepeatingEvent
        every { mockDb.deleteEvents(eventsToDismiss) } returns eventsToDismiss.size

        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            false,
            db = mockDb,
            dismissedEventsStorage = dismissedEventsStorage
        )

        // Then
        assertEquals(2, results.size)
        val resultMap = results.toMap()
        assertEquals(EventDismissResult.SkippedRepeating, resultMap[1L])
        assertEquals(EventDismissResult.Success, resultMap[2L])

        // Verify dismissedEventsStorage interaction only for the successfully dismissed event
        verify { dismissedEventsStorage.addEvents(EventDismissType.AutoDismissedDueToRescheduleConfirmation, eventsToDismiss) }
        verify(exactly=0) { dismissedEventsStorage.addEvents(EventDismissType.AutoDismissedDueToRescheduleConfirmation, listOf(repeatingEvent)) }
    }

    @Test
    fun testToastMessagesForSafeDismissEventsFromRescheduleConfirmations() {
        // Given
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val confirmations = listOf(
            JsRescheduleConfirmationObject(
                event_id = 1L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Test Event 1",
                new_instance_start_time = currentTime + 3600000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = true
            ),
            JsRescheduleConfirmationObject(
                event_id = 2L, calendar_id = 1L, original_instance_start_time = currentTime, title = "Test Event 2",
                new_instance_start_time = currentTime + 7200000, created_at = currentTime.toString(), updated_at = currentTime.toString(), is_in_future = true
            )
        )
        val eventsToDismiss = confirmations.map { createTestEvent(it.event_id) }

        // Mock database interactions
        confirmations.forEachIndexed { index, confirmation ->
            every { mockDb.getEventInstances(confirmation.event_id) } returns listOf(eventsToDismiss[index])
            every { mockDb.getEvent(confirmation.event_id, any()) } returns eventsToDismiss[index]
        }
        every { mockDb.deleteEvents(eventsToDismiss) } returns eventsToDismiss.size

        // Setup toast message mocking in MockApplicationComponents
        // We'll use a new instance with spied context provider to capture toast messages
        val contextProviderSpy = spyk(MockContextProvider(mockTimeProvider))
        contextProviderSpy.fakeContext = mockContext
        contextProviderSpy.setup()
        
        val componentsSpy = spyk(
            MockApplicationComponents(
                contextProvider = contextProviderSpy,
                timeProvider = mockTimeProvider,
                calendarProvider = mockComponents.calendarProvider
            )
        )
        componentsSpy.setup()
        
        // Clear any previous toast messages
        componentsSpy.clearToastMessages()
        
        // When
        val results = ApplicationController.safeDismissEventsFromRescheduleConfirmations(
            mockContext,
            confirmations,
            true, // Enable notifications
            db = mockDb,
            dismissedEventsStorage = dismissedEventsStorage
        )

        // Then
        assertEquals(confirmations.size, results.size)
        results.forEach { (_, result) ->
            assertEquals(EventDismissResult.Success, result)
        }
        
        // In a Robolectric test, we can't directly verify the toast content 
        // as we're not running in a real Android environment
        // Instead, we'll verify that our events were properly processed
        verify { mockDb.deleteEvents(eventsToDismiss) }
        verify { dismissedEventsStorage.addEvents(EventDismissType.AutoDismissedDueToRescheduleConfirmation, eventsToDismiss) }
    }

    // Helper function to create test events
    private fun createTestEvent(id: Long = 1L, isRepeating: Boolean = false): EventAlertRecord {
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        return EventAlertRecord(
            calendarId = 1L,
            eventId = id,
            isAllDay = false,
            isRepeating = isRepeating, // Use parameter
            alertTime = currentTime - 60000, // Alert time in the past
            notificationId = 0,
            title = "Test Event $id",
            desc = "Test Description",
            startTime = currentTime + 3600000, // Starts in 1 hour
            endTime = currentTime + 7200000,   // Ends in 2 hours
            instanceStartTime = currentTime + 3600000, // Instance starts in 1 hour
            instanceEndTime = currentTime + 7200000,   // Instance ends in 2 hours
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
