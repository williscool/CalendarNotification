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

class NotificationSettingsFragmentX : PreferenceFragmentCompat() {

    private var pendingRingtonePreference: RingtonePreferenceX? = null

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        pendingRingtonePreference?.onRingtonePickerResult(uri)
        pendingRingtonePreference = null
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.notification_preferences_x, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference is RingtonePreferenceX) {
            pendingRingtonePreference = preference
            ringtonePickerLauncher.launch(preference.createRingtonePickerIntent())
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        val dialogFragment = when (preference) {
            is LEDColorPickerPreferenceX ->
                LEDColorPickerPreferenceX.Dialog.newInstance(preference.key)
            is LEDPatternPreferenceX ->
                LEDPatternPreferenceX.Dialog.newInstance(preference.key)
            is MaxNotificationsPreferenceX ->
                MaxNotificationsPreferenceX.Dialog.newInstance(preference.key)
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
        private const val DIALOG_FRAGMENT_TAG = "NotificationSettingsFragmentX.DIALOG"
    }
}

