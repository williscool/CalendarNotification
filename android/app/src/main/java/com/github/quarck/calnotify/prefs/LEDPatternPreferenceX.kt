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
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.utils.findOrThrow

class LEDPatternPreferenceX @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    defStyleRes: Int = 0
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes) {

    var patternValue: String = Consts.DEFAULT_LED_PATTERN
        private set

    init {
        dialogLayoutResource = R.layout.dialog_led_pattern
        positiveButtonText = context.getString(android.R.string.ok)
        negativeButtonText = context.getString(android.R.string.cancel)
    }

    fun persistPattern(value: String) {
        patternValue = value
        persistString(value)
        notifyChanged()
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        patternValue = getPersistedString((defaultValue as? String) ?: Consts.DEFAULT_LED_PATTERN)
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    class Dialog : PreferenceDialogFragmentCompat(), SeekBar.OnSeekBarChangeListener {
        private var ledOnTime = 0
        private var ledOffTime = 0

        private lateinit var onTimeSeeker: SeekBar
        private lateinit var offTimeSeeker: SeekBar
        private lateinit var onTimeText: TextView
        private lateinit var offTimeText: TextView
        private lateinit var millisecondsString: String

        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            onTimeSeeker = view.findOrThrow(R.id.seek_bar_led_on_time)
            offTimeSeeker = view.findOrThrow(R.id.seek_bar_led_off_time)
            onTimeText = view.findOrThrow(R.id.text_view_led_on_time_value)
            offTimeText = view.findOrThrow(R.id.text_view_led_off_time_value)

            millisecondsString = view.resources.getString(R.string.milliseconds_suffix)

            val pref = preference as LEDPatternPreferenceX
            parseSetting(pref.patternValue)

            updateTexts()

            onTimeSeeker.progress = onTimeSeeker.max *
                (ledOnTime - Consts.LED_MIN_DURATION) / (Consts.LED_MAX_DURATION - Consts.LED_MIN_DURATION)
            offTimeSeeker.progress = offTimeSeeker.max *
                (ledOffTime - Consts.LED_MIN_DURATION) / (Consts.LED_MAX_DURATION - Consts.LED_MIN_DURATION)

            onTimeSeeker.setOnSeekBarChangeListener(this)
            offTimeSeeker.setOnSeekBarChangeListener(this)
        }

        private fun parseSetting(settingValue: String) {
            val (on, off) = settingValue.split(",")
            ledOnTime = on.toInt()
            ledOffTime = off.toInt()
        }

        private fun updateTexts() {
            onTimeText.text = "$ledOnTime $millisecondsString"
            offTimeText.text = "$ledOffTime $millisecondsString"
        }

        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            when (seekBar) {
                onTimeSeeker ->
                    ledOnTime = (Consts.LED_MAX_DURATION - Consts.LED_MIN_DURATION) *
                        progress / onTimeSeeker.max + Consts.LED_MIN_DURATION
                offTimeSeeker ->
                    ledOffTime = (Consts.LED_MAX_DURATION - Consts.LED_MIN_DURATION) *
                        progress / offTimeSeeker.max + Consts.LED_MIN_DURATION
            }

            ledOnTime = Math.round(ledOnTime.toFloat() / Consts.LED_DURATION_GRANULARITY).toInt() *
                Consts.LED_DURATION_GRANULARITY
            ledOffTime = Math.round(ledOffTime.toFloat() / Consts.LED_DURATION_GRANULARITY).toInt() *
                Consts.LED_DURATION_GRANULARITY

            updateTexts()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}

        private fun capDuration(duration: Int) = when {
            duration < Consts.LED_MIN_DURATION -> Consts.LED_MIN_DURATION
            duration > Consts.LED_MAX_DURATION -> Consts.LED_MAX_DURATION
            else -> duration
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                val pref = preference as LEDPatternPreferenceX
                val newValue = "${capDuration(ledOnTime)},${capDuration(ledOffTime)}"
                if (pref.callChangeListener(newValue)) {
                    pref.persistPattern(newValue)
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

