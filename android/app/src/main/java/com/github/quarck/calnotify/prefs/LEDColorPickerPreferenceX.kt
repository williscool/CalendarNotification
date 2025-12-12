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
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewParent
import android.widget.Button
import android.widget.LinearLayout
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.utils.findOrThrow

class LEDColorPickerPreferenceX @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    defStyleRes: Int = 0
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes) {

    var colorValue: Int = 0
        private set

    init {
        dialogLayoutResource = R.layout.dialog_color_picker
        widgetLayoutResource = R.layout.dialog_color_picker_widget
        positiveButtonText = context.getString(android.R.string.ok)
        negativeButtonText = context.getString(android.R.string.cancel)
    }

    override fun onBindViewHolder(holder: androidx.preference.PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val widget = holder.findViewById(R.id.dialog_color_picker_widget)
        widget?.background = ColorDrawable(colorValue)
    }

    fun persistColor(value: Int) {
        colorValue = value
        persistInt(value)
        notifyChanged()
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        colorValue = getPersistedInt((defaultValue as? Int) ?: 0)
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any {
        return a.getInteger(index, 0)
    }

    class Dialog : PreferenceDialogFragmentCompat() {
        private var colorValue = 0
        private val originalColors = mutableListOf<Pair<LinearLayout, ColorDrawable>>()
        private var primaryColor: ColorDrawable? = null

        private val colorButtonIds = intArrayOf(
            R.id.button_color_picker_clr1,
            R.id.button_color_picker_clr2,
            R.id.button_color_picker_clr3,
            R.id.button_color_picker_clr4,
            R.id.button_color_picker_clr5,
            R.id.button_color_picker_clr6,
            R.id.button_color_picker_clr7,
            R.id.button_color_picker_clr8
        )

        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            val pref = preference as LEDColorPickerPreferenceX
            colorValue = pref.colorValue
            originalColors.clear()

            for (buttonId in colorButtonIds) {
                val button = view.findOrThrow<Button>(buttonId)
                button.setOnClickListener { v -> onColorClick(v) }

                val parent: ViewParent = button.parent
                if (parent is LinearLayout) {
                    val background = parent.background
                    if (background is ColorDrawable) {
                        originalColors.add(Pair(parent, background))
                        if (background.color == colorValue) {
                            parent.background = getPrimaryColor(view)
                        }
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        private fun getPrimaryColor(v: View): ColorDrawable {
            if (primaryColor == null) {
                primaryColor = ColorDrawable(v.resources.getColor(R.color.primary))
            }
            return primaryColor!!
        }

        private fun onColorClick(v: View) {
            colorValue = Consts.DEFAULT_LED_COLOR

            val background = v.background
            if (background is ColorDrawable) {
                colorValue = background.color
            }

            for (hl in originalColors) {
                hl.first.background = hl.second
            }

            val parent: ViewParent = v.parent
            if (parent is LinearLayout) {
                parent.background = getPrimaryColor(v)
            }
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                val pref = preference as LEDColorPickerPreferenceX
                if (pref.callChangeListener(colorValue)) {
                    pref.persistColor(colorValue)
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

