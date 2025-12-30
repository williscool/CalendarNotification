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
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.quarck.calnotify.database.CrSqliteRoomFactory
import com.github.quarck.calnotify.logs.DevLog
import java.io.File

/**
 * Room database for MonitorStorage.
 * 
 * Uses the existing "CalendarMonitor" database file to read existing data.
 * Uses CrSqliteRoomFactory for cr-sqlite extension support.
 * 
 * MIGRATION NOTE: This Room database takes over from legacy SQLiteOpenHelper.
 * Before Room opens, we migrate the legacy schema to add NOT NULL constraints
 * on primary key columns (required by Room but missing in legacy schema).
 */
@Database(
    entities = [MonitorAlertEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MonitorDatabase : RoomDatabase() {
    
    abstract fun monitorAlertDao(): MonitorAlertDao

    companion object {
        private const val LOG_TAG = "MonitorDatabase"
        private const val DATABASE_NAME = "CalendarMonitor"

        @Volatile
        private var INSTANCE: MonitorDatabase? = null

        fun getInstance(context: Context): MonitorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    // Migrate legacy database BEFORE Room opens it
                    migrateLegacyDatabaseIfNeeded(context)
                    buildDatabase(context).also { INSTANCE = it }
                }
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
                .addCallback(LoggingCallback())
                .build()
        }
        
        /**
         * Migrate legacy SQLiteOpenHelper database to Room-compatible schema.
         * 
         * The legacy schema has nullable primary key columns (no NOT NULL constraint).
         * Room requires primary key columns to be NOT NULL.
         * 
         * This migration:
         * 1. Detects legacy database (has manualAlertsV1 but no room_master_table)
         * 2. Recreates the table with NOT NULL constraints on PK columns
         * 3. Preserves all existing data
         */
        private fun migrateLegacyDatabaseIfNeeded(context: Context) {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (!dbFile.exists()) {
                DevLog.info(LOG_TAG, "No existing database - fresh install")
                return
            }
            
            // Open database directly with Android's SQLiteDatabase (not Room, not requery)
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                // Check if this is a legacy database (has table but no room_master_table)
                val hasLegacyTable = db.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='manualAlertsV1'", null
                ).use { it.moveToFirst() }
                
                val hasRoomTable = db.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='room_master_table'", null
                ).use { it.moveToFirst() }
                
                if (!hasLegacyTable) {
                    DevLog.info(LOG_TAG, "No legacy table found - nothing to migrate")
                    return
                }
                
                if (hasRoomTable) {
                    DevLog.info(LOG_TAG, "Database already migrated to Room")
                    return
                }
                
                DevLog.info(LOG_TAG, "Legacy database detected - migrating schema for Room compatibility")
                
                // Count existing rows for verification
                val rowCountBefore = db.rawQuery("SELECT COUNT(*) FROM manualAlertsV1", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0
                }
                DevLog.info(LOG_TAG, "Found $rowCountBefore rows to migrate")
                
                db.beginTransaction()
                try {
                    // Create new table with Room-compatible schema (NOT NULL on PK columns)
                    db.execSQL("""
                        CREATE TABLE manualAlertsV1_new (
                            calendarId INTEGER,
                            eventId INTEGER NOT NULL,
                            alertTime INTEGER NOT NULL,
                            instanceStart INTEGER NOT NULL,
                            instanceEnd INTEGER,
                            allDay INTEGER,
                            alertCreatedByUs INTEGER,
                            wasHandled INTEGER,
                            i1 INTEGER,
                            i2 INTEGER,
                            PRIMARY KEY (eventId, alertTime, instanceStart)
                        )
                    """.trimIndent())
                    
                    // Copy data (coalesce handles any null PKs, though there shouldn't be any)
                    db.execSQL("""
                        INSERT INTO manualAlertsV1_new 
                        SELECT calendarId, 
                               COALESCE(eventId, 0), 
                               COALESCE(alertTime, 0), 
                               COALESCE(instanceStart, 0),
                               instanceEnd, allDay, alertCreatedByUs, wasHandled, i1, i2
                        FROM manualAlertsV1
                    """.trimIndent())
                    
                    // Drop old table and rename new one
                    db.execSQL("DROP TABLE manualAlertsV1")
                    db.execSQL("ALTER TABLE manualAlertsV1_new RENAME TO manualAlertsV1")
                    
                    // Recreate the index
                    db.execSQL("""
                        CREATE UNIQUE INDEX IF NOT EXISTS manualAlertsV1IdxV1 
                        ON manualAlertsV1 (eventId, alertTime, instanceStart)
                    """.trimIndent())
                    
                    db.setTransactionSuccessful()
                    
                    // Verify migration
                    val rowCountAfter = db.rawQuery("SELECT COUNT(*) FROM manualAlertsV1", null).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getLong(0) else 0
                    }
                    DevLog.info(LOG_TAG, "Migration complete: $rowCountAfter rows (was $rowCountBefore)")
                    
                    if (rowCountAfter != rowCountBefore) {
                        DevLog.error(LOG_TAG, "WARNING: Row count mismatch after migration!")
                    }
                    
                } finally {
                    db.endTransaction()
                }
                
            } finally {
                db.close()
            }
        }
        
        /** Simple callback for logging */
        private class LoggingCallback : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                DevLog.info(LOG_TAG, "Room database opened, version=${db.version}")
            }
            
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                DevLog.info(LOG_TAG, "Room database created (fresh install)")
            }
        }
    }
}

