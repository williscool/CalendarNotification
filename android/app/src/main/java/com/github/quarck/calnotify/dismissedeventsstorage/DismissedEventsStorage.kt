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

package com.github.quarck.calnotify.dismissedeventsstorage

import android.content.Context
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusSystemClock

/**
 * DismissedEventsStorage - tracks dismissed calendar event alerts.
 * 
 * Attempts to use Room database with cr-sqlite support.
 * Falls back to legacy SQLiteOpenHelper if Room migration fails.
 * 
 * This fallback strategy ensures:
 * - No data loss if migration has bugs
 * - Future app versions can retry migration
 * - Legacy database is preserved untouched
 */
class DismissedEventsStorage private constructor(
    result: Pair<DismissedEventsStorageInterface, Boolean>
) : DismissedEventsStorageInterface by result.first {
    
    private val delegate: DismissedEventsStorageInterface = result.first
    val isUsingRoom: Boolean = result.second
    
    constructor(context: Context, clock: CNPlusClockInterface = CNPlusSystemClock()) : this(createStorage(context, clock))

    override fun close() {
        delegate.close()
    }

    companion object {
        private const val LOG_TAG = "DismissedEventsStorage"
        
        private fun createStorage(context: Context, clock: CNPlusClockInterface): Pair<DismissedEventsStorageInterface, Boolean> {
            return try {
                DevLog.info(LOG_TAG, "Attempting to use Room storage...")
                val roomStorage = RoomDismissedEventsStorage(context, clock)
                DevLog.info(LOG_TAG, "✅ Using Room storage")
                Pair(roomStorage, true)
            } catch (e: DismissedEventsMigrationException) {
                DevLog.error(LOG_TAG, "⚠️ Room migration failed, falling back to legacy storage: ${e.message}")
                Pair(LegacyDismissedEventsStorage(context, clock), false)
            }
        }
    }
}
