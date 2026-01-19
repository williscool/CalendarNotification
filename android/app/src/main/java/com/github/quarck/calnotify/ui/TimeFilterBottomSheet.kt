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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet for selecting time filter options.
 * Options differ based on which tab is active (Active vs Dismissed).
 */
class TimeFilterBottomSheet : BottomSheetDialogFragment() {
    
    /** Current selected filter - restored from arguments */
    private val currentFilter: TimeFilter
        get() = TimeFilter.entries.getOrNull(
            arguments?.getInt(ARG_CURRENT_FILTER, 0) ?: 0
        ) ?: TimeFilter.ALL
    
    /** Which tab this is shown for (affects available options) - restored from arguments */
    private val tabType: TabType
        get() = TabType.entries.getOrNull(
            arguments?.getInt(ARG_TAB_TYPE, 0) ?: 0
        ) ?: TabType.ACTIVE
    
    enum class TabType { ACTIVE, DISMISSED }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_time_filter, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val radioGroup = view.findViewById<RadioGroup>(R.id.time_filter_radio_group)
        val applyButton = view.findViewById<Button>(R.id.btn_apply)
        
        // Set up radio buttons based on tab type
        setupRadioButtons(view)
        
        // Set current selection
        val selectedId = when (currentFilter) {
            TimeFilter.ALL -> R.id.radio_all
            TimeFilter.STARTED_TODAY -> R.id.radio_started_today
            TimeFilter.STARTED_THIS_WEEK -> R.id.radio_started_this_week
            TimeFilter.PAST -> R.id.radio_past
            TimeFilter.STARTED_THIS_MONTH -> R.id.radio_started_this_month
        }
        radioGroup.check(selectedId)
        
        applyButton.setOnClickListener {
            val selectedFilter = when (radioGroup.checkedRadioButtonId) {
                R.id.radio_all -> TimeFilter.ALL
                R.id.radio_started_today -> TimeFilter.STARTED_TODAY
                R.id.radio_started_this_week -> TimeFilter.STARTED_THIS_WEEK
                R.id.radio_past -> TimeFilter.PAST
                R.id.radio_started_this_month -> TimeFilter.STARTED_THIS_MONTH
                else -> TimeFilter.ALL
            }
            // Use Fragment Result API (survives config changes)
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_FILTER to selectedFilter.ordinal))
            dismiss()
        }
    }
    
    private fun setupRadioButtons(view: View) {
        val radioPast = view.findViewById<RadioButton>(R.id.radio_past)
        val radioThisMonth = view.findViewById<RadioButton>(R.id.radio_started_this_month)
        
        when (tabType) {
            TabType.ACTIVE -> {
                // Active tab: show Past, hide This Month
                radioPast.visibility = View.VISIBLE
                radioThisMonth.visibility = View.GONE
            }
            TabType.DISMISSED -> {
                // Dismissed tab: show This Month, hide Past
                radioPast.visibility = View.GONE
                radioThisMonth.visibility = View.VISIBLE
            }
        }
    }
    
    companion object {
        private const val ARG_CURRENT_FILTER = "current_filter"
        private const val ARG_TAB_TYPE = "tab_type"
        
        /** Fragment Result API key for listening to time filter selection */
        const val REQUEST_KEY = "time_filter_request"
        /** Bundle key for the result (Int - TimeFilter ordinal) */
        const val RESULT_FILTER = "result_filter"
        
        fun newInstance(currentFilter: TimeFilter, tabType: TabType): TimeFilterBottomSheet {
            return TimeFilterBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_CURRENT_FILTER, currentFilter.ordinal)
                    putInt(ARG_TAB_TYPE, tabType.ordinal)
                }
            }
        }
    }
}
