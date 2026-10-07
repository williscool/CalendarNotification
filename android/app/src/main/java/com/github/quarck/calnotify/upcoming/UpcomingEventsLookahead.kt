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

import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import java.util.Calendar

/**
 * Calculates the end time for the upcoming events lookahead window.
 * 
 * Supports two modes:
 * - "fixed" (default): Shows events for a fixed number of hours ahead
 * - "day_boundary": Shows events until the next day boundary (e.g., 4 AM)
 * 
 * The day boundary mode is for night owls who think in terms of "today" vs "tomorrow"
 * rather than fixed hours. Before the boundary hour, you're still mentally in "yesterday";
 * after it, you're planning "today".
 */
class UpcomingEventsLookahead(
    private val settings: Settings,
    private val clock: CNPlusClockInterface
) {
    
    /**
     * Get the end time for the upcoming events lookahead window.
     * Events with alertTime between now and this end time will be shown.
     * 
     * @return The lookahead end time in milliseconds since epoch
     */
    fun getLookaheadEndTime(): Long {
        val now = clock.currentTimeMillis()
        
        return when (settings.upcomingEventsMode) {
            MODE_DAY_BOUNDARY -> calculateDayBoundaryEndTime(now)
            else -> calculateFixedEndTime(now)  // Default to fixed mode
        }
    }
    
    /**
     * Fixed lookahead: now + configured interval (milliseconds).
     * Uses upcomingEventsFixedLookaheadMillis which falls back to legacy fixedHours if needed.
     */
    private fun calculateFixedEndTime(now: Long): Long {
        return now + settings.upcomingEventsFixedLookaheadMillis
    }
    
    /**
     * Day boundary mode: before the boundary hour, show until today's boundary;
     * after the boundary hour, show until tomorrow's boundary.
     * 
     * Example with boundary at 4 AM:
     * - At 1 AM: show until 4 AM today (3 hours) - "still yesterday"
     * - At 5 AM: show until 4 AM tomorrow (23 hours) - "today has begun"
     * - At 10 PM: show until 4 AM tomorrow (6 hours) - "winding down"
     */
    private fun calculateDayBoundaryEndTime(now: Long): Long {
        val boundaryHour = settings.upcomingEventsDayBoundaryHour
        
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        // Set to today at boundary hour
        calendar.set(Calendar.HOUR_OF_DAY, boundaryHour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // If we're at or past the boundary, use tomorrow's boundary
        if (currentHour >= boundaryHour) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        return calendar.timeInMillis
    }
    
    companion object {
        const val MODE_FIXED = "fixed"
        const val MODE_DAY_BOUNDARY = "day_boundary"
    }
}
