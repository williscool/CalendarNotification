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

import android.os.Looper
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.permissions.PermissionsManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Robolectric tests for CalendarsActivity
 * 
 * Tests basic UI setup for calendar sync refresh feature.
 * 
 * Note: Menu testing is skipped due to Robolectric limitations with options menus
 * in activities using Toolbar. Menu functionality is verified via manual testing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [28])
class CalendarsActivityRobolectricTest {

    @Before
    fun setup() {
        ShadowLog.stream = System.out
        
        // Grant calendar permissions
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = shadowOf(app)
        shadowApp.grantPermissions(
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR
        )
        
        // Mock PermissionsManager
        mockkObject(PermissionsManager)
        every { PermissionsManager.hasAllCalendarPermissions(any()) } returns true
        every { PermissionsManager.hasReadCalendar(any()) } returns true
        
        // Mock CalendarProvider to return test calendars
        mockkObject(CalendarProvider)
        every { CalendarProvider.getCalendars(any()) } returns listOf(
            createTestCalendar(1L, "Personal", "user@gmail.com"),
            createTestCalendar(2L, "Work", "user@work.com", isHandled = false, upcomingEvents = 5)
        )
        every { CalendarProvider.getUpcomingEventCountsByCalendar(any(), any()) } returns mapOf(
            1L to 0,
            2L to 5
        )
    }
    
    private fun createTestCalendar(
        id: Long, 
        name: String, 
        account: String,
        isHandled: Boolean = true,
        upcomingEvents: Int = 0
    ) = CalendarRecord(
        calendarId = id,
        owner = account,
        accountName = account,
        accountType = "com.google",
        name = name,
        displayName = name,
        color = 0xFF6200EE.toInt(),
        isVisible = true,
        timeZone = "UTC",
        isReadOnly = false,
        isPrimary = id == 1L,
        isSynced = true
    )

    @After
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun testSwipeRefreshLayoutExists() {
        val scenario = ActivityScenario.launch(CalendarsActivity::class.java)
        
        shadowOf(Looper.getMainLooper()).idle()
        
        scenario.onActivity { activity ->
            val swipeRefresh = activity.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_calendars)
            assertNotNull("SwipeRefreshLayout should exist", swipeRefresh)
        }
        
        scenario.close()
    }

    @Test
    fun testRecyclerViewExists() {
        val scenario = ActivityScenario.launch(CalendarsActivity::class.java)
        
        shadowOf(Looper.getMainLooper()).idle()
        
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_calendars)
            assertNotNull("RecyclerView should exist", recyclerView)
        }
        
        scenario.close()
    }

    @Test
    fun testNoCalendarsTextExists() {
        val scenario = ActivityScenario.launch(CalendarsActivity::class.java)
        
        shadowOf(Looper.getMainLooper()).idle()
        
        scenario.onActivity { activity ->
            val noCalendarsText = activity.findViewById<TextView>(R.id.no_calendars_text)
            assertNotNull("No calendars text view should exist", noCalendarsText)
        }
        
        scenario.close()
    }

    @Test
    fun testActivityLaunchesSuccessfully() {
        val scenario = ActivityScenario.launch(CalendarsActivity::class.java)
        
        shadowOf(Looper.getMainLooper()).idle()
        
        scenario.onActivity { activity ->
            assertNotNull("Activity should launch successfully", activity)
        }
        
        scenario.close()
    }
}
