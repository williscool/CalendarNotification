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

import android.app.PendingIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for pendingIntentFlagCompat at different SDK levels.
 * SDK 31+ requires FLAG_IMMUTABLE or FLAG_MUTABLE on PendingIntents.
 */
@RunWith(RobolectricTestRunner::class)
class PendingIntentFlagsRobolectricTest {

    /**
     * Test that on SDK 31+ (Android 12+), FLAG_IMMUTABLE is added.
     */
    @Test
    @Config(sdk = [34])
    fun `pendingIntentFlagCompat adds FLAG_IMMUTABLE on SDK 31+`() {
        val baseFlags = PendingIntent.FLAG_UPDATE_CURRENT
        val result = pendingIntentFlagCompat(baseFlags)
        
        // Should have both the base flag and FLAG_IMMUTABLE
        assertTrue("FLAG_UPDATE_CURRENT should be present",
            (result and PendingIntent.FLAG_UPDATE_CURRENT) != 0)
        assertTrue("FLAG_IMMUTABLE should be present on SDK 31+",
            (result and PendingIntent.FLAG_IMMUTABLE) != 0)
    }

    /**
     * Test that on SDK 30 (pre-Android 12), FLAG_IMMUTABLE is NOT added.
     */
    @Test
    @Config(sdk = [30])
    fun `pendingIntentFlagCompat does not add FLAG_IMMUTABLE on SDK 30`() {
        val baseFlags = PendingIntent.FLAG_UPDATE_CURRENT
        val result = pendingIntentFlagCompat(baseFlags)
        
        // Should only have the base flag
        assertEquals("Should only have FLAG_UPDATE_CURRENT on SDK 30",
            PendingIntent.FLAG_UPDATE_CURRENT, result)
    }

    /**
     * Test with FLAG_CANCEL_CURRENT base flag on SDK 31+.
     */
    @Test
    @Config(sdk = [34])
    fun `pendingIntentFlagCompat works with FLAG_CANCEL_CURRENT on SDK 31+`() {
        val baseFlags = PendingIntent.FLAG_CANCEL_CURRENT
        val result = pendingIntentFlagCompat(baseFlags)
        
        assertTrue("FLAG_CANCEL_CURRENT should be present",
            (result and PendingIntent.FLAG_CANCEL_CURRENT) != 0)
        assertTrue("FLAG_IMMUTABLE should be present on SDK 31+",
            (result and PendingIntent.FLAG_IMMUTABLE) != 0)
    }

    /**
     * Test with zero flags on SDK 31+.
     */
    @Test
    @Config(sdk = [34])
    fun `pendingIntentFlagCompat adds FLAG_IMMUTABLE to zero flags on SDK 31+`() {
        val result = pendingIntentFlagCompat(0)
        
        assertEquals("Should have only FLAG_IMMUTABLE when base is 0",
            PendingIntent.FLAG_IMMUTABLE, result)
    }

    /**
     * Test with zero flags on SDK 30 (pre-Android 12).
     */
    @Test
    @Config(sdk = [30])
    fun `pendingIntentFlagCompat returns zero for zero flags on SDK 30`() {
        val result = pendingIntentFlagCompat(0)
        
        assertEquals("Should return 0 when base is 0 on SDK 30", 0, result)
    }

    /**
     * Test with FLAG_NO_CREATE on SDK 31+.
     */
    @Test
    @Config(sdk = [34])
    fun `pendingIntentFlagCompat works with FLAG_NO_CREATE on SDK 31+`() {
        val baseFlags = PendingIntent.FLAG_NO_CREATE
        val result = pendingIntentFlagCompat(baseFlags)
        
        assertTrue("FLAG_NO_CREATE should be present",
            (result and PendingIntent.FLAG_NO_CREATE) != 0)
        assertTrue("FLAG_IMMUTABLE should be present on SDK 31+",
            (result and PendingIntent.FLAG_IMMUTABLE) != 0)
    }
}

