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
    object Success : ImportResult()
    data class VersionTooNew(val backupVersion: Int, val supportedVersion: Int) : ImportResult()
    data class ParseError(val message: String) : ImportResult()
    data class IoError(val message: String) : ImportResult()
}

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

        // Keys to exclude from backup (runtime state, not user settings)
        // These are from PersistentState, ReminderState, CalendarMonitorState
        private val EXCLUDED_KEYS = setOf(
            // PersistentState keys
            "A", "B", "C",  // notificationLastFireTime, nextSnoozeAlarmExpectedAt, lastCustomSnoozeIntervalMillis
            // Note: We don't exclude calendar_handled_ keys - those ARE user settings
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
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Failed to export settings: ${e.message}")
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

            restoreBackupData(backupData)
            DevLog.info(LOG_TAG, "Settings imported successfully from backup created at ${backupData.exportedAt}")
            ImportResult.Success
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
            settings = exportSharedPreferences(getDefaultPreferences()),
            carModeSettings = exportSharedPreferences(getCarModePreferences())
        )
    }

    /**
     * Restore backup data to SharedPreferences.
     */
    private fun restoreBackupData(backupData: BackupData) {
        importToSharedPreferences(getDefaultPreferences(), backupData.settings)
        importToSharedPreferences(getCarModePreferences(), backupData.carModeSettings)
    }

    /**
     * Export SharedPreferences to a map of JSON elements.
     */
    private fun exportSharedPreferences(prefs: SharedPreferences): Map<String, JsonElement> {
        val result = mutableMapOf<String, JsonElement>()

        for ((key, value) in prefs.all) {
            // Skip excluded runtime state keys
            if (key in EXCLUDED_KEYS) continue

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
     */
    private fun importToSharedPreferences(prefs: SharedPreferences, data: Map<String, JsonElement>) {
        val editor = prefs.edit()

        for ((key, jsonValue) in data) {
            // Skip excluded keys on import too (safety)
            if (key in EXCLUDED_KEYS) continue

            when (jsonValue) {
                is JsonPrimitive -> {
                    when {
                        jsonValue.isString -> editor.putString(key, jsonValue.content)
                        jsonValue.booleanOrNull != null -> editor.putBoolean(key, jsonValue.boolean)
                        jsonValue.longOrNull != null -> {
                            // Try to preserve int vs long based on value range
                            val longVal = jsonValue.long
                            if (longVal in Int.MIN_VALUE..Int.MAX_VALUE) {
                                // Check if original was likely an int by checking existing pref
                                val existing = prefs.all[key]
                                if (existing is Int || existing == null) {
                                    editor.putInt(key, longVal.toInt())
                                } else {
                                    editor.putLong(key, longVal)
                                }
                            } else {
                                editor.putLong(key, longVal)
                            }
                        }
                        jsonValue.floatOrNull != null -> editor.putFloat(key, jsonValue.float)
                    }
                }
                is JsonArray -> {
                    val stringSet = jsonValue.mapNotNull { 
                        (it as? JsonPrimitive)?.contentOrNull 
                    }.toSet()
                    editor.putStringSet(key, stringSet)
                }
                is JsonNull -> {
                    editor.remove(key)
                }
                else -> {
                    DevLog.warn(LOG_TAG, "Skipping unsupported JSON type for key $key")
                }
            }
        }

        editor.apply()
    }

    private fun getDefaultPreferences(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    private fun getCarModePreferences(): SharedPreferences {
        return context.getSharedPreferences(CAR_MODE_PREFS_NAME, Context.MODE_PRIVATE)
    }
}

