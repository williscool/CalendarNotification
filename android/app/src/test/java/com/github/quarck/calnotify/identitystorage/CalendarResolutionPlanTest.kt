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
 * Tests the plan that re-association will follow, before any write happens.
 *
 * Planning is pure, so these cover the decisions exhaustively without a
 * database: which events change calendar, which are already right, and which
 * are deliberately left alone.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
class CalendarResolutionPlanTest {

    private fun calendar(
        id: Long,
        accountName: String,
        owner: String = "c_resource$id@group.calendar.google.com",
        displayName: String = "Calendar $id",
        name: String = displayName
    ) = CalendarRecord(
        calendarId = id,
        owner = owner,
        displayName = displayName,
        name = name,
        accountName = accountName,
        accountType = "com.google",
        timeZone = "UTC",
        color = 0,
        isVisible = true,
        isPrimary = false,
        isReadOnly = false,
        isSynced = true
    )

    /** An identity row as captured against [capturedCalendarId] on the old device. */
    private fun identity(
        eventId: Long,
        capturedCalendarId: Long,
        accountName: String = ACCOUNT_A,
        owner: String = "c_resource$capturedCalendarId@group.calendar.google.com",
        displayName: String = "Calendar $capturedCalendarId",
        name: String = displayName
    ) = EventIdentityEntity.create(
        eventId = eventId,
        instanceStartTime = INSTANCE_START,
        calendarId = capturedCalendarId,
        backupInfo = CalendarBackupInfo(
            calendarId = capturedCalendarId,
            accountName = accountName,
            accountType = "com.google",
            ownerAccount = owner,
            displayName = displayName,
            name = name
        ),
        eventSyncId = "sync-$eventId",
        eventUid = null,
        capturedAtTime = 1L
    )

    /** Stands in for reading `eventsV9.cid`; defaults to the captured value. */
    private fun currentCid(overrides: Map<Long, Long> = emptyMap()):
            (EventIdentityEntity) -> Long =
        { overrides[it.eventId] ?: it.originalCalendarId }

    // --- The case the whole feature exists for ---

    @Test
    fun planRenumbersEventsOntoTheirNewCalendarId() {
        // Captured against calendar 6; this device calls the same calendar 31.
        val plan = CalendarResolutionPlan.compute(
            identities = listOf(identity(100L, capturedCalendarId = 6L)),
            calendars = listOf(
                calendar(31L, ACCOUNT_A,
                         owner = "c_resource6@group.calendar.google.com",
                         displayName = "Calendar 6")
            ),
            currentCalendarIdOf = currentCid()
        )

        assertEquals(1, plan.changes.size)
        val change = plan.changes.first()
        assertEquals(6L, change.oldCalendarId)
        assertEquals(31L, change.newCalendarId)
        assertEquals(CalendarIdentityMatcher.MatchStrength.UNIQUE_ACCOUNT, change.strength)
    }

    @Test
    fun anEventAlreadyOnTheRightCalendarIsNotRewritten() {
        // A healthy device. Guards against pointless writes on every rescan.
        val plan = CalendarResolutionPlan.compute(
            identities = listOf(identity(100L, capturedCalendarId = 6L)),
            calendars = listOf(
                calendar(6L, ACCOUNT_A,
                         owner = "c_resource6@group.calendar.google.com",
                         displayName = "Calendar 6")
            ),
            currentCalendarIdOf = currentCid()
        )

        assertTrue("nothing to write", plan.isEmpty)
        assertEquals(1, plan.alreadyCurrent.size)
    }

    @Test
    fun comparesAgainstTheLiveCidNotTheCapturedOne() {
        // The event row has already been re-linked to 31 by an earlier pass,
        // while the identity row still records 6 as where it was captured.
        // originalCalendarId is history; the event row is what matters.
        val plan = CalendarResolutionPlan.compute(
            identities = listOf(identity(100L, capturedCalendarId = 6L)),
            calendars = listOf(
                calendar(31L, ACCOUNT_A,
                         owner = "c_resource6@group.calendar.google.com",
                         displayName = "Calendar 6")
            ),
            currentCalendarIdOf = currentCid(mapOf(100L to 31L))
        )

        assertTrue("already re-linked, so no second write", plan.isEmpty)
        assertEquals(1, plan.alreadyCurrent.size)
    }

    // --- Rows deliberately left alone ---

    @Test
    fun accountMissingFromTheDeviceIsReportedNotGuessed() {
        val plan = CalendarResolutionPlan.compute(
            identities = listOf(identity(100L, capturedCalendarId = 6L, accountName = ACCOUNT_A)),
            calendars = listOf(calendar(11L, ACCOUNT_B)),
            currentCalendarIdOf = currentCid()
        )

        assertTrue(plan.isEmpty)
        assertEquals(1, plan.calendarNotFound.size)
    }

    @Test
    fun ambiguousCalendarsAreReportedWithTheirCandidates() {
        // Two indistinguishable calendars. Picking one would attach events to an
        // arbitrary calendar and look like success.
        val plan = CalendarResolutionPlan.compute(
            identities = listOf(
                identity(100L, capturedCalendarId = 6L, owner = "", displayName = "dup", name = "dup")
            ),
            calendars = listOf(
                calendar(11L, ACCOUNT_A, owner = "", displayName = "dup", name = "dup"),
                calendar(12L, ACCOUNT_A, owner = "", displayName = "dup", name = "dup")
            ),
            currentCalendarIdOf = currentCid()
        )

        assertTrue("must not write anything", plan.isEmpty)
        assertEquals(1, plan.ambiguous.size)
        assertEquals(listOf(11L, 12L), plan.ambiguous.first().candidateIds)
    }

    @Test
    fun anEmptyCalendarListYieldsNoChanges() {
        // Device mid-sync: calendars have not arrived yet. Must produce a plan
        // that writes nothing rather than one that clears cids.
        val plan = CalendarResolutionPlan.compute(
            identities = (1L..5L).map { identity(it, capturedCalendarId = 6L) },
            calendars = emptyList(),
            currentCalendarIdOf = currentCid()
        )

        assertTrue(plan.isEmpty)
        assertEquals(5, plan.calendarNotFound.size)
    }

    @Test
    fun noIdentitiesYieldsAnEmptyPlan() {
        val plan = CalendarResolutionPlan.compute(emptyList(), emptyList(), currentCid())

        assertTrue(plan.isEmpty)
        assertEquals(0, plan.alreadyCurrent.size)
        assertEquals(0, plan.calendarNotFound.size)
        assertEquals(0, plan.ambiguous.size)
    }

    // --- Efficiency: the matcher runs per calendar, not per event ---

    @Test
    fun eventsSharingACalendarAreMatchedOnce() {
        // 200 events over 2 calendars is the real shape. The matcher must run
        // twice, not 200 times -- it scans the whole calendar list each call.
        var matcherCalls = 0
        val calendars = listOf(
            calendar(31L, ACCOUNT_A, owner = "c_resource6@group.calendar.google.com", displayName = "Calendar 6"),
            calendar(32L, ACCOUNT_B, owner = "c_resource16@group.calendar.google.com", displayName = "Calendar 16")
        )

        val identities =
            (1L..100L).map { identity(it, capturedCalendarId = 6L, accountName = ACCOUNT_A) } +
            (101L..200L).map { identity(it, capturedCalendarId = 16L, accountName = ACCOUNT_B) }

        val plan = CalendarResolutionPlan.compute(
            identities = identities,
            calendars = calendars,
            currentCalendarIdOf = { matcherCalls++; it.originalCalendarId }
        )

        assertEquals("every event re-linked", 200, plan.changes.size)
        assertEquals(
            "the cid lookup still runs per event, but matching is cached",
            200, matcherCalls
        )
        assertEquals(
            "two calendars, so two remappings -- not 200",
            mapOf(6L to 31L, 16L to 32L), plan.calendarIdRemapping
        )
    }

    // --- The remapping that drives per-calendar settings repair ---

    @Test
    fun remappingCollapsesToOneEntryPerCalendar() {
        val plan = CalendarResolutionPlan.compute(
            identities = (1L..10L).map { identity(it, capturedCalendarId = 6L) },
            calendars = listOf(
                calendar(99L, ACCOUNT_A,
                         owner = "c_resource6@group.calendar.google.com",
                         displayName = "Calendar 6")
            ),
            currentCalendarIdOf = currentCid()
        )

        assertEquals(10, plan.changes.size)
        assertEquals(
            "ten events, one calendar_handled_ key to repair",
            mapOf(6L to 99L), plan.calendarIdRemapping
        )
    }

    @Test
    fun remappingIsEmptyWhenNothingChanges() {
        val plan = CalendarResolutionPlan.compute(
            identities = listOf(identity(100L, capturedCalendarId = 6L)),
            calendars = listOf(
                calendar(6L, ACCOUNT_A,
                         owner = "c_resource6@group.calendar.google.com",
                         displayName = "Calendar 6")
            ),
            currentCalendarIdOf = currentCid()
        )

        assertTrue(plan.calendarIdRemapping.isEmpty())
    }

    // --- A mixed batch, which is what a real restore looks like ---

    @Test
    fun aMixedBatchSortsEveryEventIntoExactlyOneBucket() {
        val calendars = listOf(
            // account A's calendar, renumbered
            calendar(31L, ACCOUNT_A, owner = "c_resource6@group.calendar.google.com", displayName = "Calendar 6"),
            // two indistinguishable ones
            calendar(41L, ACCOUNT_B, owner = "", displayName = "dup", name = "dup"),
            calendar(42L, ACCOUNT_B, owner = "", displayName = "dup", name = "dup")
        )

        val identities = listOf(
            identity(1L, capturedCalendarId = 6L, accountName = ACCOUNT_A),            // -> change
            identity(2L, capturedCalendarId = 31L, accountName = ACCOUNT_A,
                     owner = "c_resource6@group.calendar.google.com",
                     displayName = "Calendar 6"),                                      // -> current
            identity(3L, capturedCalendarId = 9L, accountName = ACCOUNT_B,
                     owner = "", displayName = "dup", name = "dup"),                   // -> ambiguous
            identity(4L, capturedCalendarId = 7L, accountName = "gone@example.com")    // -> not found
        )

        val plan = CalendarResolutionPlan.compute(
            identities = identities,
            calendars = calendars,
            currentCalendarIdOf = currentCid(mapOf(2L to 31L))
        )

        assertEquals(1, plan.changes.size)
        assertEquals(1, plan.alreadyCurrent.size)
        assertEquals(1, plan.ambiguous.size)
        assertEquals(1, plan.calendarNotFound.size)
        assertEquals(
            "every identity accounted for exactly once",
            identities.size,
            plan.changes.size + plan.alreadyCurrent.size +
                plan.ambiguous.size + plan.calendarNotFound.size
        )
    }

    @Test
    fun summaryNamesEveryBucket() {
        val plan = CalendarResolutionPlan.compute(
            identities = listOf(identity(1L, capturedCalendarId = 6L)),
            calendars = listOf(
                calendar(31L, ACCOUNT_A,
                         owner = "c_resource6@group.calendar.google.com",
                         displayName = "Calendar 6")
            ),
            currentCalendarIdOf = currentCid()
        )

        val summary = plan.summary()
        assertTrue("mentions what will change: $summary", summary.contains("1 to re-link"))
        assertTrue("mentions the calendar count: $summary", summary.contains("1 calendar(s)"))
    }

    companion object {
        private const val ACCOUNT_A = "will@example.com"
        private const val ACCOUNT_B = "w.harris@example.org"
        private const val INSTANCE_START = 1_700_000_000_000L
    }
}
