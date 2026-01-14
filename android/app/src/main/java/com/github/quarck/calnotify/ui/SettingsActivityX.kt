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
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.prefs.NavigationSettingsFragmentX
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
            // Check if we should navigate directly to a specific fragment
            val prefFragment = intent.getStringExtra(EXTRA_PREF_FRAGMENT)
            val targetFragment = getFragmentForKey(prefFragment)
            
            if (targetFragment != null) {
                // Show headers first, then push target fragment
                supportFragmentManager.commit {
                    replace(R.id.settings_container, PreferenceHeadersFragment())
                }
                supportFragmentManager.commit {
                    replace(R.id.settings_container, targetFragment)
                    addToBackStack(null)
                }
            } else {
                supportFragmentManager.commit {
                    replace(R.id.settings_container, PreferenceHeadersFragment())
                }
            }
        }
        
        supportFragmentManager.addOnBackStackChangedListener {
            updateTitle()
        }
        updateTitle()
    }
    
    /**
     * Returns the fragment instance for a given preference key.
     * Used for direct navigation via intent extra.
     */
    private fun getFragmentForKey(key: String?): PreferenceFragmentCompat? {
        return when (key) {
            PREF_FRAGMENT_NAVIGATION -> NavigationSettingsFragmentX()
            else -> null
        }
    }
    
    companion object {
        /** Intent extra key for direct navigation to a preference fragment */
        const val EXTRA_PREF_FRAGMENT = "pref_fragment"
        
        /** Value for navigating to Navigation/UI settings */
        const val PREF_FRAGMENT_NAVIGATION = "navigation"
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

