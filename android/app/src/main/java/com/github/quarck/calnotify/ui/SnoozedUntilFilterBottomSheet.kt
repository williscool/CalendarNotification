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

package com.github.quarck.calnotify.ui

import android.content.DialogInterface
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.DatePicker
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TimePicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.prefs.PreferenceUtils
import com.github.quarck.calnotify.utils.findOrThrow
import com.github.quarck.calnotify.utils.hourCompat
import com.github.quarck.calnotify.utils.minuteCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Calendar

/**
 * Bottom sheet for filtering active events by snoozed-until time.
 * Features configurable interval presets, before/after toggle,
 * and custom period / specific date-time pickers.
 */
class SnoozedUntilFilterBottomSheet : BottomSheetDialogFragment() {
    
    private val CUSTOM_PERIOD_RADIO_ID = View.generateViewId()
    private val SPECIFIC_TIME_RADIO_ID = View.generateViewId()
    
    private var customPeriodMillis: Long = 0L
    private var specificTimeMillis: Long = 0L
    
    private val currentConfig: SnoozedUntilFilterConfig
        get() {
            val args = arguments ?: return SnoozedUntilFilterConfig()
            return SnoozedUntilFilterConfig(
                mode = SnoozedUntilFilterMode.entries.getOrNull(
                    args.getInt(ARG_MODE, 0)
                ) ?: SnoozedUntilFilterMode.ALL,
                direction = FilterDirection.entries.getOrNull(
                    args.getInt(ARG_DIRECTION, 0)
                ) ?: FilterDirection.BEFORE,
                valueMillis = args.getLong(ARG_VALUE, 0L),
                includeUnsnoozed = args.getBoolean(ARG_INCLUDE_UNSNOOZED, false)
            )
        }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_snoozed_until_filter, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val settings = Settings(requireContext())
        val directionGroup = view.findViewById<RadioGroup>(R.id.direction_toggle)
        val radioGroup = view.findViewById<RadioGroup>(R.id.snoozed_until_radio_group).apply {
            isSaveEnabled = false
        }
        val applyButton = view.findViewById<Button>(R.id.btn_apply)
        val includeUnsnoozedCheckbox = view.findViewById<CheckBox>(R.id.checkbox_include_unsnoozed)
        
        val config = currentConfig
        val presets = settings.snoozedUntilPresets
        val radioPaddingV = resources.getDimensionPixelSize(R.dimen.bottom_sheet_radio_padding_vertical)
        val dividerHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_divider_height)
        
        // Restore custom values if editing existing filter
        if (config.mode == SnoozedUntilFilterMode.CUSTOM_PERIOD) {
            customPeriodMillis = config.valueMillis
        } else if (config.mode == SnoozedUntilFilterMode.SPECIFIC_TIME) {
            specificTimeMillis = config.valueMillis
        }
        
        // 3-way direction toggle: Off / Before / After
        when {
            config.mode == SnoozedUntilFilterMode.ALL -> directionGroup.check(R.id.radio_off)
            config.direction == FilterDirection.BEFORE -> directionGroup.check(R.id.radio_before)
            config.direction == FilterDirection.AFTER -> directionGroup.check(R.id.radio_after)
        }
        
        includeUnsnoozedCheckbox.isChecked = config.includeUnsnoozed
        val isFilterActive = config.mode != SnoozedUntilFilterMode.ALL
        includeUnsnoozedCheckbox.isEnabled = isFilterActive
        includeUnsnoozedCheckbox.alpha = if (isFilterActive) 1.0f else 0.4f
        
        // Preset options
        val presetRadioIds = mutableListOf<Pair<Int, Long>>()
        for (presetMillis in presets) {
            val radioId = View.generateViewId()
            presetRadioIds.add(radioId to presetMillis)
            val radio = RadioButton(requireContext()).apply {
                id = radioId
                text = PreferenceUtils.formatPresetHumanReadable(requireContext(), presetMillis)
                setPadding(0, radioPaddingV, 0, radioPaddingV)
            }
            radioGroup.addView(radio)
        }
        
        // Divider
        radioGroup.addView(createDivider(dividerHeight))
        
        // "For a custom period..." option
        val customPeriodRadio = RadioButton(requireContext()).apply {
            id = CUSTOM_PERIOD_RADIO_ID
            text = if (customPeriodMillis > 0) {
                PreferenceUtils.formatPresetHumanReadable(requireContext(), customPeriodMillis)
            } else {
                getString(R.string.filter_snoozed_until_custom_period)
            }
            setPadding(0, radioPaddingV, 0, radioPaddingV)
        }
        radioGroup.addView(customPeriodRadio)
        
        // "Until a specific time and date..." option
        val specificTimeRadio = RadioButton(requireContext()).apply {
            id = SPECIFIC_TIME_RADIO_ID
            text = if (specificTimeMillis > 0) {
                DateUtils.formatDateTime(
                    requireContext(), specificTimeMillis,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL
                )
            } else {
                getString(R.string.filter_snoozed_until_specific_time)
            }
            setPadding(0, radioPaddingV, 0, radioPaddingV)
        }
        radioGroup.addView(specificTimeRadio)
        
        // Pre-select current value in preset list (only if filter is active)
        if (config.mode != SnoozedUntilFilterMode.ALL) {
            when (config.mode) {
                SnoozedUntilFilterMode.PRESET -> {
                    val match = presetRadioIds.firstOrNull { it.second == config.valueMillis }
                    if (match != null) radioGroup.check(match.first)
                }
                SnoozedUntilFilterMode.CUSTOM_PERIOD -> radioGroup.check(CUSTOM_PERIOD_RADIO_ID)
                SnoozedUntilFilterMode.SPECIFIC_TIME -> radioGroup.check(SPECIFIC_TIME_RADIO_ID)
                else -> {}
            }
        }
        
        fun setControlsEnabled(enabled: Boolean) {
            for (i in 0 until radioGroup.childCount) {
                val child = radioGroup.getChildAt(i)
                child.isEnabled = enabled
                child.alpha = if (enabled) 1.0f else 0.4f
            }
            includeUnsnoozedCheckbox.isEnabled = enabled
            includeUnsnoozedCheckbox.alpha = if (enabled) 1.0f else 0.4f
        }
        setControlsEnabled(config.mode != SnoozedUntilFilterMode.ALL)
        
        directionGroup.setOnCheckedChangeListener { _, checkedId ->
            val isOff = checkedId == R.id.radio_off
            setControlsEnabled(!isOff)
            if (isOff) {
                radioGroup.clearCheck()
            }
        }
        
        // Open picker dialogs when custom options are tapped
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                CUSTOM_PERIOD_RADIO_ID -> showCustomPeriodDialog(customPeriodRadio)
                SPECIFIC_TIME_RADIO_ID -> showDatePickerDialog(specificTimeRadio)
            }
        }
        
        applyButton.setOnClickListener {
            val directionId = directionGroup.checkedRadioButtonId
            
            val includeUnsnoozed = includeUnsnoozedCheckbox.isChecked
            
            // Off = clear filter
            if (directionId == R.id.radio_off) {
                setFragmentResult(REQUEST_KEY, bundleOf(
                    RESULT_MODE to SnoozedUntilFilterMode.ALL.ordinal,
                    RESULT_DIRECTION to FilterDirection.BEFORE.ordinal,
                    RESULT_VALUE to 0L,
                    RESULT_INCLUDE_UNSNOOZED to false
                ))
                dismiss()
                return@setOnClickListener
            }
            
            val direction = when (directionId) {
                R.id.radio_after -> FilterDirection.AFTER
                else -> FilterDirection.BEFORE
            }
            
            val checkedId = radioGroup.checkedRadioButtonId
            when {
                checkedId == CUSTOM_PERIOD_RADIO_ID && customPeriodMillis > 0 -> {
                    setFragmentResult(REQUEST_KEY, bundleOf(
                        RESULT_MODE to SnoozedUntilFilterMode.CUSTOM_PERIOD.ordinal,
                        RESULT_DIRECTION to direction.ordinal,
                        RESULT_VALUE to customPeriodMillis,
                        RESULT_INCLUDE_UNSNOOZED to includeUnsnoozed
                    ))
                }
                checkedId == SPECIFIC_TIME_RADIO_ID && specificTimeMillis > 0 -> {
                    setFragmentResult(REQUEST_KEY, bundleOf(
                        RESULT_MODE to SnoozedUntilFilterMode.SPECIFIC_TIME.ordinal,
                        RESULT_DIRECTION to direction.ordinal,
                        RESULT_VALUE to specificTimeMillis,
                        RESULT_INCLUDE_UNSNOOZED to includeUnsnoozed
                    ))
                }
                else -> {
                    val selectedPreset = presetRadioIds.firstOrNull { it.first == checkedId }
                    if (selectedPreset != null) {
                        setFragmentResult(REQUEST_KEY, bundleOf(
                            RESULT_MODE to SnoozedUntilFilterMode.PRESET.ordinal,
                            RESULT_DIRECTION to direction.ordinal,
                            RESULT_VALUE to selectedPreset.second,
                            RESULT_INCLUDE_UNSNOOZED to includeUnsnoozed
                        ))
                    } else {
                        setFragmentResult(REQUEST_KEY, bundleOf(
                            RESULT_MODE to SnoozedUntilFilterMode.ALL.ordinal,
                            RESULT_DIRECTION to FilterDirection.BEFORE.ordinal,
                            RESULT_VALUE to 0L,
                            RESULT_INCLUDE_UNSNOOZED to false
                        ))
                    }
                }
            }
            dismiss()
        }
    }
    
    private fun createDivider(height: Int): View {
        val dividerAttrs = intArrayOf(android.R.attr.listDivider)
        val ta = requireContext().obtainStyledAttributes(dividerAttrs)
        val dividerDrawable = ta.getDrawable(0)
        ta.recycle()
        return View(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height
            )
            background = dividerDrawable
        }
    }
    
    private fun showCustomPeriodDialog(radioButton: RadioButton) {
        val ctx = context ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_interval_picker, null)
        val picker = TimeIntervalPickerController(dialogView, R.string.snooze_for, 0, false)
        if (customPeriodMillis > 0) {
            picker.intervalMilliseconds = customPeriodMillis
        }
        
        AlertDialog.Builder(ctx)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                customPeriodMillis = picker.intervalMilliseconds
                radioButton.text = PreferenceUtils.formatPresetHumanReadable(ctx, customPeriodMillis)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
    }
    
    private fun showDatePickerDialog(radioButton: RadioButton) {
        val ctx = context ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_date_picker, null) ?: return
        val datePicker = dialogView.findOrThrow<DatePicker>(R.id.datePickerCustomSnooze)
        
        val firstDayOfWeek = Settings(ctx).firstDayOfWeek
        if (firstDayOfWeek != -1) {
            datePicker.firstDayOfWeek = firstDayOfWeek
        }
        
        if (specificTimeMillis > 0) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = specificTimeMillis
            datePicker.updateDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        }
        
        AlertDialog.Builder(ctx)
            .setView(dialogView)
            .setPositiveButton(R.string.next) { _: DialogInterface?, _: Int ->
                datePicker.clearFocus()
                val date = Calendar.getInstance()
                date.set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 0, 0, 0)
                showTimePickerDialog(radioButton, date.timeInMillis)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
    }
    
    private fun showTimePickerDialog(radioButton: RadioButton, dateMillis: Long) {
        val ctx = context ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_time_picker, null) ?: return
        val timePicker = dialogView.findOrThrow<TimePicker>(R.id.timePickerCustomSnooze)
        timePicker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(ctx))
        
        if (specificTimeMillis > 0) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = specificTimeMillis
            timePicker.hourCompat = cal.get(Calendar.HOUR_OF_DAY)
            timePicker.minuteCompat = cal.get(Calendar.MINUTE)
        }
        
        val title = dialogView.findOrThrow<TextView>(R.id.textViewSnoozeUntilDate)
        title.text = String.format(
            resources.getString(R.string.choose_time),
            DateUtils.formatDateTime(ctx, dateMillis, DateUtils.FORMAT_SHOW_DATE)
        )
        
        AlertDialog.Builder(ctx)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                timePicker.clearFocus()
                val date = Calendar.getInstance()
                date.timeInMillis = dateMillis
                date.set(Calendar.HOUR_OF_DAY, timePicker.hourCompat)
                date.set(Calendar.MINUTE, timePicker.minuteCompat)
                specificTimeMillis = date.timeInMillis
                radioButton.text = DateUtils.formatDateTime(
                    ctx, specificTimeMillis,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
    }
    
    companion object {
        private const val ARG_MODE = "arg_mode"
        private const val ARG_DIRECTION = "arg_direction"
        private const val ARG_VALUE = "arg_value"
        private const val ARG_INCLUDE_UNSNOOZED = "arg_include_unsnoozed"
        
        const val REQUEST_KEY = "snoozed_until_filter_request"
        const val RESULT_MODE = "result_mode"
        const val RESULT_DIRECTION = "result_direction"
        const val RESULT_VALUE = "result_value"
        const val RESULT_INCLUDE_UNSNOOZED = "result_include_unsnoozed"
        
        fun newInstance(config: SnoozedUntilFilterConfig): SnoozedUntilFilterBottomSheet {
            return SnoozedUntilFilterBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_MODE, config.mode.ordinal)
                    putInt(ARG_DIRECTION, config.direction.ordinal)
                    putLong(ARG_VALUE, config.valueMillis)
                    putBoolean(ARG_INCLUDE_UNSNOOZED, config.includeUnsnoozed)
                }
            }
        }
    }
}
