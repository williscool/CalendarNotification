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
    val selectedCalendarIds: Set<Long> = emptySet(),  // empty = all calendars
    val statusFilters: Set<StatusOption> = emptySet(),  // empty = show all (no filter)
    val timeFilter: TimeFilter = TimeFilter.ALL
) {
    /** Check if an event matches current status filters (empty set = match all) */
    fun matchesStatus(event: EventAlertRecord): Boolean {
        if (statusFilters.isEmpty()) return true  // No filter = show all
        return statusFilters.any { it.matches(event) }
    }
    
    /** Check if an event matches current time filter */
    fun matchesTime(event: EventAlertRecord, now: Long): Boolean {
        return timeFilter.matches(event, now)
    }
    
    /** Check if an event matches current calendar filter (empty set = match all) */
    fun matchesCalendar(event: EventAlertRecord): Boolean {
        if (selectedCalendarIds.isEmpty()) return true  // No filter = show all
        return event.calendarId in selectedCalendarIds
    }
    
    /**
     * Filter events by specified filter types.
     * @param events List of events to filter
     * @param now Current time (required if TIME filter is applied)
     * @param apply Which filters to apply (defaults to all)
     */
    fun <T> filterEvents(
        events: List<T>,
        now: Long,
        apply: Set<FilterType>,
        eventExtractor: (T) -> EventAlertRecord
    ): Array<T> {
        @Suppress("UNCHECKED_CAST")
        return events.filter { item ->
            val event = eventExtractor(item)
            (FilterType.CALENDAR !in apply || matchesCalendar(event)) &&
            (FilterType.STATUS !in apply || matchesStatus(event)) &&
            (FilterType.TIME !in apply || matchesTime(event, now))
        }.toTypedArray() as Array<T>
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
