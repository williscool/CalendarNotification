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
 * Whether a stored event still points at the event it was stored for.
 *
 * `eventsV9` keys events by `id`, a row number this device's Calendar Provider
 * assigned. Restore the database onto another phone and that number belongs to
 * some unrelated event, or to nothing. The stored [EventIdentityEntity.eventSyncId]
 * is the server's own id for the event and is the same string on every device
 * that syncs the account, so comparing it against what the provider now returns
 * for the stored `id` answers the question directly.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
enum class EventIdentityVerdict {
    /** Provider agrees: the stored id still resolves to the right event. */
    CURRENT,

    /** The stored id resolves to a different event, or to nothing. Re-resolve it. */
    STALE,

    /** No stored sync id to compare against, so no verdict is possible. */
    UNKNOWN
}

/**
 * The self-check.
 *
 * Deliberately a pure function of the two things it compares, with no context,
 * storage or provider of its own. That keeps the rule that drives the whole
 * feature in one readable place, and testable without a device.
 *
 * **Why not detect a restore device-wide instead?** Two attempts at that were
 * built and reverted (an install fingerprint, then a validation sample over a
 * handful of events). Both read a legitimately absent value as proof the
 * *device* was wrong, and so disabled capture for every event on a healthy
 * phone. This asks about one row, answers about one row, and cannot be
 * poisoned by another row.
 *
 * @param storedSyncId `eventSyncId` from the identity row.
 * @param providerSyncId `Events._SYNC_ID` the provider currently returns for the
 *   stored event id, or null if the provider returned no such event.
 */
fun checkEventIdentity(storedSyncId: String?, providerSyncId: String?): EventIdentityVerdict {
    // Nothing captured, so nothing to compare. Never-synced events land here,
    // as do rows captured before the event had a server id. They are skipped
    // rather than guessed at -- a wrong match is worse than no match.
    if (storedSyncId.isNullOrBlank())
        return EventIdentityVerdict.UNKNOWN

    // The id resolves to nothing, or to an event the server calls something
    // else. Either way this row is not describing that event any more.
    if (providerSyncId.isNullOrBlank() || providerSyncId != storedSyncId)
        return EventIdentityVerdict.STALE

    return EventIdentityVerdict.CURRENT
}
