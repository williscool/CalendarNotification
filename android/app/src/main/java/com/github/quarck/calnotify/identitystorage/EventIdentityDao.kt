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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room DAO for [EventIdentityEntity].
 *
 * Note: Room @Query annotations require string literals, so the table and
 * column names are interpolated from the entity's constants. They are
 * validated at compile time against the @Entity definition.
 */
@Dao
interface EventIdentityDao {

    @Query("SELECT * FROM ${EventIdentityEntity.TABLE_NAME}")
    fun getAll(): List<EventIdentityEntity>

    @Query("SELECT COUNT(*) FROM ${EventIdentityEntity.TABLE_NAME}")
    fun count(): Int

    @Query(
        "SELECT * FROM ${EventIdentityEntity.TABLE_NAME} " +
        "WHERE ${EventIdentityEntity.COL_EVENT_ID} = :eventId " +
        "AND ${EventIdentityEntity.COL_INSTANCE_START_TIME} = :instanceStartTime"
    )
    fun getByKey(eventId: Long, instanceStartTime: Long): EventIdentityEntity?

    @Query(
        "SELECT * FROM ${EventIdentityEntity.TABLE_NAME} " +
        "WHERE ${EventIdentityEntity.COL_EVENT_ID} = :eventId"
    )
    fun getByEventId(eventId: Long): List<EventIdentityEntity>

    /**
     * Rows whose identity has not been re-resolved yet on this device.
     *
     * "Not yet resolved" means the event id still matches what was captured.
     * Once resolution rewrites the key, the two diverge and the row drops out.
     * Capped by attempt count so a permanently unmatchable event stops being
     * retried forever.
     */
    @Query(
        "SELECT * FROM ${EventIdentityEntity.TABLE_NAME} " +
        "WHERE ${EventIdentityEntity.COL_EVENT_ID} = ${EventIdentityEntity.COL_ORIGINAL_EVENT_ID} " +
        "AND ${EventIdentityEntity.COL_RESOLUTION_ATTEMPT_COUNT} < :maxAttempts"
    )
    fun getUnresolved(maxAttempts: Int): List<EventIdentityEntity>

    /** REPLACE: re-capturing an event's identity should overwrite, not fail. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(entity: EventIdentityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putAll(entities: List<EventIdentityEntity>)

    @Query(
        "DELETE FROM ${EventIdentityEntity.TABLE_NAME} " +
        "WHERE ${EventIdentityEntity.COL_EVENT_ID} = :eventId " +
        "AND ${EventIdentityEntity.COL_INSTANCE_START_TIME} = :instanceStartTime"
    )
    fun deleteByKey(eventId: Long, instanceStartTime: Long): Int

    @Query("DELETE FROM ${EventIdentityEntity.TABLE_NAME}")
    fun deleteAllRows()

    /**
     * Move an identity row onto a new event id, keeping the same occurrence.
     *
     * Used by the re-key step: the event's row in `eventsV9` is deleted and
     * re-inserted under the resolved id, and its identity row has to follow or
     * the next retry pass would not find it.
     */
    @Query(
        "UPDATE ${EventIdentityEntity.TABLE_NAME} " +
        "SET ${EventIdentityEntity.COL_EVENT_ID} = :newEventId " +
        "WHERE ${EventIdentityEntity.COL_EVENT_ID} = :oldEventId " +
        "AND ${EventIdentityEntity.COL_INSTANCE_START_TIME} = :instanceStartTime"
    )
    fun reKey(oldEventId: Long, instanceStartTime: Long, newEventId: Long): Int

    /** Record a resolution attempt, so the retry cap and backoff can apply. */
    @Query(
        "UPDATE ${EventIdentityEntity.TABLE_NAME} " +
        "SET ${EventIdentityEntity.COL_RESOLUTION_ATTEMPT_COUNT} = " +
            "${EventIdentityEntity.COL_RESOLUTION_ATTEMPT_COUNT} + 1, " +
            "${EventIdentityEntity.COL_LAST_RESOLUTION_ATTEMPT_TIME} = :attemptTime " +
        "WHERE ${EventIdentityEntity.COL_EVENT_ID} = :eventId " +
        "AND ${EventIdentityEntity.COL_INSTANCE_START_TIME} = :instanceStartTime"
    )
    fun recordResolutionAttempt(eventId: Long, instanceStartTime: Long, attemptTime: Long): Int
}
