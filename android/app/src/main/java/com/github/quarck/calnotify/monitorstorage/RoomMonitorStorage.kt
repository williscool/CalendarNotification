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

package com.github.quarck.calnotify.monitorstorage

import android.content.Context
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
/**
 * Room-based implementation of MonitorStorageInterface.
 * 
 * Replaces the legacy SQLiteOpenHelper-based MonitorStorage.
 * Uses MonitorDatabase with cr-sqlite support via CrSqliteRoomFactory.
 */
class RoomMonitorStorage(context: Context) : MonitorStorageInterface {

    private val database = MonitorDatabase.getInstance(context)
    private val dao = database.monitorAlertDao()

    override fun addAlert(entry: MonitorEventAlertEntry) {
        dao.insert(MonitorAlertEntity.fromAlertEntry(entry))
    }

    override fun addAlerts(entries: Collection<MonitorEventAlertEntry>) {
        dao.insertAll(entries.map { MonitorAlertEntity.fromAlertEntry(it) })
    }

    override fun deleteAlert(entry: MonitorEventAlertEntry) {
        deleteAlert(entry.eventId, entry.alertTime, entry.instanceStartTime)
    }

    override fun deleteAlerts(entries: Collection<MonitorEventAlertEntry>) {
        dao.deleteAll(entries.map { MonitorAlertEntity.fromAlertEntry(it) })
    }

    override fun deleteAlert(eventId: Long, alertTime: Long, instanceStart: Long) {
        dao.deleteByKey(eventId, alertTime, instanceStart)
    }

    override fun deleteAlertsMatching(filter: (MonitorEventAlertEntry) -> Boolean) {
        // Wrap in transaction to ensure atomicity (read + delete as single unit)
        database.runInTransaction {
            val allAlerts = dao.getAll()
            val toDelete = allAlerts.filter { filter(it.toAlertEntry()) }
            if (toDelete.isNotEmpty()) {
                dao.deleteAll(toDelete)
            }
        }
    }

    override fun updateAlert(entry: MonitorEventAlertEntry) {
        dao.update(MonitorAlertEntity.fromAlertEntry(entry))
    }

    override fun updateAlerts(entries: Collection<MonitorEventAlertEntry>) {
        dao.updateAll(entries.map { MonitorAlertEntity.fromAlertEntry(it) })
    }

    override fun getAlert(eventId: Long, alertTime: Long, instanceStart: Long): MonitorEventAlertEntry? {
        return dao.getByKey(eventId, alertTime, instanceStart)?.toAlertEntry()
    }

    override fun getInstanceAlerts(eventId: Long, instanceStart: Long): List<MonitorEventAlertEntry> {
        return dao.getByEventAndInstance(eventId, instanceStart).map { it.toAlertEntry() }
    }

    override fun getNextAlert(since: Long): Long? {
        return dao.getNextAlertTime(since)
    }

    override fun getAlertsAt(time: Long): List<MonitorEventAlertEntry> {
        return dao.getByAlertTime(time).map { it.toAlertEntry() }
    }

    override val alerts: List<MonitorEventAlertEntry>
        get() = dao.getAll().map { it.toAlertEntry() }

    override fun getAlertsForInstanceStartRange(scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry> {
        return dao.getByInstanceStartRange(scanFrom, scanTo).map { it.toAlertEntry() }
    }

    override fun getAlertsForAlertRange(scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry> {
        return dao.getByAlertTimeRange(scanFrom, scanTo).map { it.toAlertEntry() }
    }

    override fun close() {
        // Room handles connection management via singleton pattern
        // Individual close is not needed - database will be closed when app terminates
    }
}

