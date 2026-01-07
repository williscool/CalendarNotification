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
}

