package com.github.quarck.calnotify.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atiurin.ultron.core.espresso.UltronEspressoInteraction
import com.atiurin.ultron.extensions.click
import com.atiurin.ultron.extensions.doesNotExist
import com.atiurin.ultron.extensions.isClickable
import com.atiurin.ultron.extensions.isDisplayed
import com.atiurin.ultron.extensions.isNotDisplayed
import com.atiurin.ultron.extensions.replaceText
import com.atiurin.ultron.extensions.withTimeout
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
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
 * Ultron UI tests for MainActivityModern.
 * 
 * Tests the modern navigation UI with bottom tabs, event list display, search, and bulk actions.
 * Mirrors MainActivityTest but for the fragment-based UI.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityModernTest : BaseUltronTest() {
    
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
        
        // Enable new navigation UI for these tests
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Settings(context).useNewNavigationUI = true
    }
    
    @After
    fun cleanup() {
        // Reset to legacy UI (default for other tests)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Settings(context).useNewNavigationUI = false
        
        fixture.cleanup()
    }
    
    // === Activity Launch Tests ===
    
    @Test
    fun mainActivityModern_launches_successfully() {
        val scenario = fixture.launchMainActivityModern()
        
        scenario.onActivity { activity ->
            assert(activity != null)
        }
        
        scenario.close()
    }
    
    @Test
    fun mainActivityModern_shows_toolbar() {
        val scenario = fixture.launchMainActivityModern()
        
        withId(R.id.toolbar).isDisplayed()
        
        scenario.close()
    }
    
    // === Bottom Navigation Tests ===
    
    @Test
    fun mainActivityModern_shows_bottom_navigation() {
        val scenario = fixture.launchMainActivityModern()
        
        withId(R.id.bottom_navigation).isDisplayed()
        
        scenario.close()
    }
    
    // === Empty State Tests ===
    
    @Test
    fun mainActivityModern_shows_empty_view_when_no_events() {
        // No events seeded
        val scenario = fixture.launchMainActivityModern()
        
        withId(R.id.empty_view).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun mainActivityModern_hides_empty_view_when_events_exist() {
        fixture.seedEvents(3)
        
        val scenario = fixture.launchMainActivityModern()
        
        withId(R.id.empty_view).isNotDisplayed()
        
        scenario.close()
    }
    
    // === Event List Display Tests ===
    
    @Test
    fun mainActivityModern_displays_events_in_list() {
        fixture.createEvent(title = "Meeting with Team")
        fixture.createEvent(title = "Doctor Appointment")
        
        val scenario = fixture.launchMainActivityModern()
        
        // RecyclerView should be visible (in fragment)
        withId(R.id.recycler_view).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun mainActivityModern_displays_event_title() {
        val title = "Important Meeting"
        fixture.createEvent(title = title)
        
        val scenario = fixture.launchMainActivityModern()
        
        withText(title).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun mainActivityModern_displays_multiple_events() {
        fixture.createEvent(title = "Event One")
        fixture.createEvent(title = "Event Two")
        fixture.createEvent(title = "Event Three")
        
        val scenario = fixture.launchMainActivityModern()
        
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
        
        val scenario = fixture.launchMainActivityModern()
        
        // Click on the event
        withText("Clickable Event").click()
        
        // The ViewEventActivity should open - we verify by checking the scenario doesn't crash
        // In a real test, we'd use Intents to verify the launched activity
        
        scenario.close()
    }
    
    // === Floating Action Button Tests ===
    
    @Test
    fun fab_is_displayed() {
        val scenario = fixture.launchMainActivityModern()
        
        withId(R.id.action_btn_add_event).isDisplayed()
        
        scenario.close()
    }
    
    // === Menu Tests ===
    
    @Test
    fun snooze_all_menu_item_visible_when_events_exist() {
        fixture.seedEvents(2)
        
        val scenario = fixture.launchMainActivityModern()
        
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
        
        val scenario = fixture.launchMainActivityModern()
        
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
        val scenario = fixture.launchMainActivityModern()
        
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
        
        val scenario = fixture.launchMainActivityModern()
        
        // Verify the event is displayed
        withText("Clickable Event Card").isDisplayed()
        
        // The click listener is on the card container (card_view_main_holder), not the text view
        withId(R.id.card_view_main_holder).isClickable()
        
        scenario.close()
    }
    
    // === Pull to Refresh Tests ===
    
    @Test
    fun swipe_refresh_layout_exists() {
        val scenario = fixture.launchMainActivityModern()
        
        withId(R.id.refresh_layout).isDisplayed()
        
        scenario.close()
    }
    
    // === Snoozed Event Display Tests ===
    
    @Test
    fun snoozed_event_is_displayed() {
        fixture.createSnoozedEvent(title = "Snoozed Event")
        
        val scenario = fixture.launchMainActivityModern()
        
        withText("Snoozed Event").isDisplayed()
        
        scenario.close()
    }
    
    // === Muted Event Display Tests ===
    
    @Test
    fun muted_event_is_displayed() {
        fixture.createMutedEvent(title = "Muted Event")
        
        val scenario = fixture.launchMainActivityModern()
        
        withText("Muted Event").isDisplayed()
        
        scenario.close()
    }
    
    // === Task Event Display Tests ===
    
    @Test
    fun task_event_is_displayed() {
        fixture.createTaskEvent(title = "Task Event")
        
        val scenario = fixture.launchMainActivityModern()
        
        withText("Task Event").isDisplayed()
        
        scenario.close()
    }
    
    // === Search Back Button Tests ===
    
    @Test
    fun search_filters_events() {
        fixture.createEvent(title = "Alpha Meeting")
        fixture.createEvent(title = "Beta Meeting")
        
        val scenario = fixture.launchMainActivityModern()
        
        // Both events visible
        withText("Alpha Meeting").isDisplayed()
        withText("Beta Meeting").isDisplayed()
        
        // Open search (keyboard + search view = 2 navigation levels)
        openSearchViewReliably(scenario)
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
        
        val scenario = fixture.launchMainActivityModern()
        
        withText("Alpha Meeting").isDisplayed()
        
        // Open search (keyboard + search view = 2 navigation levels)
        openSearchViewReliably(scenario)
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
        
        val scenario = fixture.launchMainActivityModern()
        
        withText("Alpha Meeting").isDisplayed()
        
        // Open search (keyboard + search view = 2 navigation levels)
        openSearchViewReliably(scenario)
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
    
    // === Tab Switching Tests ===
    
    @Test
    fun tab_switching_to_upcoming_works() {
        val scenario = fixture.launchMainActivityModern()
        
        // Start on Active tab (default)
        withId(R.id.activeEventsFragment).isDisplayed()
        
        // Switch to Upcoming tab
        withId(R.id.upcomingEventsFragment).click()
        
        // Upcoming tab content should be visible
        withId(R.id.recycler_view).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun tab_switching_to_dismissed_works() {
        val scenario = fixture.launchMainActivityModern()
        
        // Start on Active tab (default)
        withId(R.id.activeEventsFragment).isDisplayed()
        
        // Switch to Dismissed tab
        withId(R.id.dismissedEventsFragment).click()
        
        // Dismissed tab content should be visible
        withId(R.id.recycler_view).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun tab_switching_back_to_active_works() {
        val scenario = fixture.launchMainActivityModern()
        
        // Switch to Upcoming tab
        withId(R.id.upcomingEventsFragment).click()
        
        // Switch back to Active tab
        withId(R.id.activeEventsFragment).click()
        
        // Active tab content should be visible
        withId(R.id.recycler_view).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun search_clears_when_switching_tabs() {
        fixture.createEvent(title = "Alpha Meeting")
        fixture.createEvent(title = "Beta Meeting")
        
        val scenario = fixture.launchMainActivityModern()
        
        // Both events visible
        withText("Alpha Meeting").isDisplayed()
        withText("Beta Meeting").isDisplayed()
        
        // Open search and filter
        openSearchViewReliably(scenario)
        fixture.pushNavigation(2)
        withId(androidx.appcompat.R.id.search_src_text).replaceText("Alpha")
        
        // Only Alpha visible
        withText("Alpha Meeting").isDisplayed()
        withText("Beta Meeting").doesNotExist()
        
        // Dismiss keyboard so we can click bottom nav (keyboard covers it)
        pressBack()
        fixture.popNavigation()
        
        // Switch to Upcoming tab using content description (more reliable for BottomNav items)
        withContentDescription(R.string.nav_upcoming).click()
        // Verify we're on Upcoming tab by checking toolbar title changed
        withText("Upcoming Events").isDisplayed()
        
        // Switch back to Active tab
        withContentDescription(R.string.nav_active).click()
        // Verify we're back on Active tab by checking toolbar title
        withText("Calendar Notifications - Active").isDisplayed()
        
        // Both events should be visible again (search was cleared)
        withText("Alpha Meeting").isDisplayed()
        withText("Beta Meeting").isDisplayed()
        
        scenario.close()
    }
    
    // === Search Integration Tests ===
    
    @Test
    fun search_query_filters_events_correctly() {
        fixture.createEvent(title = "Project Alpha Review")
        fixture.createEvent(title = "Team Beta Standup")
        fixture.createEvent(title = "Alpha Planning Session")
        
        val scenario = fixture.launchMainActivityModern()
        
        // All 3 events visible
        withText("Project Alpha Review").isDisplayed()
        withText("Team Beta Standup").isDisplayed()
        withText("Alpha Planning Session").isDisplayed()
        
        // Search for "Alpha"
        openSearchViewReliably(scenario)
        fixture.pushNavigation(2)
        withId(androidx.appcompat.R.id.search_src_text).replaceText("Alpha")
        
        // Only Alpha events visible (2 of 3)
        withText("Project Alpha Review").isDisplayed()
        withText("Alpha Planning Session").isDisplayed()
        withText("Team Beta Standup").doesNotExist()
        
        fixture.clearNavigationStack()
        scenario.close()
    }
    
    @Test
    fun search_close_button_clears_filter() {
        fixture.createEvent(title = "Alpha Event")
        fixture.createEvent(title = "Beta Event")
        
        val scenario = fixture.launchMainActivityModern()
        
        // Both events visible
        withText("Alpha Event").isDisplayed()
        withText("Beta Event").isDisplayed()
        
        // Search for Alpha
        openSearchViewReliably(scenario)
        fixture.pushNavigation(2)
        withId(androidx.appcompat.R.id.search_src_text).replaceText("Alpha")
        
        // Only Alpha visible
        withText("Alpha Event").isDisplayed()
        withText("Beta Event").doesNotExist()
        
        // Click close button (X) to clear search
        // Note: Close button collapses the search action view
        withId(androidx.appcompat.R.id.search_close_btn).click()
        
        // Both events should be visible again
        withText("Alpha Event").isDisplayed()
        withText("Beta Event").isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun search_on_dismissed_tab_filters_dismissed_events() {
        fixture.createDismissedEvent(title = "Dismissed Alpha")
        fixture.createDismissedEvent(title = "Dismissed Beta")
        
        val scenario = fixture.launchMainActivityModern()
        
        // Switch to Dismissed tab
        withId(R.id.dismissedEventsFragment).click()
        
        // Wait for tab content to load
        withId(R.id.recycler_view).isDisplayed()
        
        // Both dismissed events visible
        withText("Dismissed Alpha").isDisplayed()
        withText("Dismissed Beta").isDisplayed()
        
        // Search for Alpha
        openSearchViewReliably(scenario)
        fixture.pushNavigation(2)
        withId(androidx.appcompat.R.id.search_src_text).replaceText("Alpha")
        
        // Only Alpha dismissed event visible
        withText("Dismissed Alpha").isDisplayed()
        withText("Dismissed Beta").doesNotExist()
        
        fixture.clearNavigationStack()
        scenario.close()
    }
    
    @Test
    fun search_state_clears_when_navigating_between_tabs() {
        fixture.createEvent(title = "Active Alpha")
        fixture.createEvent(title = "Active Beta")
        fixture.createDismissedEvent(title = "Dismissed Gamma")
        fixture.createDismissedEvent(title = "Dismissed Delta")
        
        val scenario = fixture.launchMainActivityModern()
        
        // Search on Active tab
        withText("Active Alpha").isDisplayed()
        openSearchViewReliably(scenario)
        fixture.pushNavigation(2)
        withId(androidx.appcompat.R.id.search_src_text).replaceText("Alpha")
        
        // Only Alpha visible
        withText("Active Alpha").isDisplayed()
        withText("Active Beta").doesNotExist()
        
        // Dismiss keyboard so we can click bottom nav (keyboard covers it)
        pressBack()
        fixture.popNavigation()
        
        // Switch to Dismissed tab using content description (more reliable for BottomNav items)
        withContentDescription(R.string.nav_dismissed).click()
        // Verify we're on Dismissed tab by checking toolbar title changed
        withText("Dismissed Events").isDisplayed()
        // Wait for actual data to load
        withText("Dismissed Gamma").isDisplayed()
        withText("Dismissed Delta").isDisplayed()
        
        // Switch back to Active tab
        withContentDescription(R.string.nav_active).click()
        // Verify we're back on Active tab by checking toolbar title
        withText("Calendar Notifications - Active").isDisplayed()
        // Wait for actual data to load (new fragment loads events asynchronously)
        withText("Active Alpha").isDisplayed()
        withText("Active Beta").isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun search_is_case_insensitive() {
        fixture.createEvent(title = "UPPERCASE EVENT")
        fixture.createEvent(title = "lowercase event")
        fixture.createEvent(title = "MixedCase Event")
        
        val scenario = fixture.launchMainActivityModern()
        
        // All events visible
        withText("UPPERCASE EVENT").isDisplayed()
        withText("lowercase event").isDisplayed()
        withText("MixedCase Event").isDisplayed()
        
        // Search with lowercase
        openSearchViewReliably(scenario)
        fixture.pushNavigation(2)
        withId(androidx.appcompat.R.id.search_src_text).replaceText("event")
        
        // All should match (case insensitive)
        withText("UPPERCASE EVENT").isDisplayed()
        withText("lowercase event").isDisplayed()
        withText("MixedCase Event").isDisplayed()
        
        // Clear and search with uppercase
        // Note: Close button collapses the search action view, clearing our navigation tracking
        withId(androidx.appcompat.R.id.search_close_btn).click()
        
        // Re-open search
        openSearchViewReliably(scenario)
        withId(androidx.appcompat.R.id.search_src_text).replaceText("MIXED")
        
        // Only MixedCase should match
        withText("MixedCase Event").isDisplayed()
        withText("UPPERCASE EVENT").doesNotExist()
        withText("lowercase event").doesNotExist()
        
        scenario.close()
    }
    
    @Test
    fun search_partial_match_works() {
        fixture.createEvent(title = "Important Meeting")
        fixture.createEvent(title = "Unimportant Task")
        fixture.createEvent(title = "Very Important Discussion")
        
        val scenario = fixture.launchMainActivityModern()
        
        // Wait for events to load first (ensures menu is properly set up)
        withText("Important Meeting").isDisplayed()
        
        // Search for partial word - use reliable helper to handle CI flakiness
        openSearchViewReliably(scenario)
        fixture.pushNavigation(2)
        withId(androidx.appcompat.R.id.search_src_text).replaceText("port")
        
        // All "port" containing events should match (Important, Unimportant)
        withText("Important Meeting").isDisplayed()
        withText("Unimportant Task").isDisplayed()
        withText("Very Important Discussion").isDisplayed()
        
        fixture.clearNavigationStack()
        scenario.close()
    }
    
    @Test
    fun search_no_results_shows_empty_state() {
        fixture.createEvent(title = "Alpha Event")
        fixture.createEvent(title = "Beta Event")
        
        val scenario = fixture.launchMainActivityModern()
        
        // Search for non-existent term
        openSearchViewReliably(scenario)
        fixture.pushNavigation(2)
        withId(androidx.appcompat.R.id.search_src_text).replaceText("ZZZZZZZ")
        
        // No events should be visible
        withText("Alpha Event").doesNotExist()
        withText("Beta Event").doesNotExist()
        
        // Empty view should be shown
        withId(R.id.empty_view).isDisplayed()
        
        fixture.clearNavigationStack()
        scenario.close()
    }
    
    // === Menu Items Per Tab Tests ===
    
    @Test
    fun snooze_all_menu_visible_on_active_tab() {
        fixture.seedEvents(2)
        
        val scenario = fixture.launchMainActivityModern()
        
        // Wait for events to load
        withText("Event 1").isDisplayed()
        
        // On Active tab - snooze all should be visible
        withId(R.id.action_snooze_all).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun snooze_all_menu_hidden_on_upcoming_tab() {
        fixture.seedEvents(2)
        
        val scenario = fixture.launchMainActivityModern()
        
        // Wait for events to load
        withText("Event 1").isDisplayed()
        
        // Switch to Upcoming tab
        withId(R.id.upcomingEventsFragment).click()
        
        // Wait for tab content to load
        withId(R.id.recycler_view).isDisplayed()
        
        // Snooze all menu item should not exist on Upcoming tab (isVisible = false removes it from hierarchy)
        withId(R.id.action_snooze_all).doesNotExist()
        
        scenario.close()
    }
    
    @Test
    fun dismiss_all_menu_visible_on_active_tab() {
        fixture.seedEvents(2)
        
        val scenario = fixture.launchMainActivityModern()
        
        // Wait for events to load
        withText("Event 1").isDisplayed()
        
        // Open overflow menu
        openActionBarOverflowOrOptionsMenu(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        
        // Dismiss all should be visible on Active tab
        withText(R.string.dismiss_all_events).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun dismiss_all_menu_hidden_on_dismissed_tab() {
        fixture.seedEvents(2)
        
        val scenario = fixture.launchMainActivityModern()
        
        // Switch to Dismissed tab
        withId(R.id.dismissedEventsFragment).click()
        
        // Open overflow menu
        openActionBarOverflowOrOptionsMenu(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        
        // Dismiss all should not be visible on Dismissed tab
        withText(R.string.dismiss_all_events).doesNotExist()
        
        scenario.close()
    }
    
    // === Helper Methods ===
    
    /**
     * Reliably opens the SearchView with multiple fallback strategies.
     * 
     * This addresses flakiness in CI where the SearchView expansion can fail due to:
     * 1. Menu not being fully interactive yet
     * 2. Click not registering properly
     * 3. Animation/timing issues on slower CI machines
     * 
     * Strategy:
     * 1. Wait for menu item to be displayed AND clickable
     * 2. Try clicking the menu item
     * 3. Check if SearchView expanded (search_src_text visible) with short timeout
     * 4. If not, fall back to programmatic expansion via activity
     * 5. Retry the entire flow up to 3 times
     */
    private fun openSearchViewReliably(scenario: ActivityScenario<MainActivityModern>) {
        val maxAttempts = 3
        val shortTimeout = 2000L
        
        for (attempt in 1..maxAttempts) {
            // Strategy 1: Ensure menu item is fully ready before clicking
            withId(R.id.action_search).isDisplayed()
            withId(R.id.action_search).isClickable()
            
            // Strategy 2: Try clicking the menu item
            withId(R.id.action_search).click()
            
            // Check if SearchView expanded with a short timeout
            val expanded = try {
                withId(androidx.appcompat.R.id.search_src_text)
                    .withTimeout(shortTimeout)
                    .isDisplayed()
                true
            } catch (e: Exception) {
                false
            }
            
            if (expanded) {
                return // Success!
            }
            
            // Strategy 3: Fall back to programmatic expansion
            if (attempt < maxAttempts) {
                scenario.onActivity { activity ->
                    activity.searchMenuItem?.expandActionView()
                }
                
                // Check again after programmatic expansion
                val expandedProgrammatically = try {
                    withId(androidx.appcompat.R.id.search_src_text)
                        .withTimeout(shortTimeout)
                        .isDisplayed()
                    true
                } catch (e: Exception) {
                    false
                }
                
                if (expandedProgrammatically) {
                    return // Success via programmatic expansion!
                }
            }
        }
        
        // Final attempt - use standard assertion which will throw with full error details
        withId(androidx.appcompat.R.id.search_src_text).isDisplayed()
    }
    
    // Inherits setConfig() from BaseUltronTest
}
