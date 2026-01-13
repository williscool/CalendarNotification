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

import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atiurin.ultron.extensions.click
import com.atiurin.ultron.extensions.isDisplayed
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.testutils.UITestFixture
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for DismissedEventsFragment when accessed via MainActivity's new navigation UI.
 * 
 * These tests require the full activity with toolbar to test menu functionality
 * that isn't available when the fragment is launched standalone.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityDismissedEventsTabTest : BaseUltronTest() {
    
    private lateinit var fixture: UITestFixture
    
    @Before
    fun setup() {
        fixture = UITestFixture.create()
        fixture.setup(
            waitForAsyncTasks = true,
            preventCalendarReload = true,
            grantPermissions = true,
            suppressBatteryDialog = true
        )
        
        // Enable new navigation UI for these tests
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Settings(context).useNewNavigationUI = true
    }
    
    @After
    fun cleanup() {
        // Reset to legacy UI (default for other tests)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Settings(context).useNewNavigationUI = false
        
        fixture.cleanup()
    }
    
    @Test
    fun clicking_remove_all_shows_confirmation_dialog() {
        fixture.createDismissedEvent(title = "Test Dismissed Event")
        
        val scenario = fixture.launchMainActivity()
        
        // Navigate to Dismissed tab via bottom navigation
        withId(R.id.dismissedEventsFragment).click()
        
        // Wait for the dismissed events to load
        withId(R.id.recycler_view).isDisplayed()
        withText("Test Dismissed Event").isDisplayed()
        
        // Open overflow menu (now available via MainActivity's toolbar)
        openActionBarOverflowOrOptionsMenu(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        
        // Click Remove All
        withText(R.string.remove_all).click()
        
        // Confirmation dialog should appear
        withText(R.string.remove_all_confirmation).isDisplayed()
        
        scenario.close()
    }
    
    companion object {
        private const val LOG_TAG = "MainActivityDismissedEventsTabTest"
    }
}
