//
//   Calendar Notifications Plus
//   Copyright (C) 2017 Sergey Parshin (s.parshin.sc@gmail.com)
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

data class MonitorEventAlertEntryKey(
        val eventId: Long,
        val alertTime: Long,
        val instanceStartTime: Long
)

data class MonitorEventAlertEntry(
        val eventId: Long,
        val isAllDay: Boolean,
        val alertTime: Long,
        val instanceStartTime: Long,
        val instanceEndTime: Long,
        var alertCreatedByUs: Boolean,
        var wasHandled: Boolean, // we should keep event alerts for a little bit longer to avoid double
        // alerting when reacting to different notification sources
        // (e.g. calendar provider vs our internal manual handler)
        val flags: Long = 0  // Stores preMuted and future flags, maps to DB column i1
) {
    val key: MonitorEventAlertEntryKey
        get() = MonitorEventAlertEntryKey(eventId, alertTime, instanceStartTime)

    /** True if this event should be muted when its notification fires */
    val preMuted: Boolean
        get() = (flags and PRE_MUTED_FLAG) != 0L

    /** Create a copy with preMuted flag set or cleared */
    fun withPreMuted(value: Boolean): MonitorEventAlertEntry {
        val newFlags = if (value) {
            flags or PRE_MUTED_FLAG
        } else {
            flags and PRE_MUTED_FLAG.inv()
        }
        return copy(flags = newFlags)
    }

    fun detailsChanged(other: MonitorEventAlertEntry): Boolean {
        return (eventId != other.eventId) ||
                (isAllDay != other.isAllDay) ||
                (alertTime != other.alertTime) ||
                (instanceStartTime != other.instanceStartTime) ||
                (instanceEndTime != other.instanceEndTime)
    }

    companion object {
        /** Flag indicating the event should be muted when it fires */
        const val PRE_MUTED_FLAG = 1L
    }
}
