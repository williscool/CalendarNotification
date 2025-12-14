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
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.utils.toIntOrNull

class MaxRemindersPreferenceX @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    defStyleRes: Int = 0
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes) {

    var value: Int = 0
        private set

    init {
        dialogLayoutResource = R.layout.dialog_max_reminders
        positiveButtonText = context.getString(android.R.string.ok)
        negativeButtonText = context.getString(android.R.string.cancel)
    }

    fun persistValue(newValue: Int) {
        value = newValue
        persistString("$newValue") // backward compatibility
        notifyChanged()
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedString((defaultValue as? String) ?: "0").toIntOrNull() ?: 0
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    class Dialog : PreferenceDialogFragmentCompat() {
        private lateinit var picker: NumberPickerController

        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            val pref = preference as MaxRemindersPreferenceX
            picker = NumberPickerController(view, 0, 999)
            picker.value = pref.value
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                picker.clearFocus()
                val newValue = picker.value
                val pref = preference as MaxRemindersPreferenceX
                if (pref.callChangeListener(newValue)) {
                    pref.persistValue(newValue)
                }
            }
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

