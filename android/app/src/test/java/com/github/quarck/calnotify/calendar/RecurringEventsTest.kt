package com.github.quarck.calnotify.calendar

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

/**
 * Unit tests for recurring event expansion logic.
 */
class RecurringEventsTest {

    // Simple test clock implementation for deterministic time in tests
    class TestClock(private var currentTimeMillis: Long = 0) {
        fun getCurrentTimeMillis(): Long = currentTimeMillis
        
        fun setCurrentTime(timeMillis: Long) {
            currentTimeMillis = timeMillis
        }
        
        fun advanceBy(milliseconds: Long) {
            currentTimeMillis += milliseconds
        }
    }
    
    private lateinit var clock: TestClock
    private lateinit var recurrenceExpander: RecurrenceExpander
    
    @Before
    fun setup() {
        // Set fixed time for testing (2021-11-01)
        clock = TestClock(1635724800000)
        recurrenceExpander = RecurrenceExpander()
    }
    
    @Test
    fun `daily recurrence with count creates correct number of instances`() {
        // Given
        val baseEvent = createTestEvent(
            startTime = clock.getCurrentTimeMillis(),
            repeatingRule = "FREQ=DAILY;COUNT=5"
        )
        
        // When
        val instances = recurrenceExpander.expandRecurringEvent(
            baseEvent, 
            currentTimeMillis = clock.getCurrentTimeMillis()
        )
        
        // Then
        assertEquals("Should create exactly 5 instances", 5, instances.size)
        
        instances.forEachIndexed { index, instance ->
            val expectedStartTime = baseEvent.startTime + (index * 24 * 60 * 60 * 1000L)
            assertEquals("Instance $index should have correct startTime", expectedStartTime, instance.startTime)
            assertEquals("Instance should have correct eventId", baseEvent.eventId, instance.eventId)
            assertEquals("Instance should be marked as repeating", true, instance.isRepeating)
        }
    }
    
    @Test
    fun `weekly recurrence creates instances on correct days`() {
        // Given
        val baseEvent = createTestEvent(
            startTime = clock.getCurrentTimeMillis(),
            repeatingRule = "FREQ=WEEKLY;COUNT=4"
        )
        
        // When
        val instances = recurrenceExpander.expandRecurringEvent(
            baseEvent,
            currentTimeMillis = clock.getCurrentTimeMillis()
        )
        
        // Then
        assertEquals("Should create exactly 4 instances", 4, instances.size)
        
        instances.forEachIndexed { index, instance ->
            val expectedStartTime = baseEvent.startTime + (index * 7 * 24 * 60 * 60 * 1000L)
            assertEquals("Instance $index should have correct startTime", expectedStartTime, instance.startTime)
        }
    }
    
    @Test
    fun `monthly recurrence creates instances in correct months`() {
        // Given
        val baseEvent = createTestEvent(
            startTime = clock.getCurrentTimeMillis(),
            repeatingRule = "FREQ=MONTHLY;COUNT=3"
        )
        
        // When
        val instances = recurrenceExpander.expandRecurringEvent(
            baseEvent,
            currentTimeMillis = clock.getCurrentTimeMillis()
        )
        
        // Then
        assertEquals("Should create exactly 3 instances", 3, instances.size)
        
        // Verify instances are approximately 1 month apart
        instances.forEachIndexed { index, instance ->
            if (index > 0) {
                val previousInstance = instances[index - 1]
                val diff = instance.startTime - previousInstance.startTime
                
                // A month is approximately 30 days
                val approxOneMonth = 30 * 24 * 60 * 60 * 1000L
                
                // Allow some flexibility (28-31 days)
                assertTrue("Instances should be approximately 1 month apart", 
                    diff >= 28 * 24 * 60 * 60 * 1000L && diff <= 31 * 24 * 60 * 60 * 1000L)
            }
        }
    }
    
    @Test
    fun `recurrence with interval creates correctly spaced instances`() {
        // Given
        val baseEvent = createTestEvent(
            startTime = clock.getCurrentTimeMillis(),
            repeatingRule = "FREQ=DAILY;INTERVAL=2;COUNT=3" // Every 2 days, 3 instances
        )
        
        // When
        val instances = recurrenceExpander.expandRecurringEvent(
            baseEvent,
            currentTimeMillis = clock.getCurrentTimeMillis()
        )
        
        // Then
        assertEquals("Should create exactly 3 instances", 3, instances.size)
        
        instances.forEachIndexed { index, instance ->
            val expectedStartTime = baseEvent.startTime + (index * 2 * 24 * 60 * 60 * 1000L)
            assertEquals("Instance $index should have correct startTime", expectedStartTime, instance.startTime)
        }
    }
    
    private fun createTestEvent(
        eventId: Long = 1234L,
        calendarId: Long = 1L,
        startTime: Long,
        duration: Long = 3600000, // 1 hour
        title: String = "Test Event",
        repeatingRule: String
    ): EventRecord {
        return EventRecord(
            calendarId = calendarId,
            eventId = eventId,
            details = CalendarEventDetails(
                title = title,
                desc = "Test Description",
                location = "",
                timezone = "UTC",
                startTime = startTime,
                endTime = startTime + duration,
                isAllDay = false,
                reminders = listOf(EventReminderRecord(millisecondsBefore = 15 * 60 * 1000)),
                repeatingRule = repeatingRule,
                repeatingRDate = "",
                repeatingExRule = "",
                repeatingExRDate = "",
                color = 0xFF0000FF.toInt()
            ),
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None
        )
    }
} 