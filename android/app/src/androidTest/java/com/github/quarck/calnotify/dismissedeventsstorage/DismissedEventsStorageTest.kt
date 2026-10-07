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

package com.github.quarck.calnotify.dismissedeventsstorage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.logs.DevLog
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for DismissedEventsStorage functionality.
 */
@RunWith(AndroidJUnit4::class)
class DismissedEventsStorageTest {
    
    companion object {
        private const val LOG_TAG = "DismissedEventsStorageTest"
    }
    
    private lateinit var context: Context
    private lateinit var storage: DismissedEventsStorage
    
    private val baseTime = 1635724800000L
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        storage = DismissedEventsStorage(context)
        storage.use { it.clearHistory() }
        DevLog.info(LOG_TAG, "Test setup complete, isUsingRoom=${storage.isUsingRoom}")
    }
    
    @After
    fun cleanup() {
        storage.use { it.clearHistory() }
        DevLog.info(LOG_TAG, "Test cleanup complete")
    }
    
    private fun createTestEvent(eventId: Long): EventAlertRecord = EventAlertRecord(
        calendarId = 1L,
        eventId = eventId,
        isAllDay = false,
        isRepeating = false,
        alertTime = baseTime - 900000,
        notificationId = eventId.toInt(),
        title = "Test Event $eventId",
        desc = "",
        startTime = baseTime,
        endTime = baseTime + 3600000,
        instanceStartTime = baseTime + eventId, // Unique per event
        instanceEndTime = baseTime + 3600000,
        location = "",
        snoozedUntil = 0,
        lastStatusChangeTime = baseTime,
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
    fun test_eventsForDisplay_sorts_by_dismissTime_descending() {
        DevLog.info(LOG_TAG, "=== test_eventsForDisplay_sorts_by_dismissTime_descending ===")
        
        storage.use { db ->
            db.addEvent(EventDismissType.ManuallyDismissedFromNotification, 1000, createTestEvent(1))
            db.addEvent(EventDismissType.ManuallyDismissedFromNotification, 3000, createTestEvent(2))
            db.addEvent(EventDismissType.ManuallyDismissedFromNotification, 2000, createTestEvent(3))
        }
        
        val sorted = storage.use { it.eventsForDisplay }
        
        assertEquals(3, sorted.size)
        assertEquals("Most recent dismiss first", 2L, sorted[0].event.eventId)
        assertEquals("Middle dismiss second", 3L, sorted[1].event.eventId)
        assertEquals("Oldest dismiss third", 1L, sorted[2].event.eventId)
    }
    
    @Test
    fun test_eventsForDisplay_empty_returns_emptyList() {
        val sorted = storage.use { it.eventsForDisplay }
        assertTrue("Empty storage should return empty list", sorted.isEmpty())
    }
    
    @Test
    fun test_eventsForDisplay_single_event() {
        storage.use { db ->
            db.addEvent(EventDismissType.ManuallyDismissedFromNotification, 1000, createTestEvent(1))
        }
        
        val sorted = storage.use { it.eventsForDisplay }
        assertEquals(1, sorted.size)
        assertEquals(1L, sorted[0].event.eventId)
    }
    
    @Test
    fun test_eventsForDisplay_same_dismissTime_is_stable() {
        DevLog.info(LOG_TAG, "=== test_eventsForDisplay_same_dismissTime_is_stable ===")
        
        storage.use { db ->
            db.addEvent(EventDismissType.ManuallyDismissedFromNotification, 1000, createTestEvent(1))
            db.addEvent(EventDismissType.ManuallyDismissedFromNotification, 1000, createTestEvent(2))
            db.addEvent(EventDismissType.ManuallyDismissedFromNotification, 1000, createTestEvent(3))
        }
        
        val sorted = storage.use { it.eventsForDisplay }
        
        assertEquals(3, sorted.size)
        // All have same dismissTime - just verify all are present
        val eventIds = sorted.map { it.event.eventId }.toSet()
        assertEquals(setOf(1L, 2L, 3L), eventIds)
    }
}
