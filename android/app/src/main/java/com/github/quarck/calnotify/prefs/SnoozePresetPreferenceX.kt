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

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.utils.find

class SnoozePresetPreferenceX @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    defStyleRes: Int = 0
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes) {

    var snoozePresetValue: String = Settings.DEFAULT_SNOOZE_PRESET
        private set

    init {
        dialogLayoutResource = R.layout.dialog_snooze_presets
        positiveButtonText = context.getString(android.R.string.ok)
        negativeButtonText = context.getString(android.R.string.cancel)
    }

    fun persistPreset(value: String) {
        snoozePresetValue = value
        persistString(value)
        notifyChanged()
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        snoozePresetValue = getPersistedString((defaultValue as? String) ?: Settings.DEFAULT_SNOOZE_PRESET)
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    class Dialog : PreferenceDialogFragmentCompat() {
        private var edit: EditText? = null

        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            val pref = preference as SnoozePresetPreferenceX
            edit = view.find(R.id.edit_text_snooze_presets)
            edit?.setText(pref.snoozePresetValue)
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                val value = edit?.text?.toString()

                if (value != null) {
                    val presets = PreferenceUtils.parseSnoozePresets(value)
                    if (presets != null) {
                        val newValue = if (presets.isEmpty()) {
                            Settings.DEFAULT_SNOOZE_PRESET
                        } else {
                            value.split(',')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .joinToString(", ")
                        }

                        val pref = preference as SnoozePresetPreferenceX
                        if (pref.callChangeListener(newValue)) {
                            pref.persistPreset(newValue)
                        }

                        if (presets.size > Consts.MAX_SUPPORTED_PRESETS) {
                            showMessage(R.string.error_too_many_presets)
                        }
                    } else {
                        showMessage(R.string.error_cannot_parse_preset)
                    }
                }
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

