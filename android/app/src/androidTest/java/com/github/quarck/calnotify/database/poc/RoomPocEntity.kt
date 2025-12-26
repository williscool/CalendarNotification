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

package com.github.quarck.calnotify.database.poc

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * Minimal Room entity for POC testing of Room + cr-sqlite compatibility.
 * 
 * This entity is intentionally simple to isolate the Room/cr-sqlite integration
 * from any business logic complexity.
 */
@Entity(tableName = "poc_test")
data class RoomPocEntity(
    @PrimaryKey
    val id: Long,
    
    val name: String,
    
    val timestamp: Long,
    
    val description: String = ""
)

/**
 * Data Access Object for RoomPocEntity.
 * 
 * Provides basic CRUD operations to test Room functionality with cr-sqlite.
 */
@Dao
interface RoomPocDao {
    
    @Query("SELECT * FROM poc_test ORDER BY id ASC")
    fun getAll(): List<RoomPocEntity>
    
    @Query("SELECT * FROM poc_test WHERE id = :id")
    fun getById(id: Long): RoomPocEntity?
    
    @Query("SELECT * FROM poc_test WHERE name LIKE :name")
    fun findByName(name: String): List<RoomPocEntity>
    
    @Query("SELECT COUNT(*) FROM poc_test")
    fun count(): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: RoomPocEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<RoomPocEntity>)
    
    @Update
    fun update(entity: RoomPocEntity): Int
    
    @Delete
    fun delete(entity: RoomPocEntity): Int
    
    @Query("DELETE FROM poc_test")
    fun deleteAll()
    
    @Query("DELETE FROM poc_test WHERE id = :id")
    fun deleteById(id: Long): Int
}

