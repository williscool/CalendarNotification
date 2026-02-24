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

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings

class UpcomingTimePresetPreferenceX @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    defStyleRes: Int = 0
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes) {

    var presetValue: String = Settings.DEFAULT_UPCOMING_TIME_PRESETS
        private set

    init {
        dialogLayoutResource = R.layout.dialog_upcoming_time_presets
        positiveButtonText = context.getString(android.R.string.ok)
        negativeButtonText = context.getString(android.R.string.cancel)
    }

    fun persistPreset(value: String) {
        presetValue = value
        persistString(value)
        notifyChanged()
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        presetValue = getPersistedString((defaultValue as? String) ?: Settings.DEFAULT_UPCOMING_TIME_PRESETS)
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    class Dialog : PreferenceDialogFragmentCompat() {
        private var edit: EditText? = null

        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            val pref = preference as UpcomingTimePresetPreferenceX

            val label = view.findViewById<TextView>(R.id.text_label_upcoming_presets)
            label?.text = getString(R.string.dialog_upcoming_time_presets_label, Settings.MAX_LOOKAHEAD_DAYS.toInt(), Settings.MAX_UPCOMING_TIME_PRESETS)

            edit = view.findViewById(R.id.edit_text_upcoming_time_presets)
            edit?.setText(pref.presetValue)
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (!positiveResult) return
            val value = edit?.text?.toString() ?: return

            val newValue = PreferenceUtils.normalizePresetInput(
                value, Settings.DEFAULT_UPCOMING_TIME_PRESETS
            ) { it > 0 && it <= Settings.MAX_LOOKAHEAD_MILLIS }

            if (newValue != null) {
                val pref = preference as UpcomingTimePresetPreferenceX
                if (pref.callChangeListener(newValue)) {
                    pref.persistPreset(newValue)
                }

                val parsed = PreferenceUtils.parseSnoozePresets(newValue)
                if (parsed != null && parsed.size > Settings.MAX_UPCOMING_TIME_PRESETS) {
                    showFormattedMessage(R.string.error_too_many_upcoming_presets, Settings.MAX_UPCOMING_TIME_PRESETS)
                }
            } else {
                showMessage(R.string.error_cannot_parse_preset)
            }
        }

        private fun showMessage(id: Int) {
            val context = requireContext()
            AlertDialog.Builder(context)
                .setMessage(context.getString(id))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .create()
                .show()
        }

        private fun showFormattedMessage(id: Int, vararg args: Any) {
            val context = requireContext()
            AlertDialog.Builder(context)
                .setMessage(context.getString(id, *args))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .create()
                .show()
        }

        companion object {
            fun newInstance(key: String): Dialog {
                val fragment = Dialog()
                val args = Bundle(1)
                args.putString(ARG_KEY, key)
                fragment.arguments = args
                return fragment
            }
        }
    }
}
