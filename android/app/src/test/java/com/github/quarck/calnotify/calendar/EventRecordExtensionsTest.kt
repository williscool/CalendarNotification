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

    private fun reminderTime(eventStartTime: Long, minutes: Int): Long {
        return eventStartTime - (minutes * Consts.MINUTE_IN_MILLISECONDS)
    }
    
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

    private data class Case(
        val name: String,
        val startTime: Long,
        val reminders: List<EventReminderRecord>,
        val anchor: Long,
        val expected: Long?
    )

    @Test
    fun `getNextAlertTimeAfter selects the latest reminder after anchor`() {
        val oneHourFromBase = baseTime + Consts.HOUR_IN_MILLISECONDS
        val twoHoursFromBase = baseTime + 2 * Consts.HOUR_IN_MILLISECONDS
        val cases = listOf(
            Case(
                name = "no reminders",
                startTime = oneHourFromBase,
                reminders = emptyList(),
                anchor = baseTime,
                expected = null
            ),
            Case(
                name = "single reminder before anchor",
                startTime = oneHourFromBase,
                reminders = listOf(EventReminderRecord.minutes(15)),
                anchor = oneHourFromBase,
                expected = null
            ),
            Case(
                name = "single reminder after anchor",
                startTime = oneHourFromBase,
                reminders = listOf(EventReminderRecord.minutes(15)),
                anchor = baseTime,
                expected = reminderTime(oneHourFromBase, 15)
            ),
            Case(
                name = "all reminders before anchor",
                startTime = oneHourFromBase,
                reminders = listOf(
                    EventReminderRecord.minutes(60),
                    EventReminderRecord.minutes(30),
                    EventReminderRecord.minutes(15)
                ),
                anchor = oneHourFromBase,
                expected = null
            ),
            Case(
                name = "anchor exactly at reminder time",
                startTime = oneHourFromBase,
                reminders = listOf(EventReminderRecord.minutes(15)),
                anchor = reminderTime(oneHourFromBase, 15),
                expected = null
            ),
            Case(
                name = "mixed reminders keep latest future one",
                startTime = twoHoursFromBase,
                reminders = listOf(
                    EventReminderRecord.minutes(90),
                    EventReminderRecord.minutes(60),
                    EventReminderRecord.minutes(30),
                    EventReminderRecord.minutes(15)
                ),
                anchor = oneHourFromBase,
                expected = reminderTime(twoHoursFromBase, 15)
            )
        )

        for (case in cases) {
            val event = createTestEvent(startTime = case.startTime, reminders = case.reminders)
            val result = event.getNextAlertTimeAfter(case.anchor)

            assertEquals(case.name, case.expected, result)
        }
    }
}

