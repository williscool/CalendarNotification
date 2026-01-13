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

import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import java.util.Calendar

/**
 * Calculates the cutoff time for the upcoming events lookahead window.
 * 
 * Supports two modes:
 * - "cutoff": Shows events until the next morning cutoff time (e.g., 10 AM tomorrow)
 * - "fixed": Shows events for a fixed number of hours ahead
 * 
 * The cutoff mode is useful for seeing late-night events without showing all of tomorrow's events.
 */
class UpcomingEventsLookahead(
    private val settings: Settings,
    private val clock: CNPlusClockInterface
) {
    
    /**
     * Get the cutoff time for the upcoming events window.
     * Events with alertTime between now and this cutoff will be shown.
     * 
     * @return The cutoff time in milliseconds since epoch
     */
    fun getCutoffTime(): Long {
        val now = clock.currentTimeMillis()
        
        return when (settings.upcomingEventsMode) {
            MODE_FIXED -> calculateFixedCutoff(now)
            MODE_CUTOFF -> calculateMorningCutoff(now)
            else -> calculateMorningCutoff(now)  // Default to cutoff mode
        }
    }
    
    /**
     * Fixed hours lookahead: now + configured hours
     */
    private fun calculateFixedCutoff(now: Long): Long {
        val fixedHours = settings.upcomingEventsFixedHours
        return now + (fixedHours * Consts.HOUR_IN_MILLISECONDS)
    }
    
    /**
     * Next morning cutoff: Shows events until the cutoff hour the next morning.
     * 
     * If current time is before the cutoff hour, use today's cutoff.
     * If current time is at or after the cutoff hour, use tomorrow's cutoff.
     * 
     * Example with cutoff at 10 AM:
     * - At 2 AM: show events until 10 AM today
     * - At 11 PM: show events until 10 AM tomorrow
     */
    private fun calculateMorningCutoff(now: Long): Long {
        val cutoffHour = settings.upcomingEventsCutoffHour
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        // Set to today at cutoff hour
        calendar.set(Calendar.HOUR_OF_DAY, cutoffHour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // If we're past the cutoff already, move to tomorrow
        if (currentHour >= cutoffHour) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        return calendar.timeInMillis
    }
    
    companion object {
        const val MODE_CUTOFF = "cutoff"
        const val MODE_FIXED = "fixed"
    }
}
