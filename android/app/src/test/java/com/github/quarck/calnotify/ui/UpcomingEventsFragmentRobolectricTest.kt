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

import android.os.Looper
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric UI tests for UpcomingEventsFragment
 * 
 * Tests the upcoming events list display (read-only in Milestone 1).
 * Pre-actions (snooze, mute, dismiss) will be tested in Milestone 2.
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
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
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
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
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
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
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
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
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
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
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
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
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
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
        scenario.onFragment { fragment ->
            val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
            val adapter = recyclerView.adapter
            assertNotNull(adapter)
            assertEquals(5, adapter?.itemCount)
        }
        
        scenario.close()
    }
}
