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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the edits that carry out a calendar re-association.
 *
 * Plain JUnit: the applier takes a plan plus plain lookups and returns edited
 * copies, so nothing here needs a database or a Context. Writing those edits is
 * the caller's job and is covered separately.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
class CalendarResolutionApplierTest {

    private fun event(eventId: Long, calendarId: Long) = EventAlertRecord(
        calendarId = calendarId,
        eventId = eventId,
        isAllDay = false,
        isRepeating = false,
        alertTime = INSTANCE_START - 600_000L,
        notificationId = 0,
        title = "Event $eventId",
        desc = "",
        startTime = INSTANCE_START,
        endTime = INSTANCE_START + 3_600_000L,
        instanceStartTime = INSTANCE_START,
        instanceEndTime = INSTANCE_START + 3_600_000L,
        location = "",
        lastStatusChangeTime = 0L
    )

    private fun identity(eventId: Long, calendarId: Long) = EventIdentityEntity.create(
        eventId = eventId,
        instanceStartTime = INSTANCE_START,
        calendarId = calendarId,
        backupInfo = CalendarBackupInfo(
            calendarId = calendarId,
            accountName = "user@example.com",
            accountType = "com.google",
            ownerAccount = "c_resource$calendarId@group.calendar.google.com",
            displayName = "Calendar $calendarId",
            name = "Calendar $calendarId"
        ),
        eventSyncId = "sync-$eventId",
        eventUid = null,
        capturedAtTime = 1L
    )

    private fun planMoving(vararg moves: Triple<Long, Long, Long>): CalendarResolutionPlan =
        CalendarResolutionPlan(
            changes = moves.map { (eventId, from, to) ->
                CalendarResolutionPlan.CalendarChange(
                    identity = identity(eventId, from),
                    oldCalendarId = from,
                    newCalendarId = to,
                    strength = CalendarIdentityMatcher.MatchStrength.UNIQUE_ACCOUNT
                )
            },
            alreadyCurrent = emptyList(),
            calendarNotFound = emptyList(),
            ambiguous = emptyList()
        )

    private fun edits(
        plan: CalendarResolutionPlan,
        events: List<EventAlertRecord>,
        handled: Map<Long, Boolean> = emptyMap()
    ) = CalendarResolutionApplier.computeEdits(
        plan = plan,
        eventsByKey = events.associateBy { it.eventId to it.instanceStartTime },
        isCalendarHandled = { handled[it] ?: true },
        isCalendarHandledExplicitlySet = { it in handled }
    )

    // --- Moving events onto the right calendar ---

    @Test
    fun rewritesTheCalendarIdAndNothingElse() {
        val original = event(100L, calendarId = 6L)

        val result = edits(planMoving(Triple(100L, 6L, 31L)), listOf(original))

        val updated = result.updatedEvents.single()
        assertEquals("only the calendar moves", 31L, updated.calendarId)
        assertEquals(original.eventId, updated.eventId)
        assertEquals(original.instanceStartTime, updated.instanceStartTime)
        assertEquals(original.title, updated.title)
        assertEquals(original.alertTime, updated.alertTime)
        assertEquals(original.snoozedUntil, updated.snoozedUntil)
    }

    @Test
    fun movesEveryEventInThePlan() {
        val events = (1L..5L).map { event(it, calendarId = 6L) }

        val result = edits(planMoving(*(1L..5L).map { Triple(it, 6L, 31L) }.toTypedArray()), events)

        assertEquals(5, result.updatedEvents.size)
        assertTrue(result.updatedEvents.all { it.calendarId == 31L })
    }

    @Test
    fun anEmptyPlanProducesNoEdits() {
        val result = edits(planMoving(), listOf(event(100L, 6L)))

        assertTrue(result.isEmpty)
    }

    // --- Guards against writing the wrong thing ---

    @Test
    fun skipsAnEventThatIsAlreadyOnTheTargetCalendar() {
        // The plan is a snapshot; something may have moved since. Rewriting a
        // row that is already correct is a pointless write on every rescan.
        val result = edits(planMoving(Triple(100L, 6L, 31L)), listOf(event(100L, calendarId = 31L)))

        assertTrue("nothing to write", result.updatedEvents.isEmpty())
    }

    @Test
    fun skipsAnEventThatNoLongerExists() {
        // Dismissed or deleted between planning and applying. Must not
        // resurrect it, and must not throw.
        val result = edits(planMoving(Triple(100L, 6L, 31L)), events = emptyList())

        assertTrue(result.updatedEvents.isEmpty())
    }

    @Test
    fun movesTheEventsItCanWhenOthersHaveGone() {
        val result = edits(
            planMoving(Triple(1L, 6L, 31L), Triple(2L, 6L, 31L), Triple(3L, 6L, 31L)),
            listOf(event(1L, 6L), event(3L, 6L))     // 2 is gone
        )

        assertEquals(2, result.updatedEvents.size)
        assertEquals(setOf(1L, 3L), result.updatedEvents.map { it.eventId }.toSet())
    }

    // --- Per-calendar handled settings ---

    @Test
    fun movesAnExplicitlyDisabledCalendarSetting() {
        // The setting users actually notice: a calendar they turned off. If it
        // does not move, the restored device starts notifying from it again.
        val result = edits(
            planMoving(Triple(100L, 6L, 31L)),
            listOf(event(100L, 6L)),
            handled = mapOf(6L to false)
        )

        assertEquals(mapOf(31L to false), result.handledSettingsToMove)
        assertEquals(setOf(6L), result.handledSettingsToClear)
    }

    @Test
    fun movesAnExplicitlyEnabledCalendarSettingToo() {
        // Explicitly-true is still a user decision, and the old key has to go
        // regardless or it lingers pointing at a calendar that no longer exists.
        val result = edits(
            planMoving(Triple(100L, 6L, 31L)),
            listOf(event(100L, 6L)),
            handled = mapOf(6L to true)
        )

        assertEquals(mapOf(31L to true), result.handledSettingsToMove)
    }

    @Test
    fun leavesNeverConfiguredCalendarsAlone() {
        // getCalendarIsHandled defaults to true, so an unconfigured calendar
        // reads as handled. Writing that through would create a setting the
        // user never made -- and make "has this been configured?" answer yes
        // from then on.
        val result = edits(
            planMoving(Triple(100L, 6L, 31L)),
            listOf(event(100L, 6L)),
            handled = emptyMap()
        )

        assertTrue("no setting to move", result.handledSettingsToMove.isEmpty())
        assertTrue("and nothing to clear", result.handledSettingsToClear.isEmpty())
    }

    @Test
    fun collapsesToOneSettingPerCalendarNotPerEvent() {
        // The real shape: hundreds of events over a couple of calendars.
        val events = (1L..200L).map { event(it, calendarId = 6L) }

        val result = edits(
            planMoving(*(1L..200L).map { Triple(it, 6L, 31L) }.toTypedArray()),
            events,
            handled = mapOf(6L to false)
        )

        assertEquals(200, result.updatedEvents.size)
        assertEquals("200 events, one setting", 1, result.handledSettingsToMove.size)
    }

    @Test
    fun handlesSeveralCalendarsMovingAtOnce() {
        val result = edits(
            planMoving(Triple(1L, 6L, 31L), Triple(2L, 16L, 32L)),
            listOf(event(1L, 6L), event(2L, 16L)),
            handled = mapOf(6L to false, 16L to true)
        )

        assertEquals(mapOf(31L to false, 32L to true), result.handledSettingsToMove)
        assertEquals(setOf(6L, 16L), result.handledSettingsToClear)
    }

    @Test
    fun movesSettingsForOnlyTheCalendarsThatWereConfigured() {
        val result = edits(
            planMoving(Triple(1L, 6L, 31L), Triple(2L, 16L, 32L)),
            listOf(event(1L, 6L), event(2L, 16L)),
            handled = mapOf(6L to false)        // 16 never configured
        )

        assertEquals(mapOf(31L to false), result.handledSettingsToMove)
        assertEquals(setOf(6L), result.handledSettingsToClear)
    }

    @Test
    fun summaryNamesBothKindsOfEdit() {
        val result = edits(
            planMoving(Triple(100L, 6L, 31L)),
            listOf(event(100L, 6L)),
            handled = mapOf(6L to false)
        )

        val summary = result.summary()
        assertTrue(summary, summary.contains("1 event(s) re-linked"))
        assertTrue(summary, summary.contains("1 calendar setting(s) moved"))
    }

    companion object {
        private const val INSTANCE_START = 1_700_000_000_000L
    }
}
