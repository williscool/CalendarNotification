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

import com.github.quarck.calnotify.calendar.CalendarRecord

/**
 * What re-associating a restored database's calendars *would* do, computed
 * before anything is written.
 *
 * Deliberately split from the write. Planning is pure -- it takes the stored
 * rows and the device's calendar list and returns a decision per event -- so it
 * can be tested exhaustively without a database, logged before it runs, and
 * surfaced to the user by a manual "re-link" action. The write step then does
 * nothing but apply [CalendarResolutionPlan.changes].
 *
 * Only `cid` is in scope here. The event id is half the primary key of
 * `eventsV9`, so changing it means delete + re-insert across four databases;
 * that is a separate, riskier step. Updating `cid` alone is a plain column
 * update and already fixes calendar attribution, the filter pills, and
 * per-calendar handled settings.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
data class CalendarResolutionPlan(
    /** Events whose `cid` should change, and what to. */
    val changes: List<CalendarChange>,

    /** Already pointing at the right calendar. Nothing to do. */
    val alreadyCurrent: List<EventIdentityEntity>,

    /** The account exists on no calendar here -- not signed in, or not synced yet. */
    val calendarNotFound: List<EventIdentityEntity>,

    /**
     * Several calendars matched and none could be told apart.
     *
     * Reported rather than guessed: attaching events to an arbitrary one of
     * several candidates is corruption that looks like success. These keep their
     * stale `cid` and are retried on a later pass, by which time a sync may have
     * made the calendars distinguishable.
     */
    val ambiguous: List<AmbiguousCalendar>
) {
    data class CalendarChange(
        val identity: EventIdentityEntity,
        val oldCalendarId: Long,
        val newCalendarId: Long,
        val strength: CalendarIdentityMatcher.MatchStrength
    )

    data class AmbiguousCalendar(
        val identity: EventIdentityEntity,
        val candidateIds: List<Long>
    )

    val isEmpty: Boolean get() = changes.isEmpty()

    /**
     * Old calendar id to new, for repairing the per-calendar `calendar_handled_.<id>`
     * preferences alongside the event rows.
     *
     * A map rather than the per-event list because settings are per calendar:
     * 373 events across two calendars produce two remappings, not 373.
     */
    val calendarIdRemapping: Map<Long, Long>
        get() = changes.associate { it.oldCalendarId to it.newCalendarId }

    fun summary(): String =
        "${changes.size} to re-link (${calendarIdRemapping.size} calendar(s)), " +
        "${alreadyCurrent.size} current, ${calendarNotFound.size} calendar not found, " +
        "${ambiguous.size} ambiguous"

    companion object {
        /**
         * Work out what should change, without changing anything.
         *
         * @param identities stored identity rows for the events to consider --
         *   normally the ones the self-check reported stale.
         * @param calendars every calendar currently on the device.
         * @param currentCalendarIdOf the `cid` each event row holds right now.
         *   Passed in rather than read from the identity row, because
         *   `originalCalendarId` records what was captured on the *old* device
         *   and the event row is what actually needs correcting.
         */
        fun compute(
            identities: List<EventIdentityEntity>,
            calendars: List<CalendarRecord>,
            currentCalendarIdOf: (EventIdentityEntity) -> Long
        ): CalendarResolutionPlan {
            val changes = mutableListOf<CalendarChange>()
            val current = mutableListOf<EventIdentityEntity>()
            val notFound = mutableListOf<EventIdentityEntity>()
            val ambiguous = mutableListOf<AmbiguousCalendar>()

            // One match per distinct calendar tuple, not per event: hundreds of
            // events typically share a handful of calendars.
            val matchCache = HashMap<CalendarTupleKey, CalendarIdentityMatcher.Result>()

            for (identity in identities) {
                val result = matchCache.getOrPut(CalendarTupleKey.of(identity)) {
                    CalendarIdentityMatcher.match(identity, calendars)
                }

                when (result) {
                    is CalendarIdentityMatcher.Result.NotFound ->
                        notFound.add(identity)

                    is CalendarIdentityMatcher.Result.Ambiguous ->
                        ambiguous.add(AmbiguousCalendar(identity, result.candidateIds))

                    is CalendarIdentityMatcher.Result.Matched -> {
                        val now = currentCalendarIdOf(identity)
                        if (now == result.calendarId)
                            current.add(identity)
                        else
                            changes.add(CalendarChange(
                                identity = identity,
                                oldCalendarId = now,
                                newCalendarId = result.calendarId,
                                strength = result.strength
                            ))
                    }
                }
            }

            return CalendarResolutionPlan(changes, current, notFound, ambiguous)
        }
    }

    /** The fields the matcher actually keys on, for caching its result. */
    private data class CalendarTupleKey(
        val accountName: String,
        val accountType: String,
        val ownerAccount: String,
        val name: String,
        val displayName: String
    ) {
        companion object {
            fun of(identity: EventIdentityEntity) = CalendarTupleKey(
                accountName = identity.calendarAccountName,
                accountType = identity.calendarAccountType,
                ownerAccount = identity.calendarOwnerAccount,
                name = identity.calendarName,
                displayName = identity.calendarDisplayName
            )
        }
    }
}
