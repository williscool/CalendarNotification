package com.github.quarck.calnotify.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.testutils.UITestFixture
import io.mockk.*
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso UI tests for DismissedEventsActivity.
 * 
 * Tests the dismissed events list display and restore functionality.
 */
@RunWith(AndroidJUnit4::class)
class DismissedEventsActivityTest {
    
    private lateinit var fixture: UITestFixture
    
    @Before
    fun setup() {
        fixture = UITestFixture.create()
        fixture.setup()
    }
    
    @After
    fun cleanup() {
        fixture.cleanup()
    }
    
    // === Activity Launch Tests ===
    
    @Test
    fun dismissedEventsActivity_launches_successfully() {
        val scenario = fixture.launchActivity<DismissedEventsActivity>()
        
        scenario.onActivity { activity ->
            assert(activity != null)
        }
        
        scenario.close()
    }
    
    @Test
    fun dismissedEventsActivity_shows_toolbar() {
        val scenario = fixture.launchActivity<DismissedEventsActivity>()
        
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun dismissedEventsActivity_has_back_navigation() {
        val scenario = fixture.launchActivity<DismissedEventsActivity>()
        
        // The activity should have a back button/up navigation
        onView(withContentDescription("Navigate up"))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Empty State Tests ===
    
    @Test
    fun dismissedEventsActivity_shows_recycler_view() {
        val scenario = fixture.launchActivity<DismissedEventsActivity>()
        
        onView(withId(R.id.list_events))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Dismissed Events Display Tests ===
    
    @Test
    fun dismissedEventsActivity_displays_dismissed_events() {
        // Create dismissed events
        fixture.createDismissedEvent(title = "Dismissed Meeting")
        fixture.createDismissedEvent(title = "Dismissed Appointment")
        
        val scenario = fixture.launchActivity<DismissedEventsActivity>()
        
        // Give time for data to load
        Thread.sleep(500)
        
        // RecyclerView should be visible
        onView(withId(R.id.list_events))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun dismissedEventsActivity_displays_event_title() {
        fixture.createDismissedEvent(title = "Important Dismissed Event")
        
        val scenario = fixture.launchActivity<DismissedEventsActivity>()
        
        // Give time for data to load
        Thread.sleep(500)
        
        onView(withText("Important Dismissed Event"))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Click to Show Menu Tests ===
    
    @Test
    fun clicking_dismissed_event_shows_popup_menu() {
        fixture.createDismissedEvent(title = "Clickable Dismissed Event")
        
        val scenario = fixture.launchActivity<DismissedEventsActivity>()
        
        // Give time for data to load
        Thread.sleep(500)
        
        // Click on the event
        onView(withText("Clickable Dismissed Event"))
            .perform(click())
        
        // Popup menu with Restore option should appear
        onView(withText(R.string.restore))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Restore Action Tests ===
    
    @Test
    fun clicking_restore_triggers_restore_event() {
        fixture.mockApplicationController()
        every { ApplicationController.restoreEvent(any(), any()) } returns Unit
        
        fixture.createDismissedEvent(title = "Event to Restore")
        
        val scenario = fixture.launchActivity<DismissedEventsActivity>()
        
        // Give time for data to load
        Thread.sleep(500)
        
        // Click on the event
        onView(withText("Event to Restore"))
            .perform(click())
        
        // Click Restore in popup menu
        onView(withText(R.string.restore))
            .perform(click())
        
        // Verify restore was called
        verify(timeout = 2000) { 
            ApplicationController.restoreEvent(any(), any()) 
        }
        
        scenario.close()
    }
    
    // === Options Menu Tests ===
    
    @Test
    fun dismissedEventsActivity_has_remove_all_option() {
        fixture.createDismissedEvent(title = "Test Event")
        
        val scenario = fixture.launchActivity<DismissedEventsActivity>()
        
        // Open overflow menu
        openActionBarOverflowOrOptionsMenu(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        
        // Remove All option should be visible
        onView(withText(R.string.remove_all))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun clicking_remove_all_shows_confirmation_dialog() {
        fixture.createDismissedEvent(title = "Test Event")
        
        val scenario = fixture.launchActivity<DismissedEventsActivity>()
        
        // Open overflow menu
        openActionBarOverflowOrOptionsMenu(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        
        // Click Remove All
        onView(withText(R.string.remove_all))
            .perform(click())
        
        // Confirmation dialog should appear
        onView(withText(R.string.remove_all_confirmation))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Multiple Dismissed Events Tests ===
    
    @Test
    fun dismissedEventsActivity_displays_multiple_events() {
        fixture.createDismissedEvent(title = "First Dismissed")
        fixture.createDismissedEvent(title = "Second Dismissed")
        fixture.createDismissedEvent(title = "Third Dismissed")
        
        val scenario = fixture.launchActivity<DismissedEventsActivity>()
        
        // Give time for data to load
        Thread.sleep(500)
        
        // All events should be visible
        onView(withText("First Dismissed"))
            .check(matches(isDisplayed()))
        onView(withText("Second Dismissed"))
            .check(matches(isDisplayed()))
        onView(withText("Third Dismissed"))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
}

