//
//   Calendar Notifications Plus
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
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


package com.github.quarck.calnotify.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionsManager {
    
    const val PERMISSION_REQUEST_CALENDAR = 0
    const val PERMISSION_REQUEST_LOCATION = 1
    const val PERMISSION_REQUEST_NOTIFICATIONS = 2
    const val PERMISSION_REQUEST_BLUETOOTH_CONNECT = 3
    
    private fun Context.hasPermission(perm: String) =
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun Activity.shouldShowRationale(perm: String) =
            ActivityCompat.shouldShowRequestPermissionRationale(this, perm)

    fun hasWriteCalendarNoCache(context: Context)
            = context.hasPermission(Manifest.permission.WRITE_CALENDAR)

    fun hasReadCalendarNoCache(context: Context)
            = context.hasPermission(Manifest.permission.READ_CALENDAR)

    fun hasCoarseLocationNoCache(context: Context)
            = context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    /**
     * Check if we have notification permission.
     * On Android 13+ (API 33), POST_NOTIFICATIONS is required.
     * On older versions, permission is implicitly granted.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true // Permission not required on older versions
        }
    }

    private var hasWriteCalendarCached: Boolean = false
    private var hasReadCalendarCached: Boolean = false
    private var hasAccessCoarseLocationCached: Boolean = false

    fun hasWriteCalendar(context: Context): Boolean {
        if (!hasWriteCalendarCached)
            hasWriteCalendarCached = hasWriteCalendarNoCache(context)
        return hasWriteCalendarCached
    }

    fun hasReadCalendar(context: Context): Boolean {
        if (!hasReadCalendarCached)
            hasReadCalendarCached = hasReadCalendarNoCache(context)
        return hasReadCalendarCached
    }

    fun hasAccessCoarseLocation(context: Context): Boolean {
        if (!hasAccessCoarseLocationCached)
            hasAccessCoarseLocationCached = hasCoarseLocationNoCache(context)
        return hasAccessCoarseLocationCached
    }

    fun hasAllCalendarPermissions(context: Context) = hasWriteCalendar(context) && hasReadCalendar(context)

    fun hasAllCalendarPermissionsNoCache(context: Context) = hasWriteCalendarNoCache(context) && hasReadCalendarNoCache(context)

    fun shouldShowCalendarRationale(activity: Activity) =
            activity.shouldShowRationale(Manifest.permission.WRITE_CALENDAR) || activity.shouldShowRationale(Manifest.permission.READ_CALENDAR)

    fun shouldShowLocationRationale(activity: Activity) =
            activity.shouldShowRationale(Manifest.permission.ACCESS_COARSE_LOCATION)

    /**
     * Check if we should show rationale for notification permission.
     * Only applicable on Android 13+ (API 33).
     */
    fun shouldShowNotificationRationale(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.shouldShowRationale(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            false
        }
    }

    fun requestCalendarPermissions(activity: Activity) =
            ActivityCompat.requestPermissions(activity,
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), 
                    PERMISSION_REQUEST_CALENDAR)

    fun requestLocationPermissions(activity: Activity) =
            ActivityCompat.requestPermissions(activity,
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 
                    PERMISSION_REQUEST_LOCATION)

    /**
     * Request notification permission (POST_NOTIFICATIONS).
     * Only applicable on Android 13+ (API 33). No-op on older versions.
     */
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_NOTIFICATIONS)
        }
    }

    /**
     * Check if we have Bluetooth connect permission.
     * On Android 12+ (API 31), BLUETOOTH_CONNECT is required to access paired devices.
     * On older versions, the legacy BLUETOOTH permission (declared in manifest) is sufficient.
     */
    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true // Legacy BLUETOOTH permission covers this on older versions
        }
    }

    /**
     * Check if we should show rationale for Bluetooth connect permission.
     * Only applicable on Android 12+ (API 31).
     */
    fun shouldShowBluetoothConnectRationale(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.shouldShowRationale(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            false
        }
    }

    /**
     * Request Bluetooth connect permission (BLUETOOTH_CONNECT).
     * Only applicable on Android 12+ (API 31). No-op on older versions.
     */
    fun requestBluetoothConnectPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(activity,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    PERMISSION_REQUEST_BLUETOOTH_CONNECT)
        }
    }
}
