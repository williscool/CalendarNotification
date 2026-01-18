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

    private data class FlagCase(val name: String, val baseFlags: Int, val expectedFlags: Int)

    /**
     * Test that on SDK 31+ (Android 12+), FLAG_IMMUTABLE is added.
     */
    @Test
    @Config(sdk = [34])
    fun `pendingIntentFlagCompat adds FLAG_IMMUTABLE on SDK 31+`() {
        val cases = listOf(
            FlagCase(
                name = "FLAG_UPDATE_CURRENT",
                baseFlags = PendingIntent.FLAG_UPDATE_CURRENT,
                expectedFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ),
            FlagCase(
                name = "FLAG_CANCEL_CURRENT",
                baseFlags = PendingIntent.FLAG_CANCEL_CURRENT,
                expectedFlags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ),
            FlagCase(
                name = "FLAG_NO_CREATE",
                baseFlags = PendingIntent.FLAG_NO_CREATE,
                expectedFlags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ),
            FlagCase(
                name = "zero flags",
                baseFlags = 0,
                expectedFlags = PendingIntent.FLAG_IMMUTABLE
            )
        )

        for (case in cases) {
            val result = pendingIntentFlagCompat(case.baseFlags)
            assertEquals("${case.name} should include FLAG_IMMUTABLE", case.expectedFlags, result)
        }
    }

    /**
     * Test that on SDK 30 (pre-Android 12), FLAG_IMMUTABLE is NOT added.
     */
    @Test
    @Config(sdk = [30])
    fun `pendingIntentFlagCompat does not add FLAG_IMMUTABLE on SDK 30`() {
        val cases = listOf(
            FlagCase(
                name = "FLAG_UPDATE_CURRENT",
                baseFlags = PendingIntent.FLAG_UPDATE_CURRENT,
                expectedFlags = PendingIntent.FLAG_UPDATE_CURRENT
            ),
            FlagCase(
                name = "zero flags",
                baseFlags = 0,
                expectedFlags = 0
            )
        )

        for (case in cases) {
            val result = pendingIntentFlagCompat(case.baseFlags)
            assertEquals("${case.name} should remain unchanged", case.expectedFlags, result)
        }
    }
}

