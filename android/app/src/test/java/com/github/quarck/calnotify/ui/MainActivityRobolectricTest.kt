package com.github.quarck.calnotify.ui

import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SearchView
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
import android.os.Looper

/**
 * Robolectric UI tests for MainActivity.
 * 
 * Tests the main event list display, search, and bulk actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class MainActivityRobolectricTest {
    
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
    
    // === Activity Launch Tests ===
    
    @Test
    fun mainActivity_launches_successfully() {
        val scenario = fixture.launchMainActivity()
        
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
        
        scenario.close()
    }
    
    @Test
    fun mainActivity_shows_toolbar() {
        val scenario = fixture.launchMainActivity()
        
        scenario.onActivity { activity ->
            val toolbar = activity.findViewById<View>(R.id.toolbar)
            assertNotNull(toolbar)
            assertEquals(View.VISIBLE, toolbar.visibility)
        }
        
        scenario.close()
    }
    
    // === Empty State Tests ===
    
    @Test
    fun mainActivity_shows_empty_view_when_no_events() {
        val scenario = fixture.launchMainActivity()
        
        // Wait for async data loading
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
        scenario.onActivity { activity ->
            val emptyView = activity.findViewById<View>(R.id.empty_view)
            assertNotNull(emptyView)
            assertEquals(View.VISIBLE, emptyView.visibility)
        }
        
        scenario.close()
    }
    
    @Test
    fun mainActivity_hides_empty_view_when_events_exist() {
        fixture.seedEvents(3)
        
        val scenario = fixture.launchMainActivity()
        
        scenario.onActivity { activity ->
            val emptyView = activity.findViewById<View>(R.id.empty_view)
            // Empty view should be hidden or gone when events exist
            assertTrue(emptyView.visibility == View.GONE || emptyView.visibility == View.INVISIBLE)
        }
        
        scenario.close()
    }
    
    // === Event List Display Tests ===
    
    @Test
    fun mainActivity_has_recycler_view() {
        val scenario = fixture.launchMainActivity()
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_events)
            assertNotNull(recyclerView)
        }
        
        scenario.close()
    }
    
    @Test
    fun mainActivity_displays_events_in_list() {
        fixture.createEvent(title = "Meeting with Team")
        fixture.createEvent(title = "Doctor Appointment")
        
        val scenario = fixture.launchMainActivity()
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_events)
            assertNotNull(recyclerView)
            assertEquals(View.VISIBLE, recyclerView.visibility)
        }
        
        scenario.close()
    }
    
    @Test
    fun mainActivity_recycler_has_correct_item_count() {
        fixture.createEvent(title = "Event One")
        fixture.createEvent(title = "Event Two")
        fixture.createEvent(title = "Event Three")
        
        val scenario = fixture.launchMainActivity()
        
        // Wait for async data loading
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_events)
            val adapter = recyclerView.adapter
            assertNotNull(adapter)
            assertEquals(3, adapter?.itemCount)
        }
        
        scenario.close()
    }
    
    // === Floating Action Button Tests ===
    
    @Test
    fun fab_is_displayed() {
        val scenario = fixture.launchMainActivity()
        
        scenario.onActivity { activity ->
            val fab = activity.findViewById<View>(R.id.action_btn_add_event)
            assertNotNull(fab)
            assertEquals(View.VISIBLE, fab.visibility)
        }
        
        scenario.close()
    }
    
    // === Pull to Refresh Tests ===
    
    @Test
    fun swipe_refresh_layout_exists() {
        val scenario = fixture.launchMainActivity()
        
        scenario.onActivity { activity ->
            val swipeRefresh = activity.findViewById<SwipeRefreshLayout>(R.id.cardview_refresh_layout)
            assertNotNull(swipeRefresh)
            assertEquals(View.VISIBLE, swipeRefresh.visibility)
        }
        
        scenario.close()
    }
    
    // === Snoozed Event Display Tests ===
    
    @Test
    fun snoozed_event_is_in_list() {
        fixture.createSnoozedEvent(title = "Snoozed Event")
        
        val scenario = fixture.launchMainActivity()
        
        // Wait for async data loading
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_events)
            val adapter = recyclerView.adapter
            assertNotNull(adapter)
            assertEquals(1, adapter?.itemCount)
        }
        
        scenario.close()
    }
    
    // === Muted Event Display Tests ===
    
    @Test
    fun muted_event_is_in_list() {
        fixture.createMutedEvent(title = "Muted Event")
        
        val scenario = fixture.launchMainActivity()
        
        // Wait for async data loading
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_events)
            val adapter = recyclerView.adapter
            assertNotNull(adapter)
            assertEquals(1, adapter?.itemCount)
        }
        
        scenario.close()
    }
    
    // === Task Event Display Tests ===
    
    @Test
    fun task_event_is_in_list() {
        fixture.createTaskEvent(title = "Task Event")
        
        val scenario = fixture.launchMainActivity()
        
        // Wait for async data loading
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_events)
            val adapter = recyclerView.adapter
            assertNotNull(adapter)
            assertEquals(1, adapter?.itemCount)
        }
        
        scenario.close()
    }
    
    // === Menu Tests ===
    
    @Test
    fun activity_has_options_menu() {
        val scenario = fixture.launchMainActivity()
        
        scenario.onActivity { activity ->
            // Verify activity supports options menu by checking menu is inflated
            assertNotNull(activity.window)
        }
        
        scenario.close()
    }
    
    // === Search Back Button Tests ===
    
    @Test
    fun search_filter_persists_after_first_back_press() {
        fixture.createEvent(title = "Alpha Event")
        fixture.createEvent(title = "Beta Event")
        
        val scenario = fixture.launchMainActivity()
        shadowOf(Looper.getMainLooper()).idle()
        
        scenario.onActivity { activity ->
            // Trigger menu creation
            activity.invalidateOptionsMenu()
            shadowOf(Looper.getMainLooper()).idle()
            
            // Get adapter and set search
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_events)
            val adapter = recyclerView.adapter as EventListAdapter
            
            // Apply search filter
            adapter.setSearchText("Alpha")
            adapter.setEventsToDisplay()
            shadowOf(Looper.getMainLooper()).idle()
            
            assertEquals(1, adapter.itemCount)
            assertEquals("Alpha", adapter.searchString)
            
            // Press back - should keep filter
            activity.onBackPressedDispatcher.onBackPressed()
            shadowOf(Looper.getMainLooper()).idle()
            
            // Filter should still be active
            assertEquals("Alpha", adapter.searchString)
            assertEquals(1, adapter.itemCount)
        }
        
        scenario.close()
    }
    
    @Test
    fun search_filter_clears_after_second_back_press() {
        fixture.createEvent(title = "Alpha Event")
        fixture.createEvent(title = "Beta Event")
        
        val scenario = fixture.launchMainActivity()
        shadowOf(Looper.getMainLooper()).idle()
        
        scenario.onActivity { activity ->
            activity.invalidateOptionsMenu()
            shadowOf(Looper.getMainLooper()).idle()
            
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_events)
            val adapter = recyclerView.adapter as EventListAdapter
            
            // Apply search filter
            adapter.setSearchText("Alpha")
            adapter.setEventsToDisplay()
            shadowOf(Looper.getMainLooper()).idle()
            
            assertEquals(1, adapter.itemCount)
            
            // First back - clears focus (or keeps filter if no focus)
            activity.onBackPressedDispatcher.onBackPressed()
            shadowOf(Looper.getMainLooper()).idle()
            
            // Second back - should clear filter
            activity.onBackPressedDispatcher.onBackPressed()
            shadowOf(Looper.getMainLooper()).idle()
            
            // Filter should be cleared
            assertTrue(adapter.searchString.isNullOrEmpty())
            assertEquals(2, adapter.itemCount)
        }
        
        scenario.close()
    }
    
    @Test
    fun back_press_without_search_finishes_activity() {
        val scenario = fixture.launchMainActivity()
        shadowOf(Looper.getMainLooper()).idle()
        
        scenario.onActivity { activity ->
            activity.invalidateOptionsMenu()
            shadowOf(Looper.getMainLooper()).idle()
            
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_events)
            val adapter = recyclerView.adapter as EventListAdapter
            
            // No search active
            assertTrue(adapter.searchString.isNullOrEmpty())
            
            // Back should finish activity
            activity.onBackPressedDispatcher.onBackPressed()
            shadowOf(Looper.getMainLooper()).idle()
            
            assertTrue(activity.isFinishing)
        }
        
        scenario.close()
    }
}

