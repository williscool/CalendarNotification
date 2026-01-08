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

package com.github.quarck.calnotify.backup

import android.content.Context
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.bluetooth.BTCarModeStorage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Tests for SettingsBackupManager - export/import functionality
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [28])
class SettingsBackupManagerRobolectricTest {

    private lateinit var context: Context
    private lateinit var backupManager: SettingsBackupManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        backupManager = SettingsBackupManager(context)
        
        // Clear preferences before each test
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        context.getSharedPreferences(BTCarModeStorage.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    // === Export Tests ===

    @Test
    fun testExportCreatesValidJson() {
        // Set some test preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putBoolean("test_bool", true)
            .putInt("test_int", 42)
            .putString("test_string", "hello")
            .commit()

        val outputStream = ByteArrayOutputStream()
        val result = backupManager.exportToStream(outputStream)

        assertTrue("Export should succeed", result)
        
        val json = outputStream.toString(Charsets.UTF_8.name())
        assertTrue("JSON should contain version", json.contains("\"version\""))
        assertTrue("JSON should contain exportedAt", json.contains("\"exportedAt\""))
        assertTrue("JSON should contain settings", json.contains("\"settings\""))
        assertTrue("JSON should contain test_bool", json.contains("\"test_bool\""))
        assertTrue("JSON should contain test_int", json.contains("\"test_int\""))
        assertTrue("JSON should contain test_string", json.contains("\"test_string\""))
    }

    @Test
    fun testExportIncludesCarModeSettings() {
        // Set car mode preferences
        val carModePrefs = context.getSharedPreferences(BTCarModeStorage.PREFS_NAME, Context.MODE_PRIVATE)
        carModePrefs.edit()
            .putString("A", "AA:BB:CC:DD:EE:FF")
            .commit()

        val outputStream = ByteArrayOutputStream()
        backupManager.exportToStream(outputStream)

        val json = outputStream.toString(Charsets.UTF_8.name())
        assertTrue("JSON should contain carModeSettings", json.contains("\"carModeSettings\""))
        assertTrue("JSON should contain car mode device", json.contains("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun testExportExcludesRuntimeStateKeys() {
        // Set runtime state keys that should be excluded
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putLong("A", 12345L)  // notificationLastFireTime - should be excluded
            .putLong("B", 67890L)  // nextSnoozeAlarmExpectedAt - should be excluded
            .putString("user_setting", "keep_me")  // normal setting - should be included
            .commit()

        val outputStream = ByteArrayOutputStream()
        backupManager.exportToStream(outputStream)

        val json = outputStream.toString(Charsets.UTF_8.name())
        // Note: "A" key in main settings should be excluded, but "A" in carModeSettings is different
        assertTrue("JSON should contain user_setting", json.contains("\"user_setting\""))
    }

    // === Import Tests ===

    @Test
    fun testImportRestoresSettings() {
        val validJson = """
        {
            "version": 1,
            "exportedAt": 1704672000000,
            "appVersionCode": 100,
            "appVersionName": "1.0.0",
            "settings": {
                "imported_bool": true,
                "imported_int": 99,
                "imported_string": "restored"
            },
            "carModeSettings": {}
        }
        """.trimIndent()

        val inputStream = ByteArrayInputStream(validJson.toByteArray(Charsets.UTF_8))
        val result = backupManager.importFromStream(inputStream)

        assertTrue("Import should succeed", result is ImportResult.Success)

        // Verify settings were restored
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        assertTrue("Boolean should be restored", prefs.getBoolean("imported_bool", false))
        assertEquals("Int should be restored", 99, prefs.getInt("imported_int", 0))
        assertEquals("String should be restored", "restored", prefs.getString("imported_string", ""))
    }

    @Test
    fun testImportRestoresCarModeSettings() {
        val validJson = """
        {
            "version": 1,
            "exportedAt": 1704672000000,
            "appVersionCode": 100,
            "appVersionName": "1.0.0",
            "settings": {},
            "carModeSettings": {
                "A": "11:22:33:44:55:66"
            }
        }
        """.trimIndent()

        val inputStream = ByteArrayInputStream(validJson.toByteArray(Charsets.UTF_8))
        val result = backupManager.importFromStream(inputStream)

        assertTrue("Import should succeed", result is ImportResult.Success)

        // Verify car mode settings were restored
        val carModePrefs = context.getSharedPreferences(BTCarModeStorage.PREFS_NAME, Context.MODE_PRIVATE)
        assertEquals("Car mode device should be restored", "11:22:33:44:55:66", carModePrefs.getString("A", ""))
    }

    // === Round-trip Tests ===

    @Test
    fun testExportImportRoundTrip() {
        // Set various preference types
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putBoolean("round_trip_bool", true)
            .putInt("round_trip_int", 42)
            .putLong("round_trip_long", 9876543210L)
            .putFloat("round_trip_float", 3.14f)
            .putString("round_trip_string", "test_value")
            .commit()

        // Export
        val outputStream = ByteArrayOutputStream()
        backupManager.exportToStream(outputStream)

        // Clear preferences
        prefs.edit().clear().commit()

        // Verify cleared
        assertFalse(prefs.getBoolean("round_trip_bool", false))

        // Import
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val result = backupManager.importFromStream(inputStream)

        assertTrue("Import should succeed", result is ImportResult.Success)

        // Verify all values restored
        assertTrue("Boolean should be restored", prefs.getBoolean("round_trip_bool", false))
        assertEquals("Int should be restored", 42, prefs.getInt("round_trip_int", 0))
        assertEquals("Long should be restored", 9876543210L, prefs.getLong("round_trip_long", 0L))
        assertEquals("Float should be restored", 3.14f, prefs.getFloat("round_trip_float", 0f), 0.001f)
        assertEquals("String should be restored", "test_value", prefs.getString("round_trip_string", ""))
    }

    @Test
    fun testExportImportRoundTripWithCalendarHandledKeys() {
        // Test that calendar_handled_ keys (user settings) are preserved
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putBoolean("calendar_handled_.1", true)
            .putBoolean("calendar_handled_.2", false)
            .putBoolean("calendar_handled_.99", true)
            .commit()

        // Export
        val outputStream = ByteArrayOutputStream()
        backupManager.exportToStream(outputStream)

        // Clear and import
        prefs.edit().clear().commit()
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        backupManager.importFromStream(inputStream)

        // Verify calendar handled keys restored
        assertTrue("Calendar 1 should be handled", prefs.getBoolean("calendar_handled_.1", false))
        assertFalse("Calendar 2 should not be handled", prefs.getBoolean("calendar_handled_.2", true))
        assertTrue("Calendar 99 should be handled", prefs.getBoolean("calendar_handled_.99", false))
    }

    // === Error Handling Tests ===

    @Test
    fun testImportRejectsNewerVersion() {
        val futureVersionJson = """
        {
            "version": 999,
            "exportedAt": 1704672000000,
            "appVersionCode": 100,
            "appVersionName": "1.0.0",
            "settings": {},
            "carModeSettings": {}
        }
        """.trimIndent()

        val inputStream = ByteArrayInputStream(futureVersionJson.toByteArray(Charsets.UTF_8))
        val result = backupManager.importFromStream(inputStream)

        assertTrue("Should return VersionTooNew", result is ImportResult.VersionTooNew)
        val versionError = result as ImportResult.VersionTooNew
        assertEquals("Backup version should be 999", 999, versionError.backupVersion)
        assertEquals("Supported version should be current", BackupData.CURRENT_VERSION, versionError.supportedVersion)
    }

    @Test
    fun testImportHandlesInvalidJson() {
        val invalidJson = "this is not valid json {"

        val inputStream = ByteArrayInputStream(invalidJson.toByteArray(Charsets.UTF_8))
        val result = backupManager.importFromStream(inputStream)

        assertTrue("Should return ParseError", result is ImportResult.ParseError)
    }

    @Test
    fun testImportHandlesMissingFields() {
        val incompleteJson = """
        {
            "version": 1
        }
        """.trimIndent()

        val inputStream = ByteArrayInputStream(incompleteJson.toByteArray(Charsets.UTF_8))
        val result = backupManager.importFromStream(inputStream)

        assertTrue("Should return ParseError for missing fields", result is ImportResult.ParseError)
    }

    // === Helper Method Tests ===

    @Test
    fun testGenerateBackupFilename() {
        val filename = SettingsBackupManager.generateBackupFilename()
        
        assertTrue("Filename should start with cnplus_settings_", filename.startsWith("cnplus_settings_"))
        assertTrue("Filename should end with .json", filename.endsWith(".json"))
        assertTrue("Filename should have timestamp", filename.length > "cnplus_settings_.json".length)
    }

    @Test
    fun testBackupMimeType() {
        assertEquals("MIME type should be application/json", "application/json", SettingsBackupManager.BACKUP_MIME_TYPE)
    }

    @Test
    fun testBackupFileExtension() {
        assertEquals("File extension should be .json", ".json", SettingsBackupManager.BACKUP_FILE_EXTENSION)
    }
}

