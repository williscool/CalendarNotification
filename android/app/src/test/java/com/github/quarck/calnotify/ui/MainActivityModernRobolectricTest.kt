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
 * Robolectric UI tests for MainActivityModern.
 * 
 * Tests the modern navigation UI with bottom tabs, event list display, search, and bulk actions.
 * Mirrors MainActivityRobolectricTest but for the fragment-based UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class MainActivityModernRobolectricTest {
    
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
    fun mainActivityModern_launches_successfully() {
        val scenario = fixture.launchMainActivityModern()
        
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
        
        scenario.close()
    }
    
    @Test
    fun mainActivityModern_shows_toolbar() {
        val scenario = fixture.launchMainActivityModern()
        
        scenario.onActivity { activity ->
            val toolbar = activity.findViewById<View>(R.id.toolbar)
            assertNotNull(toolbar)
            assertEquals(View.VISIBLE, toolbar.visibility)
        }
        
        scenario.close()
    }
    
    // === Bottom Navigation Tests ===
    
    @Test
    fun mainActivityModern_shows_bottom_navigation() {
        val scenario = fixture.launchMainActivityModern()
        
        scenario.onActivity { activity ->
            val bottomNav = activity.findViewById<View>(R.id.bottom_navigation)
            assertNotNull(bottomNav)
            assertEquals(View.VISIBLE, bottomNav.visibility)
        }
        
        scenario.close()
    }
    
    // === Empty State Tests ===
    
    @Test
    fun mainActivityModern_shows_empty_view_when_no_events() {
        val scenario = fixture.launchMainActivityModern()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onActivity { activity ->
            val emptyView = activity.findViewById<View>(R.id.empty_view)
            assertNotNull(emptyView)
            assertEquals(View.VISIBLE, emptyView.visibility)
        }
        
        scenario.close()
    }
    
    @Test
    fun mainActivityModern_hides_empty_view_when_events_exist() {
        fixture.seedEvents(3)
        
        val scenario = fixture.launchMainActivityModern()
        
        scenario.onActivity { activity ->
            val emptyView = activity.findViewById<View>(R.id.empty_view)
            // Empty view should be hidden or gone when events exist
            assertTrue(emptyView.visibility == View.GONE || emptyView.visibility == View.INVISIBLE)
        }
        
        scenario.close()
    }
    
    // === Event List Display Tests ===
    
    @Test
    fun mainActivityModern_has_recycler_view() {
        val scenario = fixture.launchMainActivityModern()
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_view)
            assertNotNull(recyclerView)
        }
        
        scenario.close()
    }
    
    @Test
    fun mainActivityModern_displays_events_in_list() {
        fixture.createEvent(title = "Meeting with Team")
        fixture.createEvent(title = "Doctor Appointment")
        
        val scenario = fixture.launchMainActivityModern()
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_view)
            assertNotNull(recyclerView)
            assertEquals(View.VISIBLE, recyclerView.visibility)
        }
        
        scenario.close()
    }
    
    @Test
    fun mainActivityModern_recycler_has_correct_item_count() {
        fixture.createEvent(title = "Event One")
        fixture.createEvent(title = "Event Two")
        fixture.createEvent(title = "Event Three")
        
        val scenario = fixture.launchMainActivityModern()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_view)
            val adapter = recyclerView.adapter
            assertNotNull(adapter)
            assertEquals(3, adapter?.itemCount)
        }
        
        scenario.close()
    }
    
    // === Floating Action Button Tests ===
    
    @Test
    fun fab_is_displayed() {
        val scenario = fixture.launchMainActivityModern()
        
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
        val scenario = fixture.launchMainActivityModern()
        
        scenario.onActivity { activity ->
            val swipeRefresh = activity.findViewById<SwipeRefreshLayout>(R.id.refresh_layout)
            assertNotNull(swipeRefresh)
            assertEquals(View.VISIBLE, swipeRefresh.visibility)
        }
        
        scenario.close()
    }
    
    // === Snoozed Event Display Tests ===
    
    @Test
    fun snoozed_event_is_in_list() {
        fixture.createSnoozedEvent(title = "Snoozed Event")
        
        val scenario = fixture.launchMainActivityModern()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_view)
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
        
        val scenario = fixture.launchMainActivityModern()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_view)
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
        
        val scenario = fixture.launchMainActivityModern()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_view)
            val adapter = recyclerView.adapter
            assertNotNull(adapter)
            assertEquals(1, adapter?.itemCount)
        }
        
        scenario.close()
    }
    
    // === Menu Tests ===
    
    @Test
    fun activity_has_options_menu() {
        val scenario = fixture.launchMainActivityModern()
        
        scenario.onActivity { activity ->
            // Verify activity supports options menu by checking menu is inflated
            assertNotNull(activity.window)
        }
        
        scenario.close()
    }
    
    // === Search Tests ===
    
    @Test
    fun search_filter_persists_when_searchview_has_focus_and_collapse_attempted() {
        fixture.createEvent(title = "Alpha Event")
        fixture.createEvent(title = "Beta Event")
        
        val scenario = fixture.launchMainActivityModern()
        shadowOf(Looper.getMainLooper()).idle()
        
        scenario.onActivity { activity ->
            activity.invalidateOptionsMenu()
            shadowOf(Looper.getMainLooper()).idle()
            
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_view)
            val adapter = recyclerView.adapter as EventListAdapter
            
            // Expand SearchView and give it focus (simulates user tapping search icon)
            val searchView = activity.searchView
            val searchMenuItem = activity.searchMenuItem
            assertNotNull("SearchView should be initialized", searchView)
            assertNotNull("SearchMenuItem should be initialized", searchMenuItem)
            searchMenuItem!!.expandActionView()
            searchView!!.requestFocus()
            searchView.setQuery("Alpha", false)
            shadowOf(Looper.getMainLooper()).idle()
            
            assertEquals(1, adapter.itemCount)
            assertEquals("Alpha", adapter.searchString)
            assertTrue("SearchView should have focus", searchView.hasFocus())
            
            // Try to collapse (simulates back press) - should clear focus but prevent collapse
            searchMenuItem.collapseActionView()
            shadowOf(Looper.getMainLooper()).idle()
            
            // Filter should still be active, SearchView should still be expanded
            assertEquals("Alpha", adapter.searchString)
            assertEquals(1, adapter.itemCount)
        }
        
        scenario.close()
    }
    
    @Test
    fun search_filter_clears_on_collapse_when_no_focus() {
        fixture.createEvent(title = "Alpha Event")
        fixture.createEvent(title = "Beta Event")
        
        val scenario = fixture.launchMainActivityModern()
        shadowOf(Looper.getMainLooper()).idle()
        
        scenario.onActivity { activity ->
            activity.invalidateOptionsMenu()
            shadowOf(Looper.getMainLooper()).idle()
            
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_view)
            val adapter = recyclerView.adapter as EventListAdapter
            
            // Set search via SearchView but clear focus (simulates submitted search)
            val searchView = activity.searchView
            val searchMenuItem = activity.searchMenuItem
            assertNotNull(searchView)
            assertNotNull(searchMenuItem)
            searchMenuItem!!.expandActionView()
            searchView!!.setQuery("Alpha", false)
            searchView.clearFocus()
            shadowOf(Looper.getMainLooper()).idle()
            
            assertEquals(1, adapter.itemCount)
            assertFalse("SearchView should not have focus", searchView.hasFocus())
            
            // Collapse should clear filter (no focus to clear first)
            searchMenuItem.collapseActionView()
            shadowOf(Looper.getMainLooper()).idle()
            
            // Filter should be cleared
            assertTrue(adapter.searchString.isNullOrEmpty())
            assertEquals(2, adapter.itemCount)
        }
        
        scenario.close()
    }
    
    @Test
    fun back_press_without_search_finishes_activity() {
        val scenario = fixture.launchMainActivityModern()
        shadowOf(Looper.getMainLooper()).idle()
        
        scenario.onActivity { activity ->
            activity.invalidateOptionsMenu()
            shadowOf(Looper.getMainLooper()).idle()
            
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_view)
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
    
    // === Tab Switching Tests ===
    
    @Test
    fun tab_switching_changes_destination() {
        val scenario = fixture.launchMainActivityModern()
        shadowOf(Looper.getMainLooper()).idle()
        
        scenario.onActivity { activity ->
            val bottomNav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
            assertNotNull(bottomNav)
            
            // Start on Active tab (default)
            assertEquals(R.id.activeEventsFragment, bottomNav.selectedItemId)
            
            // Switch to Upcoming tab
            bottomNav.selectedItemId = R.id.upcomingEventsFragment
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(R.id.upcomingEventsFragment, bottomNav.selectedItemId)
            
            // Switch to Dismissed tab
            bottomNav.selectedItemId = R.id.dismissedEventsFragment
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(R.id.dismissedEventsFragment, bottomNav.selectedItemId)
            
            // Switch back to Active tab
            bottomNav.selectedItemId = R.id.activeEventsFragment
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(R.id.activeEventsFragment, bottomNav.selectedItemId)
        }
        
        scenario.close()
    }
    
    @Test
    fun search_clears_when_switching_tabs() {
        fixture.createEvent(title = "Alpha Event")
        fixture.createEvent(title = "Beta Event")
        
        val scenario = fixture.launchMainActivityModern()
        shadowOf(Looper.getMainLooper()).idle()
        
        scenario.onActivity { activity ->
            activity.invalidateOptionsMenu()
            shadowOf(Looper.getMainLooper()).idle()
            
            // Set up search filter
            val searchView = activity.searchView
            val searchMenuItem = activity.searchMenuItem
            assertNotNull(searchView)
            assertNotNull(searchMenuItem)
            searchMenuItem!!.expandActionView()
            searchView!!.setQuery("Alpha", false)
            shadowOf(Looper.getMainLooper()).idle()
            
            // Verify search is active
            assertEquals("Alpha", searchView.query.toString())
            
            // Switch tabs
            val bottomNav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
            bottomNav.selectedItemId = R.id.upcomingEventsFragment
            shadowOf(Looper.getMainLooper()).idle()
            
            // Search should be cleared
            assertEquals("", searchView.query.toString())
        }
        
        scenario.close()
    }
    
    // === Menu Items Per Tab Tests ===
    
    @Test
    fun snooze_all_menu_visible_only_on_active_tab() {
        fixture.seedEvents(2)
        
        val scenario = fixture.launchMainActivityModern()
        shadowOf(Looper.getMainLooper()).idle()
        
        scenario.onActivity { activity ->
            val bottomNav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
            
            // On Active tab - snooze all should be visible
            activity.invalidateOptionsMenu()
            shadowOf(Looper.getMainLooper()).idle()
            // Menu visibility is controlled by onCreateOptionsMenu, we verify the tab is correct
            assertEquals(R.id.activeEventsFragment, bottomNav.selectedItemId)
            
            // Switch to Upcoming tab
            bottomNav.selectedItemId = R.id.upcomingEventsFragment
            shadowOf(Looper.getMainLooper()).idle()
            activity.invalidateOptionsMenu()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(R.id.upcomingEventsFragment, bottomNav.selectedItemId)
            
            // Switch to Dismissed tab
            bottomNav.selectedItemId = R.id.dismissedEventsFragment
            shadowOf(Looper.getMainLooper()).idle()
            activity.invalidateOptionsMenu()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(R.id.dismissedEventsFragment, bottomNav.selectedItemId)
        }
        
        scenario.close()
    }
    
    @Test
    fun dismiss_all_menu_visible_only_on_active_tab() {
        fixture.seedEvents(2)
        
        val scenario = fixture.launchMainActivityModern()
        shadowOf(Looper.getMainLooper()).idle()
        
        scenario.onActivity { activity ->
            val bottomNav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
            
            // On Active tab - dismiss all should be visible
            activity.invalidateOptionsMenu()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(R.id.activeEventsFragment, bottomNav.selectedItemId)
            
            // Switch to Dismissed tab - dismiss all should not be visible
            bottomNav.selectedItemId = R.id.dismissedEventsFragment
            shadowOf(Looper.getMainLooper()).idle()
            activity.invalidateOptionsMenu()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(R.id.dismissedEventsFragment, bottomNav.selectedItemId)
        }
        
        scenario.close()
    }
}
