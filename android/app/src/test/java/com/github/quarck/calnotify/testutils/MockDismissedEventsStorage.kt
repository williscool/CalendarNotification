package com.github.quarck.calnotify.testutils

import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventAlertRecord
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorageInterface
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.logs.DevLog

/**
 * In-memory implementation of DismissedEventsStorageInterface for Robolectric tests.
 * 
 * This avoids the need for native SQLite libraries by storing events in memory.
 */
class MockDismissedEventsStorage : DismissedEventsStorageInterface {
    private val LOG_TAG = "MockDismissedEventsStorage"
    
    // Track closed state to match real storage behavior
    private var closed = false
    
    private fun ensureNotClosed() {
        if (closed) throw IllegalStateException("MockDismissedEventsStorage has been closed - cannot perform operations on closed storage")
    }
    
    // In-memory storage for dismissed events
    // Key: (eventId, instanceStartTime)
    private val eventsMap = mutableMapOf<EventKey, DismissedEventAlertRecord>()
    
    data class EventKey(val eventId: Long, val instanceStartTime: Long)
    
    override fun addEvent(type: EventDismissType, changeTime: Long, event: EventAlertRecord) {
        ensureNotClosed()
        DevLog.info(LOG_TAG, "Adding dismissed event: eventId=${event.eventId}, type=$type")
        val key = EventKey(event.eventId, event.instanceStartTime)
        val dismissedRecord = DismissedEventAlertRecord(
            event = event,
            dismissTime = changeTime,
            dismissType = type
        )
        eventsMap[key] = dismissedRecord
    }
    
    override fun addEvent(type: EventDismissType, event: EventAlertRecord) {
        ensureNotClosed()
        addEvent(type, System.currentTimeMillis(), event)
    }
    
    override fun addEvents(type: EventDismissType, events: Collection<EventAlertRecord>) {
        ensureNotClosed()
        DevLog.info(LOG_TAG, "Adding ${events.size} dismissed events")
        val changeTime = System.currentTimeMillis()
        events.forEach { addEvent(type, changeTime, it) }
    }
    
    override fun deleteEvent(entry: DismissedEventAlertRecord) {
        ensureNotClosed()
        val key = EventKey(entry.event.eventId, entry.event.instanceStartTime)
        eventsMap.remove(key)
        DevLog.info(LOG_TAG, "Deleted dismissed event: eventId=${entry.event.eventId}")
    }
    
    override fun deleteEvent(event: EventAlertRecord) {
        ensureNotClosed()
        val key = EventKey(event.eventId, event.instanceStartTime)
        eventsMap.remove(key)
        DevLog.info(LOG_TAG, "Deleted dismissed event: eventId=${event.eventId}")
    }
    
    override fun clearHistory() {
        ensureNotClosed()
        DevLog.info(LOG_TAG, "Clearing all ${eventsMap.size} dismissed events")
        eventsMap.clear()
    }
    
    override fun purgeOld(currentTime: Long, maxLiveTime: Long) {
        ensureNotClosed()
        val cutoff = currentTime - maxLiveTime
        val toRemove = eventsMap.filter { it.value.dismissTime < cutoff }
        toRemove.forEach { eventsMap.remove(it.key) }
        DevLog.info(LOG_TAG, "Purged ${toRemove.size} old events")
    }
    
    override val events: List<DismissedEventAlertRecord>
        get() {
            ensureNotClosed()
            return eventsMap.values.toList()
        }
    
    override val eventsForDisplay: List<DismissedEventAlertRecord>
        get() {
            ensureNotClosed()
            return eventsMap.values.sortedByDescending { it.dismissTime }
        }
    
    override fun close() {
        closed = true
    }
    
    /**
     * Clears all events - useful for test cleanup
     */
    fun clear() {
        DevLog.info(LOG_TAG, "Clearing all ${eventsMap.size} dismissed events")
        eventsMap.clear()
    }
    
    /**
     * Returns count of events - useful for test assertions
     */
    val eventCount: Int
        get() = eventsMap.size
}

