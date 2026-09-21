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
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
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
    }

    @After
    fun teardown() {
        ApplicationController.eventIdentityStorageProvider = null
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

    /**
     * Calls capture the way the reload pass does: stored events paired with the
     * provider rows it has already read for them.
     */
    private fun capture(events: List<EventAlertRecord>) {
        val pairs = events.map { stored ->
            stored to eventRecord(stored.eventId, stored.calendarId)
        }
        ApplicationController.captureEventIdentities(context, pairs)
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
            "capture must not re-query events -- the caller already read them",
            0, getEventLookups
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

    companion object {
        private const val CALENDAR_ID = 6L
        private const val OTHER_CALENDAR_ID = 16L
        private const val UNKNOWN_CALENDAR_ID = -1L
        private const val EVENT_WITHOUT_IDENTITY = 999L
        private const val INSTANCE_START = 1_700_000_000_000L
    }
}
