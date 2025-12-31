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
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.EventAlertFlags
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.setFlag
import java.io.Closeable

/**
 * Room-based implementation of EventsStorageInterface.
 * 
 * Replaces the legacy SQLiteOpenHelper-based EventsStorage.
 * Uses EventsDatabase with cr-sqlite support via CrSqliteRoomFactory.
 */
class RoomEventsStorage(context: Context) : EventsStorageInterface, Closeable {

    private val database = EventsDatabase.getInstance(context)
    private val dao = database.eventAlertDao()

    override fun addEvent(event: EventAlertRecord): Boolean {
        // Assign notification ID if not set
        val eventWithId = if (event.notificationId == 0) {
            event.copy(notificationId = nextNotificationId())
        } else {
            event
        }
        
        // Check if already exists - if so, preserve original notification ID
        val existing = dao.getByKey(eventWithId.eventId, eventWithId.instanceStartTime)
        if (existing != null) {
            val merged = eventWithId.copy(notificationId = existing.notificationId ?: eventWithId.notificationId)
            return dao.update(EventAlertEntity.fromRecord(merged)) == 1
        }
        
        return dao.insert(EventAlertEntity.fromRecord(eventWithId)) != -1L
    }

    override fun addEvents(events: List<EventAlertRecord>): Boolean {
        var success = true
        database.runInTransaction {
            for (event in events) {
                if (!addEvent(event)) {
                    success = false
                }
            }
        }
        return success
    }

    override fun updateEvent(
        event: EventAlertRecord,
        alertTime: Long?,
        title: String?,
        snoozedUntil: Long?,
        startTime: Long?,
        endTime: Long?,
        location: String?,
        lastStatusChangeTime: Long?,
        displayStatus: EventDisplayStatus?,
        color: Int?,
        isRepeating: Boolean?,
        isMuted: Boolean?
    ): Pair<Boolean, EventAlertRecord> {
        var newFlags = event.flags
        if (isMuted != null) {
            newFlags = event.flags.setFlag(EventAlertFlags.IS_MUTED, isMuted)
        }

        val newEvent = event.copy(
            alertTime = alertTime ?: event.alertTime,
            title = title ?: event.title,
            snoozedUntil = snoozedUntil ?: event.snoozedUntil,
            startTime = startTime ?: event.startTime,
            endTime = endTime ?: event.endTime,
            location = location ?: event.location,
            lastStatusChangeTime = lastStatusChangeTime ?: event.lastStatusChangeTime,
            displayStatus = displayStatus ?: event.displayStatus,
            color = color ?: event.color,
            isRepeating = isRepeating ?: event.isRepeating,
            flags = newFlags
        )

        val success = updateEvent(newEvent)
        return Pair(success, newEvent)
    }

    override fun updateEvents(
        events: List<EventAlertRecord>,
        alertTime: Long?,
        title: String?,
        snoozedUntil: Long?,
        startTime: Long?,
        endTime: Long?,
        location: String?,
        lastStatusChangeTime: Long?,
        displayStatus: EventDisplayStatus?,
        color: Int?,
        isRepeating: Boolean?
    ): Boolean {
        val newEvents = events.map { event ->
            event.copy(
                alertTime = alertTime ?: event.alertTime,
                title = title ?: event.title,
                snoozedUntil = snoozedUntil ?: event.snoozedUntil,
                startTime = startTime ?: event.startTime,
                endTime = endTime ?: event.endTime,
                location = location ?: event.location,
                lastStatusChangeTime = lastStatusChangeTime ?: event.lastStatusChangeTime,
                displayStatus = displayStatus ?: event.displayStatus,
                color = color ?: event.color,
                isRepeating = isRepeating ?: event.isRepeating
            )
        }
        return updateEvents(newEvents)
    }

    override fun updateEventAndInstanceTimes(event: EventAlertRecord, instanceStart: Long, instanceEnd: Long): Boolean {
        val updatedEvent = event.copy(instanceStartTime = instanceStart, instanceEndTime = instanceEnd)
        // Need to delete old key and insert new since PK changed - wrap in transaction for atomicity
        // Only succeed if original record existed (matches legacy UPDATE semantics)
        var success = false
        database.runInTransaction {
            val deleted = dao.deleteByKey(event.eventId, event.instanceStartTime)
            if (deleted == 1) {
                success = dao.insert(EventAlertEntity.fromRecord(updatedEvent)) != -1L
            }
            // If deleted == 0, record didn't exist - success stays false (matches legacy)
        }
        return success
    }

    override fun updateEventsAndInstanceTimes(events: Collection<EventWithNewInstanceTime>): Boolean {
        var success = true
        database.runInTransaction {
            for ((event, instanceStart, instanceEnd) in events) {
                if (!updateEventAndInstanceTimes(event, instanceStart, instanceEnd)) {
                    success = false
                }
            }
        }
        return success
    }

    override fun updateEvent(event: EventAlertRecord): Boolean {
        return dao.update(EventAlertEntity.fromRecord(event)) == 1
    }

    override fun updateEvents(events: List<EventAlertRecord>): Boolean {
        var success = true
        database.runInTransaction {
            for (event in events) {
                if (dao.update(EventAlertEntity.fromRecord(event)) != 1) {
                    success = false
                }
            }
        }
        return success
    }

    override fun getEvent(eventId: Long, instanceStartTime: Long): EventAlertRecord? {
        return dao.getByKey(eventId, instanceStartTime)?.toRecord()
    }

    override fun getEventInstances(eventId: Long): List<EventAlertRecord> {
        return dao.getByEventId(eventId).map { it.toRecord() }
    }

    override fun deleteEvent(eventId: Long, instanceStartTime: Long): Boolean {
        return dao.deleteByKey(eventId, instanceStartTime) == 1
    }

    override fun deleteEvent(ev: EventAlertRecord): Boolean {
        return deleteEvent(ev.eventId, ev.instanceStartTime)
    }

    override fun deleteEvents(events: Collection<EventAlertRecord>): Int {
        var count = 0
        database.runInTransaction {
            for (event in events) {
                if (deleteEvent(event.eventId, event.instanceStartTime)) {
                    count++
                }
            }
        }
        return count
    }

    override fun deleteAllEvents(): Boolean {
        dao.deleteAllRows()
        return true
    }

    override val events: List<EventAlertRecord>
        get() = dao.getAll().map { it.toRecord() }

    override fun close() {
        // Room handles connection management via singleton pattern
        // Individual close is not needed - database will be closed when app terminates
    }

    private fun nextNotificationId(): Int {
        val maxId = dao.getMaxNotificationId() ?: 0
        return if (maxId < Consts.NOTIFICATION_ID_DYNAMIC_FROM) {
            Consts.NOTIFICATION_ID_DYNAMIC_FROM
        } else {
            maxId + 1
        }
    }
}

