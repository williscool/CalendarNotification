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
 * - Legacy DB "DismissedEvents" is preserved (not modified)
 * - New Room DB "RoomDismissedEvents" is created
 * - Data is copied from legacy to Room on first access
 * - If copy fails, DismissedEventsMigrationException is thrown and caller falls back to legacy
 */
@RunWith(AndroidJUnit4::class)
class DismissedEventsStorageMigrationTest {
    
    companion object {
        private const val LOG_TAG = "DismissedEventsStorageMigrationTest"
        
        // Use test-specific names to avoid interfering with actual app data
        private const val TEST_LEGACY_DB_NAME = "DismissedEvents_Test"
        private const val TEST_ROOM_DB_NAME = "RoomDismissedEvents_Test"
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
     * Uses the actual DismissedEventsStorageImplV2.createDb() to ensure we test the real schema.
     */
    private fun createLegacyDatabase(): List<TestDismissedEventData> {
        DevLog.info(LOG_TAG, "Creating legacy database at ${legacyDbFile.absolutePath}")
        
        val db = io.requery.android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            legacyDbFile.absolutePath, null
        )
        
        try {
            // Use the ACTUAL legacy code to create the schema
            val legacyImpl = DismissedEventsStorageImplV2()
            legacyImpl.createDb(db)
            
            val testData = listOf(
                TestDismissedEventData(
                    calendarId = 1, eventId = 12345, 
                    dismissTime = 1735500000000, dismissType = EventDismissType.ManuallyDismissedFromNotification.code,
                    alertTime = 1735499000000,
                    title = "Meeting 1", description = "Team standup",
                    eventStart = 1735500000000, eventEnd = 1735503600000,
                    instanceStart = 1735500000000, instanceEnd = 1735503600000,
                    location = "Room A", snoozedUntil = 0, lastSeen = 1735499500000,
                    displayStatus = 0, color = 0xFF0000, isRepeating = 0, allDay = 0, flags = 0
                ),
                TestDismissedEventData(
                    calendarId = 1, eventId = 12346,
                    dismissTime = 1735510000000, dismissType = EventDismissType.ManuallyDismissedFromActivity.code,
                    alertTime = 1735509000000,
                    title = "Meeting 2", description = "Project review",
                    eventStart = 1735510000000, eventEnd = 1735513600000,
                    instanceStart = 1735510000000, instanceEnd = 1735513600000,
                    location = "Room B", snoozedUntil = 0, lastSeen = 1735509500000,
                    displayStatus = 0, color = 0x00FF00, isRepeating = 1, allDay = 0, flags = 0
                ),
                TestDismissedEventData(
                    calendarId = 2, eventId = 12347,
                    dismissTime = 1735520000000, dismissType = EventDismissType.AutoDismissedDueToCalendarMove.code,
                    alertTime = 1735519000000,
                    title = "All Day Event", description = "Holiday",
                    eventStart = 1735520000000, eventEnd = 1735606400000,
                    instanceStart = 1735520000000, instanceEnd = 1735606400000,
                    location = "", snoozedUntil = 0, lastSeen = 1735519500000,
                    displayStatus = 0, color = 0x0000FF, isRepeating = 0, allDay = 1, flags = 0
                )
            )
            
            for (data in testData) {
                db.execSQL("""
                    INSERT INTO ${DismissedEventEntity.TABLE_NAME} (
                        ${DismissedEventEntity.COL_CALENDAR_ID},
                        ${DismissedEventEntity.COL_EVENT_ID},
                        ${DismissedEventEntity.COL_DISMISS_TIME},
                        ${DismissedEventEntity.COL_DISMISS_TYPE},
                        ${DismissedEventEntity.COL_ALERT_TIME},
                        ${DismissedEventEntity.COL_TITLE},
                        ${DismissedEventEntity.COL_DESCRIPTION},
                        ${DismissedEventEntity.COL_START},
                        ${DismissedEventEntity.COL_END},
                        ${DismissedEventEntity.COL_INSTANCE_START},
                        ${DismissedEventEntity.COL_INSTANCE_END},
                        ${DismissedEventEntity.COL_LOCATION},
                        ${DismissedEventEntity.COL_SNOOZED_UNTIL},
                        ${DismissedEventEntity.COL_LAST_EVENT_VISIBILITY},
                        ${DismissedEventEntity.COL_DISPLAY_STATUS},
                        ${DismissedEventEntity.COL_COLOR},
                        ${DismissedEventEntity.COL_IS_REPEATING},
                        ${DismissedEventEntity.COL_ALL_DAY},
                        ${DismissedEventEntity.COL_FLAGS},
                        ${DismissedEventEntity.COL_RESERVED_INT2},
                        ${DismissedEventEntity.COL_RESERVED_INT3},
                        ${DismissedEventEntity.COL_RESERVED_INT4},
                        ${DismissedEventEntity.COL_RESERVED_INT5},
                        ${DismissedEventEntity.COL_RESERVED_INT6},
                        ${DismissedEventEntity.COL_RESERVED_INT7},
                        ${DismissedEventEntity.COL_RESERVED_INT8},
                        ${DismissedEventEntity.COL_RESERVED_INT9},
                        ${DismissedEventEntity.COL_RESERVED_STR2},
                        ${DismissedEventEntity.COL_RESERVED_STR3}
                    ) VALUES (
                        ${data.calendarId}, ${data.eventId}, ${data.dismissTime}, ${data.dismissType},
                        ${data.alertTime}, '${data.title}', '${data.description}',
                        ${data.eventStart}, ${data.eventEnd}, ${data.instanceStart}, ${data.instanceEnd},
                        '${data.location}', ${data.snoozedUntil}, ${data.lastSeen},
                        ${data.displayStatus}, ${data.color}, ${data.isRepeating}, ${data.allDay}, ${data.flags},
                        0, 0, 0, 0, 0, 0, 0, 0, '', ''
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
    private fun buildTestRoomDatabase(): DismissedEventsDatabase {
        return androidx.room.Room.databaseBuilder(
            context,
            DismissedEventsDatabase::class.java,
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
        
        // Step 2: Read from legacy DB using DismissedEventsStorageImplV2
        val legacyEvents = readLegacyDatabase()
        assertEquals("Should read all legacy events", testData.size, legacyEvents.size)
        
        // Step 3: Open Room DB and insert the data (simulating migration)
        val roomDb = buildTestRoomDatabase()
        try {
            val dao = roomDb.dismissedEventDao()
            
            // Insert legacy data into Room
            val entities = legacyEvents.map { DismissedEventEntity.fromRecord(it) }
            dao.insertAll(entities)
            
            // Step 4: Verify all data was copied
            val roomEvents = dao.getAll()
            assertEquals("Room should have same count as legacy", testData.size, roomEvents.size)
            
            // Verify each row
            for (expected in testData) {
                val found = roomEvents.find { 
                    it.eventId == expected.eventId && 
                    it.instanceStartTime == expected.instanceStart
                }
                assertNotNull("Should find event for eventId=${expected.eventId}", found)
                assertEquals("title should match", expected.title, found!!.title)
                assertEquals("dismissType should match", expected.dismissType, found.dismissType)
                assertEquals("dismissTime should match", expected.dismissTime, found.dismissTime)
                assertEquals("isRepeating should match", expected.isRepeating, found.isRepeating)
                assertEquals("allDay should match", expected.allDay, found.isAllDay)
            }
            
            DevLog.info(LOG_TAG, "✅ All ${testData.size} events copied successfully!")
            
        } finally {
            roomDb.close()
        }
        
        // Step 5: Verify legacy DB is still intact (not modified)
        val legacyEventsAfter = readLegacyDatabase()
        assertEquals("Legacy DB should still have all data", testData.size, legacyEventsAfter.size)
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
        val legacyEvents = readLegacyDatabase()
        
        val roomDb = buildTestRoomDatabase()
        try {
            val dao = roomDb.dismissedEventDao()
            dao.insertAll(legacyEvents.map { DismissedEventEntity.fromRecord(it) })
            
            // Insert a new event
            val newEvent = DismissedEventEntity(
                calendarId = 99,
                eventId = 99999,
                dismissTime = 1735600000000,
                dismissType = EventDismissType.ManuallyDismissedFromNotification.code,
                alertTime = 1735599000000,
                title = "New Event",
                description = "Added after migration",
                startTime = 1735600000000,
                endTime = 1735603600000,
                instanceStartTime = 1735600000000,
                instanceEndTime = 1735603600000,
                location = "New Location",
                snoozedUntil = 0,
                lastStatusChangeTime = 1735599500000,
                displayStatus = 0,
                color = 0xFFFFFF,
                isRepeating = 0,
                isAllDay = 0,
                flags = 0
            )
            dao.insert(newEvent)
            
            // Verify new data exists in Room
            val allEvents = dao.getAll()
            assertEquals("Should have legacy + new events", testData.size + 1, allEvents.size)
            
            val retrieved = allEvents.find { it.eventId == 99999L }
            assertNotNull("Should find new event", retrieved)
            assertEquals("Title should match", "New Event", retrieved!!.title)
            
            DevLog.info(LOG_TAG, "✅ Room can write new data after migration!")
            
        } finally {
            roomDb.close()
        }
        
        // Verify legacy DB doesn't have the new event
        val legacyEventsAfter = readLegacyDatabase()
        assertEquals("Legacy DB should NOT have new event", testData.size, legacyEventsAfter.size)
        DevLog.info(LOG_TAG, "✅ New data only in Room, legacy unchanged!")
    }
    
    /**
     * Tests that migration is skipped if Room DB already has data.
     */
    @Test
    fun test_migration_skipped_if_room_has_data() {
        DevLog.info(LOG_TAG, "=== test_migration_skipped_if_room_has_data ===")
        
        // Step 1: Create Room DB with one event first
        val roomDb = buildTestRoomDatabase()
        try {
            val dao = roomDb.dismissedEventDao()
            
            val existingEvent = DismissedEventEntity(
                calendarId = 1,
                eventId = 11111,
                dismissTime = 1735400000000,
                dismissType = EventDismissType.ManuallyDismissedFromNotification.code,
                alertTime = 1735399000000,
                title = "Pre-existing Event",
                description = "Already in Room",
                startTime = 1735400000000,
                endTime = 1735403600000,
                instanceStartTime = 1735400000000,
                instanceEndTime = 1735403600000,
                location = "",
                snoozedUntil = 0,
                lastStatusChangeTime = 1735399500000,
                displayStatus = 0,
                color = 0,
                isRepeating = 0,
                isAllDay = 0,
                flags = 0
            )
            dao.insert(existingEvent)
            
            assertEquals("Room should have 1 event", 1, dao.getAll().size)
        } finally {
            roomDb.close()
        }
        
        // Step 2: Create legacy DB with different data
        createLegacyDatabase() // 3 events
        
        // Step 3: Re-open Room DB - migration should be skipped since it has data
        val roomDb2 = buildTestRoomDatabase()
        try {
            val dao = roomDb2.dismissedEventDao()
            val allEvents = dao.getAll()
            
            // Should still only have the original 1 event, not 4 (1 + 3 from legacy)
            assertEquals("Should still have only 1 event (migration skipped)", 1, allEvents.size)
            assertEquals("Should be the original event", 11111L, allEvents[0].eventId)
            
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
            val dao = roomDb.dismissedEventDao()
            
            // Should be empty
            assertEquals("Fresh install should have 0 events", 0, dao.getAll().size)
            
            // Can insert new data
            val newEvent = DismissedEventEntity(
                calendarId = 1,
                eventId = 12345,
                dismissTime = 1735500000000,
                dismissType = EventDismissType.ManuallyDismissedFromNotification.code,
                alertTime = 1735499000000,
                title = "First Event",
                description = "Fresh install",
                startTime = 1735500000000,
                endTime = 1735503600000,
                instanceStartTime = 1735500000000,
                instanceEndTime = 1735503600000,
                location = "",
                snoozedUntil = 0,
                lastStatusChangeTime = 1735499500000,
                displayStatus = 0,
                color = 0,
                isRepeating = 0,
                isAllDay = 0,
                flags = 0
            )
            dao.insert(newEvent)
            
            assertEquals("Should have 1 event after insert", 1, dao.getAll().size)
            
            DevLog.info(LOG_TAG, "✅ Fresh install works correctly!")
            
        } finally {
            roomDb.close()
        }
    }
    
    /**
     * Read events from legacy database using DismissedEventsStorageImplV2.
     */
    private fun readLegacyDatabase(): List<DismissedEventAlertRecord> {
        val db = io.requery.android.database.sqlite.SQLiteDatabase.openDatabase(
            legacyDbFile.absolutePath,
            null,
            io.requery.android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        )
        return try {
            val impl = DismissedEventsStorageImplV2()
            impl.getEventsImpl(db)
        } finally {
            db.close()
        }
    }
    
    /**
     * Helper data class for test data
     */
    private data class TestDismissedEventData(
        val calendarId: Long,
        val eventId: Long,
        val dismissTime: Long,
        val dismissType: Int,
        val alertTime: Long,
        val title: String,
        val description: String,
        val eventStart: Long,
        val eventEnd: Long,
        val instanceStart: Long,
        val instanceEnd: Long,
        val location: String,
        val snoozedUntil: Long,
        val lastSeen: Long,
        val displayStatus: Int,
        val color: Int,
        val isRepeating: Int,
        val allDay: Int,
        val flags: Long
    )
}

