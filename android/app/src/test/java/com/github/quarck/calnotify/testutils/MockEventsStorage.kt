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

package com.github.quarck.calnotify.testutils

import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.eventsstorage.EventWithNewInstanceTime
import com.github.quarck.calnotify.logs.DevLog

/**
 * In-memory implementation of EventsStorageInterface for Robolectric tests.
 * 
 * This avoids the need for native SQLite libraries by storing events in memory.
 */
class MockEventsStorage : EventsStorageInterface {
    private val LOG_TAG = "MockEventsStorage"
    
    // In-memory storage for events
    // Key: (eventId, instanceStartTime)
    private val eventsMap = mutableMapOf<EventKey, EventAlertRecord>()
    
    data class EventKey(val eventId: Long, val instanceStartTime: Long)
    
    override fun addEvent(event: EventAlertRecord): Boolean {
        DevLog.info(LOG_TAG, "Adding event: eventId=${event.eventId}")
        val key = EventKey(event.eventId, event.instanceStartTime)
        eventsMap[key] = event
        return true
    }
    
    override fun addEvents(events: List<EventAlertRecord>): Boolean {
        DevLog.info(LOG_TAG, "Adding ${events.size} events")
        events.forEach { addEvent(it) }
        return true
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
        val key = EventKey(event.eventId, event.instanceStartTime)
        val existing = eventsMap[key] ?: return Pair(false, event)
        
        val updated = existing.copy(
            alertTime = alertTime ?: existing.alertTime,
            title = title ?: existing.title,
            snoozedUntil = snoozedUntil ?: existing.snoozedUntil,
            startTime = startTime ?: existing.startTime,
            endTime = endTime ?: existing.endTime,
            location = location ?: existing.location,
            lastStatusChangeTime = lastStatusChangeTime ?: existing.lastStatusChangeTime,
            displayStatus = displayStatus ?: existing.displayStatus,
            color = color ?: existing.color,
            isRepeating = isRepeating ?: existing.isRepeating,
            isMuted = isMuted ?: existing.isMuted
        )
        eventsMap[key] = updated
        DevLog.info(LOG_TAG, "Updated event: eventId=${event.eventId}")
        return Pair(true, updated)
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
        events.forEach { event ->
            updateEvent(event, alertTime, title, snoozedUntil, startTime, endTime, 
                location, lastStatusChangeTime, displayStatus, color, isRepeating)
        }
        return true
    }
    
    override fun updateEventAndInstanceTimes(event: EventAlertRecord, instanceStart: Long, instanceEnd: Long): Boolean {
        val oldKey = EventKey(event.eventId, event.instanceStartTime)
        eventsMap.remove(oldKey)
        
        val updated = event.copy(
            instanceStartTime = instanceStart,
            instanceEndTime = instanceEnd
        )
        val newKey = EventKey(event.eventId, instanceStart)
        eventsMap[newKey] = updated
        return true
    }
    
    override fun updateEventsAndInstanceTimes(events: Collection<EventWithNewInstanceTime>): Boolean {
        events.forEach { 
            updateEventAndInstanceTimes(it.event, it.newInstanceStartTime, it.newInstanceEndTime)
        }
        return true
    }
    
    override fun updateEvent(event: EventAlertRecord): Boolean {
        val key = EventKey(event.eventId, event.instanceStartTime)
        eventsMap[key] = event
        DevLog.info(LOG_TAG, "Updated event: eventId=${event.eventId}")
        return true
    }
    
    override fun updateEvents(events: List<EventAlertRecord>): Boolean {
        events.forEach { updateEvent(it) }
        return true
    }
    
    override fun getEvent(eventId: Long, instanceStartTime: Long): EventAlertRecord? {
        val key = EventKey(eventId, instanceStartTime)
        return eventsMap[key]
    }
    
    override fun getEventInstances(eventId: Long): List<EventAlertRecord> {
        return eventsMap.values.filter { it.eventId == eventId }
    }
    
    override fun deleteEvent(eventId: Long, instanceStartTime: Long): Boolean {
        val key = EventKey(eventId, instanceStartTime)
        val removed = eventsMap.remove(key)
        DevLog.info(LOG_TAG, "Deleted event: eventId=$eventId, found=${removed != null}")
        return removed != null
    }
    
    override fun deleteEvent(ev: EventAlertRecord): Boolean {
        return deleteEvent(ev.eventId, ev.instanceStartTime)
    }
    
    override fun deleteEvents(events: Collection<EventAlertRecord>): Int {
        var count = 0
        events.forEach { 
            if (deleteEvent(it)) count++
        }
        DevLog.info(LOG_TAG, "Deleted $count events")
        return count
    }
    
    override fun deleteAllEvents(): Boolean {
        DevLog.info(LOG_TAG, "Deleting all ${eventsMap.size} events")
        eventsMap.clear()
        return true
    }
    
    override val events: List<EventAlertRecord>
        get() = eventsMap.values.toList()
    
    /**
     * Clears all events - useful for test cleanup
     */
    fun clear() {
        DevLog.info(LOG_TAG, "Clearing all ${eventsMap.size} events")
        eventsMap.clear()
    }
    
    /**
     * Returns count of events - useful for test assertions
     */
    val eventCount: Int
        get() = eventsMap.size
}
