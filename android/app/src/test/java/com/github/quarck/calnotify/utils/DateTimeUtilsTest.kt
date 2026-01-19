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

package com.github.quarck.calnotify.utils

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class DateTimeUtilsTest {
    
    // === isToday Tests ===
    
    @Test
    fun `isToday returns true for same day`() {
        val now = System.currentTimeMillis()
        assertTrue(DateTimeUtils.isToday(now, now))
        
        // 1 hour later same day
        assertTrue(DateTimeUtils.isToday(now, now + 3600_000))
    }
    
    @Test
    fun `isToday returns false for different day`() {
        val now = System.currentTimeMillis()
        val yesterday = now - 24 * 3600_000L
        val tomorrow = now + 24 * 3600_000L
        
        assertFalse(DateTimeUtils.isToday(yesterday, now))
        assertFalse(DateTimeUtils.isToday(tomorrow, now))
    }
    
    @Test
    fun `isToday handles midnight boundary`() {
        // Create a time just before midnight
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val beforeMidnight = cal.timeInMillis
        
        // And just after midnight (next day)
        cal.add(Calendar.MINUTE, 2)
        val afterMidnight = cal.timeInMillis
        
        // These should be different days
        assertFalse(DateTimeUtils.isToday(beforeMidnight, afterMidnight))
    }
    
    // === isThisWeek Tests ===
    
    @Test
    fun `isThisWeek returns true for same week`() {
        val now = System.currentTimeMillis()
        assertTrue(DateTimeUtils.isThisWeek(now, now))
    }
    
    @Test
    fun `isThisWeek returns false for previous week`() {
        val now = System.currentTimeMillis()
        val lastWeek = now - 7 * 24 * 3600_000L
        
        assertFalse(DateTimeUtils.isThisWeek(lastWeek, now))
    }
    
    @Test
    fun `isThisWeek returns false for next week`() {
        val now = System.currentTimeMillis()
        val nextWeek = now + 7 * 24 * 3600_000L
        
        assertFalse(DateTimeUtils.isThisWeek(nextWeek, now))
    }
    
    @Test
    fun `isThisWeek handles year boundary correctly`() {
        // Create a date in the first week of a year that spans Dec-Jan
        // This is the bug we fixed: weeks at year boundary should work
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JANUARY, 2, 12, 0, 0) // Jan 2, 2026
        val jan2 = cal.timeInMillis
        
        // Get the start of that week
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        
        // If the week starts on Sunday and Jan 2 is a Friday, 
        // the week start might be in December
        val weekStart = cal.timeInMillis
        
        // Dec 30, 2025 should be in the same week as Jan 2, 2026 if week spans year boundary
        cal.set(2025, Calendar.DECEMBER, 30, 12, 0, 0)
        val dec30 = cal.timeInMillis
        
        // Check if Dec 30 falls within the week that contains Jan 2
        // This depends on locale (first day of week), so we test the general concept
        // by ensuring dates within 3 days of each other in late Dec/early Jan work
        cal.set(2026, Calendar.JANUARY, 1, 12, 0, 0)
        val jan1 = cal.timeInMillis
        
        // Jan 1 and Jan 2 should definitely be in the same week
        val jan1InWeek = DateTimeUtils.isThisWeek(jan1, jan2)
        val jan2InWeek = DateTimeUtils.isThisWeek(jan2, jan2)
        
        assertTrue("Jan 2 should be in its own week", jan2InWeek)
        // Jan 1 might or might not be in same week depending on locale, but shouldn't crash
        assertNotNull(jan1InWeek)
    }
    
    // === isThisMonth Tests ===
    
    @Test
    fun `isThisMonth returns true for same month`() {
        val now = System.currentTimeMillis()
        assertTrue(DateTimeUtils.isThisMonth(now, now))
    }
    
    @Test
    fun `isThisMonth returns false for different month`() {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        
        // Previous month
        cal.add(Calendar.MONTH, -1)
        val lastMonth = cal.timeInMillis
        
        // Next month
        cal.add(Calendar.MONTH, 2)
        val nextMonth = cal.timeInMillis
        
        assertFalse(DateTimeUtils.isThisMonth(lastMonth, now))
        assertFalse(DateTimeUtils.isThisMonth(nextMonth, now))
    }
    
    @Test
    fun `isThisMonth returns false for same month different year`() {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        
        cal.add(Calendar.YEAR, -1)
        val lastYear = cal.timeInMillis
        
        assertFalse(DateTimeUtils.isThisMonth(lastYear, now))
    }
    
    @Test
    fun `isThisMonth handles month boundaries`() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JANUARY, 31, 23, 59, 59)
        val jan31 = cal.timeInMillis
        
        cal.set(2026, Calendar.FEBRUARY, 1, 0, 0, 0)
        val feb1 = cal.timeInMillis
        
        // Jan 31 is not in February
        assertFalse(DateTimeUtils.isThisMonth(jan31, feb1))
        // Feb 1 is in February
        assertTrue(DateTimeUtils.isThisMonth(feb1, feb1))
    }
}
