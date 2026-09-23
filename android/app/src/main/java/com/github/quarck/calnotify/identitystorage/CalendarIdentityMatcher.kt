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
 * Finds the calendar a captured identity refers to, on a device that assigned
 * different row ids than the one it was captured on.
 *
 * ### Why not `CalendarProvider.findMatchingCalendarId`
 *
 * That matcher exists and is used for single-event un-dismiss, but it is the
 * wrong tool here. Its second tier matches on `accountName` + `accountType`
 * alone and returns the first row the provider hands back. **Measured on a real
 * device: that tier covers 9 and 7 calendars respectively for the two accounts
 * present** -- so it would silently attach events to whichever of nine calendars
 * happened to sort first.
 *
 * Tolerable when a human is un-dismissing one event and can see the result.
 * Not tolerable when re-associating several hundred rows unattended, where a
 * wrong answer is indistinguishable from a right one and nobody is watching.
 *
 * So this matcher reports [Ambiguous] rather than picking. An unresolved row
 * keeps its stale id and can be retried or resolved by hand; a row silently
 * attached to the wrong calendar is corruption that looks like success.
 *
 * ### The tiers
 *
 * 1. `accountName` + `accountType` + `ownerAccount` -- the durable triple.
 *    Measured unique across all 16 calendars on a real device.
 * 2. Tier 1 plus `name`, then plus `displayName` -- only ever *narrows* a tier-1
 *    tie, never widens the search.
 *
 * There is deliberately no display-name-only tier. Display names collide across
 * accounts in practice (two calendars named "William H - uws" on different
 * accounts, on the measured device), and matching on one would cross an account
 * boundary -- the one thing this must never do.
 *
 * Pure: takes the calendar list rather than a Context, so it tests without a
 * device. See docs/dev_todo/portable_event_identity.md.
 */
object CalendarIdentityMatcher {

    sealed class Result {
        /** Exactly one calendar matched. */
        data class Matched(val calendarId: Long, val tier: Tier) : Result()

        /** Several matched and none could be narrowed; caller must not guess. */
        data class Ambiguous(val candidateIds: List<Long>) : Result()

        /** Nothing matched -- the account is absent from this device. */
        object NotFound : Result()
    }

    /** Which rule produced a match, for logging and for tests to assert on. */
    enum class Tier {
        /** accountName + accountType + ownerAccount. */
        ACCOUNT_TRIPLE,

        /** Tier 1 narrowed by the calendar's `name`. */
        ACCOUNT_TRIPLE_AND_NAME,

        /** Tier 1 narrowed by its display name. */
        ACCOUNT_TRIPLE_AND_DISPLAY_NAME
    }

    /**
     * @param identity the captured identity row whose calendar is wanted.
     * @param calendars every calendar currently on the device.
     */
    fun match(identity: EventIdentityEntity, calendars: List<CalendarRecord>): Result {
        // An identity with no account recorded has nothing to match on. Happens
        // where the calendar was already gone when capture ran.
        if (identity.calendarAccountName.isBlank() || identity.calendarAccountType.isBlank())
            return Result.NotFound

        val byTriple = calendars.filter {
            it.accountName == identity.calendarAccountName &&
            it.accountType == identity.calendarAccountType &&
            it.owner == identity.calendarOwnerAccount
        }

        byTriple.singleOrNull()?.let {
            return Result.Matched(it.calendarId, Tier.ACCOUNT_TRIPLE)
        }

        if (byTriple.isEmpty())
            return Result.NotFound

        // Several calendars share the account triple. Narrow -- never widen.
        byTriple.filter { it.name == identity.calendarName }
            .singleOrNull()
            ?.let { return Result.Matched(it.calendarId, Tier.ACCOUNT_TRIPLE_AND_NAME) }

        byTriple.filter { it.displayName == identity.calendarDisplayName }
            .singleOrNull()
            ?.let { return Result.Matched(it.calendarId, Tier.ACCOUNT_TRIPLE_AND_DISPLAY_NAME) }

        return Result.Ambiguous(byTriple.map { it.calendarId }.sorted())
    }
}
