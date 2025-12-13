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

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.TimePicker
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.utils.findOrThrow
import com.github.quarck.calnotify.utils.hourCompat
import com.github.quarck.calnotify.utils.minuteCompat
import java.text.DateFormat
import java.util.*

class TimeOfDayPreferenceX @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    defStyleRes: Int = 0
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes) {

    var timeValue: Pair<Int, Int> = Pair(0, 0)
        private set

    init {
        dialogLayoutResource = R.layout.dialog_time_of_day
        positiveButtonText = context.getString(android.R.string.ok)
        negativeButtonText = context.getString(android.R.string.cancel)
    }

    fun persistTime(value: Pair<Int, Int>) {
        timeValue = value
        persistInt(PreferenceUtils.packTime(value))
        summary = formatTime(value)
        notifyChanged()
    }

    @Suppress("DEPRECATION")
    private fun formatTime(time: Pair<Int, Int>): String {
        val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
        val date = Date(1, 1, 1, time.first, time.second, 0)
        return timeFormatter.format(date)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val timeValueInt = getPersistedInt((defaultValue as? Int) ?: 0)
        timeValue = PreferenceUtils.unpackTime(timeValueInt)
        summary = formatTime(timeValue)
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any {
        return a.getInteger(index, 0)
    }

    class Dialog : PreferenceDialogFragmentCompat() {
        private lateinit var picker: TimePicker

        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            val pref = preference as TimeOfDayPreferenceX
            picker = view.findOrThrow(R.id.time_picker_pref_time_of_day)

            val isTwentyFourHour = android.text.format.DateFormat.is24HourFormat(context)
            picker.setIs24HourView(isTwentyFourHour)
            picker.hourCompat = pref.timeValue.first
            picker.minuteCompat = pref.timeValue.second
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                picker.clearFocus()
                val newValue = Pair(picker.hourCompat, picker.minuteCompat)
                val pref = preference as TimeOfDayPreferenceX
                if (pref.callChangeListener(newValue)) {
                    pref.persistTime(newValue)
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

