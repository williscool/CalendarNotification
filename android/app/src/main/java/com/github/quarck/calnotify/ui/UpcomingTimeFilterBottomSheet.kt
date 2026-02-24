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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.prefs.PreferenceUtils
import com.github.quarck.calnotify.upcoming.UpcomingEventsLookahead
import com.github.quarck.calnotify.utils.DateTimeUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet for selecting the upcoming events lookahead window.
 * Unlike the regular TimeFilterBottomSheet (in-memory filter), this persists to Settings
 * because it controls which events get fetched from MonitorStorage.
 *
 * Options:
 * - Day boundary mode (with configured boundary hour)
 * - Configurable fixed interval presets (e.g., 4h, 8h, 1d, 3d, 1w)
 */
class UpcomingTimeFilterBottomSheet : BottomSheetDialogFragment() {
    
    private val DAY_BOUNDARY_RADIO_ID = View.generateViewId()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_upcoming_time_filter, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val settings = Settings(requireContext())
        val radioGroup = view.findViewById<RadioGroup>(R.id.upcoming_time_radio_group).apply {
            isSaveEnabled = false
        }
        val applyButton = view.findViewById<Button>(R.id.btn_apply)
        
        val currentMode = settings.upcomingEventsMode
        val currentMillis = settings.upcomingEventsFixedLookaheadMillis
        val presets = settings.upcomingTimePresets
        
        // Day boundary option
        val dayBoundaryRadio = RadioButton(requireContext()).apply {
            id = DAY_BOUNDARY_RADIO_ID
            val hourStr = DateTimeUtils.formatHourOfDay(settings.upcomingEventsDayBoundaryHour)
            text = getString(R.string.upcoming_time_day_boundary, hourStr)
            setPadding(0, 24, 0, 24)
        }
        radioGroup.addView(dayBoundaryRadio)
        
        // Divider — use ?android:attr/listDivider for dark mode compatibility
        val dividerAttrs = intArrayOf(android.R.attr.listDivider)
        val ta = requireContext().obtainStyledAttributes(dividerAttrs)
        val dividerDrawable = ta.getDrawable(0)
        ta.recycle()
        val divider = View(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 2
            )
            background = dividerDrawable
        }
        radioGroup.addView(divider)
        
        // Fixed interval preset options
        val presetRadioIds = mutableListOf<Pair<Int, Long>>()
        for (presetMillis in presets) {
            val radioId = View.generateViewId()
            presetRadioIds.add(radioId to presetMillis)
            val radio = RadioButton(requireContext()).apply {
                id = radioId
                text = PreferenceUtils.formatPresetHumanReadable(presetMillis)
                setPadding(0, 24, 0, 24)
            }
            radioGroup.addView(radio)
        }
        
        // Pre-select current setting
        if (currentMode == UpcomingEventsLookahead.MODE_DAY_BOUNDARY) {
            radioGroup.check(DAY_BOUNDARY_RADIO_ID)
        } else {
            val matchingPreset = presetRadioIds.firstOrNull { it.second == currentMillis }
            if (matchingPreset != null) {
                radioGroup.check(matchingPreset.first)
            }
        }
        
        applyButton.setOnClickListener {
            val checkedId = radioGroup.checkedRadioButtonId
            if (checkedId == DAY_BOUNDARY_RADIO_ID) {
                setFragmentResult(REQUEST_KEY, bundleOf(
                    RESULT_MODE to UpcomingEventsLookahead.MODE_DAY_BOUNDARY
                ))
            } else {
                val selectedMillis = presetRadioIds.firstOrNull { it.first == checkedId }?.second
                    ?: currentMillis
                setFragmentResult(REQUEST_KEY, bundleOf(
                    RESULT_MODE to UpcomingEventsLookahead.MODE_FIXED,
                    RESULT_MILLIS to selectedMillis
                ))
            }
            dismiss()
        }
    }
    
    companion object {
        const val REQUEST_KEY = "upcoming_time_filter_request"
        const val RESULT_MODE = "result_mode"
        const val RESULT_MILLIS = "result_millis"
        
        fun newInstance(): UpcomingTimeFilterBottomSheet {
            return UpcomingTimeFilterBottomSheet()
        }
    }
}
