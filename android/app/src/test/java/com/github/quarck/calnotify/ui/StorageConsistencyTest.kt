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

package com.github.quarck.calnotify.ui

import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.testutils.MockDismissedEventsStorage
import com.github.quarck.calnotify.testutils.MockEventsStorage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Tests for storage consistency between EventsStorage and DismissedEventsStorage.
 * 
 * These tests verify that the cleanupOrphanedEvents logic correctly identifies and
 * removes events that exist in both storages (orphaned events from failed deletions).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class StorageConsistencyTest {
    
    private lateinit var eventsStorage: MockEventsStorage
    private lateinit var dismissedStorage: MockDismissedEventsStorage
    
    // Base time for tests
    private val baseTime = 1635724800000L // 2021-11-01 00:00:00 UTC
    
    @Before
    fun setup() {
        ShadowLog.stream = System.out
        eventsStorage = MockEventsStorage()
        dismissedStorage = MockDismissedEventsStorage()
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
    
    /**
     * Simulates the cleanupOrphanedEvents logic from MainActivity.
     */
    private fun cleanupOrphanedEvents() {
        // Get keys of all dismissed events
        val dismissedKeys = dismissedStorage.events.map { 
            Pair(it.event.eventId, it.event.instanceStartTime) 
        }.toSet()
        
        if (dismissedKeys.isEmpty()) return
        
        // Find any active events that are also in dismissed storage
        val orphaned = eventsStorage.events.filter { event ->
            dismissedKeys.contains(Pair(event.eventId, event.instanceStartTime))
        }
        
        if (orphaned.isNotEmpty()) {
            eventsStorage.deleteEvents(orphaned)
        }
    }
    
    @Test
    fun testNoOrphanedEvents() {
        // Add events only to active storage
        eventsStorage.addEvent(createTestEvent(1, baseTime))
        eventsStorage.addEvent(createTestEvent(2, baseTime + 3600000))
        
        // Add different events to dismissed storage
        dismissedStorage.addEvent(
            EventDismissType.ManuallyDismissed,
            baseTime,
            createTestEvent(3, baseTime + 7200000)
        )
        
        assertEquals("Active storage should have 2 events", 2, eventsStorage.eventCount)
        assertEquals("Dismissed storage should have 1 event", 1, dismissedStorage.eventCount)
        
        cleanupOrphanedEvents()
        
        assertEquals("Active storage should still have 2 events", 2, eventsStorage.eventCount)
        assertEquals("Dismissed storage should still have 1 event", 1, dismissedStorage.eventCount)
    }
    
    @Test
    fun testSingleOrphanedEvent() {
        val orphanedEvent = createTestEvent(1, baseTime)
        
        // Add event to both storages (simulating failed deletion)
        eventsStorage.addEvent(orphanedEvent)
        dismissedStorage.addEvent(EventDismissType.ManuallyDismissed, baseTime, orphanedEvent)
        
        // Add normal event only to active storage
        eventsStorage.addEvent(createTestEvent(2, baseTime + 3600000))
        
        assertEquals("Active storage should have 2 events", 2, eventsStorage.eventCount)
        assertEquals("Dismissed storage should have 1 event", 1, dismissedStorage.eventCount)
        
        cleanupOrphanedEvents()
        
        assertEquals("Active storage should have 1 event after cleanup", 1, eventsStorage.eventCount)
        assertEquals("Remaining event should be event 2", 2L, eventsStorage.events[0].eventId)
        assertEquals("Dismissed storage should still have 1 event", 1, dismissedStorage.eventCount)
    }
    
    @Test
    fun testMultipleOrphanedEvents() {
        // Add 3 events to both storages
        for (i in 1L..3L) {
            val event = createTestEvent(i, baseTime + i * 3600000)
            eventsStorage.addEvent(event)
            dismissedStorage.addEvent(EventDismissType.ManuallyDismissed, baseTime, event)
        }
        
        // Add 2 normal events only to active storage
        eventsStorage.addEvent(createTestEvent(4, baseTime + 4 * 3600000))
        eventsStorage.addEvent(createTestEvent(5, baseTime + 5 * 3600000))
        
        assertEquals("Active storage should have 5 events", 5, eventsStorage.eventCount)
        assertEquals("Dismissed storage should have 3 events", 3, dismissedStorage.eventCount)
        
        cleanupOrphanedEvents()
        
        assertEquals("Active storage should have 2 events after cleanup", 2, eventsStorage.eventCount)
        
        val remainingEventIds = eventsStorage.events.map { it.eventId }.sorted()
        assertEquals("Remaining events should be 4 and 5", listOf(4L, 5L), remainingEventIds)
        
        assertEquals("Dismissed storage should still have 3 events", 3, dismissedStorage.eventCount)
    }
    
    @Test
    fun testEmptyStorages() {
        assertEquals("Active storage should be empty", 0, eventsStorage.eventCount)
        assertEquals("Dismissed storage should be empty", 0, dismissedStorage.eventCount)
        
        // Should not throw on empty storages
        cleanupOrphanedEvents()
        
        assertEquals("Active storage should still be empty", 0, eventsStorage.eventCount)
        assertEquals("Dismissed storage should still be empty", 0, dismissedStorage.eventCount)
    }
    
    @Test
    fun testOnlyDismissedEventsNoActive() {
        // Add events only to dismissed storage
        dismissedStorage.addEvent(
            EventDismissType.ManuallyDismissed,
            baseTime,
            createTestEvent(1, baseTime)
        )
        dismissedStorage.addEvent(
            EventDismissType.ManuallyDismissed,
            baseTime,
            createTestEvent(2, baseTime + 3600000)
        )
        
        assertEquals("Active storage should be empty", 0, eventsStorage.eventCount)
        assertEquals("Dismissed storage should have 2 events", 2, dismissedStorage.eventCount)
        
        cleanupOrphanedEvents()
        
        assertEquals("Active storage should still be empty", 0, eventsStorage.eventCount)
        assertEquals("Dismissed storage should still have 2 events", 2, dismissedStorage.eventCount)
    }
    
    @Test
    fun testOrphanedEventWithDifferentInstanceTime() {
        // Same eventId but different instanceStartTime should NOT be considered orphaned
        val event1 = createTestEvent(1, baseTime)
        val event1DifferentInstance = createTestEvent(1, baseTime + Consts.DAY_IN_MILLISECONDS)
        
        eventsStorage.addEvent(event1)
        dismissedStorage.addEvent(
            EventDismissType.ManuallyDismissed,
            baseTime,
            event1DifferentInstance
        )
        
        assertEquals("Active storage should have 1 event", 1, eventsStorage.eventCount)
        assertEquals("Dismissed storage should have 1 event", 1, dismissedStorage.eventCount)
        
        cleanupOrphanedEvents()
        
        // The active event should NOT be removed because instanceStartTime differs
        assertEquals("Active storage should still have 1 event", 1, eventsStorage.eventCount)
        assertEquals("Dismissed storage should still have 1 event", 1, dismissedStorage.eventCount)
    }
    
    @Test
    fun testOrphanedEventWithSameInstanceTime() {
        // Same eventId AND same instanceStartTime IS considered orphaned
        val event1 = createTestEvent(1, baseTime)
        
        eventsStorage.addEvent(event1)
        dismissedStorage.addEvent(EventDismissType.ManuallyDismissed, baseTime, event1)
        
        assertEquals("Active storage should have 1 event", 1, eventsStorage.eventCount)
        assertEquals("Dismissed storage should have 1 event", 1, dismissedStorage.eventCount)
        
        cleanupOrphanedEvents()
        
        // The active event SHOULD be removed because both eventId and instanceStartTime match
        assertEquals("Active storage should be empty after cleanup", 0, eventsStorage.eventCount)
        assertEquals("Dismissed storage should still have 1 event", 1, dismissedStorage.eventCount)
    }
}

