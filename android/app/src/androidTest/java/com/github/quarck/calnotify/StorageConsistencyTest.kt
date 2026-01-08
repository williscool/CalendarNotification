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

package com.github.quarck.calnotify

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.ui.MainActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for storage consistency between EventsStorage and DismissedEventsStorage.
 * 
 * These tests verify that the cleanupOrphanedEvents logic (removing events that exist in both
 * storages due to failed deletions) works correctly with real Room databases.
 */
@RunWith(AndroidJUnit4::class)
class StorageConsistencyTest {
    
    companion object {
        private const val LOG_TAG = "StorageConsistencyTest"
    }
    
    private lateinit var context: Context
    private lateinit var eventsStorage: EventsStorage
    private lateinit var dismissedStorage: DismissedEventsStorage
    
    private val baseTime = 1635724800000L // 2021-11-01 00:00:00 UTC
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        eventsStorage = EventsStorage(context)
        dismissedStorage = DismissedEventsStorage(context)
        
        // Clear storages
        eventsStorage.classCustomUse { it.deleteAllEvents() }
        dismissedStorage.classCustomUse { it.clearHistory() }
        
        DevLog.info(LOG_TAG, "Test setup complete")
    }
    
    @After
    fun cleanup() {
        eventsStorage.classCustomUse { it.deleteAllEvents() }
        dismissedStorage.classCustomUse { it.clearHistory() }
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
    
    /**
     * Calls the real cleanupOrphanedEvents from MainActivity.
     */
    private fun cleanupOrphanedEvents() {
        MainActivity.cleanupOrphanedEvents(context)
    }
    
    @Test
    fun testNoOrphanedEvents() {
        DevLog.info(LOG_TAG, "Running testNoOrphanedEvents")
        
        // Add events only to active storage
        eventsStorage.classCustomUse { it.addEvent(createTestEvent(1, baseTime)) }
        eventsStorage.classCustomUse { it.addEvent(createTestEvent(2, baseTime + 3600000)) }
        
        // Add different event to dismissed storage
        dismissedStorage.classCustomUse { 
            it.addEvent(EventDismissType.ManuallyDismissedFromNotification, baseTime, createTestEvent(3, baseTime + 7200000))
        }
        
        val activeCountBefore = eventsStorage.classCustomUse { it.events.size }
        assertEquals("Active storage should have 2 events", 2, activeCountBefore)
        
        cleanupOrphanedEvents()
        
        val activeCountAfter = eventsStorage.classCustomUse { it.events.size }
        assertEquals("Active storage should still have 2 events", 2, activeCountAfter)
    }
    
    @Test
    fun testSingleOrphanedEvent() {
        DevLog.info(LOG_TAG, "Running testSingleOrphanedEvent")
        
        val orphanedEvent = createTestEvent(1, baseTime)
        
        // Add event to both storages (simulating failed deletion)
        eventsStorage.classCustomUse { it.addEvent(orphanedEvent) }
        dismissedStorage.classCustomUse { 
            it.addEvent(EventDismissType.ManuallyDismissedFromNotification, baseTime, orphanedEvent)
        }
        
        // Add normal event only to active storage
        eventsStorage.classCustomUse { it.addEvent(createTestEvent(2, baseTime + 3600000)) }
        
        val activeCountBefore = eventsStorage.classCustomUse { it.events.size }
        assertEquals("Active storage should have 2 events", 2, activeCountBefore)
        
        cleanupOrphanedEvents()
        
        val activeCountAfter = eventsStorage.classCustomUse { it.events.size }
        assertEquals("Active storage should have 1 event after cleanup", 1, activeCountAfter)
        
        val remainingEvent = eventsStorage.classCustomUse { it.events[0] }
        assertEquals("Remaining event should be event 2", 2L, remainingEvent.eventId)
    }
    
    @Test
    fun testOrphanedEventWithDifferentInstanceTime() {
        DevLog.info(LOG_TAG, "Running testOrphanedEventWithDifferentInstanceTime")
        
        // Same eventId but different instanceStartTime should NOT be considered orphaned
        val activeEvent = createTestEvent(1, baseTime)
        val dismissedEvent = createTestEvent(1, baseTime + Consts.DAY_IN_MILLISECONDS)
        
        eventsStorage.classCustomUse { it.addEvent(activeEvent) }
        dismissedStorage.classCustomUse { 
            it.addEvent(EventDismissType.ManuallyDismissedFromNotification, baseTime, dismissedEvent)
        }
        
        val activeCountBefore = eventsStorage.classCustomUse { it.events.size }
        assertEquals("Active storage should have 1 event", 1, activeCountBefore)
        
        cleanupOrphanedEvents()
        
        // Active event should NOT be removed because instanceStartTime differs
        val activeCountAfter = eventsStorage.classCustomUse { it.events.size }
        assertEquals("Active storage should still have 1 event", 1, activeCountAfter)
    }
    
    @Test
    fun testOrphanedEventWithSameInstanceTime() {
        DevLog.info(LOG_TAG, "Running testOrphanedEventWithSameInstanceTime")
        
        // Same eventId AND same instanceStartTime IS considered orphaned
        val event = createTestEvent(1, baseTime)
        
        eventsStorage.classCustomUse { it.addEvent(event) }
        dismissedStorage.classCustomUse { 
            it.addEvent(EventDismissType.ManuallyDismissedFromNotification, baseTime, event)
        }
        
        val activeCountBefore = eventsStorage.classCustomUse { it.events.size }
        assertEquals("Active storage should have 1 event", 1, activeCountBefore)
        
        cleanupOrphanedEvents()
        
        // Active event SHOULD be removed because both eventId and instanceStartTime match
        val activeCountAfter = eventsStorage.classCustomUse { it.events.size }
        assertEquals("Active storage should be empty after cleanup", 0, activeCountAfter)
    }
    
    @Test
    fun testEmptyStorages() {
        DevLog.info(LOG_TAG, "Running testEmptyStorages")
        
        // Both storages are already empty from setup
        val activeCount = eventsStorage.classCustomUse { it.events.size }
        val dismissedCount = dismissedStorage.classCustomUse { it.events.size }
        
        assertEquals("Active storage should be empty", 0, activeCount)
        assertEquals("Dismissed storage should be empty", 0, dismissedCount)
        
        // Should not throw on empty storages
        cleanupOrphanedEvents()
        
        val activeCountAfter = eventsStorage.classCustomUse { it.events.size }
        assertEquals("Active storage should still be empty", 0, activeCountAfter)
    }
}

