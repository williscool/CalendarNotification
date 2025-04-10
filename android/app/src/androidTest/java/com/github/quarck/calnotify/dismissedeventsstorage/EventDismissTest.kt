package com.github.quarck.calnotify.dismissedeventsstorage

import android.content.Context
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.AttendanceStatus
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EventDismissTest {
    private val LOG_TAG = "EventDismissTest"
    
    private lateinit var mockContext: Context
    private lateinit var mockDb: EventsStorageInterface
    private lateinit var mockComponents: MockApplicationComponents
    
    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up EventDismissTest")
        
        // Setup mock context
        mockContext = mockk<Context>(relaxed = true)
        
        // Setup mock database
        mockDb = mockk<EventsStorageInterface>(relaxed = true)
        
        // Setup mock components
        mockComponents = MockApplicationComponents(
            MockContextProvider(mockContext),
            MockTimeProvider(),
            MockCalendarProvider()
        )
        mockComponents.setup()
    }
    
    @Test
    fun `test original dismissEvent with valid event`() {
        // Given
        val event = createTestEvent()
        every { mockDb.getEvent(event.eventId, event.instanceStartTime) } returns event
        every { mockDb.deleteEvent(event.eventId, event.instanceStartTime) } returns true
        
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
        verify { mockDb.getEvent(event.eventId, event.instanceStartTime) }
        verify { mockDb.deleteEvent(event.eventId, event.instanceStartTime) }
    }
    
    @Test
    fun `test original dismissEvent with non-existent event`() {
        // Given
        val event = createTestEvent()
        every { mockDb.getEvent(event.eventId, event.instanceStartTime) } returns null
        
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
        verify { mockDb.getEvent(event.eventId, event.instanceStartTime) }
        verify(exactly = 0) { mockDb.deleteEvent(any(), any()) }
    }
    
    @Test
    fun `test safeDismissEvents with valid events`() {
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
    fun `test safeDismissEvents with mixed valid and invalid events`() {
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
        val validResult = results.find { it.first == validEvent }
        val invalidResult = results.find { it.first == invalidEvent }
        
        assertNotNull(validResult)
        assertNotNull(invalidResult)
        assertEquals(EventDismissResult.Success, validResult.second)
        assertEquals(EventDismissResult.EventNotFound, invalidResult.second)
    }
    
    @Test
    fun `test safeDismissEvents by ID with valid events`() {
        // Given
        val eventIds = listOf(1L, 2L)
        val events = eventIds.map { createTestEvent(it) }
        
        every { mockDb.getEventInstances(any()) } returns events
        every { mockDb.getEvent(any(), any()) } returns events[0]
        every { mockDb.deleteEvents(any()) } returns events.size
        
        // When
        val results = ApplicationController.safeDismissEvents(
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
    fun `test safeDismissEvents by ID with non-existent events`() {
        // Given
        val eventIds = listOf(1L, 2L)
        every { mockDb.getEventInstances(any()) } returns emptyList()
        
        // When
        val results = ApplicationController.safeDismissEvents(
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
    fun `test safeDismissEvents with database error`() {
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