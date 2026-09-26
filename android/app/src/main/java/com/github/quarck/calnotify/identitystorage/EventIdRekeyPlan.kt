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

/**
 * Which stored events need their `id` re-keyed, and to what.
 *
 * The companion to `CalendarResolutionPlan`, one layer up. The `cid` update
 * fixes calendar attribution; this fixes the event id itself, so tapping a
 * notification opens the right event and not just the right calendar.
 *
 * ### Deliberately split into planning and applying
 *
 * `eventsV9`'s primary key is `(id, instanceStartTime)`. Room's `@Update`
 * matches *on* the PK, so it structurally cannot change it. Changing `id`
 * means **delete + re-insert**, and the same event `id` lives in three other
 * databases (`RoomEventIdentity`, `dismissedEventsV2`, `manualAlertsV1`) that
 * all need moving in lockstep. Those are four separate SQLite files with no
 * shared transaction — see the plan doc and `ApplicationController.unsnoozeToUpcoming`.
 *
 * So this file is planning only. It works out, for each stored identity:
 *
 *   - Which sync id it holds
 *   - What event id that sync id resolves to on **this** device
 *   - Whether the event is on the calendar its identity already resolved to
 *     (i.e. the `cid` re-attach from the layer below has already run)
 *   - Whether the new id differs from what the event row currently has
 *
 * The apply step (a separate file, once this lands) reads a plan and does the
 * four writes with manual rollback.
 *
 * ### Why the identity row is authoritative for the *new* id, not the *live* row
 *
 * The identity row has `originalEventId` — what the event was called on the
 * old device. The live `eventsV9` row has an `eventId` field which, before
 * this system runs, is the same as `originalEventId` (nothing has re-keyed it
 * yet). We compare the *provider's* current answer for the sync id against
 * whatever the live row says today; a change means we act.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
data class EventIdRekeyPlan(
    /** Events whose `id` should change, and to what. */
    val changes: List<EventIdChange>,

    /** Already correct (or nothing to change). */
    val alreadyCurrent: List<EventIdentityEntity>,

    /** Identity had no sync id, or the sync id could not be resolved. */
    val unresolved: List<EventIdentityEntity>,

    /**
     * The event row this identity references is not in `eventsV9`.
     *
     * Reported so we notice — an identity row without its event is
     * strange, but not corrupt: dismissed events live in a different
     * database and are outside this plan's scope.
     */
    val eventRowMissing: List<EventIdentityEntity>
) {
    data class EventIdChange(
        val identity: EventIdentityEntity,

        /** What the live `eventsV9` row calls this event today. */
        val currentEventId: Long,

        /** What the provider on this device calls it. */
        val newEventId: Long,

        /** Sync id used to resolve, kept for logging. */
        val syncId: String
    )

    val isEmpty: Boolean get() = changes.isEmpty()

    fun summary(): String =
        "${changes.size} to re-key, " +
        "${alreadyCurrent.size} current, " +
        "${unresolved.size} unresolved, " +
        "${eventRowMissing.size} row missing"

    companion object {
        /**
         * Work out which events need re-keying, without changing anything.
         *
         * @param identities every stored identity row.
         * @param liveEventIdOf what `eventsV9.id` currently holds for a given
         *   `(identityEventId, instanceStartTime)`. Returns null when there is
         *   no event row — the caller supplies a map or a lookup.
         * @param providerEventIdOf given a calendar and a sync id, the current
         *   event id on this device, or -1L when the provider does not know
         *   the sync id. Wraps `CalendarProvider.findEventIdBySyncId`; kept as
         *   a function so this class stays pure.
         */
        fun compute(
            identities: List<EventIdentityEntity>,
            liveEventIdOf: (EventIdentityEntity) -> Long?,
            providerEventIdOf: (calendarId: Long, syncId: String) -> Long
        ): EventIdRekeyPlan {
            val changes = mutableListOf<EventIdChange>()
            val current = mutableListOf<EventIdentityEntity>()
            val unresolved = mutableListOf<EventIdentityEntity>()
            val missing = mutableListOf<EventIdentityEntity>()

            for (identity in identities) {
                val syncId = identity.eventSyncId
                if (syncId.isNullOrBlank()) {
                    unresolved.add(identity)
                    continue
                }

                val liveId = liveEventIdOf(identity)
                if (liveId == null) {
                    missing.add(identity)
                    continue
                }

                // The re-key can only run once `cid` is correct — otherwise
                // we would be looking up the sync id under the wrong
                // calendar. In practice the `cid` layer runs first in the
                // same rescan pass; if the identity row still points at the
                // old device's `originalCalendarId`, the `cid` re-attach has
                // not run yet and we should not act.
                //
                // The identity row's `originalCalendarId` is not updated when
                // `cid` moves (that lives on the event row), so instead we
                // ask the provider directly: does the sync id resolve to
                // some event on the calendar the identity claims?
                val newId = providerEventIdOf(identity.originalCalendarId, syncId)
                if (newId == -1L) {
                    unresolved.add(identity)
                    continue
                }

                if (newId == liveId)
                    current.add(identity)
                else
                    changes.add(EventIdChange(
                        identity = identity,
                        currentEventId = liveId,
                        newEventId = newId,
                        syncId = syncId
                    ))
            }

            return EventIdRekeyPlan(changes, current, unresolved, missing)
        }
    }
}
