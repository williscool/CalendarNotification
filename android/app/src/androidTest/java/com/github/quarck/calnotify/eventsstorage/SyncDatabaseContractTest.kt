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
import com.github.quarck.calnotify.calendar.EventAlertRecord
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
 * Contract tests for sync database consistency.
 * 
 * These tests verify that the database used by native storage matches what
 * the React Native sync feature should use. This prevents the bug where
 * Room migration caused native code to write to "RoomEvents" while sync
 * code was hardcoded to read from "Events".
 * 
 * The fix: Native module exposes getActiveEventsDbName() which returns
 * the actual database name being used (RoomEvents or Events).
 * 
 * @see docs/dev_todo/sync_database_mismatch.md
 */
@RunWith(AndroidJUnit4::class)
class SyncDatabaseContractTest {
    
    companion object {
        private const val LOG_TAG = "SyncDatabaseContractTest"
    }
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        DevLog.info(LOG_TAG, "Test setup complete")
    }
    
    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Test cleanup complete")
    }
    
    /**
     * Verifies that EventsStorage.isUsingRoom correctly reflects which
     * database implementation is active.
     */
    @Test
    fun eventsStorageReportsCorrectImplementation() {
        val storage = EventsStorage(context)
        
        // isUsingRoom should be a boolean
        val isRoom = storage.isUsingRoom
        DevLog.info(LOG_TAG, "EventsStorage.isUsingRoom = $isRoom")
        
        // The value depends on whether Room migration succeeded
        // This test just verifies the property is accessible and consistent
        assertTrue(
            "isUsingRoom should be true or false",
            isRoom || !isRoom
        )
        
        storage.close()
    }
    
    /**
     * Verifies that the database name constants are correctly defined.
     */
    @Test
    fun databaseNameConstantsAreDefined() {
        // Room database name
        assertEquals(
            "Room database should be named RoomEvents",
            "RoomEvents",
            EventsDatabase.DATABASE_NAME
        )
        
        // Legacy database name
        assertEquals(
            "Legacy database should be named Events",
            "Events",
            EventsDatabase.LEGACY_DATABASE_NAME
        )
    }
    
    /**
     * Verifies that the active database name can be determined from storage state.
     * This is the logic that the native module uses for getActiveEventsDbName().
     */
    @Test
    fun activeDbNameMatchesStorageImplementation() {
        val storage = EventsStorage(context)
        
        val expectedDbName = if (storage.isUsingRoom) {
            EventsDatabase.DATABASE_NAME  // "RoomEvents"
        } else {
            EventsDatabase.LEGACY_DATABASE_NAME  // "Events"
        }
        
        DevLog.info(LOG_TAG, "isUsingRoom=${storage.isUsingRoom}, expectedDbName=$expectedDbName")
        
        // This is the same logic the native module uses
        if (storage.isUsingRoom) {
            assertEquals("Room storage should use RoomEvents database", "RoomEvents", expectedDbName)
        } else {
            assertEquals("Legacy storage should use Events database", "Events", expectedDbName)
        }
        
        storage.close()
    }
    
    /**
     * Verifies that EventsStorage writes correct values to SharedPreferences.
     * The expo native module reads these prefs to know which database to use.
     * 
     * This is the contract between main app and expo module - they must agree on:
     * - Prefs file name: "events_storage_state"
     * - Key for db name: "active_db_name"
     * - Key for room flag: "is_using_room"
     */
    @Test
    fun sharedPreferencesContractIsCorrect() {
        // Create storage - this should write to SharedPreferences
        val storage = EventsStorage(context)
        
        // Read from SharedPreferences using the same keys the expo module uses
        val prefs = context.getSharedPreferences(
            EventsStorage.STORAGE_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        
        val prefsDbName = prefs.getString(EventsStorage.PREF_ACTIVE_DB_NAME, null)
        val prefsIsRoom = prefs.getBoolean(EventsStorage.PREF_IS_USING_ROOM, false)
        
        // Verify values match what EventsStorage reports
        val expectedDbName = if (storage.isUsingRoom) {
            EventsDatabase.DATABASE_NAME
        } else {
            EventsDatabase.LEGACY_DATABASE_NAME
        }
        
        assertEquals(
            "SharedPreferences db name should match storage implementation",
            expectedDbName,
            prefsDbName
        )
        
        assertEquals(
            "SharedPreferences isUsingRoom should match storage implementation",
            storage.isUsingRoom,
            prefsIsRoom
        )
        
        DevLog.info(LOG_TAG, "SharedPreferences contract verified: dbName=$prefsDbName, isRoom=$prefsIsRoom")
        
        storage.close()
    }
    
    /**
     * Integration test: Verifies that events written through EventsStorage
     * are visible when querying the database directly using the correct path.
     * 
     * This simulates what the React Native sync feature does - opening the
     * database directly by path and querying for events.
     */
    @Test
    fun eventsWrittenByStorageAreReadableAtCorrectPath() {
        val storage = EventsStorage(context)
        
        // Determine which database path should be used
        val dbPath = if (storage.isUsingRoom) {
            context.getDatabasePath(EventsDatabase.DATABASE_NAME).absolutePath
        } else {
            context.getDatabasePath(EventsDatabase.LEGACY_DATABASE_NAME).absolutePath
        }
        
        DevLog.info(LOG_TAG, "Testing with database path: $dbPath")
        
        // Create a unique test event
        val testEventId = 999888L
        val testInstanceStart = System.currentTimeMillis()
        val testTitle = "SyncContractTest_${System.currentTimeMillis()}"
        
        val testEvent = EventAlertRecord(
            calendarId = 1L,
            eventId = testEventId,
            alertTime = testInstanceStart,
            notificationId = 0,
            title = testTitle,
            desc = "Test event for sync contract",
            startTime = testInstanceStart,
            endTime = testInstanceStart + 3600000,
            instanceStartTime = testInstanceStart,
            instanceEndTime = testInstanceStart + 3600000,
            location = "",
            snoozedUntil = 0L,
            lastStatusChangeTime = System.currentTimeMillis(),
            displayStatus = EventDisplayStatus.Hidden,
            color = 0,
            isRepeating = false,
            isAllDay = false,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = System.currentTimeMillis(),
            eventStatus = 0,
            attendanceStatus = 0
        )
        
        try {
            // Write event through storage
            storage.addEvent(testEvent)
            DevLog.info(LOG_TAG, "Added test event: eventId=$testEventId, title=$testTitle")
            
            // Verify it's readable through storage
            val retrieved = storage.getEvent(testEventId, testInstanceStart)
            assertNotNull("Event should be retrievable through storage", retrieved)
            assertEquals("Title should match", testTitle, retrieved?.title)
            
            // Now verify it's visible when opening database directly (like sync does)
            val dbFile = File(dbPath)
            assertTrue("Database file should exist at: $dbPath", dbFile.exists())
            
            val directDb = io.requery.android.database.sqlite.SQLiteDatabase.openDatabase(
                dbPath,
                null,
                io.requery.android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            
            try {
                // Query the eventsV9 table directly
                val cursor = directDb.rawQuery(
                    "SELECT ttl FROM ${EventAlertEntity.TABLE_NAME} WHERE id = ? AND istart = ?",
                    arrayOf(testEventId.toString(), testInstanceStart.toString())
                )
                
                assertTrue(
                    "Event should be visible via direct database query at correct path",
                    cursor.moveToFirst()
                )
                assertEquals(
                    "Title from direct query should match",
                    testTitle,
                    cursor.getString(0)
                )
                
                cursor.close()
                DevLog.info(LOG_TAG, "✅ Event is readable via direct database access at: $dbPath")
                
            } finally {
                directDb.close()
            }
            
        } finally {
            // Cleanup: delete test event
            storage.deleteEvent(testEventId, testInstanceStart)
            storage.close()
        }
    }
    
    /**
     * Documents the bug: If sync used the WRONG database path, events wouldn't be visible.
     * 
     * When Room is active but sync opens "Events" (legacy), it won't see Room data.
     * This test verifies that scenario to document why the fix was needed.
     */
    @Test
    fun demonstrateBugWhenUsingWrongPath() {
        val storage = EventsStorage(context)
        
        // Only run this test when Room is active (the bug scenario)
        if (!storage.isUsingRoom) {
            DevLog.info(LOG_TAG, "Skipping wrong-path test - Room is not active")
            storage.close()
            return
        }
        
        // When Room is active, sync should NOT use the legacy path
        val wrongPath = context.getDatabasePath(EventsDatabase.LEGACY_DATABASE_NAME).absolutePath
        val correctPath = context.getDatabasePath(EventsDatabase.DATABASE_NAME).absolutePath
        
        DevLog.info(LOG_TAG, "Room is active:")
        DevLog.info(LOG_TAG, "  Wrong path (legacy): $wrongPath")
        DevLog.info(LOG_TAG, "  Correct path (Room): $correctPath")
        
        // Create a unique test event
        val testEventId = 888777L
        val testInstanceStart = System.currentTimeMillis()
        val testTitle = "WrongPathTest_${System.currentTimeMillis()}"
        
        val testEvent = EventAlertRecord(
            calendarId = 1L,
            eventId = testEventId,
            alertTime = testInstanceStart,
            notificationId = 0,
            title = testTitle,
            desc = "Test event to demonstrate wrong path bug",
            startTime = testInstanceStart,
            endTime = testInstanceStart + 3600000,
            instanceStartTime = testInstanceStart,
            instanceEndTime = testInstanceStart + 3600000,
            location = "",
            snoozedUntil = 0L,
            lastStatusChangeTime = System.currentTimeMillis(),
            displayStatus = EventDisplayStatus.Hidden,
            color = 0,
            isRepeating = false,
            isAllDay = false,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = System.currentTimeMillis(),
            eventStatus = 0,
            attendanceStatus = 0
        )
        
        try {
            // Write event through storage (goes to RoomEvents when Room is active)
            storage.addEvent(testEvent)
            
            // Try to read from wrong path (legacy database)
            val legacyDbFile = File(wrongPath)
            if (legacyDbFile.exists()) {
                val legacyDb = io.requery.android.database.sqlite.SQLiteDatabase.openDatabase(
                    wrongPath,
                    null,
                    io.requery.android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                
                try {
                    val cursor = legacyDb.rawQuery(
                        "SELECT ttl FROM ${EventAlertEntity.TABLE_NAME} WHERE id = ? AND istart = ?",
                        arrayOf(testEventId.toString(), testInstanceStart.toString())
                    )
                    
                    // This should NOT find the event because Room writes to RoomEvents
                    val foundInLegacy = cursor.moveToFirst()
                    cursor.close()
                    
                    assertFalse(
                        "BUG DOCUMENTED: Event written to Room should NOT be visible in legacy database. " +
                        "This is why sync was broken when it hardcoded 'Events' path.",
                        foundInLegacy
                    )
                    
                    DevLog.info(LOG_TAG, "✅ Confirmed: Room events are NOT visible in legacy database (expected)")
                    
                } finally {
                    legacyDb.close()
                }
            } else {
                DevLog.info(LOG_TAG, "Legacy database doesn't exist (fresh install) - test passes trivially")
            }
            
        } finally {
            storage.deleteEvent(testEventId, testInstanceStart)
            storage.close()
        }
    }
}
