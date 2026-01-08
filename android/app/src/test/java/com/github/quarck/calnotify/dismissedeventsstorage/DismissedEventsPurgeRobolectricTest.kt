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

import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.testutils.MockDismissedEventsStorage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Tests for dismissed events purge functionality.
 * 
 * These tests verify that the purgeOld function correctly removes old events
 * based on the configured retention period, including the "forever" option.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class DismissedEventsPurgeRobolectricTest {
    
    private lateinit var storage: MockDismissedEventsStorage
    
    // Base time for tests
    private val baseTime = 1635724800000L // 2021-11-01 00:00:00 UTC
    private val dayInMillis = Consts.DAY_IN_MILLISECONDS
    
    @Before
    fun setup() {
        ShadowLog.stream = System.out
        storage = MockDismissedEventsStorage()
    }
    
    /**
     * Creates a test event with the given parameters.
     */
    private fun createTestEvent(
        eventId: Long,
        instanceStartTime: Long = baseTime
    ): EventAlertRecord {
        return EventAlertRecord(
            calendarId = 1L,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = instanceStartTime - 900000, // 15 min before
            notificationId = eventId.toInt(),
            title = "Test Event $eventId",
            desc = "Description",
            startTime = instanceStartTime,
            endTime = instanceStartTime + 3600000, // 1 hour
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
        // Add events dismissed at different times
        val currentTime = baseTime + 10 * dayInMillis // Day 10
        
        // Event dismissed on day 1 (9 days old) - should be purged with 3-day retention
        storage.addEvent(
            EventDismissType.ManuallyDismissedFromNotification,
            baseTime + 1 * dayInMillis,
            createTestEvent(1, baseTime)
        )
        
        // Event dismissed on day 8 (2 days old) - should NOT be purged
        storage.addEvent(
            EventDismissType.ManuallyDismissedFromNotification,
            baseTime + 8 * dayInMillis,
            createTestEvent(2, baseTime + 8 * dayInMillis)
        )
        
        assertEquals("Should have 2 events before purge", 2, storage.eventCount)
        
        // Purge with 3-day retention
        val maxLiveTime = 3 * dayInMillis
        storage.purgeOld(currentTime, maxLiveTime)
        
        assertEquals("Should have 1 event after purge", 1, storage.eventCount)
        assertEquals("Remaining event should be event 2", 2L, storage.events[0].event.eventId)
    }
    
    @Test
    fun testPurgeOldWithForeverRetention() {
        // Add events dismissed at different times
        val currentTime = baseTime + 365 * dayInMillis // 1 year later
        
        // Event dismissed on day 1 (364 days old)
        storage.addEvent(
            EventDismissType.ManuallyDismissedFromNotification,
            baseTime + 1 * dayInMillis,
            createTestEvent(1, baseTime)
        )
        
        // Event dismissed on day 100
        storage.addEvent(
            EventDismissType.ManuallyDismissedFromNotification,
            baseTime + 100 * dayInMillis,
            createTestEvent(2, baseTime + 100 * dayInMillis)
        )
        
        assertEquals("Should have 2 events before purge", 2, storage.eventCount)
        
        // Purge with "forever" retention (Long.MAX_VALUE means never purge)
        // When keepHistoryDays is 0, we skip calling purgeOld entirely in production,
        // but if called with Long.MAX_VALUE, nothing should be purged
        storage.purgeOld(currentTime, Long.MAX_VALUE)
        
        assertEquals("Should still have 2 events (forever retention)", 2, storage.eventCount)
    }
    
    @Test
    fun testPurgeOldWithDifferentRetentionPeriods() {
        val currentTime = baseTime + 100 * dayInMillis // Day 100
        
        // Add events dismissed at different times
        for (day in listOf(5, 50, 80, 95, 99)) {
            storage.addEvent(
                EventDismissType.ManuallyDismissedFromNotification,
                baseTime + day * dayInMillis,
                createTestEvent(day.toLong(), baseTime + day * dayInMillis)
            )
        }
        
        assertEquals("Should have 5 events before purge", 5, storage.eventCount)
        
        // Test with 7-day retention
        val sevenDayRetention = 7 * dayInMillis
        storage.purgeOld(currentTime, sevenDayRetention)
        
        // Only events from days 95 and 99 should remain (within last 7 days from day 100)
        assertEquals("Should have 2 events after 7-day purge", 2, storage.eventCount)
        
        val remainingEventIds = storage.events.map { it.event.eventId }.sorted()
        assertEquals(listOf(95L, 99L), remainingEventIds)
    }
    
    @Test
    fun testPurgeOldWithEmptyStorage() {
        assertEquals("Should start with 0 events", 0, storage.eventCount)
        
        // Purge should not throw on empty storage
        storage.purgeOld(baseTime, 3 * dayInMillis)
        
        assertEquals("Should still have 0 events", 0, storage.eventCount)
    }
    
    @Test
    fun testPurgeOldDoesNotAffectRecentEvents() {
        val currentTime = baseTime + 10 * dayInMillis
        
        // Add 5 events all within the retention period
        for (i in 1..5) {
            storage.addEvent(
                EventDismissType.ManuallyDismissedFromNotification,
                baseTime + (10 - i) * dayInMillis, // Days 9, 8, 7, 6, 5 - all within 7 days
                createTestEvent(i.toLong(), baseTime + (10 - i) * dayInMillis)
            )
        }
        
        assertEquals("Should have 5 events before purge", 5, storage.eventCount)
        
        // Purge with 7-day retention
        storage.purgeOld(currentTime, 7 * dayInMillis)
        
        // All events should remain (days 5-9 are all within 7 days of day 10)
        assertEquals("Should still have 5 events (all recent)", 5, storage.eventCount)
    }
    
    @Test
    fun testPurgeOldWith90DayRetention() {
        val currentTime = baseTime + 100 * dayInMillis
        
        // Add events from various times
        for (day in listOf(1, 10, 20, 50, 90)) {
            storage.addEvent(
                EventDismissType.ManuallyDismissedFromNotification,
                baseTime + day * dayInMillis,
                createTestEvent(day.toLong(), baseTime + day * dayInMillis)
            )
        }
        
        assertEquals("Should have 5 events before purge", 5, storage.eventCount)
        
        // Purge with 90-day retention
        storage.purgeOld(currentTime, 90 * dayInMillis)
        
        // Events from days 10, 20, 50, 90 should remain (day 10 is exactly at cutoff, < not <=)
        assertEquals("Should have 4 events after 90-day purge", 4, storage.eventCount)
        
        val remainingEventIds = storage.events.map { it.event.eventId }.sorted()
        assertEquals(listOf(10L, 20L, 50L, 90L), remainingEventIds)
    }
    
    @Test
    fun testPurgeOldWith365DayRetention() {
        val currentTime = baseTime + 400 * dayInMillis // Over a year out
        
        // Add events from various times
        for (day in listOf(1, 100, 200, 350, 399)) {
            storage.addEvent(
                EventDismissType.ManuallyDismissedFromNotification,
                baseTime + day * dayInMillis,
                createTestEvent(day.toLong(), baseTime + day * dayInMillis)
            )
        }
        
        assertEquals("Should have 5 events before purge", 5, storage.eventCount)
        
        // Purge with 365-day retention
        storage.purgeOld(currentTime, 365 * dayInMillis)
        
        // Only events from days 100, 200, 350, 399 should remain
        assertEquals("Should have 4 events after 365-day purge", 4, storage.eventCount)
        
        val remainingEventIds = storage.events.map { it.event.eventId }.sorted()
        assertEquals(listOf(100L, 200L, 350L, 399L), remainingEventIds)
    }
}

