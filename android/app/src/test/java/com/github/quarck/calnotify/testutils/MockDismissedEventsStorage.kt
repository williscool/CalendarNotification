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
    
    // Track closed state - warn instead of throw since Room's close() is a no-op anyway
    // TODO: When legacy storage is removed, consider stricter enforcement or removing Closeable entirely
    private var closed = false
    
    private fun warnIfClosed() {
        if (closed) {
            DevLog.warn(LOG_TAG, "MockDismissedEventsStorage used after close() - this pattern works because Room's close() is a no-op, but is conceptually incorrect. See docs/architecture/storage_lifecycle.md")
        }
    }
    
    // In-memory storage for dismissed events
    // Key: (eventId, instanceStartTime)
    private val eventsMap = mutableMapOf<EventKey, DismissedEventAlertRecord>()
    
    data class EventKey(val eventId: Long, val instanceStartTime: Long)
    
    override fun addEvent(type: EventDismissType, changeTime: Long, event: EventAlertRecord) {
        warnIfClosed()
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
        warnIfClosed()
        addEvent(type, TestTimeConstants.STANDARD_TEST_TIME, event)
    }
    
    override fun addEvents(type: EventDismissType, events: Collection<EventAlertRecord>) {
        warnIfClosed()
        DevLog.info(LOG_TAG, "Adding ${events.size} dismissed events")
        val changeTime = TestTimeConstants.STANDARD_TEST_TIME
        events.forEach { addEvent(type, changeTime, it) }
    }
    
    override fun deleteEvent(entry: DismissedEventAlertRecord) {
        warnIfClosed()
        val key = EventKey(entry.event.eventId, entry.event.instanceStartTime)
        eventsMap.remove(key)
        DevLog.info(LOG_TAG, "Deleted dismissed event: eventId=${entry.event.eventId}")
    }
    
    override fun deleteEvent(event: EventAlertRecord) {
        warnIfClosed()
        val key = EventKey(event.eventId, event.instanceStartTime)
        eventsMap.remove(key)
        DevLog.info(LOG_TAG, "Deleted dismissed event: eventId=${event.eventId}")
    }
    
    override fun clearHistory() {
        warnIfClosed()
        DevLog.info(LOG_TAG, "Clearing all ${eventsMap.size} dismissed events")
        eventsMap.clear()
    }
    
    override fun purgeOld(currentTime: Long, maxLiveTime: Long) {
        warnIfClosed()
        val cutoff = currentTime - maxLiveTime
        val toRemove = eventsMap.filter { it.value.dismissTime < cutoff }
        toRemove.forEach { eventsMap.remove(it.key) }
        DevLog.info(LOG_TAG, "Purged ${toRemove.size} old events")
    }
    
    override val events: List<DismissedEventAlertRecord>
        get() {
            warnIfClosed()
            return eventsMap.values.toList()
        }
    
    override val eventsForDisplay: List<DismissedEventAlertRecord>
        get() {
            warnIfClosed()
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

