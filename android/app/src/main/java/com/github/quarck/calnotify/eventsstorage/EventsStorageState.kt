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
import com.github.quarck.calnotify.utils.PersistentStorageBase

/**
 * Persists which database storage is active (Room vs Legacy).
 * 
 * Used for cross-module communication - the Expo native module (MyModule.kt)
 * reads these SharedPreferences to know which database to use for sync.
 * 
 * This prefs file is NOT in backup_rules.xml, so it won't be backed up.
 * The value is recalculated on each app launch based on migration success.
 * 
 * CONTRACT: MyModule.kt must use matching constants:
 *   - PREFS_NAME = "events_storage_state"
 *   - PREF_ACTIVE_DB_NAME = "active_db_name" 
 *   - PREF_IS_USING_ROOM = "is_using_room"
 */
class EventsStorageState(ctx: Context) : PersistentStorageBase(ctx, PREFS_NAME) {
    
    /** The active database name: "RoomEvents" or "Events" */
    var activeDbName by StringProperty(EventsDatabase.DATABASE_NAME, PREF_ACTIVE_DB_NAME)
    
    /** Whether Room storage is being used (true) or legacy fallback (false) */
    var isUsingRoom by BooleanProperty(true, PREF_IS_USING_ROOM)
    
    companion object {
        // SharedPreferences name - must match MyModule.kt
        const val PREFS_NAME = "events_storage_state"
        
        // Key names - must match MyModule.kt
        const val PREF_ACTIVE_DB_NAME = "active_db_name"
        const val PREF_IS_USING_ROOM = "is_using_room"
    }
}
