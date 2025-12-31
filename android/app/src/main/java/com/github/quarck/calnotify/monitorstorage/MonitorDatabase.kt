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
        
        private fun migrateLegacyDatabaseIfNeeded(context: Context) {
            migrateLegacyDatabaseIfNeeded(context.getDatabasePath(DATABASE_NAME))
        }
        
        /**
         * Migrate legacy SQLiteOpenHelper database to Room-compatible schema.
         * 
         * This runs BEFORE Room opens the database because Room validates schema
         * before running any migrations. For pre-Room databases (no room_master_table),
         * the schema must match exactly or Room will reject it.
         * 
         * Changes made:
         * - Adds NOT NULL constraints to primary key columns (Room requirement)
         * - Preserves the existing index
         * - All data is preserved
         * 
         * @param dbFile The database file to migrate. Exposed as internal for testing.
         */
        internal fun migrateLegacyDatabaseIfNeeded(dbFile: File) {
            if (!dbFile.exists()) {
                DevLog.info(LOG_TAG, "No existing database - fresh install")
                return
            }
            
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                if (!isLegacyDatabase(db)) return
                performLegacyMigration(db)
            } finally {
                db.close()
            }
        }
        
        private fun isLegacyDatabase(db: SQLiteDatabase): Boolean {
            val tableName = MonitorAlertEntity.TABLE_NAME
            
            val hasTable = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='$tableName'", null
            ).use { it.moveToFirst() }
            
            val hasRoomTable = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='room_master_table'", null
            ).use { it.moveToFirst() }
            
            return when {
                !hasTable -> { DevLog.info(LOG_TAG, "No legacy table - nothing to migrate"); false }
                hasRoomTable -> { DevLog.info(LOG_TAG, "Already Room-managed"); false }
                else -> { DevLog.info(LOG_TAG, "Legacy database detected"); true }
            }
        }
        
        private fun performLegacyMigration(db: SQLiteDatabase) {
            val t = MonitorAlertEntity.TABLE_NAME
            val idx = MonitorAlertEntity.INDEX_NAME
            // Column names
            val calendarId = MonitorAlertEntity.COL_CALENDAR_ID
            val eventId = MonitorAlertEntity.COL_EVENT_ID
            val alertTime = MonitorAlertEntity.COL_ALERT_TIME
            val instanceStart = MonitorAlertEntity.COL_INSTANCE_START
            val instanceEnd = MonitorAlertEntity.COL_INSTANCE_END
            val allDay = MonitorAlertEntity.COL_ALL_DAY
            val alertCreatedByUs = MonitorAlertEntity.COL_ALERT_CREATED_BY_US
            val wasHandled = MonitorAlertEntity.COL_WAS_HANDLED
            val i1 = MonitorAlertEntity.COL_RESERVED_INT1
            val i2 = MonitorAlertEntity.COL_RESERVED_INT2
            
            val rowCountBefore = db.rawQuery("SELECT COUNT(*) FROM $t", null)
                .use { if (it.moveToFirst()) it.getLong(0) else 0 }
            DevLog.info(LOG_TAG, "Migrating $rowCountBefore rows to Room-compatible schema")
            
            db.beginTransaction()
            try {
                // Recreate table with NOT NULL on primary key columns
                db.execSQL("""
                    CREATE TABLE ${t}_new (
                        $calendarId INTEGER,
                        $eventId INTEGER NOT NULL,
                        $alertTime INTEGER NOT NULL,
                        $instanceStart INTEGER NOT NULL,
                        $instanceEnd INTEGER,
                        $allDay INTEGER,
                        $alertCreatedByUs INTEGER,
                        $wasHandled INTEGER,
                        $i1 INTEGER,
                        $i2 INTEGER,
                        PRIMARY KEY ($eventId, $alertTime, $instanceStart)
                    )
                """)
                
                // Copy data (COALESCE ensures no nulls in PK columns)
                db.execSQL("""
                    INSERT INTO ${t}_new 
                    SELECT $calendarId, COALESCE($eventId, 0), COALESCE($alertTime, 0), 
                           COALESCE($instanceStart, 0), $instanceEnd, $allDay, 
                           $alertCreatedByUs, $wasHandled, $i1, $i2
                    FROM $t
                """)
                
                db.execSQL("DROP TABLE $t")
                db.execSQL("ALTER TABLE ${t}_new RENAME TO $t")
                db.execSQL("CREATE UNIQUE INDEX $idx ON $t ($eventId, $alertTime, $instanceStart)")
                
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            
            val rowCountAfter = db.rawQuery("SELECT COUNT(*) FROM $t", null)
                .use { if (it.moveToFirst()) it.getLong(0) else 0 }
            DevLog.info(LOG_TAG, "Migration complete: $rowCountAfter rows")
            
            if (rowCountAfter != rowCountBefore) {
                DevLog.error(LOG_TAG, "Row count mismatch! Before=$rowCountBefore, After=$rowCountAfter")
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

