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
import com.github.quarck.calnotify.testutils.TestTimeConstants
import com.github.quarck.calnotify.utils.CNPlusUnitTestClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * Tests for UpcomingEventsLookahead - lookahead end time calculations
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

    // === Fixed Hours Mode Tests (Default) ===

    @Test
    fun testFixedHoursMode_defaultHours() {
        // Set to fixed mode with default 8 hours
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_FIXED
        settings.upcomingEventsFixedHours = 8
        
        val now = TestTimeConstants.STANDARD_TEST_TIME
        clock.setCurrentTime(now)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val endTime = lookahead.getLookaheadEndTime()
        
        val expectedEndTime = now + (8 * Consts.HOUR_IN_MILLISECONDS)
        assertEquals("Fixed hours should be now + 8 hours", expectedEndTime, endTime)
    }

    @Test
    fun testFixedHoursMode_customHours() {
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_FIXED
        settings.upcomingEventsFixedHours = 24
        
        val now = 1000000L
        clock.setCurrentTime(now)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val endTime = lookahead.getLookaheadEndTime()
        
        val expectedEndTime = now + (24 * Consts.HOUR_IN_MILLISECONDS)
        assertEquals("Fixed hours should be now + 24 hours", expectedEndTime, endTime)
    }

    @Test
    fun testFixedMode_isDefault() {
        // Fresh settings should default to fixed mode
        // (Settings default is "fixed", unknown modes also fall through to fixed)
        settings.upcomingEventsMode = "some_unknown_mode"
        settings.upcomingEventsFixedHours = 8
        
        val now = TestTimeConstants.STANDARD_TEST_TIME
        clock.setCurrentTime(now)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val endTime = lookahead.getLookaheadEndTime()
        
        val expectedEndTime = now + (8 * Consts.HOUR_IN_MILLISECONDS)
        assertEquals("Unknown mode should default to fixed hours", expectedEndTime, endTime)
    }

    // === Day Boundary Mode Tests ===

    @Test
    fun testDayBoundaryMode_beforeBoundary_usesToday() {
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_DAY_BOUNDARY
        settings.upcomingEventsDayBoundaryHour = 4  // 4 AM boundary
        
        // Set clock to 1 AM today
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        clock.setCurrentTime(calendar.timeInMillis)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val endTime = lookahead.getLookaheadEndTime()
        
        // Expected: 4 AM today (3 hours ahead)
        val expectedCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        assertEquals("At 1 AM with 4 AM boundary, should use 4 AM today", expectedCalendar.timeInMillis, endTime)
    }

    @Test
    fun testDayBoundaryMode_atBoundary_usesTomorrow() {
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_DAY_BOUNDARY
        settings.upcomingEventsDayBoundaryHour = 4  // 4 AM boundary
        
        // Set clock to exactly 4 AM today
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        clock.setCurrentTime(calendar.timeInMillis)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val endTime = lookahead.getLookaheadEndTime()
        
        // Expected: 4 AM tomorrow (24 hours ahead)
        val expectedCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        
        assertEquals("At 4 AM with 4 AM boundary, should use 4 AM tomorrow", expectedCalendar.timeInMillis, endTime)
    }

    @Test
    fun testDayBoundaryMode_afterBoundary_usesTomorrow() {
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_DAY_BOUNDARY
        settings.upcomingEventsDayBoundaryHour = 4  // 4 AM boundary
        
        // Set clock to 10 AM today
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        clock.setCurrentTime(calendar.timeInMillis)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val endTime = lookahead.getLookaheadEndTime()
        
        // Expected: 4 AM tomorrow (18 hours ahead)
        val expectedCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        
        assertEquals("At 10 AM with 4 AM boundary, should use 4 AM tomorrow", expectedCalendar.timeInMillis, endTime)
    }

    @Test
    fun testDayBoundaryMode_lateNight_usesTomorrow() {
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_DAY_BOUNDARY
        settings.upcomingEventsDayBoundaryHour = 4  // 4 AM boundary
        
        // Set clock to 10 PM today
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        clock.setCurrentTime(calendar.timeInMillis)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val endTime = lookahead.getLookaheadEndTime()
        
        // Expected: 4 AM tomorrow (6 hours ahead)
        val expectedCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        
        assertEquals("At 10 PM with 4 AM boundary, should use 4 AM tomorrow", expectedCalendar.timeInMillis, endTime)
    }

    @Test
    fun testDayBoundaryMode_midnightBoundary_noSlack() {
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_DAY_BOUNDARY
        settings.upcomingEventsDayBoundaryHour = 0  // Midnight boundary (no slack)
        
        // Set clock to 11 PM today
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        clock.setCurrentTime(calendar.timeInMillis)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val endTime = lookahead.getLookaheadEndTime()
        
        // Expected: Midnight tomorrow (1 hour ahead)
        val expectedCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        
        assertEquals("At 11 PM with midnight boundary, should use midnight tomorrow", expectedCalendar.timeInMillis, endTime)
    }

    @Test
    fun testDayBoundaryMode_10amBoundary_maxSlack() {
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_DAY_BOUNDARY
        settings.upcomingEventsDayBoundaryHour = 10  // 10 AM boundary (max slack)
        
        // Set clock to 2 AM today
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        clock.setCurrentTime(calendar.timeInMillis)
        
        val lookahead = UpcomingEventsLookahead(settings, clock)
        val endTime = lookahead.getLookaheadEndTime()
        
        // Expected: 10 AM today (8 hours ahead)
        val expectedCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        assertEquals("At 2 AM with 10 AM boundary, should use 10 AM today", expectedCalendar.timeInMillis, endTime)
    }

    // === Settings Bounds Tests ===

    @Test
    fun testDayBoundaryHourBounds_coercedToValidRange() {
        // Test that setting out-of-bounds values gets coerced
        settings.upcomingEventsDayBoundaryHour = 100  // Way above max
        assertEquals("Day boundary hour should be coerced to max", 10, settings.upcomingEventsDayBoundaryHour)
        
        settings.upcomingEventsDayBoundaryHour = -5  // Below min
        assertEquals("Day boundary hour should be coerced to min", 0, settings.upcomingEventsDayBoundaryHour)
    }

    @Test
    fun testFixedHoursBounds_coercedToValidRange() {
        settings.upcomingEventsFixedHours = 100  // Above max
        assertEquals("Fixed hours should be coerced to max", 48, settings.upcomingEventsFixedHours)
        
        settings.upcomingEventsFixedHours = 0  // Below min
        assertEquals("Fixed hours should be coerced to min", 1, settings.upcomingEventsFixedHours)
    }
}
