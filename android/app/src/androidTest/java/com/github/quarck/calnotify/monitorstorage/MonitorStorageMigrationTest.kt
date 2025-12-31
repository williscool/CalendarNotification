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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.logs.DevLog
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Tests Room's ability to open and use a pre-existing SQLite database
 * that was created by the legacy SQLiteOpenHelper (without room_master_table).
 * 
 * This validates the database migration path for existing users upgrading
 * from the old implementation to Room.
 */
@RunWith(AndroidJUnit4::class)
class MonitorStorageMigrationTest {
    
    companion object {
        private const val LOG_TAG = "MonitorStorageMigrationTest"
        private const val LEGACY_DB_NAME = "CalendarMonitor_MigrationTest"
    }
    
    private lateinit var context: Context
    private lateinit var dbFile: File
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        dbFile = context.getDatabasePath(LEGACY_DB_NAME)
        
        // Ensure clean state
        if (dbFile.exists()) {
            dbFile.delete()
        }
        dbFile.parentFile?.mkdirs()
        
        DevLog.info(LOG_TAG, "Test setup complete, dbFile=${dbFile.absolutePath}")
    }
    
    @After
    fun cleanup() {
        // Clean up test database
        if (dbFile.exists()) {
            dbFile.delete()
        }
        // Also clean up any Room-generated files
        File(dbFile.absolutePath + "-shm").delete()
        File(dbFile.absolutePath + "-wal").delete()
        
        DevLog.info(LOG_TAG, "Test cleanup complete")
    }
    
    /**
     * Creates a legacy database with the exact schema that SQLiteOpenHelper would create,
     * WITHOUT room_master_table. This simulates a user's existing database before upgrade.
     * 
     * Uses the actual MonitorStorageImplV1.createDb() to ensure we test the real schema.
     */
    private fun createLegacyDatabase(): List<TestAlertData> {
        DevLog.info(LOG_TAG, "Creating legacy database at ${dbFile.absolutePath}")
        
        // Use requery SQLiteDatabase - same as the legacy code uses
        val db = io.requery.android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            dbFile.absolutePath, null
        )
        
        try {
            // Use the ACTUAL legacy code to create the schema - this is what we're migrating from
            val legacyImpl = MonitorStorageImplV1(context)
            legacyImpl.createDb(db)
            
            // Insert test data using the legacy implementation
            val testData = listOf(
                TestAlertData(calendarId = 1, eventId = 12345, alertTime = 1735500000000, 
                    instanceStart = 1735500000000, instanceEnd = 1735503600000, 
                    allDay = 0, alertCreatedByUs = 1, wasHandled = 0),
                TestAlertData(calendarId = 1, eventId = 12346, alertTime = 1735510000000, 
                    instanceStart = 1735510000000, instanceEnd = 1735513600000, 
                    allDay = 0, alertCreatedByUs = 0, wasHandled = 1),
                TestAlertData(calendarId = 2, eventId = 12347, alertTime = 1735520000000, 
                    instanceStart = 1735520000000, instanceEnd = 1735523600000, 
                    allDay = 1, alertCreatedByUs = 1, wasHandled = 1)
            )
            
            for (data in testData) {
                db.execSQL("""
                    INSERT INTO ${MonitorAlertEntity.TABLE_NAME} VALUES (
                        ${data.calendarId}, ${data.eventId}, ${data.alertTime},
                        ${data.instanceStart}, ${data.instanceEnd}, ${data.allDay},
                        ${data.alertCreatedByUs}, ${data.wasHandled}, 0, 0
                    )
                """.trimIndent())
            }
            
            // Verify NO room_master_table exists (this is the key test condition)
            val cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='room_master_table'", 
                null
            )
            val hasRoomTable = cursor.moveToFirst()
            cursor.close()
            
            assertFalse("Legacy database should NOT have room_master_table", hasRoomTable)
            
            DevLog.info(LOG_TAG, "Created legacy database with ${testData.size} test rows, no room_master_table")
            return testData
            
        } finally {
            db.close()
        }
    }
    
    /**
     * Performs pre-Room migration on a legacy database.
     * Calls the ACTUAL migration function from MonitorDatabase to ensure test fidelity.
     */
    private fun performPreRoomMigration() {
        DevLog.info(LOG_TAG, "Running pre-Room migration via MonitorDatabase.migrateLegacyDatabaseIfNeeded()")
        MonitorDatabase.migrateLegacyDatabaseIfNeeded(dbFile)
        DevLog.info(LOG_TAG, "Pre-Room migration complete")
    }
    
    /**
     * Tests that Room can open a legacy database and read existing data.
     * 
     * This is the critical migration test - if this passes, existing users
     * can safely upgrade to the Room-based implementation.
     */
    @Test
    fun test_room_opens_legacy_database_without_crash() {
        DevLog.info(LOG_TAG, "=== test_room_opens_legacy_database_without_crash ===")
        
        // Step 1: Create legacy database with test data (no room_master_table)
        val testData = createLegacyDatabase()
        
        // Step 2: Run pre-Room migration (same as MonitorDatabase does)
        performPreRoomMigration()
        
        // Step 3: Open database with Room via MonitorDatabase
        // This should NOT crash - Room should handle the pre-existing table
        DevLog.info(LOG_TAG, "Opening legacy database with Room...")
        
        val roomDb = androidx.room.Room.databaseBuilder(
            context,
            MonitorDatabase::class.java,
            LEGACY_DB_NAME
        )
            .openHelperFactory(com.github.quarck.calnotify.database.CrSqliteRoomFactory())
            .allowMainThreadQueries()
            .build()
        
        try {
            // Step 4: Read data via Room DAO
            val dao = roomDb.monitorAlertDao()
            val alerts = dao.getAll()
            
            DevLog.info(LOG_TAG, "Room read ${alerts.size} alerts from legacy database")
            
            // Step 5: Verify data integrity
            assertEquals("Should read all test alerts", testData.size, alerts.size)
            
            // Verify each row
            for (expected in testData) {
                val found = alerts.find { 
                    it.eventId == expected.eventId && 
                    it.alertTime == expected.alertTime &&
                    it.instanceStartTime == expected.instanceStart
                }
                assertNotNull("Should find alert for eventId=${expected.eventId}", found)
                assertEquals("alertCreatedByUs should match", expected.alertCreatedByUs, found!!.alertCreatedByUs)
                assertEquals("wasHandled should match", expected.wasHandled, found.wasHandled)
                assertEquals("allDay should match", expected.allDay, found.isAllDay)
            }
            
            // Step 6: Verify Room created its metadata table
            val supportDb = roomDb.openHelper.writableDatabase
            supportDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='room_master_table'").use { cursor ->
                assertTrue("Room should have created room_master_table", cursor.moveToFirst())
            }
            
            DevLog.info(LOG_TAG, "✅ Room successfully opened legacy database and preserved all data!")
            
        } finally {
            roomDb.close()
        }
    }
    
    /**
     * Tests that Room can write to a database that was originally created by SQLiteOpenHelper.
     */
    @Test
    fun test_room_can_write_to_legacy_database() {
        DevLog.info(LOG_TAG, "=== test_room_can_write_to_legacy_database ===")
        
        // Create legacy database and migrate
        createLegacyDatabase()
        performPreRoomMigration()
        
        // Open with Room
        val roomDb = androidx.room.Room.databaseBuilder(
            context,
            MonitorDatabase::class.java,
            LEGACY_DB_NAME
        )
            .openHelperFactory(com.github.quarck.calnotify.database.CrSqliteRoomFactory())
            .allowMainThreadQueries()
            .build()
        
        try {
            val dao = roomDb.monitorAlertDao()
            
            // Insert a new alert via Room
            val newAlert = MonitorAlertEntity(
                calendarId = 99,
                eventId = 99999,
                alertTime = 1735600000000,
                instanceStartTime = 1735600000000,
                instanceEndTime = 1735603600000,
                isAllDay = 0,
                alertCreatedByUs = 1,
                wasHandled = 0
            )
            
            dao.insert(newAlert)
            DevLog.info(LOG_TAG, "Inserted new alert via Room")
            
            // Verify it was inserted
            val retrieved = dao.getByKey(99999, 1735600000000, 1735600000000)
            assertNotNull("Should retrieve newly inserted alert", retrieved)
            assertEquals("eventId should match", 99999L, retrieved!!.eventId)
            
            // Verify total count increased
            val allAlerts = dao.getAll()
            assertEquals("Should have 4 alerts total (3 legacy + 1 new)", 4, allAlerts.size)
            
            DevLog.info(LOG_TAG, "✅ Room successfully wrote to legacy database!")
            
        } finally {
            roomDb.close()
        }
    }
    
    /**
     * Tests that Room can update existing records in a legacy database.
     */
    @Test
    fun test_room_can_update_legacy_records() {
        DevLog.info(LOG_TAG, "=== test_room_can_update_legacy_records ===")
        
        // Create legacy database and migrate
        createLegacyDatabase()
        performPreRoomMigration()
        
        // Open with Room
        val roomDb = androidx.room.Room.databaseBuilder(
            context,
            MonitorDatabase::class.java,
            LEGACY_DB_NAME
        )
            .openHelperFactory(com.github.quarck.calnotify.database.CrSqliteRoomFactory())
            .allowMainThreadQueries()
            .build()
        
        try {
            val dao = roomDb.monitorAlertDao()
            
            // Get the first alert (wasHandled = 0)
            val alert = dao.getByKey(12345, 1735500000000, 1735500000000)
            assertNotNull("Should find test alert", alert)
            assertEquals("Should initially be unhandled", 0, alert!!.wasHandled)
            
            // Update via Room
            val updated = alert.copy(wasHandled = 1)
            dao.update(updated)
            DevLog.info(LOG_TAG, "Updated alert via Room")
            
            // Verify update
            val retrieved = dao.getByKey(12345, 1735500000000, 1735500000000)
            assertEquals("wasHandled should be updated", 1, retrieved!!.wasHandled)
            
            DevLog.info(LOG_TAG, "✅ Room successfully updated legacy records!")
            
        } finally {
            roomDb.close()
        }
    }
    
    /**
     * Helper data class for test data
     */
    private data class TestAlertData(
        val calendarId: Long,
        val eventId: Long,
        val alertTime: Long,
        val instanceStart: Long,
        val instanceEnd: Long,
        val allDay: Int,
        val alertCreatedByUs: Int,
        val wasHandled: Int
    )
}

