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
import android.database.SQLException
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.quarck.calnotify.database.CrSqliteRoomFactory
import com.github.quarck.calnotify.logs.DevLog
import java.io.File

/**
 * Exception thrown when migration from legacy database fails.
 * Signals that the caller should fall back to legacy storage.
 */
class MigrationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Room database for MonitorStorage.
 * 
 * Uses a NEW database file "RoomCalendarMonitor" (not the legacy "CalendarMonitor").
 * On first use, copies data from legacy database if it exists.
 * Uses CrSqliteRoomFactory for cr-sqlite extension support.
 * 
 * MIGRATION STRATEGY: Copy data from old DB to new Room DB.
 * - Legacy DB "CalendarMonitor" is preserved (not modified)
 * - If migration fails, throws MigrationException so caller can fall back to legacy
 * - This allows retry on future app versions if bugs are found
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
        
        /** New Room database name - separate from legacy */
        internal const val DATABASE_NAME = "RoomCalendarMonitor"
        
        /** Legacy SQLiteOpenHelper database name */
        internal const val LEGACY_DATABASE_NAME = "CalendarMonitor"

        @Volatile
        private var INSTANCE: MonitorDatabase? = null

        /**
         * Get the Room database instance.
         * 
         * @throws MigrationException if migration from legacy DB fails
         */
        fun getInstance(context: Context): MonitorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val db = buildDatabase(context)
                    var success = false
                    try {
                        // Copy from legacy DB if needed (throws MigrationException on failure)
                        copyFromLegacyIfNeeded(context, db)
                        INSTANCE = db
                        success = true
                        db
                    } catch (e: MigrationException) {
                        throw e
                    } catch (e: RuntimeException) {
                        // Wrap unexpected runtime exceptions so caller can fall back to legacy
                        throw MigrationException("Migration failed with unexpected error: ${e.message}", e)
                    } finally {
                        if (!success) db.close()
                    }
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
         * Copy data from legacy "CalendarMonitor" database to new Room database.
         * 
         * This is a one-time migration that:
         * 1. Checks if Room DB already has data (skip if so)
         * 2. Reads all alerts from legacy DB using MonitorStorageImplV1
         * 3. Inserts them into Room DB
         * 4. Validates row counts match
         * 
         * @throws MigrationException if row count mismatch (data loss detected)
         */
        private fun copyFromLegacyIfNeeded(context: Context, roomDb: MonitorDatabase) {
            val legacyDbFile = context.getDatabasePath(LEGACY_DATABASE_NAME)
            
            // No legacy DB = fresh install, nothing to migrate
            if (!legacyDbFile.exists()) {
                DevLog.info(LOG_TAG, "No legacy database found - fresh install")
                return
            }
            
            val dao = roomDb.monitorAlertDao()
            
            try {
                // Check if Room DB already has data (migration already done)
                val existingCount = dao.count()
                if (existingCount > 0) {
                    DevLog.info(LOG_TAG, "Room database already has $existingCount rows - skipping migration")
                    return
                }
                
                DevLog.info(LOG_TAG, "Starting migration from legacy database: ${legacyDbFile.absolutePath}")
                
                // Get actual count from legacy DB first (don't trust read count - impl may swallow errors)
                val legacyCount = countLegacyRows(legacyDbFile)
                DevLog.info(LOG_TAG, "Legacy database has $legacyCount rows")
                
                if (legacyCount == 0) {
                    DevLog.info(LOG_TAG, "Legacy database is empty - nothing to migrate")
                    return
                }
                
                // Read from legacy DB using the legacy implementation
                val legacyAlerts = readFromLegacyDatabase(context, legacyDbFile)
                
                // Validate read count matches actual count (detect partial reads)
                if (legacyAlerts.size != legacyCount) {
                    val msg = "Partial read detected! DB has $legacyCount rows but only read ${legacyAlerts.size}"
                    DevLog.error(LOG_TAG, msg)
                    throw MigrationException(msg)
                }
                
                DevLog.info(LOG_TAG, "Read ${legacyAlerts.size} alerts from legacy database")
                
                // Insert into Room DB
                val entities = legacyAlerts.map { MonitorAlertEntity.fromAlertEntry(it) }
                dao.insertAll(entities)
                
                // Validate row count in Room matches legacy
                val newCount = dao.count()
                DevLog.info(LOG_TAG, "Inserted $newCount rows into Room database")
                
                if (newCount != legacyCount) {
                    val msg = "Migration row count mismatch! Legacy=$legacyCount, Room=$newCount"
                    DevLog.error(LOG_TAG, msg)
                    throw MigrationException(msg)
                }
                
                DevLog.info(LOG_TAG, "✅ Migration complete: $legacyCount alerts copied successfully")
                
            } catch (e: MigrationException) {
                throw e
            } catch (e: SQLException) {
                val msg = "Migration failed with database error: ${e.message}"
                DevLog.error(LOG_TAG, msg)
                throw MigrationException(msg, e)
            } catch (e: IllegalStateException) {
                val msg = "Migration failed with Room validation error: ${e.message}"
                DevLog.error(LOG_TAG, msg)
                throw MigrationException(msg, e)
            }
        }
        
        /**
         * Count rows in legacy database directly via SQL.
         * This is more reliable than trusting getAlerts() which may swallow errors.
         */
        private fun countLegacyRows(dbFile: File): Int {
            val db = io.requery.android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                io.requery.android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            return try {
                db.rawQuery("SELECT COUNT(*) FROM ${MonitorAlertEntity.TABLE_NAME}", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
            } finally {
                db.close()
            }
        }
        
        /**
         * Read all alerts from the legacy database.
         * Uses requery SQLiteDatabase directly to avoid opening the legacy SQLiteOpenHelper.
         */
        private fun readFromLegacyDatabase(context: Context, dbFile: File): List<com.github.quarck.calnotify.calendar.MonitorEventAlertEntry> {
            val db = io.requery.android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                io.requery.android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            
            return try {
                val impl = MonitorStorageImplV1(context)
                impl.getAlerts(db)
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
