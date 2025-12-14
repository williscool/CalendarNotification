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

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.quarck.calnotify.R

class SnoozeSettingsFragmentX : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.snooze_preferences_x, rootKey)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is SnoozePresetPreferenceX) {
            val dialogFragment = SnoozePresetPreferenceX.Dialog.newInstance(preference.key)
            @Suppress("DEPRECATION")
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, DIALOG_FRAGMENT_TAG)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    companion object {
        private const val DIALOG_FRAGMENT_TAG = "SnoozeSettingsFragmentX.DIALOG"
    }
}

