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

package com.github.quarck.calnotify.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for pre-snooze functionality (Milestone 2, Phase 6.2).
 * 
 * Tests that pre-snoozed events:
 * - Get marked as handled in MonitorStorage
 * - Appear in EventsStorage with snoozedUntil set
 * - Disappear from upcoming events
 * 
 * These tests use real storage implementations to verify end-to-end behavior.
 */
@RunWith(AndroidJUnit4::class)
class PreSnoozeIntegrationTest {

    private val LOG_TAG = "PreSnoozeIntegrationTest"
    private lateinit var context: Context
    private val baseTime = System.currentTimeMillis()
    private var testEventId = 910000L  // Use high IDs to avoid conflicts

    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up test")
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Clean up any leftover test data
        cleanupTestData()
    }

    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test")
        cleanupTestData()
    }
    
    private fun cleanupTestData() {
        // Clean up events storage
        EventsStorage(context).use { db ->
            db.events.filter { it.title.startsWith("PreSnooze Test") }.forEach {
                db.deleteEvent(it.eventId, it.instanceStartTime)
            }
        }
        
        // Clean up monitor storage
        MonitorStorage(context).use { storage ->
            storage.deleteAlertsMatching { it.eventId >= 910000L }
        }
    }

    private fun createTestAlert(
        eventId: Long = testEventId++,
        alertTime: Long = baseTime + Consts.HOUR_IN_MILLISECONDS,  // Alert 1 hour from now
        instanceStartTime: Long = baseTime + 2 * Consts.HOUR_IN_MILLISECONDS,  // Event starts 2 hours from now
        wasHandled: Boolean = false
    ): MonitorEventAlertEntry {
        return MonitorEventAlertEntry(
            eventId = eventId,
            isAllDay = false,
            alertTime = alertTime,
            instanceStartTime = instanceStartTime,
            instanceEndTime = instanceStartTime + Consts.HOUR_IN_MILLISECONDS,
            alertCreatedByUs = false,
            wasHandled = wasHandled,
            flags = 0
        )
    }
    
    private fun createTestEventRecord(
        alert: MonitorEventAlertEntry,
        title: String = "PreSnooze Test Event ${alert.eventId}"
    ): EventAlertRecord {
        return EventAlertRecord(
            calendarId = -1L,  // Use -1L to bypass handled calendars filter
            eventId = alert.eventId,
            isAllDay = alert.isAllDay,
            isRepeating = false,
            alertTime = alert.alertTime,
            notificationId = 0,
            title = title,
            desc = "",
            startTime = alert.instanceStartTime,
            endTime = alert.instanceEndTime,
            instanceStartTime = alert.instanceStartTime,
            instanceEndTime = alert.instanceEndTime,
            location = "",
            lastStatusChangeTime = baseTime,
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0,
            origin = EventOrigin.ProviderManual,
            timeFirstSeen = baseTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None
        )
    }

    // === Pre-snooze storage tests ===

    @Test
    fun preSnoozedEvent_appearsInEventsStorage() {
        DevLog.info(LOG_TAG, "Running preSnoozedEvent_appearsInEventsStorage")
        
        val alert = createTestAlert()
        val event = createTestEventRecord(alert, title = "PreSnooze Test - Should Appear in Events")
        val snoozeUntil = baseTime + 30 * 60 * 1000L  // Snooze for 30 minutes
        
        // Pre-snooze: add event to EventsStorage with snoozedUntil
        val snoozedEvent = event.copy(
            snoozedUntil = snoozeUntil,
            lastStatusChangeTime = baseTime,
            displayStatus = EventDisplayStatus.Hidden
        )
        
        EventsStorage(context).use { db ->
            val success = db.addEvent(snoozedEvent)
            assertTrue("Should successfully add snoozed event", success)
        }
        
        // Verify event is in EventsStorage with correct snoozedUntil
        EventsStorage(context).use { db ->
            val storedEvent = db.getEvent(event.eventId, event.instanceStartTime)
            assertNotNull("Event should be in EventsStorage", storedEvent)
            assertEquals("snoozedUntil should match", snoozeUntil, storedEvent!!.snoozedUntil)
        }
    }

    @Test
    fun preSnoozedEvent_alertMarkedAsHandled() {
        DevLog.info(LOG_TAG, "Running preSnoozedEvent_alertMarkedAsHandled")
        
        val alert = createTestAlert(wasHandled = false)
        
        // Add alert to MonitorStorage
        MonitorStorage(context).use { storage ->
            storage.addAlert(alert)
        }
        
        // Verify alert starts as not handled
        MonitorStorage(context).use { storage ->
            val retrieved = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)
            assertNotNull("Alert should exist", retrieved)
            assertFalse("Alert should start as not handled", retrieved!!.wasHandled)
        }
        
        // Mark as handled (simulating pre-snooze)
        MonitorStorage(context).use { storage ->
            val retrieved = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)
            storage.updateAlert(retrieved!!.copy(wasHandled = true))
        }
        
        // Verify alert is now handled
        MonitorStorage(context).use { storage ->
            val retrieved = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)
            assertTrue("Alert should be marked as handled", retrieved!!.wasHandled)
        }
    }

    @Test
    fun preSnoozedEvent_doesNotAppearInUpcomingAfterHandled() {
        DevLog.info(LOG_TAG, "Running preSnoozedEvent_doesNotAppearInUpcomingAfterHandled")
        
        val alert = createTestAlert(wasHandled = false)
        
        // Add alert to MonitorStorage
        MonitorStorage(context).use { storage ->
            storage.addAlert(alert)
        }
        
        // Verify alert is in upcoming (wasHandled = false)
        MonitorStorage(context).use { storage ->
            val unhandledAlerts = storage.alerts.filter { !it.wasHandled && it.eventId == alert.eventId }
            assertEquals("Should have 1 unhandled alert", 1, unhandledAlerts.size)
        }
        
        // Mark as handled
        MonitorStorage(context).use { storage ->
            val retrieved = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)
            storage.updateAlert(retrieved!!.copy(wasHandled = true))
        }
        
        // Verify alert no longer appears in upcoming (wasHandled = true)
        MonitorStorage(context).use { storage ->
            val unhandledAlerts = storage.alerts.filter { !it.wasHandled && it.eventId == alert.eventId }
            assertEquals("Should have 0 unhandled alerts after marking as handled", 0, unhandledAlerts.size)
        }
    }

    @Test
    fun preSnoozedEvent_preservesEventProperties() {
        DevLog.info(LOG_TAG, "Running preSnoozedEvent_preservesEventProperties")
        
        val alert = createTestAlert()
        val event = createTestEventRecord(alert, title = "PreSnooze Test - Properties")
        val snoozeUntil = baseTime + 15 * 60 * 1000L  // Snooze for 15 minutes
        
        // Set some properties on the event
        event.isMuted = true
        event.location = "Test Location"
        
        // Pre-snooze
        val snoozedEvent = event.copy(
            snoozedUntil = snoozeUntil,
            lastStatusChangeTime = baseTime,
            displayStatus = EventDisplayStatus.Hidden
        )
        
        EventsStorage(context).use { db ->
            db.addEvent(snoozedEvent)
        }
        
        // Verify properties are preserved
        EventsStorage(context).use { db ->
            val storedEvent = db.getEvent(event.eventId, event.instanceStartTime)
            assertNotNull("Event should exist", storedEvent)
            assertEquals("Title should match", "PreSnooze Test - Properties", storedEvent!!.title)
            assertEquals("Location should match", "Test Location", storedEvent.location)
            assertTrue("Muted flag should be preserved", storedEvent.isMuted)
            assertEquals("snoozedUntil should match", snoozeUntil, storedEvent.snoozedUntil)
        }
    }

    @Test
    fun multiplePreSnoozes_independentEvents() {
        DevLog.info(LOG_TAG, "Running multiplePreSnoozes_independentEvents")
        
        val alert1 = createTestAlert()
        val alert2 = createTestAlert()
        val event1 = createTestEventRecord(alert1, title = "PreSnooze Test 1")
        val event2 = createTestEventRecord(alert2, title = "PreSnooze Test 2")
        
        val snooze1 = baseTime + 15 * 60 * 1000L
        val snooze2 = baseTime + 30 * 60 * 1000L
        
        // Pre-snooze both
        EventsStorage(context).use { db ->
            db.addEvent(event1.copy(snoozedUntil = snooze1, lastStatusChangeTime = baseTime))
            db.addEvent(event2.copy(snoozedUntil = snooze2, lastStatusChangeTime = baseTime))
        }
        
        // Verify both have correct snooze times
        EventsStorage(context).use { db ->
            val stored1 = db.getEvent(event1.eventId, event1.instanceStartTime)
            val stored2 = db.getEvent(event2.eventId, event2.instanceStartTime)
            
            assertNotNull("Event 1 should exist", stored1)
            assertNotNull("Event 2 should exist", stored2)
            assertEquals("Event 1 snooze time", snooze1, stored1!!.snoozedUntil)
            assertEquals("Event 2 snooze time", snooze2, stored2!!.snoozedUntil)
        }
    }
}
