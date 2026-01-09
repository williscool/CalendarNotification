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
package com.github.quarck.calnotify.prefs

import android.view.Menu
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.github.quarck.calnotify.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLog

/**
 * Robolectric tests for CalendarsActivity
 * 
 * Tests UI setup for calendar sync refresh feature:
 * - SwipeRefreshLayout presence and configuration
 * - Menu inflation with refresh/help items
 * - Help dialog content
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [28])
class CalendarsActivityRobolectricTest {

    @Before
    fun setup() {
        ShadowLog.stream = System.out
    }

    @Test
    fun testSwipeRefreshLayoutExists() {
        val activity = Robolectric.buildActivity(CalendarsActivity::class.java)
            .create()
            .start()
            .resume()
            .get()

        val swipeRefresh = activity.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_calendars)
        assertNotNull("SwipeRefreshLayout should exist", swipeRefresh)
    }

    @Test
    fun testMenuInflation() {
        val activityController = Robolectric.buildActivity(CalendarsActivity::class.java)
            .create()
            .start()
            .resume()

        val activity = activityController.get()
        
        // Trigger menu creation
        val shadowActivity = Shadows.shadowOf(activity)
        shadowActivity.onCreateOptionsMenu(Menu.NONE)
        
        val menu = shadowActivity.optionsMenu
        assertNotNull("Menu should be inflated", menu)
        assertNotNull("Refresh item should exist", menu?.findItem(R.id.action_refresh_calendars))
        assertNotNull("Help item should exist", menu?.findItem(R.id.action_calendar_sync_help))
    }

    @Test
    fun testRefreshMenuItemTriggersRefresh() {
        val activity = Robolectric.buildActivity(CalendarsActivity::class.java)
            .create()
            .start()
            .resume()
            .get()

        val swipeRefresh = activity.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_calendars)
        
        // Initially not refreshing
        assertFalse("Should not be refreshing initially", swipeRefresh.isRefreshing)
        
        // Trigger refresh via menu item
        activity.onOptionsItemSelected(
            activity.findViewById<android.view.View>(R.id.action_refresh_calendars)?.let {
                // Menu item click
                val shadowActivity = Shadows.shadowOf(activity)
                val menu = shadowActivity.optionsMenu
                menu?.findItem(R.id.action_refresh_calendars)
            } ?: return
        )
        
        // Should now be refreshing
        assertTrue("Should be refreshing after menu item click", swipeRefresh.isRefreshing)
    }

    @Test
    fun testHelpMenuItemShowsDialog() {
        val activity = Robolectric.buildActivity(CalendarsActivity::class.java)
            .create()
            .start()
            .resume()
            .get()

        // Trigger help menu item
        val shadowActivity = Shadows.shadowOf(activity)
        shadowActivity.onCreateOptionsMenu(Menu.NONE)
        val menu = shadowActivity.optionsMenu
        val helpItem = menu?.findItem(R.id.action_calendar_sync_help)
        
        assertNotNull("Help menu item should exist", helpItem)
        
        // Click help item
        activity.onOptionsItemSelected(helpItem!!)
        
        // Verify dialog is shown
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("Help dialog should be shown", dialog)
        assertTrue("Dialog should be showing", dialog.isShowing)
    }
}
