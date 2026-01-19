package com.github.quarck.calnotify.testutils

import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorageInterface

/**
 * In-memory implementation of MonitorStorageInterface for Robolectric tests.
 * 
 * This avoids the need for native SQLite libraries by storing alerts in memory.
 * The storage is scoped to the test and cleared between tests.
 */
class MockMonitorStorage : MonitorStorageInterface {
    private val LOG_TAG = "MockMonitorStorage"
    
    // Track closed state to match real storage behavior
    private var closed = false
    
    private fun ensureNotClosed() {
        if (closed) throw IllegalStateException("MockMonitorStorage has been closed - cannot perform operations on closed storage")
    }
    
    // In-memory storage for alerts
    private val alertsMap = mutableMapOf<AlertKey, MonitorEventAlertEntry>()
    
    override fun close() {
        closed = true
    }
    
    data class AlertKey(val eventId: Long, val alertTime: Long, val instanceStart: Long)
    
    override fun addAlert(entry: MonitorEventAlertEntry) {
        ensureNotClosed()
        DevLog.info(LOG_TAG, "Adding alert: eventId=${entry.eventId}, alertTime=${entry.alertTime}")
        val key = AlertKey(entry.eventId, entry.alertTime, entry.instanceStartTime)
        alertsMap[key] = entry
    }
    
    override fun addAlerts(entries: Collection<MonitorEventAlertEntry>) {
        ensureNotClosed()
        DevLog.info(LOG_TAG, "Adding ${entries.size} alerts")
        entries.forEach { addAlert(it) }
    }
    
    override fun getAlert(eventId: Long, alertTime: Long, instanceStart: Long): MonitorEventAlertEntry? {
        ensureNotClosed()
        val key = AlertKey(eventId, alertTime, instanceStart)
        return alertsMap[key]
    }
    
    override fun getInstanceAlerts(eventId: Long, instanceStart: Long): List<MonitorEventAlertEntry> {
        ensureNotClosed()
        return alertsMap.values.filter { it.eventId == eventId && it.instanceStartTime == instanceStart }
    }
    
    override fun deleteAlert(entry: MonitorEventAlertEntry) {
        ensureNotClosed()
        val key = AlertKey(entry.eventId, entry.alertTime, entry.instanceStartTime)
        alertsMap.remove(key)
        DevLog.info(LOG_TAG, "Deleted alert: eventId=${entry.eventId}")
    }
    
    override fun deleteAlerts(entries: Collection<MonitorEventAlertEntry>) {
        ensureNotClosed()
        entries.forEach { deleteAlert(it) }
    }
    
    override fun deleteAlert(eventId: Long, alertTime: Long, instanceStart: Long) {
        ensureNotClosed()
        val key = AlertKey(eventId, alertTime, instanceStart)
        alertsMap.remove(key)
    }
    
    override fun deleteAlertsMatching(filter: (MonitorEventAlertEntry) -> Boolean) {
        ensureNotClosed()
        val toRemove = alertsMap.values.filter(filter).map { AlertKey(it.eventId, it.alertTime, it.instanceStartTime) }
        toRemove.forEach { alertsMap.remove(it) }
        DevLog.info(LOG_TAG, "Deleted ${toRemove.size} alerts matching filter")
    }
    
    override fun updateAlert(entry: MonitorEventAlertEntry) {
        ensureNotClosed()
        val key = AlertKey(entry.eventId, entry.alertTime, entry.instanceStartTime)
        alertsMap[key] = entry
        DevLog.info(LOG_TAG, "Updated alert: eventId=${entry.eventId}")
    }
    
    override fun updateAlerts(entries: Collection<MonitorEventAlertEntry>) {
        ensureNotClosed()
        entries.forEach { updateAlert(it) }
    }
    
    override fun getNextAlert(since: Long): Long? {
        ensureNotClosed()
        return alertsMap.values
            .filter { it.alertTime > since }
            .minByOrNull { it.alertTime }
            ?.alertTime
    }
    
    override fun getAlertsAt(time: Long): List<MonitorEventAlertEntry> {
        ensureNotClosed()
        return alertsMap.values.filter { it.alertTime == time }
    }
    
    override val alerts: List<MonitorEventAlertEntry>
        get() {
            ensureNotClosed()
            return alertsMap.values.toList()
        }
    
    override fun getAlertsForInstanceStartRange(scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry> {
        ensureNotClosed()
        return alertsMap.values.filter { it.instanceStartTime in scanFrom..scanTo }
    }
    
    override fun getAlertsForAlertRange(scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry> {
        ensureNotClosed()
        return alertsMap.values.filter { it.alertTime in scanFrom..scanTo }
    }
    
    /**
     * Clears all alerts - useful for test cleanup
     */
    fun clear() {
        DevLog.info(LOG_TAG, "Clearing all ${alertsMap.size} alerts")
        alertsMap.clear()
    }
    
    /**
     * Returns count of alerts - useful for test assertions
     */
    val alertCount: Int
        get() = alertsMap.size
}

