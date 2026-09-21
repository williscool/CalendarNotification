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

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.calendar.CalendarBackupInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [EventIdentityDao] against real SQLite.
 *
 * The Robolectric test fakes the DAO, because cr-sqlite is a native library
 * that cannot load under Robolectric (see
 * docs/dev_completed/sqlite-mocking-robolectric.md). That leaves the actual
 * Room queries unverified, which is what this covers: the schema builds, the
 * primary key behaves, and the hand-written UPDATE statements do what their
 * names claim.
 *
 * Uses a throwaway database name so it never touches the real one.
 */
@RunWith(AndroidJUnit4::class)
class EventIdentityStorageTest {

    private lateinit var context: Context
    private lateinit var database: EventIdentityDatabase
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
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE_NAME)

        database = EventIdentityDatabase.buildDatabase(context, TEST_DATABASE_NAME)
        storage = EventIdentityStorage(database.eventIdentityDao())
    }

    @After
    fun teardown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    private fun identity(
        eventId: Long = 100L,
        instanceStartTime: Long = INSTANCE_START,
        syncId: String? = "sync-abc",
        uid: String? = null
    ) = EventIdentityEntity.create(
        eventId = eventId,
        instanceStartTime = instanceStartTime,
        calendarId = 6L,
        backupInfo = backupInfo,
        eventSyncId = syncId,
        eventUid = uid,
        capturedAtTime = CAPTURED_AT
    )

    @Test
    fun schemaBuildsAndRoundTripsEveryColumn() {
        storage.put(identity(uid = "uid-xyz"))

        val loaded = storage.get(100L, INSTANCE_START)

        assertNotNull(loaded)
        assertEquals(100L, loaded!!.eventId)
        assertEquals(INSTANCE_START, loaded.instanceStartTime)
        assertEquals("user@example.com", loaded.calendarAccountName)
        assertEquals("com.google", loaded.calendarAccountType)
        assertEquals("user@example.com", loaded.calendarOwnerAccount)
        assertEquals("Work", loaded.calendarDisplayName)
        assertEquals("user@example.com", loaded.calendarName)
        assertEquals("sync-abc", loaded.eventSyncId)
        assertEquals("uid-xyz", loaded.eventUid)
        assertEquals(6L, loaded.originalCalendarId)
        assertEquals(100L, loaded.originalEventId)
        assertEquals(CAPTURED_AT, loaded.capturedAtTime)
        assertEquals(0, loaded.resolutionAttemptCount)
    }

    @Test
    fun nullIdentifiersPersistAsNull() {
        storage.put(identity(syncId = null, uid = null))

        val loaded = storage.get(100L, INSTANCE_START)

        assertNull(loaded?.eventSyncId)
        assertNull(loaded?.eventUid)
        assertFalse(
            "a never-synced event has nothing to resolve against",
            loaded!!.hasUsableIdentifier()
        )
    }

    @Test
    fun primaryKeyIsEventIdPlusInstanceStart() {
        // Same event, two occurrences: both rows must coexist.
        storage.put(identity(instanceStartTime = INSTANCE_START))
        storage.put(identity(instanceStartTime = INSTANCE_START + 86_400_000L))

        assertEquals(2, storage.count())
    }

    @Test
    fun putReplacesRatherThanFailingOnConflict() {
        storage.put(identity(syncId = "first"))
        storage.put(identity(syncId = "second"))

        assertEquals(1, storage.count())
        assertEquals("second", storage.get(100L, INSTANCE_START)?.eventSyncId)
    }

    @Test
    fun putAllInsertsEveryRow() {
        storage.putAll((1L..5L).map { identity(eventId = it) })

        assertEquals(5, storage.count())
    }

    // --- The hand-written UPDATE statements ---

    @Test
    fun reKeyMovesTheRowAndLeavesOriginalEventIdAlone() {
        storage.put(identity(eventId = 100L))

        assertTrue(storage.reKey(100L, INSTANCE_START, 555L))

        assertNull(storage.get(100L, INSTANCE_START))

        val moved = storage.get(555L, INSTANCE_START)
        assertNotNull("row should exist under the new id", moved)
        assertEquals(555L, moved!!.eventId)
        assertEquals(
            "originalEventId is the staleness marker and must not move",
            100L, moved.originalEventId
        )
        assertEquals("sync-abc", moved.eventSyncId)
    }

    @Test
    fun recordResolutionAttemptIncrements() {
        storage.put(identity())

        storage.recordResolutionAttempt(100L, INSTANCE_START, 999L)
        storage.recordResolutionAttempt(100L, INSTANCE_START, 1000L)

        val row = storage.get(100L, INSTANCE_START)
        assertEquals(2, row?.resolutionAttemptCount)
        assertEquals(1000L, row?.lastResolutionAttemptTime)
    }

    // --- getUnresolved drives the retry pass, so its WHERE clause matters ---

    @Test
    fun unresolvedIncludesFreshlyCapturedRows() {
        storage.putAll((1L..3L).map { identity(eventId = it) })

        assertEquals(3, storage.getUnresolved().size)
    }

    @Test
    fun unresolvedExcludesReKeyedRows() {
        storage.put(identity(eventId = 100L))
        storage.put(identity(eventId = 200L))
        storage.reKey(100L, INSTANCE_START, 555L)

        val unresolved = storage.getUnresolved()

        assertEquals(1, unresolved.size)
        assertEquals(200L, unresolved.first().eventId)
    }

    @Test
    fun unresolvedExcludesRowsAtTheAttemptCap() {
        storage.put(identity())
        repeat(EventIdentityStorage.DEFAULT_MAX_RESOLUTION_ATTEMPTS) {
            storage.recordResolutionAttempt(100L, INSTANCE_START, it.toLong())
        }

        assertEquals(
            "a permanently unmatchable event must stop being retried",
            0, storage.getUnresolved().size
        )
    }

    @Test
    fun unresolvedStillIncludesRowsBelowTheCap() {
        storage.put(identity())
        storage.recordResolutionAttempt(100L, INSTANCE_START, 1L)

        assertEquals(1, storage.getUnresolved().size)
    }

    @Test
    fun deleteRemovesOnlyTheTargetedRow() {
        storage.put(identity(eventId = 100L))
        storage.put(identity(eventId = 200L))

        assertTrue(storage.delete(100L, INSTANCE_START))

        assertEquals(1, storage.count())
        assertNotNull(storage.get(200L, INSTANCE_START))
    }

    @Test
    fun emptyDatabaseReadsCleanly() {
        assertEquals(0, storage.count())
        assertNull(storage.get(1L, 1L))
        assertTrue(storage.getAll().isEmpty())
        assertTrue(storage.getUnresolved().isEmpty())
    }

    companion object {
        private const val TEST_DATABASE_NAME = "RoomEventIdentity_Test"
        private const val INSTANCE_START = 1_700_000_000_000L
        private const val CAPTURED_AT = 1_700_000_500_000L
    }
}
