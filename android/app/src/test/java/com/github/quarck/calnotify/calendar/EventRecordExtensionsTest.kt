// 
//   Calendar Notifications Plus
//   Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//

package com.github.quarck.calnotify.calendar

import com.github.quarck.calnotify.Consts
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for EventRecord extension functions.
 * Tests getNextAlertTimeAfter which calculates the next reminder time after a given anchor.
 */
class EventRecordExtensionsTest {

    // Base time for tests: 2021-11-01 12:00:00 UTC
    private val baseTime = 1635768000000L
    
    private fun createTestEvent(
        startTime: Long = baseTime + Consts.HOUR_IN_MILLISECONDS,
        reminders: List<EventReminderRecord> = listOf(EventReminderRecord.minutes(15))
    ): EventRecord {
        return EventRecord(
            calendarId = 1L,
            eventId = 1L,
            details = CalendarEventDetails(
                title = "Test Event",
                desc = "Test Description",
                location = "",
                timezone = "UTC",
                startTime = startTime,
                endTime = startTime + Consts.HOUR_IN_MILLISECONDS,
                isAllDay = false,
                reminders = reminders,
                repeatingRule = "",
                repeatingRDate = "",
                repeatingExRule = "",
                repeatingExRDate = "",
                color = 0
            ),
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None
        )
    }

    @Test
    fun `getNextAlertTimeAfter returns null when no reminders`() {
        val event = createTestEvent(reminders = emptyList())
        val anchor = baseTime
        
        val result = event.getNextAlertTimeAfter(anchor)
        
        assertNull("Should return null when event has no reminders", result)
    }

    @Test
    fun `getNextAlertTimeAfter returns null when single reminder is before anchor`() {
        // Event starts 1 hour from baseTime, reminder is 15 minutes before
        val eventStartTime = baseTime + Consts.HOUR_IN_MILLISECONDS
        val event = createTestEvent(
            startTime = eventStartTime,
            reminders = listOf(EventReminderRecord.minutes(15))
        )
        // Reminder fires at: eventStartTime - 15min = baseTime + 45min
        // Anchor is after the reminder time
        val anchor = eventStartTime  // anchor at event start, reminder already fired
        
        val result = event.getNextAlertTimeAfter(anchor)
        
        assertNull("Should return null when reminder is before anchor", result)
    }

    @Test
    fun `getNextAlertTimeAfter returns reminder time when single reminder is after anchor`() {
        // Event starts 1 hour from baseTime, reminder is 15 minutes before
        val eventStartTime = baseTime + Consts.HOUR_IN_MILLISECONDS
        val event = createTestEvent(
            startTime = eventStartTime,
            reminders = listOf(EventReminderRecord.minutes(15))
        )
        // Reminder fires at: eventStartTime - 15min = baseTime + 45min
        val expectedReminderTime = eventStartTime - (15 * Consts.MINUTE_IN_MILLISECONDS)
        // Anchor is before the reminder time
        val anchor = baseTime
        
        val result = event.getNextAlertTimeAfter(anchor)
        
        assertNotNull("Should return reminder time when reminder is after anchor", result)
        assertEquals("Should return correct reminder time", expectedReminderTime, result)
    }

    @Test
    fun `getNextAlertTimeAfter returns max of future reminders when multiple reminders exist`() {
        // Event starts 2 hours from baseTime
        val eventStartTime = baseTime + 2 * Consts.HOUR_IN_MILLISECONDS
        // Reminders at: 60min before (baseTime + 1h), 30min before (baseTime + 1.5h), 15min before (baseTime + 1h45min)
        val event = createTestEvent(
            startTime = eventStartTime,
            reminders = listOf(
                EventReminderRecord.minutes(60),  // fires at baseTime + 1h
                EventReminderRecord.minutes(30),  // fires at baseTime + 1.5h  
                EventReminderRecord.minutes(15)   // fires at baseTime + 1h45min
            )
        )
        // Anchor is at baseTime + 1h (after the 60min reminder)
        val anchor = baseTime + Consts.HOUR_IN_MILLISECONDS
        
        // Expected: max of future reminders = 1h45min (15min before event)
        val expectedMaxFutureReminder = eventStartTime - (15 * Consts.MINUTE_IN_MILLISECONDS)
        
        val result = event.getNextAlertTimeAfter(anchor)
        
        assertNotNull("Should return max of future reminders", result)
        assertEquals("Should return the latest future reminder", expectedMaxFutureReminder, result)
    }

    @Test
    fun `getNextAlertTimeAfter returns null when all reminders are before anchor`() {
        // Event starts 1 hour from baseTime
        val eventStartTime = baseTime + Consts.HOUR_IN_MILLISECONDS
        val event = createTestEvent(
            startTime = eventStartTime,
            reminders = listOf(
                EventReminderRecord.minutes(60),  // fires at baseTime
                EventReminderRecord.minutes(30),  // fires at baseTime + 30min
                EventReminderRecord.minutes(15)   // fires at baseTime + 45min
            )
        )
        // Anchor is after all reminders (at event start time)
        val anchor = eventStartTime
        
        val result = event.getNextAlertTimeAfter(anchor)
        
        assertNull("Should return null when all reminders are before anchor", result)
    }

    @Test
    fun `getNextAlertTimeAfter handles anchor exactly at reminder time`() {
        // Event starts 1 hour from baseTime, reminder is 15 minutes before
        val eventStartTime = baseTime + Consts.HOUR_IN_MILLISECONDS
        val event = createTestEvent(
            startTime = eventStartTime,
            reminders = listOf(EventReminderRecord.minutes(15))
        )
        // Reminder fires at exactly: eventStartTime - 15min
        val reminderTime = eventStartTime - (15 * Consts.MINUTE_IN_MILLISECONDS)
        // Anchor is exactly at reminder time - should NOT include this reminder (filter is > not >=)
        val anchor = reminderTime
        
        val result = event.getNextAlertTimeAfter(anchor)
        
        assertNull("Should return null when anchor is exactly at reminder time", result)
    }

    @Test
    fun `getNextAlertTimeAfter with multiple reminders returns only future ones`() {
        // Event starts 2 hours from baseTime
        val eventStartTime = baseTime + 2 * Consts.HOUR_IN_MILLISECONDS
        val event = createTestEvent(
            startTime = eventStartTime,
            reminders = listOf(
                EventReminderRecord.minutes(90),  // fires at baseTime + 30min (PAST)
                EventReminderRecord.minutes(60),  // fires at baseTime + 1h (EXACTLY AT ANCHOR - excluded)
                EventReminderRecord.minutes(30),  // fires at baseTime + 1.5h (FUTURE)
                EventReminderRecord.minutes(15)   // fires at baseTime + 1h45min (FUTURE)
            )
        )
        // Anchor is at baseTime + 1h
        val anchor = baseTime + Consts.HOUR_IN_MILLISECONDS
        
        // Future reminders are: 30min before and 15min before
        // Max of those = 15min before = baseTime + 1h45min
        val expectedMaxFutureReminder = eventStartTime - (15 * Consts.MINUTE_IN_MILLISECONDS)
        
        val result = event.getNextAlertTimeAfter(anchor)
        
        assertNotNull("Should return a future reminder", result)
        assertEquals("Should return max of future reminders only", expectedMaxFutureReminder, result)
    }
}

