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
 * ### How a calendar is identified
 *
 * Three fields together identify a calendar independently of any device:
 *
 * | Field | Example | What it is |
 * |---|---|---|
 * | `accountName` | `you@gmail.com` | the account the calendar syncs under |
 * | `accountType` | `com.google` | which sync adapter owns it |
 * | `ownerAccount` | `c_9tnin89@group.calendar.google.com` | who the calendar *belongs to* |
 *
 * `ownerAccount` is the field doing the real work, and it is easy to assume it
 * duplicates `accountName`. It does not. Google gives every secondary calendar
 * its own address as the owner; only your primary calendar has
 * `ownerAccount == accountName`. So on a device with 16 calendars over 2
 * accounts, `accountName` + `accountType` alone leaves 9 and 7 candidates,
 * while adding `ownerAccount` makes all 16 unique -- measured, not assumed.
 *
 * It is also what keeps a *shared* calendar distinct. Subscribe to a calendar
 * owned by another account and you get a row whose `accountName` is yours and
 * whose `ownerAccount` is theirs. Match without the owner and that calendar
 * merges with whatever else you own.
 *
 * ### When the three fields are not enough
 *
 * If several calendars still share all three, the search is **narrowed** by the
 * calendar's `name`, then by its `displayName`. These only ever split an
 * already-matching group; they are never searched on their own.
 *
 * That restriction is deliberate. Display names collide across accounts in
 * practice -- two calendars called "William H - uws" on two different accounts,
 * on the measured device -- so searching by name alone would cross an account
 * boundary, the one thing this must never do.
 *
 * Pure: takes the calendar list rather than a Context, so it tests without a
 * device. See docs/dev_todo/portable_event_identity.md.
 */
object CalendarIdentityMatcher {

    sealed class Result {
        /** Exactly one calendar matched. */
        data class Matched(val calendarId: Long, val strength: MatchStrength) : Result()

        /** Several matched and none could be narrowed; caller must not guess. */
        data class Ambiguous(val candidateIds: List<Long>) : Result()

        /** Nothing matched -- the account is absent from this device. */
        object NotFound : Result()
    }

    /**
     * How confidently a calendar was identified, for logging and for tests.
     *
     * Ordered from strongest to weakest: the account fields alone are
     * conclusive, while the later two mean several calendars shared an account
     * and a human-readable name was needed to tell them apart.
     */
    enum class MatchStrength {
        /** Account fields alone identified exactly one calendar. */
        UNIQUE_ACCOUNT,

        /** Several calendars shared the account; the calendar's `name` split them. */
        ACCOUNT_PLUS_CALENDAR_NAME,

        /** Several shared account and `name`; the display name split them. */
        ACCOUNT_PLUS_DISPLAY_NAME
    }

    /**
     * @param identity the captured identity row whose calendar is wanted.
     * @param calendars every calendar currently on the device.
     */
    fun match(identity: EventIdentityEntity, calendars: List<CalendarRecord>): Result {
        // An identity with no account recorded has nothing to match on. Happens
        // where the calendar was already gone when capture ran.
        if (!identity.hasUsableCalendar())
            return Result.NotFound

        val sameAccount = calendars.filter {
            it.accountName == identity.calendarAccountName &&
            it.accountType == identity.calendarAccountType &&
            it.owner == identity.calendarOwnerAccount
        }

        sameAccount.singleOrNull()?.let {
            return Result.Matched(it.calendarId, MatchStrength.UNIQUE_ACCOUNT)
        }

        if (sameAccount.isEmpty())
            return Result.NotFound

        // Several calendars share the account fields. Split that group by name
        // -- never search outside it, which would cross an account boundary.
        sameAccount.filter { it.name == identity.calendarName }
            .singleOrNull()
            ?.let { return Result.Matched(it.calendarId, MatchStrength.ACCOUNT_PLUS_CALENDAR_NAME) }

        sameAccount.filter { it.displayName == identity.calendarDisplayName }
            .singleOrNull()
            ?.let { return Result.Matched(it.calendarId, MatchStrength.ACCOUNT_PLUS_DISPLAY_NAME) }

        return Result.Ambiguous(sameAccount.map { it.calendarId }.sorted())
    }
}
