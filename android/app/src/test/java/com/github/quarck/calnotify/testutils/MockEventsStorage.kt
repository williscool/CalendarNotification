package com.github.quarck.calnotify.testutils

import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.eventsstorage.EventWithNewInstanceTime
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.logs.DevLog

/**
 * In-memory implementation of EventsStorageInterface for Robolectric tests.
 * 
 * This avoids the need for native SQLite libraries by storing events in memory.
 * The storage is scoped to the test and cleared between tests.
 */
class MockEventsStorage : EventsStorageInterface {
    private val LOG_TAG = "MockEventsStorage"
    
    // In-memory storage for events
    // Key: (eventId, instanceStartTime)
    private val eventsMap = mutableMapOf<EventKey, EventAlertRecord>()
    
    data class EventKey(val eventId: Long, val instanceStartTime: Long)
    
    override fun addEvent(event: EventAlertRecord): Boolean {
        DevLog.info(LOG_TAG, "Adding event: eventId=${event.eventId}, title=${event.title}")
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
            isRepeating = isRepeating ?: existing.isRepeating
        )
        
        // isMuted is a computed property from flags, set it via the property setter
        if (isMuted != null) {
            updated.isMuted = isMuted
        }
        
        eventsMap[key] = updated
        DevLog.info(LOG_TAG, "Updated event: eventId=${event.eventId}, isMuted=${updated.isMuted}")
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
            updateEvent(event, alertTime, title, snoozedUntil, startTime, endTime, location, 
                lastStatusChangeTime, displayStatus, color, isRepeating, null)
        }
        return true
    }
    
    override fun updateEventAndInstanceTimes(event: EventAlertRecord, instanceStart: Long, instanceEnd: Long): Boolean {
        val key = EventKey(event.eventId, event.instanceStartTime)
        val existing = eventsMap[key] ?: return false
        
        // Remove old entry
        eventsMap.remove(key)
        
        // Add new entry with updated instance times
        val updated = existing.copy(
            instanceStartTime = instanceStart,
            instanceEndTime = instanceEnd
        )
        val newKey = EventKey(event.eventId, instanceStart)
        eventsMap[newKey] = updated
        
        return true
    }
    
    override fun updateEventsAndInstanceTimes(events: Collection<EventWithNewInstanceTime>): Boolean {
        events.forEach { (event, newStart, newEnd) ->
            updateEventAndInstanceTimes(event, newStart, newEnd)
        }
        return true
    }
    
    override fun updateEvent(event: EventAlertRecord): Boolean {
        val key = EventKey(event.eventId, event.instanceStartTime)
        if (eventsMap.containsKey(key)) {
            eventsMap[key] = event
            return true
        }
        return false
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
        val removed = eventsMap.remove(key) != null
        DevLog.info(LOG_TAG, "Deleted event: eventId=$eventId, success=$removed")
        return removed
    }
    
    override fun deleteEvent(ev: EventAlertRecord): Boolean {
        return deleteEvent(ev.eventId, ev.instanceStartTime)
    }
    
    override fun deleteEvents(events: Collection<EventAlertRecord>): Int {
        var count = 0
        events.forEach { event ->
            val key = EventKey(event.eventId, event.instanceStartTime)
            if (eventsMap.remove(key) != null) {
                count++
            }
        }
        DevLog.info(LOG_TAG, "Deleted $count events")
        return count
    }
    
    override val events: List<EventAlertRecord>
        get() = eventsMap.values.toList()
    
    override fun deleteAllEvents(): Boolean {
        DevLog.info(LOG_TAG, "Deleting all ${eventsMap.size} events")
        eventsMap.clear()
        return true
    }
    
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

