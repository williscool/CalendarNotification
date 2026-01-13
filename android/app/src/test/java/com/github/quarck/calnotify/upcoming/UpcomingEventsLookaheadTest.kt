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

package com.github.quarck.calnotify.upcoming

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.utils.CNPlusUnitTestClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * Tests for UpcomingEventsLookahead - cutoff time calculations
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class UpcomingEventsLookaheadTest {

    private lateinit var context: Context
    private lateinit var settings: Settings
    private lateinit var clock: CNPlusUnitTestClock

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settings = Settings(context)
        clock = CNPlusUnitTestClock()
    }

    // === Fixed Hours Mode Tests ===

    @Test
    fun testFixedHoursMode_defaultHours() {
        // Set to fixed mode with default 8 hours
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_FIXED
        settings.upcomingEventsFixedHours = 8
        
        val now = System.currentTimeMillis()
        clock.setCurrentTime(now)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val cutoff = lookahead.getCutoffTime()
        
        val expectedCutoff = now + (8 * Consts.HOUR_IN_MILLISECONDS)
        assertEquals("Fixed hours cutoff should be now + 8 hours", expectedCutoff, cutoff)
    }

    @Test
    fun testFixedHoursMode_customHours() {
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_FIXED
        settings.upcomingEventsFixedHours = 24
        
        val now = 1000000L
        clock.setCurrentTime(now)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val cutoff = lookahead.getCutoffTime()
        
        val expectedCutoff = now + (24 * Consts.HOUR_IN_MILLISECONDS)
        assertEquals("Fixed hours cutoff should be now + 24 hours", expectedCutoff, cutoff)
    }

    // === Morning Cutoff Mode Tests ===

    @Test
    fun testCutoffMode_beforeCutoffHour_usesToday() {
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_CUTOFF
        settings.upcomingEventsCutoffHour = 10  // 10 AM cutoff
        
        // Set clock to 2 AM today
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        clock.setCurrentTime(calendar.timeInMillis)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val cutoff = lookahead.getCutoffTime()
        
        // Expected: 10 AM today
        val expectedCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        assertEquals("At 2 AM, cutoff should be 10 AM today", expectedCalendar.timeInMillis, cutoff)
    }

    @Test
    fun testCutoffMode_afterCutoffHour_usesTomorrow() {
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_CUTOFF
        settings.upcomingEventsCutoffHour = 10  // 10 AM cutoff
        
        // Set clock to 11 PM today
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        clock.setCurrentTime(calendar.timeInMillis)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val cutoff = lookahead.getCutoffTime()
        
        // Expected: 10 AM tomorrow
        val expectedCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        
        assertEquals("At 11 PM, cutoff should be 10 AM tomorrow", expectedCalendar.timeInMillis, cutoff)
    }

    @Test
    fun testCutoffMode_atCutoffHour_usesTomorrow() {
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_CUTOFF
        settings.upcomingEventsCutoffHour = 10  // 10 AM cutoff
        
        // Set clock to exactly 10 AM today
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        clock.setCurrentTime(calendar.timeInMillis)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val cutoff = lookahead.getCutoffTime()
        
        // Expected: 10 AM tomorrow (we're at/past the cutoff)
        val expectedCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        
        assertEquals("At exactly 10 AM, cutoff should be 10 AM tomorrow", expectedCalendar.timeInMillis, cutoff)
    }

    @Test
    fun testCutoffMode_customCutoffHour_6AM() {
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_CUTOFF
        settings.upcomingEventsCutoffHour = 6  // 6 AM cutoff (earliest allowed)
        
        // Set clock to 3 AM
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        clock.setCurrentTime(calendar.timeInMillis)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val cutoff = lookahead.getCutoffTime()
        
        // Expected: 6 AM today
        val expectedCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        assertEquals("At 3 AM with 6 AM cutoff, should use 6 AM today", expectedCalendar.timeInMillis, cutoff)
    }

    @Test
    fun testCutoffMode_customCutoffHour_12PM() {
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_CUTOFF
        settings.upcomingEventsCutoffHour = 12  // 12 PM cutoff (latest allowed)
        
        // Set clock to 8 AM
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        clock.setCurrentTime(calendar.timeInMillis)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val cutoff = lookahead.getCutoffTime()
        
        // Expected: 12 PM today
        val expectedCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        assertEquals("At 8 AM with 12 PM cutoff, should use 12 PM today", expectedCalendar.timeInMillis, cutoff)
    }

    // === Default Mode Tests ===

    @Test
    fun testUnknownMode_defaultsToCutoff() {
        settings.upcomingEventsMode = "invalid_mode"
        settings.upcomingEventsCutoffHour = 10
        
        // Set clock to 2 AM
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        clock.setCurrentTime(calendar.timeInMillis)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val cutoff = lookahead.getCutoffTime()
        
        // Expected: 10 AM today (defaults to cutoff mode)
        val expectedCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        assertEquals("Unknown mode should default to cutoff mode", expectedCalendar.timeInMillis, cutoff)
    }

    // === Settings Bounds Tests ===

    @Test
    fun testCutoffHourBounds_coercedToValidRange() {
        // Test that setting out-of-bounds values gets coerced
        settings.upcomingEventsCutoffHour = 100  // Way above max
        assertEquals("Cutoff hour should be coerced to max", 12, settings.upcomingEventsCutoffHour)
        
        settings.upcomingEventsCutoffHour = 1  // Below min
        assertEquals("Cutoff hour should be coerced to min", 6, settings.upcomingEventsCutoffHour)
    }

    @Test
    fun testFixedHoursBounds_coercedToValidRange() {
        settings.upcomingEventsFixedHours = 100  // Above max
        assertEquals("Fixed hours should be coerced to max", 48, settings.upcomingEventsFixedHours)
        
        settings.upcomingEventsFixedHours = 0  // Below min
        assertEquals("Fixed hours should be coerced to min", 1, settings.upcomingEventsFixedHours)
    }
}
