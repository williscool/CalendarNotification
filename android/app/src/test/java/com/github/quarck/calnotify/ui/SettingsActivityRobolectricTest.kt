package com.github.quarck.calnotify.ui

import android.content.Intent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.prefs.*
import com.github.quarck.calnotify.testutils.UITestFixtureRobolectric
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config

/**
 * Robolectric UI tests for SettingsActivityX.
 * 
 * Tests settings navigation and preference fragment loading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class SettingsActivityRobolectricTest {
    
    private lateinit var fixture: UITestFixtureRobolectric
    
    @Before
    fun setup() {
        fixture = UITestFixtureRobolectric.create()
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
        
        scenario.onActivity { activity: SettingsActivityX ->
            assertNotNull(activity)
        }
        
        scenario.close()
    }
    
    // === Preference Headers Display Tests ===
    
    @Test
    fun settingsActivity_shows_preference_container() {
        val scenario = fixture.launchSettingsActivity()
        
        scenario.onActivity { activity: SettingsActivityX ->
            // AndroidX Preferences use a FrameLayout container instead of android.R.id.list
            val container = activity.findViewById<View>(R.id.settings_container)
            assertNotNull(container)
            assertEquals(View.VISIBLE, container.visibility)
        }
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_has_content_view() {
        val scenario = fixture.launchSettingsActivity()
        
        scenario.onActivity { activity: SettingsActivityX ->
            val contentView = activity.findViewById<View>(android.R.id.content)
            assertNotNull(contentView)
        }
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_initially_shows_headers_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        scenario.onActivity { activity: SettingsActivityX ->
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.settings_container)
            assertNotNull("Should have a fragment in container", fragment)
            assertTrue("Should be PreferenceHeadersFragment", fragment is PreferenceHeadersFragment)
        }
        
        scenario.close()
    }
    
    // === Fragment Navigation Tests ===
    
    @Test
    fun navigating_to_notification_settings_loads_correct_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        scenario.onActivity { activity: SettingsActivityX ->
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, NotificationSettingsFragmentX())
                .addToBackStack(null)
                .commit()
            
            activity.supportFragmentManager.executePendingTransactions()
            
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.settings_container)
            assertNotNull("Should have a fragment", fragment)
            assertTrue("Should be NotificationSettingsFragmentX", fragment is NotificationSettingsFragmentX)
        }
        
        scenario.close()
    }
    
    @Test
    fun navigating_to_snooze_settings_loads_correct_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        scenario.onActivity { activity: SettingsActivityX ->
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SnoozeSettingsFragmentX())
                .addToBackStack(null)
                .commit()
            
            activity.supportFragmentManager.executePendingTransactions()
            
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.settings_container)
            assertTrue("Should be SnoozeSettingsFragmentX", fragment is SnoozeSettingsFragmentX)
        }
        
        scenario.close()
    }
    
    @Test
    fun navigating_to_reminder_settings_loads_correct_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        scenario.onActivity { activity: SettingsActivityX ->
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, ReminderSettingsFragmentX())
                .addToBackStack(null)
                .commit()
            
            activity.supportFragmentManager.executePendingTransactions()
            
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.settings_container)
            assertTrue("Should be ReminderSettingsFragmentX", fragment is ReminderSettingsFragmentX)
        }
        
        scenario.close()
    }
    
    @Test
    fun navigating_to_quiet_hours_settings_loads_correct_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        scenario.onActivity { activity: SettingsActivityX ->
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, QuietHoursSettingsFragmentX())
                .addToBackStack(null)
                .commit()
            
            activity.supportFragmentManager.executePendingTransactions()
            
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.settings_container)
            assertTrue("Should be QuietHoursSettingsFragmentX", fragment is QuietHoursSettingsFragmentX)
        }
        
        scenario.close()
    }
    
    @Test
    fun navigating_to_behavior_settings_loads_correct_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        scenario.onActivity { activity: SettingsActivityX ->
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, BehaviorSettingsFragmentX())
                .addToBackStack(null)
                .commit()
            
            activity.supportFragmentManager.executePendingTransactions()
            
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.settings_container)
            assertTrue("Should be BehaviorSettingsFragmentX", fragment is BehaviorSettingsFragmentX)
        }
        
        scenario.close()
    }
    
    @Test
    fun navigating_to_misc_settings_loads_correct_fragment() {
        val scenario = fixture.launchSettingsActivity()
        
        scenario.onActivity { activity: SettingsActivityX ->
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, MiscSettingsFragmentX())
                .addToBackStack(null)
                .commit()
            
            activity.supportFragmentManager.executePendingTransactions()
            
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.settings_container)
            assertTrue("Should be MiscSettingsFragmentX", fragment is MiscSettingsFragmentX)
        }
        
        scenario.close()
    }

    // === Upcoming Events Help Tests ===

    /** Opens the navigation settings screen and hands back its fragment */
    private fun showNavigationSettings(activity: SettingsActivityX): NavigationSettingsFragmentX {
        val fragment = NavigationSettingsFragmentX()
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, fragment)
            .commit()
        activity.supportFragmentManager.executePendingTransactions()
        return fragment
    }

    @Test
    fun upcoming_events_help_follows_new_navigation_toggle() {
        val scenario = fixture.launchSettingsActivity()

        scenario.onActivity { activity: SettingsActivityX ->
            val fragment = showNavigationSettings(activity)
            val toggle = fragment.findPreference<CheckBoxPreference>("use_new_navigation_ui")!!
            val help = fragment.findPreference<Preference>("upcoming_events_help")!!

            toggle.isChecked = true
            assertTrue("Help should be enabled with the new UI", help.isEnabled)

            toggle.isChecked = false
            assertFalse("Help should be disabled without the Upcoming tab", help.isEnabled)
        }

        scenario.close()
    }

    @Test
    fun upcoming_events_help_click_shows_dialog() {
        val scenario = fixture.launchSettingsActivity()

        scenario.onActivity { activity: SettingsActivityX ->
            val fragment = showNavigationSettings(activity)
            fragment.findPreference<Preference>("upcoming_events_help")!!.performClick()

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertTrue(dialog.isShowing)
        }

        scenario.close()
    }

    @Test
    fun upcoming_events_help_full_docs_opens_docs_url() {
        val scenario = fixture.launchSettingsActivity()

        scenario.onActivity { activity: SettingsActivityX ->
            val fragment = showNavigationSettings(activity)
            fragment.findPreference<Preference>("upcoming_events_help")!!.performClick()
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()

            val started = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, started.action)
            assertEquals(activity.getString(R.string.upcoming_events_docs_url), started.dataString)
        }

        scenario.close()
    }
}
