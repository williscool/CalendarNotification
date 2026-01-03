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

import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.quarck.calnotify.R

class ReminderSettingsFragmentX : PreferenceFragmentCompat() {

    private var pendingNotificationSoundPreference: NotificationSoundPreference? = null

    private val activityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // For Android 7.x ringtone picker
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            pendingNotificationSoundPreference?.onRingtonePickerResult(uri)
        }
        // For Android 8+ system settings, refresh the summary when returning
        pendingNotificationSoundPreference?.updateSummary()
        pendingNotificationSoundPreference = null
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.reminder_preferences_x, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference is NotificationSoundPreference) {
            pendingNotificationSoundPreference = preference
            activityLauncher.launch(preference.createIntent())
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onResume() {
        super.onResume()
        // Refresh sound preference summary when returning from system settings
        findPreference<NotificationSoundPreference>("reminder_pref_key_ringtone")?.updateSummary()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        val dialogFragment = when (preference) {
            is ReminderPatternPreferenceX ->
                ReminderPatternPreferenceX.Dialog.newInstance(preference.key)
            is MaxRemindersPreferenceX ->
                MaxRemindersPreferenceX.Dialog.newInstance(preference.key)
            else -> {
                super.onDisplayPreferenceDialog(preference)
                return
            }
        }

        @Suppress("DEPRECATION")
        dialogFragment.setTargetFragment(this, 0)
        dialogFragment.show(parentFragmentManager, DIALOG_FRAGMENT_TAG)
    }

    companion object {
        private const val DIALOG_FRAGMENT_TAG = "ReminderSettingsFragmentX.DIALOG"
    }
}

