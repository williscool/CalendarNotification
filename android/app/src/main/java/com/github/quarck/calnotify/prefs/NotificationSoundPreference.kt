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
import android.content.res.TypedArray
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import androidx.preference.Preference
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.notification.NotificationChannels

/**
 * A preference for configuring notification sounds.
 * 
 * On Android 8+ (API 26+): Opens the system notification channel settings where users
 * can configure the sound. The summary displays the current channel sound.
 * 
 * On Android 7.x (API 24-25): Uses a ringtone picker to select the sound directly,
 * since notification channels don't exist.
 */
class NotificationSoundPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    /**
     * The notification channel ID to configure (Android 8+).
     * Set via XML attribute app:channelId or programmatically.
     */
    var channelId: String = NotificationChannels.CHANNEL_ID_DEFAULT

    /**
     * Ringtone type for the picker (Android 7.x only).
     */
    var ringtoneType: Int = RingtoneManager.TYPE_NOTIFICATION

    // For Android 7.x - stores the selected ringtone URI
    private var currentRingtoneUri: Uri? = null

    init {
        // Parse custom attributes
        val a = context.obtainStyledAttributes(attrs, R.styleable.NotificationSoundPreference)
        channelId = a.getString(R.styleable.NotificationSoundPreference_channelId) 
            ?: NotificationChannels.CHANNEL_ID_DEFAULT
        a.recycle()

        // Parse ringtoneType from XML (for Android 7.x fallback)
        val b = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.ringtoneType))
        ringtoneType = b.getInt(0, RingtoneManager.TYPE_NOTIFICATION)
        b.recycle()
    }

    override fun onAttached() {
        super.onAttached()
        updateSummary()
    }

    /**
     * Updates the summary to show the current sound name.
     */
    fun updateSummary() {
        summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8+: Get sound from notification channel
            NotificationChannels.getChannelSoundTitle(context, channelId)
        } else {
            // Android 7.x: Get sound from stored preference
            if (currentRingtoneUri == null) {
                context.getString(R.string.silent)
            } else {
                val ringtone = RingtoneManager.getRingtone(context, currentRingtoneUri)
                ringtone?.getTitle(context) ?: context.getString(R.string.unknown)
            }
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        // For Android 7.x - load persisted ringtone URI
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val uriString = getPersistedString(defaultValue as? String)
            currentRingtoneUri = if (uriString.isNullOrEmpty()) null else Uri.parse(uriString)
        }
        updateSummary()
    }

    /**
     * Creates the appropriate intent based on Android version.
     * 
     * Android 8+: Opens system notification channel settings
     * Android 7.x: Opens ringtone picker
     */
    fun createIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8+: Open system channel settings
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            }
        } else {
            // Android 7.x: Open ringtone picker
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, ringtoneType)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentRingtoneUri)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, 
                    RingtoneManager.getDefaultUri(ringtoneType))
            }
        }
    }

    /**
     * Handles the result from the ringtone picker (Android 7.x only).
     */
    fun onRingtonePickerResult(uri: Uri?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (callChangeListener(uri)) {
                currentRingtoneUri = uri
                persistString(uri?.toString() ?: "")
                updateSummary()
            }
        }
    }

    /**
     * Returns true if this preference uses the ringtone picker (Android 7.x).
     * Returns false if it opens system settings (Android 8+).
     */
    fun usesRingtonePicker(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
    }
}

