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

package com.github.quarck.calnotify.testutils

import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus

/**
 * Object Mother pattern for creating EventAlertRecord instances in tests.
 * 
 * Provides factory methods for common test scenarios, reducing boilerplate
 * and ensuring consistent test data creation across the test suite.
 * 
 * Usage:
 * ```
 * // Simple default event
 * val event = EventMother.createDefault()
 * 
 * // Muted event
 * val mutedEvent = EventMother.createMuted()
 * 
 * // Custom event using builder-style
 * val customEvent = EventMother.createDefault(
 *     eventId = 42L,
 *     title = "Team Meeting"
 * )
 * 
 * // Snoozed event
 * val snoozedEvent = EventMother.createSnoozed(until = System.currentTimeMillis() + 3600000)
 * ```
 */
object EventMother {
    
    // Default base time for tests (can be overridden)
    private const val DEFAULT_BASE_TIME = 1635724800000L // 2021-11-01 00:00:00 UTC
    
    /**
     * Creates a default EventAlertRecord with sensible test values.
     * All parameters can be overridden for specific test needs.
     */
    fun createDefault(
        calendarId: Long = 1L,
        eventId: Long = 1L,
        isAllDay: Boolean = false,
        isRepeating: Boolean = false,
        alertTime: Long = DEFAULT_BASE_TIME,
        notificationId: Int = 1,
        title: String = "Test Event",
        desc: String = "Test Description",
        startTime: Long = DEFAULT_BASE_TIME + Consts.HOUR_IN_MILLISECONDS,
        endTime: Long = DEFAULT_BASE_TIME + 2 * Consts.HOUR_IN_MILLISECONDS,
        instanceStartTime: Long = DEFAULT_BASE_TIME + Consts.HOUR_IN_MILLISECONDS,
        instanceEndTime: Long = DEFAULT_BASE_TIME + 2 * Consts.HOUR_IN_MILLISECONDS,
        location: String = "",
        lastStatusChangeTime: Long = DEFAULT_BASE_TIME,
        snoozedUntil: Long = 0L,
        displayStatus: EventDisplayStatus = EventDisplayStatus.Hidden,
        color: Int = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
        origin: EventOrigin = EventOrigin.ProviderBroadcast,
        timeFirstSeen: Long = DEFAULT_BASE_TIME,
        eventStatus: EventStatus = EventStatus.Confirmed,
        attendanceStatus: AttendanceStatus = AttendanceStatus.None,
        flags: Long = 0L
    ) = EventAlertRecord(
        calendarId = calendarId,
        eventId = eventId,
        isAllDay = isAllDay,
        isRepeating = isRepeating,
        alertTime = alertTime,
        notificationId = notificationId,
        title = title,
        desc = desc,
        startTime = startTime,
        endTime = endTime,
        instanceStartTime = instanceStartTime,
        instanceEndTime = instanceEndTime,
        location = location,
        lastStatusChangeTime = lastStatusChangeTime,
        snoozedUntil = snoozedUntil,
        displayStatus = displayStatus,
        color = color,
        origin = origin,
        timeFirstSeen = timeFirstSeen,
        eventStatus = eventStatus,
        attendanceStatus = attendanceStatus,
        flags = flags
    )
    
    /**
     * Creates a muted event (won't trigger sound/vibration).
     */
    fun createMuted(
        eventId: Long = 1L,
        title: String = "Muted Event"
    ) = createDefault(eventId = eventId, title = title).also { 
        it.isMuted = true 
    }
    
    /**
     * Creates a task event (different handling than regular calendar events).
     */
    fun createTask(
        eventId: Long = 1L,
        title: String = "Task Event"
    ) = createDefault(eventId = eventId, title = title).also { 
        it.isTask = true 
    }
    
    /**
     * Creates an alarm event (overrides quiet hours).
     */
    fun createAlarm(
        eventId: Long = 1L,
        title: String = "Alarm Event"
    ) = createDefault(eventId = eventId, title = title).also { 
        it.isAlarm = true 
    }
    
    /**
     * Creates a snoozed event.
     */
    fun createSnoozed(
        eventId: Long = 1L,
        until: Long,
        title: String = "Snoozed Event"
    ) = createDefault(eventId = eventId, title = title, snoozedUntil = until)
    
    /**
     * Creates an all-day event.
     */
    fun createAllDay(
        eventId: Long = 1L,
        title: String = "All Day Event",
        startTime: Long = DEFAULT_BASE_TIME
    ) = createDefault(
        eventId = eventId,
        title = title,
        isAllDay = true,
        startTime = startTime,
        endTime = startTime + Consts.DAY_IN_MILLISECONDS,
        instanceStartTime = startTime,
        instanceEndTime = startTime + Consts.DAY_IN_MILLISECONDS
    )
    
    /**
     * Creates a repeating event.
     */
    fun createRepeating(
        eventId: Long = 1L,
        title: String = "Repeating Event"
    ) = createDefault(eventId = eventId, title = title, isRepeating = true)
    
    /**
     * Creates an event with specific timing relative to a base time.
     * Useful for tests that need precise time control.
     */
    fun createWithTiming(
        eventId: Long = 1L,
        baseTime: Long,
        alertOffsetMinutes: Int = 15,
        durationMinutes: Int = 60,
        title: String = "Timed Event"
    ): EventAlertRecord {
        val startTime = baseTime + alertOffsetMinutes * Consts.MINUTE_IN_MILLISECONDS
        val endTime = startTime + durationMinutes * Consts.MINUTE_IN_MILLISECONDS
        val alertTime = startTime - alertOffsetMinutes * Consts.MINUTE_IN_MILLISECONDS
        
        return createDefault(
            eventId = eventId,
            title = title,
            alertTime = alertTime,
            startTime = startTime,
            endTime = endTime,
            instanceStartTime = startTime,
            instanceEndTime = endTime,
            lastStatusChangeTime = baseTime,
            timeFirstSeen = baseTime
        )
    }
    
    /**
     * Creates a cancelled event.
     */
    fun createCancelled(
        eventId: Long = 1L,
        title: String = "Cancelled Event"
    ) = createDefault(
        eventId = eventId, 
        title = title, 
        eventStatus = EventStatus.Cancelled
    )
    
    /**
     * Creates a tentative event.
     */
    fun createTentative(
        eventId: Long = 1L,
        title: String = "Tentative Event"
    ) = createDefault(
        eventId = eventId, 
        title = title, 
        eventStatus = EventStatus.Tentative
    )
    
    /**
     * Creates an event with declined attendance.
     */
    fun createDeclined(
        eventId: Long = 1L,
        title: String = "Declined Event"
    ) = createDefault(
        eventId = eventId, 
        title = title, 
        attendanceStatus = AttendanceStatus.Declined
    )
    
    /**
     * Creates multiple events with sequential IDs.
     * Useful for tests that need a batch of events.
     */
    fun createBatch(
        count: Int,
        startingEventId: Long = 1L,
        titlePrefix: String = "Event"
    ): List<EventAlertRecord> = (0 until count).map { index ->
        createDefault(
            eventId = startingEventId + index,
            notificationId = (startingEventId + index).toInt(),
            title = "$titlePrefix ${index + 1}"
        )
    }
}
