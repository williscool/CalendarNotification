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
        // =========================================================================
        // Static helper methods - can be used standalone without creating a context
        // =========================================================================

        /**
         * Computes whether any event has an unmuted, non-task alarm.
         * 
         * This is the canonical definition of "has alarms" for notification decisions:
         * - Must be marked as alarm (`isAlarm`)
         * - Must NOT be a task (`!isTask`) - tasks don't use alarm sounds
         * - Must NOT be muted (`!isMuted`) - muted alarms are silent
         * 
         * @param events List of events to check
         * @return true if any event qualifies as an active alarm
         */
        fun computeHasAlarms(events: List<EventAlertRecord>): Boolean =
            events.any { it.isAlarm && !it.isTask && !it.isMuted }

        /**
         * Computes whether all events are muted.
         * 
         * Note: An empty list returns false (no events = not "all muted").
         * 
         * @param events List of events to check
         * @return true if list is non-empty AND all events are muted
         */
        fun computeAllMuted(events: List<EventAlertRecord>): Boolean =
            events.isNotEmpty() && events.all { it.isMuted }

        /**
         * Computes whether any new triggering event exists.
         * 
         * A "new triggering" event is one that:
         * - Has never been shown (`displayStatus == Hidden`)
         * - Is not returning from snooze (`snoozedUntil == 0`)
         * - Is not muted (`!isMuted`)
         * 
         * This determines whether to use the "events" channel (new) vs "reminders" channel.
         * 
         * @param events List of events to check
         * @return true if any event qualifies as a new triggering event
         */
        fun computeHasNewTriggeringEvent(events: List<EventAlertRecord>): Boolean =
            events.any {
                it.displayStatus == EventDisplayStatus.Hidden &&
                it.snoozedUntil == 0L &&
                !it.isMuted
            }

        /**
         * Determines if a single event should be treated as a "reminder" for channel selection.
         * 
         * Philosophy (Issue #162):
         * - First notification from calendar → calendar_events channel
         * - Every time after → calendar_reminders channel
         * 
         * An event is NEW (not a reminder) when:
         * - displayStatus == Hidden (never been shown) AND
         * - snoozedUntil == 0 (not returning from snooze)
         * 
         * An event is ALREADY TRACKED (is a reminder) when:
         * - displayStatus != Hidden (was shown before - collapsed or normal), OR
         * - snoozedUntil != 0 (returning from snooze)
         * 
         * Note: This is the single-event version. For aggregates, use [computeHasNewTriggeringEvent].
         * 
         * @param event The event to check
         * @return true if event should use reminders channel, false for events channel
         */
        fun isReminderEvent(event: EventAlertRecord): Boolean =
            event.displayStatus != EventDisplayStatus.Hidden || event.snoozedUntil != 0L

        // =========================================================================
        // Factory method
        // =========================================================================

        /**
         * Factory: builds a valid NotificationContext from an events list.
         * 
         * This method computes all derived properties correctly using the
         * static helper methods, ensuring the resulting context satisfies
         * all invariants.
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
                hasAlarms = computeHasAlarms(events),
                allMuted = computeAllMuted(events),
                hasNewTriggeringEvent = computeHasNewTriggeringEvent(events),
                mode = mode,
                playReminderSound = playReminderSound,
                isQuietPeriodActive = isQuietPeriodActive
            )
        }
    }
}
