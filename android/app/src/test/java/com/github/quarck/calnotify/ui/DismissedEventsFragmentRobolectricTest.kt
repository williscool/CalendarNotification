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
 * Robolectric UI tests for DismissedEventsFragment.
 * 
 * Tests the dismissed events list display and restore functionality.
 * Mirrors DismissedEventsActivityRobolectricTest but for the fragment-based UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class DismissedEventsFragmentRobolectricTest {
    
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
    fun dismissedEventsFragment_launches_successfully() {
        val scenario = fixture.launchDismissedEventsFragment()
        
        scenario.onFragment { fragment ->
            assertNotNull(fragment)
        }
        
        scenario.close()
    }
    
    // === RecyclerView Tests ===
    
    @Test
    fun dismissedEventsFragment_shows_recycler_view() {
        val scenario = fixture.launchDismissedEventsFragment()
        
        scenario.onFragment { fragment ->
            val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
            assertNotNull(recyclerView)
            assertEquals(View.VISIBLE, recyclerView.visibility)
        }
        
        scenario.close()
    }
    
    // === Dismissed Events Display Tests ===
    
    @Test
    fun dismissedEventsFragment_displays_dismissed_events() {
        fixture.createDismissedEvent(title = "Dismissed Meeting")
        fixture.createDismissedEvent(title = "Dismissed Appointment")
        
        val scenario = fixture.launchDismissedEventsFragment()
        
        // Wait for async data loading
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
        scenario.onFragment { fragment ->
            val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
            assertNotNull(recyclerView)
            val adapter = recyclerView.adapter
            assertNotNull(adapter)
            assertEquals(2, adapter?.itemCount)
        }
        
        scenario.close()
    }
    
    @Test
    fun dismissedEventsFragment_shows_empty_when_no_dismissed_events() {
        val scenario = fixture.launchDismissedEventsFragment()
        
        // Wait for async data loading
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
        scenario.onFragment { fragment ->
            val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
            val adapter = recyclerView.adapter
            assertEquals(0, adapter?.itemCount ?: 0)
            
            val emptyView = fragment.requireView().findViewById<View>(R.id.empty_view)
            assertEquals(View.VISIBLE, emptyView.visibility)
        }
        
        scenario.close()
    }
    
    // === Multiple Dismissed Events Tests ===
    
    @Test
    fun dismissedEventsFragment_displays_multiple_events() {
        fixture.createDismissedEvent(title = "First Dismissed")
        fixture.createDismissedEvent(title = "Second Dismissed")
        fixture.createDismissedEvent(title = "Third Dismissed")
        
        val scenario = fixture.launchDismissedEventsFragment()
        
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
    fun dismissedEventsFragment_has_swipe_refresh_layout() {
        val scenario = fixture.launchDismissedEventsFragment()
        
        scenario.onFragment { fragment ->
            val swipeRefresh = fragment.requireView().findViewById<SwipeRefreshLayout>(R.id.refresh_layout)
            assertNotNull(swipeRefresh)
            assertEquals(View.VISIBLE, swipeRefresh.visibility)
        }
        
        scenario.close()
    }
    
    // === Empty State Message Tests ===
    
    @Test
    fun dismissedEventsFragment_shows_correct_empty_message() {
        val scenario = fixture.launchDismissedEventsFragment()
        
        // Wait for async data loading
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
        scenario.onFragment { fragment ->
            val emptyView = fragment.requireView().findViewById<TextView>(R.id.empty_view)
            assertNotNull(emptyView)
            assertEquals(fragment.getString(R.string.empty_dismissed), emptyView.text)
        }
        
        scenario.close()
    }
}
