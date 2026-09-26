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

package com.github.quarck.calnotify.dismissedeventsstorage

import com.github.quarck.calnotify.calendar.EventAlertRecord
import java.io.Closeable

interface DismissedEventsStorageInterface : Closeable {

    fun addEvent(type: EventDismissType, changeTime: Long, event: EventAlertRecord)

    fun addEvent(type: EventDismissType, event: EventAlertRecord)

    fun addEvents(type: EventDismissType, events: Collection<EventAlertRecord>)

    fun deleteEvent(entry: DismissedEventAlertRecord)

    fun deleteEvent(event: EventAlertRecord)

    fun clearHistory()

    fun purgeOld(currentTime: Long, maxLiveTime: Long);

    val events: List<DismissedEventAlertRecord> get
    
    /** Events sorted for UI display: dismissTime descending (most recent first) */
    val eventsForDisplay: List<DismissedEventAlertRecord> get

    /**
     * Every dismissed event's primary key, without reading the rows.
     *
     * This is the app's largest table -- measured at 4183 rows against 373
     * active events -- so callers that only need to know *which* events exist
     * should not pay [events]' full row read, sort and mapping. Unsorted.
     */
    fun getAllKeys(): List<DismissedEventKey>

    /**
     * The events for [keys], and only those.
     *
     * The companion to [getAllKeys]: decide which dismissed events matter, then
     * read just them. Chunks internally, since SQLite bounds how many parameters
     * an `IN` list may carry.
     */
    fun getEventsByKeys(keys: Collection<DismissedEventKey>): List<DismissedEventAlertRecord>

}