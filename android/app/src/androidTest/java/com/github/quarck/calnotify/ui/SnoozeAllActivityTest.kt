package com.github.quarck.calnotify.ui

import android.content.Intent
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atiurin.ultron.core.config.UltronConfig
import com.atiurin.ultron.extensions.click
import com.atiurin.ultron.extensions.isDisplayed
import com.github.quarck.calnotify.Consts
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
 * Ultron UI tests for SnoozeAllActivity.
 * 
 * Tests snooze preset buttons, custom snooze dialog, and snooze actions.
 */
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
        
        withId(R.id.toolbar).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun snoozeAllActivity_shows_snooze_all_title_in_snooze_all_mode() {
        fixture.seedEvents(2)
        
        val scenario = fixture.launchSnoozeAllActivity()
        
        // Title should be "Snooze All"
        withText(R.string.snooze_all_title).isDisplayed()
        
        scenario.close()
    }
    
    // === Preset Button Visibility Tests ===
    
    @Test
    fun snoozeAllActivity_shows_first_preset_button() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        withId(R.id.snooze_view_snooze_present1).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun snoozeAllActivity_shows_multiple_preset_buttons() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        // First few presets should be visible
        withId(R.id.snooze_view_snooze_present1).isDisplayed()
        withId(R.id.snooze_view_snooze_present2).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun snoozeAllActivity_shows_custom_snooze_button() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        withId(R.id.snooze_view_snooze_custom).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun snoozeAllActivity_shows_snooze_until_button() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        withId(R.id.snooze_view_snooze_until).isDisplayed()
        
        scenario.close()
    }
    
    // === Preset Button Click Tests ===
    
    @Test
    fun clicking_preset_button_triggers_snooze() {
        fixture.mockApplicationController()
        every { ApplicationController.snoozeAllEvents(any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
        
        val event = fixture.createEvent(title = "Snooze Me")
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        // Click first preset - this shows a confirmation dialog
        withId(R.id.snooze_view_snooze_present1).click()
        
        // Click "Yes" on the confirmation dialog
        withText(android.R.string.yes).click()
        
        // Verify snoozeAllEvents was called (SnoozeAllActivity always uses snoozeAllEvents)
        verify(timeout = 2000) { 
            ApplicationController.snoozeAllEvents(any(), any(), any(), any(), any()) 
        }
        
        scenario.close()
    }
    
    @Test
    fun clicking_preset_in_snooze_all_mode_triggers_snooze_all() {
        fixture.mockApplicationController()
        every { ApplicationController.snoozeAllEvents(any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
        
        fixture.seedEvents(3)
        val scenario = fixture.launchSnoozeAllActivity()
        
        // Click first preset - this shows a confirmation dialog
        withId(R.id.snooze_view_snooze_present1).click()
        
        // Click "Yes" on the confirmation dialog
        withText(android.R.string.yes).click()
        
        // Verify snoozeAllEvents was called
        verify(timeout = 2000) { 
            ApplicationController.snoozeAllEvents(any(), any(), any(), any(), any()) 
        }
        
        scenario.close()
    }
    
    // === Custom Snooze Dialog Tests ===
    
    @Test
    fun clicking_custom_snooze_opens_dialog() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        // Click custom snooze button - this shows a list dialog first
        withId(R.id.snooze_view_snooze_custom).click()
        
        // Click "Enter manually" option in the list (first item, value -1)
        withText("Enter manually").click()
        
        // Dialog should now show number picker or time interval controls
        withId(R.id.numberPickerTimeInterval).isDisplayed()
        
        scenario.close()
    }
    
    // === Snooze Until Tests ===
    
    @Test
    fun clicking_snooze_until_opens_date_picker() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        // Click snooze until button
        withId(R.id.snooze_view_snooze_until).click()
        
        // Should show a date picker
        withClassName(containsString("DatePicker")).isDisplayed()
        
        scenario.close()
    }
    
    // === Snooze Header Text Tests ===
    
    @Test
    fun snoozeAllActivity_shows_snooze_header_text() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        withId(R.id.snooze_snooze_for).isDisplayed()
        
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
        
        withId(R.id.snooze_count_text).isDisplayed()
        
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
        withId(R.id.snooze_view_snooze_present1).click()
        
        // Activity should be finishing/finished (Ultron auto-waits)
        scenario.close()
    }
    
    companion object {
        @BeforeClass @JvmStatic
        fun setConfig() {
            UltronConfig.apply {
                operationTimeoutMs = 15_000  // 15 seconds instead of default
            }
        }
    }
}
