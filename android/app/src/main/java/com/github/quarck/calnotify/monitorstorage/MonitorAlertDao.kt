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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Room DAO for MonitorAlertEntity.
 * 
 * Provides all database operations needed by MonitorStorageInterface.
 * 
 * Note: Room @Query annotations require string literals, so we can't use
 * MonitorAlertEntity constants directly in queries. The table/column names
 * are validated at compile time against the @Entity definition.
 */
@Dao
interface MonitorAlertDao {
    
    @Query("SELECT * FROM ${MonitorAlertEntity.TABLE_NAME}")
    fun getAll(): List<MonitorAlertEntity>

    @Query("SELECT * FROM ${MonitorAlertEntity.TABLE_NAME} WHERE ${MonitorAlertEntity.COL_EVENT_ID} = :eventId AND ${MonitorAlertEntity.COL_ALERT_TIME} = :alertTime AND ${MonitorAlertEntity.COL_INSTANCE_START} = :instanceStart")
    fun getByKey(eventId: Long, alertTime: Long, instanceStart: Long): MonitorAlertEntity?

    @Query("SELECT * FROM ${MonitorAlertEntity.TABLE_NAME} WHERE ${MonitorAlertEntity.COL_EVENT_ID} = :eventId AND ${MonitorAlertEntity.COL_INSTANCE_START} = :instanceStart")
    fun getByEventAndInstance(eventId: Long, instanceStart: Long): List<MonitorAlertEntity>

    @Query("SELECT * FROM ${MonitorAlertEntity.TABLE_NAME} WHERE ${MonitorAlertEntity.COL_ALERT_TIME} = :time")
    fun getByAlertTime(time: Long): List<MonitorAlertEntity>

    @Query("SELECT MIN(${MonitorAlertEntity.COL_ALERT_TIME}) FROM ${MonitorAlertEntity.TABLE_NAME} WHERE ${MonitorAlertEntity.COL_ALERT_TIME} >= :since")
    fun getNextAlertTime(since: Long): Long?

    @Query("SELECT * FROM ${MonitorAlertEntity.TABLE_NAME} WHERE ${MonitorAlertEntity.COL_INSTANCE_START} >= :scanFrom AND ${MonitorAlertEntity.COL_INSTANCE_START} <= :scanTo")
    fun getByInstanceStartRange(scanFrom: Long, scanTo: Long): List<MonitorAlertEntity>

    @Query("SELECT * FROM ${MonitorAlertEntity.TABLE_NAME} WHERE ${MonitorAlertEntity.COL_ALERT_TIME} >= :scanFrom AND ${MonitorAlertEntity.COL_ALERT_TIME} <= :scanTo")
    fun getByAlertTimeRange(scanFrom: Long, scanTo: Long): List<MonitorAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: MonitorAlertEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<MonitorAlertEntity>)

    @Update
    fun update(entity: MonitorAlertEntity)

    @Update
    fun updateAll(entities: List<MonitorAlertEntity>)

    @Delete
    fun delete(entity: MonitorAlertEntity)

    @Delete
    fun deleteAll(entities: List<MonitorAlertEntity>)

    @Query("DELETE FROM ${MonitorAlertEntity.TABLE_NAME} WHERE ${MonitorAlertEntity.COL_EVENT_ID} = :eventId AND ${MonitorAlertEntity.COL_ALERT_TIME} = :alertTime AND ${MonitorAlertEntity.COL_INSTANCE_START} = :instanceStart")
    fun deleteByKey(eventId: Long, alertTime: Long, instanceStart: Long)
}

