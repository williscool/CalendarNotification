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

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.github.quarck.calnotify.database.CrSqliteRoomFactory

/**
 * Room database for MonitorStorage.
 * 
 * Uses the existing "CalendarMonitor" database file to read existing data.
 * Uses CrSqliteRoomFactory for cr-sqlite extension support.
 */
@Database(
    entities = [MonitorAlertEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MonitorDatabase : RoomDatabase() {
    
    abstract fun monitorAlertDao(): MonitorAlertDao

    companion object {
        private const val DATABASE_NAME = "CalendarMonitor"

        @Volatile
        private var INSTANCE: MonitorDatabase? = null

        fun getInstance(context: Context): MonitorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): MonitorDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MonitorDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(CrSqliteRoomFactory())
                .allowMainThreadQueries() // Match existing behavior - MonitorStorage uses sync queries
                .build()
        }
    }
}

