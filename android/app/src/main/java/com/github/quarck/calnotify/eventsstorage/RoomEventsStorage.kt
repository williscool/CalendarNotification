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
import android.database.sqlite.SQLiteConstraintException
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.EventAlertFlags
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.setFlag
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.detailed
import java.io.Closeable

/**
 * Room-based implementation of EventsStorageInterface.
 * 
 * Replaces the legacy SQLiteOpenHelper-based EventsStorage.
 * Uses EventsDatabase with cr-sqlite support via CrSqliteRoomFactory.
 * 
 * All operations are synchronized on the class object to match legacy thread-safety
 * guarantees, particularly for notification ID generation which requires atomic
 * read-then-write.
 */
class RoomEventsStorage(context: Context) : EventsStorageInterface, Closeable {

    companion object {
        private const val LOG_TAG = "RoomEventsStorage"
    }

    private val database = EventsDatabase.getInstance(context)
    private val dao = database.eventAlertDao()

    /**
     * Adds or updates an event alert record.
     * 
     * If [event.notificationId] is 0, assigns a new unique notification ID.
     * If the event already exists (same eventId + instanceStartTime), updates it
     * while preserving the original notification ID.
     * 
     * @return true if insert/update succeeded, false otherwise
     */
    override fun addEvent(event: EventAlertRecord): Boolean = synchronized(RoomEventsStorage::class.java) {
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
            dao.update(EventAlertEntity.fromRecord(merged)) == 1
        } else {
            dao.insert(EventAlertEntity.fromRecord(eventWithId)) != -1L
        }
    }

    /**
     * Adds multiple events in a single transaction.
     * 
     * Rolls back entire transaction if any event fails to add.
     * 
     * @return true if all events added successfully, false if any failed (all rolled back)
     */
    override fun addEvents(events: List<EventAlertRecord>): Boolean = synchronized(RoomEventsStorage::class.java) {
        // Use manual transaction to match legacy rollback-on-failure semantics
        database.beginTransaction()
        try {
            for (event in events) {
                if (!addEventInternal(event)) {
                    return@synchronized false
                }
            }
            database.setTransactionSuccessful()
            true
        } finally {
            database.endTransaction()
        }
    }
    
    /** Internal add without synchronization - for use within transactions. */
    private fun addEventInternal(event: EventAlertRecord): Boolean {
        val eventWithId = if (event.notificationId == 0) {
            event.copy(notificationId = nextNotificationId())
        } else {
            event
        }
        
        val existing = dao.getByKey(eventWithId.eventId, eventWithId.instanceStartTime)
        return if (existing != null) {
            val merged = eventWithId.copy(notificationId = existing.notificationId ?: eventWithId.notificationId)
            dao.update(EventAlertEntity.fromRecord(merged)) == 1
        } else {
            dao.insert(EventAlertEntity.fromRecord(eventWithId)) != -1L
        }
    }

    /**
     * Updates specific fields of an event, leaving others unchanged.
     * 
     * Non-null parameters override the corresponding field in the event.
     * Useful for partial updates like snoozing or changing display status.
     * 
     * @return Pair of (success, updated event record)
     */
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
    ): Pair<Boolean, EventAlertRecord> = synchronized(RoomEventsStorage::class.java) {
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

        val success = updateEventInternal(newEvent)
        Pair(success, newEvent)
    }

    /**
     * Updates specific fields of multiple events in a transaction.
     * 
     * Rolls back entire transaction if any update fails.
     * 
     * @return true if all updates succeeded, false if any failed (all rolled back)
     */
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
    ): Boolean = synchronized(RoomEventsStorage::class.java) {
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
        updateEventsInternal(newEvents)
    }

    /**
     * Updates an event's instance times (changes the primary key).
     * 
     * Used when a recurring event instance is rescheduled. Since the primary key
     * (eventId, instanceStartTime) changes, this performs delete + insert atomically.
     * 
     * @throws SQLiteConstraintException if new instance time conflicts with existing record
     * @return true if update succeeded, false if original record didn't exist
     */
    override fun updateEventAndInstanceTimes(event: EventAlertRecord, instanceStart: Long, instanceEnd: Long): Boolean = 
        synchronized(RoomEventsStorage::class.java) {
            // Wrap in transaction for atomicity (delete + insert must be atomic)
            // Note: Unlike batch method, single method does NOT catch SQLiteConstraintException (matches legacy)
            var success = false
            database.runInTransaction {
                success = updateEventAndInstanceTimesRaw(event, instanceStart, instanceEnd)
            }
            success
        }
    
    /** Raw version without transaction wrapper - for use inside existing transactions. */
    private fun updateEventAndInstanceTimesRaw(event: EventAlertRecord, instanceStart: Long, instanceEnd: Long): Boolean {
        val updatedEvent = event.copy(instanceStartTime = instanceStart, instanceEndTime = instanceEnd)
        // Need to delete old key and insert new since PK changed
        // Only succeed if original record existed (matches legacy UPDATE semantics)
        val deleted = dao.deleteByKey(event.eventId, event.instanceStartTime)
        return if (deleted == 1) {
            dao.insert(EventAlertEntity.fromRecord(updatedEvent)) != -1L
        } else {
            false  // Record didn't exist - matches legacy UPDATE returning 0 rows
        }
    }

    /**
     * Updates multiple events' instance times in a single transaction.
     * 
     * Unlike the single-event version, catches SQLiteConstraintException and returns false
     * (matches legacy defensive batch behavior).
     * 
     * @return true if all updates succeeded, false if any failed (all rolled back)
     */
    override fun updateEventsAndInstanceTimes(events: Collection<EventWithNewInstanceTime>): Boolean = 
        synchronized(RoomEventsStorage::class.java) {
            // Use manual transaction to match legacy rollback-on-failure semantics
            database.beginTransaction()
            try {
                for ((event, instanceStart, instanceEnd) in events) {
                    if (!updateEventAndInstanceTimesRaw(event, instanceStart, instanceEnd)) {
                        return@synchronized false  // Rollback - don't call setTransactionSuccessful
                    }
                }
                database.setTransactionSuccessful()
                true
            } catch (ex: SQLiteConstraintException) {
                // Match legacy behavior: log and return false if new instance time conflicts with existing row
                DevLog.error(LOG_TAG, "updateEventsAndInstanceTimes: hit SQLiteConstraintException: ${ex.detailed}")
                false
            } finally {
                database.endTransaction()
            }
        }

    /**
     * Updates an event record in place (same primary key).
     * 
     * @return true if exactly one row was updated, false otherwise
     */
    override fun updateEvent(event: EventAlertRecord): Boolean = synchronized(RoomEventsStorage::class.java) {
        updateEventInternal(event)
    }
    
    private fun updateEventInternal(event: EventAlertRecord): Boolean {
        return dao.update(EventAlertEntity.fromRecord(event)) == 1
    }

    /**
     * Updates multiple event records in a single transaction.
     * 
     * Rolls back entire transaction if any update fails.
     * 
     * @return true if all updates succeeded, false if any failed (all rolled back)
     */
    override fun updateEvents(events: List<EventAlertRecord>): Boolean = synchronized(RoomEventsStorage::class.java) {
        updateEventsInternal(events)
    }
    
    private fun updateEventsInternal(events: List<EventAlertRecord>): Boolean {
        // Use manual transaction to match legacy rollback-on-failure semantics
        database.beginTransaction()
        try {
            for (event in events) {
                if (dao.update(EventAlertEntity.fromRecord(event)) != 1) {
                    return false  // Rollback - don't call setTransactionSuccessful
                }
            }
            database.setTransactionSuccessful()
            return true
        } finally {
            database.endTransaction()
        }
    }

    /**
     * Retrieves a single event by its primary key.
     * 
     * @return the event record, or null if not found
     */
    override fun getEvent(eventId: Long, instanceStartTime: Long): EventAlertRecord? = 
        synchronized(RoomEventsStorage::class.java) {
            dao.getByKey(eventId, instanceStartTime)?.toRecord()
        }

    /**
     * Retrieves all instances of a recurring event.
     * 
     * @return list of all event records with the given eventId (may be empty)
     */
    override fun getEventInstances(eventId: Long): List<EventAlertRecord> = 
        synchronized(RoomEventsStorage::class.java) {
            dao.getByEventId(eventId).map { it.toRecord() }
        }

    /**
     * Deletes an event by its primary key.
     * 
     * @return true if exactly one row was deleted, false otherwise
     */
    override fun deleteEvent(eventId: Long, instanceStartTime: Long): Boolean = 
        synchronized(RoomEventsStorage::class.java) {
            deleteEventInternal(eventId, instanceStartTime)
        }
    
    private fun deleteEventInternal(eventId: Long, instanceStartTime: Long): Boolean {
        return dao.deleteByKey(eventId, instanceStartTime) == 1
    }

    /**
     * Deletes an event by its record.
     * 
     * @return true if exactly one row was deleted, false otherwise
     */
    override fun deleteEvent(ev: EventAlertRecord): Boolean = 
        synchronized(RoomEventsStorage::class.java) {
            deleteEventInternal(ev.eventId, ev.instanceStartTime)
        }

    /**
     * Deletes multiple events in a single transaction.
     * 
     * Continues processing even if individual deletes fail.
     * 
     * @return count of successfully deleted events
     */
    override fun deleteEvents(events: Collection<EventAlertRecord>): Int = 
        synchronized(RoomEventsStorage::class.java) {
            var count = 0
            database.runInTransaction {
                for (event in events) {
                    if (deleteEventInternal(event.eventId, event.instanceStartTime)) {
                        count++
                    }
                }
            }
            count
        }

    /**
     * Deletes all events from storage.
     * 
     * @return true (always succeeds; throws on database error)
     */
    override fun deleteAllEvents(): Boolean = synchronized(RoomEventsStorage::class.java) {
        dao.deleteAllRows()
        true
    }

    /**
     * Retrieves all stored event alert records.
     */
    override val events: List<EventAlertRecord>
        get() = synchronized(RoomEventsStorage::class.java) {
            dao.getAll().map { it.toRecord() }
        }

    /** Room manages connections via singleton; individual close is a no-op. */
    override fun close() {
    }

    /**
     * Generates the next unique notification ID.
     * 
     * Returns max(existing IDs) + 1, or NOTIFICATION_ID_DYNAMIC_FROM if table is empty
     * or all IDs are below the dynamic range.
     */
    private fun nextNotificationId(): Int {
        val maxId = dao.getMaxNotificationId() ?: 0
        return if (maxId < Consts.NOTIFICATION_ID_DYNAMIC_FROM) {
            Consts.NOTIFICATION_ID_DYNAMIC_FROM
        } else {
            maxId + 1
        }
    }
}

