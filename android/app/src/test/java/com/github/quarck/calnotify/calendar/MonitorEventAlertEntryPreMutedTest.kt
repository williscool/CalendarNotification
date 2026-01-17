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

package com.github.quarck.calnotify.calendar

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MonitorEventAlertEntry preMuted flag functionality.
 * 
 * The preMuted flag allows users to mark upcoming events to fire silently
 * (no sound/vibration) when their notification eventually fires.
 * 
 * Related: Milestone 2, Phase 6.1 (Pre-Mute)
 */
class MonitorEventAlertEntryPreMutedTest {

    private fun createTestAlert(
        eventId: Long = 1L,
        alertTime: Long = System.currentTimeMillis(),
        instanceStartTime: Long = System.currentTimeMillis() + 3600000L,
        instanceEndTime: Long = System.currentTimeMillis() + 7200000L,
        isAllDay: Boolean = false,
        alertCreatedByUs: Boolean = false,
        wasHandled: Boolean = false,
        flags: Long = 0L
    ) = MonitorEventAlertEntry(
        eventId = eventId,
        isAllDay = isAllDay,
        alertTime = alertTime,
        instanceStartTime = instanceStartTime,
        instanceEndTime = instanceEndTime,
        alertCreatedByUs = alertCreatedByUs,
        wasHandled = wasHandled,
        flags = flags
    )

    // === preMuted flag default value tests ===

    @Test
    fun `new alert defaults to preMuted false`() {
        val alert = createTestAlert()
        
        assertFalse("New alerts should default to preMuted = false", alert.preMuted)
    }

    @Test
    fun `alert with flags 0 has preMuted false`() {
        val alert = createTestAlert(flags = 0L)
        
        assertFalse("Alert with flags=0 should have preMuted = false", alert.preMuted)
    }

    // === withPreMuted tests ===

    @Test
    fun `withPreMuted true sets preMuted flag`() {
        val alert = createTestAlert()
        
        val mutedAlert = alert.withPreMuted(true)
        
        assertTrue("withPreMuted(true) should set preMuted = true", mutedAlert.preMuted)
    }

    @Test
    fun `withPreMuted false clears preMuted flag`() {
        val alert = createTestAlert(flags = MonitorEventAlertEntry.PRE_MUTED_FLAG)
        assertTrue("Precondition: alert should start as preMuted", alert.preMuted)
        
        val unmutedAlert = alert.withPreMuted(false)
        
        assertFalse("withPreMuted(false) should set preMuted = false", unmutedAlert.preMuted)
    }

    @Test
    fun `withPreMuted creates copy with same other fields`() {
        val original = createTestAlert(
            eventId = 42L,
            alertTime = 1000L,
            instanceStartTime = 2000L,
            instanceEndTime = 3000L,
            isAllDay = true,
            alertCreatedByUs = true,
            wasHandled = true
        )
        
        val mutedAlert = original.withPreMuted(true)
        
        // preMuted should change
        assertTrue("preMuted should be true", mutedAlert.preMuted)
        
        // All other fields should be unchanged
        assertEquals("eventId should be unchanged", original.eventId, mutedAlert.eventId)
        assertEquals("alertTime should be unchanged", original.alertTime, mutedAlert.alertTime)
        assertEquals("instanceStartTime should be unchanged", original.instanceStartTime, mutedAlert.instanceStartTime)
        assertEquals("instanceEndTime should be unchanged", original.instanceEndTime, mutedAlert.instanceEndTime)
        assertEquals("isAllDay should be unchanged", original.isAllDay, mutedAlert.isAllDay)
        assertEquals("alertCreatedByUs should be unchanged", original.alertCreatedByUs, mutedAlert.alertCreatedByUs)
        assertEquals("wasHandled should be unchanged", original.wasHandled, mutedAlert.wasHandled)
    }

    @Test
    fun `withPreMuted does not modify original alert`() {
        val original = createTestAlert()
        
        original.withPreMuted(true)
        
        assertFalse("Original alert should remain unchanged", original.preMuted)
    }

    // === flags preservation tests ===

    @Test
    fun `withPreMuted preserves other flags when setting true`() {
        // If there are future flags (bits 1, 2, etc.), they should be preserved
        val otherFlag = 2L  // Some hypothetical future flag
        val alert = createTestAlert(flags = otherFlag)
        
        val mutedAlert = alert.withPreMuted(true)
        
        assertTrue("preMuted should be true", mutedAlert.preMuted)
        // Other flag should still be set
        assertTrue("Other flags should be preserved", (mutedAlert.flags and otherFlag) != 0L)
    }

    @Test
    fun `withPreMuted preserves other flags when setting false`() {
        val otherFlag = 2L  // Some hypothetical future flag
        val alert = createTestAlert(flags = MonitorEventAlertEntry.PRE_MUTED_FLAG or otherFlag)
        
        val unmutedAlert = alert.withPreMuted(false)
        
        assertFalse("preMuted should be false", unmutedAlert.preMuted)
        // Other flag should still be set
        assertTrue("Other flags should be preserved", (unmutedAlert.flags and otherFlag) != 0L)
    }

    // === toggle behavior tests ===

    @Test
    fun `preMuted can be toggled multiple times`() {
        val alert = createTestAlert()
        assertFalse("Start: preMuted should be false", alert.preMuted)
        
        val muted = alert.withPreMuted(true)
        assertTrue("After first toggle: preMuted should be true", muted.preMuted)
        
        val unmuted = muted.withPreMuted(false)
        assertFalse("After second toggle: preMuted should be false", unmuted.preMuted)
        
        val mutedAgain = unmuted.withPreMuted(true)
        assertTrue("After third toggle: preMuted should be true", mutedAgain.preMuted)
    }

    // === Different instances independence tests ===

    @Test
    fun `different instances of same event have independent preMuted flags`() {
        val eventId = 42L
        val instance1Start = 1000L
        val instance2Start = 2000L
        
        val instance1 = createTestAlert(
            eventId = eventId,
            instanceStartTime = instance1Start
        ).withPreMuted(true)
        
        val instance2 = createTestAlert(
            eventId = eventId,
            instanceStartTime = instance2Start
        ).withPreMuted(false)
        
        assertTrue("Instance 1 should be preMuted", instance1.preMuted)
        assertFalse("Instance 2 should NOT be preMuted", instance2.preMuted)
    }

    // === PRE_MUTED_FLAG constant tests ===

    @Test
    fun `PRE_MUTED_FLAG is bit 0`() {
        assertEquals("PRE_MUTED_FLAG should be 1 (bit 0)", 1L, MonitorEventAlertEntry.PRE_MUTED_FLAG)
    }

    @Test
    fun `preMuted is true when flags has bit 0 set`() {
        val alert = createTestAlert(flags = 1L)
        
        assertTrue("preMuted should be true when bit 0 is set", alert.preMuted)
    }

    @Test
    fun `preMuted is false when flags has only other bits set`() {
        val alert = createTestAlert(flags = 2L)  // Only bit 1 set, not bit 0
        
        assertFalse("preMuted should be false when only other bits are set", alert.preMuted)
    }
}
