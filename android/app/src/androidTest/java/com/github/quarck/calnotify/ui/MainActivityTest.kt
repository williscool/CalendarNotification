package com.github.quarck.calnotify.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atiurin.ultron.extensions.click
import com.atiurin.ultron.extensions.doesNotExist
import com.atiurin.ultron.extensions.isClickable
import com.atiurin.ultron.extensions.isDisplayed
import com.atiurin.ultron.extensions.isNotDisplayed
import com.atiurin.ultron.extensions.replaceText
import com.atiurin.ultron.extensions.withTimeout
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.testutils.UITestFixture
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ultron UI tests for MainActivity.
 * 
 * Tests the main event list display, search, and bulk actions.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : BaseUltronTest() {
    
    private lateinit var fixture: UITestFixture
    
    @Before
    fun setup() {
        fixture = UITestFixture.create()
        // Full optimization for fast UI tests:
        // - waitForAsyncTasks: Wait for background data loading instead of polling with timeouts
        // - preventCalendarReload: Stop calendar rescans from clearing test events
        // - grantPermissions: Avoid permission dialogs
        // - suppressBatteryDialog: Suppress battery optimization dialog
        fixture.setup(
            waitForAsyncTasks = true,
            preventCalendarReload = true,
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
    fun mainActivity_launches_successfully() {
        val scenario = fixture.launchMainActivity()
        
        scenario.onActivity { activity ->
            assert(activity != null)
        }
        
        scenario.close()
    }
    
    @Test
    fun mainActivity_shows_toolbar() {
        val scenario = fixture.launchMainActivity()
        
        withId(R.id.toolbar).isDisplayed()
        
        scenario.close()
    }
    
    // === Empty State Tests ===
    
    @Test
    fun mainActivity_shows_empty_view_when_no_events() {
        // No events seeded
        val scenario = fixture.launchMainActivity()
        
        withId(R.id.empty_view).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun mainActivity_hides_empty_view_when_events_exist() {
        fixture.seedEvents(3)
        
        val scenario = fixture.launchMainActivity()
        
        withId(R.id.empty_view).isNotDisplayed()
        
        scenario.close()
    }
    
    // === Event List Display Tests ===
    
    @Test
    fun mainActivity_displays_events_in_list() {
        fixture.createEvent(title = "Meeting with Team")
        fixture.createEvent(title = "Doctor Appointment")
        
        val scenario = fixture.launchMainActivity()
        
        // RecyclerView should be visible
        withId(R.id.list_events).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun mainActivity_displays_event_title() {
        val title = "Important Meeting"
        fixture.createEvent(title = title)
        
        val scenario = fixture.launchMainActivity()
        
        withText(title).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun mainActivity_displays_multiple_events() {
        fixture.createEvent(title = "Event One")
        fixture.createEvent(title = "Event Two")
        fixture.createEvent(title = "Event Three")
        
        val scenario = fixture.launchMainActivity()
        
        // All events should be visible (assuming they fit on screen)
        withText("Event One").isDisplayed()
        withText("Event Two").isDisplayed()
        withText("Event Three").isDisplayed()
        
        scenario.close()
    }
    
    // === Event Click Tests ===
    
    @Test
    fun clicking_event_opens_view_event_activity() {
        val event = fixture.createEvent(title = "Clickable Event")
        
        val scenario = fixture.launchMainActivity()
        
        // Click on the event
        withText("Clickable Event").click()
        
        // The ViewEventActivity should open - we verify by checking the scenario doesn't crash
        // In a real test, we'd use Intents to verify the launched activity
        
        scenario.close()
    }
    
    // === Floating Action Button Tests ===
    
    @Test
    fun fab_is_displayed() {
        val scenario = fixture.launchMainActivity()
        
        withId(R.id.action_btn_add_event).isDisplayed()
        
        scenario.close()
    }
    
    // === Menu Tests ===
    
    @Test
    fun snooze_all_menu_item_visible_when_events_exist() {
        fixture.seedEvents(2)
        
        val scenario = fixture.launchMainActivity()
        
        // Wait for events to load - reloadData() runs in background thread
        withText("Event 1").isDisplayed()
        
        // Snooze All appears in toolbar as icon (showAsAction="ifRoom"), not in overflow menu
        // Check by ID since it's an icon, not text
        withId(R.id.action_snooze_all).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun dismiss_all_menu_item_visible_when_events_exist() {
        fixture.seedEvents(2)
        
        val scenario = fixture.launchMainActivity()
        
        // Wait for events to load
        withText("Event 1").isDisplayed()
        
        // Dismiss all is in overflow menu (showAsAction="never")
        openActionBarOverflowOrOptionsMenu(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        
        withText(R.string.dismiss_all_events).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun settings_menu_item_is_visible() {
        val scenario = fixture.launchMainActivity()
        
        openActionBarOverflowOrOptionsMenu(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        
        withText(R.string.settings).isDisplayed()
        
        scenario.close()
    }
    
    // === Swipe to Dismiss Tests ===
    
    @Test
    fun event_card_is_clickable() {
        fixture.mockApplicationController()
        every { 
            ApplicationController.dismissEvent(
                any(), any<EventDismissType>(), any(), any(), any(), any()
            ) 
        } returns Unit
        
        fixture.createEvent(title = "Clickable Event Card")
        
        val scenario = fixture.launchMainActivity()
        
        // Verify the event is displayed
        withText("Clickable Event Card").isDisplayed()
        
        // The click listener is on the card container (card_view_main_holder), not the text view
        withId(R.id.card_view_main_holder).isClickable()
        
        scenario.close()
    }
    
    // === Pull to Refresh Tests ===
    
    @Test
    fun swipe_refresh_layout_exists() {
        val scenario = fixture.launchMainActivity()
        
        withId(R.id.cardview_refresh_layout).isDisplayed()
        
        scenario.close()
    }
    
    // === Snoozed Event Display Tests ===
    
    @Test
    fun snoozed_event_is_displayed() {
        fixture.createSnoozedEvent(title = "Snoozed Event")
        
        val scenario = fixture.launchMainActivity()
        
        withText("Snoozed Event").isDisplayed()
        
        scenario.close()
    }
    
    // === Muted Event Display Tests ===
    
    @Test
    fun muted_event_is_displayed() {
        fixture.createMutedEvent(title = "Muted Event")
        
        val scenario = fixture.launchMainActivity()
        
        withText("Muted Event").isDisplayed()
        
        scenario.close()
    }
    
    // === Task Event Display Tests ===
    
    @Test
    fun task_event_is_displayed() {
        fixture.createTaskEvent(title = "Task Event")
        
        val scenario = fixture.launchMainActivity()
        
        withText("Task Event").isDisplayed()
        
        scenario.close()
    }
    
    // === Search Back Button Tests ===
    
    @Test
    fun search_filters_events() {
        fixture.createEvent(title = "Alpha Meeting")
        fixture.createEvent(title = "Beta Meeting")
        
        val scenario = fixture.launchMainActivity()
        
        // Both events visible
        withText("Alpha Meeting").isDisplayed()
        withText("Beta Meeting").isDisplayed()
        
        // Open search (keyboard + search view = 2 navigation levels)
        fixture.openSearchViewReliably(scenario)
        fixture.pushNavigation(2)
        withId(androidx.appcompat.R.id.search_src_text).replaceText("Alpha")
        
        // Only Alpha should be visible (Beta is filtered out completely)
        withText("Alpha Meeting").isDisplayed()
        withText("Beta Meeting").doesNotExist()
        
        fixture.clearNavigationStack()
        scenario.close()
    }
    
    @Test
    fun first_back_press_hides_keyboard_keeps_filter() {
        fixture.createEvent(title = "Alpha Meeting")
        fixture.createEvent(title = "Beta Meeting")
        
        val scenario = fixture.launchMainActivity()
        
        withText("Alpha Meeting").isDisplayed()
        
        // Open search (keyboard + search view = 2 navigation levels)
        fixture.openSearchViewReliably(scenario)
        fixture.pushNavigation(2)
        withId(androidx.appcompat.R.id.search_src_text).replaceText("Alpha")
        
        // Only Alpha should be visible (Beta is filtered out completely)
        withText("Alpha Meeting").isDisplayed()
        withText("Beta Meeting").doesNotExist()
        
        // Press back - should keep filter active (hides keyboard)
        pressBack()
        fixture.popNavigation()
        
        // Filter should still be active - only Alpha visible
        withText("Alpha Meeting").isDisplayed()
        withText("Beta Meeting").doesNotExist()
        
        fixture.clearNavigationStack()
        scenario.close()
    }
    
    @Test
    fun second_back_press_clears_filter() {
        fixture.createEvent(title = "Alpha Meeting")
        fixture.createEvent(title = "Beta Meeting")
        
        val scenario = fixture.launchMainActivity()
        
        withText("Alpha Meeting").isDisplayed()
        
        // Open search (keyboard + search view = 2 navigation levels)
        fixture.openSearchViewReliably(scenario)
        fixture.pushNavigation(2)
        withId(androidx.appcompat.R.id.search_src_text).replaceText("Alpha")
        
        // Only Alpha visible (Beta is filtered out completely)
        withText("Alpha Meeting").isDisplayed()
        withText("Beta Meeting").doesNotExist()
        
        // First back - hides keyboard, keeps filter
        pressBack()
        fixture.popNavigation()
        withText("Beta Meeting").doesNotExist()
        
        // Second back - clears filter
        pressBack()
        fixture.popNavigation()
        
        // Both events should be visible again
        withText("Alpha Meeting").isDisplayed()
        withText("Beta Meeting").isDisplayed()
        
        // Navigation stack already cleared by the test itself
        scenario.close()
    }
    
    // Inherits setConfig() from BaseUltronTest
}
