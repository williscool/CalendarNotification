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

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.util.AttributeSet
import androidx.preference.Preference

/**
 * AndroidX-compatible RingtonePreference.
 * 
 * Unlike the legacy android.preference.RingtonePreference, this requires
 * manual handling of the ringtone picker result in the hosting fragment.
 */
class RingtonePreferenceX @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    var ringtoneType: Int = RingtoneManager.TYPE_NOTIFICATION

    private var currentRingtoneUri: Uri? = null

    init {
        // Parse ringtoneType from XML
        val a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.ringtoneType))
        ringtoneType = a.getInt(0, RingtoneManager.TYPE_NOTIFICATION)
        a.recycle()
    }

    fun getRingtoneUri(): Uri? = currentRingtoneUri

    fun setRingtoneUri(uri: Uri?) {
        currentRingtoneUri = uri
        persistString(uri?.toString() ?: "")
        updateSummary()
    }

    private fun updateSummary() {
        summary = if (currentRingtoneUri == null) {
            "Silent"
        } else {
            val ringtone = RingtoneManager.getRingtone(context, currentRingtoneUri)
            ringtone?.getTitle(context) ?: "Unknown"
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val uriString = getPersistedString(defaultValue as? String)
        currentRingtoneUri = if (uriString.isNullOrEmpty()) null else Uri.parse(uriString)
        updateSummary()
    }

    /**
     * Creates an intent for the ringtone picker.
     * The hosting fragment should launch this and handle the result.
     */
    fun createRingtonePickerIntent(): Intent {
        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, ringtoneType)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentRingtoneUri)
            
            val defaultUri = RingtoneManager.getDefaultUri(ringtoneType)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri)
        }
    }

    /**
     * Handles the result from the ringtone picker.
     */
    fun onRingtonePickerResult(uri: Uri?) {
        if (callChangeListener(uri)) {
            setRingtoneUri(uri)
        }
    }
}

