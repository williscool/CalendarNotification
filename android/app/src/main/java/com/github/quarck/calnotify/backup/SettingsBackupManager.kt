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
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.github.quarck.calnotify.bluetooth.BTCarModeStorage
import com.github.quarck.calnotify.calendar.CalendarBackupInfo
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.logs.DevLog
import kotlinx.serialization.json.*
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result of a backup import operation.
 */
sealed class ImportResult {
    /**
     * Import succeeded. Contains stats about what was imported.
     */
    data class Success(val stats: ImportStats) : ImportResult()
    data class VersionTooNew(val backupVersion: Int, val supportedVersion: Int) : ImportResult()
    data class ParseError(val message: String) : ImportResult()
    data class IoError(val message: String) : ImportResult()
}

/**
 * Statistics about an import operation for user feedback.
 */
data class ImportStats(
    val settingsCount: Int = 0,
    val carModeSettingsCount: Int = 0,
    val calendarsMatched: Int = 0,
    val calendarsUnmatched: Int = 0,
    val unmatchedCalendarNames: List<String> = emptyList()
)

/**
 * Manages export and import of app settings.
 * 
 * Exports user preferences to JSON format, excluding runtime state.
 * Uses Storage Access Framework (SAF) for file access - no permissions needed.
 */
class SettingsBackupManager(private val context: Context) {

    companion object {
        private const val LOG_TAG = "SettingsBackupManager"

        /** MIME type for backup files */
        const val BACKUP_MIME_TYPE = "application/json"

        /** File extension for backup files */
        const val BACKUP_FILE_EXTENSION = ".json"

        /**
         * Generate a default filename for backup export.
         * Format: cnplus_settings_YYYYMMDD_HHmmss.json
         */
        fun generateBackupFilename(): String {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val timestamp = dateFormat.format(Date())
            return "cnplus_settings_$timestamp$BACKUP_FILE_EXTENSION"
        }

        // Keys to exclude from backup in DEFAULT preferences (runtime state, not user settings)
        // These are from PersistentState, ReminderState, CalendarMonitorState
        // NOTE: Only applied to default prefs, NOT car mode prefs
        private val EXCLUDED_DEFAULT_PREF_KEYS = setOf(
            // PersistentState keys (stored in default prefs with short names)
            "A", "B", "C",  // notificationLastFireTime, nextSnoozeAlarmExpectedAt, lastCustomSnoozeIntervalMillis
        )

        // Keys to exclude from CAR MODE preferences (runtime state, not user settings)
        // Car mode uses: "A" = trigger devices (user config, KEEP), "B" = silentUntil timestamp (runtime, EXCLUDE)
        private val EXCLUDED_CAR_MODE_PREF_KEYS = setOf(
            "B",  // carModeSilentUntil - runtime state, would cause incorrect silent mode if restored
        )

        // Calendar handled keys are exported separately with identifying info for cross-device matching
        private const val CALENDAR_IS_HANDLED_KEY_PREFIX = "calendar_handled_."

        // Keys that must be stored as Long (not Int) to avoid ClassCastException
        // These are read via getLong() in the app
        private val LONG_PREFERENCE_KEYS = setOf(
            "first_installed_ver",    // Settings.versionCodeFirstInstalled
            "manual_quiet_until"      // Settings.manualQuietPeriodUntil
        )

        // Preference file for car mode settings
        private const val CAR_MODE_PREFS_NAME = BTCarModeStorage.PREFS_NAME
    }

    private val json = Json { 
        prettyPrint = true 
        encodeDefaults = true
    }

    /**
     * Export all user settings to an OutputStream (for SAF).
     * @return true if export succeeded
     */
    fun exportToStream(outputStream: OutputStream): Boolean {
        return try {
            val backupData = createBackupData()
            val jsonString = json.encodeToString(BackupData.serializer(), backupData)
            outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            DevLog.info(LOG_TAG, "Settings exported successfully")
            true
        } catch (e: java.io.IOException) {
            DevLog.error(LOG_TAG, "IO error exporting settings: ${e.message}")
            false
        } catch (e: kotlinx.serialization.SerializationException) {
            DevLog.error(LOG_TAG, "Serialization error exporting settings: ${e.message}")
            false
        }
    }

    /**
     * Import settings from an InputStream (for SAF).
     * @return ImportResult indicating success or specific failure reason
     */
    fun importFromStream(inputStream: InputStream): ImportResult {
        return try {
            val jsonString = inputStream.bufferedReader(Charsets.UTF_8).readText()
            val backupData = try {
                json.decodeFromString(BackupData.serializer(), jsonString)
            } catch (e: kotlinx.serialization.SerializationException) {
                DevLog.error(LOG_TAG, "Failed to parse backup file: ${e.message}")
                return ImportResult.ParseError(e.message ?: "Invalid backup file format")
            }
            
            // Validate version
            if (backupData.version > BackupData.CURRENT_VERSION) {
                DevLog.error(LOG_TAG, "Backup version ${backupData.version} is newer than supported ${BackupData.CURRENT_VERSION}")
                return ImportResult.VersionTooNew(backupData.version, BackupData.CURRENT_VERSION)
            }

            val stats = restoreBackupData(backupData)
            DevLog.info(LOG_TAG, "Settings imported successfully from backup created at ${backupData.exportedAt}")
            ImportResult.Success(stats)
        } catch (e: java.io.IOException) {
            DevLog.error(LOG_TAG, "IO error importing settings: ${e.message}")
            ImportResult.IoError(e.message ?: "Failed to read backup file")
        }
    }

    /**
     * Create backup data from current settings.
     */
    private fun createBackupData(): BackupData {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        return BackupData(
            exportedAt = System.currentTimeMillis(),
            appVersionCode = pInfo.versionCode.toLong(),
            appVersionName = pInfo.versionName ?: "unknown",
            settings = exportSharedPreferences(getDefaultPreferences(), EXCLUDED_DEFAULT_PREF_KEYS),
            carModeSettings = exportSharedPreferences(getCarModePreferences(), EXCLUDED_CAR_MODE_PREF_KEYS),
            calendarSettings = exportCalendarSettings()
        )
    }

    /**
     * Restore backup data to SharedPreferences.
     * Each section is restored independently - failure in one doesn't stop others (best-effort).
     * @return ImportStats with counts of what was imported
     */
    private fun restoreBackupData(backupData: BackupData): ImportStats {
        var settingsCount = 0
        var carModeSettingsCount = 0
        var calendarsMatched = 0
        var calendarsUnmatched = 0
        var unmatchedCalendarNames = listOf<String>()

        // Restore main settings (best-effort)
        try {
            settingsCount = importToSharedPreferences(getDefaultPreferences(), backupData.settings, EXCLUDED_DEFAULT_PREF_KEYS, applyLongKeyFix = true)
        } catch (e: RuntimeException) {
            DevLog.error(LOG_TAG, "Failed to restore main settings, continuing with other sections: ${e.message}")
        }

        // Restore car mode settings (best-effort)
        try {
            carModeSettingsCount = importToSharedPreferences(getCarModePreferences(), backupData.carModeSettings, EXCLUDED_CAR_MODE_PREF_KEYS, applyLongKeyFix = false)
        } catch (e: RuntimeException) {
            DevLog.error(LOG_TAG, "Failed to restore car mode settings, continuing with other sections: ${e.message}")
        }

        // Restore calendar settings (best-effort)
        try {
            val calendarResult = importCalendarSettings(backupData.calendarSettings)
            calendarsMatched = calendarResult.first
            calendarsUnmatched = calendarResult.second
            unmatchedCalendarNames = calendarResult.third
        } catch (e: RuntimeException) {
            DevLog.error(LOG_TAG, "Failed to restore calendar settings: ${e.message}")
        }

        return ImportStats(
            settingsCount = settingsCount,
            carModeSettingsCount = carModeSettingsCount,
            calendarsMatched = calendarsMatched,
            calendarsUnmatched = calendarsUnmatched,
            unmatchedCalendarNames = unmatchedCalendarNames
        )
    }

    /**
     * Export SharedPreferences to a map of JSON elements.
     * @param excludedKeys Set of keys to exclude from export (runtime state)
     */
    private fun exportSharedPreferences(prefs: SharedPreferences, excludedKeys: Set<String>): Map<String, JsonElement> {
        val result = mutableMapOf<String, JsonElement>()
        val isDefaultPrefs = (excludedKeys === EXCLUDED_DEFAULT_PREF_KEYS)

        for ((key, value) in prefs.all) {
            // Skip excluded runtime state keys
            if (key in excludedKeys) continue
            // Skip calendar_handled_ keys - they're exported separately with identifying info (default prefs only)
            if (isDefaultPrefs && key.startsWith(CALENDAR_IS_HANDLED_KEY_PREFIX)) continue

            val jsonValue: JsonElement = when (value) {
                is Boolean -> JsonPrimitive(value)
                is Int -> JsonPrimitive(value)
                is Long -> JsonPrimitive(value)
                is Float -> JsonPrimitive(value)
                is String -> JsonPrimitive(value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    JsonArray((value as Set<String>).map { JsonPrimitive(it) })
                }
                null -> JsonNull
                else -> {
                    DevLog.warn(LOG_TAG, "Unknown preference type for key $key: ${value::class.simpleName}")
                    continue
                }
            }
            result[key] = jsonValue
        }

        return result
    }

    /**
     * Import JSON elements to SharedPreferences.
     * Best-effort: each key is imported independently, failures are logged and skipped.
     * @param excludedKeys Set of keys to skip on import (runtime state)
     * @param applyLongKeyFix If true, uses LONG_PREFERENCE_KEYS to ensure known Long values stay as Long
     * @return count of settings successfully imported
     */
    private fun importToSharedPreferences(prefs: SharedPreferences, data: Map<String, JsonElement>, excludedKeys: Set<String>, applyLongKeyFix: Boolean): Int {
        val editor = prefs.edit()
        var importedCount = 0

        for ((key, jsonValue) in data) {
            try {
                // Skip excluded keys on import (safety - shouldn't be in backup but just in case)
                if (key in excludedKeys) continue

                when (jsonValue) {
                    is JsonPrimitive -> {
                        when {
                            jsonValue.isString -> editor.putString(key, jsonValue.content)
                            jsonValue.booleanOrNull != null -> editor.putBoolean(key, jsonValue.boolean)
                            jsonValue.longOrNull != null -> {
                                val longVal = jsonValue.long
                                // Check if this key is known to be a Long type
                                if (applyLongKeyFix && key in LONG_PREFERENCE_KEYS) {
                                    // Always store as Long for known Long keys
                                    editor.putLong(key, longVal)
                                } else if (longVal in Int.MIN_VALUE..Int.MAX_VALUE) {
                                    // For other keys in Int range, check existing value type
                                    val existing = prefs.all[key]
                                    if (existing is Long) {
                                        editor.putLong(key, longVal)
                                    } else {
                                        editor.putInt(key, longVal.toInt())
                                    }
                                } else {
                                    // Value exceeds Int range, must be Long
                                    editor.putLong(key, longVal)
                                }
                            }
                            jsonValue.floatOrNull != null -> editor.putFloat(key, jsonValue.float)
                        }
                        importedCount++
                    }
                    is JsonArray -> {
                        val stringSet = jsonValue.mapNotNull { 
                            (it as? JsonPrimitive)?.contentOrNull 
                        }.toSet()
                        editor.putStringSet(key, stringSet)
                        importedCount++
                    }
                    is JsonNull -> {
                        editor.remove(key)
                        // Don't count removals as imports
                    }
                    else -> {
                        DevLog.warn(LOG_TAG, "Skipping unsupported JSON type for key $key")
                    }
                }
            } catch (e: RuntimeException) {
                DevLog.error(LOG_TAG, "Failed to import preference key '$key', skipping: ${e.message}")
            }
        }

        editor.apply()
        return importedCount
    }

    /**
     * Export calendar enabled/disabled settings with identifying info for cross-device matching.
     * Uses CalendarProvider to get calendar metadata (account, name, etc.)
     * Best-effort: failures for individual calendars are logged and skipped.
     */
    private fun exportCalendarSettings(): List<CalendarSettingBackup> {
        val result = mutableListOf<CalendarSettingBackup>()
        val prefs = getDefaultPreferences()
        val calendarProvider = CalendarProvider

        for ((key, value) in prefs.all) {
            try {
                if (!key.startsWith(CALENDAR_IS_HANDLED_KEY_PREFIX)) continue
                if (value !is Boolean) continue

                // Extract calendar ID from key: "calendar_handled_.<calendarId>"
                val calendarIdStr = key.removePrefix(CALENDAR_IS_HANDLED_KEY_PREFIX)
                val calendarId = calendarIdStr.toLongOrNull() ?: continue

                // Get calendar info for cross-device matching
                val backupInfo = calendarProvider.getCalendarBackupInfo(context, calendarId)
                if (backupInfo == null) {
                    DevLog.warn(LOG_TAG, "Could not get backup info for calendar $calendarId, skipping")
                    continue
                }

                result.add(CalendarSettingBackup(
                    accountName = backupInfo.accountName,
                    accountType = backupInfo.accountType,
                    displayName = backupInfo.displayName,
                    ownerAccount = backupInfo.ownerAccount,
                    name = backupInfo.name,
                    enabled = value
                ))
            } catch (e: RuntimeException) {
                DevLog.error(LOG_TAG, "Failed to export calendar setting for key '$key', skipping: ${e.message}")
            }
        }

        DevLog.info(LOG_TAG, "Exported ${result.size} calendar settings")
        return result
    }

    /**
     * Import calendar settings, matching by account+name to find correct calendar IDs on this device.
     * Best-effort: failures for individual calendars are logged and skipped.
     * @return Triple of (matched count, unmatched count, list of unmatched calendar names)
     */
    private fun importCalendarSettings(calendarSettings: List<CalendarSettingBackup>): Triple<Int, Int, List<String>> {
        if (calendarSettings.isEmpty()) {
            DevLog.info(LOG_TAG, "No calendar settings to import")
            return Triple(0, 0, emptyList())
        }

        val prefs = getDefaultPreferences()
        val editor = prefs.edit()
        val calendarProvider = CalendarProvider
        var matchedCount = 0
        var unmatchedCount = 0
        val unmatchedNames = mutableListOf<String>()

        for (setting in calendarSettings) {
            try {
                // Convert to CalendarBackupInfo for matching
                val backupInfo = CalendarBackupInfo(
                    calendarId = -1,  // Not used for matching
                    accountName = setting.accountName,
                    accountType = setting.accountType,
                    ownerAccount = setting.ownerAccount,
                    displayName = setting.displayName,
                    name = setting.name
                )

                // Find matching calendar on this device
                val newCalendarId = calendarProvider.findMatchingCalendarId(context, backupInfo)
                if (newCalendarId == -1L) {
                    DevLog.warn(LOG_TAG, "No matching calendar found for ${setting.displayName} (${setting.accountName})")
                    unmatchedCount++
                    unmatchedNames.add(setting.displayName)
                    continue
                }

                // Set the preference for the matched calendar
                val key = "$CALENDAR_IS_HANDLED_KEY_PREFIX$newCalendarId"
                editor.putBoolean(key, setting.enabled)
                DevLog.debug(LOG_TAG, "Matched calendar ${setting.displayName} -> ID $newCalendarId (enabled=${setting.enabled})")
                matchedCount++
            } catch (e: RuntimeException) {
                DevLog.error(LOG_TAG, "Failed to import calendar setting for ${setting.displayName}, skipping: ${e.message}")
                unmatchedCount++
                unmatchedNames.add("${setting.displayName} (error)")
            }
        }

        editor.apply()
        DevLog.info(LOG_TAG, "Imported calendar settings: $matchedCount matched, $unmatchedCount unmatched")
        return Triple(matchedCount, unmatchedCount, unmatchedNames)
    }

    private fun getDefaultPreferences(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    private fun getCarModePreferences(): SharedPreferences {
        return context.getSharedPreferences(CAR_MODE_PREFS_NAME, Context.MODE_PRIVATE)
    }
}

