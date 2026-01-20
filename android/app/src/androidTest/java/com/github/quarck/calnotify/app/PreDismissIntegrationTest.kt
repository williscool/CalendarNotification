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
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import com.github.quarck.calnotify.testutils.TestTimeConstants
import com.github.quarck.calnotify.utils.CNPlusTestClock
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for pre-dismiss functionality (Milestone 2, Phase 6.3).
 * 
 * Tests that pre-dismissed events:
 * - Get marked as handled in MonitorStorage
 * - Appear in DismissedEventsStorage
 * - Smart restore: returns to Upcoming if alert hasn't fired, Active if it has
 * 
 * These tests use real storage implementations to verify end-to-end behavior.
 */
@RunWith(AndroidJUnit4::class)
class PreDismissIntegrationTest {

    private val LOG_TAG = "PreDismissIntegrationTest"
    private lateinit var context: Context
    private val baseTime = TestTimeConstants.STANDARD_TEST_TIME
    private var testEventId = 920000L  // Use high IDs to avoid conflicts
    private lateinit var testClock: CNPlusTestClock

    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up test")
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Set up test clock using MockK
        testClock = CNPlusTestClock(baseTime)
        mockkObject(ApplicationController)
        every { ApplicationController.clock } returns testClock
        
        // Clean up any leftover test data
        cleanupTestData()
    }

    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test")
        cleanupTestData()
        
        // Restore original clock by unmocking
        unmockkObject(ApplicationController)
    }
    
    private fun cleanupTestData() {
        // Clean up events storage
        EventsStorage(context).use { db ->
            db.events.filter { it.title.startsWith("PreDismiss Test") }.forEach {
                db.deleteEvent(it.eventId, it.instanceStartTime)
            }
        }
        
        // Clean up dismissed events storage
        DismissedEventsStorage(context).use { db ->
            db.events.filter { it.event.title.startsWith("PreDismiss Test") }.forEach {
                db.deleteEvent(it.event)
            }
        }
        
        // Clean up monitor storage
        MonitorStorage(context).use { storage ->
            storage.deleteAlertsMatching { it.eventId >= 920000L }
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
        title: String = "PreDismiss Test Event ${alert.eventId}"
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

    // === Pre-dismiss storage tests ===

    @Test
    fun preDismissEvent_marksAlertAsHandled() {
        DevLog.info(LOG_TAG, "Running preDismissEvent_marksAlertAsHandled")
        
        val alert = createTestAlert(wasHandled = false)
        val event = createTestEventRecord(alert, title = "PreDismiss Test - Mark Handled")
        
        // Add alert to MonitorStorage
        MonitorStorage(context).use { storage ->
            storage.addAlert(alert)
        }
        
        // Pre-dismiss the event
        val result = ApplicationController.preDismissEvent(context, event)
        assertTrue("Pre-dismiss should succeed", result)
        
        // Verify alert is marked as handled
        MonitorStorage(context).use { storage ->
            val retrieved = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)
            assertNotNull("Alert should exist", retrieved)
            assertTrue("Alert should be marked as handled", retrieved!!.wasHandled)
        }
    }

    @Test
    fun preDismissEvent_addsToDissmisedStorage() {
        DevLog.info(LOG_TAG, "Running preDismissEvent_addsToDissmisedStorage")
        
        val alert = createTestAlert()
        val event = createTestEventRecord(alert, title = "PreDismiss Test - Add to Dismissed")
        
        // Add alert to MonitorStorage
        MonitorStorage(context).use { storage ->
            storage.addAlert(alert)
        }
        
        // Pre-dismiss the event
        ApplicationController.preDismissEvent(context, event)
        
        // Verify event is in DismissedEventsStorage
        DismissedEventsStorage(context).use { db ->
            val dismissed = db.events.find { it.event.eventId == event.eventId }
            assertNotNull("Event should be in DismissedEventsStorage", dismissed)
        }
    }

    @Test
    fun preDismissEvent_failsWhenAlertNotFound() {
        DevLog.info(LOG_TAG, "Running preDismissEvent_failsWhenAlertNotFound")
        
        val alert = createTestAlert()
        val event = createTestEventRecord(alert, title = "PreDismiss Test - No Alert")
        
        // Don't add alert to MonitorStorage
        
        // Pre-dismiss should fail
        val result = ApplicationController.preDismissEvent(context, event)
        assertFalse("Pre-dismiss should fail when alert not found", result)
        
        // Verify event is NOT in DismissedEventsStorage
        DismissedEventsStorage(context).use { db ->
            val dismissed = db.events.find { it.event.eventId == event.eventId }
            assertNull("Event should NOT be in DismissedEventsStorage", dismissed)
        }
    }

    // === Smart restore tests (critical for swipe-to-dismiss + undo) ===

    @Test
    fun restore_alertTimeInFuture_restoresToUpcoming() {
        DevLog.info(LOG_TAG, "Running restore_alertTimeInFuture_restoresToUpcoming")
        
        // Alert time is 1 hour from now
        val alertTime = baseTime + Consts.HOUR_IN_MILLISECONDS
        val alert = createTestAlert(alertTime = alertTime)
        val event = createTestEventRecord(alert, title = "PreDismiss Test - Restore to Upcoming")
        
        // Add alert to MonitorStorage
        MonitorStorage(context).use { storage ->
            storage.addAlert(alert)
        }
        
        // Pre-dismiss the event
        ApplicationController.preDismissEvent(context, event)
        
        // Verify alert is handled
        MonitorStorage(context).use { storage ->
            val retrieved = storage.getAlert(alert.eventId, alertTime, alert.instanceStartTime)
            assertTrue("Alert should be handled after pre-dismiss", retrieved!!.wasHandled)
        }
        
        // Restore the event (alertTime still in future)
        ApplicationController.restoreEvent(context, event)
        
        // Verify alert's wasHandled is cleared (restored to Upcoming)
        MonitorStorage(context).use { storage ->
            val restored = storage.getAlert(alert.eventId, alertTime, alert.instanceStartTime)
            assertNotNull("Alert should still exist", restored)
            assertFalse("wasHandled should be cleared (back to Upcoming)", restored!!.wasHandled)
        }
        
        // Verify event is NOT in EventsStorage (Active)
        EventsStorage(context).use { db ->
            val active = db.getEvent(event.eventId, event.instanceStartTime)
            assertNull("Event should NOT be in Active events", active)
        }
        
        // Verify event is removed from DismissedEventsStorage
        DismissedEventsStorage(context).use { db ->
            val dismissed = db.events.find { it.event.eventId == event.eventId }
            assertNull("Event should be removed from DismissedEventsStorage", dismissed)
        }
    }

    @Test
    fun restore_alertTimePassed_restoresToActive() {
        DevLog.info(LOG_TAG, "Running restore_alertTimePassed_restoresToActive")
        
        // Alert time is 1 hour from "now" initially
        val alertTime = baseTime + Consts.HOUR_IN_MILLISECONDS
        val alert = createTestAlert(alertTime = alertTime)
        val event = createTestEventRecord(alert, title = "PreDismiss Test - Restore to Active")
        
        // Add alert to MonitorStorage
        MonitorStorage(context).use { storage ->
            storage.addAlert(alert)
        }
        
        // Pre-dismiss the event
        ApplicationController.preDismissEvent(context, event)
        
        // Simulate time passing - advance clock past the alert time
        testClock.setCurrentTime(alertTime + Consts.MINUTE_IN_MILLISECONDS)
        DevLog.info(LOG_TAG, "Advanced clock past alert time")
        
        // Restore the event (alertTime now in past)
        ApplicationController.restoreEvent(context, event)
        
        // Verify event IS in EventsStorage (Active)
        EventsStorage(context).use { db ->
            val active = db.getEvent(event.eventId, event.instanceStartTime)
            assertNotNull("Event should be in Active events after restore", active)
            assertEquals("Event title should match", "PreDismiss Test - Restore to Active", active!!.title)
        }
        
        // Verify event is removed from DismissedEventsStorage
        DismissedEventsStorage(context).use { db ->
            val dismissed = db.events.find { it.event.eventId == event.eventId }
            assertNull("Event should be removed from DismissedEventsStorage", dismissed)
        }
    }

    @Test
    fun restore_alertTimeExactlyNow_restoresToActive() {
        DevLog.info(LOG_TAG, "Running restore_alertTimeExactlyNow_restoresToActive")
        
        // Alert time is exactly at current time
        val alertTime = baseTime + Consts.HOUR_IN_MILLISECONDS
        val alert = createTestAlert(alertTime = alertTime)
        val event = createTestEventRecord(alert, title = "PreDismiss Test - Restore Edge Case")
        
        // Add alert to MonitorStorage
        MonitorStorage(context).use { storage ->
            storage.addAlert(alert)
        }
        
        // Pre-dismiss the event
        ApplicationController.preDismissEvent(context, event)
        
        // Set clock to exactly alertTime
        testClock.setCurrentTime(alertTime)
        DevLog.info(LOG_TAG, "Set clock to exactly alert time")
        
        // Restore the event (alertTime == currentTime means it has fired)
        ApplicationController.restoreEvent(context, event)
        
        // Verify event IS in EventsStorage (Active) - edge case: exactly at alert time = fired
        EventsStorage(context).use { db ->
            val active = db.getEvent(event.eventId, event.instanceStartTime)
            assertNotNull("Event should be in Active events (alertTime == currentTime)", active)
        }
    }

    @Test
    fun swipeDismissAndUndo_fullFlow() {
        DevLog.info(LOG_TAG, "Running swipeDismissAndUndo_fullFlow - simulating swipe to dismiss + undo")
        
        // This test simulates the full flow:
        // 1. User sees event in Upcoming
        // 2. User swipes to dismiss
        // 3. Time passes, alert time fires
        // 4. User hits undo
        // 5. Event should appear in Active (not Upcoming)
        
        val alertTime = baseTime + Consts.HOUR_IN_MILLISECONDS
        val alert = createTestAlert(alertTime = alertTime)
        val event = createTestEventRecord(alert, title = "PreDismiss Test - Full Swipe Flow")
        
        // Setup: Add alert to MonitorStorage (appears in Upcoming)
        MonitorStorage(context).use { storage ->
            storage.addAlert(alert)
        }
        
        // Step 1: User swipes to dismiss (calls preDismissEvent)
        val dismissResult = ApplicationController.preDismissEvent(context, event)
        assertTrue("Pre-dismiss should succeed", dismissResult)
        
        // Verify: Event is now in DismissedEventsStorage, not in Upcoming
        MonitorStorage(context).use { storage ->
            val retrieved = storage.getAlert(alert.eventId, alertTime, alert.instanceStartTime)
            assertTrue("Alert should be handled (dismissed)", retrieved!!.wasHandled)
        }
        
        // Step 2: Time passes - alert time fires
        testClock.setCurrentTime(alertTime + 5 * Consts.MINUTE_IN_MILLISECONDS)
        
        // Step 3: User hits undo (calls restoreEvent)
        ApplicationController.restoreEvent(context, event)
        
        // Verify: Event should be in Active events (since alert time passed)
        EventsStorage(context).use { db ->
            val active = db.getEvent(event.eventId, event.instanceStartTime)
            assertNotNull("Event should be restored to Active after undo (alert time passed)", active)
        }
        
        // Verify: Event should NOT still be in Dismissed
        DismissedEventsStorage(context).use { db ->
            val dismissed = db.events.find { it.event.eventId == event.eventId }
            assertNull("Event should NOT be in DismissedEventsStorage after restore", dismissed)
        }
    }

    // === Additional alarm/notification behavior tests ===

    @Test
    fun preDismiss_blocksAlertFromFiring() {
        DevLog.info(LOG_TAG, "Running preDismiss_blocksAlertFromFiring")
        
        // When an event is pre-dismissed, the wasHandled flag should prevent
        // the alert from being processed when EVENT_REMINDER fires
        
        val alertTime = baseTime + Consts.HOUR_IN_MILLISECONDS
        val alert = createTestAlert(alertTime = alertTime, wasHandled = false)
        val event = createTestEventRecord(alert, title = "PreDismiss Test - Block Alert")
        
        // Add alert to MonitorStorage
        MonitorStorage(context).use { storage ->
            storage.addAlert(alert)
        }
        
        // Pre-dismiss the event
        ApplicationController.preDismissEvent(context, event)
        
        // Verify wasHandled is true - this is what blocks the alert
        MonitorStorage(context).use { storage ->
            val retrieved = storage.getAlert(alert.eventId, alertTime, alert.instanceStartTime)
            assertTrue("wasHandled=true should block alert from firing", retrieved!!.wasHandled)
        }
        
        // Verify event is NOT in Active events (no notification fired)
        EventsStorage(context).use { db ->
            val active = db.getEvent(event.eventId, event.instanceStartTime)
            assertNull("Pre-dismissed event should NOT appear in Active events", active)
        }
    }

    @Test
    fun restoreToUpcoming_allowsAlertToFire() {
        DevLog.info(LOG_TAG, "Running restoreToUpcoming_allowsAlertToFire")
        
        // When restored to upcoming, wasHandled=false allows EVENT_REMINDER to be processed
        
        val alertTime = baseTime + Consts.HOUR_IN_MILLISECONDS
        val alert = createTestAlert(alertTime = alertTime, wasHandled = true)
        val event = createTestEventRecord(alert, title = "PreDismiss Test - Allow Alert")
        
        // Setup: Event was pre-dismissed
        MonitorStorage(context).use { storage ->
            storage.addAlert(alert)
        }
        DismissedEventsStorage(context).use { db ->
            db.addEvent(com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType.ManuallyDismissedFromUpcoming, event)
        }
        
        // Restore to Upcoming (alertTime still in future)
        ApplicationController.restoreEvent(context, event)
        
        // Verify wasHandled is now false - this allows the alert to fire
        MonitorStorage(context).use { storage ->
            val retrieved = storage.getAlert(alert.eventId, alertTime, alert.instanceStartTime)
            assertNotNull("Alert should still exist", retrieved)
            assertFalse("wasHandled=false should allow alert to fire when time comes", retrieved!!.wasHandled)
        }
        
        // Verify event is NOT in Active events yet (alert time hasn't passed)
        EventsStorage(context).use { db ->
            val active = db.getEvent(event.eventId, event.instanceStartTime)
            assertNull("Event should not be in Active until alert time", active)
        }
    }
}
