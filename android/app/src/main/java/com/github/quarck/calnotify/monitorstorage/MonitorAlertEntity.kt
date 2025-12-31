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
    tableName = MonitorAlertEntity.TABLE_NAME,
    primaryKeys = [MonitorAlertEntity.COL_EVENT_ID, MonitorAlertEntity.COL_ALERT_TIME, MonitorAlertEntity.COL_INSTANCE_START],
    indices = [Index(
        value = [MonitorAlertEntity.COL_EVENT_ID, MonitorAlertEntity.COL_ALERT_TIME, MonitorAlertEntity.COL_INSTANCE_START],
        unique = true,
        name = MonitorAlertEntity.INDEX_NAME
    )]
)
data class MonitorAlertEntity(
    @ColumnInfo(name = COL_CALENDAR_ID) val calendarId: Long? = -1,
    @ColumnInfo(name = COL_EVENT_ID) val eventId: Long,
    @ColumnInfo(name = COL_ALERT_TIME) val alertTime: Long,
    @ColumnInfo(name = COL_INSTANCE_START) val instanceStartTime: Long,
    @ColumnInfo(name = COL_INSTANCE_END) val instanceEndTime: Long? = 0,
    @ColumnInfo(name = COL_ALL_DAY) val isAllDay: Int? = 0,
    @ColumnInfo(name = COL_ALERT_CREATED_BY_US) val alertCreatedByUs: Int? = 0,
    @ColumnInfo(name = COL_WAS_HANDLED) val wasHandled: Int? = 0,
    @ColumnInfo(name = COL_RESERVED_INT1) val reservedInt1: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_INT2) val reservedInt2: Long? = 0
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
        // Table and index names
        const val TABLE_NAME = "manualAlertsV1"
        const val INDEX_NAME = "manualAlertsV1IdxV1"
        
        // Column names (must match legacy schema exactly)
        const val COL_CALENDAR_ID = "calendarId"
        const val COL_EVENT_ID = "eventId"
        const val COL_ALERT_TIME = "alertTime"
        const val COL_INSTANCE_START = "instanceStart"
        const val COL_INSTANCE_END = "instanceEnd"
        const val COL_ALL_DAY = "allDay"
        const val COL_ALERT_CREATED_BY_US = "alertCreatedByUs"
        const val COL_WAS_HANDLED = "wasHandled"
        const val COL_RESERVED_INT1 = "i1"
        const val COL_RESERVED_INT2 = "i2"

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

