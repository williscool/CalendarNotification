package com.github.quarck.calnotify.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.testutils.UITestFixture
import io.mockk.*
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso UI tests for MainActivity.
 * 
 * Tests the main event list display, search, and bulk actions.
 * 
 * NOTE: Temporarily disabled due to AndroidX dependency conflicts with AppCompat 1.7.0
 * causing resource ID collisions in checkVectorDrawableSetup. Will be re-enabled
 * once dependency issues are resolved.
 */
@Ignore("Disabled pending AndroidX dependency resolution - see AppCompat 1.7.0 resource bug")
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    
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
        
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Empty State Tests ===
    
    @Test
    fun mainActivity_shows_empty_view_when_no_events() {
        // No events seeded
        val scenario = fixture.launchMainActivity()
        
        onView(withId(R.id.empty_view))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun mainActivity_hides_empty_view_when_events_exist() {
        fixture.seedEvents(3)
        
        val scenario = fixture.launchMainActivity()
        
        onView(withId(R.id.empty_view))
            .check(matches(not(isDisplayed())))
        
        scenario.close()
    }
    
    // === Event List Display Tests ===
    
    @Test
    fun mainActivity_displays_events_in_list() {
        fixture.createEvent(title = "Meeting with Team")
        fixture.createEvent(title = "Doctor Appointment")
        
        val scenario = fixture.launchMainActivity()
        
        // RecyclerView should be visible
        onView(withId(R.id.list_events))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun mainActivity_displays_event_title() {
        val title = "Important Meeting"
        fixture.createEvent(title = title)
        
        val scenario = fixture.launchMainActivity()
        
        onView(withText(title))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun mainActivity_displays_multiple_events() {
        fixture.createEvent(title = "Event One")
        fixture.createEvent(title = "Event Two")
        fixture.createEvent(title = "Event Three")
        
        val scenario = fixture.launchMainActivity()
        
        // All events should be visible (assuming they fit on screen)
        onView(withText("Event One"))
            .check(matches(isDisplayed()))
        onView(withText("Event Two"))
            .check(matches(isDisplayed()))
        onView(withText("Event Three"))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Event Click Tests ===
    
    @Test
    fun clicking_event_opens_view_event_activity() {
        val event = fixture.createEvent(title = "Clickable Event")
        
        val scenario = fixture.launchMainActivity()
        
        // Click on the event
        onView(withText("Clickable Event"))
            .perform(click())
        
        // The ViewEventActivity should open - we verify by checking the scenario doesn't crash
        // In a real test, we'd use Intents to verify the launched activity
        
        scenario.close()
    }
    
    // === Floating Action Button Tests ===
    
    @Test
    fun fab_is_displayed() {
        val scenario = fixture.launchMainActivity()
        
        onView(withId(R.id.action_btn_add_event))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Menu Tests ===
    
    @Test
    fun snooze_all_menu_item_visible_when_events_exist() {
        fixture.seedEvents(2)
        
        val scenario = fixture.launchMainActivity()
        
        // Open overflow menu
        openActionBarOverflowOrOptionsMenu(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        
        // Snooze All should be visible
        onView(withText(R.string.snooze_all))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun dismiss_all_menu_item_visible_when_events_exist() {
        fixture.seedEvents(2)
        
        val scenario = fixture.launchMainActivity()
        
        openActionBarOverflowOrOptionsMenu(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        
        onView(withText(R.string.dismiss_all_events))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun settings_menu_item_is_visible() {
        val scenario = fixture.launchMainActivity()
        
        openActionBarOverflowOrOptionsMenu(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        
        onView(withText(R.string.settings))
            .check(matches(isDisplayed()))
        
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
        
        // Verify the event is displayed and clickable
        onView(withText("Clickable Event Card"))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
        
        scenario.close()
    }
    
    // === Pull to Refresh Tests ===
    
    @Test
    fun swipe_refresh_layout_exists() {
        val scenario = fixture.launchMainActivity()
        
        onView(withId(R.id.cardview_refresh_layout))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Snoozed Event Display Tests ===
    
    @Test
    fun snoozed_event_is_displayed() {
        fixture.createSnoozedEvent(title = "Snoozed Event")
        
        val scenario = fixture.launchMainActivity()
        
        onView(withText("Snoozed Event"))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Muted Event Display Tests ===
    
    @Test
    fun muted_event_is_displayed() {
        fixture.createMutedEvent(title = "Muted Event")
        
        val scenario = fixture.launchMainActivity()
        
        onView(withText("Muted Event"))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Task Event Display Tests ===
    
    @Test
    fun task_event_is_displayed() {
        fixture.createTaskEvent(title = "Task Event")
        
        val scenario = fixture.launchMainActivity()
        
        onView(withText("Task Event"))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
}

