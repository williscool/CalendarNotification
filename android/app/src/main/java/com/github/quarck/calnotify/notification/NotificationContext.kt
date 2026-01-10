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

package com.github.quarck.calnotify.notification

import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus

/**
 * Channel categories for notification decisions.
 * Maps to Android notification channel IDs via [toChannelId].
 */
enum class ChannelCategory {
    /** New event from calendar (DEFAULT channel) */
    EVENTS,
    /** New alarm-tagged event */
    ALARM,
    /** Already-tracked event (snooze return, periodic reminder, expanded from collapsed) */
    REMINDERS,
    /** Already-tracked alarm event */
    ALARM_REMINDERS,
    /** Muted event */
    SILENT;

    /** Convert to Android notification channel ID */
    fun toChannelId(): String = when (this) {
        EVENTS -> NotificationChannels.CHANNEL_ID_DEFAULT
        ALARM -> NotificationChannels.CHANNEL_ID_ALARM
        REMINDERS -> NotificationChannels.CHANNEL_ID_REMINDERS
        ALARM_REMINDERS -> NotificationChannels.CHANNEL_ID_ALARM_REMINDERS
        SILENT -> NotificationChannels.CHANNEL_ID_SILENT
    }
}

/**
 * Captures all derived properties needed for notification decisions.
 * 
 * This reduces state space complexity by:
 * 1. Computing derived properties once from events list
 * 2. Enforcing constraints in [init] block (impossible states throw)
 * 3. Providing decision properties ([collapsedChannel], [isReminder])
 * 
 * **Invariants enforced:**
 * - If [allMuted] is true, then [hasAlarms] must be false (muted alarms excluded from hasAlarms)
 * - If [allMuted] is true, then [hasNewTriggeringEvent] must be false (muted events can't trigger)
 * 
 * @property eventCount Number of events
 * @property hasAlarms Whether any event is alarm && !task && !muted
 * @property allMuted Whether all events are muted
 * @property hasNewTriggeringEvent Whether any new (Hidden, not snoozed, not muted) event exists
 * @property mode The notification display mode
 * @property playReminderSound Whether this is a periodic reminder
 * @property isQuietPeriodActive Whether quiet hours are active (deprecated, kept for compatibility)
 */
data class NotificationContext(
    val eventCount: Int,
    val hasAlarms: Boolean,
    val allMuted: Boolean,
    val hasNewTriggeringEvent: Boolean,
    val mode: EventNotificationManager.NotificationMode,
    val playReminderSound: Boolean,
    val isQuietPeriodActive: Boolean = false
) {
    init {
        require(!allMuted || !hasAlarms) {
            "Invariant violated: allMuted=true but hasAlarms=true (muted alarms are excluded from hasAlarms calculation)"
        }
        require(!allMuted || !hasNewTriggeringEvent) {
            "Invariant violated: allMuted=true but hasNewTriggeringEvent=true (muted events cannot trigger)"
        }
    }

    /**
     * Whether this notification should use the reminders channel.
     * True when: periodic reminder OR no new events triggering.
     */
    val isReminder: Boolean
        get() = playReminderSound || !hasNewTriggeringEvent

    /**
     * The channel category for collapsed notifications.
     * Decision tree:
     * 1. allMuted → SILENT (muted takes precedence)
     * 2. isReminder && hasAlarms → ALARM_REMINDERS
     * 3. isReminder → REMINDERS
     * 4. hasAlarms → ALARM
     * 5. else → EVENTS (new from calendar)
     */
    val collapsedChannel: ChannelCategory
        get() = when {
            allMuted -> ChannelCategory.SILENT
            isReminder && hasAlarms -> ChannelCategory.ALARM_REMINDERS
            isReminder -> ChannelCategory.REMINDERS
            hasAlarms -> ChannelCategory.ALARM
            else -> ChannelCategory.EVENTS
        }

    companion object {
        /**
         * Factory: builds a valid NotificationContext from an events list.
         * 
         * This method computes all derived properties correctly, ensuring
         * the resulting context satisfies all invariants.
         * 
         * @param events List of events to analyze
         * @param mode The notification display mode
         * @param playReminderSound Whether this is a periodic reminder
         * @param isQuietPeriodActive Whether quiet hours are active
         * @return A valid NotificationContext
         */
        fun fromEvents(
            events: List<EventAlertRecord>,
            mode: EventNotificationManager.NotificationMode,
            playReminderSound: Boolean,
            isQuietPeriodActive: Boolean = false
        ): NotificationContext {
            return NotificationContext(
                eventCount = events.size,
                hasAlarms = events.any { it.isAlarm && !it.isTask && !it.isMuted },
                allMuted = events.isNotEmpty() && events.all { it.isMuted },
                hasNewTriggeringEvent = events.any {
                    it.displayStatus == EventDisplayStatus.Hidden &&
                    it.snoozedUntil == 0L &&
                    !it.isMuted
                },
                mode = mode,
                playReminderSound = playReminderSound,
                isQuietPeriodActive = isQuietPeriodActive
            )
        }
    }
}
