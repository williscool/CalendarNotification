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
import com.github.quarck.calnotify.calendar.CalendarEventDetails
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests what a capture pass decides to write, before it writes anything.
 *
 * Plain JUnit: the decision is a pure function of the stored sync ids, what the
 * provider returns, and the calendar tuples, so none of Robolectric, a Context
 * or a database is involved. The surrounding I/O is covered by
 * [EventIdentityCaptureRobolectricTest].
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
class IdentityCapturePlanTest {

    private fun event(eventId: Long, calendarId: Long = CALENDAR_ID) = EventAlertRecord(
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

    private fun providerEvent(
        eventId: Long,
        syncId: String? = "sync-$eventId",
        uid: String? = null,
        calendarId: Long = CALENDAR_ID
    ) = EventRecord(
        calendarId = calendarId,
        eventId = eventId,
        details = CalendarEventDetails(
            title = "Event $eventId",
            desc = "",
            location = "",
            timezone = "UTC",
            startTime = INSTANCE_START,
            endTime = INSTANCE_START + 3_600_000L,
            isAllDay = false,
            reminders = listOf(),
            repeatingRule = "",
            repeatingRDate = "",
            repeatingExRule = "",
            repeatingExRDate = "",
            color = 0
        ),
        syncId = syncId,
        uid2445 = uid
    )

    private fun backupInfo(calendarId: Long = CALENDAR_ID) = CalendarBackupInfo(
        calendarId = calendarId,
        accountName = "user@example.com",
        accountType = "com.google",
        ownerAccount = "user@example.com",
        displayName = "Calendar $calendarId",
        name = "Calendar $calendarId"
    )

    /** All inputs healthy unless a test overrides one. */
    private fun plan(
        events: List<EventAlertRecord>,
        stored: Map<Long, String?> = emptyMap(),
        provider: (Long) -> EventRecord? = { providerEvent(it) },
        calendars: (Long) -> CalendarBackupInfo? = { backupInfo(it) }
    ) = IdentityCapturePlan.compute(
        events = events,
        storedSyncIdOf = { stored[it.eventId] },
        providerEventOf = { provider(it.eventId) },
        backupInfoOf = calendars,
        capturedAtTime = CAPTURED_AT
    )

    // --- The ordinary case ---

    @Test
    fun capturesEveryHealthyEvent() {
        val result = plan((1L..5L).map { event(it) })

        assertEquals(5, result.toCapture.size)
        assertEquals(0, result.stale.size)
        assertEquals(0, result.gone.size)
    }

    @Test
    fun capturedRowCarriesBothHalvesOfTheIdentity() {
        val row = plan(listOf(event(100L))).toCapture.single()

        assertEquals("sync-100", row.eventSyncId)
        assertEquals("user@example.com", row.calendarAccountName)
        assertEquals("com.google", row.calendarAccountType)
        assertEquals(CALENDAR_ID, row.originalCalendarId)
        assertEquals(100L, row.originalEventId)
        assertEquals(CAPTURED_AT, row.capturedAtTime)
    }

    @Test
    fun refreshesAnEventWhoseStoredSyncIdStillMatches() {
        // A healthy device re-running the pass: the tuple is refreshed in case
        // the calendar was renamed.
        val result = plan(listOf(event(100L)), stored = mapOf(100L to "sync-100"))

        assertEquals(1, result.toCapture.size)
        assertEquals(0, result.stale.size)
    }

    @Test
    fun emptyInputYieldsAnEmptyPlan() {
        val result = plan(emptyList())

        assertTrue(result.isEmpty)
        assertEquals(0, result.stale.size)
        assertEquals(0, result.gone.size)
    }

    // --- Stale: the restored-device case ---

    @Test
    fun doesNotCaptureAnEventWhoseStoredSyncIdNoLongerMatches() {
        // The stored id now resolves to a different event. Capturing from it
        // would record a plausible-looking pointer to the wrong event.
        val result = plan(listOf(event(100L)), stored = mapOf(100L to "sync-from-old-phone"))

        assertTrue("nothing written for a stale row", result.isEmpty)
        assertEquals(1, result.stale.size)
        assertEquals(100L, result.stale.single().eventId)
    }

    @Test
    fun anEventGoneFromTheProviderIsStaleWhenSomethingWasStored() {
        // The other branch to STALE: the id resolves to nothing at all.
        val result = plan(
            listOf(event(100L)),
            stored = mapOf(100L to "sync-from-old-phone"),
            provider = { null }
        )

        assertEquals(1, result.stale.size)
        assertEquals(0, result.gone.size)
    }

    @Test
    fun everyEventStaleOnARestoredDeviceCapturesNothing() {
        val result = plan(
            (1L..10L).map { event(it) },
            stored = (1L..10L).associateWith { "old-$it" }
        )

        assertTrue(result.isEmpty)
        assertEquals(10, result.stale.size)
    }

    // --- Gone: aged-out history ---

    @Test
    fun anUnknownEventWithNoCalendarIsGoneNotStale() {
        // Nothing stored, provider knows nothing, calendar gone: there is no
        // evidence of staleness, just nothing to record.
        val result = plan(
            listOf(event(100L)),
            provider = { null },
            calendars = { null }
        )

        assertTrue(result.isEmpty)
        assertEquals(0, result.stale.size)
        assertEquals(1, result.gone.size)
    }

    @Test
    fun theCalendarHalfIsStillWorthCapturingWithoutASyncId() {
        // Provider knows nothing about the event, but the calendar resolves.
        // Attribution can still be fixed even if the event cannot be re-found.
        val result = plan(listOf(event(100L)), provider = { null })

        val row = result.toCapture.single()
        assertNull(row.eventSyncId)
        assertEquals("user@example.com", row.calendarAccountName)
        assertEquals(0, result.gone.size)
    }

    @Test
    fun uid2445AloneIsEnoughToCapture() {
        // Null on Google Calendar, but other providers populate it.
        val result = plan(
            listOf(event(100L)),
            provider = { providerEvent(it, syncId = null, uid = "uid-xyz") },
            calendars = { null }
        )

        val row = result.toCapture.single()
        assertNull(row.eventSyncId)
        assertEquals("uid-xyz", row.eventUid)
    }

    // --- One unidentifiable event must not affect its neighbours ---

    @Test
    fun oneGoneEventDoesNotSuppressTheOthers() {
        // The failure that killed the earlier validation-sample design: one
        // event with nothing to record was read as proof the device was wrong.
        val result = plan(
            (1L..5L).map { event(it) },
            provider = { if (it == 3L) null else providerEvent(it) },
            calendars = { if (it == UNKNOWN_CALENDAR) null else backupInfo(it) }
        )

        assertEquals("the other four are still captured", 5, result.toCapture.size)
    }

    @Test
    fun oneStaleEventDoesNotSuppressTheOthers() {
        val result = plan(
            (1L..5L).map { event(it) },
            stored = mapOf(3L to "old-3")
        )

        assertEquals(4, result.toCapture.size)
        assertEquals(1, result.stale.size)
    }

    // --- Calendar lookups are per calendar, not per event ---

    @Test
    fun looksUpEachCalendarOnceNoMatterHowManyEventsUseIt() {
        var calendarLookups = 0

        val result = plan(
            (1L..50L).map { event(it, calendarId = CALENDAR_ID) } +
                (51L..100L).map { event(it, calendarId = OTHER_CALENDAR) },
            calendars = { calendarLookups++; backupInfo(it) }
        )

        assertEquals(100, result.toCapture.size)
        assertEquals("two calendars, two lookups -- not 100", 2, calendarLookups)
    }

    @Test
    fun aNullCalendarLookupIsStillCachedRatherThanRetriedPerEvent() {
        // A missing calendar must not cost one failed lookup per event.
        var calendarLookups = 0

        plan(
            (1L..20L).map { event(it) },
            calendars = { calendarLookups++; null }
        )

        assertEquals(1, calendarLookups)
    }

    // --- A mixed batch, which is what a real pass looks like ---

    @Test
    fun aMixedBatchSortsEveryEventIntoExactlyOneBucket() {
        val events = (1L..6L).map { event(it) }

        val result = plan(
            events,
            stored = mapOf(2L to "old-2"),                        // -> stale
            provider = { if (it == 4L) null else providerEvent(it) },
            calendars = { if (it == UNKNOWN_CALENDAR) null else backupInfo(it) }
        )

        assertEquals(
            "every event accounted for exactly once",
            events.size,
            result.toCapture.size + result.stale.size + result.gone.size
        )
        assertEquals(1, result.stale.size)
    }

    @Test
    fun summaryNamesEveryBucket() {
        val result = plan(listOf(event(1L), event(2L)), stored = mapOf(2L to "old-2"))

        val summary = result.summary(total = 2)
        assertTrue(summary, summary.contains("1 of 2 event(s) captured"))
        assertTrue(summary, summary.contains("1 stale"))
    }

    companion object {
        private const val CALENDAR_ID = 6L
        private const val OTHER_CALENDAR = 16L
        private const val UNKNOWN_CALENDAR = -1L
        private const val INSTANCE_START = 1_700_000_000_000L
        private const val CAPTURED_AT = 1_700_000_500_000L
    }
}
