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

import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for FilterState and StatusOption.
 * Tests the filtering logic for event lists (Milestone 3: Filter Pills).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class FilterStateTest {

    // === Helper Functions ===
    
    private fun createEvent(
        snoozedUntil: Long = 0L,
        isMuted: Boolean = false,
        isRepeating: Boolean = false
    ): EventAlertRecord {
        val now = System.currentTimeMillis()
        return EventAlertRecord(
            calendarId = 1L,
            eventId = 1L,
            isAllDay = false,
            isRepeating = isRepeating,
            alertTime = now,
            notificationId = 0,
            title = "Test Event",
            desc = "",
            startTime = now + 3600000,
            endTime = now + 7200000,
            instanceStartTime = now + 3600000,
            instanceEndTime = now + 7200000,
            location = "",
            lastStatusChangeTime = now,
            snoozedUntil = snoozedUntil,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0
        ).apply {
            this.isMuted = isMuted
        }
    }
    
    private fun createActiveEvent() = createEvent(snoozedUntil = 0L)
    private fun createSnoozedEvent() = createEvent(snoozedUntil = System.currentTimeMillis() + 3600000)
    private fun createMutedEvent() = createEvent(isMuted = true)
    private fun createRecurringEvent() = createEvent(isRepeating = true)
    
    // === FilterState.matchesStatus() Tests ===
    
    @Test
    fun `empty filter set matches all events`() {
        val filter = FilterState(statusFilters = emptySet())
        
        assertTrue(filter.matchesStatus(createActiveEvent()))
        assertTrue(filter.matchesStatus(createSnoozedEvent()))
        assertTrue(filter.matchesStatus(createMutedEvent()))
        assertTrue(filter.matchesStatus(createRecurringEvent()))
    }
    
    @Test
    fun `single SNOOZED filter matches only snoozed events`() {
        val filter = FilterState(statusFilters = setOf(StatusOption.SNOOZED))
        
        assertFalse(filter.matchesStatus(createActiveEvent()))
        assertTrue(filter.matchesStatus(createSnoozedEvent()))
        assertFalse(filter.matchesStatus(createMutedEvent()))
        assertFalse(filter.matchesStatus(createRecurringEvent()))
    }
    
    @Test
    fun `single ACTIVE filter matches only active events`() {
        val filter = FilterState(statusFilters = setOf(StatusOption.ACTIVE))
        
        assertTrue(filter.matchesStatus(createActiveEvent()))
        assertFalse(filter.matchesStatus(createSnoozedEvent()))
        // Muted event with snoozedUntil=0 is also "active" (not snoozed)
        assertTrue(filter.matchesStatus(createMutedEvent()))
        assertTrue(filter.matchesStatus(createRecurringEvent()))
    }
    
    @Test
    fun `single MUTED filter matches only muted events`() {
        val filter = FilterState(statusFilters = setOf(StatusOption.MUTED))
        
        assertFalse(filter.matchesStatus(createActiveEvent()))
        assertFalse(filter.matchesStatus(createSnoozedEvent()))
        assertTrue(filter.matchesStatus(createMutedEvent()))
        assertFalse(filter.matchesStatus(createRecurringEvent()))
    }
    
    @Test
    fun `single RECURRING filter matches only recurring events`() {
        val filter = FilterState(statusFilters = setOf(StatusOption.RECURRING))
        
        assertFalse(filter.matchesStatus(createActiveEvent()))
        assertFalse(filter.matchesStatus(createSnoozedEvent()))
        assertFalse(filter.matchesStatus(createMutedEvent()))
        assertTrue(filter.matchesStatus(createRecurringEvent()))
    }
    
    @Test
    fun `multi-select uses OR logic - SNOOZED or MUTED`() {
        val filter = FilterState(statusFilters = setOf(StatusOption.SNOOZED, StatusOption.MUTED))
        
        assertFalse(filter.matchesStatus(createActiveEvent()))
        assertTrue(filter.matchesStatus(createSnoozedEvent()))
        assertTrue(filter.matchesStatus(createMutedEvent()))
        assertFalse(filter.matchesStatus(createRecurringEvent()))
    }
    
    @Test
    fun `multi-select uses OR logic - ACTIVE or RECURRING`() {
        val filter = FilterState(statusFilters = setOf(StatusOption.ACTIVE, StatusOption.RECURRING))
        
        assertTrue(filter.matchesStatus(createActiveEvent()))
        assertFalse(filter.matchesStatus(createSnoozedEvent()))
        assertTrue(filter.matchesStatus(createMutedEvent())) // muted but not snoozed = active
        assertTrue(filter.matchesStatus(createRecurringEvent()))
    }
    
    @Test
    fun `multi-select all options matches all events`() {
        val filter = FilterState(statusFilters = StatusOption.entries.toSet())
        
        assertTrue(filter.matchesStatus(createActiveEvent()))
        assertTrue(filter.matchesStatus(createSnoozedEvent()))
        assertTrue(filter.matchesStatus(createMutedEvent()))
        assertTrue(filter.matchesStatus(createRecurringEvent()))
    }
    
    // === StatusOption.matches() Tests ===
    
    @Test
    fun `StatusOption SNOOZED matches event with snoozedUntil greater than 0`() {
        val snoozedEvent = createEvent(snoozedUntil = System.currentTimeMillis() + 1000)
        val notSnoozedEvent = createEvent(snoozedUntil = 0L)
        
        assertTrue(StatusOption.SNOOZED.matches(snoozedEvent))
        assertFalse(StatusOption.SNOOZED.matches(notSnoozedEvent))
    }
    
    @Test
    fun `StatusOption ACTIVE matches event with snoozedUntil equal to 0`() {
        val activeEvent = createEvent(snoozedUntil = 0L)
        val snoozedEvent = createEvent(snoozedUntil = System.currentTimeMillis() + 1000)
        
        assertTrue(StatusOption.ACTIVE.matches(activeEvent))
        assertFalse(StatusOption.ACTIVE.matches(snoozedEvent))
    }
    
    @Test
    fun `StatusOption MUTED matches event with isMuted true`() {
        val mutedEvent = createEvent(isMuted = true)
        val notMutedEvent = createEvent(isMuted = false)
        
        assertTrue(StatusOption.MUTED.matches(mutedEvent))
        assertFalse(StatusOption.MUTED.matches(notMutedEvent))
    }
    
    @Test
    fun `StatusOption RECURRING matches event with isRepeating true`() {
        val recurringEvent = createEvent(isRepeating = true)
        val notRecurringEvent = createEvent(isRepeating = false)
        
        assertTrue(StatusOption.RECURRING.matches(recurringEvent))
        assertFalse(StatusOption.RECURRING.matches(notRecurringEvent))
    }
    
    // === Edge Cases ===
    
    @Test
    fun `event can match multiple status options`() {
        // Snoozed AND muted event
        val snoozedMutedEvent = createEvent(
            snoozedUntil = System.currentTimeMillis() + 1000,
            isMuted = true
        )
        
        assertTrue(StatusOption.SNOOZED.matches(snoozedMutedEvent))
        assertTrue(StatusOption.MUTED.matches(snoozedMutedEvent))
        assertFalse(StatusOption.ACTIVE.matches(snoozedMutedEvent))
        assertFalse(StatusOption.RECURRING.matches(snoozedMutedEvent))
    }
    
    @Test
    fun `recurring muted active event matches multiple filters`() {
        val event = createEvent(
            snoozedUntil = 0L,
            isMuted = true,
            isRepeating = true
        )
        
        assertTrue(StatusOption.ACTIVE.matches(event))
        assertTrue(StatusOption.MUTED.matches(event))
        assertTrue(StatusOption.RECURRING.matches(event))
        assertFalse(StatusOption.SNOOZED.matches(event))
    }
    
    // === FilterState Data Class Tests ===
    
    @Test
    fun `FilterState default constructor creates empty filters`() {
        val filter = FilterState()
        
        assertTrue(filter.statusFilters.isEmpty())
        assertTrue(filter.selectedCalendarIds.isEmpty())
    }
    
    @Test
    fun `FilterState copy preserves other fields when updating statusFilters`() {
        val original = FilterState(
            selectedCalendarIds = setOf(1L, 2L),
            statusFilters = setOf(StatusOption.SNOOZED)
        )
        
        val updated = original.copy(statusFilters = setOf(StatusOption.MUTED))
        
        assertEquals(setOf(1L, 2L), updated.selectedCalendarIds)
        assertEquals(setOf(StatusOption.MUTED), updated.statusFilters)
    }
}
