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

import android.database.SQLException
import com.github.quarck.calnotify.calendar.CalendarBackupInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests [EventIdentityStorage]'s behaviour against a fake DAO.
 *
 * Real SQLite cannot run under Robolectric here -- cr-sqlite is a native
 * library (see docs/dev_completed/sqlite-mocking-robolectric.md) -- so the DAO
 * is faked and the actual queries are covered by the instrumentation test
 * `EventIdentityStorageTest`. What is worth testing at this level is the
 * facade's own contract: that capture failures are swallowed rather than
 * propagated, and that reads degrade to empty instead of throwing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class EventIdentityStorageRobolectricTest {

    /** In-memory stand-in for the Room DAO, with an injectable failure mode. */
    private class FakeEventIdentityDao : EventIdentityDao {
        val rows = mutableMapOf<Pair<Long, Long>, EventIdentityEntity>()
        var failWith: SQLException? = null

        private fun checkFailure() {
            failWith?.let { throw it }
        }

        override fun getAll(): List<EventIdentityEntity> {
            checkFailure()
            return rows.values.toList()
        }

        override fun count(): Int {
            checkFailure()
            return rows.size
        }

        override fun getByKey(eventId: Long, instanceStartTime: Long): EventIdentityEntity? {
            checkFailure()
            return rows[eventId to instanceStartTime]
        }

        override fun getByEventId(eventId: Long): List<EventIdentityEntity> {
            checkFailure()
            return rows.values.filter { it.eventId == eventId }
        }

        override fun getUnresolved(maxAttempts: Int): List<EventIdentityEntity> {
            checkFailure()
            return rows.values.filter {
                it.eventId == it.originalEventId && it.resolutionAttemptCount < maxAttempts
            }
        }

        override fun put(entity: EventIdentityEntity) {
            checkFailure()
            rows[entity.eventId to entity.instanceStartTime] = entity
        }

        override fun putAll(entities: List<EventIdentityEntity>) {
            checkFailure()
            entities.forEach { rows[it.eventId to it.instanceStartTime] = it }
        }

        override fun deleteByKey(eventId: Long, instanceStartTime: Long): Int {
            checkFailure()
            return if (rows.remove(eventId to instanceStartTime) != null) 1 else 0
        }

        override fun deleteAllRows() {
            checkFailure()
            rows.clear()
        }

        override fun reKey(oldEventId: Long, instanceStartTime: Long, newEventId: Long): Int {
            checkFailure()
            val existing = rows.remove(oldEventId to instanceStartTime) ?: return 0
            rows[newEventId to instanceStartTime] = existing.copy(eventId = newEventId)
            return 1
        }

        override fun recordResolutionAttempt(
            eventId: Long,
            instanceStartTime: Long,
            attemptTime: Long
        ): Int {
            checkFailure()
            val existing = rows[eventId to instanceStartTime] ?: return 0
            rows[eventId to instanceStartTime] = existing.copy(
                resolutionAttemptCount = existing.resolutionAttemptCount + 1,
                lastResolutionAttemptTime = attemptTime
            )
            return 1
        }
    }

    private lateinit var dao: FakeEventIdentityDao
    private lateinit var storage: EventIdentityStorage

    private val backupInfo = CalendarBackupInfo(
        calendarId = 6L,
        accountName = "user@example.com",
        accountType = "com.google",
        ownerAccount = "user@example.com",
        displayName = "Work",
        name = "user@example.com"
    )

    @Before
    fun setup() {
        dao = FakeEventIdentityDao()
        storage = EventIdentityStorage(dao)
    }

    private fun identity(
        eventId: Long = 100L,
        instanceStartTime: Long = 1_700_000_000_000L,
        syncId: String? = "sync-abc",
        uid: String? = null
    ) = EventIdentityEntity.create(
        eventId = eventId,
        instanceStartTime = instanceStartTime,
        calendarId = 6L,
        backupInfo = backupInfo,
        eventSyncId = syncId,
        eventUid = uid,
        capturedAtTime = 1_700_000_000_000L
    )

    @Test
    fun putThenGetRoundTrips() {
        assertTrue(storage.put(identity()))

        val loaded = storage.get(100L, 1_700_000_000_000L)

        assertEquals("sync-abc", loaded?.eventSyncId)
        assertEquals("user@example.com", loaded?.calendarAccountName)
        assertEquals(6L, loaded?.originalCalendarId)
        assertEquals(100L, loaded?.originalEventId)
    }

    @Test
    fun getMissingRowReturnsNull() {
        assertNull(storage.get(999L, 1L))
    }

    @Test
    fun putOverwritesExistingRowForSameKey() {
        storage.put(identity(syncId = "first"))
        storage.put(identity(syncId = "second"))

        assertEquals(1, storage.count())
        assertEquals("second", storage.get(100L, 1_700_000_000_000L)?.eventSyncId)
    }

    @Test
    fun putAllOfEmptyListIsANoOp() {
        assertTrue(storage.putAll(emptyList()))
        assertEquals(0, storage.count())
    }

    @Test
    fun putAllWritesEveryRow() {
        // Distinct from the empty-list case above, which short-circuits before
        // reaching the DAO at all.
        storage.putAll((1L..3L).map { identity(eventId = it) })

        assertEquals(3, storage.count())
        assertEquals("sync-abc", storage.get(2L, 1_700_000_000_000L)?.eventSyncId)
    }

    @Test
    fun putAllReportsFailure() {
        dao.failWith = SQLException("disk full")

        assertFalse(storage.putAll(listOf(identity())))
    }

    @Test
    fun getAllReturnsEveryStoredRow() {
        storage.putAll((1L..3L).map { identity(eventId = it) })

        assertEquals(3, storage.getAll().size)
    }

    // --- The point of the facade: capture must never break the event write ---

    @Test
    fun writeFailureIsSwallowedAndReported() {
        dao.failWith = SQLException("disk full")

        assertFalse("write should report failure, not throw", storage.put(identity()))
    }

    @Test
    fun readFailureDegradesToEmptyRatherThanThrowing() {
        dao.failWith = SQLException("corrupt")

        assertNull(storage.get(100L, 1L))
        assertEquals(emptyList<EventIdentityEntity>(), storage.getAll())
        assertEquals(emptyList<EventIdentityEntity>(), storage.getUnresolved())
        assertEquals(0, storage.count())
    }

    // --- Re-key: identity has to follow the event onto its new id ---

    @Test
    fun reKeyMovesRowToNewEventId() {
        storage.put(identity(eventId = 100L))

        assertTrue(storage.reKey(100L, 1_700_000_000_000L, 555L))

        assertNull("old key should be gone", storage.get(100L, 1_700_000_000_000L))
        val moved = storage.get(555L, 1_700_000_000_000L)
        assertEquals(555L, moved?.eventId)
        assertEquals(
            "originalEventId must NOT move - it is the staleness marker",
            100L, moved?.originalEventId
        )
    }

    @Test
    fun reKeyToSameIdIsANoOpAndSucceeds() {
        storage.put(identity(eventId = 100L))

        assertTrue(storage.reKey(100L, 1_700_000_000_000L, 100L))
        assertEquals(100L, storage.get(100L, 1_700_000_000_000L)?.eventId)
    }

    @Test
    fun reKeyOfMissingRowReportsFailure() {
        assertFalse(storage.reKey(1L, 2L, 3L))
    }

    // --- Unresolved set drives the retry pass ---

    @Test
    fun freshlyCapturedRowCountsAsUnresolved() {
        storage.put(identity())

        assertEquals(1, storage.getUnresolved().size)
    }

    @Test
    fun reKeyedRowDropsOutOfUnresolved() {
        storage.put(identity(eventId = 100L))
        storage.reKey(100L, 1_700_000_000_000L, 555L)

        assertEquals(
            "eventId now differs from originalEventId, so it is resolved",
            0, storage.getUnresolved().size
        )
    }

    @Test
    fun rowAtAttemptCapDropsOutOfUnresolved() {
        storage.put(identity())
        repeat(EventIdentityStorage.DEFAULT_MAX_RESOLUTION_ATTEMPTS) {
            storage.recordResolutionAttempt(100L, 1_700_000_000_000L, 1L)
        }

        assertEquals(
            "capped rows must stop being retried",
            0, storage.getUnresolved().size
        )
    }

    @Test
    fun recordResolutionAttemptIncrementsCountAndTime() {
        storage.put(identity())

        storage.recordResolutionAttempt(100L, 1_700_000_000_000L, 42L)

        val row = storage.get(100L, 1_700_000_000_000L)
        assertEquals(1, row?.resolutionAttemptCount)
        assertEquals(42L, row?.lastResolutionAttemptTime)
    }

    @Test
    fun deleteRemovesRow() {
        storage.put(identity())

        assertTrue(storage.delete(100L, 1_700_000_000_000L))
        assertNull(storage.get(100L, 1_700_000_000_000L))
    }

    // --- Entity helpers ---

    @Test
    fun hasUsableIdentifierReflectsWhatWasCaptured() {
        assertTrue(identity(syncId = "s", uid = null).hasUsableIdentifier())
        assertTrue(identity(syncId = null, uid = "u").hasUsableIdentifier())

        assertFalse(
            "never-synced local events have neither, and cannot be resolved",
            identity(syncId = null, uid = null).hasUsableIdentifier()
        )
        assertFalse(identity(syncId = "", uid = "").hasUsableIdentifier())
    }

    @Test
    fun toCalendarBackupInfoFeedsTheExistingMatcher() {
        val info = identity().toCalendarBackupInfo()

        assertEquals("user@example.com", info.accountName)
        assertEquals("com.google", info.accountType)
        assertEquals("user@example.com", info.ownerAccount)
        assertEquals("Work", info.displayName)
    }
}
