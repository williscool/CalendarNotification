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

package com.github.quarck.calnotify.textutils

import android.content.Context
import android.text.format.DateUtils
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.displayedEndTime
import com.github.quarck.calnotify.calendar.displayedStartTime
import com.github.quarck.calnotify.calendar.CalendarProviderInterface
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.getNextAlertTimeAfter
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.reminders.ReminderStateInterface
import com.github.quarck.calnotify.utils.DateTimeUtils
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusSystemClock
import java.util.*

/**
 * Type of next notification
 */
enum class NextNotificationType {
    GCAL_REMINDER,
    APP_ALERT
}

/**
 * Result of calculating the next notification info
 */
data class NextNotificationInfo(
    val type: NextNotificationType,
    val timeUntilMillis: Long,
    val isMuted: Boolean
)

fun dateToStr(ctx: Context, time: Long)
        = DateUtils.formatDateTime(ctx, time, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE)

const val literals = "0123456789QWRTYPASDFGHJKLZXCVBNMqwrtypasdfghjklzxcvbnm"

fun encodedMinuteTimestamp(modulo: Long = 60 * 24 * 30L, clock: CNPlusClockInterface = CNPlusSystemClock()): String {

    val ts = clock.currentTimeMillis()
    val minutes = ts / 60L / 1000L

    val numLiterals = literals.length

    val sb = StringBuilder(10)

    var moduloMinutes = minutes % modulo
    var moduloVal = modulo

    while (moduloVal != 0L) {
        sb.append(literals[(moduloMinutes % numLiterals).toInt()])
        moduloMinutes /= numLiterals
        moduloVal /= numLiterals
    }

    return sb.toString()
}

class EventFormatter(
    val ctx: Context,
    private val clock: CNPlusClockInterface = CNPlusSystemClock(),
    private val calendarProvider: CalendarProviderInterface = CalendarProvider,
    private val reminderStateProvider: () -> ReminderStateInterface = { ReminderState(ctx) }
) : EventFormatterInterface {

    private val defaultLocale by lazy { Locale.getDefault() }


    private fun formatDateRangeUTC(startMillis: Long, endMillis: Long, flags: Int): String {
        val formatter = Formatter(StringBuilder(60), defaultLocale)
        return DateUtils.formatDateRange(ctx, formatter, startMillis, endMillis,
                flags, DateTimeUtils.utcTimeZoneName).toString()
    }

    private fun formatDateTimeUTC(startMillis: Long, flags: Int) =
            formatDateRangeUTC(startMillis, startMillis, flags)

    override fun formatNotificationSecondaryText(event: EventAlertRecord): String {
        val sb = StringBuilder()

        sb.append(formatDateTimeOneLine(event, false))

        val settings = Settings(ctx)
        val nextNotificationDisplay = formatNextNotificationIndicator(
            event = event,
            displayNextGCalReminder = settings.displayNextGCalReminder,
            displayNextAppAlert = settings.displayNextAppAlert,
            remindersEnabled = settings.remindersEnabled
        )
        if (nextNotificationDisplay != null) {
            sb.append(" ")
            sb.append(nextNotificationDisplay)
        }

        if (event.location != "") {
            sb.append("\n")
            sb.append(ctx.resources.getString(R.string.location));
            sb.append(" ")
            sb.append(event.location)
        }

        return sb.toString()
    }

    /**
     * Formats the next notification indicator for a single event.
     * Returns null if no indicator should be shown.
     */
    fun formatNextNotificationIndicator(
        event: EventAlertRecord,
        displayNextGCalReminder: Boolean,
        displayNextAppAlert: Boolean,
        remindersEnabled: Boolean
    ): String? {
        val currentTime = clock.currentTimeMillis()
        
        val nextInfo = calculateNextNotificationInfo(
            event = event,
            currentTime = currentTime,
            displayNextGCalReminder = displayNextGCalReminder,
            displayNextAppAlert = displayNextAppAlert,
            remindersEnabled = remindersEnabled
        ) ?: return null
        
        return formatNextNotificationInfo(nextInfo)
    }

    /**
     * Calculates what the next notification will be for a single event.
     */
    fun calculateNextNotificationInfo(
        event: EventAlertRecord,
        currentTime: Long,
        displayNextGCalReminder: Boolean,
        displayNextAppAlert: Boolean,
        remindersEnabled: Boolean
    ): NextNotificationInfo? {
        // Get next GCal reminder if enabled
        val nextGCalTime: Long? = if (displayNextGCalReminder) {
            val eventRecord = calendarProvider.getEvent(ctx, event.eventId)
            eventRecord?.getNextAlertTimeAfter(currentTime)
        } else null
        
        // Get next app alert if enabled
        val nextAppTime: Long? = if (displayNextAppAlert && remindersEnabled && !event.isMuted) {
            val reminderState = reminderStateProvider()
            val nextFire = reminderState.nextFireExpectedAt
            if (nextFire > currentTime) nextFire else null
        } else null
        
        return calculateNextNotification(
            nextGCalTime = nextGCalTime,
            nextAppTime = nextAppTime,
            currentTime = currentTime,
            isMuted = event.isMuted
        )
    }

    /**
     * Formats the next notification indicator for collapsed notifications.
     * Finds the soonest notification across all events.
     */
    fun formatNextNotificationIndicatorForCollapsed(
        events: List<EventAlertRecord>,
        displayNextGCalReminder: Boolean,
        displayNextAppAlert: Boolean,
        remindersEnabled: Boolean
    ): String? {
        val currentTime = clock.currentTimeMillis()
        
        // Find soonest GCal reminder across all events
        val soonestGCalTime: Long? = if (displayNextGCalReminder) {
            events.mapNotNull { event ->
                calendarProvider.getEvent(ctx, event.eventId)?.getNextAlertTimeAfter(currentTime)
            }.minOrNull()
        } else null
        
        // App alert time is the same for all events
        val nextAppTime: Long? = if (displayNextAppAlert && remindersEnabled) {
            // For collapsed, check if ANY event is unmuted (app alerts only fire for unmuted)
            val anyUnmuted = events.any { !it.isMuted }
            if (anyUnmuted) {
                val reminderState = reminderStateProvider()
                val nextFire = reminderState.nextFireExpectedAt
                if (nextFire > currentTime) nextFire else null
            } else null
        } else null
        
        // For collapsed, consider muted if ALL events are muted
        val allMuted = events.all { it.isMuted }
        
        val nextInfo = calculateNextNotification(
            nextGCalTime = soonestGCalTime,
            nextAppTime = nextAppTime,
            currentTime = currentTime,
            isMuted = allMuted
        ) ?: return null
        
        return formatNextNotificationInfo(nextInfo)
    }

    /**
     * Formats a NextNotificationInfo into a display string.
     */
    private fun formatNextNotificationInfo(info: NextNotificationInfo): String {
        // Round to nearest minute and format with compound units (e.g., "6h 56m")
        val roundedMillis = roundToNearestMinute(info.timeUntilMillis)
        val timeStr = formatDurationCompact(roundedMillis)
        
        val indicatorStr = when (info.type) {
            NextNotificationType.GCAL_REMINDER -> ctx.getString(R.string.next_gcal_indicator, timeStr)
            NextNotificationType.APP_ALERT -> ctx.getString(R.string.next_app_indicator, timeStr)
        }
        
        // Add muted prefix with explicit space and wrap in parentheses
        return if (info.isMuted) {
            "(${ctx.getString(R.string.muted_prefix)} $indicatorStr)"
        } else {
            "($indicatorStr)"
        }
    }

    override fun formatDateTimeTwoLines(event: EventAlertRecord, showWeekDay: Boolean): Pair<String, String> =
            when {
                event.isAllDay ->
                    formatDateTimeTwoLinesAllDay(event, showWeekDay)

                else ->
                    formatDateTimeTwoLinesRegular(event, showWeekDay)
            }

    private fun formatDateTimeTwoLinesRegular(event: EventAlertRecord, showWeekDay: Boolean = true): Pair<String, String> {

        val startTime = event.displayedStartTime
        var endTime = event.displayedEndTime
        if (endTime == 0L)
            endTime = startTime

        val startIsToday = DateUtils.isToday(startTime)
        val endIsToday = DateUtils.isToday(endTime)

        val weekFlag = if (showWeekDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0

        val line1: String
        val line2: String

        if (startIsToday && endIsToday) {
            // Today
            line1 = ctx.resources.getString(R.string.today)
            line2 = DateUtils.formatDateRange(
                    ctx, startTime, endTime, DateUtils.FORMAT_SHOW_TIME)
        }
        else {
            // Not today...
            val startIsTomorrow = DateUtils.isToday(startTime - Consts.DAY_IN_MILLISECONDS)
            val endIsTomorrow = DateUtils.isToday(endTime - Consts.DAY_IN_MILLISECONDS)

            if (startIsTomorrow && endIsTomorrow) {
                // tomorrow
                line1 = ctx.resources.getString(R.string.tomorrow)
                line2 = DateUtils.formatDateRange(
                        ctx, startTime, endTime, DateUtils.FORMAT_SHOW_TIME)
            }
            else {
                // not tomorrow...
                if (DateTimeUtils.calendarDayEquals(startTime, endTime)) {
                    // but same start and and days (so end can't be zero)
                    line1 = DateUtils.formatDateTime(
                            ctx, startTime, DateUtils.FORMAT_SHOW_DATE or weekFlag)

                    line2 = DateUtils.formatDateRange(
                            ctx, startTime, endTime,
                            DateUtils.FORMAT_SHOW_TIME)
                }
                else {
                    line1 = DateUtils.formatDateTime(
                            ctx, startTime,
                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or weekFlag)

                    line2 =
                            if (endTime != startTime)
                                DateUtils.formatDateTime(ctx, endTime,
                                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or weekFlag)
                            else
                                ""
                }
            }
        }

        return Pair(line1, line2)
    }

    private fun formatDateTimeTwoLinesAllDay(event: EventAlertRecord, showWeekDay: Boolean = true): Pair<String, String> {

        val ret: Pair<String, String>

        // A bit of a hack here: full day requests are pointing to the millisecond after
        // the time period, and it is already +1 day, so substract 1s to make formatting work correct
        // also all-day requests ignoring timezone, so should be printed as UTC
        val startTime = event.displayedStartTime
        var endTime = event.displayedEndTime
        if (endTime != 0L)
            endTime -= 1000L
        else
            endTime = startTime

        val startIsToday = DateTimeUtils.isUTCToday(startTime)
        val endIsToday = DateTimeUtils.isUTCToday(endTime)

        val weekFlag = if (showWeekDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0

        if (startIsToday && endIsToday) {
            // full day one-day event that is today
            ret = Pair(ctx.resources.getString(R.string.today), "")
        }
        else {
            val startIsTomorrow = DateTimeUtils.isUTCToday(startTime - Consts.DAY_IN_MILLISECONDS)
            val endIsTomorrow = DateTimeUtils.isUTCToday(endTime - Consts.DAY_IN_MILLISECONDS)

            if (startIsTomorrow && endIsTomorrow) {
                // full-day one-day event that is tomorrow
                ret = Pair(ctx.resources.getString(R.string.tomorrow), "")
            }
            else {
                // otherwise -- format full range if we have end time, or just start time
                // if we only have start time
                if (!DateTimeUtils.calendarDayUTCEquals(startTime, endTime)) {

                    val from = formatDateTimeUTC(
                            startTime, DateUtils.FORMAT_SHOW_DATE or weekFlag)

                    val to = formatDateTimeUTC(
                            endTime, DateUtils.FORMAT_SHOW_DATE or weekFlag);

                    val hyp = ctx.resources.getString(R.string.hyphen)

                    ret = Pair("$from $hyp", to)
                }
                else {
                    ret = Pair(formatDateTimeUTC(
                            startTime, DateUtils.FORMAT_SHOW_DATE or weekFlag), "")
                }
            }
        }

        return ret
    }

    override fun formatDateTimeOneLine(event: EventAlertRecord, showWeekDay: Boolean) =
            when {
                event.isAllDay ->
                    formatDateTimeOneLineAllDay(event, showWeekDay)
                else ->
                    formatDateTimeOneLineRegular(event, showWeekDay)
            }

    private fun formatDateTimeOneLineRegular(event: EventAlertRecord, showWeekDay: Boolean = false): String {

        val startTime = event.displayedStartTime
        var endTime = event.displayedEndTime
        if (endTime == 0L)
            endTime = startTime

        val startIsToday = DateUtils.isToday(startTime)
        val endIsToday = DateUtils.isToday(endTime)

        val weekFlag = if (showWeekDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0

        val ret: String

        if (startIsToday && endIsToday) {
            ret = DateUtils.formatDateRange(ctx, startTime, endTime, DateUtils.FORMAT_SHOW_TIME)
        }
        else {

            val startIsTomorrow = DateUtils.isToday(startTime - Consts.DAY_IN_MILLISECONDS)
            val endIsTomorrow = DateUtils.isToday(endTime - Consts.DAY_IN_MILLISECONDS)

            if (startIsTomorrow && endIsTomorrow) {
                ret = ctx.resources.getString(R.string.tomorrow) + " " +
                        DateUtils.formatDateRange(ctx, startTime, endTime, DateUtils.FORMAT_SHOW_TIME)
            }
            else {
                ret = DateUtils.formatDateRange(
                        ctx, startTime, endTime,
                        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or weekFlag)
            }
        }

        return ret
    }

    private fun formatDateTimeOneLineAllDay(event: EventAlertRecord, showWeekDay: Boolean = false): String {

        // A bit of a hack here: full day requests are pointing to the millisecond after
        // the time period, and it is already +1 day, so substract 1s to make formatting work correct
        // also all-day requests ignoring timezone, so should be printed as UTC
        val startTime = event.displayedStartTime
        var endTime = event.displayedEndTime
        if (endTime != 0L)
            endTime -= 1000L
        else
            endTime = startTime

        val startIsToday = DateTimeUtils.isUTCToday(startTime)
        val endIsToday = DateTimeUtils.isUTCToday(endTime)

        val weekFlag = if (showWeekDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0

        val ret: String

        if (startIsToday && endIsToday) {
            // full day one-day event that is today
            ret = ctx.resources.getString(R.string.today)
        }
        else {
            val startIsTomorrow = DateTimeUtils.isUTCToday(startTime - Consts.DAY_IN_MILLISECONDS)
            val endIsTomorrow = DateTimeUtils.isUTCToday(endTime - Consts.DAY_IN_MILLISECONDS)

            if (startIsTomorrow && endIsTomorrow) {
                // full-day one-day event that is tomorrow
                ret = ctx.resources.getString(R.string.tomorrow)
            }
            else {
                // otherwise -- format full range if we have end time, or just start time
                // if we only have start time

                if (!DateTimeUtils.calendarDayUTCEquals(startTime, endTime)) {
                    val from = formatDateTimeUTC(startTime, DateUtils.FORMAT_SHOW_DATE or weekFlag)

                    val to = formatDateTimeUTC(endTime, DateUtils.FORMAT_SHOW_DATE or weekFlag)

                    val hyp = ctx.resources.getString(R.string.hyphen)

                    ret = "$from $hyp $to"
                }
                else {
                    ret = formatDateTimeUTC(startTime, DateUtils.FORMAT_SHOW_DATE or weekFlag)
                }
            }
        }

        return ret
    }

    override fun formatTimePoint(time: Long): String {

        val ret: String

        if (time != 0L) {

            val isToday = DateUtils.isToday(time)
            val isTomorrow = DateUtils.isToday(time - Consts.DAY_IN_MILLISECONDS)

            if (isToday) { // only need to show time
                ret = DateUtils.formatDateTime(ctx, time, DateUtils.FORMAT_SHOW_TIME)
            }
            else if (isTomorrow) { // Tomorrow + time
                ret = ctx.resources.getString(R.string.tomorrow) + ", " +
                        DateUtils.formatDateTime(ctx, time, DateUtils.FORMAT_SHOW_TIME)
            }
            else {
                var flags = DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY

                if ((time - clock.currentTimeMillis()) / (Consts.DAY_IN_MILLISECONDS * 30) >= 3L) // over 3mon - show year
                    flags = flags or DateUtils.FORMAT_SHOW_YEAR

                ret = DateUtils.formatDateTime(ctx, time, flags)
            }
        }
        else {
            ret = ""
        }

        return ret
    }

    override fun formatSnoozedUntil(event: EventAlertRecord): String {
        return formatTimePoint(event.snoozedUntil)
    }

    override fun formatTimeDuration(time: Long, granularity: Long): String {
        val num: Long
        val unit: String
        var timeSeconds = time / 1000L;

        timeSeconds -= timeSeconds % granularity

        val resources = ctx.resources

        if (timeSeconds == 0L)
            return resources.getString(R.string.now)

        if (timeSeconds < 60L) {
            num = timeSeconds
            unit =
                    if (num != 1L)
                        resources.getString(R.string.seconds)
                    else
                        resources.getString(R.string.second)
        }
        else if (timeSeconds % Consts.DAY_IN_SECONDS == 0L) {
            num = timeSeconds / Consts.DAY_IN_SECONDS;
            unit =
                    if (num != 1L)
                        resources.getString(R.string.days)
                    else
                        resources.getString(R.string.day)
        }
        else if (timeSeconds % Consts.HOUR_IN_SECONDS == 0L) {
            num = timeSeconds / Consts.HOUR_IN_SECONDS;
            unit =
                    if (num != 1L)
                        resources.getString(R.string.hours)
                    else
                        resources.getString(R.string.hour)
        }
        else {
            num = timeSeconds / Consts.MINUTE_IN_SECONDS;
            unit =
                    if (num != 1L)
                        resources.getString(R.string.minutes)
                    else
                        resources.getString(R.string.minute)
        }

        return "$num $unit"
    }

    companion object {
        private const val MINUTE_MS = 60 * 1000L
        private const val HOUR_MS = 60 * MINUTE_MS
        private const val DAY_MS = 24 * HOUR_MS
        
        /**
         * Rounds milliseconds to the nearest minute for cleaner display.
         * Minimum of 1 minute to avoid showing "0m" or "now".
         */
        fun roundToNearestMinute(millis: Long): Long {
            val rounded = ((millis + MINUTE_MS / 2) / MINUTE_MS) * MINUTE_MS
            return maxOf(rounded, MINUTE_MS)  // At least 1 minute
        }
        
        /**
         * Formats a duration in milliseconds into a compact human-readable string.
         * - < 1 hour: just minutes (e.g., "45m")
         * - >= 1 hour, < 1 day: hours + minutes (e.g., "6h 56m")
         * - >= 1 day: days + hours, no minutes (e.g., "2d 3h")
         */
        fun formatDurationCompact(millis: Long): String {
            val totalMinutes = millis / MINUTE_MS
            val totalHours = millis / HOUR_MS
            val totalDays = millis / DAY_MS
            
            return when {
                totalDays >= 1 -> {
                    // Days + hours remainder (no minutes)
                    val remainingHours = (millis % DAY_MS) / HOUR_MS
                    if (remainingHours > 0) "${totalDays}d ${remainingHours}h" else "${totalDays}d"
                }
                totalHours >= 1 -> {
                    // Hours + minutes remainder
                    val remainingMinutes = (millis % HOUR_MS) / MINUTE_MS
                    if (remainingMinutes > 0) "${totalHours}h ${remainingMinutes}m" else "${totalHours}h"
                }
                else -> {
                    // Just minutes
                    "${maxOf(totalMinutes, 1)}m"
                }
            }
        }

        /**
         * Pure calculation function to determine the next notification.
         * GCal wins ties.
         * Returns null if neither time is available.
         */
        fun calculateNextNotification(
            nextGCalTime: Long?,
            nextAppTime: Long?,
            currentTime: Long,
            isMuted: Boolean
        ): NextNotificationInfo? {
            val gcalDuration = nextGCalTime?.let { it - currentTime }?.takeIf { it > 0 }
            val appDuration = nextAppTime?.let { it - currentTime }?.takeIf { it > 0 }
            
            return when {
                gcalDuration != null && appDuration != null -> {
                    // Both available - GCal wins ties (<=)
                    if (gcalDuration <= appDuration) {
                        NextNotificationInfo(NextNotificationType.GCAL_REMINDER, gcalDuration, isMuted)
                    } else {
                        NextNotificationInfo(NextNotificationType.APP_ALERT, appDuration, isMuted)
                    }
                }
                gcalDuration != null -> {
                    NextNotificationInfo(NextNotificationType.GCAL_REMINDER, gcalDuration, isMuted)
                }
                appDuration != null -> {
                    NextNotificationInfo(NextNotificationType.APP_ALERT, appDuration, isMuted)
                }
                else -> null
            }
        }
    }
}