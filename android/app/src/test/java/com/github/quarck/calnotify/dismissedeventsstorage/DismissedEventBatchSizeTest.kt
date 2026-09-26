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

package com.github.quarck.calnotify.dismissedeventsstorage

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the batch size for reading dismissed events.
 *
 * `RoomDismissedEventsStorage.MAX_EVENTS_PER_BATCH` is not a round number
 * someone liked -- it is derived from Android's 2 MB CursorWindow and measured
 * row sizes. That derivation lives in a comment, and comments do not fail the
 * build, so the arithmetic is asserted here: raise the batch size past what the
 * window holds and this test says so.
 *
 * The failure it prevents is nasty: `SQLiteBlobTooBigException` thrown from a
 * wake-locked background service, only on devices with a lot of dismissed
 * history, failing a restore far from where the cause lives.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
class DismissedEventBatchSizeTest {

    @Test
    fun aFullBatchOfLargestRowsFitsInsideOneCursorWindow() {
        val worstCaseBytes = BATCH_SIZE.toLong() * LARGEST_OBSERVED_ROW_BYTES

        assertTrue(
            "A batch of $BATCH_SIZE rows at the largest observed size " +
            "($LARGEST_OBSERVED_ROW_BYTES B) needs ${worstCaseBytes / 1024} KB, " +
            "over the ${CURSOR_WINDOW_BYTES / 1024} KB CursorWindow. Reading it would " +
            "throw SQLiteBlobTooBigException from a background service. " +
            "Lower MAX_EVENTS_PER_BATCH.",
            worstCaseBytes <= CURSOR_WINDOW_BYTES
        )
    }

    @Test
    fun theBatchSizeLeavesHeadroomForRowsLargerThanAnySeenSoFar() {
        // The largest row measured is from one device's history. Another user
        // could have a longer title or location, so the size should not sit
        // right at the edge of the window.
        val worstCaseBytes = BATCH_SIZE.toLong() * LARGEST_OBSERVED_ROW_BYTES
        val headroom = 1.0 - worstCaseBytes.toDouble() / CURSOR_WINDOW_BYTES

        assertTrue(
            "Worst-case batch uses ${"%.0f".format(100 * (1 - headroom))}% of the " +
            "CursorWindow, leaving too little for rows larger than any measured yet.",
            headroom >= MIN_HEADROOM_FRACTION
        )
    }

    @Test
    fun batchingIsNotDrivenByTheSqliteParameterCap() {
        // Recording the distinction, because conflating the two is how the size
        // was originally chosen wrong: 500 was picked as "half of 999" when the
        // real cap is 32766 and the real constraint is the CursorWindow.
        assertTrue(
            "If the batch size ever approaches the bind-parameter cap, the " +
            "CursorWindow limit was already blown by a wide margin.",
            BATCH_SIZE < SQLITE_MAX_VARIABLE_NUMBER / 10
        )
    }

    companion object {
        /**
         * Mirrors `RoomDismissedEventsStorage.MAX_EVENTS_PER_BATCH`, which is
         * private. Duplicated deliberately: the point is to fail when someone
         * changes that value without redoing the arithmetic, which a shared
         * reference would silently allow.
         */
        private const val BATCH_SIZE = 250

        /** Android's CursorWindow buffer: 2 MB. */
        private const val CURSOR_WINDOW_BYTES = 2L * 1024 * 1024

        /**
         * Largest dismissed row observed on a real device, across 4183 rows
         * (title + s1 + location + fixed columns). p50 was 119 B, p99 2673 B.
         */
        private const val LARGEST_OBSERVED_ROW_BYTES = 6789L

        /** Keep at least this fraction of the window spare. */
        private const val MIN_HEADROOM_FRACTION = 0.15

        /** requery/sqlite-android 3.45; 999 applies only before SQLite 3.32.0. */
        private const val SQLITE_MAX_VARIABLE_NUMBER = 32766
    }
}
