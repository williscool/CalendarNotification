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

package com.github.quarck.calnotify.permissions

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for notification permission handling at different SDK levels.
 * POST_NOTIFICATIONS permission is required on SDK 33+ (Android 13+).
 */
@RunWith(RobolectricTestRunner::class)
class NotificationPermissionRobolectricTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
    }

    /**
     * On SDK 32 (pre-Android 13), notification permission should always return true.
     */
    @Test
    @Config(sdk = [32])
    fun `hasNotificationPermission returns true on SDK 32 regardless of permission state`() {
        // Even without granting the permission, it should return true on pre-33
        val result = PermissionsManager.hasNotificationPermission(application)
        
        assertTrue("Should return true on SDK 32 (permission not required)", result)
    }

    /**
     * On SDK 33+ with permission granted, should return true.
     */
    @Test
    @Config(sdk = [34])
    fun `hasNotificationPermission returns true when permission granted on SDK 34`() {
        // Grant the notification permission
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        
        val result = PermissionsManager.hasNotificationPermission(application)
        
        assertTrue("Should return true when permission is granted", result)
    }

    /**
     * On SDK 33+ without permission, should return false.
     */
    @Test
    @Config(sdk = [34])
    fun `hasNotificationPermission returns false when permission denied on SDK 34`() {
        // Explicitly deny the permission (this is the default in Robolectric)
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        
        val result = PermissionsManager.hasNotificationPermission(application)
        
        assertFalse("Should return false when permission is denied on SDK 34", result)
    }

    /**
     * On SDK 30, permission should always be considered granted.
     */
    @Test
    @Config(sdk = [30])
    fun `hasNotificationPermission returns true on SDK 30`() {
        val result = PermissionsManager.hasNotificationPermission(application)
        
        assertTrue("Should return true on SDK 30 (permission not required)", result)
    }

    /**
     * On SDK 24 (minSdk), permission should always be considered granted.
     */
    @Test
    @Config(sdk = [24])
    fun `hasNotificationPermission returns true on SDK 24`() {
        val result = PermissionsManager.hasNotificationPermission(application)
        
        assertTrue("Should return true on SDK 24 (permission not required)", result)
    }

    /**
     * Calendar permissions should still work independently of notification permission.
     */
    @Test
    @Config(sdk = [34])
    fun `calendar and notification permissions are independent`() {
        // Grant calendar permissions but not notification
        shadowOf(application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        
        assertTrue("Calendar permissions should be granted",
            PermissionsManager.hasAllCalendarPermissionsNoCache(application))
        assertFalse("Notification permission should be denied",
            PermissionsManager.hasNotificationPermission(application))
    }

    /**
     * Both permissions can be granted together.
     */
    @Test
    @Config(sdk = [34])
    fun `both calendar and notification permissions can be granted`() {
        shadowOf(application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        assertTrue("Calendar permissions should be granted",
            PermissionsManager.hasAllCalendarPermissionsNoCache(application))
        assertTrue("Notification permission should be granted",
            PermissionsManager.hasNotificationPermission(application))
    }
}

