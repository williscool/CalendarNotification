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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room DAO for DismissedEventEntity.
 * 
 * Provides all database operations needed by DismissedEventsStorageInterface.
 */
@Dao
interface DismissedEventDao {
    
    @Query("SELECT * FROM ${DismissedEventEntity.TABLE_NAME} ORDER BY ${DismissedEventEntity.COL_DISMISS_TIME} DESC")
    fun getAll(): List<DismissedEventEntity>

    @Query("SELECT COUNT(*) FROM ${DismissedEventEntity.TABLE_NAME}")
    fun count(): Int

    /**
     * Primary keys only, unsorted.
     *
     * For callers that need to know *which* dismissed events exist without
     * reading them. [getAll] is `SELECT *` plus an `ORDER BY`, and this table is
     * the largest the app keeps -- measured at 4183 rows against 373 active
     * events -- so reading whole rows to look at two columns costs a full row
     * scan, a sort, and an entity mapping per row, most of it then discarded.
     *
     * Used by portable-identity capture, which runs every 30 minutes on a
     * wake-locked service and only needs the keys it has not captured yet.
     * See docs/dev_todo/portable_event_identity.md.
     */
    @Query("SELECT ${DismissedEventEntity.COL_EVENT_ID}, " +
           "${DismissedEventEntity.COL_INSTANCE_START} " +
           "FROM ${DismissedEventEntity.TABLE_NAME}")
    fun getAllKeys(): List<DismissedEventKey>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: DismissedEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<DismissedEventEntity>)

    @Delete
    fun delete(entity: DismissedEventEntity)

    @Query("DELETE FROM ${DismissedEventEntity.TABLE_NAME} WHERE ${DismissedEventEntity.COL_EVENT_ID} = :eventId AND ${DismissedEventEntity.COL_INSTANCE_START} = :instanceStart")
    fun deleteByKey(eventId: Long, instanceStart: Long)

    @Query("DELETE FROM ${DismissedEventEntity.TABLE_NAME}")
    fun deleteAll()

    @Query("DELETE FROM ${DismissedEventEntity.TABLE_NAME} WHERE ${DismissedEventEntity.COL_DISMISS_TIME} < :cutoffTime")
    fun deleteOlderThan(cutoffTime: Long)

    /**
     * Full rows for a set of keys.
     *
     * The companion to [getAllKeys]: having decided which dismissed events are
     * of interest, read only those.
     *
     * **Callers must batch.** Results come back through a CursorWindow, a 2 MB
     * buffer; overflow it and the read throws `SQLiteBlobTooBigException: Row
     * too big to fit into CursorWindow`. An unbounded key list would therefore
     * fail on exactly the devices with the most history, from a background
     * service. `RoomDismissedEventsStorage` batches at `MAX_EVENTS_PER_BATCH`
     * -- see that constant for the row-size measurements it is derived from.
     *
     * Filters on `eventId` alone rather than the full key; an event with several
     * dismissed occurrences returns all of them, which the caller narrows. That
     * keeps the query to one bound list instead of a synthesised `OR` chain.
     */
    @Query("SELECT * FROM ${DismissedEventEntity.TABLE_NAME} " +
           "WHERE ${DismissedEventEntity.COL_EVENT_ID} IN (:eventIds)")
    fun getByEventIds(eventIds: List<Long>): List<DismissedEventEntity>
}

/**
 * A dismissed event's primary key, without the rest of the row.
 *
 * Room matches the projection's columns onto these properties by name. The
 * columns are already called eventId/instanceStart, so the query needs no
 * aliases -- add one only if a column is renamed away from its property.
 */
data class DismissedEventKey(
    val eventId: Long,
    val instanceStart: Long
)

