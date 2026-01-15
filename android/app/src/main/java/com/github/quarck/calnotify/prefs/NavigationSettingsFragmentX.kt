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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.ui.MainActivity

/**
 * Settings fragment for Navigation/UI preferences.
 * Includes the new tabbed navigation toggle and upcoming events lookahead settings.
 */
class NavigationSettingsFragmentX : PreferenceFragmentCompat() {
    
    companion object {
        // Delay before restarting app to let user see the "Restarting..." toast
        private const val RESTART_DELAY_FOR_TOAST_VISIBILITY_MS = 500L
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.navigation_preferences, rootKey)
        
        // Set up click handler for "About Upcoming Events" help
        findPreference<Preference>("upcoming_events_help")?.setOnPreferenceClickListener {
            showUpcomingEventsHelpDialog()
            true
        }
        
        // Set up click handler for "Switch to Classic View" button
        findPreference<Preference>("switch_to_classic_view")?.setOnPreferenceClickListener {
            showSwitchToClassicViewDialog()
            true
        }
        
        // Set up click handler for "Switch to New View" button
        findPreference<Preference>("switch_to_new_view")?.setOnPreferenceClickListener {
            showSwitchToNewViewDialog()
            true
        }
    }
    
    private fun showUpcomingEventsHelpDialog() {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle(R.string.upcoming_events_help_dialog_title)
            .setMessage(R.string.upcoming_events_help_dialog_message)
            .setPositiveButton(R.string.upcoming_events_help_view_full_docs) { _, _ ->
                openUpcomingEventsDocumentation()
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }
    
    private fun openUpcomingEventsDocumentation() {
        val ctx = context ?: return
        val docsUrl = ctx.getString(R.string.upcoming_events_docs_url)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(docsUrl))
        startActivity(intent)
    }
    
    private fun showSwitchToClassicViewDialog() {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setMessage(R.string.switch_to_classic_view_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                switchToClassicView()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun showSwitchToNewViewDialog() {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setMessage(R.string.switch_to_new_view_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                switchToNewView()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun switchToClassicView() {
        val ctx = context ?: return
        val appContext = ctx.applicationContext
        
        // Disable new UI
        Settings(appContext).useNewNavigationUI = false
        
        // Show toast and restart
        Toast.makeText(ctx, R.string.restarting, Toast.LENGTH_SHORT).show()
        
        // Restart MainActivity after a short delay
        // Use applicationContext since activity may be destroyed during delay
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(appContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            appContext.startActivity(intent)
        }, RESTART_DELAY_FOR_TOAST_VISIBILITY_MS)
    }
    
    private fun switchToNewView() {
        val ctx = context ?: return
        val appContext = ctx.applicationContext
        
        // Enable new UI
        Settings(appContext).useNewNavigationUI = true
        
        // Show toast and restart
        Toast.makeText(ctx, R.string.restarting, Toast.LENGTH_SHORT).show()
        
        // Restart MainActivity after a short delay
        // Use applicationContext since activity may be destroyed during delay
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(appContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            appContext.startActivity(intent)
        }, RESTART_DELAY_FOR_TOAST_VISIBILITY_MS)
    }
}
