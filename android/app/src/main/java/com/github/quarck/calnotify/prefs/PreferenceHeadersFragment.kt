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

package com.github.quarck.calnotify.prefs

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings

/**
 * Main settings screen showing all settings categories.
 * Replaces the old preference_headers.xml approach.
 */
class PreferenceHeadersFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_headers_x, rootKey)
        
        // Handle theme selection
        findPreference<ListPreference>("theme_mode")?.apply {
            // Set initial value from Settings
            val settings = Settings(requireContext())
            value = settings.themeMode.toString()
            
            // Update summary to show current selection
            updateThemeSummary(this)
            
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, newValue ->
                val mode = (newValue as String).toInt()
                settings.themeMode = mode
                AppCompatDelegate.setDefaultNightMode(mode)
                // Use newValue to find the entry since the preference value hasn't been committed yet
                val listPref = pref as ListPreference
                val index = listPref.findIndexOfValue(newValue)
                pref.summary = if (index >= 0) listPref.entries[index] else getString(R.string.theme_setting_summary)
                true
            }
        }
        
        // Handle Calendars - launches separate activity
        findPreference<Preference>("pref_calendars")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), CalendarsActivity::class.java))
            true
        }
        
        // Handle Car Mode - launches separate activity
        findPreference<Preference>("pref_car_mode")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), CarModeActivity::class.java))
            true
        }
    }
    
    private fun updateThemeSummary(pref: ListPreference) {
        pref.summary = pref.entry ?: getString(R.string.theme_setting_summary)
    }
}

