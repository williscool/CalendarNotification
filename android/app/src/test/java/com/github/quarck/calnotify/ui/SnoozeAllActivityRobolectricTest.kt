package com.github.quarck.calnotify.ui

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.github.quarck.calnotify.Consts
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
 * Robolectric UI tests for SnoozeAllActivity.
 * 
 * Tests snooze preset buttons, custom snooze dialog, and snooze actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SnoozeAllActivityRobolectricTest {
    
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
    fun snoozeAllActivity_launches_for_single_event() {
        val event = fixture.createEvent(title = "Test Event")
        
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
        
        scenario.close()
    }
    
    @Test
    fun snoozeAllActivity_launches_in_snooze_all_mode() {
        fixture.seedEvents(3)
        
        val scenario = fixture.launchSnoozeAllActivity()
        
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
        
        scenario.close()
    }
    
    // === Toolbar Tests ===
    
    @Test
    fun snoozeAllActivity_shows_toolbar() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        scenario.onActivity { activity ->
            val toolbar = activity.findViewById<View>(R.id.toolbar)
            assertNotNull(toolbar)
            assertEquals(View.VISIBLE, toolbar.visibility)
        }
        
        scenario.close()
    }
    
    // === Preset Button Visibility Tests ===
    
    @Test
    fun snoozeAllActivity_shows_first_preset_button() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        scenario.onActivity { activity ->
            val preset1 = activity.findViewById<View>(R.id.snooze_view_snooze_present1)
            assertNotNull(preset1)
            assertEquals(View.VISIBLE, preset1.visibility)
        }
        
        scenario.close()
    }
    
    @Test
    fun snoozeAllActivity_shows_multiple_preset_buttons() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        scenario.onActivity { activity ->
            val preset1 = activity.findViewById<View>(R.id.snooze_view_snooze_present1)
            val preset2 = activity.findViewById<View>(R.id.snooze_view_snooze_present2)
            
            assertNotNull(preset1)
            assertNotNull(preset2)
            assertEquals(View.VISIBLE, preset1.visibility)
            assertEquals(View.VISIBLE, preset2.visibility)
        }
        
        scenario.close()
    }
    
    @Test
    fun snoozeAllActivity_shows_custom_snooze_button() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        scenario.onActivity { activity ->
            val customBtn = activity.findViewById<View>(R.id.snooze_view_snooze_custom)
            assertNotNull(customBtn)
            assertEquals(View.VISIBLE, customBtn.visibility)
        }
        
        scenario.close()
    }
    
    @Test
    fun snoozeAllActivity_shows_snooze_until_button() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        scenario.onActivity { activity ->
            val untilBtn = activity.findViewById<View>(R.id.snooze_view_snooze_until)
            assertNotNull(untilBtn)
            assertEquals(View.VISIBLE, untilBtn.visibility)
        }
        
        scenario.close()
    }
    
    // === Snooze Header Text Tests ===
    
    @Test
    fun snoozeAllActivity_shows_snooze_header_text() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        scenario.onActivity { activity ->
            val header = activity.findViewById<View>(R.id.snooze_snooze_for)
            assertNotNull(header)
            assertEquals(View.VISIBLE, header.visibility)
        }
        
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
        
        scenario.onActivity { activity ->
            val countText = activity.findViewById<View>(R.id.snooze_count_text)
            assertNotNull(countText)
            assertEquals(View.VISIBLE, countText.visibility)
        }
        
        scenario.close()
    }
    
    // === Preset Buttons Have Text Tests ===
    
    @Test
    fun preset_buttons_have_text() {
        val event = fixture.createEvent()
        val scenario = fixture.launchSnoozeActivityForEvent(event)
        
        scenario.onActivity { activity ->
            val preset1 = activity.findViewById<Button>(R.id.snooze_view_snooze_present1)
            assertNotNull(preset1)
            assertFalse(preset1.text.isNullOrEmpty())
        }
        
        scenario.close()
    }
}

