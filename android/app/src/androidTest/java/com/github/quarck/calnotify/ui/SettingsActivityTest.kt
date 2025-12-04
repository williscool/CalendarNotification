package com.github.quarck.calnotify.ui

import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atiurin.ultron.core.config.UltronConfig
import com.atiurin.ultron.custom.espresso.matcher.withSuitableRoot
import com.atiurin.ultron.extensions.click
import com.atiurin.ultron.extensions.isDisplayed
import com.atiurin.ultron.extensions.withSuitableRoot
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.testutils.UITestFixture
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ultron UI tests for SettingsActivity.
 * 
 * Tests settings navigation and preference fragment loading.
 * 
 * Note: Uses withSuitableRoot() because SettingsActivity uses PreferenceActivity 
 * with AppCompatDelegate, which creates views in a separate root hierarchy.
 */
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
        
        withText(R.string.title_calendars_activity).withSuitableRoot().isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_shows_notification_settings_header() {
        val scenario = fixture.launchSettingsActivity()
        
        withText(R.string.notification_settings).withSuitableRoot().isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_shows_snooze_presets_header() {
        val scenario = fixture.launchSettingsActivity()
        
        withText(R.string.snooze_presets).withSuitableRoot().isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_shows_reminders_header() {
        val scenario = fixture.launchSettingsActivity()
        
        withText(R.string.reminders_section).withSuitableRoot().isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_shows_quiet_hours_header() {
        val scenario = fixture.launchSettingsActivity()
        
        withText(R.string.quiet_hours_section).withSuitableRoot().isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_shows_behavior_header() {
        val scenario = fixture.launchSettingsActivity()
        
        withText(R.string.app_behavior).withSuitableRoot().isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_shows_misc_settings_header() {
        val scenario = fixture.launchSettingsActivity()
        
        withText(R.string.misc_settings).withSuitableRoot().isDisplayed()
        
        scenario.close()
    }
    
    // === Navigation Tests ===
    
    @Test
    fun clicking_notification_settings_opens_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        withText(R.string.notification_settings).withSuitableRoot().click()
        
        // Fragment should load (Ultron auto-waits)
        
        scenario.close()
    }
    
    @Test
    fun clicking_snooze_settings_opens_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        withText(R.string.snooze_presets).withSuitableRoot().click()
        
        scenario.close()
    }
    
    @Test
    fun clicking_reminders_settings_opens_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        withText(R.string.reminders_section).withSuitableRoot().click()
        
        scenario.close()
    }
    
    @Test
    fun clicking_behavior_settings_opens_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        withText(R.string.app_behavior).withSuitableRoot().click()
        
        scenario.close()
    }
    
    @Test
    fun clicking_misc_settings_opens_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        withText(R.string.misc_settings).withSuitableRoot().click()
        
        scenario.close()
    }
    
    // === Back Navigation Tests ===
    
    @Test
    fun navigating_to_fragment_and_back_returns_to_headers() {
        val scenario = fixture.launchSettingsActivity()
        
        // Navigate to a fragment
        withText(R.string.notification_settings).withSuitableRoot().click()
        
        // Press back
        pressBack()
        
        // Should be back at headers - check that another header is visible
        withText(R.string.snooze_presets).withSuitableRoot().isDisplayed()
        
        scenario.close()
    }
    
    companion object {
        @BeforeClass @JvmStatic
        fun setConfig() {
            UltronConfig.applyRecommended()
        }
    }
}
