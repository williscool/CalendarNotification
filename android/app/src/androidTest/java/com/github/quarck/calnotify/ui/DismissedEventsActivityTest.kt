package com.github.quarck.calnotify.ui

import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atiurin.ultron.extensions.click
import com.atiurin.ultron.extensions.isDisplayed
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.testutils.UITestFixture
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ultron UI tests for DismissedEventsActivity.
 * 
 * Tests the dismissed events list display and restore functionality.
 */
@RunWith(AndroidJUnit4::class)
class DismissedEventsActivityTest : BaseUltronTest() {
    
    private lateinit var fixture: UITestFixture
    
    @Before
    fun setup() {
        fixture = UITestFixture.create()
        // Full optimization for fast UI tests:
        // - waitForAsyncTasks: Wait for background data loading instead of polling with timeouts
        // - grantPermissions: Avoid permission dialogs
        // - suppressBatteryDialog: Suppress battery optimization dialog
        fixture.setup(
            waitForAsyncTasks = true,
            grantPermissions = true,
            suppressBatteryDialog = true
        )
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
            assert(activity != null)
        }
        
        scenario.close()
    }
    
    @Test
    fun dismissedEventsActivity_shows_toolbar() {
        val scenario = fixture.launchDismissedEventsActivity()
        
        withId(R.id.toolbar).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun dismissedEventsActivity_has_back_navigation() {
        val scenario = fixture.launchDismissedEventsActivity()
        
        // The activity should have a back button/up navigation
        withContentDescription("Navigate up").isDisplayed()
        
        scenario.close()
    }
    
    // === Empty State Tests ===
    
    @Test
    fun dismissedEventsActivity_shows_recycler_view() {
        val scenario = fixture.launchDismissedEventsActivity()
        
        withId(R.id.list_events).isDisplayed()
        
        scenario.close()
    }
    
    // === Dismissed Events Display Tests ===
    
    @Test
    fun dismissedEventsActivity_displays_dismissed_events() {
        // Create dismissed events
        fixture.createDismissedEvent(title = "Dismissed Meeting")
        fixture.createDismissedEvent(title = "Dismissed Appointment")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // RecyclerView should be visible (Ultron auto-waits)
        withId(R.id.list_events).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun dismissedEventsActivity_displays_event_title() {
        fixture.createDismissedEvent(title = "Important Dismissed Event")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // Wait for recycler to be ready
        withId(R.id.list_events).isDisplayed()
        
        withText("Important Dismissed Event").isDisplayed()
        
        scenario.close()
    }
    
    // === Click to Show Menu Tests ===
    
    @Test
    fun clicking_dismissed_event_shows_popup_menu() {
        fixture.createDismissedEvent(title = "Clickable Dismissed Event")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // Wait for recycler to be ready
        withId(R.id.list_events).isDisplayed()
        
        // Click on the event
        withText("Clickable Dismissed Event").click()
        
        // Popup menu with Restore option should appear
        withText(R.string.restore).isDisplayed()
        
        scenario.close()
    }
    
    // === Restore Action Tests ===
    
    @Test
    fun clicking_restore_triggers_restore_event() {
        fixture.mockApplicationController()
        every { ApplicationController.restoreEvent(any(), any()) } returns Unit
        
        fixture.createDismissedEvent(title = "Event to Restore")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // Wait for recycler to be ready
        withId(R.id.list_events).isDisplayed()
        
        // Click on the event
        withText("Event to Restore").click()
        
        // Click Restore in popup menu
        withText(R.string.restore).click()
        
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
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // First check that toolbar loaded
        withId(R.id.toolbar).isDisplayed()
        
        // Open overflow menu
            openActionBarOverflowOrOptionsMenu(
                InstrumentationRegistry.getInstrumentation().targetContext
            )
        
        // Remove All option should be visible
        withText(R.string.remove_all).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun clicking_remove_all_shows_confirmation_dialog() {
        fixture.createDismissedEvent(title = "Test Event")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // Wait for toolbar to be ready
        withId(R.id.toolbar).isDisplayed()
        
        // Open overflow menu
        openActionBarOverflowOrOptionsMenu(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        
        // Click Remove All
        withText(R.string.remove_all).click()
        
        // Confirmation dialog should appear
        withText(R.string.remove_all_confirmation).isDisplayed()
        
        scenario.close()
    }
    
    // === Multiple Dismissed Events Tests ===
    
    @Test
    fun dismissedEventsActivity_displays_multiple_events() {
        fixture.createDismissedEvent(title = "First Dismissed")
        fixture.createDismissedEvent(title = "Second Dismissed")
        fixture.createDismissedEvent(title = "Third Dismissed")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // Wait for recycler to be ready
        withId(R.id.list_events).isDisplayed()
        
        // All events should be visible
        withText("First Dismissed").isDisplayed()
        withText("Second Dismissed").isDisplayed()
        withText("Third Dismissed").isDisplayed()
        
        scenario.close()
    }
    
    companion object {
        private const val LOG_TAG = "DismissedEventsActivityTest"
    }
}
