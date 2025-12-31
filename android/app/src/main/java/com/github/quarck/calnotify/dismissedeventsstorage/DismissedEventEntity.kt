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

package com.github.quarck.calnotify.dismissedeventsstorage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus

/**
 * Room entity matching the dismissedEventsV2 table schema.
 * 
 * Schema compatibility:
 * - Primary key columns (eventId, instanceStart) are NOT NULL
 * - Other columns are nullable to match legacy schema
 * - Index matches legacy `dismissedEventsIdxV2` index
 */
@Entity(
    tableName = DismissedEventEntity.TABLE_NAME,
    primaryKeys = [DismissedEventEntity.COL_EVENT_ID, DismissedEventEntity.COL_INSTANCE_START],
    indices = [Index(
        value = [DismissedEventEntity.COL_EVENT_ID, DismissedEventEntity.COL_INSTANCE_START],
        unique = true,
        name = DismissedEventEntity.INDEX_NAME
    )]
)
data class DismissedEventEntity(
    @ColumnInfo(name = COL_CALENDAR_ID) val calendarId: Long? = -1,
    @ColumnInfo(name = COL_EVENT_ID) val eventId: Long,
    @ColumnInfo(name = COL_DISMISS_TIME) val dismissTime: Long? = 0,
    @ColumnInfo(name = COL_DISMISS_TYPE) val dismissType: Int? = 0,
    @ColumnInfo(name = COL_ALERT_TIME) val alertTime: Long? = 0,
    @ColumnInfo(name = COL_TITLE) val title: String? = "",
    @ColumnInfo(name = COL_DESCRIPTION) val description: String? = "",
    @ColumnInfo(name = COL_START) val startTime: Long? = 0,
    @ColumnInfo(name = COL_END) val endTime: Long? = 0,
    @ColumnInfo(name = COL_INSTANCE_START) val instanceStartTime: Long,
    @ColumnInfo(name = COL_INSTANCE_END) val instanceEndTime: Long? = 0,
    @ColumnInfo(name = COL_LOCATION) val location: String? = "",
    @ColumnInfo(name = COL_SNOOZED_UNTIL) val snoozedUntil: Long? = 0,
    @ColumnInfo(name = COL_LAST_EVENT_VISIBILITY) val lastStatusChangeTime: Long? = 0,
    @ColumnInfo(name = COL_DISPLAY_STATUS) val displayStatus: Int? = 0,
    @ColumnInfo(name = COL_COLOR) val color: Int? = 0,
    @ColumnInfo(name = COL_IS_REPEATING) val isRepeating: Int? = 0,
    @ColumnInfo(name = COL_ALL_DAY) val isAllDay: Int? = 0,
    @ColumnInfo(name = COL_FLAGS) val flags: Long? = 0,
    // Reserved fields (match legacy schema)
    @ColumnInfo(name = COL_RESERVED_INT2) val reservedInt2: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_INT3) val reservedInt3: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_INT4) val reservedInt4: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_INT5) val reservedInt5: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_INT6) val reservedInt6: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_INT7) val reservedInt7: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_INT8) val reservedInt8: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_INT9) val reservedInt9: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_STR2) val reservedStr2: String? = "",
    @ColumnInfo(name = COL_RESERVED_STR3) val reservedStr3: String? = ""
) {
    /** Convert to domain model */
    fun toRecord() = DismissedEventAlertRecord(
        event = EventAlertRecord(
            calendarId = calendarId ?: -1L,
            eventId = eventId,
            alertTime = alertTime ?: 0,
            notificationId = 0,
            title = title ?: "",
            desc = description ?: "",
            startTime = startTime ?: 0,
            endTime = endTime ?: 0,
            instanceStartTime = instanceStartTime,
            instanceEndTime = instanceEndTime ?: 0,
            location = location ?: "",
            snoozedUntil = snoozedUntil ?: 0,
            lastStatusChangeTime = lastStatusChangeTime ?: 0,
            displayStatus = EventDisplayStatus.fromInt(displayStatus ?: 0),
            color = color ?: 0,
            isRepeating = (isRepeating ?: 0) != 0,
            isAllDay = (isAllDay ?: 0) != 0,
            flags = flags ?: 0
        ),
        dismissTime = dismissTime ?: 0,
        dismissType = EventDismissType.fromInt(dismissType ?: 0)
    )

    companion object {
        // Table and index names (must match legacy schema)
        const val TABLE_NAME = "dismissedEventsV2"
        const val INDEX_NAME = "dismissedEventsIdxV2"
        
        // Column names (must match legacy schema exactly)
        const val COL_CALENDAR_ID = "calendarId"
        const val COL_EVENT_ID = "eventId"
        const val COL_DISMISS_TIME = "dismissTime"
        const val COL_DISMISS_TYPE = "dismissType"
        const val COL_ALERT_TIME = "alertTime"
        const val COL_TITLE = "title"
        const val COL_DESCRIPTION = "s1"
        const val COL_START = "eventStart"
        const val COL_END = "eventEnd"
        const val COL_INSTANCE_START = "instanceStart"
        const val COL_INSTANCE_END = "instanceEnd"
        const val COL_LOCATION = "location"
        const val COL_SNOOZED_UNTIL = "snoozeUntil"
        const val COL_LAST_EVENT_VISIBILITY = "lastSeen"
        const val COL_DISPLAY_STATUS = "displayStatus"
        const val COL_COLOR = "color"
        const val COL_IS_REPEATING = "isRepeating"
        const val COL_ALL_DAY = "allDay"
        const val COL_FLAGS = "i1"
        const val COL_RESERVED_INT2 = "i2"
        const val COL_RESERVED_INT3 = "i3"
        const val COL_RESERVED_INT4 = "i4"
        const val COL_RESERVED_INT5 = "i5"
        const val COL_RESERVED_INT6 = "i6"
        const val COL_RESERVED_INT7 = "i7"
        const val COL_RESERVED_INT8 = "i8"
        const val COL_RESERVED_INT9 = "i9"
        const val COL_RESERVED_STR2 = "s2"
        const val COL_RESERVED_STR3 = "s3"

        /** Convert from domain model */
        fun fromRecord(record: DismissedEventAlertRecord) = DismissedEventEntity(
            calendarId = record.event.calendarId,
            eventId = record.event.eventId,
            dismissTime = record.dismissTime,
            dismissType = record.dismissType.code,
            alertTime = record.event.alertTime,
            title = record.event.title,
            description = record.event.desc,
            startTime = record.event.startTime,
            endTime = record.event.endTime,
            instanceStartTime = record.event.instanceStartTime,
            instanceEndTime = record.event.instanceEndTime,
            location = record.event.location,
            snoozedUntil = record.event.snoozedUntil,
            lastStatusChangeTime = record.event.lastStatusChangeTime,
            displayStatus = record.event.displayStatus.code,
            color = record.event.color,
            isRepeating = if (record.event.isRepeating) 1 else 0,
            isAllDay = if (record.event.isAllDay) 1 else 0,
            flags = record.event.flags
        )
        
        /** Convert from EventAlertRecord with dismiss info */
        fun fromEventRecord(event: EventAlertRecord, dismissTime: Long, dismissType: EventDismissType) = DismissedEventEntity(
            calendarId = event.calendarId,
            eventId = event.eventId,
            dismissTime = dismissTime,
            dismissType = dismissType.code,
            alertTime = event.alertTime,
            title = event.title,
            description = event.desc,
            startTime = event.startTime,
            endTime = event.endTime,
            instanceStartTime = event.instanceStartTime,
            instanceEndTime = event.instanceEndTime,
            location = event.location,
            snoozedUntil = event.snoozedUntil,
            lastStatusChangeTime = event.lastStatusChangeTime,
            displayStatus = event.displayStatus.code,
            color = event.color,
            isRepeating = if (event.isRepeating) 1 else 0,
            isAllDay = if (event.isAllDay) 1 else 0,
            flags = event.flags
        )
    }
}

