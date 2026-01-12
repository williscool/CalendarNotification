//
//   Calendar Notifications Plus
//   Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//

package com.github.quarck.calnotify.ui

import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atiurin.ultron.extensions.isDisplayed
import com.atiurin.ultron.extensions.isNotDisplayed
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.testutils.UITestFixture
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ultron UI tests for ActiveEventsFragment.
 * 
 * Tests the active events list display and interactions.
 * Mirrors MainActivityTest but for the fragment-based UI.
 */
@RunWith(AndroidJUnit4::class)
class ActiveEventsFragmentTest : BaseUltronTest() {
    
    private lateinit var fixture: UITestFixture
    
    @Before
    fun setup() {
        fixture = UITestFixture.create()
        // Full optimization for fast UI tests
        fixture.setup(
            waitForAsyncTasks = true,
            preventCalendarReload = true,
            grantPermissions = true,
            suppressBatteryDialog = true
        )
    }
    
    @After
    fun cleanup() {
        fixture.cleanup()
    }
    
    // === Fragment Launch Tests ===
    
    @Test
    fun activeEventsFragment_launches_successfully() {
        val scenario = fixture.launchActiveEventsFragment()
        
        scenario.onFragment { fragment ->
            assert(fragment != null)
        }
        
        scenario.close()
    }
    
    // === RecyclerView Tests ===
    
    @Test
    fun activeEventsFragment_shows_recycler_view() {
        val scenario = fixture.launchActiveEventsFragment()
        
        withId(R.id.recycler_view).isDisplayed()
        
        scenario.close()
    }
    
    // === Empty State Tests ===
    
    @Test
    fun activeEventsFragment_shows_empty_view_when_no_events() {
        val scenario = fixture.launchActiveEventsFragment()
        
        withId(R.id.empty_view).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun activeEventsFragment_hides_empty_view_when_events_exist() {
        fixture.seedEvents(3)
        
        val scenario = fixture.launchActiveEventsFragment()
        
        withId(R.id.empty_view).isNotDisplayed()
        
        scenario.close()
    }
    
    // === Event List Display Tests ===
    
    @Test
    fun activeEventsFragment_displays_events_in_list() {
        fixture.createEvent(title = "Meeting with Team")
        fixture.createEvent(title = "Doctor Appointment")
        
        val scenario = fixture.launchActiveEventsFragment()
        
        withId(R.id.recycler_view).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun activeEventsFragment_displays_event_title() {
        val title = "Important Meeting"
        fixture.createEvent(title = title)
        
        val scenario = fixture.launchActiveEventsFragment()
        
        withText(title).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun activeEventsFragment_displays_multiple_events() {
        fixture.createEvent(title = "Event One")
        fixture.createEvent(title = "Event Two")
        fixture.createEvent(title = "Event Three")
        
        val scenario = fixture.launchActiveEventsFragment()
        
        withText("Event One").isDisplayed()
        withText("Event Two").isDisplayed()
        withText("Event Three").isDisplayed()
        
        scenario.close()
    }
    
    // === Pull to Refresh Tests ===
    
    @Test
    fun activeEventsFragment_has_swipe_refresh_layout() {
        val scenario = fixture.launchActiveEventsFragment()
        
        withId(R.id.refresh_layout).isDisplayed()
        
        scenario.close()
    }
    
    // === Snoozed Event Display Tests ===
    
    @Test
    fun activeEventsFragment_snoozed_event_is_displayed() {
        fixture.createSnoozedEvent(title = "Snoozed Event")
        
        val scenario = fixture.launchActiveEventsFragment()
        
        withText("Snoozed Event").isDisplayed()
        
        scenario.close()
    }
    
    // === Muted Event Display Tests ===
    
    @Test
    fun activeEventsFragment_muted_event_is_displayed() {
        fixture.createMutedEvent(title = "Muted Event")
        
        val scenario = fixture.launchActiveEventsFragment()
        
        withText("Muted Event").isDisplayed()
        
        scenario.close()
    }
    
    // === Task Event Display Tests ===
    
    @Test
    fun activeEventsFragment_task_event_is_displayed() {
        fixture.createTaskEvent(title = "Task Event")
        
        val scenario = fixture.launchActiveEventsFragment()
        
        withText("Task Event").isDisplayed()
        
        scenario.close()
    }
    
    companion object {
        private const val LOG_TAG = "ActiveEventsFragmentTest"
    }
}
