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

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.database.SQLException
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import com.github.quarck.calnotify.BuildConfig
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.app.UndoManager
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorState
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorageInterface
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusSystemClock
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.powerManager
import org.jetbrains.annotations.NotNull

/**
 * Abstract base class for MainActivity variants (Legacy and Modern).
 * Contains shared functionality:
 * - Permission checking (calendar, notifications, battery optimization)
 * - ApplicationController lifecycle hooks
 * - Reminder state management
 * - Common menu item handling
 */
abstract class MainActivityBase : AppCompatActivity() {

    protected val settings: Settings by lazy { Settings(this) }
    
    protected val undoManager by lazy { UndoManager }

    open val clock: CNPlusClockInterface = CNPlusSystemClock()

    // Visible for testing - shared search UI components
    internal var searchView: SearchView? = null
    internal var searchMenuItem: MenuItem? = null

    protected var calendarRescanEnabled = true
    protected var shouldRemindForEventsWithNoReminders = true
    protected var shouldForceRepost = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevLog.debug(LOG_TAG, "onCreateView")
        ApplicationController.onMainActivityCreate(this)
    }

    override fun onStart() {
        DevLog.info(LOG_TAG, "onStart()")
        super.onStart()
        ApplicationController.onMainActivityStarted(this)
    }

    override fun onStop() {
        DevLog.info(LOG_TAG, "onStop()")
        super.onStop()
    }

    override fun onResume() {
        DevLog.info(LOG_TAG, "onResume")
        super.onResume()

        checkPermissions()
        
        if (calendarRescanEnabled != settings.enableCalendarRescan) {
            calendarRescanEnabled = settings.enableCalendarRescan
            if (!calendarRescanEnabled) {
                CalendarMonitorState(this).firstScanEver = true
            }
        }

        refreshReminderLastFired()

        var monitorSettingsChanged = false
        if (settings.shouldRemindForEventsWithNoReminders != shouldRemindForEventsWithNoReminders) {
            shouldRemindForEventsWithNoReminders = settings.shouldRemindForEventsWithNoReminders
            monitorSettingsChanged = true
        }

        background {
            ApplicationController.onMainActivityResumed(this, shouldForceRepost, monitorSettingsChanged)
            shouldForceRepost = false
        }

        invalidateOptionsMenu()
    }

    override fun onPause() {
        DevLog.info(LOG_TAG, "onPause")
        refreshReminderLastFired()
        undoManager.clearUndoState()
        super.onPause()
    }

    protected fun refreshReminderLastFired() {
        // avoid firing reminders when UI is active and user is interacting with it
        ReminderState(applicationContext).reminderLastFireTime = clock.currentTimeMillis()
    }

    protected fun checkPermissions() {
        val hasPermissions = PermissionsManager.hasAllCalendarPermissions(this)

        if (!hasPermissions) {
            if (PermissionsManager.shouldShowCalendarRationale(this)) {
                AlertDialog.Builder(this)
                    .setMessage(R.string.application_has_no_access)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        PermissionsManager.requestCalendarPermissions(this)
                    }
                    .setNegativeButton(R.string.exit) { _, _ ->
                        finish()
                    }
                    .create()
                    .show()
            } else {
                PermissionsManager.requestCalendarPermissions(this)
            }
        } else {
            // Check notification permission (Android 13+)
            checkNotificationPermission()
            
            // Check for power manager optimisations
            if (!settings.doNotShowBatteryOptimisationWarning &&
                !powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.battery_optimisation_title))
                    .setMessage(getString(R.string.battery_optimisation_details))
                    .setPositiveButton(getString(R.string.you_can_do_it)) { _, _ ->
                        val intent = Intent()
                            .setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID))
                        startActivity(intent)
                    }
                    .setNeutralButton(getString(R.string.you_can_do_it_later)) { _, _ -> }
                    .setNegativeButton(getString(R.string.you_cannot_do_it)) { _, _ ->
                        settings.doNotShowBatteryOptimisationWarning = true
                    }
                    .create()
                    .show()
            }
        }
    }

    /**
     * Check and request notification permission on Android 13+ (API 33).
     * This is required for the app to post notifications.
     */
    protected fun checkNotificationPermission() {
        if (!PermissionsManager.hasNotificationPermission(this)) {
            if (PermissionsManager.shouldShowNotificationRationale(this)) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.notification_permission_title)
                    .setMessage(R.string.notification_permission_explanation)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        PermissionsManager.requestNotificationPermission(this)
                    }
                    .setNegativeButton(R.string.cancel) { _, _ ->
                        // User declined, they won't get notifications
                    }
                    .create()
                    .show()
            } else {
                PermissionsManager.requestNotificationPermission(this)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        @NotNull permissions: Array<out String>,
        @NotNull grantResults: IntArray
    ) {
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                DevLog.error(LOG_TAG, "Permission is not granted!")
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /**
     * Handle common menu items shared between Legacy and Modern activities.
     * Returns true if the item was handled, false otherwise.
     */
    protected fun handleCommonMenuItem(item: MenuItem): Boolean {
        refreshReminderLastFired()

        return when (item.itemId) {
            R.id.action_settings -> {
                shouldForceRepost = true // so onResume would re-post everything
                startActivity(
                    Intent(this, SettingsActivityX::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                true
            }

            R.id.action_report_a_bug -> {
                startActivity(
                    Intent(this, ReportABugActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                true
            }

            R.id.action_about -> {
                startActivity(
                    Intent(this, AboutActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                true
            }

            R.id.my_react_activity -> {
                startActivity(
                    Intent(this, MyReactActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                true
            }

            R.id.action_test_page -> {
                startActivity(
                    Intent(this, TestActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                true
            }

            else -> false
        }
    }

    companion object {
        private const val LOG_TAG = "MainActivityBase"

        /**
         * Provider for DismissedEventsStorage to enable dependency injection in tests.
         */
        var dismissedEventsStorageProvider: (() -> DismissedEventsStorageInterface)? = null

        /**
         * Provider for EventsStorage to enable dependency injection in tests.
         */
        var eventsStorageProvider: ((android.content.Context) -> EventsStorageInterface)? = null

        /**
         * Gets DismissedEventsStorage - uses provider if set, otherwise creates real instance.
         */
        fun getDismissedEventsStorage(context: android.content.Context): DismissedEventsStorageInterface {
            return dismissedEventsStorageProvider?.invoke() ?: DismissedEventsStorage(context)
        }

        /**
         * Gets EventsStorage - uses provider if set, otherwise creates real instance.
         */
        fun getEventsStorage(context: android.content.Context): EventsStorageInterface {
            return eventsStorageProvider?.invoke(context) ?: EventsStorage(context)
        }

        /**
         * Cleans up orphaned events that exist in both active and dismissed storage.
         * This can happen if an event fails to delete from EventsStorage during dismissal.
         *
         * Events found in both storages are removed from EventsStorage (keeping them in dismissed).
         */
        fun cleanupOrphanedEvents(context: android.content.Context) {
            try {
                getDismissedEventsStorage(context).classCustomUse { dismissedStorage ->
                    getEventsStorage(context).classCustomUse { eventsStorage ->
                        val dismissedKeys = dismissedStorage.events.map {
                            Pair(it.event.eventId, it.event.instanceStartTime)
                        }.toSet()

                        if (dismissedKeys.isEmpty()) return@classCustomUse

                        val orphaned = eventsStorage.events.filter { event ->
                            dismissedKeys.contains(Pair(event.eventId, event.instanceStartTime))
                        }

                        if (orphaned.isNotEmpty()) {
                            DevLog.warn(LOG_TAG, "Found ${orphaned.size} orphaned events in both storages, cleaning up")
                            eventsStorage.deleteEvents(orphaned)
                        }
                    }
                }
            } catch (ex: SQLException) {
                DevLog.error(LOG_TAG, "Error during orphaned event cleanup: ${ex.message}")
            } catch (ex: IllegalStateException) {
                DevLog.error(LOG_TAG, "Error during orphaned event cleanup: ${ex.message}")
            }
        }
    }
}
