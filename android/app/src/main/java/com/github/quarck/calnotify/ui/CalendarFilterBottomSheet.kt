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
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
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
 * Supports search and configurable max items limit.
 * 
 * Performance: Only creates views for displayed calendars, not all calendars.
 */
class CalendarFilterBottomSheet : BottomSheetDialogFragment() {
    
    /** Callback when calendars are selected (null = all calendars, empty = none) */
    var onCalendarsSelected: ((Set<Long>?) -> Unit)? = null
    
    /** Currently selected calendar IDs - restored from arguments */
    private val selectedCalendarIds: MutableSet<Long> = mutableSetOf()
    
    /** All handled calendars (full list - data only, no views) */
    private var allHandledCalendars: List<CalendarRecord> = emptyList()
    
    /** Max items setting */
    private var maxItems: Int = 20
    
    /** Current search query for rebuilding list */
    private var currentSearchQuery: String = ""
    
    /** UI references */
    private var calendarContainer: LinearLayout? = null
    private var allCalendarsCheckbox: CheckBox? = null
    private var showingCountText: TextView? = null
    
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
        maxItems = settings.calendarFilterMaxItems
        
        calendarContainer = view.findViewById(R.id.calendar_list_container)
        allCalendarsCheckbox = view.findViewById(R.id.checkbox_all_calendars)
        showingCountText = view.findViewById(R.id.showing_count)
        val applyButton = view.findViewById<Button>(R.id.btn_apply)
        val searchEditText = view.findViewById<EditText>(R.id.search_calendars)
        
        // Get handled calendars (data only - no views created yet)
        val allCalendars = CalendarProvider.getCalendars(ctx)
        allHandledCalendars = allCalendars.filter { settings.getCalendarIsHandled(it.calendarId) }
        
        // If selectedCalendarIds is empty (from FilterState meaning "all"), populate with all IDs
        // This way empty set always means "none selected" internally
        if (selectedCalendarIds.isEmpty()) {
            selectedCalendarIds.addAll(allHandledCalendars.map { it.calendarId })
        }
        
        // Initial display with limit applied - only creates views for displayed items
        rebuildCalendarList("")
        
        // Set up search
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                rebuildCalendarList(s?.toString() ?: "")
            }
        })
        
        // Set up "All Calendars" checkbox initial state
        updateAllCalendarsCheckboxState()
        
        allCalendarsCheckbox?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Select all handled calendars
                selectedCalendarIds.clear()
                selectedCalendarIds.addAll(allHandledCalendars.map { it.calendarId })
            } else {
                // Deselect all - makes it easy to start fresh and pick specific ones
                selectedCalendarIds.clear()
            }
            // Rebuild to update individual checkboxes (preserve search)
            rebuildCalendarList(currentSearchQuery)
        }
        
        applyButton.setOnClickListener {
            // If all calendars are selected, return null (means "no filter")
            // Otherwise return exactly what's selected (empty = none)
            val result: Set<Long>? = if (selectedCalendarIds.containsAll(allHandledCalendars.map { it.calendarId })) {
                null  // All selected = no filter
            } else {
                selectedCalendarIds.toSet()  // Could be empty = show nothing
            }
            onCalendarsSelected?.invoke(result)
            dismiss()
        }
    }
    
    /**
     * Rebuild the calendar list based on search query.
     * Only creates views for calendars that will actually be displayed.
     */
    private fun rebuildCalendarList(query: String) {
        currentSearchQuery = query
        val container = calendarContainer ?: return
        container.removeAllViews()
        
        val lowerQuery = query.lowercase()
        
        // Filter by search query (searches name and ID)
        val matchingCalendars = if (query.isEmpty()) {
            allHandledCalendars
        } else {
            allHandledCalendars.filter { calendar ->
                val name = calendar.displayName.ifEmpty { calendar.name }
                name.lowercase().contains(lowerQuery) || 
                    calendar.calendarId.toString().contains(lowerQuery)
            }
        }
        
        // Apply limit - when searching, show more results (up to 50) to help find calendars
        // If maxItems is 0 (no limit), keep it as 0 (no limit)
        val effectiveMax = when {
            maxItems == 0 -> 0  // No limit setting - respect it
            query.isNotEmpty() -> maxOf(maxItems, 50)  // When searching, show at least 50
            else -> maxItems
        }
        val calendarsToDisplay = if (effectiveMax > 0 && matchingCalendars.size > effectiveMax) {
            matchingCalendars.take(effectiveMax)
        } else {
            matchingCalendars
        }
        
        // Create views ONLY for displayed calendars
        calendarsToDisplay.forEach { calendar ->
            val itemView = layoutInflater.inflate(R.layout.item_calendar_filter, container, false)
            val colorView = itemView.findViewById<View>(R.id.calendar_color)
            val checkbox = itemView.findViewById<CheckBox>(R.id.checkbox_calendar)
            
            colorView.background = ColorDrawable(calendar.color)
            val calendarName = calendar.displayName.ifEmpty { calendar.name }
            checkbox.text = getString(R.string.filter_calendar_name_with_id, calendarName, calendar.calendarId)
            checkbox.isChecked = calendar.calendarId in selectedCalendarIds
            
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedCalendarIds.add(calendar.calendarId)
                } else {
                    selectedCalendarIds.remove(calendar.calendarId)
                }
                updateAllCalendarsCheckboxState()
            }
            
            container.addView(itemView)
        }
        
        // Update count text
        val totalMatching = matchingCalendars.size
        val displayed = calendarsToDisplay.size
        if (displayed < totalMatching) {
            showingCountText?.text = getString(R.string.calendar_filter_showing_count, displayed, totalMatching)
            showingCountText?.visibility = View.VISIBLE
        } else {
            showingCountText?.visibility = View.GONE
        }
        
        updateAllCalendarsCheckboxState()
    }
    
    private fun updateAllCalendarsCheckboxState() {
        // All selected when the set contains all handled calendars
        val allSelected = selectedCalendarIds.containsAll(allHandledCalendars.map { it.calendarId })
        // Temporarily remove listener to avoid recursion, then restore
        allCalendarsCheckbox?.setOnCheckedChangeListener(null)
        allCalendarsCheckbox?.isChecked = allSelected
        // Re-attach the same listener behavior as in onViewCreated
        allCalendarsCheckbox?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedCalendarIds.clear()
                selectedCalendarIds.addAll(allHandledCalendars.map { it.calendarId })
            } else {
                selectedCalendarIds.clear()
            }
            rebuildCalendarList(currentSearchQuery)
        }
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
