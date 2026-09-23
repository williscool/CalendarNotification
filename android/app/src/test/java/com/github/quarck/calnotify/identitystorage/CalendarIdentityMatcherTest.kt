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
import com.github.quarck.calnotify.calendar.CalendarRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the calendar half of resolution.
 *
 * The fixtures mirror a real device's shape deliberately: two Google accounts
 * with 7 and 9 calendars, several sharing similar display names. That is the
 * arrangement in which a loose matcher silently attaches events to the wrong
 * calendar, so it is the arrangement worth testing against.
 *
 * Plain JUnit -- the matcher takes a calendar list rather than a Context.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
class CalendarIdentityMatcherTest {

    private fun calendar(
        id: Long,
        accountName: String,
        owner: String = accountName,
        displayName: String = "Calendar $id",
        name: String = displayName,
        accountType: String = "com.google"
    ) = CalendarRecord(
        calendarId = id,
        owner = owner,
        displayName = displayName,
        name = name,
        accountName = accountName,
        accountType = accountType,
        timeZone = "UTC",
        color = 0,
        isVisible = true,
        isPrimary = false,
        isReadOnly = false,
        isSynced = true
    )

    private fun identity(
        calendarId: Long = 6L,
        accountName: String = ACCOUNT_A,
        owner: String = ACCOUNT_A,
        displayName: String = "Work",
        name: String = "Work",
        accountType: String = "com.google"
    ) = EventIdentityEntity.create(
        eventId = 100L,
        instanceStartTime = 1_700_000_000_000L,
        calendarId = calendarId,
        backupInfo = CalendarBackupInfo(
            calendarId = calendarId,
            accountName = accountName,
            accountType = accountType,
            ownerAccount = owner,
            displayName = displayName,
            name = name
        ),
        eventSyncId = "sync-abc",
        eventUid = null,
        capturedAtTime = 1L
    )

    // --- The normal case ---

    @Test
    fun matchesOnTheAccountTripleWhenItIsUnique() {
        val result = CalendarIdentityMatcher.match(
            identity(accountName = ACCOUNT_A, owner = ACCOUNT_A),
            listOf(
                calendar(11L, ACCOUNT_A),
                calendar(22L, ACCOUNT_B)
            )
        )

        assertEquals(
            CalendarIdentityMatcher.Result.Matched(
                11L, CalendarIdentityMatcher.MatchStrength.UNIQUE_ACCOUNT
            ),
            result
        )
    }

    @Test
    fun newDeviceIdDiffersFromTheCapturedOne() {
        // The whole point: the identity was captured against calendar 6, and the
        // new device calls the same calendar 31.
        val result = CalendarIdentityMatcher.match(
            identity(calendarId = 6L),
            listOf(calendar(31L, ACCOUNT_A))
        )

        assertEquals(
            CalendarIdentityMatcher.Result.Matched(
                31L, CalendarIdentityMatcher.MatchStrength.UNIQUE_ACCOUNT
            ),
            result
        )
    }

    @Test
    fun accountAbsentFromTheDeviceIsNotFound() {
        val result = CalendarIdentityMatcher.match(
            identity(accountName = ACCOUNT_A),
            listOf(calendar(22L, ACCOUNT_B))
        )

        assertEquals(CalendarIdentityMatcher.Result.NotFound, result)
    }

    @Test
    fun emptyCalendarListIsNotFound() {
        // A device mid-sync has no calendars yet. Must not throw, and must not
        // be mistaken for a match.
        assertEquals(
            CalendarIdentityMatcher.Result.NotFound,
            CalendarIdentityMatcher.match(identity(), emptyList())
        )
    }

    // --- Account boundaries must never be crossed ---

    @Test
    fun neverMatchesAcrossAccountsEvenOnAnIdenticalDisplayName() {
        // Measured on a real device: two calendars named "William H - uws" on
        // two different accounts. Matching on display name alone would attach
        // one account's events to the other's calendar.
        val result = CalendarIdentityMatcher.match(
            identity(accountName = ACCOUNT_A, owner = ACCOUNT_A, displayName = "William H - uws", name = "William H - uws"),
            listOf(calendar(99L, ACCOUNT_B, displayName = "William H - uws", name = "William H - uws"))
        )

        assertEquals(
            "a same-named calendar on a different account is not a match",
            CalendarIdentityMatcher.Result.NotFound, result
        )
    }

    @Test
    fun differentAccountTypeIsNotAMatch() {
        val result = CalendarIdentityMatcher.match(
            identity(accountName = ACCOUNT_A, accountType = "com.google"),
            listOf(calendar(11L, ACCOUNT_A, accountType = "com.exchange"))
        )

        assertEquals(CalendarIdentityMatcher.Result.NotFound, result)
    }

    @Test
    fun aCalendarSharedFromAnotherAccountIsNotConfusedWithYourOwn() {
        // The real case this protects: a calendar shared from one account into
        // another. Your account name is on the row, but the owner is theirs --
        // measured on a real device, where a calendar is shared between two
        // accounts to keep Calendly blocks consistent. Matching without the
        // owner would merge it with a calendar you actually own.
        val result = CalendarIdentityMatcher.match(
            identity(accountName = ACCOUNT_A, owner = ACCOUNT_A),
            listOf(calendar(11L, ACCOUNT_A, owner = "someone.else@example.com"))
        )

        assertEquals(CalendarIdentityMatcher.Result.NotFound, result)
    }

    // --- Ambiguity is reported, never guessed ---

    @Test
    fun severalCalendarsSharingTheTripleAreNarrowedByName() {
        val result = CalendarIdentityMatcher.match(
            identity(name = "Holidays", displayName = "Holidays"),
            listOf(
                calendar(11L, ACCOUNT_A, name = "Meetups", displayName = "Meetups"),
                calendar(12L, ACCOUNT_A, name = "Holidays", displayName = "Holidays"),
                calendar(13L, ACCOUNT_A, name = "Family", displayName = "Family")
            )
        )

        assertEquals(
            CalendarIdentityMatcher.Result.Matched(
                12L, CalendarIdentityMatcher.MatchStrength.ACCOUNT_PLUS_CALENDAR_NAME
            ),
            result
        )
    }

    @Test
    fun fallsBackToDisplayNameWhenNameDoesNotDistinguish() {
        // `name` is often a raw calendar id or empty; displayName is what the
        // user sees. Both are only ever used to narrow a tier-1 set.
        val result = CalendarIdentityMatcher.match(
            identity(name = "", displayName = "Trello"),
            listOf(
                calendar(11L, ACCOUNT_A, name = "", displayName = "Meetups"),
                calendar(12L, ACCOUNT_A, name = "", displayName = "Trello")
            )
        )

        assertEquals(
            CalendarIdentityMatcher.Result.Matched(
                12L, CalendarIdentityMatcher.MatchStrength.ACCOUNT_PLUS_DISPLAY_NAME
            ),
            result
        )
    }

    @Test
    fun trulyIndistinguishableCalendarsReportAmbiguousRatherThanPickingOne() {
        // This is the property that matters most. Guessing here attaches events
        // to an arbitrary calendar and looks like success.
        val result = CalendarIdentityMatcher.match(
            identity(name = "Work", displayName = "Work"),
            listOf(
                calendar(30L, ACCOUNT_A, name = "Work", displayName = "Work"),
                calendar(11L, ACCOUNT_A, name = "Work", displayName = "Work")
            )
        )

        assertEquals(
            CalendarIdentityMatcher.Result.Ambiguous(listOf(11L, 30L)),
            result
        )
    }

    @Test
    fun ambiguityReportsEveryCandidateSoTheCallerCanLogIt() {
        val result = CalendarIdentityMatcher.match(
            identity(name = "x", displayName = "x"),
            (1L..4L).map { calendar(it, ACCOUNT_A, name = "y", displayName = "y") }
        )

        assertTrue(result is CalendarIdentityMatcher.Result.Ambiguous)
        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            (result as CalendarIdentityMatcher.Result.Ambiguous).candidateIds
        )
    }

    // --- Degenerate identities ---

    @Test
    fun identityWithNoAccountRecordedIsNotFound() {
        // Capture writes empty strings when the calendar was already gone. There
        // is nothing to match on, and an empty-string match against a calendar
        // with empty fields would be a false positive.
        val result = CalendarIdentityMatcher.match(
            identity(accountName = "", owner = "", accountType = ""),
            listOf(calendar(11L, "", owner = "", accountType = ""))
        )

        assertEquals(CalendarIdentityMatcher.Result.NotFound, result)
    }

    @Test
    fun blankAccountTypeAloneIsAlsoRejected() {
        assertEquals(
            CalendarIdentityMatcher.Result.NotFound,
            CalendarIdentityMatcher.match(
                identity(accountName = ACCOUNT_A, accountType = ""),
                listOf(calendar(11L, ACCOUNT_A, accountType = ""))
            )
        )
    }

    // --- The measured device, reproduced ---

    @Test
    fun resolvesEveryCalendarOnTheMeasuredDeviceLayout() {
        // Reproduces the real snapshot's shape: 16 calendars over two Google
        // accounts (7 + 9). The key detail is that Google gives each secondary
        // calendar its own resource id as `ownerAccount` -- only the primary has
        // owner == accountName. That is precisely why the account *triple* is
        // unique per calendar while accountName+accountType alone covers 7 and 9
        // of them.
        val calendars =
            (1L..6L).map { calendar(it, ACCOUNT_A, owner = "c_resource$it@group.calendar.google.com") } +
            listOf(calendar(7L, ACCOUNT_A, owner = ACCOUNT_A)) +          // the primary
            (8L..15L).map { calendar(it, ACCOUNT_B, owner = "c_resource$it@group.calendar.google.com") } +
            listOf(calendar(16L, ACCOUNT_B, owner = ACCOUNT_B))          // the primary

        // Every one must resolve on tier 1 alone, at a renumbered id.
        calendars.forEach { cal ->
            val result = CalendarIdentityMatcher.match(
                identity(
                    calendarId = cal.calendarId + 1000L,   // captured under a different id
                    accountName = cal.accountName,
                    owner = cal.owner,
                    displayName = cal.displayName,
                    name = cal.name
                ),
                calendars
            )
            assertEquals(
                "calendar ${cal.calendarId} must resolve on the account triple",
                CalendarIdentityMatcher.Result.Matched(
                    cal.calendarId, CalendarIdentityMatcher.MatchStrength.UNIQUE_ACCOUNT
                ),
                result
            )
        }
    }

    @Test
    fun accountNameAndTypeAloneWouldHaveBeenAmbiguous() {
        // The regression guard for why CalendarProvider.findMatchingCalendarId
        // is not used here: its tier 2 drops `ownerAccount`, and on this layout
        // that leaves 7 indistinguishable candidates. Asserted by stripping the
        // owner from both sides and checking the matcher refuses to guess.
        val calendars = (1L..7L).map { calendar(it, ACCOUNT_A, owner = "", displayName = "shared", name = "shared") }

        val result = CalendarIdentityMatcher.match(
            identity(accountName = ACCOUNT_A, owner = "", displayName = "shared", name = "shared"),
            calendars
        )

        assertEquals(
            "without the owner field these are indistinguishable, so refuse",
            CalendarIdentityMatcher.Result.Ambiguous(listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L)),
            result
        )
    }

    companion object {
        private const val ACCOUNT_A = "will@example.com"
        private const val ACCOUNT_B = "w.harris@example.org"
    }
}
