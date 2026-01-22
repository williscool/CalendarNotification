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

import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.NumberPicker
import android.widget.TextView
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.findOrThrow

class TimeIntervalPickerController(
        val view: View,
        titleId: Int?,
        val maxIntervalMilliseconds: Long, // 0 by default
        val allowSubMinuteIntervals: Boolean // false by default
) {

    var SecondsIndex = -1
    var MinutesIndex = 0
    var HoursIndex = 1
    var DaysIndex = 2

    var numberPicker: NumberPicker
    var timeUnitsDropdown: AutoCompleteTextView
    private var selectedPosition: Int = 0
    private lateinit var timeUnitsArray: Array<String>

    init {
        numberPicker = view.findOrThrow<NumberPicker>(R.id.numberPickerTimeInterval)
        timeUnitsDropdown = view.findOrThrow<AutoCompleteTextView>(R.id.spinnerTimeIntervalUnit)

        if (allowSubMinuteIntervals) {
            timeUnitsArray = view.context.resources.getStringArray(R.array.time_units_plurals_with_seconds)
            SecondsIndex += 1
            MinutesIndex += 1
            HoursIndex += 1
            DaysIndex += 1
        }
        else {
            timeUnitsArray = view.context.resources.getStringArray(R.array.time_units_plurals)
        }

        val adapter = ArrayAdapter(
                view.context,
                android.R.layout.simple_dropdown_item_1line,
                timeUnitsArray
        )
        timeUnitsDropdown.setAdapter(adapter)

        timeUnitsDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedPosition = position
            onItemSelected(position)
        }

        val label: TextView? = view.find<TextView>(R.id.textViewTimeIntervalTitle)
        if (titleId != null)
            label?.setText(titleId)
        else
            label?.visibility = View.GONE

        setSelection(MinutesIndex)

        numberPicker.minValue = 1
        numberPicker.maxValue = 100
    }

    private fun setSelection(position: Int) {
        selectedPosition = position
        if (position >= 0 && position < timeUnitsArray.size) {
            timeUnitsDropdown.setText(timeUnitsArray[position], false)
        }
    }

    fun clearFocus() {
        numberPicker.clearFocus()
        timeUnitsDropdown.clearFocus()
    }


    private fun onItemSelected(position: Int) {
        if (maxIntervalMilliseconds == 0L) {
            if (position == SecondsIndex) {
                numberPicker.minValue = 15
                numberPicker.maxValue = 60
            } else {
                numberPicker.minValue = 1
                numberPicker.maxValue = 100
            }
            return
        }


        val maxValue =
            when (selectedPosition) {
                SecondsIndex ->
                    maxIntervalMilliseconds / 1000L
                MinutesIndex ->
                    maxIntervalMilliseconds / Consts.MINUTE_IN_MILLISECONDS
                HoursIndex ->
                    maxIntervalMilliseconds / Consts.HOUR_IN_MILLISECONDS
                DaysIndex ->
                    maxIntervalMilliseconds / Consts.DAY_IN_MILLISECONDS
                else ->
                    throw Exception("Unknown time unit")
            }

        numberPicker.maxValue = Math.min(maxValue.toInt(), 100)
    }

    var intervalSeconds: Int
        get() {
            clearFocus()

            val number = numberPicker.value

            val multiplier =
                    when (selectedPosition) {
                        SecondsIndex ->
                            1
                        MinutesIndex ->
                            60
                        HoursIndex ->
                            60 * 60
                        DaysIndex ->
                            24 * 60 * 60
                        else ->
                            throw Exception("Unknown time unit")
                    }

            return (number * multiplier).toInt()
        }
        set(value) {

            if (allowSubMinuteIntervals) {
                var number = value
                var units = SecondsIndex

                if ((number % 60) == 0) {
                    units = MinutesIndex
                    number /= 60 // to minutes
                }

                if ((number % 60) == 0) {
                    units = HoursIndex
                    number /= 60 // to hours
                }

                if ((number % 24) == 0) {
                    units = DaysIndex
                    number /= 24 // to days
                }

                setSelection(units)
                numberPicker.value = number.toInt()

            }
            else {
                var number = value / 60 // convert to minutes
                var units = MinutesIndex

                if ((number % 60) == 0) {
                    units = HoursIndex
                    number /= 60 // to hours
                }

                if ((number % 24) == 0) {
                    units = DaysIndex
                    number /= 24 // to days
                }

                setSelection(units)
                numberPicker.value = number.toInt()
            }
        }

    var intervalMilliseconds: Long
        get() = intervalSeconds * 1000L
        set(value) {
            intervalSeconds = (value / 1000L).toInt()
        }

    var intervalMinutes: Int
        get() = (intervalSeconds / Consts.MINUTE_IN_SECONDS).toInt()
        set(value) {
            intervalSeconds = (value * Consts.MINUTE_IN_SECONDS).toInt()
        }
}