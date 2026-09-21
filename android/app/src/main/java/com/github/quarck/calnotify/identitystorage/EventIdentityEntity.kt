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

package com.github.quarck.calnotify.identitystorage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.github.quarck.calnotify.calendar.CalendarBackupInfo

/**
 * Durable, provider-independent identity for one stored event.
 *
 * `eventsV9` stores `cid` and `id`, which are row numbers assigned by *this*
 * device's Calendar Provider. Restore the database onto a new phone and both
 * point at nothing. This table holds the information needed to find the same
 * event again from scratch, using only values that mean something on any
 * device: the calendar's account tuple, and the event's server-assigned id.
 *
 * See docs/dev_todo/portable_event_identity.md.
 *
 * **Column names are spelled out deliberately.** The abbreviations in
 * `eventsV9` (`cid`, `istart`, `attsts`) are a 2016 inheritance that is now
 * costly to change; this table is new and carries no such constraint. The
 * calendar columns map one-to-one onto the `CalendarContract.Calendars`
 * columns they are read from.
 */
@Entity(
    tableName = EventIdentityEntity.TABLE_NAME,
    primaryKeys = [
        EventIdentityEntity.COL_EVENT_ID,
        EventIdentityEntity.COL_INSTANCE_START_TIME
    ],
    indices = [
        // Resolution looks rows up by sync id, not by primary key.
        Index(value = [EventIdentityEntity.COL_EVENT_SYNC_ID],
              name = EventIdentityEntity.INDEX_SYNC_ID)
    ]
)
data class EventIdentityEntity(
    /** Joins to `eventsV9.id`. Changes when an event is re-keyed after a restore. */
    @ColumnInfo(name = COL_EVENT_ID) val eventId: Long,

    /** Joins to `eventsV9.istart`. Stable across devices for the same occurrence. */
    @ColumnInfo(name = COL_INSTANCE_START_TIME) val instanceStartTime: Long,

    @ColumnInfo(name = COL_CALENDAR_ACCOUNT_NAME) val calendarAccountName: String = "",
    @ColumnInfo(name = COL_CALENDAR_ACCOUNT_TYPE) val calendarAccountType: String = "",
    @ColumnInfo(name = COL_CALENDAR_OWNER_ACCOUNT) val calendarOwnerAccount: String = "",
    @ColumnInfo(name = COL_CALENDAR_DISPLAY_NAME) val calendarDisplayName: String = "",
    @ColumnInfo(name = COL_CALENDAR_NAME) val calendarName: String = "",

    /**
     * `Events._SYNC_ID` — the primary event identifier.
     *
     * Measured on a real device: populated and unique for 100% of 4761 events,
     * while [eventUid] was null for every one of them. Null here means the
     * event never synced to an account, which is the unresolvable case.
     */
    @ColumnInfo(name = COL_EVENT_SYNC_ID) val eventSyncId: String? = null,

    /**
     * `Events.UID_2445` — the iCalendar UID, read opportunistically.
     *
     * Null on Google Calendar (see https://issuetracker.google.com/issues/37053160),
     * but other providers may populate it, so it is captured rather than
     * depended upon.
     */
    @ColumnInfo(name = COL_EVENT_UID) val eventUid: String? = null,

    /**
     * The `cid`/`id` in force when this row was captured.
     *
     * These duplicate the event row on purpose: they are the staleness check.
     * If [originalEventId] still equals the event's current `id`, resolution
     * has not run; if they differ, it already has. Without them there is no way
     * to tell "never resolved" from "already resolved", which matters because
     * the retry pass re-runs on every launch.
     */
    @ColumnInfo(name = COL_ORIGINAL_CALENDAR_ID) val originalCalendarId: Long = -1L,
    @ColumnInfo(name = COL_ORIGINAL_EVENT_ID) val originalEventId: Long = -1L,

    /** Set via CNPlusClockInterface, never System.currentTimeMillis(). */
    @ColumnInfo(name = COL_CAPTURED_AT_TIME) val capturedAtTime: Long = 0L,

    /** Backs the retry cap, so a permanently unmatchable event stops re-querying. */
    @ColumnInfo(name = COL_RESOLUTION_ATTEMPT_COUNT) val resolutionAttemptCount: Int = 0,
    @ColumnInfo(name = COL_LAST_RESOLUTION_ATTEMPT_TIME) val lastResolutionAttemptTime: Long = 0L
) {
    /** True when there is any server-assigned id to resolve against. */
    fun hasUsableIdentifier(): Boolean =
        !eventSyncId.isNullOrBlank() || !eventUid.isNullOrBlank()

    /** The calendar half of the identity, in the shape the existing matcher takes. */
    fun toCalendarBackupInfo(): CalendarBackupInfo =
        CalendarBackupInfo(
            calendarId = originalCalendarId,
            accountName = calendarAccountName,
            accountType = calendarAccountType,
            ownerAccount = calendarOwnerAccount,
            displayName = calendarDisplayName,
            name = calendarName
        )

    companion object {
        const val TABLE_NAME = "eventIdentityV1"
        const val INDEX_SYNC_ID = "eventIdentityIdxSyncIdV1"

        const val COL_EVENT_ID = "eventId"
        const val COL_INSTANCE_START_TIME = "instanceStartTime"
        const val COL_CALENDAR_ACCOUNT_NAME = "calendarAccountName"
        const val COL_CALENDAR_ACCOUNT_TYPE = "calendarAccountType"
        const val COL_CALENDAR_OWNER_ACCOUNT = "calendarOwnerAccount"
        const val COL_CALENDAR_DISPLAY_NAME = "calendarDisplayName"
        const val COL_CALENDAR_NAME = "calendarName"
        const val COL_EVENT_SYNC_ID = "eventSyncId"
        const val COL_EVENT_UID = "eventUid"
        const val COL_ORIGINAL_CALENDAR_ID = "originalCalendarId"
        const val COL_ORIGINAL_EVENT_ID = "originalEventId"
        const val COL_CAPTURED_AT_TIME = "capturedAtTime"
        const val COL_RESOLUTION_ATTEMPT_COUNT = "resolutionAttemptCount"
        const val COL_LAST_RESOLUTION_ATTEMPT_TIME = "lastResolutionAttemptTime"

        /**
         * Build an identity row from a calendar's backup info plus the event's
         * server-assigned identifiers.
         */
        fun create(
            eventId: Long,
            instanceStartTime: Long,
            calendarId: Long,
            backupInfo: CalendarBackupInfo?,
            eventSyncId: String?,
            eventUid: String?,
            capturedAtTime: Long
        ) = EventIdentityEntity(
            eventId = eventId,
            instanceStartTime = instanceStartTime,
            calendarAccountName = backupInfo?.accountName ?: "",
            calendarAccountType = backupInfo?.accountType ?: "",
            calendarOwnerAccount = backupInfo?.ownerAccount ?: "",
            calendarDisplayName = backupInfo?.displayName ?: "",
            calendarName = backupInfo?.name ?: "",
            eventSyncId = eventSyncId,
            eventUid = eventUid,
            originalCalendarId = calendarId,
            originalEventId = eventId,
            capturedAtTime = capturedAtTime
        )
    }
}
