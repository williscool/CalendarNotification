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

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.testutils.MockDismissedEventsStorage
import com.github.quarck.calnotify.testutils.MockEventsStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/**
 * Issue #298, end to end: capture and calendar re-link run through the real
 * CalendarProvider against calendars whose columns are null, as local calendars
 * on de-Googled phones are. The other identity tests stub the provider, which
 * can only hand back fully populated calendars -- so they never saw this.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class NullCalendarColumnsIdentityRobolectricTest {

    /** Answers calendar and event queries from in-memory rows keyed by column name. */
    private class FakeCalendarProvider : ContentProvider() {
        var calendars: List<Map<String, Any?>> = emptyList()
        var events: List<Map<String, Any?>> = emptyList()

        override fun onCreate() = true
        override fun query(
            uri: Uri, projection: Array<String>?, selection: String?,
            selectionArgs: Array<String>?, sortOrder: String?
        ): Cursor {
            val segments = uri.pathSegments
            val rows = when (segments.firstOrNull()) {
                "calendars" -> calendars.filter {
                    selection == null || it[CalendarContract.Calendars._ID] == selectionArgs!![0].toLong()
                }
                "events" -> events.filter {
                    segments.size < 2 || it[CalendarContract.Events._ID] == segments[1].toLong()
                }
                else -> emptyList()
            }
            return MatrixCursor(projection).apply {
                rows.forEach { row -> addRow(projection!!.map { row[it] }) }
            }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
        override fun update(
            uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?
        ) = 0
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
        override fun getByKey(eventId: Long, instanceStartTime: Long) = rows[eventId to instanceStartTime]
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
            rows[newEventId to instanceStartTime] = row.copy(eventId = newEventId)
            return 1
        }
        override fun recordResolutionAttempt(eventId: Long, instanceStartTime: Long, attemptTime: Long) = 0
    }

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val provider = FakeCalendarProvider()
    private val identityDao = FakeIdentityDao()
    private lateinit var eventsStorage: MockEventsStorage

    @Before
    fun setup() {
        shadowOf(context).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)

        eventsStorage = MockEventsStorage()
        ApplicationController.resetSettings()
        ApplicationController.eventIdentityStorageProvider = { EventIdentityStorage(identityDao) }
        ApplicationController.eventsStorageProvider = { eventsStorage }
        ApplicationController.dismissedEventsStorageProvider = { MockDismissedEventsStorage() }
    }

    @After
    fun teardown() {
        ApplicationController.eventIdentityStorageProvider = null
        ApplicationController.eventsStorageProvider = null
        ApplicationController.dismissedEventsStorageProvider = null
        ApplicationController.resetSettings()
    }

    private fun calendarRow(id: Long, accountName: String?, accountType: String?) = mapOf(
        CalendarContract.Calendars._ID to id,
        CalendarContract.Calendars.ACCOUNT_NAME to accountName,
        CalendarContract.Calendars.ACCOUNT_TYPE to accountType,
        CalendarContract.Calendars.OWNER_ACCOUNT to null,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME to "Personal",
        CalendarContract.Calendars.NAME to null
    )

    private fun providerEvent(calendarId: Long) = mapOf(
        CalendarContract.Events._ID to EVENT_ID,
        CalendarContract.Events.CALENDAR_ID to calendarId,
        CalendarContract.Events.TITLE to "Dentist",
        CalendarContract.Events.DTSTART to INSTANCE_START,
        CalendarContract.Events._SYNC_ID to SYNC_ID
    )

    private fun storedEvent(calendarId: Long) = EventAlertRecord(
        calendarId = calendarId,
        eventId = EVENT_ID,
        isAllDay = false,
        isRepeating = false,
        alertTime = INSTANCE_START - 600_000L,
        notificationId = 0,
        title = "Dentist",
        desc = "",
        startTime = INSTANCE_START,
        endTime = INSTANCE_START + 3_600_000L,
        instanceStartTime = INSTANCE_START,
        instanceEndTime = INSTANCE_START + 3_600_000L,
        location = "",
        lastStatusChangeTime = 0L
    )

    @Test
    fun localCalendarWithNullOwnerAndNameIsCapturedThenRelinkedAfterRestore() {
        provider.calendars = listOf(calendarRow(OLD_CALENDAR_ID, "local", CalendarContract.ACCOUNT_TYPE_LOCAL))
        provider.events = listOf(providerEvent(OLD_CALENDAR_ID))
        eventsStorage.addEvent(storedEvent(OLD_CALENDAR_ID))

        ApplicationController.captureEventIdentities(context)

        val captured = identityDao.rows[EVENT_ID to INSTANCE_START]
        assertNotNull("captured despite null calendar columns", captured)
        assertEquals("", captured!!.calendarOwnerAccount)
        assertEquals("", captured.calendarName)
        assertEquals(SYNC_ID, captured.eventSyncId)

        // Restore: same calendar, new local id. The "" captured for its null
        // columns must still match what getCalendars reads back.
        provider.calendars = listOf(calendarRow(NEW_CALENDAR_ID, "local", CalendarContract.ACCOUNT_TYPE_LOCAL))

        ApplicationController.resolveEventCalendars(context)

        assertEquals(NEW_CALENDAR_ID, eventsStorage.events.single().calendarId)
    }

    @Test
    fun calendarWithNoAccountAtAllDoesNotCrashCapture() {
        provider.calendars = listOf(calendarRow(OLD_CALENDAR_ID, accountName = null, accountType = null))
        provider.events = listOf(providerEvent(OLD_CALENDAR_ID))
        eventsStorage.addEvent(storedEvent(OLD_CALENDAR_ID))

        ApplicationController.captureEventIdentities(context)

        // Nothing to match a calendar on, but the event's own identity is kept.
        val captured = identityDao.rows[EVENT_ID to INSTANCE_START]
        assertNotNull(captured)
        assertEquals(SYNC_ID, captured!!.eventSyncId)
        assertFalse(captured.hasUsableCalendar())
    }

    companion object {
        private const val OLD_CALENDAR_ID = 7L
        private const val NEW_CALENDAR_ID = 12L
        private const val EVENT_ID = 100L
        private const val INSTANCE_START = 1_700_000_000_000L
        private const val SYNC_ID = "sync-100"
    }
}
