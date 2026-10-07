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

package com.github.quarck.calnotify.identitystorage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.github.quarck.calnotify.database.CrSqliteRoomFactory

/**
 * Room database holding portable identity for stored events.
 *
 * Unlike the other three databases in this app, this one has **no legacy
 * predecessor and no migration path** — it is new, starts at version 1, and
 * begins empty. Nothing to copy, nothing to fall back to.
 *
 * It is deliberately separate from `RoomEvents` rather than extra columns on
 * `eventsV9`:
 *
 * - The reserved `s2` column is a scarce one-shot resource, better spent on
 *   something that must live in the event row. Identity is read only during a
 *   restore, and joins by `(eventId, instanceStartTime)` when needed.
 * - The sync layer targets the `eventsV9` table by name, so keeping identity
 *   out of that table keeps account emails out of the Supabase payload by
 *   construction rather than by remembering to filter them.
 *
 * Backed up automatically: `res/xml/backup_rules.xml` includes
 * `domain="database"`, which is essential — this database is useless unless it
 * restores alongside the events it describes.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
@Database(
    entities = [EventIdentityEntity::class],
    version = 1,
    exportSchema = false
)
abstract class EventIdentityDatabase : RoomDatabase() {

    abstract fun eventIdentityDao(): EventIdentityDao

    companion object {
        internal const val DATABASE_NAME = "RoomEventIdentity"

        @Volatile
        private var INSTANCE: EventIdentityDatabase? = null

        fun getInstance(context: Context): EventIdentityDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, DATABASE_NAME).also { INSTANCE = it }
            }
        }

        /**
         * Build against an explicit database name. Tests use this to work on a
         * throwaway file instead of the real one.
         */
        fun buildDatabase(context: Context, databaseName: String): EventIdentityDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                EventIdentityDatabase::class.java,
                databaseName
            )
                .openHelperFactory(CrSqliteRoomFactory())
                // Matches the other storages: callers are already on background
                // threads and use synchronous queries throughout.
                .allowMainThreadQueries()
                .build()

        /** Drop the cached singleton. Tests only. */
        internal fun resetInstanceForTesting() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
