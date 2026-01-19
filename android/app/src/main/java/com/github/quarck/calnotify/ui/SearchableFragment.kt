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

/**
 * Interface for fragments that support search filtering.
 */
interface SearchableFragment {
    /** Set the search query to filter events */
    fun setSearchQuery(query: String?)
    
    /** Get the current search query */
    fun getSearchQuery(): String?
    
    /** Get the total number of events (for search placeholder) */
    fun getEventCount(): Int
    
    /** Get the count of currently displayed events (after filtering) */
    fun getDisplayedEventCount(): Int = getEventCount()
    
    /** Whether there are active (non-snoozed) events - for snooze all label */
    fun hasActiveEvents(): Boolean = true
    
    /** Whether this fragment supports snooze all action */
    fun supportsSnoozeAll(): Boolean = false
    
    /** Whether this fragment supports mute all action */
    fun supportsMuteAll(): Boolean = false
    
    /** Whether this fragment supports dismiss all action */
    fun supportsDismissAll(): Boolean = false
    
    /** Whether there are any events eligible for mute all */
    fun anyForMuteAll(): Boolean = false
    
    /** Whether there are any events eligible for dismiss all */
    fun anyForDismissAll(): Boolean = false
    
    /** Called when mute all action is triggered - fragment should reload data */
    fun onMuteAllComplete() {}
    
    /** Called when dismiss all action is triggered - fragment should reload data */
    fun onDismissAllComplete() {}
    
    /** Called when filter state changes - fragment should reload data with new filter */
    fun onFilterChanged() {}
}
