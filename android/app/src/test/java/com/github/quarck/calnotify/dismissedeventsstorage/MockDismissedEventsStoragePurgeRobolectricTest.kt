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
 * Unit tests for MockDismissedEventsStorage.purgeOld() functionality.
 * 
 * These tests verify that the mock storage correctly implements purgeOld behavior,
 * ensuring the mock can be trusted in other tests that depend on it.
 * 
 * For tests of the real Room database purge, see the instrumented test:
 * DismissedEventsPurgeTest in androidTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class MockDismissedEventsStoragePurgeRobolectricTest {
    
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
        val currentTime = baseTime + 10 * dayInMillis // Day 10
        
        // Event dismissed on day 1 - should be purged with 3-day retention
        storage.addEvent(
            EventDismissType.ManuallyDismissedFromNotification,
            baseTime + 1 * dayInMillis,
            createTestEvent(1, baseTime)
        )
        
        // Event dismissed on day 8 - should NOT be purged
        storage.addEvent(
            EventDismissType.ManuallyDismissedFromNotification,
            baseTime + 8 * dayInMillis,
            createTestEvent(2, baseTime + 8 * dayInMillis)
        )
        
        assertEquals(2, storage.eventCount)
        
        storage.purgeOld(currentTime, 3 * dayInMillis)
        
        assertEquals(1, storage.eventCount)
        assertEquals(2L, storage.events[0].event.eventId)
    }
    
    @Test
    fun testPurgeOldWithForeverRetention() {
        val currentTime = baseTime + 365 * dayInMillis
        
        storage.addEvent(
            EventDismissType.ManuallyDismissedFromNotification,
            baseTime + 1 * dayInMillis,
            createTestEvent(1, baseTime)
        )
        
        storage.addEvent(
            EventDismissType.ManuallyDismissedFromNotification,
            baseTime + 100 * dayInMillis,
            createTestEvent(2, baseTime + 100 * dayInMillis)
        )
        
        assertEquals(2, storage.eventCount)
        
        // Long.MAX_VALUE should never purge anything
        storage.purgeOld(currentTime, Long.MAX_VALUE)
        
        assertEquals(2, storage.eventCount)
    }
    
    @Test
    fun testPurgeOldWithEmptyStorage() {
        assertEquals(0, storage.eventCount)
        
        storage.purgeOld(baseTime, 3 * dayInMillis)
        
        assertEquals(0, storage.eventCount)
    }
    
    @Test
    fun testPurgeOldCutoffIsExclusive() {
        // Test that events exactly at cutoff survive (< not <=)
        val currentTime = baseTime + 100 * dayInMillis
        
        // Add event exactly at cutoff (day 10 with 90-day retention = cutoff at day 10)
        storage.addEvent(
            EventDismissType.ManuallyDismissedFromNotification,
            baseTime + 10 * dayInMillis,
            createTestEvent(1, baseTime + 10 * dayInMillis)
        )
        
        // Add event before cutoff (day 9)
        storage.addEvent(
            EventDismissType.ManuallyDismissedFromNotification,
            baseTime + 9 * dayInMillis,
            createTestEvent(2, baseTime + 9 * dayInMillis)
        )
        
        assertEquals(2, storage.eventCount)
        
        storage.purgeOld(currentTime, 90 * dayInMillis)
        
        // Day 10 should survive (10 < 10 is false), day 9 should be purged (9 < 10 is true)
        assertEquals(1, storage.eventCount)
        assertEquals(1L, storage.events[0].event.eventId)
    }
}

