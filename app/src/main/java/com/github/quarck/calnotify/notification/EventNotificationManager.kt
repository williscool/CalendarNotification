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

package com.github.quarck.calnotify.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.text.format.DateUtils
import com.github.quarck.calnotify.*
import com.github.quarck.calnotify.calendar.CalendarIntents
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.displayedStartTime
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.pebble.PebbleUtils
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.textutils.EventFormatterInterface
import com.github.quarck.calnotify.ui.MainActivity
import com.github.quarck.calnotify.ui.SnoozeActivityNoRecents
import com.github.quarck.calnotify.utils.*

class EventNotificationManager : EventNotificationManagerInterface {

    override fun onEventAdded(ctx: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {
        EventsStorage(ctx).use {
            // Update lastEventVisibility - we've just seen this event,
            // not using threshold when event is just added
            it.updateEvent(event,
                lastEventVisibility = System.currentTimeMillis())
        }

        postEventNotifications(ctx, formatter, false, event.eventId);
    }

    override fun onEventRestored(context: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {
        EventsStorage(context).use {
            it.updateEvent(event,
                // do not update last event visibility, so preserve original sorting order in the activity
                // lastEventVisibility = System.currentTimeMillis(),
                displayStatus = EventDisplayStatus.Hidden)
        }

        postEventNotifications(context, formatter, true, event.eventId);
    }

    override fun onEventDismissed(context: Context, formatter: EventFormatterInterface, eventId: Long, notificationId: Int) {
        removeNotification(context, eventId, notificationId);
        postEventNotifications(context, formatter, false, null);
    }

    override fun onEventSnoozed(context: Context, formatter: EventFormatterInterface,eventId: Long, notificationId: Int) {
        removeNotification(context, eventId, notificationId);
        postEventNotifications(context, formatter, false, null);
    }

    override fun onAllEventsSnoozed(context: Context) {
        context.notificationManager.cancelAll()
    }

    @Suppress("DEPRECATION")
    fun wakeScreenIfRequired(ctx: Context, settings: Settings) {

        if (settings.notificationWakeScreen) {
            //
            backgroundWakeLocked(
                ctx.powerManager,
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                Consts.SCREEN_WAKE_LOCK_NAME) {
                // Screen would actually be turned on for a duration of screen timeout set by the user
                // So don't need to keep wakelock for too long
                Thread.sleep(Consts.WAKE_SCREEN_DURATION);
            }
        }

    }

    override fun postEventNotifications(context: Context, formatter: EventFormatterInterface, force: Boolean, primaryEventId: Long?) {
        //
        val settings = Settings(context)

        val currentTime = System.currentTimeMillis()

        val isQuietPeriodActive = QuietHoursManager.getSilentUntil(settings) != 0L

        var postedAnyNotification = false

        EventsStorage(context).use {
            db ->

            // events with snoozedUntil == 0 are currently visible ones
            // events with experied snoozedUntil are the ones to beep about
            // everything else should be hidden and waiting for the next alarm

            val activeEvents =
                db.events
                    .filter {
                        (it.snoozedUntil == 0L)
                            || (it.snoozedUntil < currentTime + Consts.ALARM_THRESHOULD)
                    }
                    .sortedByDescending { it.displayedStartTime }

            val recentEvents = activeEvents.takeLast(Consts.MAX_NOTIFICATIONS - 1);
            val olderEvents = activeEvents.take(activeEvents.size - recentEvents.size)

            postedAnyNotification =
                postDisplayedEventNotifications(
                    context, db, settings,
                    formatter,
                    recentEvents,
                    force, isQuietPeriodActive,
                    primaryEventId)

            collapseDisplayedNotifications(context, db, olderEvents, settings, force);
        }

        // If this is a new notification -- wake screen when required
        if (primaryEventId != null || postedAnyNotification)
            wakeScreenIfRequired(context, settings);
    }

    override fun fireEventReminder(context: Context, formatter: EventFormatterInterface) {

        val mostRecentEvent =
            EventsStorage(context).use {
                db ->
                db.events
                    .filter { it.snoozedUntil == 0L }
                    .maxBy { it.lastEventVisibility }
            }

        if (mostRecentEvent != null) {

            val settings = Settings(context)

            postNotification(
                context,
                formatter,
                mostRecentEvent,
                settings.notificationSettingsSnapshot,
                true
            )

            wakeScreenIfRequired(context, settings);
        }
    }

    private fun collapseDisplayedNotifications(
        context: Context, db: EventsStorage,
        events: List<EventAlertRecord>, settings: Settings, force: Boolean ) {

        logger.debug("Hiding notifications for ${events.size} notification")

        for (event in events) {
            if ((event.displayStatus != EventDisplayStatus.Hidden) || force) {
                logger.debug("Hiding notification id ${event.notificationId}, eventId ${event.eventId}")
                removeNotification(context, event.eventId, event.notificationId);
            } else {
                logger.debug("Skipping collapsing notification id ${event.notificationId}, eventId ${event.eventId} - already collapsed");
            }

            if (event.snoozedUntil != 0L || event.displayStatus != EventDisplayStatus.DisplayedCollapsed) {
                db.updateEvent(event,
                    snoozedUntil = 0L,
                    displayStatus = EventDisplayStatus.DisplayedCollapsed)
            }
        }

        if (!events.isEmpty())
            postNumNotificationsCollapsed(context, db, settings, events);
        else
            hideCollapsedEventsNotification(context);
    }

    // force - if true - would re-post all active notifications. Normally only new notifications are posted to
    // avoid excessive blinking in the notifications area. Forced notifications are posted without sound or vibra
    private fun postDisplayedEventNotifications(
        context: Context,
        db: EventsStorage,
        settings: Settings,
        formatter: EventFormatterInterface,
        events: List<EventAlertRecord>,
        force: Boolean,
        isQuietPeriodActive: Boolean,
        primaryEventId: Long?
    ): Boolean {

        logger.debug("Posting ${events.size} notifications");

        val notificationsSettings = settings.notificationSettingsSnapshot

        val notificationsSettingsQuiet =
            notificationsSettings.copy(ringtoneUri = null, vibraOn = false, forwardToPebble = false);

        var postedNotification = false
        var playedAnySound = false

        for (event in events) {
            if (event.snoozedUntil == 0L) {
                // snooze zero could mean
                // - this is a new event -- we have to display it, it would have displayStatus == hidden
                // - this is an old event returning from "collapsed" state
                // - this is currently potentially displayed event but we are doing "force re-post" to
                //   ensure all events are displayed (like at boot or after app upgrade

                if ((event.displayStatus != EventDisplayStatus.DisplayedNormal) || force) {
                    // currently not displayed or forced -- post notifications
                    logger.debug("Posting notification id ${event.notificationId}, eventId ${event.eventId}");

                    var shouldBeQuiet = false

                    if (force) {
                        // If forced to re-post all notifications - we only have to actually display notifications
                        // so not playing sound / vibration here
                        logger.debug("event ${event.eventId}: 'forced' notification - staying quiet")
                        shouldBeQuiet = true
                    } else if (event.displayStatus == EventDisplayStatus.DisplayedCollapsed) {
                        // This event was already visible as "collapsed", user just removed some other notification
                        // and so we automatically expanding some of the events, this one was lucky.
                        // No sound / vibration should be played here
                        logger.debug("event ${event.eventId}: notification was collapsed, not playing sound");
                        shouldBeQuiet = true
                    } else if (isQuietPeriodActive) {
                        // we are in a silent period, normally we should always be quiet, but there
                        // are a few exclusions
                        if (primaryEventId != null && event.eventId == primaryEventId) {
                            // this is primary event -- play based on use preference for muting
                            // primary event reminders
                            logger.debug("event ${event.eventId}: quiet period and this is primary notification - sound according to settings")
                            shouldBeQuiet = settings.quietHoursMutePrimary
                        } else {
                            // not a primary event -- always silent in silent period
                            logger.debug("event ${event.eventId}: quiet period and this is NOT primary notification quiet")
                            shouldBeQuiet = true
                        }
                    }

                    logger.debug("event ${event.eventId}: shouldBeQuiet = $shouldBeQuiet")

                    postNotification(context, formatter, event,
                        if (shouldBeQuiet) notificationsSettingsQuiet else notificationsSettings,
                        force)

                    // Update db to indicate that this event is currently actively displayed
                    db.updateEvent(event, displayStatus = EventDisplayStatus.DisplayedNormal)

                    postedNotification = true
                    playedAnySound = playedAnySound || !shouldBeQuiet

                } else {
                    logger.debug("Not re-posting notification id ${event.notificationId}, eventId ${event.eventId} - already on the screen");
                }
            } else {
                // This event is currently snoozed and switching to "Shown" state

                logger.debug("Posting snoozed notification id ${event.notificationId}, eventId ${event.eventId}, isQuietPeriodActive=$isQuietPeriodActive");

                postNotification(context, formatter, event,
                    if (isQuietPeriodActive) notificationsSettingsQuiet else notificationsSettings, force)

                // Update Db to indicate that event is currently displayed and no longer snoozed
                // Since it is displayed now -- it is no longer snoozed, set snoozedUntil to zero
                // also update 'lastVisible' time since event just re-appeared
                db.updateEvent(event,
                    snoozedUntil = 0,
                    displayStatus = EventDisplayStatus.DisplayedNormal,
                    lastEventVisibility = System.currentTimeMillis() )

                postedNotification = true
                playedAnySound = playedAnySound || !isQuietPeriodActive
            }
        }

        if (playedAnySound)
            context.globalState.updateNotificationLastFiredTime();

        if (isQuietPeriodActive
            && events.isNotEmpty()
            && !playedAnySound
            && !settings.quietHoursOneTimeReminderEnabled) {

            logger.debug("Would remind after snooze period");

            settings.quietHoursOneTimeReminderEnabled = true;
        }

        return postedNotification
    }

    @Suppress("DEPRECATION")
    private fun postNotification(
        ctx: Context,
        formatter: EventFormatterInterface,
        event: EventAlertRecord,
        notificationSettings: NotificationSettingsSnapshot,
        isForce: Boolean
    ) {
        val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val calendarIntent = CalendarIntents.getCalendarViewIntent(event)

        val calendarPendingIntent =
            TaskStackBuilder.create(ctx)
                .addNextIntentWithParentStack(calendarIntent)
                .getPendingIntent(
                    event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_OPEN_OFFSET,
                    PendingIntent.FLAG_UPDATE_CURRENT)

        val notificationText = formatter.formatNotificationSecondaryText(event);

        val builder = Notification.Builder(ctx)
            .setContentTitle(event.title)
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.stat_notify_calendar)
            .setPriority(
                if (notificationSettings.headsUpNotification && !isForce)
                    Notification.PRIORITY_HIGH
                else
                    Notification.PRIORITY_DEFAULT
            )
            .setContentIntent(calendarPendingIntent)
            .setAutoCancel(!notificationSettings.showDismissButton)
            .setOngoing(notificationSettings.showDismissButton)
            .setStyle(Notification.BigTextStyle()
                .bigText(notificationText))
            .setWhen(event.displayedStartTime)
            .setShowWhenCompat(false)
            .setSortKeyCompat("${event.displayedStartTime}")
            .setEventCategoryCompat()

        logger.debug("adding pending intent for snooze, event id ${event.eventId}, notificationId ${event.notificationId}")


        builder.addAction(
            R.drawable.ic_update_white_24dp,
            ctx.getString(com.github.quarck.calnotify.R.string.snooze) ?: "SNOOZE",
            pendingActivityIntent(ctx,
                snoozeIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId),
                event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_SNOOOZE_OFFSET
            )
        )

        if (notificationSettings.showDismissButton) {
            builder.addAction(
                R.drawable.ic_clear_white_24dp,
                ctx.getString(com.github.quarck.calnotify.R.string.dismiss) ?: "DISMISS",
                pendingServiceIntent(ctx,
                    dismissOrDeleteIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId),
                    event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_DISMISS_OFFSET
                )
            )
        } else {
            builder.setDeleteIntent(
                pendingServiceIntent(ctx,
                    dismissOrDeleteIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId),
                    event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_DELETE_OFFSET
                )
            )
        }

        if (notificationSettings.ringtoneUri != null) {
            logger.debug("Adding ringtone uri ${notificationSettings.ringtoneUri}");
            builder.setSound(notificationSettings.ringtoneUri)
        } else {
            logger.debug("No ringtone for this notification")
        }

        if (notificationSettings.vibraOn) {
            logger.debug("adding vibration");
            builder.setVibrate(notificationSettings.vibrationPattern);
        } else {
            logger.debug("no vibration")
            builder.setVibrate(longArrayOf(0));
        }

        if (notificationSettings.ledNotificationOn) {
            logger.debug("Adding LED light")
            if (notificationSettings.ledPattern.size == 2)
                builder.setLights(notificationSettings.ledColor, notificationSettings.ledPattern[0], notificationSettings.ledPattern[1]);
            else
                builder.setLights(notificationSettings.ledColor, Consts.LED_DURATION_ON, Consts.LED_DURATION_OFF);
        } else {
            logger.debug("No LED light")
        }

        val notification = builder.build()

        try {
            logger.debug("adding: notificationId=${event.notificationId}")

            notificationManager.notify(
                event.notificationId,
                notification
            )
        } catch (ex: Exception) {
            logger.error("Exception: ${ex.toString()}, notificationId=${event.notificationId}, stack:")
            ex.printStackTrace()
        }

        if (notificationSettings.forwardToPebble)
            PebbleUtils.forwardNotificationToPebble(ctx, event.title, notificationText, notificationSettings.pebbleOldFirmware)
    }

    private fun snoozeIntent(ctx: Context, eventId: Long, instanceStartTime: Long, notificationId: Int): Intent {

        val intent = Intent(ctx, SnoozeActivityNoRecents::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, instanceStartTime)

        intent.action = Intent.ACTION_MAIN;
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        return intent;
    }

    private fun dismissOrDeleteIntent(ctx: Context, eventId: Long, instanceStartTime: Long, notificationId: Int): Intent {

        val intent = Intent(ctx, NotificationActionDismissService::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, instanceStartTime)
        return intent;
    }

    private fun pendingServiceIntent(ctx: Context, intent: Intent, id: Int): PendingIntent
        = PendingIntent.getService(ctx, id, intent, PendingIntent.FLAG_CANCEL_CURRENT)

    private fun pendingActivityIntent(ctx: Context, intent: Intent, id: Int): PendingIntent {

/*      intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val pendingIntent =
            TaskStackBuilder.create(ctx)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(id, PendingIntent.FLAG_UPDATE_CURRENT);

        return pendingIntent */

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        return PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)

    }

    @Suppress("UNUSED_PARAMETER")
    private fun removeNotification(ctx: Context, eventId: Long, notificationId: Int) {
        val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId);
    }

    @Suppress("UNUSED_PARAMETER")
    private fun postNumNotificationsCollapsed(
        context: Context,
        db: EventsStorage,
        settings: Settings,
        events: List<EventAlertRecord>
    ) {
        logger.debug("Posting collapsed view notification for ${events.size} events");

        val intent = Intent(context, MainActivity::class.java);
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

        val numEvents = events.size

        val title = java.lang.String.format(
            context.getString(com.github.quarck.calnotify.R.string.multiple_events),
            numEvents
        );

        val text = context.getString(com.github.quarck.calnotify.R.string.multiple_events_details)

        val bigText =
            events
                .fold(
                    StringBuilder(), {
                        sb, ev ->

                        val flags =
                            if (DateUtils.isToday(ev.displayedStartTime))
                                DateUtils.FORMAT_SHOW_TIME
                            else
                                DateUtils.FORMAT_SHOW_DATE

                        sb.append("${DateUtils.formatDateTime(context, ev.displayedStartTime, flags)}: ${ev.title}\n")
                    } )
                .toString()

        val builder =
            Notification.Builder(context)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(com.github.quarck.calnotify.R.drawable.stat_notify_calendar)
                .setPriority(Notification.PRIORITY_LOW ) // always LOW regardless of other settings for regular notifications, so it is always last
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .setStyle(Notification.BigTextStyle().bigText(bigText))
                .setNumber(numEvents)
                .setShowWhenCompat(false)

        if (settings.ledNotificationOn) {
            if (settings.ledPattern.size == 2)
                builder.setLights(settings.ledColor, settings.ledPattern[0], settings.ledPattern[1]);
            else
                builder.setLights(settings.ledColor, Consts.LED_DURATION_ON, Consts.LED_DURATION_OFF);
        }

        val notification = builder.build()

        context.notificationManager.notify(Consts.NOTIFICATION_ID_COLLAPSED, notification) // would update if already exists

        context.globalState.updateNotificationLastFiredTime()
    }

    private fun hideCollapsedEventsNotification(context: Context) {
        logger.debug("Hiding collapsed view notification");
        context.notificationManager.cancel(Consts.NOTIFICATION_ID_COLLAPSED);
    }

    companion object {
        private val logger = Logger("EventNotificationManager")

        const val EVENT_CODE_SNOOOZE_OFFSET = 0
        const val EVENT_CODE_DISMISS_OFFSET = 1
        const val EVENT_CODE_DELETE_OFFSET = 2
        const val EVENT_CODE_OPEN_OFFSET = 3
        const val EVENT_CODES_TOTAL = 4

    }
}
