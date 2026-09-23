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

package com.github.quarck.calnotify.identitystorage

import com.github.quarck.calnotify.calendar.CalendarBackupInfo
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventRecord

/**
 * What capturing identity for a batch of events would write, decided before
 * anything is written.
 *
 * The same shape as [CalendarResolutionPlan]: the decision is a pure function
 * of its inputs, so it can be tested exhaustively and read in one sitting,
 * while the caller keeps the parts that genuinely need a `Context` -- opening
 * databases and querying the Calendar Provider.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
data class IdentityCapturePlan(
    /** Rows to write. */
    val toCapture: List<EventIdentityEntity>,

    /**
     * Events whose stored id no longer resolves to the event it was stored for.
     *
     * Deliberately not captured: the id now points at someone else's event, so
     * writing identity from it would leave a plausible-looking pointer to the
     * wrong event -- worse than none. Left for the resolver.
     */
    val stale: List<EventAlertRecord>,

    /**
     * Events the provider no longer knows at all, with nothing to store.
     *
     * Overwhelmingly dismissed events aged out of the provider's sync window,
     * which is the expected end state for history rather than a failure.
     */
    val gone: List<EventAlertRecord>
) {
    val isEmpty: Boolean get() = toCapture.isEmpty()

    fun summary(total: Int): String =
        "${toCapture.size} of $total event(s) captured, " +
        "${stale.size} stale, ${gone.size} no longer in provider"

    companion object {
        /**
         * Decide what to capture, without reading or writing anything.
         *
         * @param events the events to consider, already deduplicated by key.
         * @param storedSyncIdOf the sync id previously captured for an event, or
         *   null if it has none. Drives the staleness self-check.
         * @param providerEventOf what the Calendar Provider currently returns
         *   for an event id, or null if it knows no such event.
         * @param backupInfoOf the account tuple for a calendar id, or null if
         *   the calendar is gone. Called once per calendar, not per event.
         * @param capturedAtTime stamped onto every row written.
         */
        fun compute(
            events: List<EventAlertRecord>,
            storedSyncIdOf: (EventAlertRecord) -> String?,
            providerEventOf: (EventAlertRecord) -> EventRecord?,
            backupInfoOf: (Long) -> CalendarBackupInfo?,
            capturedAtTime: Long
        ): IdentityCapturePlan {
            val toCapture = mutableListOf<EventIdentityEntity>()
            val stale = mutableListOf<EventAlertRecord>()
            val gone = mutableListOf<EventAlertRecord>()

            // One backup-info lookup per calendar rather than per event -- a
            // pass is usually dominated by a handful of calendars.
            val backupInfoByCalendar = HashMap<Long, CalendarBackupInfo?>()

            for (event in events) {
                val providerEvent = providerEventOf(event)

                // Self-check first: capturing a stale row would record the
                // wrong event's identity.
                val verdict = checkEventIdentity(storedSyncIdOf(event), providerEvent?.syncId)
                if (verdict == EventIdentityVerdict.STALE) {
                    stale.add(event)
                    continue
                }

                // Not getOrPut: it treats a null value as absent and would
                // re-query for every event on a calendar that no longer
                // exists -- exactly the case worth caching.
                val backupInfo =
                    if (backupInfoByCalendar.containsKey(event.calendarId))
                        backupInfoByCalendar[event.calendarId]
                    else
                        backupInfoOf(event.calendarId)
                            .also { backupInfoByCalendar[event.calendarId] = it }

                if (providerEvent?.syncId == null &&
                    providerEvent?.uid2445 == null &&
                    backupInfo == null
                ) {
                    gone.add(event)
                    continue
                }

                toCapture.add(EventIdentityEntity.create(
                    eventId = event.eventId,
                    instanceStartTime = event.instanceStartTime,
                    calendarId = event.calendarId,
                    backupInfo = backupInfo,
                    eventSyncId = providerEvent?.syncId,
                    eventUid = providerEvent?.uid2445,
                    capturedAtTime = capturedAtTime
                ))
            }

            return IdentityCapturePlan(toCapture, stale, gone)
        }
    }
}
