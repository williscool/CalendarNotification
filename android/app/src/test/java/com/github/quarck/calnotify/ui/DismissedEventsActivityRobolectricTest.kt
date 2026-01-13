package com.github.quarck.calnotify.ui

import android.view.View
import androidx.recyclerview.widget.RecyclerView
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
 * Robolectric UI tests for DismissedEventsActivity.
 * 
 * Tests the dismissed events list display and restore functionality.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class DismissedEventsActivityRobolectricTest {
    
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
    fun dismissedEventsActivity_launches_successfully() {
        val scenario = fixture.launchDismissedEventsActivity()
        
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
        
        scenario.close()
    }
    
    @Test
    fun dismissedEventsActivity_shows_toolbar() {
        val scenario = fixture.launchDismissedEventsActivity()
        
        scenario.onActivity { activity ->
            val toolbar = activity.findViewById<View>(R.id.toolbar)
            assertNotNull(toolbar)
            assertEquals(View.VISIBLE, toolbar.visibility)
        }
        
        scenario.close()
    }
    
    // === RecyclerView Tests ===
    
    @Test
    fun dismissedEventsActivity_shows_recycler_view() {
        val scenario = fixture.launchDismissedEventsActivity()
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_events)
            assertNotNull(recyclerView)
            assertEquals(View.VISIBLE, recyclerView.visibility)
        }
        
        scenario.close()
    }
    
    // === Dismissed Events Display Tests ===
    
    @Test
    fun dismissedEventsActivity_displays_dismissed_events() {
        fixture.createDismissedEvent(title = "Dismissed Meeting")
        fixture.createDismissedEvent(title = "Dismissed Appointment")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // Wait for async data loading (reloadData uses background{})
        fixture.waitForAsyncTasks()
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_events)
            assertNotNull(recyclerView)
            val adapter = recyclerView.adapter
            assertNotNull(adapter)
            assertEquals(2, adapter?.itemCount)
        }
        
        scenario.close()
    }
    
    @Test
    fun dismissedEventsActivity_shows_empty_when_no_dismissed_events() {
        val scenario = fixture.launchDismissedEventsActivity()
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_events)
            val adapter = recyclerView.adapter
            assertEquals(0, adapter?.itemCount ?: 0)
        }
        
        scenario.close()
    }
    
    // === Multiple Dismissed Events Tests ===
    
    @Test
    fun dismissedEventsActivity_displays_multiple_events() {
        fixture.createDismissedEvent(title = "First Dismissed")
        fixture.createDismissedEvent(title = "Second Dismissed")
        fixture.createDismissedEvent(title = "Third Dismissed")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // Wait for async data loading
        fixture.waitForAsyncTasks()
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_events)
            val adapter = recyclerView.adapter
            assertNotNull(adapter)
            assertEquals(3, adapter?.itemCount)
        }
        
        scenario.close()
    }
}

