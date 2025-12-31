//
//   Calendar Notifications Plus
//   Copyright (C) 2017 Sergey Parshin (s.parshin.sc@gmail.com)
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

package com.github.quarck.calnotify.monitorstorage

import android.content.Context
import com.github.quarck.calnotify.logs.DevLog
import java.io.Closeable

/**
 * MonitorStorage - tracks calendar alerts being monitored.
 * 
 * Attempts to use Room database with cr-sqlite support.
 * Falls back to legacy SQLiteOpenHelper if Room migration fails.
 * 
 * This fallback strategy ensures:
 * - No data loss if migration has bugs
 * - Future app versions can retry migration
 * - Legacy database is preserved untouched
 */
class MonitorStorage(context: Context) : MonitorStorageInterface, Closeable {
    
    private val delegate: MonitorStorageInterface
    private val isUsingRoom: Boolean
    
    init {
        val (storage, usingRoom) = createStorage(context)
        delegate = storage
        isUsingRoom = usingRoom
    }

    // Delegate all interface methods
    override fun addAlert(entry: com.github.quarck.calnotify.calendar.MonitorEventAlertEntry) = delegate.addAlert(entry)
    override fun addAlerts(entries: Collection<com.github.quarck.calnotify.calendar.MonitorEventAlertEntry>) = delegate.addAlerts(entries)
    override fun deleteAlert(entry: com.github.quarck.calnotify.calendar.MonitorEventAlertEntry) = delegate.deleteAlert(entry)
    override fun deleteAlerts(entries: Collection<com.github.quarck.calnotify.calendar.MonitorEventAlertEntry>) = delegate.deleteAlerts(entries)
    override fun deleteAlert(eventId: Long, alertTime: Long, instanceStart: Long) = delegate.deleteAlert(eventId, alertTime, instanceStart)
    override fun deleteAlertsMatching(filter: (com.github.quarck.calnotify.calendar.MonitorEventAlertEntry) -> Boolean) = delegate.deleteAlertsMatching(filter)
    override fun updateAlert(entry: com.github.quarck.calnotify.calendar.MonitorEventAlertEntry) = delegate.updateAlert(entry)
    override fun updateAlerts(entries: Collection<com.github.quarck.calnotify.calendar.MonitorEventAlertEntry>) = delegate.updateAlerts(entries)
    override fun getAlert(eventId: Long, alertTime: Long, instanceStart: Long) = delegate.getAlert(eventId, alertTime, instanceStart)
    override fun getInstanceAlerts(eventId: Long, instanceStart: Long) = delegate.getInstanceAlerts(eventId, instanceStart)
    override fun getNextAlert(since: Long) = delegate.getNextAlert(since)
    override fun getAlertsAt(time: Long) = delegate.getAlertsAt(time)
    override val alerts get() = delegate.alerts
    override fun getAlertsForInstanceStartRange(scanFrom: Long, scanTo: Long) = delegate.getAlertsForInstanceStartRange(scanFrom, scanTo)
    override fun getAlertsForAlertRange(scanFrom: Long, scanTo: Long) = delegate.getAlertsForAlertRange(scanFrom, scanTo)
    
    override fun close() {
        if (delegate is Closeable) {
            delegate.close()
        }
    }

    companion object {
        private const val LOG_TAG = "MonitorStorage"
        
        /**
         * Creates the appropriate storage implementation.
         * 
         * @return Pair of (storage implementation, isUsingRoom)
         */
        private fun createStorage(context: Context): Pair<MonitorStorageInterface, Boolean> {
            return try {
                DevLog.info(LOG_TAG, "Attempting to use Room storage...")
                val roomStorage = RoomMonitorStorage(context)
                DevLog.info(LOG_TAG, "✅ Using Room storage")
                Pair(roomStorage, true)
            } catch (e: MigrationException) {
                DevLog.error(LOG_TAG, "⚠️ Room migration failed, falling back to legacy storage: ${e.message}")
                Pair(LegacyMonitorStorage(context), false)
            }
        }
    }
}
