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

package com.github.quarck.calnotify.eventsstorage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.logs.DevLog
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for EventsStorage functionality.
 */
@RunWith(AndroidJUnit4::class)
class EventsStorageTest {
    
    companion object {
        private const val LOG_TAG = "EventsStorageTest"
    }
    
    private lateinit var context: Context
    private lateinit var storage: EventsStorage
    
    private val baseTime = 1635724800000L
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        storage = EventsStorage(context)
        storage.classCustomUse { it.deleteAllEvents() }
        DevLog.info(LOG_TAG, "Test setup complete, isUsingRoom=${storage.isUsingRoom}")
    }
    
    @After
    fun cleanup() {
        storage.classCustomUse { it.deleteAllEvents() }
        DevLog.info(LOG_TAG, "Test cleanup complete")
    }
    
    private fun createTestEvent(
        eventId: Long,
        snoozedUntil: Long = 0,
        lastStatusChangeTime: Long = baseTime
    ): EventAlertRecord = EventAlertRecord(
        calendarId = 1L,
        eventId = eventId,
        isAllDay = false,
        isRepeating = false,
        alertTime = baseTime - 900000,
        notificationId = 0,
        title = "Test Event $eventId",
        desc = "",
        startTime = baseTime,
        endTime = baseTime + 3600000,
        instanceStartTime = baseTime + eventId, // Unique per event
        instanceEndTime = baseTime + 3600000,
        location = "",
        snoozedUntil = snoozedUntil,
        lastStatusChangeTime = lastStatusChangeTime,
        displayStatus = EventDisplayStatus.Hidden,
        color = 0,
        origin = EventOrigin.ProviderBroadcast,
        timeFirstSeen = baseTime,
        eventStatus = EventStatus.Confirmed,
        attendanceStatus = AttendanceStatus.None,
        flags = 0
    )
    
    // ============ eventsForDisplay tests ============
    
    @Test
    fun test_eventsForDisplay_sorts_nonSnoozed_before_snoozed() {
        DevLog.info(LOG_TAG, "=== test_eventsForDisplay_sorts_nonSnoozed_before_snoozed ===")
        
        storage.classCustomUse { db ->
            db.addEvent(createTestEvent(1, snoozedUntil = 5000, lastStatusChangeTime = 100))
            db.addEvent(createTestEvent(2, snoozedUntil = 0, lastStatusChangeTime = 100))
        }
        
        val sorted = storage.classCustomUse { it.eventsForDisplay }
        
        assertEquals(2, sorted.size)
        assertEquals("Non-snoozed should be first", 2L, sorted[0].eventId)
        assertEquals("Snoozed should be second", 1L, sorted[1].eventId)
    }
    
    @Test
    fun test_eventsForDisplay_sorts_snoozed_by_snoozeTime_ascending() {
        DevLog.info(LOG_TAG, "=== test_eventsForDisplay_sorts_snoozed_by_snoozeTime_ascending ===")
        
        storage.classCustomUse { db ->
            db.addEvent(createTestEvent(1, snoozedUntil = 3000, lastStatusChangeTime = 100))
            db.addEvent(createTestEvent(2, snoozedUntil = 1000, lastStatusChangeTime = 100))
            db.addEvent(createTestEvent(3, snoozedUntil = 2000, lastStatusChangeTime = 100))
        }
        
        val sorted = storage.classCustomUse { it.eventsForDisplay }
        
        assertEquals(3, sorted.size)
        assertEquals("Earliest snooze first", 2L, sorted[0].eventId)
        assertEquals("Middle snooze second", 3L, sorted[1].eventId)
        assertEquals("Latest snooze third", 1L, sorted[2].eventId)
    }
    
    @Test
    fun test_eventsForDisplay_sorts_same_snooze_by_lastStatusChangeTime_descending() {
        DevLog.info(LOG_TAG, "=== test_eventsForDisplay_sorts_same_snooze_by_lastStatusChangeTime_descending ===")
        
        storage.classCustomUse { db ->
            db.addEvent(createTestEvent(1, snoozedUntil = 0, lastStatusChangeTime = 100))
            db.addEvent(createTestEvent(2, snoozedUntil = 0, lastStatusChangeTime = 300))
            db.addEvent(createTestEvent(3, snoozedUntil = 0, lastStatusChangeTime = 200))
        }
        
        val sorted = storage.classCustomUse { it.eventsForDisplay }
        
        assertEquals(3, sorted.size)
        assertEquals("Most recent change first", 2L, sorted[0].eventId)
        assertEquals("Middle change second", 3L, sorted[1].eventId)
        assertEquals("Oldest change third", 1L, sorted[2].eventId)
    }
    
    @Test
    fun test_eventsForDisplay_complex_sort() {
        DevLog.info(LOG_TAG, "=== test_eventsForDisplay_complex_sort ===")
        
        storage.classCustomUse { db ->
            // Non-snoozed events with different lastStatusChangeTime
            db.addEvent(createTestEvent(1, snoozedUntil = 0, lastStatusChangeTime = 300))
            db.addEvent(createTestEvent(2, snoozedUntil = 0, lastStatusChangeTime = 500))
            db.addEvent(createTestEvent(3, snoozedUntil = 0, lastStatusChangeTime = 400))
            // Snoozed events
            db.addEvent(createTestEvent(4, snoozedUntil = 2000, lastStatusChangeTime = 100))
            db.addEvent(createTestEvent(5, snoozedUntil = 1000, lastStatusChangeTime = 200))
        }
        
        val sorted = storage.classCustomUse { it.eventsForDisplay }
        
        assertEquals(5, sorted.size)
        // Non-snoozed first, sorted by lastStatusChangeTime desc
        assertEquals(2L, sorted[0].eventId) // lastStatusChangeTime=500
        assertEquals(3L, sorted[1].eventId) // lastStatusChangeTime=400
        assertEquals(1L, sorted[2].eventId) // lastStatusChangeTime=300
        // Snoozed last, sorted by snoozedUntil asc
        assertEquals(5L, sorted[3].eventId) // snoozedUntil=1000
        assertEquals(4L, sorted[4].eventId) // snoozedUntil=2000
    }
    
    @Test
    fun test_eventsForDisplay_empty_returns_emptyList() {
        val sorted = storage.classCustomUse { it.eventsForDisplay }
        assertTrue("Empty storage should return empty list", sorted.isEmpty())
    }
    
    @Test
    fun test_eventsForDisplay_single_event() {
        storage.classCustomUse { db ->
            db.addEvent(createTestEvent(1, snoozedUntil = 0, lastStatusChangeTime = 100))
        }
        
        val sorted = storage.classCustomUse { it.eventsForDisplay }
        assertEquals(1, sorted.size)
        assertEquals(1L, sorted[0].eventId)
    }
}
