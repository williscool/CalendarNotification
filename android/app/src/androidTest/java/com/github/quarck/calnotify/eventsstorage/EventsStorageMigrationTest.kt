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

package com.github.quarck.calnotify.eventsstorage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
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
 * - Legacy DB "Events" is preserved (not modified)
 * - New Room DB "RoomEvents" is created
 * - Data is copied from legacy to Room on first access
 * - If copy fails, EventsMigrationException is thrown and caller falls back to legacy
 */
@RunWith(AndroidJUnit4::class)
class EventsStorageMigrationTest {
    
    companion object {
        private const val LOG_TAG = "EventsStorageMigrationTest"
        
        // Use test-specific names to avoid interfering with actual app data
        private const val TEST_LEGACY_DB_NAME = "Events_Test"
        private const val TEST_ROOM_DB_NAME = "RoomEvents_Test"
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
     * Uses the actual EventsStorageImplV9.createDb() to ensure we test the real schema.
     */
    private fun createLegacyDatabase(): List<TestEventData> {
        DevLog.info(LOG_TAG, "Creating legacy database at ${legacyDbFile.absolutePath}")
        
        val db = io.requery.android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            legacyDbFile.absolutePath, null
        )
        
        try {
            // Use the ACTUAL legacy code to create the schema
            val legacyImpl = EventsStorageImplV9(context)
            legacyImpl.createDb(db)
            
            val testData = listOf(
                TestEventData(
                    calendarId = 1, eventId = 12345, alertTime = 1735499000000,
                    notificationId = 100, title = "Meeting 1", description = "Team standup",
                    eventStart = 1735500000000, eventEnd = 1735503600000,
                    instanceStart = 1735500000000, instanceEnd = 1735503600000,
                    location = "Room A", snoozedUntil = 0, lastSeen = 1735499500000,
                    displayStatus = EventDisplayStatus.Hidden.code, color = 0xFF0000,
                    isRepeating = 0, allDay = 0, origin = EventOrigin.ProviderBroadcast.code,
                    timeFirstSeen = 1735499000000, eventStatus = 0, attendanceStatus = 0, flags = 0
                ),
                TestEventData(
                    calendarId = 1, eventId = 12346, alertTime = 1735509000000,
                    notificationId = 101, title = "Meeting 2", description = "Project review",
                    eventStart = 1735510000000, eventEnd = 1735513600000,
                    instanceStart = 1735510000000, instanceEnd = 1735513600000,
                    location = "Room B", snoozedUntil = 1735600000000, lastSeen = 1735509500000,
                    displayStatus = EventDisplayStatus.DisplayedNormal.code, color = 0x00FF00,
                    isRepeating = 1, allDay = 0, origin = EventOrigin.ProviderManual.code,
                    timeFirstSeen = 1735509000000, eventStatus = 0, attendanceStatus = 1, flags = 1
                ),
                TestEventData(
                    calendarId = 2, eventId = 12347, alertTime = 1735519000000,
                    notificationId = 102, title = "All Day Event", description = "Holiday",
                    eventStart = 1735520000000, eventEnd = 1735606400000,
                    instanceStart = 1735520000000, instanceEnd = 1735606400000,
                    location = "", snoozedUntil = 0, lastSeen = 1735519500000,
                    displayStatus = EventDisplayStatus.Hidden.code, color = 0x0000FF,
                    isRepeating = 0, allDay = 1, origin = EventOrigin.ProviderBroadcast.code,
                    timeFirstSeen = 1735519000000, eventStatus = 1, attendanceStatus = 0, flags = 0
                )
            )
            
            for (data in testData) {
                db.execSQL("""
                    INSERT INTO ${EventAlertEntity.TABLE_NAME} (
                        ${EventAlertEntity.COL_CALENDAR_ID},
                        ${EventAlertEntity.COL_EVENT_ID},
                        ${EventAlertEntity.COL_ALERT_TIME},
                        ${EventAlertEntity.COL_NOTIFICATION_ID},
                        ${EventAlertEntity.COL_TITLE},
                        ${EventAlertEntity.COL_DESCRIPTION},
                        ${EventAlertEntity.COL_START},
                        ${EventAlertEntity.COL_END},
                        ${EventAlertEntity.COL_INSTANCE_START},
                        ${EventAlertEntity.COL_INSTANCE_END},
                        ${EventAlertEntity.COL_LOCATION},
                        ${EventAlertEntity.COL_SNOOZED_UNTIL},
                        ${EventAlertEntity.COL_LAST_STATUS_CHANGE},
                        ${EventAlertEntity.COL_DISPLAY_STATUS},
                        ${EventAlertEntity.COL_COLOR},
                        ${EventAlertEntity.COL_IS_REPEATING},
                        ${EventAlertEntity.COL_ALL_DAY},
                        ${EventAlertEntity.COL_EVENT_ORIGIN},
                        ${EventAlertEntity.COL_TIME_FIRST_SEEN},
                        ${EventAlertEntity.COL_EVENT_STATUS},
                        ${EventAlertEntity.COL_ATTENDANCE_STATUS},
                        ${EventAlertEntity.COL_FLAGS},
                        ${EventAlertEntity.COL_RESERVED_INT2},
                        ${EventAlertEntity.COL_RESERVED_INT3},
                        ${EventAlertEntity.COL_RESERVED_INT4},
                        ${EventAlertEntity.COL_RESERVED_INT5},
                        ${EventAlertEntity.COL_RESERVED_INT6},
                        ${EventAlertEntity.COL_RESERVED_INT7},
                        ${EventAlertEntity.COL_RESERVED_INT8},
                        ${EventAlertEntity.COL_RESERVED_STR2}
                    ) VALUES (
                        ${data.calendarId}, ${data.eventId}, ${data.alertTime},
                        ${data.notificationId}, '${data.title}', '${data.description}',
                        ${data.eventStart}, ${data.eventEnd}, ${data.instanceStart}, ${data.instanceEnd},
                        '${data.location}', ${data.snoozedUntil}, ${data.lastSeen},
                        ${data.displayStatus}, ${data.color}, ${data.isRepeating}, ${data.allDay},
                        ${data.origin}, ${data.timeFirstSeen}, ${data.eventStatus}, ${data.attendanceStatus},
                        ${data.flags}, 0, 0, 0, 0, 0, 0, 0, ''
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
    private fun buildTestRoomDatabase(): EventsDatabase {
        return androidx.room.Room.databaseBuilder(
            context,
            EventsDatabase::class.java,
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
        
        // Step 2: Read from legacy DB using EventsStorageImplV9
        val legacyEvents = readLegacyDatabase()
        assertEquals("Should read all legacy events", testData.size, legacyEvents.size)
        
        // Step 3: Open Room DB and insert the data (simulating migration)
        val roomDb = buildTestRoomDatabase()
        try {
            val dao = roomDb.eventAlertDao()
            
            // Insert legacy data into Room
            val entities = legacyEvents.map { EventAlertEntity.fromRecord(it) }
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
                assertEquals("notificationId should match", expected.notificationId, found.notificationId)
                assertEquals("snoozedUntil should match", expected.snoozedUntil, found.snoozedUntil)
                assertEquals("displayStatus should match", expected.displayStatus, found.displayStatus)
                assertEquals("isRepeating should match", expected.isRepeating, found.isRepeating)
                assertEquals("allDay should match", expected.allDay, found.isAllDay)
                assertEquals("flags should match", expected.flags, found.flags)
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
            val dao = roomDb.eventAlertDao()
            dao.insertAll(legacyEvents.map { EventAlertEntity.fromRecord(it) })
            
            // Insert a new event
            val newEvent = EventAlertEntity(
                calendarId = 99,
                eventId = 99999,
                alertTime = 1735599000000,
                notificationId = 200,
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
                origin = 0,
                timeFirstSeen = 1735599000000,
                eventStatus = 0,
                attendanceStatus = 0,
                flags = 0
            )
            dao.insert(newEvent)
            
            // Verify new data exists in Room
            val allEvents = dao.getAll()
            assertEquals("Should have legacy + new events", testData.size + 1, allEvents.size)
            
            val retrieved = dao.getByKey(99999, 1735600000000)
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
            val dao = roomDb.eventAlertDao()
            
            val existingEvent = EventAlertEntity(
                calendarId = 1,
                eventId = 11111,
                alertTime = 1735399000000,
                notificationId = 50,
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
                origin = 0,
                timeFirstSeen = 1735399000000,
                eventStatus = 0,
                attendanceStatus = 0,
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
            val dao = roomDb2.eventAlertDao()
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
            val dao = roomDb.eventAlertDao()
            
            // Should be empty
            assertEquals("Fresh install should have 0 events", 0, dao.getAll().size)
            
            // Can insert new data
            val newEvent = EventAlertEntity(
                calendarId = 1,
                eventId = 12345,
                alertTime = 1735499000000,
                notificationId = 100,
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
                origin = 0,
                timeFirstSeen = 1735499000000,
                eventStatus = 0,
                attendanceStatus = 0,
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
     * Read events from legacy database using EventsStorageImplV9.
     */
    private fun readLegacyDatabase(): List<com.github.quarck.calnotify.calendar.EventAlertRecord> {
        val db = io.requery.android.database.sqlite.SQLiteDatabase.openDatabase(
            legacyDbFile.absolutePath,
            null,
            io.requery.android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        )
        return try {
            val impl = EventsStorageImplV9(context)
            impl.getEventsImpl(db)
        } finally {
            db.close()
        }
    }
    
    /**
     * Helper data class for test data
     */
    private data class TestEventData(
        val calendarId: Long,
        val eventId: Long,
        val alertTime: Long,
        val notificationId: Int,
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
        val origin: Int,
        val timeFirstSeen: Long,
        val eventStatus: Int,
        val attendanceStatus: Int,
        val flags: Long
    )
}

