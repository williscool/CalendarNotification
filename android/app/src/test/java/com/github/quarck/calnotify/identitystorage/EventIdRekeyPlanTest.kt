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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the pure planner that decides which stored events need their `id`
 * re-keyed. Plain JUnit -- inputs are plain data plus two lookup lambdas.
 *
 * The apply step is covered by [EventIdRekeyApplierTest].
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
class EventIdRekeyPlanTest {

    private fun identity(
        eventId: Long = 100L,
        calendarId: Long = CALENDAR_ID,
        syncId: String? = "sync-$eventId",
        instanceStartTime: Long = INSTANCE_START
    ) = EventIdentityEntity.create(
        eventId = eventId,
        instanceStartTime = instanceStartTime,
        calendarId = calendarId,
        backupInfo = CalendarBackupInfo(
            calendarId = calendarId,
            accountName = "user@example.com",
            accountType = "com.google",
            ownerAccount = "user@example.com",
            displayName = "Work",
            name = "user@example.com"
        ),
        eventSyncId = syncId,
        eventUid = null,
        capturedAtTime = 1L
    )

    /**
     * `liveEventIdOf` fake. Pass every identity `eventId` that has a live
     * `eventsV9` row -- the fake returns the same id back (i.e. re-key has
     * not run yet). Identities not in the list return null (row missing).
     */
    private fun liveIds(vararg eventIdsWithRows: Long): (EventIdentityEntity) -> Long? {
        val present = eventIdsWithRows.toSet()
        return { if (it.eventId in present) it.eventId else null }
    }

    /**
     * `providerEventIdOf` fake keyed by `(calendarId, syncId)`.
     * Missing entries return `-1L`, matching the real provider contract.
     */
    private fun providerIds(vararg triples: Triple<Long, String, Long>): (Long, String) -> Long {
        val map = triples.associate { (cal, sync, id) -> (cal to sync) to id }
        return { cal, sync -> map[cal to sync] ?: -1L }
    }

    // --- The normal restored-device case ---

    @Test
    fun schedulesARekeyWhenTheProviderKnowsANewIdForTheSyncId() {
        // Identity captured on old device at eventId=100. Live row still there
        // (Room preserved it during restore). Provider on the new device
        // resolves the sync id to id=500.
        val plan = EventIdRekeyPlan.compute(
            identities = listOf(identity(eventId = 100L)),
            liveEventIdOf = liveIds(100L),
            providerEventIdOf = providerIds(Triple(CALENDAR_ID, "sync-100", 500L))
        )

        val change = plan.changes.single()
        assertEquals(100L, change.currentEventId)
        assertEquals(500L, change.newEventId)
        assertEquals("sync-100", change.syncId)
    }

    @Test
    fun aHealthyDeviceIsAlreadyCurrent() {
        // Provider agrees with what the live row already says.
        val plan = EventIdRekeyPlan.compute(
            identities = listOf(identity(eventId = 100L)),
            liveEventIdOf = liveIds(100L),
            providerEventIdOf = providerIds(Triple(CALENDAR_ID, "sync-100", 100L))
        )

        assertTrue(plan.isEmpty)
        assertEquals(1, plan.alreadyCurrent.size)
    }

    // --- Deliberate no-ops ---

    @Test
    fun identityWithNoSyncIdIsUnresolved() {
        val plan = EventIdRekeyPlan.compute(
            identities = listOf(identity(syncId = null)),
            liveEventIdOf = liveIds(100L),
            providerEventIdOf = providerIds()
        )

        assertTrue(plan.isEmpty)
        assertEquals(1, plan.unresolved.size)
    }

    @Test
    fun identityWithBlankSyncIdIsUnresolved() {
        val plan = EventIdRekeyPlan.compute(
            identities = listOf(identity(syncId = "")),
            liveEventIdOf = liveIds(100L),
            providerEventIdOf = providerIds()
        )

        assertEquals(1, plan.unresolved.size)
    }

    @Test
    fun syncIdTheProviderDoesNotKnowIsUnresolved() {
        // The event was captured, but the provider has since dropped it.
        val plan = EventIdRekeyPlan.compute(
            identities = listOf(identity(eventId = 100L, syncId = "sync-100")),
            liveEventIdOf = liveIds(100L),
            providerEventIdOf = providerIds()   // nothing configured -> -1L
        )

        assertEquals(1, plan.unresolved.size)
    }

    @Test
    fun anIdentityWithNoLiveEventRowIsReportedNotChanged() {
        // Identity captured for an event that no longer exists in eventsV9.
        // Reported so we notice, not silently guessed at.
        val plan = EventIdRekeyPlan.compute(
            identities = listOf(identity(eventId = 100L)),
            liveEventIdOf = liveIds(),    // 100 is missing
            providerEventIdOf = providerIds(Triple(CALENDAR_ID, "sync-100", 500L))
        )

        assertTrue(plan.isEmpty)
        assertEquals(1, plan.eventRowMissing.size)
    }

    // --- A restored device with a real batch ---

    @Test
    fun aMixedBatchSortsEveryEventIntoExactlyOneBucket() {
        val identities = listOf(
            identity(eventId = 1L, syncId = "sync-1"),     // -> change
            identity(eventId = 2L, syncId = "sync-2"),     // -> current
            identity(eventId = 3L, syncId = "sync-3"),     // -> unresolved (not in provider)
            identity(eventId = 4L, syncId = null),         // -> unresolved (no sync id)
            identity(eventId = 5L, syncId = "sync-5")      // -> row missing
        )
        val plan = EventIdRekeyPlan.compute(
            identities = identities,
            liveEventIdOf = liveIds(1L, 2L, 3L, 4L),   // 5L absent
            providerEventIdOf = providerIds(
                Triple(CALENDAR_ID, "sync-1", 501L),
                Triple(CALENDAR_ID, "sync-2", 2L),
                Triple(CALENDAR_ID, "sync-5", 505L)
                // sync-3 absent
            )
        )

        assertEquals(1, plan.changes.size)
        assertEquals(1, plan.alreadyCurrent.size)
        assertEquals(2, plan.unresolved.size)
        assertEquals(1, plan.eventRowMissing.size)
        assertEquals(
            "every identity accounted for exactly once",
            identities.size,
            plan.changes.size + plan.alreadyCurrent.size +
                plan.unresolved.size + plan.eventRowMissing.size
        )
    }

    @Test
    fun summaryNamesEveryBucket() {
        val plan = EventIdRekeyPlan.compute(
            identities = listOf(identity(eventId = 1L, syncId = "sync-1")),
            liveEventIdOf = liveIds(1L),
            providerEventIdOf = providerIds(Triple(CALENDAR_ID, "sync-1", 500L))
        )

        val s = plan.summary()
        assertTrue(s, s.contains("1 to re-key"))
    }

    @Test
    fun emptyInputYieldsAnEmptyPlan() {
        val plan = EventIdRekeyPlan.compute(
            identities = emptyList(),
            liveEventIdOf = { null },
            providerEventIdOf = { _, _ -> -1L }
        )

        assertTrue(plan.isEmpty)
    }

    companion object {
        private const val CALENDAR_ID = 6L
        private const val INSTANCE_START = 1_700_000_000_000L
    }
}
