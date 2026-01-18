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
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry

/**
 * Object Mother pattern for creating MonitorEventAlertEntry instances in tests.
 * 
 * Usage:
 * ```
 * // Simple unhandled alert
 * val alert = MonitorAlertMother.createDefault()
 * 
 * // Already handled alert
 * val handledAlert = MonitorAlertMother.createHandled()
 * 
 * // Pre-muted alert
 * val mutedAlert = MonitorAlertMother.createPreMuted()
 * ```
 */
object MonitorAlertMother {
    
    private const val DEFAULT_BASE_TIME = 1635724800000L // 2021-11-01 00:00:00 UTC
    
    /**
     * Creates a default MonitorEventAlertEntry with sensible test values.
     */
    fun createDefault(
        eventId: Long = 1L,
        isAllDay: Boolean = false,
        alertTime: Long = DEFAULT_BASE_TIME,
        instanceStartTime: Long = DEFAULT_BASE_TIME + Consts.HOUR_IN_MILLISECONDS,
        instanceEndTime: Long = DEFAULT_BASE_TIME + 2 * Consts.HOUR_IN_MILLISECONDS,
        alertCreatedByUs: Boolean = false,
        wasHandled: Boolean = false,
        flags: Long = 0L
    ) = MonitorEventAlertEntry(
        eventId = eventId,
        isAllDay = isAllDay,
        alertTime = alertTime,
        instanceStartTime = instanceStartTime,
        instanceEndTime = instanceEndTime,
        alertCreatedByUs = alertCreatedByUs,
        wasHandled = wasHandled,
        flags = flags
    )
    
    /**
     * Creates an alert that has already been handled.
     */
    fun createHandled(
        eventId: Long = 1L,
        alertTime: Long = DEFAULT_BASE_TIME
    ) = createDefault(
        eventId = eventId,
        alertTime = alertTime,
        wasHandled = true
    )
    
    /**
     * Creates an alert that is pre-muted.
     */
    fun createPreMuted(
        eventId: Long = 1L,
        alertTime: Long = DEFAULT_BASE_TIME
    ) = createDefault(
        eventId = eventId,
        alertTime = alertTime,
        flags = MonitorEventAlertEntry.PRE_MUTED_FLAG
    )
    
    /**
     * Creates an alert that was created by the app (not from provider).
     */
    fun createByUs(
        eventId: Long = 1L,
        alertTime: Long = DEFAULT_BASE_TIME
    ) = createDefault(
        eventId = eventId,
        alertTime = alertTime,
        alertCreatedByUs = true
    )
    
    /**
     * Creates an all-day event alert.
     */
    fun createAllDay(
        eventId: Long = 1L,
        alertTime: Long = DEFAULT_BASE_TIME,
        instanceStartTime: Long = DEFAULT_BASE_TIME
    ) = createDefault(
        eventId = eventId,
        isAllDay = true,
        alertTime = alertTime,
        instanceStartTime = instanceStartTime,
        instanceEndTime = instanceStartTime + Consts.DAY_IN_MILLISECONDS
    )
    
    /**
     * Creates an alert with specific timing relative to a base time.
     */
    fun createWithTiming(
        eventId: Long = 1L,
        baseTime: Long,
        alertOffsetMinutes: Int = 15,
        durationMinutes: Int = 60
    ): MonitorEventAlertEntry {
        val instanceStart = baseTime + alertOffsetMinutes * Consts.MINUTE_IN_MILLISECONDS
        val instanceEnd = instanceStart + durationMinutes * Consts.MINUTE_IN_MILLISECONDS
        val alertTime = instanceStart - alertOffsetMinutes * Consts.MINUTE_IN_MILLISECONDS
        
        return createDefault(
            eventId = eventId,
            alertTime = alertTime,
            instanceStartTime = instanceStart,
            instanceEndTime = instanceEnd
        )
    }
    
    /**
     * Creates multiple alerts with sequential event IDs.
     */
    fun createBatch(
        count: Int,
        startingEventId: Long = 1L,
        baseAlertTime: Long = DEFAULT_BASE_TIME,
        intervalMinutes: Int = 60
    ): List<MonitorEventAlertEntry> = (0 until count).map { index ->
        val offset = index * intervalMinutes * Consts.MINUTE_IN_MILLISECONDS
        createDefault(
            eventId = startingEventId + index,
            alertTime = baseAlertTime + offset,
            instanceStartTime = baseAlertTime + offset + Consts.HOUR_IN_MILLISECONDS,
            instanceEndTime = baseAlertTime + offset + 2 * Consts.HOUR_IN_MILLISECONDS
        )
    }
}
