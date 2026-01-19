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
 */
class CalendarFilterBottomSheet : BottomSheetDialogFragment() {
    
    /** Callback when calendars are selected */
    var onCalendarsSelected: ((Set<Long>) -> Unit)? = null
    
    /** Currently selected calendar IDs - restored from arguments */
    private val selectedCalendarIds: MutableSet<Long> = mutableSetOf()
    
    /** All handled calendars (full list) */
    private var allHandledCalendars: List<CalendarRecord> = emptyList()
    
    /** Currently displayed calendar items (after search/limit) */
    private data class CalendarItem(
        val calendar: CalendarRecord,
        val itemView: View,
        val checkbox: CheckBox
    )
    private val calendarItems = mutableListOf<CalendarItem>()
    
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
        val maxItems = settings.calendarFilterMaxItems
        
        val calendarContainer = view.findViewById<LinearLayout>(R.id.calendar_list_container)
        val allCalendarsCheckbox = view.findViewById<CheckBox>(R.id.checkbox_all_calendars)
        val applyButton = view.findViewById<Button>(R.id.btn_apply)
        val searchEditText = view.findViewById<EditText>(R.id.search_calendars)
        val showingCountText = view.findViewById<TextView>(R.id.showing_count)
        
        // Get handled calendars
        val allCalendars = CalendarProvider.getCalendars(ctx)
        allHandledCalendars = allCalendars.filter { settings.getCalendarIsHandled(it.calendarId) }
        
        // Create views for all calendars (hidden initially, shown based on search/limit)
        allHandledCalendars.forEach { calendar ->
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
                updateAllCalendarsCheckbox(allCalendarsCheckbox)
            }
            
            calendarItems.add(CalendarItem(calendar, itemView, checkbox))
            calendarContainer.addView(itemView)
        }
        
        // Initial display with limit applied
        updateDisplayedCalendars("", maxItems, showingCountText)
        
        // Set up search
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                // When searching, show all matches (no limit) so user can find what they need
                updateDisplayedCalendars(query, if (query.isNotEmpty()) 0 else maxItems, showingCountText)
            }
        })
        
        // Set up "All Calendars" checkbox
        allCalendarsCheckbox.isChecked = selectedCalendarIds.isEmpty() || 
            selectedCalendarIds.size == allHandledCalendars.size
        
        allCalendarsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Select all calendars (which means empty set = no filter)
                selectedCalendarIds.clear()
                calendarItems.forEach { it.checkbox.isChecked = true }
            }
            // Don't deselect all when unchecking - user should manually deselect
        }
        
        applyButton.setOnClickListener {
            // If all calendars are selected, return empty set (means "all")
            val result = if (selectedCalendarIds.size == allHandledCalendars.size) {
                emptySet()
            } else {
                selectedCalendarIds.toSet()
            }
            onCalendarsSelected?.invoke(result)
            dismiss()
        }
    }
    
    /**
     * Update which calendars are displayed based on search query and max limit.
     * @param query Search query (empty = show all)
     * @param maxItems Max items to show (0 = no limit)
     */
    private fun updateDisplayedCalendars(query: String, maxItems: Int, showingCountText: TextView) {
        val lowerQuery = query.lowercase()
        
        // Filter by search query
        val matchingItems = if (query.isEmpty()) {
            calendarItems
        } else {
            calendarItems.filter { item ->
                val name = item.calendar.displayName.ifEmpty { item.calendar.name }
                name.lowercase().contains(lowerQuery)
            }
        }
        
        // Apply limit
        val displayedItems = if (maxItems > 0 && matchingItems.size > maxItems) {
            matchingItems.take(maxItems)
        } else {
            matchingItems
        }
        
        // Update visibility
        calendarItems.forEach { item ->
            item.itemView.visibility = if (item in displayedItems) View.VISIBLE else View.GONE
        }
        
        // Update count text
        val totalMatching = matchingItems.size
        val displayed = displayedItems.size
        if (displayed < totalMatching) {
            showingCountText.text = getString(R.string.calendar_filter_showing_count, displayed, totalMatching)
            showingCountText.visibility = View.VISIBLE
        } else {
            showingCountText.visibility = View.GONE
        }
    }
    
    private fun updateAllCalendarsCheckbox(allCheckbox: CheckBox) {
        val checkedCount = calendarItems.count { it.checkbox.isChecked }
        allCheckbox.isChecked = checkedCount == allHandledCalendars.size
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
