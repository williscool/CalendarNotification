//
//   Calendar Notifications Plus
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
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

package com.github.quarck.calnotify.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.logs.DevLog

/**
 * Router activity that delegates to the appropriate MainActivity implementation
 * based on the user's navigation UI preference.
 *
 * This is the launcher activity declared in AndroidManifest. It immediately
 * redirects to either [MainActivityLegacy] or [MainActivityModern] and finishes.
 *
 * **Legacy UI** ([MainActivityLegacy]):
 * - Original monolithic UI with a single RecyclerView
 * - All events shown in one scrollable list
 *
 * **Modern UI** ([MainActivityModern]):
 * - Fragment-based navigation with BottomNavigationView
 * - Separate tabs for Active, Upcoming, and Dismissed events
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val useNewUI = Settings(this).useNewNavigationUI
        val targetClass = if (useNewUI) {
            MainActivityModern::class.java
        } else {
            MainActivityLegacy::class.java
        }

        DevLog.info(LOG_TAG, "Routing to ${targetClass.simpleName} (useNewNavigationUI=$useNewUI)")

        val targetIntent = Intent(this, targetClass).apply {
            // Preserve any extras from the launching intent (e.g., from notifications)
            intent.extras?.let { putExtras(it) }
            // Clear the task so back button doesn't return to this router
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(targetIntent)
        finish()
    }

    companion object {
        private const val LOG_TAG = "MainActivity"

        // === Backward compatibility aliases ===
        // These delegate to MainActivityBase for code that references MainActivity.companion

        /**
         * Provider for DismissedEventsStorage to enable dependency injection in tests.
         */
        var dismissedEventsStorageProvider
            get() = MainActivityBase.dismissedEventsStorageProvider
            set(value) { MainActivityBase.dismissedEventsStorageProvider = value }

        /**
         * Provider for EventsStorage to enable dependency injection in tests.
         */
        var eventsStorageProvider
            get() = MainActivityBase.eventsStorageProvider
            set(value) { MainActivityBase.eventsStorageProvider = value }

        /**
         * Gets DismissedEventsStorage - uses provider if set, otherwise creates real instance.
         */
        fun getDismissedEventsStorage(context: android.content.Context) =
            MainActivityBase.getDismissedEventsStorage(context)

        /**
         * Gets EventsStorage - uses provider if set, otherwise creates real instance.
         */
        fun getEventsStorage(context: android.content.Context) =
            MainActivityBase.getEventsStorage(context)

        /**
         * Cleans up orphaned events that exist in both active and dismissed storage.
         */
        fun cleanupOrphanedEvents(context: android.content.Context) =
            MainActivityBase.cleanupOrphanedEvents(context)
    }
}
