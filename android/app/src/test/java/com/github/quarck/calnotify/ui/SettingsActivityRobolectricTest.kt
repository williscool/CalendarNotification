package com.github.quarck.calnotify.ui

import android.view.View
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.testutils.UITestFixtureRobolectric
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric UI tests for SettingsActivity.
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
        
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
        
        scenario.close()
    }
    
    // === Preference Headers Display Tests ===
    
    @Test
    fun settingsActivity_shows_preference_list() {
        val scenario = fixture.launchSettingsActivity()
        
        scenario.onActivity { activity ->
            val listView = activity.findViewById<View>(android.R.id.list)
            assertNotNull(listView)
            assertEquals(View.VISIBLE, listView.visibility)
        }
        
        scenario.close()
    }
    
    @Test
    fun settingsActivity_has_content_view() {
        val scenario = fixture.launchSettingsActivity()
        
        scenario.onActivity { activity ->
            val contentView = activity.findViewById<View>(android.R.id.content)
            assertNotNull(contentView)
        }
        
        scenario.close()
    }
}

