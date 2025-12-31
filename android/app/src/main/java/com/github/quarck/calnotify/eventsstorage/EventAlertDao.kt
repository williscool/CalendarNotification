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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Room DAO for EventAlertEntity.
 * 
 * Provides all database operations needed by EventsStorageInterface.
 */
@Dao
interface EventAlertDao {
    
    @Query("SELECT * FROM ${EventAlertEntity.TABLE_NAME}")
    fun getAll(): List<EventAlertEntity>

    @Query("SELECT * FROM ${EventAlertEntity.TABLE_NAME} WHERE ${EventAlertEntity.COL_EVENT_ID} = :eventId AND ${EventAlertEntity.COL_INSTANCE_START} = :instanceStart")
    fun getByKey(eventId: Long, instanceStart: Long): EventAlertEntity?

    @Query("SELECT * FROM ${EventAlertEntity.TABLE_NAME} WHERE ${EventAlertEntity.COL_EVENT_ID} = :eventId")
    fun getByEventId(eventId: Long): List<EventAlertEntity>

    @Query("SELECT MAX(${EventAlertEntity.COL_NOTIFICATION_ID}) FROM ${EventAlertEntity.TABLE_NAME}")
    fun getMaxNotificationId(): Int?

    // ABORT matches legacy insertOrThrow behavior - duplicates are handled explicitly in RoomEventsStorage
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: EventAlertEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAll(entities: List<EventAlertEntity>): List<Long>

    @Update
    fun update(entity: EventAlertEntity): Int

    @Update
    fun updateAll(entities: List<EventAlertEntity>): Int

    @Delete
    fun delete(entity: EventAlertEntity): Int

    @Delete
    fun deleteAll(entities: List<EventAlertEntity>): Int

    @Query("DELETE FROM ${EventAlertEntity.TABLE_NAME} WHERE ${EventAlertEntity.COL_EVENT_ID} = :eventId AND ${EventAlertEntity.COL_INSTANCE_START} = :instanceStart")
    fun deleteByKey(eventId: Long, instanceStart: Long): Int

    @Query("DELETE FROM ${EventAlertEntity.TABLE_NAME}")
    fun deleteAllRows()
}

