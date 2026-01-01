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

package com.github.quarck.calnotify.eventsstorage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus

/**
 * Room entity matching the eventsV9 table schema.
 * 
 * Schema compatibility:
 * - Primary key columns (id, istart) are NOT NULL
 * - Other columns are nullable to match legacy schema
 * - Index matches legacy `eventsIdxV9` index
 * - Column names use the shortened V9 format (cid, id, ttl, etc.)
 */
@Entity(
    tableName = EventAlertEntity.TABLE_NAME,
    primaryKeys = [EventAlertEntity.COL_EVENT_ID, EventAlertEntity.COL_INSTANCE_START],
    indices = [Index(
        value = [EventAlertEntity.COL_EVENT_ID, EventAlertEntity.COL_INSTANCE_START],
        unique = true,
        name = EventAlertEntity.INDEX_NAME
    )]
)
data class EventAlertEntity(
    @ColumnInfo(name = COL_CALENDAR_ID) val calendarId: Long? = -1,
    @ColumnInfo(name = COL_EVENT_ID) val eventId: Long,
    @ColumnInfo(name = COL_ALERT_TIME) val alertTime: Long? = 0,
    @ColumnInfo(name = COL_NOTIFICATION_ID) val notificationId: Int? = 0,
    @ColumnInfo(name = COL_TITLE) val title: String? = "",
    @ColumnInfo(name = COL_DESCRIPTION) val description: String? = "",
    @ColumnInfo(name = COL_START) val startTime: Long? = 0,
    @ColumnInfo(name = COL_END) val endTime: Long? = 0,
    @ColumnInfo(name = COL_INSTANCE_START) val instanceStartTime: Long,
    @ColumnInfo(name = COL_INSTANCE_END) val instanceEndTime: Long? = 0,
    @ColumnInfo(name = COL_LOCATION) val location: String? = "",
    @ColumnInfo(name = COL_SNOOZED_UNTIL) val snoozedUntil: Long? = 0,
    @ColumnInfo(name = COL_LAST_STATUS_CHANGE) val lastStatusChangeTime: Long? = 0,
    @ColumnInfo(name = COL_DISPLAY_STATUS) val displayStatus: Int? = 0,
    @ColumnInfo(name = COL_COLOR) val color: Int? = 0,
    @ColumnInfo(name = COL_IS_REPEATING) val isRepeating: Int? = 0,
    @ColumnInfo(name = COL_ALL_DAY) val isAllDay: Int? = 0,
    @ColumnInfo(name = COL_EVENT_ORIGIN) val origin: Int? = 0,
    @ColumnInfo(name = COL_TIME_FIRST_SEEN) val timeFirstSeen: Long? = 0,
    @ColumnInfo(name = COL_EVENT_STATUS) val eventStatus: Int? = 0,
    @ColumnInfo(name = COL_ATTENDANCE_STATUS) val attendanceStatus: Int? = 0,
    @ColumnInfo(name = COL_FLAGS) val flags: Long? = 0,
    // Reserved fields (match legacy schema)
    @ColumnInfo(name = COL_RESERVED_INT2) val reservedInt2: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_INT3) val reservedInt3: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_INT4) val reservedInt4: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_INT5) val reservedInt5: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_INT6) val reservedInt6: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_INT7) val reservedInt7: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_INT8) val reservedInt8: Long? = 0,
    @ColumnInfo(name = COL_RESERVED_STR2) val reservedStr2: String? = ""
) {
    /** Convert to domain model */
    fun toRecord() = EventAlertRecord(
        calendarId = calendarId ?: -1L,
        eventId = eventId,
        alertTime = alertTime ?: 0,
        notificationId = notificationId ?: 0,
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
        origin = EventOrigin.fromInt(origin ?: 0),
        timeFirstSeen = timeFirstSeen ?: 0,
        eventStatus = EventStatus.fromInt(eventStatus ?: 0),
        attendanceStatus = AttendanceStatus.fromInt(attendanceStatus ?: 0),
        flags = flags ?: 0
    )

    companion object {
        // Table and index names (must match legacy schema)
        const val TABLE_NAME = "eventsV9"
        const val INDEX_NAME = "eventsIdxV9"
        
        // Column names (must match legacy V9 schema - shortened names)
        const val COL_CALENDAR_ID = "cid"
        const val COL_EVENT_ID = "id"
        const val COL_ALERT_TIME = "altm"
        const val COL_NOTIFICATION_ID = "nid"
        const val COL_TITLE = "ttl"
        const val COL_DESCRIPTION = "s1"
        const val COL_START = "estart"
        const val COL_END = "eend"
        const val COL_INSTANCE_START = "istart"
        const val COL_INSTANCE_END = "iend"
        const val COL_LOCATION = "loc"
        const val COL_SNOOZED_UNTIL = "snz"
        const val COL_LAST_STATUS_CHANGE = "ls"
        const val COL_DISPLAY_STATUS = "dsts"
        const val COL_COLOR = "clr"
        const val COL_IS_REPEATING = "rep"
        const val COL_ALL_DAY = "alld"
        const val COL_EVENT_ORIGIN = "ogn"
        const val COL_TIME_FIRST_SEEN = "fsn"
        const val COL_EVENT_STATUS = "attsts"
        const val COL_ATTENDANCE_STATUS = "oattsts"
        const val COL_FLAGS = "i1"
        const val COL_RESERVED_INT2 = "i2"
        const val COL_RESERVED_INT3 = "i3"
        const val COL_RESERVED_INT4 = "i4"
        const val COL_RESERVED_INT5 = "i5"
        const val COL_RESERVED_INT6 = "i6"
        const val COL_RESERVED_INT7 = "i7"
        const val COL_RESERVED_INT8 = "i8"
        const val COL_RESERVED_STR2 = "s2"

        /** Convert from domain model */
        fun fromRecord(record: EventAlertRecord) = EventAlertEntity(
            calendarId = record.calendarId,
            eventId = record.eventId,
            alertTime = record.alertTime,
            notificationId = record.notificationId,
            title = record.title,
            description = record.desc,
            startTime = record.startTime,
            endTime = record.endTime,
            instanceStartTime = record.instanceStartTime,
            instanceEndTime = record.instanceEndTime,
            location = record.location,
            snoozedUntil = record.snoozedUntil,
            lastStatusChangeTime = record.lastStatusChangeTime,
            displayStatus = record.displayStatus.code,
            color = record.color,
            isRepeating = if (record.isRepeating) 1 else 0,
            isAllDay = if (record.isAllDay) 1 else 0,
            origin = record.origin.code,
            timeFirstSeen = record.timeFirstSeen,
            eventStatus = record.eventStatus.code,
            attendanceStatus = record.attendanceStatus.code,
            flags = record.flags
        )
    }
}

