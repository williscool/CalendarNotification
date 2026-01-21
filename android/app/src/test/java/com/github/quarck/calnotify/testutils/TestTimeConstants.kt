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

package com.github.quarck.calnotify.testutils

import java.util.Calendar
import java.util.TimeZone

/**
 * Standard test time constants for deterministic testing.
 * 
 * Using System.currentTimeMillis() in tests causes flakiness because:
 * 1. Tests running near midnight can cross day boundaries mid-test
 * 2. Time-dependent comparisons become non-deterministic
 * 
 * Instead, use these constants for predictable test behavior.
 */
object TestTimeConstants {
    /**
     * Standard test time: December 23, 2023 at 12:00:00 UTC
     * 
     * This time was chosen because:
     * - Noon avoids midnight boundary issues
     * - December 23rd is a meaningful date (day before Christmas Eve)
     * - 2023 is a past date, making it clear this is a fixed test value
     */
    val STANDARD_TEST_TIME: Long = run {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2023, Calendar.DECEMBER, 23, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    
    /** One hour in milliseconds */
    const val ONE_HOUR = 3600_000L
    
    /** One day in milliseconds */
    const val ONE_DAY = 24 * ONE_HOUR
    
    /** One week in milliseconds */
    const val ONE_WEEK = 7 * ONE_DAY
}
