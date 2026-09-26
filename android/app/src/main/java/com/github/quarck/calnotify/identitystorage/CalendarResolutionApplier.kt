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

import com.github.quarck.calnotify.calendar.EventAlertRecord

/**
 * Turns a [CalendarResolutionPlan] into the edits that carry it out.
 *
 * Kept separate from both the plan and the storage call for the same reason the
 * plan is separate from the matcher: the interesting part is *what* changes, and
 * that should be inspectable without a database. This produces edited copies of
 * event records; the caller writes them.
 *
 * ### Scope: events only
 *
 * The `calendar_handled_.<id>` preferences also need re-keying when a calendar
 * id moves, so they follow the events onto the new device. That is a UX repair
 * layered on top of the data repair here and lives in a separate change.
 *
 * ### Scope: `cid` only
 *
 * `eventsV9`'s primary key is `(id, istart)`. `cid` is a payload column, so
 * changing it is an ordinary update that Room applies in place, inside the
 * transaction `RoomEventsStorage.updateEvents` already opens. Changing `id`
 * would instead mean delete + re-insert across four databases with manual
 * rollback -- a separate, much riskier step.
 *
 * `cid` alone is worth shipping on its own: it fixes calendar attribution, the
 * filter pills, and the per-calendar handled settings. An event whose `id` is
 * still stale opens the wrong thing when tapped, but it is at least filed under
 * the right calendar.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
object CalendarResolutionApplier {

    data class Edits(
        /** Event rows with their `calendarId` corrected, ready to write back. */
        val updatedEvents: List<EventAlertRecord>
    ) {
        val isEmpty: Boolean get() = updatedEvents.isEmpty()

        fun summary(): String = "${updatedEvents.size} event(s) re-linked"
    }

    /**
     * Work out the edits a plan implies.
     *
     * @param plan what resolution decided, from [CalendarResolutionPlan.compute].
     * @param eventsByKey the current event rows, keyed `(eventId, instanceStartTime)`.
     *   Rows the plan names but this does not contain are skipped: the event was
     *   dismissed or deleted between planning and applying.
     */
    fun computeEdits(
        plan: CalendarResolutionPlan,
        eventsByKey: Map<Pair<Long, Long>, EventAlertRecord>
    ): Edits {
        if (plan.changes.isEmpty())
            return Edits(emptyList())

        val updated = plan.changes.mapNotNull { change ->
            val key = change.identity.eventId to change.identity.instanceStartTime
            val event = eventsByKey[key] ?: return@mapNotNull null

            // Guard against writing a row that is already correct: the plan was
            // computed from a snapshot, and anything could have moved since.
            if (event.calendarId == change.newCalendarId)
                return@mapNotNull null

            event.copy(calendarId = change.newCalendarId)
        }

        return Edits(updated)
    }
}
