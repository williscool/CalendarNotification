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
 * Robolectric UI tests for ActiveEventsFragment.
 * 
 * Tests the active events list display and interactions.
 * Mirrors MainActivityRobolectricTest but for the fragment-based UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class ActiveEventsFragmentRobolectricTest {
    
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
    fun activeEventsFragment_launches_successfully() {
        val scenario = fixture.launchActiveEventsFragment()
        
        scenario.onFragment { fragment ->
            assertNotNull(fragment)
        }
        
        scenario.close()
    }
    
    // === Empty State Tests ===
    
    @Test
    fun activeEventsFragment_shows_empty_view_when_no_events() {
        val scenario = fixture.launchActiveEventsFragment()
        
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
    fun activeEventsFragment_hides_empty_view_when_events_exist() {
        fixture.seedEvents(3)
        
        val scenario = fixture.launchActiveEventsFragment()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onFragment { fragment ->
            val emptyView = fragment.requireView().findViewById<View>(R.id.empty_view)
            // Empty view should be hidden or gone when events exist
            assertTrue(emptyView.visibility == View.GONE || emptyView.visibility == View.INVISIBLE)
        }
        
        scenario.close()
    }
    
    // === RecyclerView Tests ===
    
    @Test
    fun activeEventsFragment_has_recycler_view() {
        val scenario = fixture.launchActiveEventsFragment()
        
        scenario.onFragment { fragment ->
            val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
            assertNotNull(recyclerView)
        }
        
        scenario.close()
    }
    
    @Test
    fun activeEventsFragment_displays_events_in_list() {
        fixture.createEvent(title = "Meeting with Team")
        fixture.createEvent(title = "Doctor Appointment")
        
        val scenario = fixture.launchActiveEventsFragment()
        
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
    fun activeEventsFragment_recycler_has_correct_item_count() {
        fixture.createEvent(title = "Event One")
        fixture.createEvent(title = "Event Two")
        fixture.createEvent(title = "Event Three")
        
        val scenario = fixture.launchActiveEventsFragment()
        
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
    fun activeEventsFragment_has_swipe_refresh_layout() {
        val scenario = fixture.launchActiveEventsFragment()
        
        scenario.onFragment { fragment ->
            val swipeRefresh = fragment.requireView().findViewById<SwipeRefreshLayout>(R.id.refresh_layout)
            assertNotNull(swipeRefresh)
            assertEquals(View.VISIBLE, swipeRefresh.visibility)
        }
        
        scenario.close()
    }
    
    // === Snoozed Event Display Tests ===
    
    @Test
    fun activeEventsFragment_snoozed_event_is_in_list() {
        fixture.createSnoozedEvent(title = "Snoozed Event")
        
        val scenario = fixture.launchActiveEventsFragment()
        
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
    
    // === Muted Event Display Tests ===
    
    @Test
    fun activeEventsFragment_muted_event_is_in_list() {
        fixture.createMutedEvent(title = "Muted Event")
        
        val scenario = fixture.launchActiveEventsFragment()
        
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
    
    // === Task Event Display Tests ===
    
    @Test
    fun activeEventsFragment_task_event_is_in_list() {
        fixture.createTaskEvent(title = "Task Event")
        
        val scenario = fixture.launchActiveEventsFragment()
        
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
    
    // === Empty State Message Tests ===
    
    @Test
    fun activeEventsFragment_shows_correct_empty_message() {
        val scenario = fixture.launchActiveEventsFragment()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onFragment { fragment ->
            val emptyView = fragment.requireView().findViewById<TextView>(R.id.empty_view)
            assertNotNull(emptyView)
            assertEquals(fragment.getString(R.string.empty_active), emptyView.text)
        }
        
        scenario.close()
    }
}
