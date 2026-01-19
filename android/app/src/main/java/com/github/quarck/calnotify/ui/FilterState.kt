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

import android.content.Context
import android.os.Bundle
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventAlertRecord
import com.github.quarck.calnotify.utils.DateTimeUtils

/**
 * Which filters to apply when filtering events.
 */
enum class FilterType {
    CALENDAR, STATUS, TIME
}

/**
 * Filter state for event lists. In-memory only - clears on tab switch and app restart.
 */
data class FilterState(
    val selectedCalendarIds: Set<Long>? = null,  // null = no filter (all), empty = none, set = specific
    val statusFilters: Set<StatusOption> = emptySet(),  // empty = show all (no filter)
    val timeFilter: TimeFilter = TimeFilter.ALL
) {
    
    companion object {
        private const val BUNDLE_CALENDAR_IDS = "filter_calendar_ids"
        private const val BUNDLE_CALENDAR_NULL = "filter_calendar_null"
        private const val BUNDLE_STATUS_FILTERS = "filter_status"
        private const val BUNDLE_TIME_FILTER = "filter_time"
        
        /** Deserialize FilterState from a Bundle */
        fun fromBundle(bundle: Bundle?): FilterState {
            if (bundle == null) return FilterState()
            
            val calendarIds: Set<Long>? = if (bundle.getBoolean(BUNDLE_CALENDAR_NULL, false)) {
                null
            } else {
                bundle.getLongArray(BUNDLE_CALENDAR_IDS)?.toSet()
            }
            
            val statusFilters = bundle.getIntArray(BUNDLE_STATUS_FILTERS)
                ?.mapNotNull { StatusOption.entries.getOrNull(it) }
                ?.toSet() ?: emptySet()
            
            val timeFilter = TimeFilter.entries.getOrNull(
                bundle.getInt(BUNDLE_TIME_FILTER, 0)
            ) ?: TimeFilter.ALL
            
            return FilterState(
                selectedCalendarIds = calendarIds,
                statusFilters = statusFilters,
                timeFilter = timeFilter
            )
        }
    }
    
    /** Serialize FilterState to a Bundle for Intent passing */
    fun toBundle(): Bundle = Bundle().apply {
        selectedCalendarIds?.let { 
            putLongArray(BUNDLE_CALENDAR_IDS, it.toLongArray())
        } ?: putBoolean(BUNDLE_CALENDAR_NULL, true)
        
        putIntArray(BUNDLE_STATUS_FILTERS, statusFilters.map { it.ordinal }.toIntArray())
        putInt(BUNDLE_TIME_FILTER, timeFilter.ordinal)
    }
    
    /** Check if any filters are active */
    fun hasActiveFilters(): Boolean {
        return selectedCalendarIds != null || 
               statusFilters.isNotEmpty() || 
               timeFilter != TimeFilter.ALL
    }
    
    /**
     * Generate human-readable description of active filters for UI display.
     * Returns null if no filters are active.
     */
    fun toDisplayString(context: Context): String? {
        val parts = mutableListOf<String>()
        
        // Calendar filter
        if (selectedCalendarIds != null) {
            val count = selectedCalendarIds.size
            if (count > 0) {
                parts.add(context.resources.getQuantityString(
                    R.plurals.filter_calendar_summary, count, count
                ))
            }
        }
        
        // Status filters (show individual names)
        if (statusFilters.isNotEmpty()) {
            val names = statusFilters.map { option ->
                when (option) {
                    StatusOption.SNOOZED -> context.getString(R.string.filter_status_snoozed)
                    StatusOption.ACTIVE -> context.getString(R.string.filter_status_active)
                    StatusOption.MUTED -> context.getString(R.string.filter_status_muted)
                    StatusOption.RECURRING -> context.getString(R.string.filter_status_recurring)
                }
            }
            parts.add(names.joinToString(", "))
        }
        
        // Time filter
        if (timeFilter != TimeFilter.ALL) {
            val timeStr = when (timeFilter) {
                TimeFilter.STARTED_TODAY -> context.getString(R.string.filter_time_started_today)
                TimeFilter.STARTED_THIS_WEEK -> context.getString(R.string.filter_time_started_this_week)
                TimeFilter.PAST -> context.getString(R.string.filter_time_past)
                TimeFilter.STARTED_THIS_MONTH -> context.getString(R.string.filter_time_started_this_month)
                else -> null
            }
            timeStr?.let { parts.add(it) }
        }
        
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }
    /** Check if an event matches current status filters (empty set = match all) */
    fun matchesStatus(event: EventAlertRecord): Boolean {
        if (statusFilters.isEmpty()) return true  // No filter = show all
        return statusFilters.any { it.matches(event) }
    }
    
    /** Check if an event matches current time filter */
    fun matchesTime(event: EventAlertRecord, now: Long): Boolean {
        return timeFilter.matches(event, now)
    }
    
    /** Check if an event matches current calendar filter (null = all, empty = none) */
    fun matchesCalendar(event: EventAlertRecord): Boolean {
        if (selectedCalendarIds == null) return true  // No filter = show all
        return event.calendarId in selectedCalendarIds  // Empty set = show none
    }
    
    /**
     * Filter events by specified filter types.
     * @param events List of events to filter
     * @param now Current time (required if TIME filter is applied)
     * @param apply Which filters to apply (defaults to all)
     */
    inline fun <reified T> filterEvents(
        events: List<T>,
        now: Long,
        apply: Set<FilterType>,
        eventExtractor: (T) -> EventAlertRecord
    ): Array<T> {
        return events.filter { item ->
            val event = eventExtractor(item)
            (FilterType.CALENDAR !in apply || matchesCalendar(event)) &&
            (FilterType.STATUS !in apply || matchesStatus(event)) &&
            (FilterType.TIME !in apply || matchesTime(event, now))
        }.toTypedArray()
    }
    
    /** Filter EventAlertRecord list with specified filters (defaults to all) */
    fun filterEvents(
        events: List<EventAlertRecord>,
        now: Long,
        apply: Set<FilterType> = setOf(FilterType.CALENDAR, FilterType.STATUS, FilterType.TIME)
    ): Array<EventAlertRecord> {
        return filterEvents(events, now, apply) { it }
    }
    
    /** Filter DismissedEventAlertRecord list with specified filters (defaults to CALENDAR + TIME) */
    fun filterDismissedEvents(
        events: List<DismissedEventAlertRecord>,
        now: Long,
        apply: Set<FilterType> = setOf(FilterType.CALENDAR, FilterType.TIME)
    ): Array<DismissedEventAlertRecord> {
        return filterEvents(events, now, apply) { it.event }
    }
}

/**
 * Individual status filter options. Multiple can be selected (OR logic).
 */
enum class StatusOption {
    SNOOZED, ACTIVE, MUTED, RECURRING;
    
    /** Check if an event matches this specific option */
    fun matches(event: EventAlertRecord): Boolean = when (this) {
        SNOOZED -> event.snoozedUntil > 0
        ACTIVE -> event.snoozedUntil == 0L
        MUTED -> event.isMuted
        RECURRING -> event.isRepeating
    }
}

/**
 * Time filter options. Single-select.
 * Options available depend on tab (Active vs Dismissed).
 */
enum class TimeFilter {
    ALL,              // No filter (default)
    STARTED_TODAY,    // Event started today
    STARTED_THIS_WEEK,// Event started within current calendar week
    PAST,             // Event has already ended (Active tab only)
    STARTED_THIS_MONTH;// Event started within current month (Dismissed tab only)
    
    /**
     * Check if an event matches this time filter.
     * @param event The event to check
     * @param now Current time in milliseconds (required for testability)
     */
    fun matches(event: EventAlertRecord, now: Long): Boolean {
        return when (this) {
            ALL -> true
            STARTED_TODAY -> DateTimeUtils.isToday(event.instanceStartTime, now)
            STARTED_THIS_WEEK -> DateTimeUtils.isThisWeek(event.instanceStartTime, now)
            PAST -> event.instanceEndTime < now
            STARTED_THIS_MONTH -> DateTimeUtils.isThisMonth(event.instanceStartTime, now)
        }
    }
}
