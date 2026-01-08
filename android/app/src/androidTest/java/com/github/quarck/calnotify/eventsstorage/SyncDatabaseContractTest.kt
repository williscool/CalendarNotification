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
import com.github.quarck.calnotify.logs.DevLog
import expo.modules.mymodule.MyModule
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Contract tests for sync database consistency.
 * 
 * These tests verify that the expo module (MyModule) and main app (EventsStorage)
 * agree on SharedPreferences keys and database names. This prevents the bug where
 * Room migration caused native code to write to "RoomEvents" while sync code
 * was hardcoded to read from "Events".
 * 
 * The fix: Communication via SharedPreferences with matching constants.
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
     * CRITICAL: Verifies that MyModule and EventsStorage use the same SharedPreferences file name.
     * If these don't match, the expo module won't read what the app writes.
     */
    @Test
    fun sharedPreferencesFileNameMatches() {
        assertEquals(
            "MyModule and EventsStorage must use the same SharedPreferences file name",
            EventsStorage.STORAGE_PREFS_NAME,
            MyModule.STORAGE_PREFS_NAME
        )
        DevLog.info(LOG_TAG, "✅ Prefs file name matches: ${EventsStorage.STORAGE_PREFS_NAME}")
    }
    
    /**
     * CRITICAL: Verifies that MyModule and EventsStorage use the same key for database name.
     */
    @Test
    fun databaseNamePreferenceKeyMatches() {
        assertEquals(
            "MyModule and EventsStorage must use the same key for active_db_name",
            EventsStorage.PREF_ACTIVE_DB_NAME,
            MyModule.PREF_ACTIVE_DB_NAME
        )
        DevLog.info(LOG_TAG, "✅ DB name key matches: ${EventsStorage.PREF_ACTIVE_DB_NAME}")
    }
    
    /**
     * CRITICAL: Verifies that MyModule and EventsStorage use the same key for is_using_room flag.
     */
    @Test
    fun isUsingRoomPreferenceKeyMatches() {
        assertEquals(
            "MyModule and EventsStorage must use the same key for is_using_room",
            EventsStorage.PREF_IS_USING_ROOM,
            MyModule.PREF_IS_USING_ROOM
        )
        DevLog.info(LOG_TAG, "✅ isUsingRoom key matches: ${EventsStorage.PREF_IS_USING_ROOM}")
    }
    
    /**
     * CRITICAL: Verifies that MyModule's Room database name constant matches EventsDatabase.
     */
    @Test
    fun roomDatabaseNameConstantMatches() {
        assertEquals(
            "MyModule.ROOM_DATABASE_NAME must match EventsDatabase.DATABASE_NAME",
            EventsDatabase.DATABASE_NAME,
            MyModule.ROOM_DATABASE_NAME
        )
        DevLog.info(LOG_TAG, "✅ Room DB name matches: ${EventsDatabase.DATABASE_NAME}")
    }
    
    /**
     * CRITICAL: Verifies that MyModule's legacy database name constant matches EventsDatabase.
     */
    @Test
    fun legacyDatabaseNameConstantMatches() {
        assertEquals(
            "MyModule.LEGACY_DATABASE_NAME must match EventsDatabase.LEGACY_DATABASE_NAME",
            EventsDatabase.LEGACY_DATABASE_NAME,
            MyModule.LEGACY_DATABASE_NAME
        )
        DevLog.info(LOG_TAG, "✅ Legacy DB name matches: ${EventsDatabase.LEGACY_DATABASE_NAME}")
    }
    
    /**
     * Integration test: Verifies that after EventsStorage writes to SharedPreferences,
     * reading with MyModule's keys returns the correct values.
     * 
     * This is the actual contract - what EventsStorage writes, MyModule must be able to read.
     */
    @Test
    fun myModuleCanReadWhatEventsStorageWrites() {
        // Create storage - this writes to SharedPreferences
        val storage = EventsStorage(context)
        
        // Read using MyModule's constants (simulating what the expo module does)
        val prefs = context.getSharedPreferences(MyModule.STORAGE_PREFS_NAME, Context.MODE_PRIVATE)
        val dbNameFromPrefs = prefs.getString(MyModule.PREF_ACTIVE_DB_NAME, MyModule.LEGACY_DATABASE_NAME)
        val isRoomFromPrefs = prefs.getBoolean(MyModule.PREF_IS_USING_ROOM, false)
        
        // Verify they match what EventsStorage reports
        val expectedDbName = if (storage.isUsingRoom) {
            EventsDatabase.DATABASE_NAME
        } else {
            EventsDatabase.LEGACY_DATABASE_NAME
        }
        
        assertEquals(
            "DB name read via MyModule's keys should match EventsStorage state",
            expectedDbName,
            dbNameFromPrefs
        )
        
        assertEquals(
            "isUsingRoom read via MyModule's keys should match EventsStorage state",
            storage.isUsingRoom,
            isRoomFromPrefs
        )
        
        DevLog.info(LOG_TAG, "✅ MyModule can read EventsStorage's values: dbName=$dbNameFromPrefs, isRoom=$isRoomFromPrefs")
        
        storage.close()
    }
}
