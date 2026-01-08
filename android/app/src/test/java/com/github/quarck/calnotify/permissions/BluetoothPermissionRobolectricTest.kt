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
 * Tests for Bluetooth connect permission handling at different SDK levels.
 * BLUETOOTH_CONNECT permission is required on SDK 31+ (Android 12+).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])  // Default SDK for class setup; individual tests override as needed
class BluetoothPermissionRobolectricTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
    }

    /**
     * On SDK 30 (pre-Android 12), Bluetooth connect permission should always return true.
     * The legacy BLUETOOTH permission (declared in manifest) is sufficient.
     */
    @Test
    @Config(sdk = [30])
    fun `hasBluetoothConnectPermission returns true on SDK 30 regardless of permission state`() {
        val result = PermissionsManager.hasBluetoothConnectPermission(application)
        
        assertTrue("Should return true on SDK 30 (legacy BLUETOOTH permission is sufficient)", result)
    }

    /**
     * On SDK 31+ with BLUETOOTH_CONNECT permission granted, should return true.
     */
    @Test
    @Config(sdk = [31])
    fun `hasBluetoothConnectPermission returns true when permission granted on SDK 31`() {
        shadowOf(application).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        
        val result = PermissionsManager.hasBluetoothConnectPermission(application)
        
        assertTrue("Should return true when permission is granted on SDK 31", result)
    }

    /**
     * On SDK 31+ without BLUETOOTH_CONNECT permission, should return false.
     */
    @Test
    @Config(sdk = [31])
    fun `hasBluetoothConnectPermission returns false when permission denied on SDK 31`() {
        shadowOf(application).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        
        val result = PermissionsManager.hasBluetoothConnectPermission(application)
        
        assertFalse("Should return false when permission is denied on SDK 31", result)
    }

    /**
     * On SDK 34 (latest) with permission granted, should return true.
     */
    @Test
    @Config(sdk = [34])
    fun `hasBluetoothConnectPermission returns true when permission granted on SDK 34`() {
        shadowOf(application).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        
        val result = PermissionsManager.hasBluetoothConnectPermission(application)
        
        assertTrue("Should return true when permission is granted on SDK 34", result)
    }

    /**
     * On SDK 34 without permission, should return false.
     */
    @Test
    @Config(sdk = [34])
    fun `hasBluetoothConnectPermission returns false when permission denied on SDK 34`() {
        shadowOf(application).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        
        val result = PermissionsManager.hasBluetoothConnectPermission(application)
        
        assertFalse("Should return false when permission is denied on SDK 34", result)
    }

    /**
     * On SDK 28, permission should always be considered granted.
     */
    @Test
    @Config(sdk = [28])
    fun `hasBluetoothConnectPermission returns true on SDK 28`() {
        val result = PermissionsManager.hasBluetoothConnectPermission(application)
        
        assertTrue("Should return true on SDK 28 (permission not required)", result)
    }

    /**
     * On SDK 24 (minSdk), permission should always be considered granted.
     */
    @Test
    @Config(sdk = [24])
    fun `hasBluetoothConnectPermission returns true on SDK 24`() {
        val result = PermissionsManager.hasBluetoothConnectPermission(application)
        
        assertTrue("Should return true on SDK 24 (permission not required)", result)
    }

    /**
     * Bluetooth and calendar permissions should be independent.
     */
    @Test
    @Config(sdk = [34])
    fun `bluetooth and calendar permissions are independent`() {
        // Grant calendar permissions but not Bluetooth
        shadowOf(application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        shadowOf(application).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        
        assertTrue("Calendar permissions should be granted",
            PermissionsManager.hasAllCalendarPermissionsNoCache(application))
        assertFalse("Bluetooth connect permission should be denied",
            PermissionsManager.hasBluetoothConnectPermission(application))
    }

    /**
     * All permissions can be granted together.
     */
    @Test
    @Config(sdk = [34])
    fun `multiple permissions can be granted together`() {
        shadowOf(application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        assertTrue("Calendar permissions should be granted",
            PermissionsManager.hasAllCalendarPermissionsNoCache(application))
        assertTrue("Bluetooth connect permission should be granted",
            PermissionsManager.hasBluetoothConnectPermission(application))
        assertTrue("Notification permission should be granted",
            PermissionsManager.hasNotificationPermission(application))
    }

    /**
     * Verify request code constant is set.
     */
    @Test
    fun `permission request code is defined`() {
        assertEquals(
            "BLUETOOTH_CONNECT request code should be 3",
            3,
            PermissionsManager.PERMISSION_REQUEST_BLUETOOTH_CONNECT
        )
    }
}

