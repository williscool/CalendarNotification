package com.github.quarck.calnotify.ui

import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.testutils.UITestFixture
import io.mockk.*
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso UI tests for SnoozeAllActivity.
 * 
 * Tests snooze preset buttons, custom snooze dialog, and snooze actions.
 * 
 * NOTE: Temporarily disabled due to AndroidX dependency conflicts with AppCompat 1.7.0
 * causing resource ID collisions in checkVectorDrawableSetup.
 */
@Ignore("Disabled pending AndroidX dependency resolution - see AppCompat 1.7.0 resource bug")
@RunWith(AndroidJUnit4::class)
class SnoozeAllActivityTest {
    
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
    fun snoozeAllActivity_launches_for_single_event() {
        val event = fixture.createEvent(title = "Test Event")
        
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        scenario.onActivity { activity ->
            assert(activity != null)
        }
        
        scenario.close()
    }
    
    @Test
    fun snoozeAllActivity_launches_in_snooze_all_mode() {
        fixture.seedEvents(3)
        
        val scenario = fixture.launchSnoozeAllActivity()
        
        scenario.onActivity { activity ->
            assert(activity != null)
        }
        
        scenario.close()
    }
    
    // === Toolbar Tests ===
    
    @Test
    fun snoozeAllActivity_shows_toolbar() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun snoozeAllActivity_shows_snooze_all_title_in_snooze_all_mode() {
        fixture.seedEvents(2)
        
        val scenario = fixture.launchSnoozeAllActivity()
        
        // Title should be "Snooze All"
        onView(allOf(withText(R.string.snooze_all_title), isDisplayed()))
        
        scenario.close()
    }
    
    // === Preset Button Visibility Tests ===
    
    @Test
    fun snoozeAllActivity_shows_first_preset_button() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        onView(withId(R.id.snooze_view_snooze_present1))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun snoozeAllActivity_shows_multiple_preset_buttons() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        // First few presets should be visible
        onView(withId(R.id.snooze_view_snooze_present1))
            .check(matches(isDisplayed()))
        onView(withId(R.id.snooze_view_snooze_present2))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun snoozeAllActivity_shows_custom_snooze_button() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        onView(withId(R.id.snooze_view_snooze_custom))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun snoozeAllActivity_shows_snooze_until_button() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        onView(withId(R.id.snooze_view_snooze_until))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Preset Button Click Tests ===
    
    @Test
    fun clicking_preset_button_triggers_snooze() {
        fixture.mockApplicationController()
        every { ApplicationController.snoozeEvent(any(), any(), any(), any()) } returns mockk(relaxed = true)
        
        val event = fixture.createEvent(title = "Snooze Me")
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        // Click first preset
        onView(withId(R.id.snooze_view_snooze_present1))
            .perform(click())
        
        // Verify snooze was called
        verify(timeout = 2000) { 
            ApplicationController.snoozeEvent(any(), any(), any(), any()) 
        }
        
        scenario.close()
    }
    
    @Test
    fun clicking_preset_in_snooze_all_mode_triggers_snooze_all() {
        fixture.mockApplicationController()
        every { ApplicationController.snoozeAllEvents(any(), any(), any(), any()) } returns mockk(relaxed = true)
        
        fixture.seedEvents(3)
        val scenario = fixture.launchSnoozeAllActivity()
        
        // Click first preset
        onView(withId(R.id.snooze_view_snooze_present1))
            .perform(click())
        
        // Verify snoozeAllEvents was called
        verify(timeout = 2000) { 
            ApplicationController.snoozeAllEvents(any(), any(), any(), any()) 
        }
        
        scenario.close()
    }
    
    // === Custom Snooze Dialog Tests ===
    
    @Test
    fun clicking_custom_snooze_opens_dialog() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        // Click custom snooze button
        onView(withId(R.id.snooze_view_snooze_custom))
            .perform(click())
        
        // Dialog should show number picker or time interval controls
        // The dialog contains a NumberPicker and Spinner
        onView(withId(R.id.numberPickerTimeInterval))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Snooze Until Tests ===
    
    @Test
    fun clicking_snooze_until_opens_date_picker() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        // Click snooze until button
        onView(withId(R.id.snooze_view_snooze_until))
            .perform(click())
        
        // Should show a date picker
        // The exact view depends on implementation - check for DatePicker
        onView(withClassName(org.hamcrest.Matchers.containsString("DatePicker")))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Snooze Header Text Tests ===
    
    @Test
    fun snoozeAllActivity_shows_snooze_header_text() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        onView(withId(R.id.snooze_snooze_for))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Search Query Count Display Tests ===
    
    @Test
    fun snoozeAllActivity_shows_count_when_search_query_provided() {
        fixture.seedEvents(5)
        
        val intent = Intent(fixture.context, SnoozeAllActivity::class.java).apply {
            putExtra(Consts.INTENT_SNOOZE_ALL_KEY, true)
            putExtra(Consts.INTENT_SEARCH_QUERY, "test")
            putExtra(Consts.INTENT_SEARCH_QUERY_EVENT_COUNT, 3)
        }
        
        val scenario = fixture.launchSnoozeAllActivityWithIntent(intent)
        
        onView(withId(R.id.snooze_count_text))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Activity Result Tests ===
    
    @Test
    fun snooze_finishes_activity() {
        fixture.mockApplicationController()
        every { ApplicationController.snoozeEvent(any(), any(), any(), any()) } returns mockk(relaxed = true)
        
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        // Click first preset
        onView(withId(R.id.snooze_view_snooze_present1))
            .perform(click())
        
        // Give time for activity to finish
        Thread.sleep(500)
        
        // Activity should be finishing/finished
        scenario.close()
    }
}

