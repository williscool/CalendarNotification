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

package com.github.quarck.calnotify.ui

import android.app.AlertDialog
import android.content.res.Configuration
import android.os.Bundle
import android.preference.PreferenceActivity
import androidx.annotation.LayoutRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings

class SettingsActivity : PreferenceActivity() {

    private val delegate by lazy { AppCompatDelegate.create(this, null) }

    public override fun onCreate(savedInstanceState: Bundle?) {
        delegate.installViewFactory()
        delegate.onCreate(savedInstanceState)
        super.onCreate(savedInstanceState)
        
        // Add theme selector as list header
        addThemeHeader()
    }
    
    private fun addThemeHeader() {
        val headerView = layoutInflater.inflate(R.layout.settings_theme_header, null)
        val summaryView = headerView.findViewById<TextView>(R.id.theme_summary)
        
        summaryView?.text = getCurrentThemeName()
        
        headerView.setOnClickListener { showThemeDialog() }
        
        listView.addHeaderView(headerView, null, true)
    }
    
    private fun getCurrentThemeName(): String {
        val themeMode = Settings(this).themeMode
        val entries = resources.getStringArray(R.array.theme_mode_entries)
        val values = resources.getStringArray(R.array.theme_mode_values)
        val index = values.indexOf(themeMode.toString())
        return if (index >= 0) entries[index] else entries[0]
    }
    
    private fun showThemeDialog() {
        val entries = resources.getStringArray(R.array.theme_mode_entries)
        val values = resources.getStringArray(R.array.theme_mode_values)
        val currentMode = Settings(this).themeMode
        val currentIndex = values.indexOf(currentMode.toString()).coerceAtLeast(0)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.theme_setting_title)
            .setSingleChoiceItems(entries, currentIndex) { dialog, which ->
                val newMode = values[which].toInt()
                Settings(this).themeMode = newMode
                AppCompatDelegate.setDefaultNightMode(newMode)
                dialog.dismiss()
                // Recreate to apply theme
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        delegate?.onPostCreate(savedInstanceState)
    }

    val supportActionBar: ActionBar?
        get() = delegate.supportActionBar

    fun setSupportActionBar(toolbar: Toolbar?) =
            delegate.setSupportActionBar(toolbar)

    override fun getMenuInflater(): MenuInflater =
            delegate.menuInflater

    override fun setContentView(@LayoutRes layoutResID: Int) =
            delegate.setContentView(layoutResID)

    override fun setContentView(view: View) =
            delegate.setContentView(view)

    override fun setContentView(view: View, params: ViewGroup.LayoutParams) =
            delegate.setContentView(view, params)

    override fun addContentView(view: View, params: ViewGroup.LayoutParams) =
            delegate.addContentView(view, params)

    override fun onPostResume() {
        super.onPostResume()
        delegate.onPostResume()
    }

    override fun onTitleChanged(title: CharSequence, color: Int) {
        super.onTitleChanged(title, color)
        delegate.setTitle(title)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        delegate.onConfigurationChanged(newConfig)
    }

    override fun onStop() {
        super.onStop()
        delegate.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        delegate.onDestroy()
    }

    override fun invalidateOptionsMenu() =
            delegate.invalidateOptionsMenu()

    override fun onBuildHeaders(target: List<PreferenceActivity.Header>) =
            loadHeadersFromResource(R.xml.preference_headers, target)

    override fun isValidFragment(fragmentName: String) = true

//    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
//
////        if (android.R.id.home == item?.itemId) {
////            finish()
////            return true
////        }
//
//        return super.onOptionsItemSelected(item)
//    }
}
