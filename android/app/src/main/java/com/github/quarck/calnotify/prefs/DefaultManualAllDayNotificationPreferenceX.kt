//
//   Calendar Notifications Plus
//   Copyright (C) 2017 Sergey Parshin (s.parshin.sc@gmail.com)
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
import android.widget.RadioButton
import android.widget.TimePicker
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.utils.findOrThrow
import com.github.quarck.calnotify.utils.hourCompat
import com.github.quarck.calnotify.utils.minuteCompat

class DefaultManualAllDayNotificationPreferenceX @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    defStyleRes: Int = 0
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes) {

    var settingValue: Int = -480
        private set

    init {
        dialogLayoutResource = R.layout.dialog_default_manual_all_day_notification
        positiveButtonText = context.getString(android.R.string.ok)
        negativeButtonText = context.getString(android.R.string.cancel)
    }

    fun persistSettingValue(value: Int) {
        settingValue = value
        persistInt(value)
        notifyChanged()
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        settingValue = getPersistedInt((defaultValue as? Int) ?: -480)
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any {
        return a.getInteger(index, -480)
    }

    class Dialog : PreferenceDialogFragmentCompat() {
        private lateinit var timePicker: TimePicker
        private lateinit var radioButtonDayBefore: RadioButton
        private lateinit var radioButtonDayOfEvent: RadioButton

        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            val pref = preference as DefaultManualAllDayNotificationPreferenceX

            timePicker = view.findOrThrow(R.id.time_picker_pref_notification_time_of_day)
            radioButtonDayBefore = view.findOrThrow(R.id.radio_button_day_before)
            radioButtonDayOfEvent = view.findOrThrow(R.id.radio_button_day_of_event)

            val isTwentyFourHour = android.text.format.DateFormat.is24HourFormat(context)
            timePicker.setIs24HourView(isTwentyFourHour)

            if (pref.settingValue < 0) {
                // Reminder on the day before
                val hrMin = Consts.DAY_IN_MINUTES + pref.settingValue
                timePicker.hourCompat = hrMin / 60
                timePicker.minuteCompat = hrMin % 60

                radioButtonDayBefore.isChecked = true
                radioButtonDayOfEvent.isChecked = false
            } else {
                // Reminder at the day of event
                timePicker.hourCompat = pref.settingValue / 60
                timePicker.minuteCompat = pref.settingValue % 60

                radioButtonDayBefore.isChecked = false
                radioButtonDayOfEvent.isChecked = true
            }
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                timePicker.clearFocus()

                val isDayBefore = radioButtonDayBefore.isChecked
                val hrMin = timePicker.hourCompat * 60 + timePicker.minuteCompat

                val newValue = if (isDayBefore) hrMin - Consts.DAY_IN_MINUTES else hrMin

                val pref = preference as DefaultManualAllDayNotificationPreferenceX
                if (pref.callChangeListener(newValue)) {
                    pref.persistSettingValue(newValue)
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

