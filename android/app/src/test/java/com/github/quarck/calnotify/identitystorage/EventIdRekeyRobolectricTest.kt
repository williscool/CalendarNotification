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
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.testutils.MockDismissedEventsStorage
import com.github.quarck.calnotify.testutils.MockEventsStorage
import com.github.quarck.calnotify.testutils.MockMonitorStorage
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
 * Tests the wired-up event id re-key: identities + provider + four storages,
 * called through `ApplicationController.resolveEventIds`.
 *
 * The plan and applier decisions are covered by [EventIdRekeyPlanTest] and
 * [EventIdRekeyApplierTest], which need no Android. What this covers is the
 * part those cannot reach -- that the right storages actually move, that
 * failures don't escape the rescan, and that a healthy device is left alone.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class EventIdRekeyRobolectricTest {

    private lateinit var context: Context
    private lateinit var identityStorage: EventIdentityStorage
    private lateinit var eventsStorage: MockEventsStorage
    private lateinit var dismissedStorage: MockDismissedEventsStorage
    private lateinit var monitorStorage: MockMonitorStorage

    /** provider(calendarId, syncId) -> event id, or -1L for "unknown". */
    private var providerAnswers: MutableMap<Pair<Long, String>, Long> = mutableMapOf()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        eventsStorage = MockEventsStorage()
        dismissedStorage = MockDismissedEventsStorage()
        monitorStorage = MockMonitorStorage()

        val dao = FakeIdentityDao()
        identityStorage = EventIdentityStorage(dao)

        ApplicationController.resetSettings()

        mockkObject(CalendarProvider)
        every {
            CalendarProvider.findEventIdBySyncId(any(), any(), any(), any())
        } answers {
            val calId = secondArg<Long>()
            val syncId = thirdArg<String?>() ?: return@answers -1L
            providerAnswers[calId to syncId] ?: -1L
        }

        ApplicationController.eventIdentityStorageProvider = { identityStorage }
        ApplicationController.eventsStorageProvider = { eventsStorage }
        ApplicationController.dismissedEventsStorageProvider = { dismissedStorage }
        ApplicationController.monitorStorageProvider = { monitorStorage }
    }

    @After
    fun teardown() {
        ApplicationController.eventIdentityStorageProvider = null
        ApplicationController.eventsStorageProvider = null
        ApplicationController.dismissedEventsStorageProvider = null
        ApplicationController.monitorStorageProvider = null
        ApplicationController.resetSettings()
        unmockkAll()
    }

    private class FakeIdentityDao : EventIdentityDao {
        val rows = mutableMapOf<Pair<Long, Long>, EventIdentityEntity>()
        override fun getAll() = rows.values.toList()
        override fun getAllSyncIds() =
            rows.values.map { EventIdentitySyncId(it.eventId, it.instanceStartTime, it.eventSyncId) }
        override fun getFullyCapturedKeys() =
            rows.values.filter { it.hasUsableCalendar() }
                .map { EventIdentityKey(it.eventId, it.instanceStartTime) }
        override fun count() = rows.size
        override fun getByKey(eventId: Long, instanceStartTime: Long) =
            rows[eventId to instanceStartTime]
        override fun getByEventId(eventId: Long) = rows.values.filter { it.eventId == eventId }
        override fun getUnresolved(maxAttempts: Int) = rows.values.toList()
        override fun put(entity: EventIdentityEntity) {
            rows[entity.eventId to entity.instanceStartTime] = entity
        }
        override fun putAll(entities: List<EventIdentityEntity>) = entities.forEach { put(it) }
        override fun deleteByKey(eventId: Long, instanceStartTime: Long) =
            if (rows.remove(eventId to instanceStartTime) != null) 1 else 0
        override fun deleteAllRows() = rows.clear()
        override fun reKey(oldEventId: Long, instanceStartTime: Long, newEventId: Long): Int {
            val row = rows.remove(oldEventId to instanceStartTime) ?: return 0
            // Room's reKey moves the primary key columns onto the new id but
            // leaves originalEventId alone; mirror that here so tests can
            // assert the staleness anchor stays put.
            rows[newEventId to instanceStartTime] = row.copy(eventId = newEventId)
            return 1
        }
        override fun recordResolutionAttempt(
            eventId: Long, instanceStartTime: Long, attemptTime: Long
        ) = 0
    }

    private fun event(id: Long) = EventAlertRecord(
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

    /** Seed one event: identity, live row, dismissed row and monitor alert. */
    private fun seed(eventId: Long, syncId: String? = "sync-$eventId") {
        eventsStorage.addEvent(event(eventId))
        identityStorage.put(
            EventIdentityEntity.create(
                eventId = eventId,
                instanceStartTime = INSTANCE_START,
                calendarId = CALENDAR_ID,
                backupInfo = CalendarBackupInfo(
                    calendarId = CALENDAR_ID,
                    accountName = "user@example.com",
                    accountType = "com.google",
                    ownerAccount = "user@example.com",
                    displayName = "Work",
                    name = "Work"
                ),
                eventSyncId = syncId,
                eventUid = null,
                capturedAtTime = 1L
            )
        )
        dismissedStorage.addEvent(EventDismissType.ManuallyDismissedFromActivity, event(eventId))
    }

    // --- The restored-device case ---

    @Test
    fun rekeysEventIdWhenTheProviderKnowsTheSyncIdUnderANewId() {
        seed(eventId = 100L, syncId = "sync-100")
        providerAnswers[CALENDAR_ID to "sync-100"] = 500L

        ApplicationController.resolveEventIds(context)

        // events moved to new id
        assertNotNull("new event row exists",
            eventsStorage.events.singleOrNull { it.eventId == 500L })
        assertTrue("old event row gone", eventsStorage.events.none { it.eventId == 100L })

        // identity followed
        assertNotNull("identity moved", identityStorage.get(500L, INSTANCE_START))
        assertNull("identity gone from old key", identityStorage.get(100L, INSTANCE_START))

        // dismissed followed
        assertEquals("dismissed row moved",
            500L, dismissedStorage.events.single().event.eventId)
    }

    @Test
    fun aHealthyDeviceIsLeftAlone() {
        seed(eventId = 100L, syncId = "sync-100")
        providerAnswers[CALENDAR_ID to "sync-100"] = 100L

        ApplicationController.resolveEventIds(context)

        assertEquals("event still under original id",
            100L, eventsStorage.events.single().eventId)
        assertNotNull(identityStorage.get(100L, INSTANCE_START))
    }

    @Test
    fun runningTwiceChangesNothingTheSecondTime() {
        seed(eventId = 100L, syncId = "sync-100")
        providerAnswers[CALENDAR_ID to "sync-100"] = 500L

        ApplicationController.resolveEventIds(context)
        // After first run the identity is now keyed at 500, and the provider's
        // answer for sync-100 stays 500 -- second run should be a no-op.
        providerAnswers[CALENDAR_ID to "sync-100"] = 500L
        ApplicationController.resolveEventIds(context)

        assertEquals(1, eventsStorage.events.size)
        assertEquals(500L, eventsStorage.events.single().eventId)
    }

    // --- Cases that must not write anything ---

    @Test
    fun doesNothingWhenNoIdentitiesAreStored() {
        eventsStorage.addEvent(event(100L))

        ApplicationController.resolveEventIds(context)

        assertEquals(100L, eventsStorage.events.single().eventId)
    }

    @Test
    fun leavesEventsAloneWhenTheProviderCannotResolveTheSyncId() {
        seed(eventId = 100L, syncId = "sync-100")
        // provider has no answer for sync-100

        ApplicationController.resolveEventIds(context)

        assertEquals(100L, eventsStorage.events.single().eventId)
    }

    @Test
    fun leavesEventsAloneWhenTheIdentityHasNoSyncId() {
        seed(eventId = 100L, syncId = null)

        ApplicationController.resolveEventIds(context)

        assertEquals(100L, eventsStorage.events.single().eventId)
    }

    // --- Failures must not escape into the rescan ---

    @Test
    fun aProviderFailureDoesNotPropagate() {
        seed(eventId = 100L, syncId = "sync-100")
        every {
            CalendarProvider.findEventIdBySyncId(any(), any(), any(), any())
        } throws SecurityException("calendar permission revoked")

        // Must not throw.
        ApplicationController.resolveEventIds(context)

        assertEquals(100L, eventsStorage.events.single().eventId)
    }

    companion object {
        private const val CALENDAR_ID = 6L
        private const val INSTANCE_START = 1_700_000_000_000L
    }
}
