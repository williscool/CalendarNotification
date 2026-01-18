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

package com.github.quarck.calnotify.testutils

import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for preMuted flag storage operations in MockMonitorStorage.
 * 
 * These tests verify that the preMuted flag is correctly persisted
 * through add/update/get operations on MonitorStorage.
 * 
 * Uses Robolectric because MockMonitorStorage uses DevLog which requires Android APIs.
 * 
 * Related: Milestone 2, Phase 6.1 (Pre-Mute)
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class MockMonitorStoragePreMutedTest {

    private lateinit var storage: MockMonitorStorage

    @Before
    fun setup() {
        storage = MockMonitorStorage()
    }

    @After
    fun cleanup() {
        storage.clear()
        storage.close()
    }

    private fun createTestAlert(
        eventId: Long = 1L,
        alertTime: Long = 1000L,
        instanceStartTime: Long = 2000L,
        preMuted: Boolean = false
    ): MonitorEventAlertEntry {
        val flags = if (preMuted) MonitorEventAlertEntry.PRE_MUTED_FLAG else 0L
        return MonitorEventAlertEntry(
            eventId = eventId,
            isAllDay = false,
            alertTime = alertTime,
            instanceStartTime = instanceStartTime,
            instanceEndTime = instanceStartTime + 3600000L,
            alertCreatedByUs = false,
            wasHandled = false,
            flags = flags
        )
    }

    // === addAlert + getAlert round-trip tests ===

    @Test
    fun `addAlert preserves preMuted false`() {
        val alert = createTestAlert(preMuted = false)
        
        storage.addAlert(alert)
        val retrieved = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)
        
        assertNotNull("Alert should be retrievable", retrieved)
        assertFalse("preMuted should remain false", retrieved!!.preMuted)
    }

    @Test
    fun `addAlert preserves preMuted true`() {
        val alert = createTestAlert(preMuted = true)
        
        storage.addAlert(alert)
        val retrieved = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)
        
        assertNotNull("Alert should be retrievable", retrieved)
        assertTrue("preMuted should remain true", retrieved!!.preMuted)
    }

    // === updateAlert tests ===

    @Test
    fun `updateAlert with preMuted true persists flag`() {
        val alert = createTestAlert(preMuted = false)
        storage.addAlert(alert)
        
        // Update to set preMuted = true
        val updatedAlert = alert.withPreMuted(true)
        storage.updateAlert(updatedAlert)
        
        val retrieved = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)
        assertNotNull("Alert should be retrievable", retrieved)
        assertTrue("preMuted should be true after update", retrieved!!.preMuted)
    }

    @Test
    fun `updateAlert with preMuted false clears flag`() {
        val alert = createTestAlert(preMuted = true)
        storage.addAlert(alert)
        
        // Update to set preMuted = false
        val updatedAlert = alert.withPreMuted(false)
        storage.updateAlert(updatedAlert)
        
        val retrieved = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)
        assertNotNull("Alert should be retrievable", retrieved)
        assertFalse("preMuted should be false after update", retrieved!!.preMuted)
    }

    // === preMuted flag survives storage round-trip test ===

    @Test
    fun `preMuted flag survives multiple operations`() {
        val eventId = 42L
        val alertTime = 1000L
        val instanceStart = 2000L
        
        // Create and add with preMuted = false
        val alert = createTestAlert(
            eventId = eventId, 
            alertTime = alertTime, 
            instanceStartTime = instanceStart,
            preMuted = false
        )
        storage.addAlert(alert)
        
        // Toggle to preMuted = true
        val muted = storage.getAlert(eventId, alertTime, instanceStart)!!.withPreMuted(true)
        storage.updateAlert(muted)
        
        // Verify it persisted
        val retrieved1 = storage.getAlert(eventId, alertTime, instanceStart)
        assertTrue("preMuted should be true after first update", retrieved1!!.preMuted)
        
        // Toggle back to preMuted = false
        val unmuted = retrieved1.withPreMuted(false)
        storage.updateAlert(unmuted)
        
        // Verify it persisted
        val retrieved2 = storage.getAlert(eventId, alertTime, instanceStart)
        assertFalse("preMuted should be false after second update", retrieved2!!.preMuted)
    }

    // === Different instances have independent flags ===

    @Test
    fun `different instances of same event have independent preMuted flags`() {
        val eventId = 42L
        val alertTime = 1000L
        val instance1Start = 2000L
        val instance2Start = 3000L
        
        // Add instance 1 with preMuted = true
        val alert1 = createTestAlert(
            eventId = eventId,
            alertTime = alertTime,
            instanceStartTime = instance1Start,
            preMuted = true
        )
        storage.addAlert(alert1)
        
        // Add instance 2 with preMuted = false
        val alert2 = createTestAlert(
            eventId = eventId,
            alertTime = alertTime,
            instanceStartTime = instance2Start,
            preMuted = false
        )
        storage.addAlert(alert2)
        
        // Verify each instance has its own flag
        val retrieved1 = storage.getAlert(eventId, alertTime, instance1Start)
        val retrieved2 = storage.getAlert(eventId, alertTime, instance2Start)
        
        assertTrue("Instance 1 should be preMuted", retrieved1!!.preMuted)
        assertFalse("Instance 2 should NOT be preMuted", retrieved2!!.preMuted)
    }

    // === Bulk operations ===

    @Test
    fun `addAlerts preserves preMuted flags for all alerts`() {
        val alerts = listOf(
            createTestAlert(eventId = 1, preMuted = true),
            createTestAlert(eventId = 2, preMuted = false),
            createTestAlert(eventId = 3, preMuted = true)
        )
        
        storage.addAlerts(alerts)
        
        assertTrue("Alert 1 should be preMuted", 
            storage.getAlert(1, 1000L, 2000L)!!.preMuted)
        assertFalse("Alert 2 should NOT be preMuted", 
            storage.getAlert(2, 1000L, 2000L)!!.preMuted)
        assertTrue("Alert 3 should be preMuted", 
            storage.getAlert(3, 1000L, 2000L)!!.preMuted)
    }

    @Test
    fun `updateAlerts preserves preMuted flags for all alerts`() {
        // Add alerts with preMuted = false
        val alerts = listOf(
            createTestAlert(eventId = 1, preMuted = false),
            createTestAlert(eventId = 2, preMuted = false),
            createTestAlert(eventId = 3, preMuted = false)
        )
        storage.addAlerts(alerts)
        
        // Update alerts 1 and 3 to preMuted = true
        val updatedAlerts = listOf(
            storage.getAlert(1, 1000L, 2000L)!!.withPreMuted(true),
            storage.getAlert(3, 1000L, 2000L)!!.withPreMuted(true)
        )
        storage.updateAlerts(updatedAlerts)
        
        assertTrue("Alert 1 should be preMuted after update", 
            storage.getAlert(1, 1000L, 2000L)!!.preMuted)
        assertFalse("Alert 2 should still NOT be preMuted (not updated)", 
            storage.getAlert(2, 1000L, 2000L)!!.preMuted)
        assertTrue("Alert 3 should be preMuted after update", 
            storage.getAlert(3, 1000L, 2000L)!!.preMuted)
    }

    // === Query methods include preMuted flag ===

    @Test
    fun `getAlertsForAlertRange returns alerts with preMuted flag`() {
        val alert = createTestAlert(alertTime = 500L, preMuted = true)
        storage.addAlert(alert)
        
        val alerts = storage.getAlertsForAlertRange(0L, 1000L)
        
        assertEquals("Should return 1 alert", 1, alerts.size)
        assertTrue("Returned alert should have preMuted = true", alerts[0].preMuted)
    }

    @Test
    fun `alerts property returns alerts with preMuted flag`() {
        storage.addAlert(createTestAlert(eventId = 1, preMuted = true))
        storage.addAlert(createTestAlert(eventId = 2, preMuted = false))
        
        val allAlerts = storage.alerts
        
        assertEquals("Should return 2 alerts", 2, allAlerts.size)
        val alert1 = allAlerts.find { it.eventId == 1L }
        val alert2 = allAlerts.find { it.eventId == 2L }
        assertTrue("Alert 1 should be preMuted", alert1!!.preMuted)
        assertFalse("Alert 2 should NOT be preMuted", alert2!!.preMuted)
    }
}
