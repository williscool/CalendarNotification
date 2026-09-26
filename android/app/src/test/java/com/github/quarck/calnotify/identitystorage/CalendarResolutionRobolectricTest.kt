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
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.testutils.MockEventsStorage
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the wired-up calendar re-link: read, plan, write.
 *
 * The decisions themselves are covered by [CalendarResolutionPlanTest] and
 * [CalendarResolutionApplierTest], which need no Android at all. What is worth
 * testing here is the part those cannot reach -- that the right rows actually
 * reach storage, and that a failure anywhere in the chain does not escape into
 * the rescan that called it.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class CalendarResolutionRobolectricTest {

    private lateinit var context: Context
    private lateinit var identityStorage: EventIdentityStorage
    private lateinit var eventsStorage: MockEventsStorage

    /** Calendars the mocked provider reports. */
    private var deviceCalendars: List<CalendarRecord> = emptyList()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        eventsStorage = MockEventsStorage()

        val dao = FakeIdentityDao()
        identityStorage = EventIdentityStorage(dao)

        mockkObject(CalendarProvider)
        every { CalendarProvider.getCalendars(any()) } answers { deviceCalendars }

        ApplicationController.eventIdentityStorageProvider = { identityStorage }
        ApplicationController.eventsStorageProvider = { eventsStorage }
    }

    @After
    fun teardown() {
        ApplicationController.eventIdentityStorageProvider = null
        ApplicationController.eventsStorageProvider = null
        unmockkAll()
    }

    /** In-memory DAO; the real queries are covered by the instrumentation test. */
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
        override fun putAll(entities: List<EventIdentityEntity>) =
            entities.forEach { put(it) }
        override fun deleteByKey(eventId: Long, instanceStartTime: Long) =
            if (rows.remove(eventId to instanceStartTime) != null) 1 else 0
        override fun deleteAllRows() = rows.clear()
        override fun reKey(oldEventId: Long, instanceStartTime: Long, newEventId: Long) = 0
        override fun recordResolutionAttempt(
            eventId: Long, instanceStartTime: Long, attemptTime: Long
        ) = 0
    }

    private fun calendar(id: Long, owner: String) = CalendarRecord(
        calendarId = id,
        owner = owner,
        displayName = "Work",
        name = "Work",
        accountName = ACCOUNT,
        accountType = "com.google",
        timeZone = "UTC",
        color = 0,
        isVisible = true,
        isPrimary = false,
        isReadOnly = false,
        isSynced = true
    )

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

    /** Seeds an event plus the identity captured for it on the old device. */
    private fun seed(eventId: Long, storedCalendarId: Long, owner: String = OWNER) {
        eventsStorage.addEvent(event(eventId, storedCalendarId))
        identityStorage.put(
            EventIdentityEntity.create(
                eventId = eventId,
                instanceStartTime = INSTANCE_START,
                calendarId = storedCalendarId,
                backupInfo = CalendarBackupInfo(
                    calendarId = storedCalendarId,
                    accountName = ACCOUNT,
                    accountType = "com.google",
                    ownerAccount = owner,
                    displayName = "Work",
                    name = "Work"
                ),
                eventSyncId = "sync-$eventId",
                eventUid = null,
                capturedAtTime = 1L
            )
        )
    }

    private fun storedCalendarIdOf(eventId: Long) =
        eventsStorage.events.single { it.eventId == eventId }.calendarId

    // --- The restored-device case ---

    @Test
    fun movesEventsOntoTheCalendarTheyNowBelongTo() {
        // Captured against calendar 6; this device calls the same calendar 31.
        seed(100L, storedCalendarId = OLD_CALENDAR)
        deviceCalendars = listOf(calendar(NEW_CALENDAR, OWNER))

        ApplicationController.resolveEventCalendars(context)

        assertEquals(NEW_CALENDAR, storedCalendarIdOf(100L))
    }

    @Test
    fun movesEveryAffectedEvent() {
        (1L..5L).forEach { seed(it, storedCalendarId = OLD_CALENDAR) }
        deviceCalendars = listOf(calendar(NEW_CALENDAR, OWNER))

        ApplicationController.resolveEventCalendars(context)

        assertTrue(eventsStorage.events.all { it.calendarId == NEW_CALENDAR })
    }

// --- The healthy device: nothing should happen ---

    @Test
    fun aHealthyDeviceIsLeftAlone() {
        seed(100L, storedCalendarId = OLD_CALENDAR)
        deviceCalendars = listOf(calendar(OLD_CALENDAR, OWNER))

        ApplicationController.resolveEventCalendars(context)

        assertEquals(OLD_CALENDAR, storedCalendarIdOf(100L))
    }

    @Test
    fun runningTwiceChangesNothingTheSecondTime() {
        // This runs every 30 minutes forever, so it has to be idempotent.
        seed(100L, storedCalendarId = OLD_CALENDAR)
        deviceCalendars = listOf(calendar(NEW_CALENDAR, OWNER))

        ApplicationController.resolveEventCalendars(context)
        val afterFirst = storedCalendarIdOf(100L)
        ApplicationController.resolveEventCalendars(context)

        assertEquals(afterFirst, storedCalendarIdOf(100L))
    }

    // --- Cases that must not write anything ---

    @Test
    fun doesNothingWhenNoCalendarsAreVisibleYet() {
        // Mid-sync, or permissions revoked. Matching against an empty list
        // would report every event as calendar-not-found.
        seed(100L, storedCalendarId = OLD_CALENDAR)
        deviceCalendars = emptyList()

        ApplicationController.resolveEventCalendars(context)

        assertEquals(OLD_CALENDAR, storedCalendarIdOf(100L))
    }

    @Test
    fun leavesEventsAloneWhenTheAccountIsGone() {
        seed(100L, storedCalendarId = OLD_CALENDAR)
        deviceCalendars = listOf(
            calendar(NEW_CALENDAR, OWNER).copy(accountName = "someone.else@example.com")
        )

        ApplicationController.resolveEventCalendars(context)

        assertEquals(OLD_CALENDAR, storedCalendarIdOf(100L))
    }

    @Test
    fun refusesToGuessBetweenIndistinguishableCalendars() {
        // Two calendars the matcher cannot tell apart. Attaching events to an
        // arbitrary one is corruption that looks like success.
        seed(100L, storedCalendarId = OLD_CALENDAR, owner = "")
        deviceCalendars = listOf(calendar(NEW_CALENDAR, ""), calendar(OTHER_NEW, ""))

        ApplicationController.resolveEventCalendars(context)

        assertEquals("left for a later pass", OLD_CALENDAR, storedCalendarIdOf(100L))
    }

    @Test
    fun doesNothingWithNoIdentityCaptured() {
        eventsStorage.addEvent(event(100L, OLD_CALENDAR))
        deviceCalendars = listOf(calendar(NEW_CALENDAR, OWNER))

        ApplicationController.resolveEventCalendars(context)

        assertEquals(OLD_CALENDAR, storedCalendarIdOf(100L))
    }

// --- Failures must not escape into the rescan ---

    @Test
    fun aProviderFailureDoesNotPropagate() {
        seed(100L, storedCalendarId = OLD_CALENDAR)
        every { CalendarProvider.getCalendars(any()) } throws
            SecurityException("calendar permission revoked")

        // Must not throw: a missed re-link costs accuracy at restore time,
        // while an escaped exception costs the user a notification.
        ApplicationController.resolveEventCalendars(context)

        assertEquals(OLD_CALENDAR, storedCalendarIdOf(100L))
    }

    @Test
    fun aStorageFailureDoesNotPropagate() {
        seed(100L, storedCalendarId = OLD_CALENDAR)
        every { CalendarProvider.getCalendars(any()) } throws
            android.database.SQLException("disk full")

        ApplicationController.resolveEventCalendars(context)
    }

    companion object {
        private const val ACCOUNT = "user@example.com"
        private const val OWNER = "c_resource@group.calendar.google.com"
        private const val OLD_CALENDAR = 6L
        private const val NEW_CALENDAR = 31L
        private const val OTHER_OLD = 16L
        private const val OTHER_NEW = 32L
        private const val INSTANCE_START = 1_700_000_000_000L
    }
}
