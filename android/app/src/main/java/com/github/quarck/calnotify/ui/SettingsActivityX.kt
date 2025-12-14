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

package com.github.quarck.calnotify.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.prefs.PreferenceHeadersFragment

/**
 * Modern Settings activity using AndroidX Preferences.
 * Supports DayNight themes properly.
 */
class SettingsActivityX : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_x)
        
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.settings_container, PreferenceHeadersFragment())
            }
        }
        
        supportFragmentManager.addOnBackStackChangedListener {
            updateTitle()
        }
        updateTitle()
    }
    
    private fun updateTitle() {
        val fragment = supportFragmentManager.findFragmentById(R.id.settings_container)
        title = if (fragment is PreferenceHeadersFragment) {
            getString(R.string.settings)
        } else {
            // Get title from fragment's preference screen
            (fragment as? PreferenceFragmentCompat)?.preferenceScreen?.title
                ?: getString(R.string.settings)
        }
    }
    
    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment ?: return false
        ).apply {
            arguments = args
        }
        
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.commit {
            replace(R.id.settings_container, fragment)
            addToBackStack(null)
        }
        return true
    }
    
    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return true
        }
        return super.onSupportNavigateUp()
    }
}

