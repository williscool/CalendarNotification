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
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class EventDismissTest {
    private val LOG_TAG = "EventDismissTest"
    
    private lateinit var mockContext: Context
    private lateinit var mockDb: EventsStorageInterface
    private lateinit var mockComponents: MockApplicationComponents
    
    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up EventDismissTest")

        // Setup mock database
        mockDb = mockk<EventsStorageInterface>(relaxed = true)
        
        // Setup mock providers
        val mockTimeProvider = MockTimeProvider()
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
        every { EventsStorage(mockContext).getEvent(event.eventId, event.instanceStartTime) } returns event
        every { EventsStorage(mockContext).deleteEvent(event.eventId, event.instanceStartTime) } returns true
        
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
        verify { EventsStorage(mockContext).getEvent(event.eventId, event.instanceStartTime) }
        verify { EventsStorage(mockContext).deleteEvent(event.eventId, event.instanceStartTime) }
    }
    
    @Test
    fun testOriginalDismissEventWithNonExistentEvent() {
        // Given
        val event = createTestEvent()
        every { EventsStorage(mockContext).getEvent(event.eventId, event.instanceStartTime) } returns null
        
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
        verify { EventsStorage(mockContext).getEvent(event.eventId, event.instanceStartTime) }
        verify(exactly = 0) { EventsStorage(mockContext).deleteEvent(any(), any()) }
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
        assertEquals(1, results.size)
        assertEquals(EventDismissResult.DatabaseError, results[0].second)
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
