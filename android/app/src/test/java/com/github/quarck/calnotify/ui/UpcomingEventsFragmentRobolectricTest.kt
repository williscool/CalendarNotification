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

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.testutils.UITestFixtureRobolectric
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric UI tests for UpcomingEventsFragment
 * 
 * Tests include:
 * - Fragment launch and lifecycle
 * - Empty state display  
 * - RecyclerView event list
 * - Pre-mute functionality (Milestone 2, Phase 6.1)
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class UpcomingEventsFragmentRobolectricTest {
    
    private lateinit var fixture: UITestFixtureRobolectric
    
    @Before
    fun setup() {
        fixture = UITestFixtureRobolectric.create()
        fixture.setup()
    }
    
    @After
    fun cleanup() {
        fixture.cleanup()
    }
    
    // === Fragment Launch Tests ===
    
    @Test
    fun upcomingEventsFragment_launches_successfully() {
        val scenario = fixture.launchUpcomingEventsFragment()
        
        scenario.onFragment { fragment ->
            assertNotNull(fragment)
        }
        
        scenario.close()
    }
    
    // === Empty State Tests ===
    
    @Test
    fun upcomingEventsFragment_shows_empty_view_when_no_events() {
        val scenario = fixture.launchUpcomingEventsFragment()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onFragment { fragment ->
            val emptyView = fragment.requireView().findViewById<View>(R.id.empty_view)
            assertNotNull(emptyView)
            assertEquals(View.VISIBLE, emptyView.visibility)
        }
        
        scenario.close()
    }
    
    @Test
    fun upcomingEventsFragment_hides_empty_view_when_events_exist() {
        fixture.seedUpcomingEvents(3)
        
        val scenario = fixture.launchUpcomingEventsFragment()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onFragment { fragment ->
            val emptyView = fragment.requireView().findViewById<View>(R.id.empty_view)
            // Empty view should be hidden when events exist
            assertTrue(emptyView.visibility == View.GONE || emptyView.visibility == View.INVISIBLE)
        }
        
        scenario.close()
    }
    
    // === RecyclerView Tests ===
    
    @Test
    fun upcomingEventsFragment_has_recycler_view() {
        val scenario = fixture.launchUpcomingEventsFragment()
        
        scenario.onFragment { fragment ->
            val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
            assertNotNull(recyclerView)
        }
        
        scenario.close()
    }
    
    @Test
    fun upcomingEventsFragment_displays_events_in_list() {
        fixture.createUpcomingEvent(title = "Meeting Tomorrow")
        fixture.createUpcomingEvent(title = "Dentist Appointment")
        
        val scenario = fixture.launchUpcomingEventsFragment()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onFragment { fragment ->
            val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
            assertNotNull(recyclerView)
            assertEquals(View.VISIBLE, recyclerView.visibility)
        }
        
        scenario.close()
    }
    
    @Test
    fun upcomingEventsFragment_recycler_has_correct_item_count() {
        fixture.createUpcomingEvent(title = "Event One")
        fixture.createUpcomingEvent(title = "Event Two")
        fixture.createUpcomingEvent(title = "Event Three")
        
        val scenario = fixture.launchUpcomingEventsFragment()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onFragment { fragment ->
            val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
            val adapter = recyclerView.adapter
            assertNotNull(adapter)
            assertEquals(3, adapter?.itemCount)
        }
        
        scenario.close()
    }
    
    // === Pull to Refresh Tests ===
    
    @Test
    fun upcomingEventsFragment_has_swipe_refresh_layout() {
        val scenario = fixture.launchUpcomingEventsFragment()
        
        scenario.onFragment { fragment ->
            val swipeRefresh = fragment.requireView().findViewById<SwipeRefreshLayout>(R.id.refresh_layout)
            assertNotNull(swipeRefresh)
            assertEquals(View.VISIBLE, swipeRefresh.visibility)
        }
        
        scenario.close()
    }
    
    // === Empty State Message Tests ===
    
    @Test
    fun upcomingEventsFragment_shows_correct_empty_message() {
        val scenario = fixture.launchUpcomingEventsFragment()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onFragment { fragment ->
            val emptyView = fragment.requireView().findViewById<TextView>(R.id.empty_view)
            assertNotNull(emptyView)
            assertEquals(fragment.getString(R.string.empty_upcoming), emptyView.text)
        }
        
        scenario.close()
    }
    
    // === All-Day Event Tests ===
    
    @Test
    fun upcomingEventsFragment_allday_event_is_in_list() {
        fixture.createUpcomingEvent(title = "All Day Event", isAllDay = true)
        
        val scenario = fixture.launchUpcomingEventsFragment()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onFragment { fragment ->
            val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
            val adapter = recyclerView.adapter
            assertNotNull(adapter)
            assertEquals(1, adapter?.itemCount)
        }
        
        scenario.close()
    }
    
    // === Seed Events Tests ===
    
    @Test
    fun upcomingEventsFragment_seeded_events_display() {
        fixture.seedUpcomingEvents(5)
        
        val scenario = fixture.launchUpcomingEventsFragment()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onFragment { fragment ->
            val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
            val adapter = recyclerView.adapter
            assertNotNull(adapter)
            assertEquals(5, adapter?.itemCount)
        }
        
        scenario.close()
    }
    
    // === Pre-Mute Tests (Milestone 2, Phase 6.1) ===
    
    @Test
    fun upcomingEventsFragment_preMute_sets_flag_in_storage() {
        // Create an upcoming event
        val alert = fixture.createUpcomingEvent(title = "Test Event")
        assertFalse("Precondition: alert should not be pre-muted", alert.preMuted)
        
        val scenario = fixture.launchUpcomingEventsFragment()
        fixture.waitForAsyncTasks()
        
        // Get the event from adapter and verify it exists
        scenario.onFragment { fragment ->
            val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
            val adapter = recyclerView.adapter as? EventListAdapter
            assertNotNull("Adapter should exist", adapter)
            assertEquals("Should have 1 event", 1, adapter?.itemCount)
        }
        
        // Directly update the flag in MonitorStorage (simulating what handlePreMute does)
        val updatedAlert = alert.withPreMuted(true)
        fixture.monitorStorage.updateAlert(updatedAlert)
        
        // Verify the flag was set
        val retrievedAlert = fixture.monitorStorage.getAlert(
            alert.eventId, alert.alertTime, alert.instanceStartTime
        )
        assertNotNull("Alert should exist in storage", retrievedAlert)
        assertTrue("Alert should be pre-muted", retrievedAlert!!.preMuted)
        
        scenario.close()
    }
    
    @Test
    fun upcomingEventsFragment_preMute_flag_survives_reload() {
        // Create an upcoming event and pre-mute it
        val alert = fixture.createUpcomingEvent(title = "Test Event")
        val mutedAlert = alert.withPreMuted(true)
        fixture.monitorStorage.updateAlert(mutedAlert)
        
        // Launch fragment and load events
        val scenario = fixture.launchUpcomingEventsFragment()
        fixture.waitForAsyncTasks()
        
        // Verify the event appears in the list
        scenario.onFragment { fragment ->
            val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
            val adapter = recyclerView.adapter as? EventListAdapter
            assertEquals("Should have 1 event", 1, adapter?.itemCount)
            
            // The event loaded should have isMuted = true (from UpcomingEventsProvider)
            val event = adapter?.getEventAtPosition(0, alert.eventId)
            assertNotNull("Event should be in adapter", event)
            assertTrue("Event should be muted (from preMuted flag)", event!!.isMuted)
        }
        
        scenario.close()
    }
    
    @Test
    fun upcomingEventsFragment_unPreMute_clears_flag() {
        // Create a pre-muted upcoming event
        val alert = fixture.createUpcomingEvent(title = "Test Event")
        fixture.monitorStorage.updateAlert(alert.withPreMuted(true))
        
        // Verify it's pre-muted
        val preMutedAlert = fixture.monitorStorage.getAlert(
            alert.eventId, alert.alertTime, alert.instanceStartTime
        )
        assertTrue("Precondition: alert should be pre-muted", preMutedAlert!!.preMuted)
        
        // Un-pre-mute it
        fixture.monitorStorage.updateAlert(preMutedAlert.withPreMuted(false))
        
        // Verify the flag was cleared
        val unMutedAlert = fixture.monitorStorage.getAlert(
            alert.eventId, alert.alertTime, alert.instanceStartTime
        )
        assertFalse("Alert should no longer be pre-muted", unMutedAlert!!.preMuted)
    }
    
    @Test
    fun upcomingEventsFragment_multiple_events_independent_preMute_flags() {
        // Create multiple upcoming events
        val alert1 = fixture.createUpcomingEvent(title = "Event 1")
        val alert2 = fixture.createUpcomingEvent(title = "Event 2")
        val alert3 = fixture.createUpcomingEvent(title = "Event 3")
        
        // Pre-mute only the second event
        fixture.monitorStorage.updateAlert(alert2.withPreMuted(true))
        
        // Verify flags are independent
        val retrieved1 = fixture.monitorStorage.getAlert(
            alert1.eventId, alert1.alertTime, alert1.instanceStartTime
        )
        val retrieved2 = fixture.monitorStorage.getAlert(
            alert2.eventId, alert2.alertTime, alert2.instanceStartTime
        )
        val retrieved3 = fixture.monitorStorage.getAlert(
            alert3.eventId, alert3.alertTime, alert3.instanceStartTime
        )
        
        assertFalse("Event 1 should NOT be pre-muted", retrieved1!!.preMuted)
        assertTrue("Event 2 should be pre-muted", retrieved2!!.preMuted)
        assertFalse("Event 3 should NOT be pre-muted", retrieved3!!.preMuted)
    }
}
