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
        // Individual notification decision helpers
        // =========================================================================

        /**
         * Whether an event is returning from snooze.
         * When true, the event's snooze timer has fired and it should be re-displayed.
         */
        fun isReturningFromSnooze(event: EventAlertRecord): Boolean =
            event.snoozedUntil != 0L

        /**
         * Whether an event was previously shown in collapsed form.
         * Used to determine "already displayed" status for individual notification sound decisions.
         */
        fun wasCollapsed(event: EventAlertRecord): Boolean =
            event.displayStatus == EventDisplayStatus.DisplayedCollapsed

        /**
         * Determines whether an individual notification should be posted for this event.
         * 
         * An individual notification should be posted when:
         * - Event is returning from snooze (always post)
         * - Event is not currently displayed normally (Hidden or DisplayedCollapsed)
         * - Force flag is set (e.g., boot, settings change)
         * 
         * @param event The event to check
         * @param force Whether to force posting regardless of current display status
         * @return true if notification should be posted
         */
        fun shouldPostIndividualNotification(event: EventAlertRecord, force: Boolean): Boolean = when {
            isReturningFromSnooze(event) -> true
            event.displayStatus != EventDisplayStatus.DisplayedNormal -> true
            force -> true
            else -> false
        }

        /**
         * Computes the channel ID for partial collapse notifications ("X more events").
         * 
         * This is for the passive summary notification when some events are shown
         * individually and older ones are collapsed. Uses DEFAULT or SILENT only
         * (no alarm/reminder variants since this is a passive summary).
         * 
         * @param events List of collapsed events
         * @return SILENT channel if all muted, DEFAULT otherwise
         */
        fun partialCollapseChannelId(events: List<EventAlertRecord>): String =
            if (computeAllMuted(events)) NotificationChannels.CHANNEL_ID_SILENT
            else NotificationChannels.CHANNEL_ID_DEFAULT

        /**
         * Computes the channel ID for individual event notifications.
         * 
         * @param event The event to get channel for
         * @param isReminder Whether this is a reminder (already tracked event)
         * @param forceAlarmStream Whether to force alarm stream (e.g., for alarm events)
         * @return The appropriate channel ID
         */
        fun individualChannelId(
            event: EventAlertRecord,
            isReminder: Boolean,
            forceAlarmStream: Boolean = false
        ): String = NotificationChannels.getChannelId(
            isAlarm = event.isAlarm || forceAlarmStream,
            isMuted = event.isMuted,
            isReminder = isReminder
        )

        // =========================================================================
        // Sound/vibration decision helpers
        // =========================================================================

        /**
         * Computes whether a single event should be quiet (no sound/vibration).
         * 
         * An event should be quiet when:
         * 1. Force repost (e.g., boot, settings change) - just display, no sound
         * 2. Already displayed - no re-alerting for already visible notifications
         * 3. Quiet period active AND not primary event - silent during quiet hours
         * 4. Quiet period active AND primary event - depends on quietHoursMutePrimary setting
         * 5. Event is muted - always quiet regardless of above
         * 
         * @param event The event to check
         * @param force Whether this is a forced repost
         * @param isAlreadyDisplayed Whether the event is already showing
         * @param isQuietPeriodActive Whether quiet hours are active
         * @param isPrimaryEvent Whether this is the primary/triggering event
         * @param quietHoursMutePrimary User setting for muting primary during quiet hours
         * @return true if the event should be quiet (no sound), false to play sound
         */
        fun shouldBeQuietForEvent(
            event: EventAlertRecord,
            force: Boolean,
            isAlreadyDisplayed: Boolean,
            isQuietPeriodActive: Boolean,
            isPrimaryEvent: Boolean,
            quietHoursMutePrimary: Boolean
        ): Boolean {
            val baseQuiet = when {
                force -> true
                isAlreadyDisplayed -> true
                isQuietPeriodActive && isPrimaryEvent -> quietHoursMutePrimary && !event.isAlarm
                isQuietPeriodActive -> true
                else -> false
            }
            return baseQuiet || event.isMuted
        }

        /**
         * Computes whether setOnlyAlertOnce should be true for individual notifications.
         * 
         * setOnlyAlertOnce(true) tells Android to not re-alert if the notification is already showing.
         * We want this for:
         * - Forced reposts (e.g., after boot) - don't replay sound
         * - Expanding from collapsed - don't replay sound
         * 
         * But NOT for reminders - we explicitly WANT sound to play for reminders!
         * 
         * @param isForce Whether this is a forced repost (e.g., boot, settings change)
         * @param wasCollapsed Whether the event was previously in a collapsed notification
         * @param isReminder Whether this is a reminder notification
         * @return true if notification should only alert once (suppress sound), false to allow sound
         */
        fun shouldOnlyAlertOnce(isForce: Boolean, wasCollapsed: Boolean, isReminder: Boolean): Boolean =
            (isForce || wasCollapsed) && !isReminder

        /**
         * Applies the reminder sound override logic.
         * 
         * When this is a reminder notification, alarms can override the muted status
         * to ensure alarm sounds still play during periodic reminders.
         * 
         * @param currentShouldPlayAndVibrate The value computed by the event loop
         * @param playReminderSound Whether this is a reminder notification
         * @param hasAlarms Whether there are non-muted alarm events
         * @return Final shouldPlayAndVibrate value
         */
        fun applyReminderSoundOverride(
            currentShouldPlayAndVibrate: Boolean,
            playReminderSound: Boolean,
            hasAlarms: Boolean
        ): Boolean = if (playReminderSound) {
            currentShouldPlayAndVibrate || hasAlarms
        } else {
            currentShouldPlayAndVibrate
        }

        /**
         * Computes whether sound/vibration should play AND whether any notification was posted
         * for collapsed notifications.
         * 
         * This iterates over all events and determines:
         * 1. Whether any notification would be "posted" (status change)
         * 2. Whether sound should play based on event states
         * 
         * @param events List of events to display
         * @param force Whether to force re-post
         * @param isQuietPeriodActive Whether quiet hours are active
         * @param primaryEventId The primary event ID (for quiet hours exception)
         * @param quietHoursMutePrimary Settings flag for muting primary during quiet hours
         * @param playReminderSound Whether this is a reminder (affects sound logic)
         * @param hasAlarms Whether there are non-muted alarm events
         * @return Pair of (shouldPlayAndVibrate, postedNotification)
         */
        fun computeShouldPlayAndVibrate(
            events: List<EventAlertRecord>,
            force: Boolean,
            isQuietPeriodActive: Boolean,
            primaryEventId: Long?,
            quietHoursMutePrimary: Boolean,
            playReminderSound: Boolean,
            hasAlarms: Boolean
        ): Pair<Boolean, Boolean> {
            var shouldPlayAndVibrate = false
            var postedNotification = false

            for (event in events) {
                if (event.snoozedUntil == 0L) {
                    // For collapsed: process if not already collapsed, or forced, or reminder
                    if (event.displayStatus != EventDisplayStatus.DisplayedCollapsed || force || playReminderSound) {
                        postedNotification = true

                        // For collapsed notifications, "already displayed" means DisplayedNormal
                        val isAlreadyDisplayed = event.displayStatus == EventDisplayStatus.DisplayedNormal
                        val isPrimaryEvent = primaryEventId != null && event.eventId == primaryEventId

                        val shouldBeQuiet = shouldBeQuietForEvent(
                            event = event,
                            force = force,
                            isAlreadyDisplayed = isAlreadyDisplayed,
                            isQuietPeriodActive = isQuietPeriodActive,
                            isPrimaryEvent = isPrimaryEvent,
                            quietHoursMutePrimary = quietHoursMutePrimary
                        )

                        shouldPlayAndVibrate = shouldPlayAndVibrate || !shouldBeQuiet
                    }
                } else {
                    // Snoozed event returning
                    postedNotification = true
                    shouldPlayAndVibrate = shouldPlayAndVibrate || (!isQuietPeriodActive && !event.isMuted)
                }
            }

            // Apply reminder sound override
            val finalShouldPlay = applyReminderSoundOverride(shouldPlayAndVibrate, playReminderSound, hasAlarms)
            return Pair(finalShouldPlay, postedNotification)
        }

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
