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
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusSystemClock
import java.io.Closeable

/**
 * Room-based implementation of DismissedEventsStorageInterface.
 * 
 * Replaces the legacy SQLiteOpenHelper-based DismissedEventsStorage.
 * Uses DismissedEventsDatabase with cr-sqlite support via CrSqliteRoomFactory.
 */
class RoomDismissedEventsStorage(
    context: Context,
    private val clock: CNPlusClockInterface = CNPlusSystemClock()
) : DismissedEventsStorageInterface, Closeable {

    private val database = DismissedEventsDatabase.getInstance(context)
    private val dao = database.dismissedEventDao()

    override fun addEvent(type: EventDismissType, event: EventAlertRecord) {
        addEvent(type, clock.currentTimeMillis(), event)
    }

    override fun addEvent(type: EventDismissType, changeTime: Long, event: EventAlertRecord) {
        dao.insert(DismissedEventEntity.fromEventRecord(event, changeTime, type))
    }

    override fun addEvents(type: EventDismissType, events: Collection<EventAlertRecord>) {
        val changeTime = clock.currentTimeMillis()
        dao.insertAll(events.map { DismissedEventEntity.fromEventRecord(it, changeTime, type) })
    }

    override fun deleteEvent(entry: DismissedEventAlertRecord) {
        dao.deleteByKey(entry.event.eventId, entry.event.instanceStartTime)
    }

    override fun deleteEvent(event: EventAlertRecord) {
        dao.deleteByKey(event.eventId, event.instanceStartTime)
    }

    override fun clearHistory() {
        dao.deleteAll()
    }

    override fun purgeOld(currentTime: Long, maxLiveTime: Long) {
        val cutoffTime = currentTime - maxLiveTime
        dao.deleteOlderThan(cutoffTime)
    }

    override val events: List<DismissedEventAlertRecord>
        get() = dao.getAll().map { it.toRecord() }

    override val eventsForDisplay: List<DismissedEventAlertRecord>
        get() = dao.getAll()
            .map { it.toRecord() }
            .sortedByDescending { it.dismissTime }

    override fun close() {
        // Room handles connection management via singleton pattern
        // Individual close is not needed - database will be closed when app terminates
    }
}

