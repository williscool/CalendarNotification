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

package com.github.quarck.calnotify.monitorstorage

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry

/**
 * Room entity matching the existing manualAlertsV1 table schema.
 * 
 * Column names must match exactly for Room to read existing data.
 * See MonitorStorageImplV1 for the original schema definition.
 */
@Entity(
    tableName = "manualAlertsV1",
    primaryKeys = ["eventId", "alertTime", "instanceStart"]
)
data class MonitorAlertEntity(
    @ColumnInfo(name = "calendarId") val calendarId: Long = -1,
    @ColumnInfo(name = "eventId") val eventId: Long,
    @ColumnInfo(name = "alertTime") val alertTime: Long,
    @ColumnInfo(name = "instanceStart") val instanceStartTime: Long,
    @ColumnInfo(name = "instanceEnd") val instanceEndTime: Long,
    @ColumnInfo(name = "allDay") val isAllDay: Int,
    @ColumnInfo(name = "alertCreatedByUs") val alertCreatedByUs: Int,
    @ColumnInfo(name = "wasHandled") val wasHandled: Int,
    // Reserved columns - must be included to match existing schema
    @ColumnInfo(name = "i1") val reservedInt1: Long = 0,
    @ColumnInfo(name = "i2") val reservedInt2: Long = 0
) {
    /** Convert to domain model */
    fun toAlertEntry() = MonitorEventAlertEntry(
        eventId = eventId,
        alertTime = alertTime,
        instanceStartTime = instanceStartTime,
        instanceEndTime = instanceEndTime,
        isAllDay = isAllDay != 0,
        alertCreatedByUs = alertCreatedByUs != 0,
        wasHandled = wasHandled != 0
    )

    companion object {
        /** Convert from domain model */
        fun fromAlertEntry(entry: MonitorEventAlertEntry) = MonitorAlertEntity(
            eventId = entry.eventId,
            alertTime = entry.alertTime,
            instanceStartTime = entry.instanceStartTime,
            instanceEndTime = entry.instanceEndTime,
            isAllDay = if (entry.isAllDay) 1 else 0,
            alertCreatedByUs = if (entry.alertCreatedByUs) 1 else 0,
            wasHandled = if (entry.wasHandled) 1 else 0
        )
    }
}

