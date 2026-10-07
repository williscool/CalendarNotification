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
import com.github.quarck.calnotify.testutils.TestTimeConstants
import com.github.quarck.calnotify.utils.CNPlusTestClock
import com.github.quarck.calnotify.app.ApplicationController
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for pre-mute functionality (Milestone 2, Phase 6.1).
 * 
 * Tests that the preMuted flag in MonitorStorage is correctly applied
 * when events fire via ApplicationController.registerNewEvents().
 * 
 * These tests use real storage implementations to verify end-to-end behavior.
 */
@RunWith(AndroidJUnit4::class)
class PreMuteIntegrationTest {

    private val LOG_TAG = "PreMuteIntegrationTest"
    private lateinit var context: Context
    private val baseTime = TestTimeConstants.STANDARD_TEST_TIME
    private var testEventId = 900000L  // Use high IDs to avoid conflicts
    private lateinit var testClock: CNPlusTestClock

    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up test")
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Set up test clock for deterministic time
        testClock = CNPlusTestClock(baseTime)
        ApplicationController.clockProvider = { testClock }
        
        // Clean up any leftover test data
        cleanupTestData()
    }

    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test")
        
        // Reset clock provider
        ApplicationController.resetClockProvider()
        
        cleanupTestData()
    }
    
    private fun cleanupTestData() {
        // Clean up events storage
        EventsStorage(context).use { db ->
            db.events.filter { it.title.startsWith("PreMute Test") }.forEach {
                db.deleteEvent(it.eventId, it.instanceStartTime)
            }
        }
        
        // Clean up monitor storage
        MonitorStorage(context).use { storage ->
            storage.deleteAlertsMatching { it.eventId >= 900000L }
        }
    }

    private fun createTestAlert(
        eventId: Long = testEventId++,
        alertTime: Long = baseTime,
        instanceStartTime: Long = baseTime + Consts.HOUR_IN_MILLISECONDS,
        preMuted: Boolean = false
    ): MonitorEventAlertEntry {
        val flags = if (preMuted) MonitorEventAlertEntry.PRE_MUTED_FLAG else 0L
        return MonitorEventAlertEntry(
            eventId = eventId,
            isAllDay = false,
            alertTime = alertTime,
            instanceStartTime = instanceStartTime,
            instanceEndTime = instanceStartTime + Consts.HOUR_IN_MILLISECONDS,
            alertCreatedByUs = false,
            wasHandled = false,
            flags = flags
        )
    }
    
    private fun createTestEventRecord(
        alert: MonitorEventAlertEntry,
        title: String = "PreMute Test Event ${alert.eventId}"
    ): EventAlertRecord {
        return EventAlertRecord(
            calendarId = -1L,  // Use -1L to bypass handled calendars filter in registerNewEvents
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

    // === preMuted flag persistence tests ===

    @Test
    fun preMutedFlag_persistsInMonitorStorage() {
        DevLog.info(LOG_TAG, "Running preMutedFlag_persistsInMonitorStorage")
        
        // Create an alert with preMuted = true
        val alert = createTestAlert(preMuted = true)
        
        // Add to storage
        MonitorStorage(context).use { storage ->
            storage.addAlert(alert)
        }
        
        // Retrieve and verify
        MonitorStorage(context).use { storage ->
            val retrieved = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)
            assertNotNull("Alert should exist in storage", retrieved)
            assertTrue("preMuted flag should be true", retrieved!!.preMuted)
        }
    }

    @Test
    fun preMutedFlag_canBeSetViaUpdate() {
        DevLog.info(LOG_TAG, "Running preMutedFlag_canBeSetViaUpdate")
        
        // Create an alert with preMuted = false
        val alert = createTestAlert(preMuted = false)
        
        // Add to storage
        MonitorStorage(context).use { storage ->
            storage.addAlert(alert)
        }
        
        // Update to preMuted = true
        MonitorStorage(context).use { storage ->
            val retrieved = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)
            assertNotNull("Alert should exist", retrieved)
            storage.updateAlert(retrieved!!.withPreMuted(true))
        }
        
        // Verify the update persisted
        MonitorStorage(context).use { storage ->
            val retrieved = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)
            assertTrue("preMuted flag should be true after update", retrieved!!.preMuted)
        }
    }

    @Test
    fun preMutedFlag_canBeClearedViaUpdate() {
        DevLog.info(LOG_TAG, "Running preMutedFlag_canBeClearedViaUpdate")
        
        // Create an alert with preMuted = true
        val alert = createTestAlert(preMuted = true)
        
        // Add to storage
        MonitorStorage(context).use { storage ->
            storage.addAlert(alert)
        }
        
        // Update to preMuted = false
        MonitorStorage(context).use { storage ->
            val retrieved = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)
            assertNotNull("Alert should exist", retrieved)
            storage.updateAlert(retrieved!!.withPreMuted(false))
        }
        
        // Verify the update persisted
        MonitorStorage(context).use { storage ->
            val retrieved = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)
            assertFalse("preMuted flag should be false after update", retrieved!!.preMuted)
        }
    }

    // === registerNewEvents integration tests ===

    @Test
    fun registerNewEvents_appliesPreMutedFlag() {
        DevLog.info(LOG_TAG, "Running registerNewEvents_appliesPreMutedFlag")
        
        // Create a pre-muted alert
        val alert = createTestAlert(preMuted = true)
        val event = createTestEventRecord(alert, title = "PreMute Test Event - Should Be Muted")
        
        // Verify event starts unmuted
        assertFalse("Event should start unmuted", event.isMuted)
        
        // Call registerNewEvents with the pair
        val pairs = listOf(Pair(alert, event))
        val result = ApplicationController.registerNewEvents(context, pairs)
        
        // Verify the event was added (returned in result)
        assertEquals("Should have 1 event registered", 1, result.size)
        
        // Check the event in storage has isMuted = true
        EventsStorage(context).use { db ->
            val storedEvent = db.getEvent(event.eventId, event.instanceStartTime)
            assertNotNull("Event should be in storage", storedEvent)
            assertTrue("Event in storage should be muted (from preMuted flag)", storedEvent!!.isMuted)
        }
    }

    @Test
    fun registerNewEvents_doesNotMuteWhenNotPreMuted() {
        DevLog.info(LOG_TAG, "Running registerNewEvents_doesNotMuteWhenNotPreMuted")
        
        // Create an alert without preMuted flag
        val alert = createTestAlert(preMuted = false)
        val event = createTestEventRecord(alert, title = "PreMute Test Event - Should Not Be Muted")
        
        // Verify event starts unmuted
        assertFalse("Event should start unmuted", event.isMuted)
        
        // Call registerNewEvents with the pair
        val pairs = listOf(Pair(alert, event))
        val result = ApplicationController.registerNewEvents(context, pairs)
        
        // Verify the event was added
        assertEquals("Should have 1 event registered", 1, result.size)
        
        // Check the event in storage is NOT muted
        EventsStorage(context).use { db ->
            val storedEvent = db.getEvent(event.eventId, event.instanceStartTime)
            assertNotNull("Event should be in storage", storedEvent)
            assertFalse("Event in storage should NOT be muted", storedEvent!!.isMuted)
        }
    }

    @Test
    fun registerNewEvents_preMutedDoesNotOverrideExistingMute() {
        DevLog.info(LOG_TAG, "Running registerNewEvents_preMutedDoesNotOverrideExistingMute")
        
        // Create an alert without preMuted, but event already muted (e.g., #mute tag)
        val alert = createTestAlert(preMuted = false)
        val event = createTestEventRecord(alert, title = "PreMute Test Event - Already Muted")
        event.isMuted = true  // Simulate already muted via #mute tag
        
        // Call registerNewEvents with the pair
        val pairs = listOf(Pair(alert, event))
        ApplicationController.registerNewEvents(context, pairs)
        
        // Check the event in storage is still muted
        EventsStorage(context).use { db ->
            val storedEvent = db.getEvent(event.eventId, event.instanceStartTime)
            assertNotNull("Event should be in storage", storedEvent)
            assertTrue("Event should remain muted", storedEvent!!.isMuted)
        }
    }

    @Test
    fun registerNewEvents_multipleEvents_independentMuteFlags() {
        DevLog.info(LOG_TAG, "Running registerNewEvents_multipleEvents_independentMuteFlags")
        
        // Create multiple alerts with different preMuted states
        val alert1 = createTestAlert(preMuted = true)
        val alert2 = createTestAlert(preMuted = false)
        val alert3 = createTestAlert(preMuted = true)
        
        val event1 = createTestEventRecord(alert1, title = "PreMute Test Event 1 - Muted")
        val event2 = createTestEventRecord(alert2, title = "PreMute Test Event 2 - Not Muted")
        val event3 = createTestEventRecord(alert3, title = "PreMute Test Event 3 - Muted")
        
        val pairs = listOf(
            Pair(alert1, event1),
            Pair(alert2, event2),
            Pair(alert3, event3)
        )
        
        ApplicationController.registerNewEvents(context, pairs)
        
        // Verify each event has correct mute state
        EventsStorage(context).use { db ->
            val stored1 = db.getEvent(event1.eventId, event1.instanceStartTime)
            val stored2 = db.getEvent(event2.eventId, event2.instanceStartTime)
            val stored3 = db.getEvent(event3.eventId, event3.instanceStartTime)
            
            assertTrue("Event 1 should be muted", stored1!!.isMuted)
            assertFalse("Event 2 should NOT be muted", stored2!!.isMuted)
            assertTrue("Event 3 should be muted", stored3!!.isMuted)
        }
    }
}
