package com.github.quarck.calnotify.ui

import android.view.View
import android.widget.TextView
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
 * Robolectric UI tests for ViewEventActivityNoRecents.
 * 
 * Tests event detail display, snooze actions, and dismiss functionality.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class ViewEventActivityRobolectricTest {
    
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
    fun viewEventActivity_launches_for_event() {
        val event = fixture.createEvent(title = "Test Event")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        scenario.onActivity { activity ->
            assertNotNull(activity)
        }
        
        scenario.close()
    }
    
    // === Event Details Display Tests ===
    
    @Test
    fun viewEventActivity_displays_event_title() {
        val event = fixture.createEvent(title = "Important Meeting")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        scenario.onActivity { activity ->
            val titleView = activity.findViewById<TextView>(R.id.snooze_view_title)
            assertNotNull(titleView)
            assertEquals(View.VISIBLE, titleView.visibility)
            assertEquals("Important Meeting", titleView.text)
        }
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_displays_event_date() {
        val event = fixture.createEvent(title = "Date Test Event")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        scenario.onActivity { activity ->
            val dateLine = activity.findViewById<View>(R.id.snooze_view_event_date_line1)
            assertNotNull(dateLine)
            assertEquals(View.VISIBLE, dateLine.visibility)
        }
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_displays_location_when_present() {
        val event = fixture.createEvent(
            title = "Event with Location",
            location = "Conference Room A"
        )
        
        val scenario = fixture.launchViewEventActivity(event)
        
        scenario.onActivity { activity ->
            val locationView = activity.findViewById<TextView>(R.id.snooze_view_location)
            assertNotNull(locationView)
            assertEquals(View.VISIBLE, locationView.visibility)
            assertEquals("Conference Room A", locationView.text)
        }
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_hides_location_when_empty() {
        val event = fixture.createEvent(
            title = "Event without Location",
            location = ""
        )
        
        val scenario = fixture.launchViewEventActivity(event)
        
        scenario.onActivity { activity ->
            val locationLayout = activity.findViewById<View>(R.id.snooze_view_location_layout)
            // Location layout should be hidden when empty
            assertTrue(locationLayout.visibility == View.GONE || locationLayout.visibility == View.INVISIBLE)
        }
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_displays_calendar_name() {
        val event = fixture.createEvent(title = "Calendar Test")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        scenario.onActivity { activity ->
            val calendarName = activity.findViewById<View>(R.id.view_event_calendar_name)
            assertNotNull(calendarName)
            assertEquals(View.VISIBLE, calendarName.visibility)
        }
        
        scenario.close()
    }
    
    // === Snooze Preset Button Tests ===
    
    @Test
    fun viewEventActivity_shows_snooze_preset_buttons() {
        val event = fixture.createEvent()
        
        val scenario = fixture.launchViewEventActivity(event)
        
        scenario.onActivity { activity ->
            val preset1 = activity.findViewById<View>(R.id.snooze_view_snooze_present1)
            assertNotNull(preset1)
            assertEquals(View.VISIBLE, preset1.visibility)
        }
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_shows_custom_snooze_button() {
        val event = fixture.createEvent()
        
        val scenario = fixture.launchViewEventActivity(event)
        
        scenario.onActivity { activity ->
            val customBtn = activity.findViewById<View>(R.id.snooze_view_snooze_custom)
            assertNotNull(customBtn)
            assertEquals(View.VISIBLE, customBtn.visibility)
        }
        
        scenario.close()
    }
    
    @Test
    fun viewEventActivity_shows_snooze_until_button() {
        val event = fixture.createEvent()
        
        val scenario = fixture.launchViewEventActivity(event)
        
        scenario.onActivity { activity ->
            val untilBtn = activity.findViewById<View>(R.id.snooze_view_snooze_until)
            assertNotNull(untilBtn)
            assertEquals(View.VISIBLE, untilBtn.visibility)
        }
        
        scenario.close()
    }
    
    // === Snoozed Event Display Tests ===
    
    @Test
    fun snoozed_event_shows_change_snooze_header() {
        val event = fixture.createSnoozedEvent(title = "Already Snoozed")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        scenario.onActivity { activity ->
            val headerView = activity.findViewById<TextView>(R.id.snooze_snooze_for)
            assertNotNull(headerView)
            assertEquals(View.VISIBLE, headerView.visibility)
            // Should show "change snooze to" text for snoozed events
            val expectedText = activity.getString(R.string.change_snooze_to)
            assertEquals(expectedText, headerView.text)
        }
        
        scenario.close()
    }
    
    // === Event Details Layout Tests ===
    
    @Test
    fun viewEventActivity_displays_event_details_layout() {
        val event = fixture.createEvent(
            title = "Colored Event",
            color = 0xFF6200EE.toInt()
        )
        
        val scenario = fixture.launchViewEventActivity(event)
        
        scenario.onActivity { activity ->
            val detailsLayout = activity.findViewById<View>(R.id.snooze_view_event_details_layout)
            assertNotNull(detailsLayout)
            assertEquals(View.VISIBLE, detailsLayout.visibility)
        }
        
        scenario.close()
    }
    
    // === Empty Title Handling ===
    
    @Test
    fun viewEventActivity_shows_placeholder_for_empty_title() {
        val event = fixture.createEvent(title = "")
        
        val scenario = fixture.launchViewEventActivity(event)
        
        scenario.onActivity { activity ->
            val titleView = activity.findViewById<TextView>(R.id.snooze_view_title)
            assertNotNull(titleView)
            assertEquals(View.VISIBLE, titleView.visibility)
            // Should show placeholder for empty title
            val expectedText = activity.getString(R.string.empty_title)
            assertEquals(expectedText, titleView.text)
        }
        
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
        
        scenario.onActivity { activity ->
            val descLayout = activity.findViewById<View>(R.id.layout_event_description)
            assertNotNull(descLayout)
            assertEquals(View.VISIBLE, descLayout.visibility)
            
            val descView = activity.findViewById<TextView>(R.id.snooze_view_event_description)
            assertEquals("This is the event description", descView.text)
        }
        
        scenario.close()
    }
    
    // === Toolbar Tests ===
    
    @Test
    fun viewEventActivity_has_toolbar() {
        val event = fixture.createEvent()
        
        val scenario = fixture.launchViewEventActivity(event)
        
        scenario.onActivity { activity ->
            val toolbar = activity.findViewById<View>(R.id.toolbar)
            assertNotNull(toolbar)
            assertEquals(View.VISIBLE, toolbar.visibility)
        }
        
        scenario.close()
    }
}

