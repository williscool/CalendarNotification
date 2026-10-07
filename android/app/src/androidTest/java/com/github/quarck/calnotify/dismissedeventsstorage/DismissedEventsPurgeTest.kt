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
import com.github.quarck.calnotify.Consts
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
 * Instrumented tests for dismissed events purge functionality using real Room database.
 * 
 * These tests verify that RoomDismissedEventsStorage.purgeOld() correctly removes old events
 * based on the configured retention period.
 */
@RunWith(AndroidJUnit4::class)
class DismissedEventsPurgeTest {
    
    companion object {
        private const val LOG_TAG = "DismissedEventsPurgeTest"
    }
    
    private lateinit var context: Context
    private lateinit var storage: DismissedEventsStorage
    
    // Base time for tests
    private val baseTime = 1635724800000L // 2021-11-01 00:00:00 UTC
    private val dayInMillis = Consts.DAY_IN_MILLISECONDS
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        storage = DismissedEventsStorage(context)
        
        // Clear any existing events
        storage.use { it.clearHistory() }
        
        DevLog.info(LOG_TAG, "Test setup complete")
    }
    
    @After
    fun cleanup() {
        storage.use { it.clearHistory() }
        DevLog.info(LOG_TAG, "Test cleanup complete")
    }
    
    private fun createTestEvent(
        eventId: Long,
        instanceStartTime: Long = baseTime
    ): EventAlertRecord {
        return EventAlertRecord(
            calendarId = 1L,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = instanceStartTime - 900000,
            notificationId = eventId.toInt(),
            title = "Test Event $eventId",
            desc = "Description",
            startTime = instanceStartTime,
            endTime = instanceStartTime + 3600000,
            instanceStartTime = instanceStartTime,
            instanceEndTime = instanceStartTime + 3600000,
            location = "Test Location",
            snoozedUntil = 0L,
            lastStatusChangeTime = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = 0L,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
    }
    
    @Test
    fun testPurgeOldRemovesExpiredEvents() {
        DevLog.info(LOG_TAG, "Running testPurgeOldRemovesExpiredEvents")
        
        val currentTime = baseTime + 10 * dayInMillis
        
        storage.use { db ->
            // Event dismissed on day 1 - should be purged with 3-day retention
            db.addEvent(
                EventDismissType.ManuallyDismissedFromNotification,
                baseTime + 1 * dayInMillis,
                createTestEvent(1, baseTime)
            )
            
            // Event dismissed on day 8 - should NOT be purged
            db.addEvent(
                EventDismissType.ManuallyDismissedFromNotification,
                baseTime + 8 * dayInMillis,
                createTestEvent(2, baseTime + 8 * dayInMillis)
            )
            
            assertEquals("Should have 2 events before purge", 2, db.events.size)
            
            db.purgeOld(currentTime, 3 * dayInMillis)
            
            assertEquals("Should have 1 event after purge", 1, db.events.size)
            assertEquals("Remaining event should be event 2", 2L, db.events[0].event.eventId)
        }
    }
    
    @Test
    fun testPurgeOldWithForeverRetention() {
        DevLog.info(LOG_TAG, "Running testPurgeOldWithForeverRetention")
        
        val currentTime = baseTime + 365 * dayInMillis
        
        storage.use { db ->
            db.addEvent(
                EventDismissType.ManuallyDismissedFromNotification,
                baseTime + 1 * dayInMillis,
                createTestEvent(1, baseTime)
            )
            
            db.addEvent(
                EventDismissType.ManuallyDismissedFromNotification,
                baseTime + 100 * dayInMillis,
                createTestEvent(2, baseTime + 100 * dayInMillis)
            )
            
            assertEquals("Should have 2 events before purge", 2, db.events.size)
            
            // Long.MAX_VALUE should never purge anything
            db.purgeOld(currentTime, Long.MAX_VALUE)
            
            assertEquals("Should still have 2 events (forever retention)", 2, db.events.size)
        }
    }
    
    @Test
    fun testPurgeOldWithEmptyStorage() {
        DevLog.info(LOG_TAG, "Running testPurgeOldWithEmptyStorage")
        
        storage.use { db ->
            assertEquals("Should start with 0 events", 0, db.events.size)
            
            db.purgeOld(baseTime, 3 * dayInMillis)
            
            assertEquals("Should still have 0 events", 0, db.events.size)
        }
    }
    
    @Test
    fun testPurgeOldCutoffIsExclusive() {
        DevLog.info(LOG_TAG, "Running testPurgeOldCutoffIsExclusive")
        
        val currentTime = baseTime + 100 * dayInMillis
        
        storage.use { db ->
            // Add event exactly at cutoff (day 10 with 90-day retention = cutoff at day 10)
            db.addEvent(
                EventDismissType.ManuallyDismissedFromNotification,
                baseTime + 10 * dayInMillis,
                createTestEvent(1, baseTime + 10 * dayInMillis)
            )
            
            // Add event before cutoff (day 9)
            db.addEvent(
                EventDismissType.ManuallyDismissedFromNotification,
                baseTime + 9 * dayInMillis,
                createTestEvent(2, baseTime + 9 * dayInMillis)
            )
            
            assertEquals("Should have 2 events before purge", 2, db.events.size)
            
            db.purgeOld(currentTime, 90 * dayInMillis)
            
            // Day 10 should survive (10 < 10 is false), day 9 should be purged (9 < 10 is true)
            assertEquals("Should have 1 event after purge", 1, db.events.size)
            assertEquals("Event at cutoff should survive", 1L, db.events[0].event.eventId)
        }
    }
    
    @Test
    fun testPurgeOldWith90DayRetention() {
        DevLog.info(LOG_TAG, "Running testPurgeOldWith90DayRetention")
        
        val currentTime = baseTime + 100 * dayInMillis
        
        storage.use { db ->
            for (day in listOf(1, 10, 20, 50, 90)) {
                db.addEvent(
                    EventDismissType.ManuallyDismissedFromNotification,
                    baseTime + day * dayInMillis,
                    createTestEvent(day.toLong(), baseTime + day * dayInMillis)
                )
            }
            
            assertEquals("Should have 5 events before purge", 5, db.events.size)
            
            db.purgeOld(currentTime, 90 * dayInMillis)
            
            // Events from days 10, 20, 50, 90 should remain (day 10 is exactly at cutoff)
            assertEquals("Should have 4 events after 90-day purge", 4, db.events.size)
            
            val remainingEventIds = db.events.map { it.event.eventId }.sorted()
            assertEquals(listOf(10L, 20L, 50L, 90L), remainingEventIds)
        }
    }
    
    @Test
    fun testEventsReturnedInDismissTimeDescOrder() {
        DevLog.info(LOG_TAG, "Running testEventsReturnedInDismissTimeDescOrder")
        
        storage.use { db ->
            // Add events in random order
            for (day in listOf(50, 10, 90, 30, 70)) {
                db.addEvent(
                    EventDismissType.ManuallyDismissedFromNotification,
                    baseTime + day * dayInMillis,
                    createTestEvent(day.toLong(), baseTime + day * dayInMillis)
                )
            }
            
            val events = db.events
            assertEquals(5, events.size)
            
            // Verify descending order by dismissTime
            val dismissTimes = events.map { it.dismissTime }
            assertEquals(
                "Events should be sorted by dismissTime DESC",
                dismissTimes.sortedDescending(),
                dismissTimes
            )
        }
    }
}

