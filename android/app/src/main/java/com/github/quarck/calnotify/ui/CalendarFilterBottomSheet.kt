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

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet for selecting calendar filter options.
 * Shows only handled (enabled) calendars with their colors.
 */
class CalendarFilterBottomSheet : BottomSheetDialogFragment() {
    
    /** Callback when calendars are selected */
    var onCalendarsSelected: ((Set<Long>) -> Unit)? = null
    
    /** Currently selected calendar IDs - restored from arguments */
    private val selectedCalendarIds: MutableSet<Long> = mutableSetOf()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Restore selected calendars from arguments
        arguments?.getLongArray(ARG_SELECTED_CALENDARS)?.let {
            selectedCalendarIds.addAll(it.toSet())
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_calendar_filter, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val ctx = requireContext()
        val settings = Settings(ctx)
        val calendarContainer = view.findViewById<LinearLayout>(R.id.calendar_list_container)
        val allCalendarsCheckbox = view.findViewById<CheckBox>(R.id.checkbox_all_calendars)
        val applyButton = view.findViewById<Button>(R.id.btn_apply)
        
        // Get handled calendars
        val allCalendars = CalendarProvider.getCalendars(ctx)
        val handledCalendars = allCalendars.filter { settings.getCalendarIsHandled(it.calendarId) }
        
        // Track checkboxes for updating "All" state
        val calendarCheckboxes = mutableListOf<Pair<Long, CheckBox>>()
        
        // Create checkbox for each handled calendar
        handledCalendars.forEach { calendar ->
            val itemView = layoutInflater.inflate(R.layout.item_calendar_filter, calendarContainer, false)
            val colorView = itemView.findViewById<View>(R.id.calendar_color)
            val checkbox = itemView.findViewById<CheckBox>(R.id.checkbox_calendar)
            
            colorView.background = ColorDrawable(calendar.color)
            checkbox.text = calendar.displayName.ifEmpty { calendar.name }
            checkbox.isChecked = selectedCalendarIds.isEmpty() || calendar.calendarId in selectedCalendarIds
            
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedCalendarIds.add(calendar.calendarId)
                } else {
                    selectedCalendarIds.remove(calendar.calendarId)
                }
                updateAllCalendarsCheckbox(allCalendarsCheckbox, calendarCheckboxes, handledCalendars.size)
            }
            
            calendarCheckboxes.add(calendar.calendarId to checkbox)
            calendarContainer.addView(itemView)
        }
        
        // Set up "All Calendars" checkbox
        allCalendarsCheckbox.isChecked = selectedCalendarIds.isEmpty() || 
            selectedCalendarIds.size == handledCalendars.size
        
        allCalendarsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Select all calendars (which means empty set = no filter)
                selectedCalendarIds.clear()
                calendarCheckboxes.forEach { (_, cb) -> cb.isChecked = true }
            }
            // Don't deselect all when unchecking - user should manually deselect
        }
        
        applyButton.setOnClickListener {
            // If all calendars are selected, return empty set (means "all")
            val result = if (selectedCalendarIds.size == handledCalendars.size) {
                emptySet()
            } else {
                selectedCalendarIds.toSet()
            }
            onCalendarsSelected?.invoke(result)
            dismiss()
        }
    }
    
    private fun updateAllCalendarsCheckbox(
        allCheckbox: CheckBox,
        calendarCheckboxes: List<Pair<Long, CheckBox>>,
        totalCount: Int
    ) {
        val checkedCount = calendarCheckboxes.count { it.second.isChecked }
        allCheckbox.isChecked = checkedCount == totalCount
    }
    
    companion object {
        private const val ARG_SELECTED_CALENDARS = "selected_calendars"
        
        fun newInstance(selectedCalendarIds: Set<Long>): CalendarFilterBottomSheet {
            return CalendarFilterBottomSheet().apply {
                arguments = Bundle().apply {
                    putLongArray(ARG_SELECTED_CALENDARS, selectedCalendarIds.toLongArray())
                }
            }
        }
    }
}
