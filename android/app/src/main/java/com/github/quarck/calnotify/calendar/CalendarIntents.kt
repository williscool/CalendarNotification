//
//   Calendar Notifications Plus
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
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

package com.github.quarck.calnotify.calendar

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import com.github.quarck.calnotify.logs.DevLog


object CalendarIntents {

    private const val LOG_TAG = "CalendarIntents"

    private fun intentForAction(action: String, eventId: Long): Intent {

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        val intent = Intent(action).setData(uri)

        return intent
    }

    private fun intentForAction(action: String, event: EventAlertRecord): Intent {

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId);
        val intent = Intent(action).setData(uri)

        // Only add event time for repeating events (non-repeating handled by calendar app on API 21+)
        val shouldAddEventTime = event.isRepeating

        val canAddEventTime =
                event.instanceStartTime != 0L &&
                        event.instanceEndTime != 0L &&
                        event.instanceStartTime < event.instanceEndTime

        if (shouldAddEventTime && canAddEventTime) {
            // only add if it is a valid instance start / end time, and we need both
            DevLog.debug(LOG_TAG, "Adding instance start / end for event ${event.eventId}, start: ${event.instanceStartTime}, end: ${event.instanceEndTime}");
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.instanceStartTime)
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.instanceEndTime)
        }

        return intent
    }

    fun calendarViewIntent(context: Context, event: EventAlertRecord)
            = intentForAction(Intent.ACTION_VIEW, event)

    fun viewCalendarEvent(context: Context, event: EventAlertRecord)
            = context.startActivity(intentForAction(Intent.ACTION_VIEW, event))

    fun viewCalendarEvent(context: Context, eventId: Long)
            = context.startActivity(intentForAction(Intent.ACTION_VIEW, eventId))

    /**
     * Opens calendar at a specific time (fallback when event not found).
     * 
     * Uses the calendar time URI format: content://com.android.calendar/time/{millis}
     * This opens the calendar app's day/week view centered on the specified time.
     * 
     * @param context The context to start the activity from
     * @param timeMillis The time in milliseconds to open the calendar at
     * 
     * @see <a href="https://github.com/williscool/CalendarNotification/issues/66">Issue #66</a>
     */
    fun viewCalendarAtTime(context: Context, timeMillis: Long) {
        val uri = Uri.parse("content://com.android.calendar/time/$timeMillis")
        val intent = Intent(Intent.ACTION_VIEW).setData(uri)
        context.startActivity(intent)
    }

    /**
     * Attempts to view an event in the calendar, with fallback to time-based view if event not found.
     * 
     * This method first checks if the event exists in the system calendar. If found, it opens
     * the event directly. If not found (e.g., after phone restore, sync issues, or deletion),
     * it falls back to opening the calendar at the event's scheduled time.
     * 
     * @param context The context to start the activity from
     * @param calendarProvider The calendar provider to check event existence
     * @param event The event to view
     * @return true if the event was found and opened directly, false if fallback was used
     * 
     * @see <a href="https://github.com/williscool/CalendarNotification/issues/66">Issue #66</a>
     */
    fun viewCalendarEventWithFallback(
        context: Context,
        calendarProvider: CalendarProviderInterface,
        event: EventAlertRecord
    ): Boolean {
        // Check if event exists in system calendar
        val calendarEvent = calendarProvider.getEvent(context, event.eventId)

        return if (calendarEvent != null) {
            // Event exists, open normally
            viewCalendarEvent(context, event)
            true
        } else {
            // Event not found, fallback to time-based view
            // Use displayedStartTime which falls back to startTime if instanceStartTime is 0
            val fallbackTime = event.displayedStartTime
            DevLog.info(LOG_TAG, "Event ${event.eventId} not found in calendar, " +
                "falling back to time view at $fallbackTime")
            viewCalendarAtTime(context, fallbackTime)
            false
        }
    }
}