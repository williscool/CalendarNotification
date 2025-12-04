package com.github.quarck.calnotify.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * Espresso UI tests for ViewEventActivityNoRecents.
 * 
 * Tests event detail display, snooze actions, and dismiss functionality.
 */
@RunWith(AndroidJUnit4::class)
class ViewEventActivityTest {
    
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
    fun viewEventActivity_launches_for_event() {
        val event = fixture.createEvent(title = "Test Event")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        scenario.onActivity { activity ->
            assert(activity != null)
        }
        
        scenario.close()
    }
    
    // === Event Details Display Tests ===
    
    @Test
    fun viewEventActivity_displays_event_title() {
        val event = fixture.createEvent(title = "Important Meeting")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        onView(withId(R.id.snooze_view_title))
            .check(matches(isDisplayed()))
            .check(matches(withText("Important Meeting")))
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_displays_event_date() {
        val event = fixture.createEvent(title = "Date Test Event")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        // Date line should be displayed
        onView(withId(R.id.snooze_view_event_date_line1))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_displays_location_when_present() {
        val event = fixture.createEvent(
            title = "Event with Location",
            location = "Conference Room A"
        )
        
        val scenario = fixture.launchViewEventActivity(event)
        
        onView(withId(R.id.snooze_view_location))
            .check(matches(isDisplayed()))
            .check(matches(withText("Conference Room A")))
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_hides_location_when_empty() {
        val event = fixture.createEvent(
            title = "Event without Location",
            location = ""
        )
        
        val scenario = fixture.launchViewEventActivity(event)
        
        // Location layout should not be visible
        onView(withId(R.id.snooze_view_location_layout))
            .check(matches(not(isDisplayed())))
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_displays_calendar_name() {
        val event = fixture.createEvent(title = "Calendar Test")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        onView(withId(R.id.view_event_calendar_name))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Snooze Preset Button Tests ===
    
    @Test
    fun viewEventActivity_shows_snooze_preset_buttons() {
        val event = fixture.createEvent()
        
        val scenario = fixture.launchViewEventActivity(event)
        
        onView(withId(R.id.snooze_view_snooze_present1))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_shows_custom_snooze_button() {
        val event = fixture.createEvent()
        
        val scenario = fixture.launchViewEventActivity(event)
        
        onView(withId(R.id.snooze_view_snooze_custom))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_shows_snooze_until_button() {
        val event = fixture.createEvent()
        
        val scenario = fixture.launchViewEventActivity(event)
        
        onView(withId(R.id.snooze_view_snooze_until))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Snooze Action Tests ===
    
    @Test
    fun clicking_snooze_preset_triggers_snooze() {
        fixture.mockApplicationController()
        every { ApplicationController.snoozeEvent(any(), any(), any(), any()) } returns mockk(relaxed = true)
        
        val event = fixture.createEvent(title = "Snooze Test")
        val scenario = fixture.launchViewEventActivity(event)
        
        // Click first snooze preset
        onView(withId(R.id.snooze_view_snooze_present1))
            .perform(click())
        
        // Verify snooze was called
        verify(timeout = 2000) { 
            ApplicationController.snoozeEvent(any(), any(), any(), any()) 
        }
        
        scenario.close()
    }
    
    @Test
    fun clicking_custom_snooze_opens_dialog() {
        val event = fixture.createEvent()
        val scenario = fixture.launchViewEventActivity(event)
        
        onView(withId(R.id.snooze_view_snooze_custom))
            .perform(click())
        
        // Should show number picker dialog
        onView(withId(R.id.numberPickerTimeInterval))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun clicking_snooze_until_opens_date_picker() {
        val event = fixture.createEvent()
        val scenario = fixture.launchViewEventActivity(event)
        
        onView(withId(R.id.snooze_view_snooze_until))
            .perform(click())
        
        // Should show date picker
        onView(withClassName(org.hamcrest.Matchers.containsString("DatePicker")))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Snoozed Event Display Tests ===
    
    @Test
    fun snoozed_event_shows_change_snooze_header() {
        val event = fixture.createSnoozedEvent(title = "Already Snoozed")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        // The header text should indicate changing snooze time
        onView(withId(R.id.snooze_snooze_for))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.change_snooze_to)))
        
        scenario.close()
    }
    
    // === Event Color Display Tests ===
    
    @Test
    fun viewEventActivity_displays_event_details_layout() {
        val event = fixture.createEvent(
            title = "Colored Event",
            color = 0xFF6200EE.toInt()
        )
        
        val scenario = fixture.launchViewEventActivity(event)
        
        // The event details layout should be visible
        onView(withId(R.id.snooze_view_event_details_layout))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Empty Title Handling ===
    
    @Test
    fun viewEventActivity_shows_placeholder_for_empty_title() {
        // Create event with empty title
        val event = fixture.createEvent(title = "")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        // Should show empty title placeholder
        onView(withId(R.id.snooze_view_title))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.empty_title)))
        
        scenario.close()
    }
    
    // === Description Display Tests ===
    
    @Test
    fun viewEventActivity_shows_description_when_present() {
        val event = fixture.createEvent(
            title = "Event with Description",
            description = "This is the event description"
        )
        
        val scenario = fixture.launchViewEventActivity(event)
        
        // Description layout should be visible
        onView(withId(R.id.layout_event_description))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.snooze_view_event_description))
            .check(matches(withText("This is the event description")))
        
        scenario.close()
    }
    
    // === Toolbar Tests ===
    
    @Test
    fun viewEventActivity_has_back_navigation() {
        val event = fixture.createEvent()
        
        val scenario = fixture.launchViewEventActivity(event)
        
        // Toolbar should be present
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
}

