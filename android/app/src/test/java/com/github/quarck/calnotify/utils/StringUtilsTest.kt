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

class StringUtilsTest {
    
    // === truncateForChip Tests ===
    
    @Test
    fun `truncateForChip returns original string when shorter than max`() {
        assertEquals("Short", "Short".truncateForChip(15))
        assertEquals("Exactly15chars!", "Exactly15chars!".truncateForChip(15))
    }
    
    @Test
    fun `truncateForChip returns original string when equal to max`() {
        val exactly15 = "123456789012345" // 15 chars
        assertEquals(exactly15, exactly15.truncateForChip(15))
    }
    
    @Test
    fun `truncateForChip truncates with ellipsis when longer than max`() {
        val long = "This is a very long calendar name"
        val result = long.truncateForChip(15)
        assertEquals(15, result.length)
        assertTrue(result.endsWith("…"))
        assertEquals("This is a very…", result)
    }
    
    @Test
    fun `truncateForChip handles custom max length`() {
        val text = "Hello World"
        assertEquals("Hello…", text.truncateForChip(6))
        assertEquals("Hello Worl…", text.truncateForChip(11))
    }
    
    @Test
    fun `truncateForChip handles empty string`() {
        assertEquals("", "".truncateForChip(15))
    }
    
    @Test
    fun `truncateForChip handles single character`() {
        assertEquals("A", "A".truncateForChip(15))
    }
    
    @Test
    fun `truncateForChip with max length 1 returns ellipsis for long strings`() {
        assertEquals("…", "Hello".truncateForChip(1))
    }
    
    @Test
    fun `truncateForChip handles real calendar name examples`() {
        // From user's production app
        assertEquals("Transferred fro…", "Transferred from admin@upscalews.com".truncateForChip(16))
        assertEquals("harris.william%…", "harris.william%example.com@gtempaccount.com".truncateForChip(16))
        
        // Short names pass through
        assertEquals("Family", "Family".truncateForChip(15))
        assertEquals("My Meetups", "My Meetups".truncateForChip(15))
    }
    
    // === toLongOrNull Tests ===
    
    @Test
    fun `toLongOrNull parses valid long`() {
        assertEquals(123L, "123".toLongOrNull())
        assertEquals(-456L, "-456".toLongOrNull())
        assertEquals(0L, "0".toLongOrNull())
    }
    
    @Test
    fun `toLongOrNull returns null for invalid string`() {
        assertNull("abc".toLongOrNull())
        assertNull("".toLongOrNull())
        assertNull("12.34".toLongOrNull())
    }
    
    // === toIntOrNull Tests ===
    
    @Test
    fun `toIntOrNull parses valid int`() {
        assertEquals(123, "123".toIntOrNull())
        assertEquals(-456, "-456".toIntOrNull())
        assertEquals(0, "0".toIntOrNull())
    }
    
    @Test
    fun `toIntOrNull returns null for invalid string`() {
        assertNull("abc".toIntOrNull())
        assertNull("".toIntOrNull())
        assertNull("12.34".toIntOrNull())
    }
}
