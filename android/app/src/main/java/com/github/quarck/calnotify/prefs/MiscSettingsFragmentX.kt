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

package com.github.quarck.calnotify.prefs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.backup.ImportResult
import com.github.quarck.calnotify.backup.SettingsBackupManager
import com.github.quarck.calnotify.logs.DevLog

class MiscSettingsFragmentX : PreferenceFragmentCompat() {

    companion object {
        private const val LOG_TAG = "MiscSettingsFragmentX"
    }

    private lateinit var backupManager: SettingsBackupManager

    // SAF launcher for creating (exporting) a backup file
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                DevLog.info(LOG_TAG, "Export destination selected: $uri")
                performExport(uri)
            }
        }
    }

    // SAF launcher for opening (importing) a backup file
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                DevLog.info(LOG_TAG, "Import source selected: $uri")
                showImportConfirmation(uri)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.misc_preferences, rootKey)

        backupManager = SettingsBackupManager(requireContext())

        // Set up export preference click
        findPreference<Preference>("export_settings")?.setOnPreferenceClickListener {
            launchExport()
            true
        }

        // Set up import preference click
        findPreference<Preference>("import_settings")?.setOnPreferenceClickListener {
            launchImport()
            true
        }
    }

    private fun launchExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = SettingsBackupManager.BACKUP_MIME_TYPE
            putExtra(Intent.EXTRA_TITLE, SettingsBackupManager.generateBackupFilename())
        }
        exportLauncher.launch(intent)
    }

    private fun launchImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = SettingsBackupManager.BACKUP_MIME_TYPE
        }
        importLauncher.launch(intent)
    }

    private fun performExport(uri: android.net.Uri) {
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                val success = backupManager.exportToStream(outputStream)
                if (success) {
                    showToast(R.string.export_success)
                } else {
                    showToast(R.string.export_failed)
                }
            } ?: run {
                DevLog.error(LOG_TAG, "Failed to open output stream for export")
                showToast(R.string.export_failed)
            }
        } catch (e: java.io.IOException) {
            DevLog.error(LOG_TAG, "IO error during export: ${e.message}")
            showToast(R.string.export_failed)
        } catch (e: SecurityException) {
            DevLog.error(LOG_TAG, "Security error during export: ${e.message}")
            showToast(R.string.export_failed)
        }
    }

    private fun showImportConfirmation(uri: android.net.Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.import_confirm_title)
            .setMessage(R.string.import_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                performImport(uri)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performImport(uri: android.net.Uri) {
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                when (val result = backupManager.importFromStream(inputStream)) {
                    is ImportResult.Success -> {
                        showToast(R.string.import_success)
                    }
                    is ImportResult.VersionTooNew -> {
                        showToast(R.string.import_version_too_new)
                    }
                    is ImportResult.ParseError -> {
                        DevLog.error(LOG_TAG, "Import parse error: ${result.message}")
                        showToast(R.string.import_invalid_file)
                    }
                    is ImportResult.IoError -> {
                        DevLog.error(LOG_TAG, "Import IO error: ${result.message}")
                        showToast(R.string.import_failed)
                    }
                }
            } ?: run {
                DevLog.error(LOG_TAG, "Failed to open input stream for import")
                showToast(R.string.import_failed)
            }
        } catch (e: java.io.IOException) {
            DevLog.error(LOG_TAG, "IO error during import: ${e.message}")
            showToast(R.string.import_failed)
        } catch (e: SecurityException) {
            DevLog.error(LOG_TAG, "Security error during import: ${e.message}")
            showToast(R.string.import_failed)
        }
    }

    private fun showToast(messageResId: Int) {
        Toast.makeText(requireContext(), messageResId, Toast.LENGTH_LONG).show()
    }
}
