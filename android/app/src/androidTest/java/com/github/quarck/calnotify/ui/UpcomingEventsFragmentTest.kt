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
 * Ultron UI tests for UpcomingEventsFragment.
 * 
 * Tests the upcoming events list display (read-only in Milestone 1).
 * Pre-actions (snooze, mute, dismiss) will be tested in Milestone 2.
 * 
 * Note: These tests create alerts in MonitorStorage. The actual event
 * enrichment requires CalendarProvider which returns mock data.
 */
@RunWith(AndroidJUnit4::class)
class UpcomingEventsFragmentTest : BaseUltronTest() {
    
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
        fixture.clearUpcomingEvents()
        fixture.cleanup()
    }
    
    // === Fragment Launch Tests ===
    
    @Test
    fun upcomingEventsFragment_launches_successfully() {
        val scenario = fixture.launchUpcomingEventsFragment()
        
        scenario.onFragment { fragment ->
            assert(fragment != null)
        }
        
        scenario.close()
    }
    
    // === RecyclerView Tests ===
    
    @Test
    fun upcomingEventsFragment_shows_recycler_view() {
        val scenario = fixture.launchUpcomingEventsFragment()
        
        withId(R.id.recycler_view).isDisplayed()
        
        scenario.close()
    }
    
    // === Empty State Tests ===
    
    @Test
    fun upcomingEventsFragment_shows_empty_view_when_no_events() {
        val scenario = fixture.launchUpcomingEventsFragment()
        
        withId(R.id.empty_view).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun upcomingEventsFragment_hides_empty_view_when_events_exist() {
        fixture.seedUpcomingEvents(3)
        
        val scenario = fixture.launchUpcomingEventsFragment()
        
        withId(R.id.empty_view).isNotDisplayed()
        
        scenario.close()
    }
    
    // === Event List Display Tests ===
    
    @Test
    fun upcomingEventsFragment_displays_events_in_list() {
        fixture.createUpcomingEvent(title = "Meeting Tomorrow")
        fixture.createUpcomingEvent(title = "Dentist Appointment")
        
        val scenario = fixture.launchUpcomingEventsFragment()
        
        withId(R.id.recycler_view).isDisplayed()
        
        scenario.close()
    }
    
    // === Pull to Refresh Tests ===
    
    @Test
    fun upcomingEventsFragment_has_swipe_refresh_layout() {
        val scenario = fixture.launchUpcomingEventsFragment()
        
        withId(R.id.refresh_layout).isDisplayed()
        
        scenario.close()
    }
    
    // === All-Day Event Tests ===
    
    @Test
    fun upcomingEventsFragment_allday_event_shows_in_list() {
        fixture.createUpcomingEvent(title = "All Day Event", isAllDay = true)
        
        val scenario = fixture.launchUpcomingEventsFragment()
        
        // If events exist, empty view should be hidden
        withId(R.id.empty_view).isNotDisplayed()
        withId(R.id.recycler_view).isDisplayed()
        
        scenario.close()
    }
    
    // === Seed Events Tests ===
    
    @Test
    fun upcomingEventsFragment_seeded_events_hide_empty_state() {
        fixture.seedUpcomingEvents(5)
        
        val scenario = fixture.launchUpcomingEventsFragment()
        
        // Empty view should be hidden when there are events
        withId(R.id.empty_view).isNotDisplayed()
        withId(R.id.recycler_view).isDisplayed()
        
        scenario.close()
    }
    
    companion object {
        private const val LOG_TAG = "UpcomingEventsFragmentTest"
    }
}
