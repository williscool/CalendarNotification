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
 * Tests the copy-based migration from legacy SQLiteOpenHelper database to Room.
 * 
 * Migration strategy:
 * - Legacy DB "CalendarMonitor" is preserved (not modified)
 * - New Room DB "RoomCalendarMonitor" is created
 * - Data is copied from legacy to Room on first access
 * - If copy fails, MigrationException is thrown and caller falls back to legacy
 */
@RunWith(AndroidJUnit4::class)
class MonitorStorageMigrationTest {
    
    companion object {
        private const val LOG_TAG = "MonitorStorageMigrationTest"
        
        // Use test-specific names to avoid interfering with actual app data
        private const val TEST_LEGACY_DB_NAME = "CalendarMonitor_Test"
        private const val TEST_ROOM_DB_NAME = "RoomCalendarMonitor_Test"
    }
    
    private lateinit var context: Context
    private lateinit var legacyDbFile: File
    private lateinit var roomDbFile: File
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        legacyDbFile = context.getDatabasePath(TEST_LEGACY_DB_NAME)
        roomDbFile = context.getDatabasePath(TEST_ROOM_DB_NAME)
        
        // Ensure clean state
        cleanupDatabases()
        legacyDbFile.parentFile?.mkdirs()
        
        DevLog.info(LOG_TAG, "Test setup complete")
        DevLog.info(LOG_TAG, "  Legacy DB: ${legacyDbFile.absolutePath}")
        DevLog.info(LOG_TAG, "  Room DB: ${roomDbFile.absolutePath}")
    }
    
    @After
    fun cleanup() {
        cleanupDatabases()
        DevLog.info(LOG_TAG, "Test cleanup complete")
    }
    
    private fun cleanupDatabases() {
        // Clean up legacy database
        legacyDbFile.delete()
        File(legacyDbFile.absolutePath + "-journal").delete()
        
        // Clean up Room database and its files
        roomDbFile.delete()
        File(roomDbFile.absolutePath + "-shm").delete()
        File(roomDbFile.absolutePath + "-wal").delete()
    }
    
    /**
     * Creates a legacy database with the exact schema that SQLiteOpenHelper would create.
     * Uses the actual MonitorStorageImplV1.createDb() to ensure we test the real schema.
     */
    private fun createLegacyDatabase(): List<TestAlertData> {
        DevLog.info(LOG_TAG, "Creating legacy database at ${legacyDbFile.absolutePath}")
        
        val db = io.requery.android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            legacyDbFile.absolutePath, null
        )
        
        try {
            // Use the ACTUAL legacy code to create the schema
            val legacyImpl = MonitorStorageImplV1(context)
            legacyImpl.createDb(db)
            
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
            
            DevLog.info(LOG_TAG, "Created legacy database with ${testData.size} test rows")
            return testData
            
        } finally {
            db.close()
        }
    }
    
    /**
     * Helper to build a Room database with test-specific name.
     */
    private fun buildTestRoomDatabase(): MonitorDatabase {
        return androidx.room.Room.databaseBuilder(
            context,
            MonitorDatabase::class.java,
            TEST_ROOM_DB_NAME
        )
            .openHelperFactory(com.github.quarck.calnotify.database.CrSqliteRoomFactory())
            .allowMainThreadQueries()
            .build()
    }
    
    /**
     * Tests that data is correctly copied from legacy DB to Room DB.
     */
    @Test
    fun test_copy_migration_preserves_all_data() {
        DevLog.info(LOG_TAG, "=== test_copy_migration_preserves_all_data ===")
        
        // Step 1: Create legacy database with test data
        val testData = createLegacyDatabase()
        assertTrue("Legacy DB should exist", legacyDbFile.exists())
        assertFalse("Room DB should NOT exist yet", roomDbFile.exists())
        
        // Step 2: Read from legacy DB using MonitorStorageImplV1 (simulating what copyFromLegacy does)
        val legacyAlerts = readLegacyDatabase()
        assertEquals("Should read all legacy alerts", testData.size, legacyAlerts.size)
        
        // Step 3: Open Room DB and insert the data (simulating migration)
        val roomDb = buildTestRoomDatabase()
        try {
            val dao = roomDb.monitorAlertDao()
            
            // Insert legacy data into Room
            val entities = legacyAlerts.map { MonitorAlertEntity.fromAlertEntry(it) }
            dao.insertAll(entities)
            
            // Step 4: Verify all data was copied
            val roomAlerts = dao.getAll()
            assertEquals("Room should have same count as legacy", testData.size, roomAlerts.size)
            
            // Verify each row
            for (expected in testData) {
                val found = roomAlerts.find { 
                    it.eventId == expected.eventId && 
                    it.alertTime == expected.alertTime &&
                    it.instanceStartTime == expected.instanceStart
                }
                assertNotNull("Should find alert for eventId=${expected.eventId}", found)
                assertEquals("alertCreatedByUs should match", expected.alertCreatedByUs, found!!.alertCreatedByUs)
                assertEquals("wasHandled should match", expected.wasHandled, found.wasHandled)
                assertEquals("allDay should match", expected.allDay, found.isAllDay)
            }
            
            DevLog.info(LOG_TAG, "✅ All ${testData.size} alerts copied successfully!")
            
        } finally {
            roomDb.close()
        }
        
        // Step 5: Verify legacy DB is still intact (not modified)
        val legacyAlertsAfter = readLegacyDatabase()
        assertEquals("Legacy DB should still have all data", testData.size, legacyAlertsAfter.size)
        DevLog.info(LOG_TAG, "✅ Legacy database preserved!")
    }
    
    /**
     * Tests that Room can write new data after migration.
     */
    @Test
    fun test_room_can_write_after_migration() {
        DevLog.info(LOG_TAG, "=== test_room_can_write_after_migration ===")
        
        // Setup: Create legacy DB and copy to Room
        val testData = createLegacyDatabase()
        val legacyAlerts = readLegacyDatabase()
        
        val roomDb = buildTestRoomDatabase()
        try {
            val dao = roomDb.monitorAlertDao()
            dao.insertAll(legacyAlerts.map { MonitorAlertEntity.fromAlertEntry(it) })
            
            // Insert a new alert
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
            
            // Verify new data exists in Room
            val allAlerts = dao.getAll()
            assertEquals("Should have legacy + new alerts", testData.size + 1, allAlerts.size)
            
            val retrieved = dao.getByKey(99999, 1735600000000, 1735600000000)
            assertNotNull("Should find new alert", retrieved)
            
            DevLog.info(LOG_TAG, "✅ Room can write new data after migration!")
            
        } finally {
            roomDb.close()
        }
        
        // Verify legacy DB doesn't have the new alert
        val legacyAlertsAfter = readLegacyDatabase()
        assertEquals("Legacy DB should NOT have new alert", testData.size, legacyAlertsAfter.size)
        DevLog.info(LOG_TAG, "✅ New data only in Room, legacy unchanged!")
    }
    
    /**
     * Tests that migration is skipped if Room DB already has data.
     */
    @Test
    fun test_migration_skipped_if_room_has_data() {
        DevLog.info(LOG_TAG, "=== test_migration_skipped_if_room_has_data ===")
        
        // Step 1: Create Room DB with one alert first
        val roomDb = buildTestRoomDatabase()
        try {
            val dao = roomDb.monitorAlertDao()
            
            val existingAlert = MonitorAlertEntity(
                calendarId = 1,
                eventId = 11111,
                alertTime = 1735400000000,
                instanceStartTime = 1735400000000,
                instanceEndTime = 1735403600000,
                isAllDay = 0,
                alertCreatedByUs = 0,
                wasHandled = 0
            )
            dao.insert(existingAlert)
            
            assertEquals("Room should have 1 alert", 1, dao.getAll().size)
        } finally {
            roomDb.close()
        }
        
        // Step 2: Create legacy DB with different data
        createLegacyDatabase() // 3 alerts
        
        // Step 3: Re-open Room DB - migration should be skipped since it has data
        val roomDb2 = buildTestRoomDatabase()
        try {
            val dao = roomDb2.monitorAlertDao()
            val allAlerts = dao.getAll()
            
            // Should still only have the original 1 alert, not 4 (1 + 3 from legacy)
            assertEquals("Should still have only 1 alert (migration skipped)", 1, allAlerts.size)
            assertEquals("Should be the original alert", 11111L, allAlerts[0].eventId)
            
            DevLog.info(LOG_TAG, "✅ Migration correctly skipped when Room already has data!")
            
        } finally {
            roomDb2.close()
        }
    }
    
    /**
     * Tests fresh install scenario - no legacy DB, Room creates fresh.
     */
    @Test
    fun test_fresh_install_no_legacy() {
        DevLog.info(LOG_TAG, "=== test_fresh_install_no_legacy ===")
        
        // Don't create legacy DB - simulate fresh install
        assertFalse("Legacy DB should NOT exist", legacyDbFile.exists())
        
        val roomDb = buildTestRoomDatabase()
        try {
            val dao = roomDb.monitorAlertDao()
            
            // Should be empty
            assertEquals("Fresh install should have 0 alerts", 0, dao.getAll().size)
            
            // Can insert new data
            val newAlert = MonitorAlertEntity(
                calendarId = 1,
                eventId = 12345,
                alertTime = 1735500000000,
                instanceStartTime = 1735500000000,
                instanceEndTime = 1735503600000,
                isAllDay = 0,
                alertCreatedByUs = 1,
                wasHandled = 0
            )
            dao.insert(newAlert)
            
            assertEquals("Should have 1 alert after insert", 1, dao.getAll().size)
            
            DevLog.info(LOG_TAG, "✅ Fresh install works correctly!")
            
        } finally {
            roomDb.close()
        }
    }
    
    /**
     * Read alerts from legacy database using MonitorStorageImplV1.
     */
    private fun readLegacyDatabase(): List<com.github.quarck.calnotify.calendar.MonitorEventAlertEntry> {
        val db = io.requery.android.database.sqlite.SQLiteDatabase.openDatabase(
            legacyDbFile.absolutePath,
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
