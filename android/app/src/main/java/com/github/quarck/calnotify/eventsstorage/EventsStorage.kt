//
//   Calendar Notifications Plus  
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
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
import com.github.quarck.calnotify.logs.DevLog
import java.io.Closeable

/**
 * EventsStorage - stores active calendar event notifications.
 * 
 * Attempts to use Room database with cr-sqlite support.
 * Falls back to legacy SQLiteOpenHelper if Room migration fails.
 * 
 * This fallback strategy ensures:
 * - No data loss if migration has bugs
 * - Future app versions can retry migration
 * - Legacy database is preserved untouched
 */
class EventsStorage private constructor(
    context: Context,
    result: Pair<EventsStorageInterface, Boolean>
) : EventsStorageInterface by result.first, Closeable {
    
    private val delegate: EventsStorageInterface = result.first
    val isUsingRoom: Boolean = result.second
    
    init {
        // Write storage state to SharedPreferences for cross-module communication
        // (expo native module reads this to know which database to use for sync)
        // This prefs file is NOT in backup_rules.xml, so it won't be backed up
        writeStorageState(context, isUsingRoom)
    }
    
    constructor(context: Context) : this(context, createStorage(context))

    override fun close() {
        (delegate as? Closeable)?.close()
    }

    companion object {
        private const val LOG_TAG = "EventsStorage"
        
        // SharedPreferences for cross-module communication (not backed up - see backup_rules.xml)
        // These constants must match MyModule.kt in the expo module
        const val STORAGE_PREFS_NAME = "events_storage_state"
        const val PREF_ACTIVE_DB_NAME = "active_db_name"
        const val PREF_IS_USING_ROOM = "is_using_room"

        private fun createStorage(context: Context): Pair<EventsStorageInterface, Boolean> {
            return try {
                DevLog.info(LOG_TAG, "Attempting to use Room storage...")
                val roomStorage = RoomEventsStorage(context)
                DevLog.info(LOG_TAG, "✅ Using Room storage")
                Pair(roomStorage, true)
            } catch (e: EventsMigrationException) {
                DevLog.error(LOG_TAG, "⚠️ Room migration failed, falling back to legacy storage: ${e.message}")
                Pair(LegacyEventsStorage(context), false)
            }
        }
        
        private fun writeStorageState(context: Context, isUsingRoom: Boolean) {
            val dbName = if (isUsingRoom) {
                EventsDatabase.DATABASE_NAME
            } else {
                EventsDatabase.LEGACY_DATABASE_NAME
            }
            
            context.getSharedPreferences(STORAGE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_ACTIVE_DB_NAME, dbName)
                .putBoolean(PREF_IS_USING_ROOM, isUsingRoom)
                .apply()
            
            DevLog.info(LOG_TAG, "Wrote storage state to prefs: dbName=$dbName, isUsingRoom=$isUsingRoom")
        }
    }
}
