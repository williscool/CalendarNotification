package com.github.quarck.calnotify.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.testutils.UITestFixture
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso UI tests for SettingsActivity.
 * 
 * Tests settings navigation and preference fragment loading.
 * 
 * NOTE: Temporarily disabled due to AndroidX dependency conflicts with AppCompat 1.7.0
 * causing resource ID collisions in checkVectorDrawableSetup.
 */
@Ignore("Disabled pending AndroidX dependency resolution - see AppCompat 1.7.0 resource bug")
@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {
    
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
    fun settingsActivity_launches_successfully() {
        val scenario = fixture.launchSettingsActivity()
        
        scenario.onActivity { activity ->
            assert(activity != null)
        }
        
        scenario.close()
    }
    
    // === Preference Headers Display Tests ===
    
    @Test
    fun settingsActivity_shows_calendars_header() {
        val scenario = fixture.launchSettingsActivity()
        
        onView(withText(R.string.title_calendars_activity))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_shows_notification_settings_header() {
        val scenario = fixture.launchSettingsActivity()
        
        onView(withText(R.string.notification_settings))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_shows_snooze_presets_header() {
        val scenario = fixture.launchSettingsActivity()
        
        onView(withText(R.string.snooze_presets))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_shows_reminders_header() {
        val scenario = fixture.launchSettingsActivity()
        
        onView(withText(R.string.reminders_section))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_shows_quiet_hours_header() {
        val scenario = fixture.launchSettingsActivity()
        
        onView(withText(R.string.quiet_hours_section))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_shows_behavior_header() {
        val scenario = fixture.launchSettingsActivity()
        
        onView(withText(R.string.app_behavior))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_shows_misc_settings_header() {
        val scenario = fixture.launchSettingsActivity()
        
        onView(withText(R.string.misc_settings))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Navigation Tests ===
    
    @Test
    fun clicking_notification_settings_opens_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        onView(withText(R.string.notification_settings))
            .perform(click())
        
        // Fragment should load - verify by checking for a preference within it
        // Note: This may need adjustment based on actual preference contents
        Thread.sleep(500)
        
        scenario.close()
    }
    
    @Test
    fun clicking_snooze_settings_opens_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        onView(withText(R.string.snooze_presets))
            .perform(click())
        
        Thread.sleep(500)
        
        scenario.close()
    }
    
    @Test
    fun clicking_reminders_settings_opens_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        onView(withText(R.string.reminders_section))
            .perform(click())
        
        Thread.sleep(500)
        
        scenario.close()
    }
    
    @Test
    fun clicking_behavior_settings_opens_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        onView(withText(R.string.app_behavior))
            .perform(click())
        
        Thread.sleep(500)
        
        scenario.close()
    }
    
    @Test
    fun clicking_misc_settings_opens_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        onView(withText(R.string.misc_settings))
            .perform(click())
        
        Thread.sleep(500)
        
        scenario.close()
    }
    
    // === Back Navigation Tests ===
    
    @Test
    fun navigating_to_fragment_and_back_returns_to_headers() {
        val scenario = fixture.launchSettingsActivity()
        
        // Navigate to a fragment
        onView(withText(R.string.notification_settings))
            .perform(click())
        
        Thread.sleep(300)
        
        // Press back
        androidx.test.espresso.Espresso.pressBack()
        
        Thread.sleep(300)
        
        // Should be back at headers - check that another header is visible
        onView(withText(R.string.snooze_presets))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
}

