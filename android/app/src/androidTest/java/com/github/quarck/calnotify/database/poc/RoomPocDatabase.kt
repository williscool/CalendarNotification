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

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * POC Room database configured to use cr-sqlite via CrSqliteRoomFactory.
 * 
 * This database is for testing Room + cr-sqlite compatibility only.
 * It is not used in production code.
 * 
 * Key things being validated:
 * 1. Room can use requery's SQLite via custom factory
 * 2. CR-SQLite extension is properly loaded
 * 3. crsql_finalize() is called on close
 * 4. Basic CRUD operations work correctly
 */
@Database(
    entities = [RoomPocEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RoomPocDatabase : RoomDatabase() {
    
    abstract fun pocDao(): RoomPocDao
    
    companion object {
        private const val DATABASE_NAME = "room_poc_test.db"
        
        /**
         * Creates a Room database that uses cr-sqlite via our custom factory.
         * 
         * This is the key integration point - if this works, Room can use cr-sqlite.
         */
        fun createWithCrSqlite(context: Context): RoomPocDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                RoomPocDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(CrSqliteRoomFactory())
                .allowMainThreadQueries()  // For testing simplicity
                .build()
        }
        
        /**
         * Creates an in-memory Room database for testing.
         * Uses standard Room SQLite (no cr-sqlite) for comparison.
         */
        fun createInMemory(context: Context): RoomPocDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                RoomPocDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
        
        /**
         * Creates an in-memory Room database with cr-sqlite.
         * 
         * Note: In-memory databases may have different behavior with extensions.
         */
        fun createInMemoryWithCrSqlite(context: Context): RoomPocDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                RoomPocDatabase::class.java
            )
                .openHelperFactory(CrSqliteRoomFactory())
                .allowMainThreadQueries()
                .build()
        }
    }
}

