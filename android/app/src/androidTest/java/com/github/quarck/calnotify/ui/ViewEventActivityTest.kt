package com.github.quarck.calnotify.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atiurin.ultron.core.config.UltronConfig
import com.atiurin.ultron.extensions.click
import com.atiurin.ultron.extensions.hasText
import com.atiurin.ultron.extensions.isDisplayed
import com.atiurin.ultron.extensions.isNotDisplayed
import com.atiurin.ultron.core.espresso.UltronEspresso.withId
import com.atiurin.ultron.core.espresso.UltronEspresso.withClassName
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.testutils.UITestFixture
import io.mockk.*
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ultron UI tests for ViewEventActivityNoRecents.
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
        
        withId(R.id.snooze_view_title).isDisplayed().hasText("Important Meeting")
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_displays_event_date() {
        val event = fixture.createEvent(title = "Date Test Event")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        // Date line should be displayed
        withId(R.id.snooze_view_event_date_line1).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_displays_location_when_present() {
        val event = fixture.createEvent(
            title = "Event with Location",
            location = "Conference Room A"
        )
        
        val scenario = fixture.launchViewEventActivity(event)
        
        withId(R.id.snooze_view_location).isDisplayed().hasText("Conference Room A")
        
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
        withId(R.id.snooze_view_location_layout).isNotDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_displays_calendar_name() {
        val event = fixture.createEvent(title = "Calendar Test")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        withId(R.id.view_event_calendar_name).isDisplayed()
        
        scenario.close()
    }
    
    // === Snooze Preset Button Tests ===
    
    @Test
    fun viewEventActivity_shows_snooze_preset_buttons() {
        val event = fixture.createEvent()
        
        val scenario = fixture.launchViewEventActivity(event)
        
        withId(R.id.snooze_view_snooze_present1).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_shows_custom_snooze_button() {
        val event = fixture.createEvent()
        
        val scenario = fixture.launchViewEventActivity(event)
        
        withId(R.id.snooze_view_snooze_custom).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_shows_snooze_until_button() {
        val event = fixture.createEvent()
        
        val scenario = fixture.launchViewEventActivity(event)
        
        withId(R.id.snooze_view_snooze_until).isDisplayed()
        
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
        withId(R.id.snooze_view_snooze_present1).click()
        
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
        
        withId(R.id.snooze_view_snooze_custom).click()
        
        // Should show number picker dialog
        withId(R.id.numberPickerTimeInterval).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun clicking_snooze_until_opens_date_picker() {
        val event = fixture.createEvent()
        val scenario = fixture.launchViewEventActivity(event)
        
        withId(R.id.snooze_view_snooze_until).click()
        
        // Should show date picker
        withClassName(containsString("DatePicker")).isDisplayed()
        
        scenario.close()
    }
    
    // === Snoozed Event Display Tests ===
    
    @Test
    fun snoozed_event_shows_change_snooze_header() {
        val event = fixture.createSnoozedEvent(title = "Already Snoozed")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        // The header text should indicate changing snooze time
        withId(R.id.snooze_snooze_for).isDisplayed().hasText(R.string.change_snooze_to)
        
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
        withId(R.id.snooze_view_event_details_layout).isDisplayed()
        
        scenario.close()
    }
    
    // === Empty Title Handling ===
    
    @Test
    fun viewEventActivity_shows_placeholder_for_empty_title() {
        // Create event with empty title
        val event = fixture.createEvent(title = "")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        // Should show empty title placeholder
        withId(R.id.snooze_view_title).isDisplayed().hasText(R.string.empty_title)
        
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
        withId(R.id.layout_event_description).isDisplayed()
        
        withId(R.id.snooze_view_event_description).hasText("This is the event description")
        
        scenario.close()
    }
    
    // === Toolbar Tests ===
    
    @Test
    fun viewEventActivity_has_back_navigation() {
        val event = fixture.createEvent()
        
        val scenario = fixture.launchViewEventActivity(event)
        
        // Toolbar should be present
        withId(R.id.toolbar).isDisplayed()
        
        scenario.close()
    }
    
    companion object {
        @BeforeClass @JvmStatic
        fun setConfig() {
            UltronConfig.applyRecommended()
        }
    }
}
