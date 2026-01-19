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
import io.requery.android.database.sqlite.SQLiteDatabase
import com.github.quarck.calnotify.database.SQLiteOpenHelper
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.detailed
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusSystemClock
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.customUse

/**
 * Legacy SQLiteOpenHelper-based implementation of DismissedEventsStorageInterface.
 * 
 * This is the original implementation preserved as a fallback in case Room migration fails.
 * Uses the original "DismissedEvents" database file.
 */
class LegacyDismissedEventsStorage(
    val context: Context,
    private val clock: CNPlusClockInterface = CNPlusSystemClock()
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION), DismissedEventsStorageInterface {

    private var impl: DismissedEventsStorageImplInterface

    init {
        impl = DismissedEventsStorageImplV2();
    }

    override fun onCreate(db: SQLiteDatabase)
            = impl.createDb(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        DevLog.info(LOG_TAG, "onUpgrade $oldVersion -> $newVersion")

        if (oldVersion == newVersion)
            return

        if (newVersion != DATABASE_VERSION_V2)
            throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")

        val implOld =
                when (oldVersion) {
                    DATABASE_VERSION_V1 -> DismissedEventsStorageImplV1()
                    else -> throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")
                }

        try {
            impl.createDb(db)

            val events = implOld.getEventsImpl(db)

            DevLog.info(LOG_TAG, "${events.size} requests to convert")

            for ((event, time, type) in events) {
                impl.addEventImpl(db, type, time, event)
                implOld.deleteEventImpl(db, event)

                DevLog.debug(LOG_TAG, "Done event ${event.eventId}, inst ${event.instanceStartTime}")
            }

            if (implOld.getEventsImpl(db).isEmpty()) {
                DevLog.info(LOG_TAG, "Finally - dropping old tables")
                implOld.dropAll(db)
            }
            else {
                throw Exception("DB Upgrade failed: some requests are still in the old version of DB")
            }

        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception during DB upgrade $oldVersion -> $newVersion: ${ex.detailed}")
            throw ex
        }
    }

    override fun addEvent(type: EventDismissType, event: EventAlertRecord)
            = addEvent(type, clock.currentTimeMillis(), event)

    override fun addEvent(type: EventDismissType, changeTime: Long, event: EventAlertRecord)
            = synchronized(LegacyDismissedEventsStorage::class.java) { writableDatabase.customUse { impl.addEventImpl(it, type, changeTime, event) } }

    override fun addEvents(type: EventDismissType, events: Collection<EventAlertRecord>)
            = synchronized(LegacyDismissedEventsStorage::class.java) { writableDatabase.customUse { impl.addEventsImpl(it, type, clock.currentTimeMillis(), events) } }

    override fun deleteEvent(entry: DismissedEventAlertRecord)
            = synchronized(LegacyDismissedEventsStorage::class.java) { writableDatabase.customUse { impl.deleteEventImpl(it, entry) } }

    override fun deleteEvent(event: EventAlertRecord)
            = synchronized(LegacyDismissedEventsStorage::class.java) { writableDatabase.customUse { impl.deleteEventImpl(it, event) } }

    override fun clearHistory()
            = synchronized(LegacyDismissedEventsStorage::class.java) { writableDatabase.customUse { impl.clearHistoryImpl(it) } }

    override val events: List<DismissedEventAlertRecord>
        get() = synchronized(LegacyDismissedEventsStorage::class.java) { readableDatabase.customUse { impl.getEventsImpl(it) } }

    override val eventsForDisplay: List<DismissedEventAlertRecord>
        get() = synchronized(LegacyDismissedEventsStorage::class.java) { 
            readableDatabase.customUse { impl.getEventsImpl(it) }
                .sortedByDescending { it.dismissTime }
        }

    override fun purgeOld(currentTime: Long, maxLiveTime: Long)
            = events.filter { (currentTime - it.dismissTime) > maxLiveTime }.forEach { deleteEvent(it) }

    override fun close() = super.close()

    companion object {
        private val LOG_TAG = "LegacyDismissedEventsStorage"

        private const val DATABASE_VERSION_V1 = 1
        private const val DATABASE_VERSION_V2 = 2
        private const val DATABASE_CURRENT_VERSION = DATABASE_VERSION_V2

        private const val DATABASE_NAME = "DismissedEvents"
    }
}

