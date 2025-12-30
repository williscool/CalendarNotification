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
import androidx.room.Index
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry

/**
 * Room entity matching the manualAlertsV1 table schema.
 * 
 * Schema compatibility:
 * - Primary key columns (eventId, alertTime, instanceStart) are NOT NULL
 * - Other columns are nullable to match legacy schema
 * - Index matches legacy `manualAlertsV1IdxV1` index
 * 
 * The legacy database is migrated to add NOT NULL constraints on PK columns
 * before Room opens it. See MonitorDatabase.migrateLegacyDatabaseIfNeeded().
 */
@Entity(
    tableName = "manualAlertsV1",
    primaryKeys = ["eventId", "alertTime", "instanceStart"],
    indices = [Index(value = ["eventId", "alertTime", "instanceStart"], unique = true, name = "manualAlertsV1IdxV1")]
)
data class MonitorAlertEntity(
    // Nullable columns (match legacy schema without NOT NULL)
    @ColumnInfo(name = "calendarId") val calendarId: Long? = -1,
    // Primary key columns - NOT NULL (migration ensures this)
    @ColumnInfo(name = "eventId") val eventId: Long,
    @ColumnInfo(name = "alertTime") val alertTime: Long,
    @ColumnInfo(name = "instanceStart") val instanceStartTime: Long,
    // Other nullable columns
    @ColumnInfo(name = "instanceEnd") val instanceEndTime: Long? = 0,
    @ColumnInfo(name = "allDay") val isAllDay: Int? = 0,
    @ColumnInfo(name = "alertCreatedByUs") val alertCreatedByUs: Int? = 0,
    @ColumnInfo(name = "wasHandled") val wasHandled: Int? = 0,
    // Reserved columns
    @ColumnInfo(name = "i1") val reservedInt1: Long? = 0,
    @ColumnInfo(name = "i2") val reservedInt2: Long? = 0
) {
    /** Convert to domain model (handles potential nulls) */
    fun toAlertEntry() = MonitorEventAlertEntry(
        eventId = eventId,
        alertTime = alertTime,
        instanceStartTime = instanceStartTime,
        instanceEndTime = instanceEndTime ?: 0,
        isAllDay = (isAllDay ?: 0) != 0,
        alertCreatedByUs = (alertCreatedByUs ?: 0) != 0,
        wasHandled = (wasHandled ?: 0) != 0
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

