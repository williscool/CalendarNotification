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
import com.atiurin.ultron.extensions.click
import com.atiurin.ultron.extensions.isDisplayed
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.testutils.UITestFixture
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ultron UI tests for DismissedEventsFragment.
 * 
 * Tests the dismissed events list display and restore functionality.
 * Mirrors DismissedEventsActivityTest but for the fragment-based UI.
 */
@RunWith(AndroidJUnit4::class)
class DismissedEventsFragmentTest : BaseUltronTest() {
    
    private lateinit var fixture: UITestFixture
    
    @Before
    fun setup() {
        fixture = UITestFixture.create()
        // Full optimization for fast UI tests
        fixture.setup(
            waitForAsyncTasks = true,
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
    fun dismissedEventsFragment_launches_successfully() {
        val scenario = fixture.launchDismissedEventsFragment()
        
        scenario.onFragment { fragment ->
            assert(fragment != null)
        }
        
        scenario.close()
    }
    
    // === RecyclerView Tests ===
    
    @Test
    fun dismissedEventsFragment_shows_recycler_view() {
        val scenario = fixture.launchDismissedEventsFragment()
        
        withId(R.id.recycler_view).isDisplayed()
        
        scenario.close()
    }
    
    // === Dismissed Events Display Tests ===
    
    @Test
    fun dismissedEventsFragment_displays_dismissed_events() {
        fixture.createDismissedEvent(title = "Dismissed Meeting")
        fixture.createDismissedEvent(title = "Dismissed Appointment")
        
        val scenario = fixture.launchDismissedEventsFragment()
        
        withId(R.id.recycler_view).isDisplayed()
        
        scenario.close()
    }
    
    @Test
    fun dismissedEventsFragment_displays_event_title() {
        fixture.createDismissedEvent(title = "Important Dismissed Event")
        
        val scenario = fixture.launchDismissedEventsFragment()
        
        withId(R.id.recycler_view).isDisplayed()
        withText("Important Dismissed Event").isDisplayed()
        
        scenario.close()
    }
    
    // === Click to Show Menu Tests ===
    
    @Test
    fun clicking_dismissed_event_shows_popup_menu() {
        fixture.createDismissedEvent(title = "Clickable Dismissed Event")
        
        val scenario = fixture.launchDismissedEventsFragment()
        
        withId(R.id.recycler_view).isDisplayed()
        
        // Click on the event
        withText("Clickable Dismissed Event").click()
        
        // Popup menu with Restore option should appear
        withText(R.string.restore).isDisplayed()
        
        scenario.close()
    }
    
    // === Restore Action Tests ===
    
    @Test
    fun clicking_restore_triggers_restore_event() {
        fixture.mockApplicationController()
        every { ApplicationController.restoreEvent(any(), any()) } returns Unit
        
        fixture.createDismissedEvent(title = "Event to Restore")
        
        val scenario = fixture.launchDismissedEventsFragment()
        
        withId(R.id.recycler_view).isDisplayed()
        
        // Click on the event
        withText("Event to Restore").click()
        
        // Click Restore in popup menu
        withText(R.string.restore).click()
        
        // Verify restore was called
        verify(timeout = 2000) { 
            ApplicationController.restoreEvent(any(), any()) 
        }
        
        scenario.close()
    }
    
    // === Multiple Dismissed Events Tests ===
    // Note: Menu tests (Remove All) are in MainActivityDismissedEventsTabTest
    
    @Test
    fun dismissedEventsFragment_displays_multiple_events() {
        fixture.createDismissedEvent(title = "First Dismissed")
        fixture.createDismissedEvent(title = "Second Dismissed")
        fixture.createDismissedEvent(title = "Third Dismissed")
        
        val scenario = fixture.launchDismissedEventsFragment()
        
        withId(R.id.recycler_view).isDisplayed()
        
        // All events should be visible
        withText("First Dismissed").isDisplayed()
        withText("Second Dismissed").isDisplayed()
        withText("Third Dismissed").isDisplayed()
        
        scenario.close()
    }
    
    // === Pull to Refresh Tests ===
    
    @Test
    fun dismissedEventsFragment_has_swipe_refresh_layout() {
        val scenario = fixture.launchDismissedEventsFragment()
        
        withId(R.id.refresh_layout).isDisplayed()
        
        scenario.close()
    }
    
    companion object {
        private const val LOG_TAG = "DismissedEventsFragmentTest"
    }
}
