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
import android.widget.*
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.google.android.material.snackbar.Snackbar
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.utils.findOrThrow

class ReminderPatternPreferenceX @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    defStyleRes: Int = 0
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes) {

    var patternValue: String = "10m"
        private set

    init {
        dialogLayoutResource = R.layout.dialog_reminder_interval_configuration
        positiveButtonText = context.getString(android.R.string.ok)
        negativeButtonText = context.getString(android.R.string.cancel)
    }

    fun persistPattern(value: String) {
        patternValue = value
        persistString(value)
        notifyChanged()
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        patternValue = getPersistedString((defaultValue as? String) ?: "10m")
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any? {
        return a.getString(index) ?: "10m"
    }

    class Dialog : PreferenceDialogFragmentCompat(), AdapterView.OnItemSelectedListener {
        private val SecondsIndex = 0
        private val MinutesIndex = 1
        private val HoursIndex = 2
        private val DaysIndex = 3

        private var reminderPatternMillis = longArrayOf(0)
        private var simpleIntervalMode = true

        private lateinit var numberPicker: NumberPicker
        private lateinit var timeUnitsSpinners: Spinner
        private lateinit var checkboxCustomPattern: CheckBox
        private lateinit var editTextCustomPattern: EditText
        private lateinit var layoutSimpleInterval: LinearLayout
        private lateinit var layoutCustomPattern: LinearLayout

        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            numberPicker = view.findOrThrow(R.id.numberPickerTimeInterval)
            timeUnitsSpinners = view.findOrThrow(R.id.spinnerTimeIntervalUnit)
            checkboxCustomPattern = view.findOrThrow(R.id.checkbox_custom_reminder_pattern)
            editTextCustomPattern = view.findOrThrow(R.id.edittext_custom_reminder_pattern)
            layoutSimpleInterval = view.findOrThrow(R.id.layout_reminder_interval_simple)
            layoutCustomPattern = view.findOrThrow(R.id.layout_reminder_interval_custom)

            timeUnitsSpinners.adapter = ArrayAdapter(
                view.context,
                android.R.layout.simple_list_item_1,
                view.context.resources.getStringArray(R.array.time_units_plurals_with_seconds)
            )

            timeUnitsSpinners.onItemSelectedListener = this
            timeUnitsSpinners.setSelection(MinutesIndex)

            numberPicker.minValue = 1
            numberPicker.maxValue = 100

            val pref = preference as ReminderPatternPreferenceX
            val pattern = PreferenceUtils.parseSnoozePresets(pref.patternValue)
            if (pattern != null) {
                reminderPatternMillis = pattern
            }

            simpleIntervalMode = reminderPatternMillis.size == 1
            checkboxCustomPattern.isChecked = !simpleIntervalMode
            updateLayout()

            checkboxCustomPattern.setOnClickListener {
                simpleIntervalMode = !checkboxCustomPattern.isChecked
                updateLayout()
            }
        }

        private fun updateLayout() {
            if (simpleIntervalMode) {
                simpleIntervalSeconds = (reminderPatternMillis[0] / 1000L).toInt()
                layoutSimpleInterval.visibility = View.VISIBLE
                layoutCustomPattern.visibility = View.GONE
            } else {
                editTextCustomPattern.setText(PreferenceUtils.formatPattern(reminderPatternMillis))
                layoutSimpleInterval.visibility = View.GONE
                layoutCustomPattern.visibility = View.VISIBLE
            }
        }

        private fun clearFocus() {
            numberPicker.clearFocus()
            timeUnitsSpinners.clearFocus()
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                clearFocus()

                val pref = preference as ReminderPatternPreferenceX
                val newPattern: String

                if (simpleIntervalMode) {
                    var simpleIntervalMillis = simpleIntervalSeconds * 1000L
                    if (simpleIntervalMillis == 0L) {
                        simpleIntervalMillis = 60 * 1000L
                        view?.let { Snackbar.make(it, R.string.invalid_reminder_interval, Snackbar.LENGTH_LONG).show() }
                    }
                    reminderPatternMillis = longArrayOf(simpleIntervalMillis)
                    newPattern = PreferenceUtils.formatPattern(reminderPatternMillis)
                } else {
                    val text = editTextCustomPattern.text.toString()
                    val pattern = PreferenceUtils.parseSnoozePresets(text)

                    if (pattern != null && pattern.isNotEmpty()) {
                        reminderPatternMillis = pattern.map {
                            Math.max(it, Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
                        }.toLongArray()
                        newPattern = PreferenceUtils.formatPattern(reminderPatternMillis)
                    } else {
                        view?.let { Snackbar.make(it, R.string.error_cannot_parse_pattern, Snackbar.LENGTH_LONG).show() }
                        return
                    }
                }

                if (pref.callChangeListener(newPattern)) {
                    pref.persistPattern(newPattern)
                }
            }
        }

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (position == SecondsIndex) {
                numberPicker.minValue = Consts.MIN_REMINDER_INTERVAL_SECONDS
                numberPicker.maxValue = 60
            } else {
                numberPicker.minValue = 1
                numberPicker.maxValue = 100
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {}

        private var simpleIntervalSeconds: Int
            get() {
                clearFocus()
                val number = numberPicker.value
                val multiplier = when (timeUnitsSpinners.selectedItemPosition) {
                    SecondsIndex -> 1
                    MinutesIndex -> 60
                    HoursIndex -> 60 * 60
                    DaysIndex -> 24 * 60 * 60
                    else -> throw Exception("Unknown time unit")
                }
                return (number * multiplier)
            }
            set(value) {
                var number = value
                var units = SecondsIndex

                if ((number % 60) == 0) {
                    units = MinutesIndex
                    number /= 60
                }
                if ((number % 60) == 0) {
                    units = HoursIndex
                    number /= 60
                }
                if ((number % 24) == 0) {
                    units = DaysIndex
                    number /= 24
                }

                timeUnitsSpinners.setSelection(units)
                numberPicker.value = number
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

