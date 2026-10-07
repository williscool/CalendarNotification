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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the four-database re-key applier with an in-memory `Ops` fake.
 *
 * Every scenario asserts on the observable state after `applyOne` returns:
 * which events exist under which keys, and whether the identity / dismissed /
 * monitor sides moved with them. The applier is pure orchestration and its
 * Ops interface is a narrow surface, so the fake is short.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
class EventIdRekeyApplierTest {

    /**
     * In-memory implementation of every op the applier needs, with knobs to
     * fail any individual step.
     */
    private class FakeOps : EventIdRekeyApplier.Ops {
        val events = HashMap<Pair<Long, Long>, EventAlertRecord>()
        val identityKeys = HashSet<Pair<Long, Long>>()
        val dismissedByEventId = HashMap<Long, Int>()      // count per event id
        val monitorByEventId = HashMap<Long, Int>()

        /** If set, the named step fails once and then returns to normal. */
        var failOn: FailPoint? = null
        var throwOn: FailPoint? = null

        enum class FailPoint {
            DELETE_EVENT, INSERT_EVENT, IDENTITY, DISMISSED, MONITOR
        }

        private fun trip(point: FailPoint): Boolean {
            if (throwOn == point) {
                throwOn = null
                throw RuntimeException("simulated throw at $point")
            }
            if (failOn == point) {
                failOn = null
                return true
            }
            return false
        }

        override fun readEvent(currentId: Long, instanceStartTime: Long) =
            events[currentId to instanceStartTime]

        override fun deleteEvent(currentId: Long, instanceStartTime: Long): Boolean {
            if (trip(FailPoint.DELETE_EVENT)) return false
            return events.remove(currentId to instanceStartTime) != null
        }

        override fun insertEvent(event: EventAlertRecord): Boolean {
            if (trip(FailPoint.INSERT_EVENT)) return false
            events[event.eventId to event.instanceStartTime] = event
            return true
        }

        override fun reKeyIdentity(oldId: Long, instanceStartTime: Long, newId: Long): Boolean {
            if (trip(FailPoint.IDENTITY)) return false
            if (!identityKeys.remove(oldId to instanceStartTime)) return false
            identityKeys.add(newId to instanceStartTime)
            return true
        }

        override fun reKeyDismissed(oldId: Long, newId: Long): Boolean {
            if (trip(FailPoint.DISMISSED)) return false
            dismissedByEventId.remove(oldId)?.let { dismissedByEventId[newId] = it }
            return true
        }

        override fun reKeyMonitorAlerts(oldId: Long, newId: Long, instanceStartTime: Long): Boolean {
            if (trip(FailPoint.MONITOR)) return false
            monitorByEventId.remove(oldId)?.let { monitorByEventId[newId] = it }
            return true
        }
    }

    private fun eventRecord(id: Long) = EventAlertRecord(
        calendarId = CALENDAR_ID,
        eventId = id,
        isAllDay = false,
        isRepeating = false,
        alertTime = INSTANCE_START - 600_000L,
        notificationId = 0,
        title = "Event $id",
        desc = "",
        startTime = INSTANCE_START,
        endTime = INSTANCE_START + 3_600_000L,
        instanceStartTime = INSTANCE_START,
        instanceEndTime = INSTANCE_START + 3_600_000L,
        location = "",
        lastStatusChangeTime = 0L
    )

    private fun change(currentId: Long, newId: Long, syncId: String = "sync-$currentId") =
        EventIdRekeyPlan.EventIdChange(
            identity = EventIdentityEntity.create(
                eventId = currentId,
                instanceStartTime = INSTANCE_START,
                calendarId = CALENDAR_ID,
                backupInfo = CalendarBackupInfo(
                    calendarId = CALENDAR_ID,
                    accountName = "user@example.com",
                    accountType = "com.google",
                    ownerAccount = "user@example.com",
                    displayName = "Work",
                    name = "user@example.com"
                ),
                eventSyncId = syncId,
                eventUid = null,
                capturedAtTime = 1L
            ),
            currentEventId = currentId,
            newEventId = newId,
            syncId = syncId
        )

    /**
     * Seed the fake with the state a live device should have before a re-key:
     * an event row at `oldId`, plus identity / dismissed / monitor entries
     * keyed the same way.
     */
    private fun seedFull(ops: FakeOps, oldId: Long) {
        ops.events[oldId to INSTANCE_START] = eventRecord(oldId)
        ops.identityKeys.add(oldId to INSTANCE_START)
        ops.dismissedByEventId[oldId] = 2         // pretend history
        ops.monitorByEventId[oldId] = 1
    }

    // --- Happy path ---

    @Test
    fun rekeysAllFourDatabasesTogether() {
        val ops = FakeOps()
        seedFull(ops, oldId = 100L)

        val result = EventIdRekeyApplier.applyOne(change(100L, 500L), ops)

        assertEquals(EventIdRekeyApplier.Result.Success, result)
        assertNull("old event key is gone", ops.events[100L to INSTANCE_START])
        assertEquals(500L, ops.events[500L to INSTANCE_START]!!.eventId)
        assertTrue(500L to INSTANCE_START in ops.identityKeys)
        assertEquals(2, ops.dismissedByEventId[500L])
        assertEquals(1, ops.monitorByEventId[500L])
        assertNull(ops.dismissedByEventId[100L])
        assertNull(ops.monitorByEventId[100L])
    }

    @Test
    fun preservesEverythingElseOnTheEventRow() {
        // Only eventId changes; title, alert time, calendar id, everything
        // else on the row must survive verbatim.
        val ops = FakeOps()
        seedFull(ops, oldId = 100L)
        val original = ops.events[100L to INSTANCE_START]!!

        EventIdRekeyApplier.applyOne(change(100L, 500L), ops)

        val moved = ops.events[500L to INSTANCE_START]!!
        assertEquals(original.title, moved.title)
        assertEquals(original.alertTime, moved.alertTime)
        assertEquals(original.calendarId, moved.calendarId)
        assertEquals(original.instanceStartTime, moved.instanceStartTime)
    }

    // --- Plan is stale between planning and applying ---

    @Test
    fun reportsWhenTheEventRowIsAlreadyGone() {
        // The event was dismissed or deleted between the plan being computed
        // and applyOne being called. Nothing to touch, no rollback needed.
        val ops = FakeOps()
        // no seedFull

        val result = EventIdRekeyApplier.applyOne(change(100L, 500L), ops)

        assertEquals(EventIdRekeyApplier.Result.EventRowMissing, result)
        assertTrue(ops.events.isEmpty())
    }

    // --- Failures at every step must roll the world back ---

    @Test
    fun eventsDeleteFailureLeavesEverythingUntouched() {
        val ops = FakeOps().apply { failOn = FakeOps.FailPoint.DELETE_EVENT }
        seedFull(ops, oldId = 100L)

        val result = EventIdRekeyApplier.applyOne(change(100L, 500L), ops)

        assertTrue(result is EventIdRekeyApplier.Result.EventsWriteFailed)
        // Nothing moved anywhere -- the delete failed before any other write.
        assertEquals(100L, ops.events[100L to INSTANCE_START]!!.eventId)
        assertTrue(100L to INSTANCE_START in ops.identityKeys)
        assertEquals(2, ops.dismissedByEventId[100L])
        assertEquals(1, ops.monitorByEventId[100L])
    }

    @Test
    fun eventsInsertFailureRestoresTheOldEventRow() {
        val ops = FakeOps().apply { failOn = FakeOps.FailPoint.INSERT_EVENT }
        seedFull(ops, oldId = 100L)

        val result = EventIdRekeyApplier.applyOne(change(100L, 500L), ops)

        assertTrue(result is EventIdRekeyApplier.Result.EventsWriteFailed)
        // Old row restored, new key never existed.
        assertEquals(100L, ops.events[100L to INSTANCE_START]!!.eventId)
        assertNull(ops.events[500L to INSTANCE_START])
    }

    @Test
    fun identityFailureRollsBackTheEventRow() {
        val ops = FakeOps().apply { failOn = FakeOps.FailPoint.IDENTITY }
        seedFull(ops, oldId = 100L)

        val result = EventIdRekeyApplier.applyOne(change(100L, 500L), ops)

        assertTrue(result is EventIdRekeyApplier.Result.LaterWriteFailed)
        val fail = result as EventIdRekeyApplier.Result.LaterWriteFailed
        assertEquals(EventIdRekeyApplier.Result.Step.IDENTITY, fail.failedAt)
        assertTrue("rollback should have restored eventsV9", fail.rolledBackCleanly)

        assertEquals("event row back where it started",
            100L, ops.events[100L to INSTANCE_START]!!.eventId)
        assertNull(ops.events[500L to INSTANCE_START])
        assertTrue("identity still on old id", 100L to INSTANCE_START in ops.identityKeys)
    }

    @Test
    fun dismissedFailureRollsBackIdentityAndEventRow() {
        val ops = FakeOps().apply { failOn = FakeOps.FailPoint.DISMISSED }
        seedFull(ops, oldId = 100L)

        val result = EventIdRekeyApplier.applyOne(change(100L, 500L), ops)

        val fail = result as EventIdRekeyApplier.Result.LaterWriteFailed
        assertEquals(EventIdRekeyApplier.Result.Step.DISMISSED, fail.failedAt)
        assertTrue(fail.rolledBackCleanly)
        assertEquals(100L, ops.events[100L to INSTANCE_START]!!.eventId)
        assertTrue(100L to INSTANCE_START in ops.identityKeys)
        assertEquals(2, ops.dismissedByEventId[100L])
    }

    @Test
    fun monitorFailureRollsBackDismissedIdentityAndEventRow() {
        val ops = FakeOps().apply { failOn = FakeOps.FailPoint.MONITOR }
        seedFull(ops, oldId = 100L)

        val result = EventIdRekeyApplier.applyOne(change(100L, 500L), ops)

        val fail = result as EventIdRekeyApplier.Result.LaterWriteFailed
        assertEquals(EventIdRekeyApplier.Result.Step.MONITOR, fail.failedAt)
        assertTrue(fail.rolledBackCleanly)
        assertEquals(100L, ops.events[100L to INSTANCE_START]!!.eventId)
        assertEquals(2, ops.dismissedByEventId[100L])
        assertEquals(1, ops.monitorByEventId[100L])
    }

    // --- Exceptions must not escape either ---

    @Test
    fun exceptionsInEventsAreConvertedToEventsWriteFailed() {
        val ops = FakeOps().apply { throwOn = FakeOps.FailPoint.INSERT_EVENT }
        seedFull(ops, oldId = 100L)

        val result = EventIdRekeyApplier.applyOne(change(100L, 500L), ops)

        assertTrue(result is EventIdRekeyApplier.Result.EventsWriteFailed)
        assertEquals(100L, ops.events[100L to INSTANCE_START]!!.eventId)
    }

    @Test
    fun exceptionsInIdentityAreConvertedAndRollBack() {
        val ops = FakeOps().apply { throwOn = FakeOps.FailPoint.IDENTITY }
        seedFull(ops, oldId = 100L)

        val result = EventIdRekeyApplier.applyOne(change(100L, 500L), ops)

        val fail = result as EventIdRekeyApplier.Result.LaterWriteFailed
        assertEquals(EventIdRekeyApplier.Result.Step.IDENTITY, fail.failedAt)
        assertTrue(fail.rolledBackCleanly)
    }

    companion object {
        private const val CALENDAR_ID = 6L
        private const val INSTANCE_START = 1_700_000_000_000L
    }
}
