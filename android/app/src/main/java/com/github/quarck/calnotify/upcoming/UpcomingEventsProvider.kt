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

package com.github.quarck.calnotify.upcoming

import android.content.Context
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorageInterface
import com.github.quarck.calnotify.utils.CNPlusClockInterface

/**
 * Provides upcoming events by fetching alerts from MonitorStorage
 * and enriching them with full event details from CalendarProvider.
 * 
 * "Upcoming" events are alerts that:
 * - Have not been handled yet (wasHandled = false)
 * - Have alertTime between now and the lookahead cutoff
 * 
 * This class is designed to be testable with injected dependencies.
 */
class UpcomingEventsProvider(
    private val context: Context,
    private val settings: Settings,
    private val clock: CNPlusClockInterface,
    private val monitorStorage: MonitorStorageInterface,
    private val calendarProvider: CalendarProviderInterface
) {
    
    private val lookahead = UpcomingEventsLookahead(settings, clock)
    
    /**
     * Get all upcoming events within the lookahead window.
     * 
     * @return List of enriched EventAlertRecords sorted by alertTime
     */
    fun getUpcomingEvents(): List<EventAlertRecord> {
        val now = clock.currentTimeMillis()
        val cutoffTime = lookahead.getCutoffTime()
        
        DevLog.debug(LOG_TAG, "getUpcomingEvents: now=$now, cutoff=$cutoffTime")
        
        // Fetch alerts in the time range
        val alerts = monitorStorage.getAlertsForAlertRange(now, cutoffTime)
            .filter { !it.wasHandled }
            .sortedBy { it.alertTime }
        
        DevLog.debug(LOG_TAG, "Found ${alerts.size} unhandled alerts in range")
        
        // Enrich each alert with full event details
        return alerts.mapNotNull { alert ->
            enrichAlert(alert)
        }
    }
    
    /**
     * Enrich a MonitorEventAlertEntry with full event details from CalendarProvider.
     * 
     * @return EventAlertRecord or null if the event no longer exists
     */
    private fun enrichAlert(alert: MonitorEventAlertEntry): EventAlertRecord? {
        val eventRecord = calendarProvider.getEvent(context, alert.eventId)
        if (eventRecord == null) {
            DevLog.debug(LOG_TAG, "Event ${alert.eventId} no longer exists in calendar, skipping")
            return null
        }
        
        return EventAlertRecord(
            calendarId = eventRecord.calendarId,
            eventId = alert.eventId,
            isAllDay = alert.isAllDay,
            isRepeating = eventRecord.rRule?.isNotEmpty() == true,
            alertTime = alert.alertTime,
            notificationId = 0, // Not used for upcoming
            title = eventRecord.title,
            desc = eventRecord.desc,
            startTime = eventRecord.startTime,
            endTime = eventRecord.endTime,
            instanceStartTime = alert.instanceStartTime,
            instanceEndTime = alert.instanceEndTime,
            location = eventRecord.location,
            lastStatusChangeTime = clock.currentTimeMillis(),
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = eventRecord.color,
            origin = EventOrigin.ProviderManual,
            timeFirstSeen = 0L,
            eventStatus = eventRecord.eventStatus,
            attendanceStatus = eventRecord.attendanceStatus,
            flags = if (alert.preMuted) EventAlertFlags.IS_MUTED else 0L
        )
    }
    
    companion object {
        private const val LOG_TAG = "UpcomingEventsProvider"
    }
}
