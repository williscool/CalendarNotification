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
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.CalendarBackupInfo
import com.github.quarck.calnotify.calendar.CalendarEventDetails
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventRecord
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.testutils.MockDismissedEventsStorage
import com.github.quarck.calnotify.testutils.MockEventsStorage
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests that portable identity is captured when events are registered.
 *
 * `eventsV9` keys events by provider row ids that mean nothing on another
 * device. Capture has to happen while those ids are still resolvable, so these
 * cover the write path: identity is recorded, the provider is not queried
 * redundantly, and -- most importantly -- a capture failure never propagates
 * into the event write it accompanies.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class EventIdentityCaptureRobolectricTest {

    /**
     * Wraps a real [EventIdentityStorage] around a fake DAO.
     *
     * Composition rather than subclassing -- EventIdentityStorage is final, and
     * going through the real facade means its SQLException handling is part of
     * what these tests exercise rather than something stubbed out.
     */
    private class RecordingIdentityStorage {
        val dao = FakeDao()
        val storage = EventIdentityStorage(dao)

        class FakeDao : EventIdentityDao {
            val rows = mutableMapOf<Pair<Long, Long>, EventIdentityEntity>()
            var throwOnWrite: android.database.SQLException? = null

            override fun getAll() = rows.values.toList()
            override fun count() = rows.size
            override fun getByKey(eventId: Long, instanceStartTime: Long) =
                rows[eventId to instanceStartTime]
            override fun getByEventId(eventId: Long) =
                rows.values.filter { it.eventId == eventId }
            override fun getUnresolved(maxAttempts: Int) =
                rows.values.filter { it.eventId == it.originalEventId }
            override fun put(entity: EventIdentityEntity) {
                throwOnWrite?.let { throw it }
                rows[entity.eventId to entity.instanceStartTime] = entity
            }
            override fun putAll(entities: List<EventIdentityEntity>) {
                throwOnWrite?.let { throw it }
                entities.forEach { rows[it.eventId to it.instanceStartTime] = it }
            }
            override fun deleteByKey(eventId: Long, instanceStartTime: Long) =
                if (rows.remove(eventId to instanceStartTime) != null) 1 else 0
            override fun deleteAllRows() = rows.clear()
            override fun reKey(oldEventId: Long, instanceStartTime: Long, newEventId: Long) = 0
            override fun recordResolutionAttempt(
                eventId: Long, instanceStartTime: Long, attemptTime: Long
            ) = 0
        }

        val stored get() = dao.rows
        fun failWrites(ex: android.database.SQLException?) { dao.throwOnWrite = ex }
    }

    private lateinit var context: Context
    private lateinit var identityStorage: RecordingIdentityStorage

    /** What EventsStorage returns for this test. */
    private var storedEvents: List<EventAlertRecord> = emptyList()

    /** What DismissedEventsStorage returns for this test. */
    private var dismissedEvents: List<EventAlertRecord> = emptyList()


    /** Counts provider calls, so redundant lookups are visible. */
    private var backupInfoLookups = 0
    private var getEventLookups = 0

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        identityStorage = RecordingIdentityStorage()
        backupInfoLookups = 0
        getEventLookups = 0

        mockkObject(CalendarProvider)

        every { CalendarProvider.getCalendarBackupInfo(any(), any()) } answers {
            backupInfoLookups++
            val calendarId = secondArg<Long>()
            if (calendarId == UNKNOWN_CALENDAR_ID) null
            else CalendarBackupInfo(
                calendarId = calendarId,
                accountName = "user@example.com",
                accountType = "com.google",
                ownerAccount = "user@example.com",
                displayName = "Calendar $calendarId",
                name = "user@example.com"
            )
        }

        every { CalendarProvider.getEvent(any(), any<Long>()) } answers {
            getEventLookups++
            val eventId = secondArg<Long>()
            if (eventId == EVENT_WITHOUT_IDENTITY) null
            else eventRecord(eventId)
        }

        ApplicationController.eventIdentityStorageProvider = { identityStorage.storage }
        ApplicationController.eventsStorageProvider = {
            MockEventsStorage().apply { storedEvents.forEach { addEvent(it) } }
        }
        ApplicationController.dismissedEventsStorageProvider = {
            MockDismissedEventsStorage().apply {
                dismissedEvents.forEach { addEvent(EventDismissType.ManuallyDismissedFromActivity, it) }
            }
        }
    }

    @After
    fun teardown() {
        ApplicationController.eventIdentityStorageProvider = null
        ApplicationController.eventsStorageProvider = null
        ApplicationController.dismissedEventsStorageProvider = null
        unmockkAll()
    }

    private fun eventRecord(eventId: Long, calendarId: Long = CALENDAR_ID) = EventRecord(
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
        syncId = if (eventId == EVENT_WITHOUT_IDENTITY) null else "sync-$eventId",
        uid2445 = null
    )

    private fun alertRecord(eventId: Long, calendarId: Long = CALENDAR_ID) = EventAlertRecord(
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

    /** Seeds both stores, then runs capture the way the reload pass does. */
    private fun capture(
        events: List<EventAlertRecord>,
        dismissed: List<EventAlertRecord> = emptyList()
    ) {
        storedEvents = events
        dismissedEvents = dismissed
        ApplicationController.captureEventIdentities(context)
    }

    @Test
    fun capturesIdentityForARegisteredEvent() {
        capture(listOf(alertRecord(100L)))

        val stored = identityStorage.stored[100L to INSTANCE_START]
        assertNotNull("identity should be recorded", stored)
        assertEquals("sync-100", stored!!.eventSyncId)
        assertEquals("user@example.com", stored.calendarAccountName)
        assertEquals("com.google", stored.calendarAccountType)
        assertEquals(CALENDAR_ID, stored.originalCalendarId)
        assertEquals(100L, stored.originalEventId)
    }

    @Test
    fun capturesIdentityForEveryEventInABatch() {
        capture((1L..5L).map { alertRecord(it) })

        assertEquals(5, identityStorage.stored.size)
    }

    @Test
    fun looksUpCalendarBackupInfoOncePerCalendarNotPerEvent() {
        // Ten events across two calendars: the provider should be asked about
        // the calendars twice, not ten times.
        capture((1L..5L).map { alertRecord(it, calendarId = CALENDAR_ID) } +
                (6L..10L).map { alertRecord(it, calendarId = OTHER_CALENDAR_ID) })

        assertEquals(10, identityStorage.stored.size)
        assertEquals("one backup-info lookup per calendar", 2, backupInfoLookups)
        assertEquals(
            "capture reads each event itself, so it stays independent of the reload",
            10, getEventLookups
        )
    }

    @Test
    fun emptyEventListDoesNotTouchTheProvider() {
        capture(emptyList())

        assertEquals(0, backupInfoLookups)
        assertEquals(0, getEventLookups)
        assertTrue(identityStorage.stored.isEmpty())
    }

    @Test
    fun eventWithNoIdentifiersAndNoCalendarIsSkipped() {
        capture(listOf(alertRecord(EVENT_WITHOUT_IDENTITY, calendarId = UNKNOWN_CALENDAR_ID)))

        assertTrue(
            "nothing resolvable to store, so no row",
            identityStorage.stored.isEmpty()
        )
    }

    @Test
    fun calendarStillCapturedWhenTheEventHasNoSyncId() {
        // A never-synced event in a known calendar: the calendar half is still
        // worth keeping, since it fixes attribution even if the event cannot be
        // re-resolved exactly.
        capture(listOf(alertRecord(EVENT_WITHOUT_IDENTITY)))

        val stored = identityStorage.stored[EVENT_WITHOUT_IDENTITY to INSTANCE_START]
        assertNotNull(stored)
        assertNull(stored!!.eventSyncId)
        assertEquals("user@example.com", stored.calendarAccountName)
    }

    // --- The property that matters most: capture must never break the write ---

    @Test
    fun storageFailureDoesNotPropagate() {
        identityStorage.failWrites(android.database.SQLException("disk full"))

        // Must not throw: a failed identity capture may cost a future restore
        // some accuracy, but must never cost the user a notification.
        capture(listOf(alertRecord(100L)))

        assertTrue(identityStorage.stored.isEmpty())
    }

    @Test
    fun revokedCalendarPermissionDoesNotPropagate() {
        // The provider is remote: permission can be revoked mid-flight.
        every { CalendarProvider.getCalendarBackupInfo(any(), any()) } throws
            SecurityException("calendar permission revoked")

        try {
            capture(listOf(alertRecord(100L)))
        } catch (ex: java.lang.reflect.InvocationTargetException) {
            throw AssertionError(
                "capture must swallow provider failures, but threw: ${ex.cause}"
            )
        }

        assertTrue(identityStorage.stored.isEmpty())
    }

    @Test
    fun providerQueryFailureDoesNotPropagate() {
        every { CalendarProvider.getCalendarBackupInfo(any(), any()) } throws
            android.database.SQLException("provider query failed")

        try {
            capture(listOf(alertRecord(100L)))
        } catch (ex: java.lang.reflect.InvocationTargetException) {
            throw AssertionError(
                "capture must swallow provider failures, but threw: ${ex.cause}"
            )
        }
    }

    // --- The self-check: stale rows must not be re-captured ---
    //
    // Capture reads the provider using the stored event id. That is right while
    // the id still resolves; after a restore it points at an unrelated event,
    // and capturing from it would leave the row holding a plausible-looking
    // pointer to the wrong event. See docs/dev_todo/portable_event_identity.md.

    /** Pre-seeds an identity row, as an earlier capture pass would have. */
    private fun seedIdentity(eventId: Long, syncId: String?) {
        identityStorage.storage.put(
            EventIdentityEntity.create(
                eventId = eventId,
                instanceStartTime = INSTANCE_START,
                calendarId = CALENDAR_ID,
                backupInfo = null,
                eventSyncId = syncId,
                eventUid = null,
                capturedAtTime = 1L
            )
        )
    }

    @Test
    fun captureRefreshesIdentityWhenTheSyncIdStillMatches() {
        // Healthy device: the provider agrees, so the row is re-captured and
        // its calendar half is refreshed.
        seedIdentity(100L, "sync-100")

        capture(listOf(alertRecord(100L)))

        val stored = identityStorage.stored[100L to INSTANCE_START]
        assertEquals("sync-100", stored!!.eventSyncId)
        assertEquals(
            "a matching row should have its calendar details refreshed",
            "user@example.com", stored.calendarAccountName
        )
    }

    @Test
    fun captureSkipsRowWhoseStoredSyncIdNoLongerMatches() {
        // The restored-device case. Event 100 is now some other event as far as
        // the provider is concerned, so the stale identity must be left exactly
        // as captured rather than overwritten with the wrong event's details.
        seedIdentity(100L, "sync-from-the-old-phone")

        capture(listOf(alertRecord(100L)))

        val stored = identityStorage.stored[100L to INSTANCE_START]
        assertEquals(
            "stale row must keep its original sync id for the resolver to use",
            "sync-from-the-old-phone", stored!!.eventSyncId
        )
        assertEquals(
            "capture must not overwrite it with what the stale id returned",
            "", stored.calendarAccountName
        )
    }

    @Test
    fun captureSkipsRowWhoseEventIsGoneFromTheProvider() {
        // Same verdict by the other branch: the id resolves to nothing. Uses
        // the event the mock provider returns null for.
        seedIdentity(EVENT_WITHOUT_IDENTITY, "sync-from-the-old-phone")

        capture(listOf(alertRecord(EVENT_WITHOUT_IDENTITY)))

        assertEquals(
            "sync-from-the-old-phone",
            identityStorage.stored[EVENT_WITHOUT_IDENTITY to INSTANCE_START]!!.eventSyncId
        )
    }

    @Test
    fun oneUnidentifiableEventDoesNotAffectItsNeighbours() {
        // The exact failure that killed the earlier validation-sample design: a
        // single event with no stored identity was read as proof the *device*
        // was wrong, which disabled capture for every event on it.
        //
        // Event 999 returns null from the provider and has no stored identity,
        // so it has no verdict. Every other event must still be captured.
        capture(listOf(alertRecord(EVENT_WITHOUT_IDENTITY)) + (1L..4L).map { alertRecord(it) })

        assertEquals(
            "an unidentifiable event must not suppress capture for the others",
            5, identityStorage.stored.size
        )
        assertEquals("sync-1", identityStorage.stored[1L to INSTANCE_START]!!.eventSyncId)
    }

    @Test
    fun aMixedBatchGivesEachRowItsOwnVerdict() {
        // Stale, current, and never-captured rows in one pass.
        seedIdentity(1L, "stale-value")      // provider says "sync-1" -> STALE
        seedIdentity(2L, "sync-2")           // provider agrees        -> CURRENT
        // event 3 has no seeded identity                              -> UNKNOWN

        capture((1L..3L).map { alertRecord(it) })

        assertEquals(
            "stale row keeps what it had",
            "stale-value", identityStorage.stored[1L to INSTANCE_START]!!.eventSyncId
        )
        assertEquals(
            "current row is refreshed",
            "user@example.com",
            identityStorage.stored[2L to INSTANCE_START]!!.calendarAccountName
        )
        assertEquals(
            "never-captured row is captured for the first time",
            "sync-3", identityStorage.stored[3L to INSTANCE_START]!!.eventSyncId
        )
    }

    // --- Dismissed events get identity too ---
    //
    // They are history, but restorable history: the un-dismiss path puts them
    // back into the active list and needs a cid/id that still resolve. Measured
    // on a real device, 2709 of 4183 dismissed rows were still resolvable in the
    // provider, so skipping them discarded most of what was recoverable.

    @Test
    fun capturesIdentityForDismissedEvents() {
        capture(events = emptyList(), dismissed = listOf(alertRecord(700L)))

        val stored = identityStorage.stored[700L to INSTANCE_START]
        assertNotNull("a dismissed event still needs portable identity", stored)
        assertEquals("sync-700", stored!!.eventSyncId)
        assertEquals("user@example.com", stored.calendarAccountName)
    }

    @Test
    fun capturesBothActiveAndDismissedInOnePass() {
        capture(events = (1L..3L).map { alertRecord(it) },
                dismissed = (10L..14L).map { alertRecord(it) })

        assertEquals("all 8 rows across both stores", 8, identityStorage.stored.size)
        assertEquals("sync-2", identityStorage.stored[2L to INSTANCE_START]!!.eventSyncId)
        assertEquals("sync-12", identityStorage.stored[12L to INSTANCE_START]!!.eventSyncId)
    }

    @Test
    fun anEventInBothStoresIsCapturedOnceFromTheActiveRow() {
        // Dismiss-then-restore can leave the same key in both stores. It must
        // not be looked up twice, and the active row is the authoritative one.
        capture(events = listOf(alertRecord(100L)), dismissed = listOf(alertRecord(100L)))

        assertEquals("deduplicated by (eventId, instanceStartTime)", 1, identityStorage.stored.size)
        assertEquals(
            "one provider lookup, not two",
            1, getEventLookups
        )
    }

    @Test
    fun dismissedEventAgedOutOfTheProviderIsSkippedNotFailed() {
        // The common case for old history: the provider has pruned the event.
        // Nothing to store, and it must not disturb the rows that do resolve.
        capture(events = listOf(alertRecord(100L)),
                dismissed = listOf(alertRecord(EVENT_WITHOUT_IDENTITY, calendarId = UNKNOWN_CALENDAR_ID)))

        assertEquals("only the resolvable row is stored", 1, identityStorage.stored.size)
        assertNotNull(identityStorage.stored[100L to INSTANCE_START])
    }

    @Test
    fun dismissedOnlyDatabaseStillCaptures() {
        // No active events at all -- capture must not bail out early on an
        // empty active list while dismissed rows are still worth recording.
        capture(events = emptyList(), dismissed = (1L..4L).map { alertRecord(it) })

        assertEquals(4, identityStorage.stored.size)
    }

    @Test
    fun alreadyCapturedDismissedEventsAreNotReReadEveryPass() {
        // Dismissed rows outnumber active ones ~11:1 on a real device and this
        // runs every 30 minutes on a wake-locked service. Their identity is
        // immutable history, so a captured dismissed row must cost nothing on
        // subsequent passes.
        seedIdentity(700L, "sync-700")

        capture(events = emptyList(), dismissed = listOf(alertRecord(700L)))

        assertEquals(
            "an already-captured dismissed event must not be queried again",
            0, getEventLookups
        )
        assertEquals(
            "and its stored identity is left alone",
            "sync-700", identityStorage.stored[700L to INSTANCE_START]!!.eventSyncId
        )
    }

    @Test
    fun activeEventsAreStillReReadEvenWhenAlreadyCaptured() {
        // The active list is small and its events move -- reschedules, edits,
        // calendar renames -- so it keeps being refreshed. Only the dismissed
        // side is skipped.
        seedIdentity(100L, "sync-100")

        capture(events = listOf(alertRecord(100L)))

        assertEquals("active events are re-read every pass", 1, getEventLookups)
    }

    @Test
    fun firstEverCaptureIsNotTreatedAsStale() {
        // Nothing stored yet, on every event, which is what installing this
        // version looks like. Must capture, not skip.
        capture((1L..3L).map { alertRecord(it) })

        assertEquals(3, identityStorage.stored.size)
    }



    companion object {
        private const val CALENDAR_ID = 6L
        private const val OTHER_CALENDAR_ID = 16L
        private const val UNKNOWN_CALENDAR_ID = -1L
        private const val EVENT_WITHOUT_IDENTITY = 999L
        private const val INSTANCE_START = 1_700_000_000_000L
    }
}
