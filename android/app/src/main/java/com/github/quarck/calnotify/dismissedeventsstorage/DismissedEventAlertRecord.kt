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

enum class EventDismissType(val code: Int) {
    ManuallyDismissedFromNotification(0),
    ManuallyDismissedFromActivity(1),
    AutoDismissedDueToCalendarMove(2),
    EventMovedUsingApp(3),
    AutoDismissedDueToRescheduleConfirmation(4);

    companion object {
        @JvmStatic
        fun fromInt(v: Int) = values()[v]
    }

    val shouldKeep: Boolean
        get() = true; // this != EventMovedUsingApp

    /**
     * Indicates whether an event dismissed with this type can be restored by the user.
     * for now I like the current behavior. where you can restore to the main events list and notification
     * but have no expectation of being able to restore the event to the calendar db
     * 
     * we could do more with it in the future if we wanted though
     * 
     * For discussion on the behavior and implications of this flag, see:
     * docs/dev_todo/event_restore_behavior.md
     */
    val canBeRestored: Boolean
        get() = this != AutoDismissedDueToCalendarMove && this != EventMovedUsingApp && this != AutoDismissedDueToRescheduleConfirmation
}

data class DismissedEventAlertRecord(
        val event: EventAlertRecord, // actual event that was dismissed
        val dismissTime: Long, // when dismissal happened
        val dismissType: EventDismissType  // type of dismiss
)
