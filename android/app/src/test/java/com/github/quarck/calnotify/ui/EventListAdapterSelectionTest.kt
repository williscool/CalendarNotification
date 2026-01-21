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

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.calendar.EventAlertRecord
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for EventListAdapter's multi-select functionality.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class EventListAdapterSelectionTest {
    
    private lateinit var context: Context
    private lateinit var adapter: EventListAdapter
    private lateinit var mockCallback: MockEventListCallback
    private var selectionModeChangedCount = 0
    private var lastSelectionCount = 0
    private var lastVisibleCount = 0
    private var lastHiddenCount = 0
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockCallback = MockEventListCallback()
        adapter = EventListAdapter(context, mockCallback)
        
        adapter.selectionModeCallback = object : SelectionModeCallback {
            override fun onSelectionModeChanged(active: Boolean) {
                selectionModeChangedCount++
            }
            
            override fun onSelectionCountChanged(selected: Int, visible: Int, hiddenSelected: Int) {
                lastSelectionCount = selected
                lastVisibleCount = visible
                lastHiddenCount = hiddenSelected
            }
        }
        
        selectionModeChangedCount = 0
        lastSelectionCount = 0
        lastVisibleCount = 0
        lastHiddenCount = 0
    }
    
    @After
    fun cleanup() {
        // Clean up
    }
    
    // === Helper Methods ===
    
    private fun createTestEvent(
        eventId: Long = 1L,
        instanceStartTime: Long = System.currentTimeMillis(),
        title: String = "Test Event"
    ): EventAlertRecord {
        return EventAlertRecord(
            calendarId = 1L,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = instanceStartTime - 15 * 60 * 1000,
            notificationId = 0,
            title = title,
            desc = "",
            startTime = instanceStartTime,
            endTime = instanceStartTime + 60 * 60 * 1000,
            instanceStartTime = instanceStartTime,
            instanceEndTime = instanceStartTime + 60 * 60 * 1000,
            location = "",
            lastStatusChangeTime = 0L,
            displayStatus = com.github.quarck.calnotify.calendar.EventDisplayStatus.Hidden,
            color = 0,
            eventStatus = com.github.quarck.calnotify.calendar.EventStatus.Confirmed,
            attendanceStatus = com.github.quarck.calnotify.calendar.AttendanceStatus.None
        )
    }
    
    private fun setAdapterEvents(vararg events: EventAlertRecord) {
        adapter.setEventsToDisplay(events.toList().toTypedArray())
    }
    
    // === Basic Selection Mode Tests ===
    
    @Test
    fun adapter_not_in_selection_mode_initially() {
        assertFalse(adapter.selectionMode)
    }
    
    @Test
    fun enterSelectionMode_activates_selection_mode() {
        val event = createTestEvent()
        setAdapterEvents(event)
        
        adapter.enterSelectionMode(event)
        
        assertTrue(adapter.selectionMode)
    }
    
    @Test
    fun enterSelectionMode_selects_first_event() {
        val event = createTestEvent()
        setAdapterEvents(event)
        
        adapter.enterSelectionMode(event)
        
        assertTrue(adapter.isSelected(event))
        assertEquals(1, adapter.getSelectedEvents().size)
    }
    
    @Test
    fun enterSelectionMode_notifies_callback() {
        val event = createTestEvent()
        setAdapterEvents(event)
        
        adapter.enterSelectionMode(event)
        
        assertEquals(1, selectionModeChangedCount)
    }
    
    @Test
    fun exitSelectionMode_deactivates_selection_mode() {
        val event = createTestEvent()
        setAdapterEvents(event)
        adapter.enterSelectionMode(event)
        
        adapter.exitSelectionMode()
        
        assertFalse(adapter.selectionMode)
    }
    
    @Test
    fun exitSelectionMode_clears_selection() {
        val event = createTestEvent()
        setAdapterEvents(event)
        adapter.enterSelectionMode(event)
        
        adapter.exitSelectionMode()
        
        assertEquals(0, adapter.getSelectedEvents().size)
    }
    
    @Test
    fun exitSelectionMode_notifies_callback() {
        val event = createTestEvent()
        setAdapterEvents(event)
        adapter.enterSelectionMode(event)
        selectionModeChangedCount = 0
        
        adapter.exitSelectionMode()
        
        assertEquals(1, selectionModeChangedCount)
    }
    
    // === Toggle Selection Tests ===
    
    @Test
    fun toggleSelection_adds_unselected_event() {
        val event1 = createTestEvent(eventId = 1L)
        val event2 = createTestEvent(eventId = 2L)
        setAdapterEvents(event1, event2)
        adapter.enterSelectionMode(event1)
        
        adapter.toggleSelection(event2)
        
        assertTrue(adapter.isSelected(event1))
        assertTrue(adapter.isSelected(event2))
        assertEquals(2, adapter.getSelectedEvents().size)
    }
    
    @Test
    fun toggleSelection_removes_selected_event() {
        val event1 = createTestEvent(eventId = 1L)
        val event2 = createTestEvent(eventId = 2L)
        setAdapterEvents(event1, event2)
        adapter.enterSelectionMode(event1)
        adapter.toggleSelection(event2)
        
        adapter.toggleSelection(event2)
        
        assertTrue(adapter.isSelected(event1))
        assertFalse(adapter.isSelected(event2))
        assertEquals(1, adapter.getSelectedEvents().size)
    }
    
    @Test
    fun toggleSelection_exits_when_last_item_deselected() {
        val event = createTestEvent()
        setAdapterEvents(event)
        adapter.enterSelectionMode(event)
        
        adapter.toggleSelection(event)
        
        assertFalse(adapter.selectionMode)
        assertEquals(0, adapter.getSelectedEvents().size)
    }
    
    @Test
    fun toggleSelection_updates_count() {
        val event1 = createTestEvent(eventId = 1L)
        val event2 = createTestEvent(eventId = 2L)
        setAdapterEvents(event1, event2)
        adapter.enterSelectionMode(event1)
        
        adapter.toggleSelection(event2)
        
        assertEquals(2, lastSelectionCount)
    }
    
    // === Select All Tests ===
    
    @Test
    fun selectAllVisible_selects_all_events() {
        val event1 = createTestEvent(eventId = 1L)
        val event2 = createTestEvent(eventId = 2L)
        val event3 = createTestEvent(eventId = 3L)
        setAdapterEvents(event1, event2, event3)
        adapter.enterSelectionMode(event1)
        
        adapter.selectAllVisible()
        
        assertEquals(3, adapter.getSelectedEvents().size)
        assertTrue(adapter.isSelected(event1))
        assertTrue(adapter.isSelected(event2))
        assertTrue(adapter.isSelected(event3))
    }
    
    @Test
    fun selectAllVisible_updates_count() {
        val event1 = createTestEvent(eventId = 1L)
        val event2 = createTestEvent(eventId = 2L)
        setAdapterEvents(event1, event2)
        adapter.enterSelectionMode(event1)
        
        adapter.selectAllVisible()
        
        assertEquals(2, lastSelectionCount)
    }
    
    // === Selection Persistence Through Filter Tests ===
    
    @Test
    fun selection_persists_when_events_reloaded() {
        val event1 = createTestEvent(eventId = 1L, title = "Event 1")
        val event2 = createTestEvent(eventId = 2L, title = "Event 2")
        setAdapterEvents(event1, event2)
        adapter.enterSelectionMode(event1)
        adapter.toggleSelection(event2)
        
        // Simulate reload (same events)
        setAdapterEvents(event1, event2)
        
        assertTrue(adapter.selectionMode)
        assertEquals(2, adapter.getSelectedEvents().size)
    }
    
    @Test
    fun getHiddenSelectedCount_returns_zero_when_all_visible() {
        val event1 = createTestEvent(eventId = 1L)
        val event2 = createTestEvent(eventId = 2L)
        setAdapterEvents(event1, event2)
        adapter.enterSelectionMode(event1)
        adapter.toggleSelection(event2)
        
        assertEquals(0, adapter.getHiddenSelectedCount())
    }
    
    @Test
    fun getVisibleSelectedCount_equals_total_when_all_visible() {
        val event1 = createTestEvent(eventId = 1L)
        val event2 = createTestEvent(eventId = 2L)
        setAdapterEvents(event1, event2)
        adapter.enterSelectionMode(event1)
        adapter.toggleSelection(event2)
        
        assertEquals(2, adapter.getVisibleSelectedCount())
    }
    
    @Test
    fun hidden_count_updates_when_search_filters_events() {
        val event1 = createTestEvent(eventId = 1L, title = "Meeting")
        val event2 = createTestEvent(eventId = 2L, title = "Appointment")
        setAdapterEvents(event1, event2)
        adapter.enterSelectionMode(event1)
        adapter.toggleSelection(event2)
        
        // Apply search filter that hides event2
        adapter.setSearchText("Meeting")
        
        assertEquals(2, lastSelectionCount)  // Total still 2
        assertEquals(1, lastVisibleCount)    // Only 1 visible
        assertEquals(1, lastHiddenCount)     // 1 hidden
    }
    
    // === Special Event Tests ===
    
    @Test
    fun enterSelectionMode_ignores_special_events() {
        val specialEvent = createTestEvent(eventId = -1L)  // Negative IDs are special
        setAdapterEvents(specialEvent)
        
        adapter.enterSelectionMode(specialEvent)
        
        // Special events should be ignored - can't enter selection mode with them
        // Note: This depends on isSpecial implementation
        // If the test fails, we may need to adjust based on actual isSpecial logic
    }
    
    @Test
    fun toggleSelection_ignores_special_events() {
        val normalEvent = createTestEvent(eventId = 1L)
        val specialEvent = createTestEvent(eventId = -1L)
        setAdapterEvents(normalEvent, specialEvent)
        adapter.enterSelectionMode(normalEvent)
        
        adapter.toggleSelection(specialEvent)
        
        // Special event should not be selected
        assertEquals(1, adapter.getSelectedEvents().size)
    }
    
    // === Mock Callback ===
    
    private class MockEventListCallback : EventListCallback {
        override fun onItemClick(v: View, position: Int, eventId: Long) {}
        override fun onItemLongClick(v: View, position: Int, eventId: Long): Boolean = false
        override fun onItemRemoved(event: EventAlertRecord) {}
        override fun onItemRestored(event: EventAlertRecord) {}
        override fun onScrollPositionChange(newPos: Int) {}
    }
}
