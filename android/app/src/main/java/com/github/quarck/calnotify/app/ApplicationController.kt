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

package com.github.quarck.calnotify.app

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendareditor.CalendarChangeRequestMonitor
import com.github.quarck.calnotify.calendareditor.CalendarChangeRequestMonitorInterface
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitor
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorInterface
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.globalState
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.notification.EventNotificationManager
import com.github.quarck.calnotify.notification.EventNotificationManagerInterface
import com.github.quarck.calnotify.persistentState
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.quiethours.QuietHoursManagerInterface
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.ui.UINotifier
import com.github.quarck.calnotify.calendareditor.CalendarChangeManagerInterface
import com.github.quarck.calnotify.calendareditor.CalendarChangeManager
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.detailed
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusSystemClock

import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.customUse

interface ApplicationControllerInterface {
    // Clock interface for time-related operations
    val clock: CNPlusClockInterface
    
    fun registerNewEvent(context: Context, event: EventAlertRecord): Boolean
    fun registerNewEvents(context: Context, pairs: List<Pair<MonitorEventAlertEntry, EventAlertRecord>>): ArrayList<Pair<MonitorEventAlertEntry, EventAlertRecord>>
    fun postEventNotifications(context: Context, events: Collection<EventAlertRecord>)
    fun hasActiveEventsToRemind(context: Context): Boolean
    fun onEventAlarm(context: Context)
    fun onAppUpdated(context: Context)
    fun onBootComplete(context: Context)
    fun onCalendarChanged(context: Context)
    fun onCalendarRescanForRescheduledFromService(context: Context, userActionUntil: Long)
    fun onCalendarReloadFromService(context: Context, userActionUntil: Long)
    fun onCalendarEventMovedWithinApp(context: Context, oldEvent: EventRecord, newEvent: EventRecord)
    fun afterCalendarEventFired(context: Context)
    fun shouldMarkEventAsHandledAndSkip(context: Context, event: EventAlertRecord): Boolean
    fun onMainActivityCreate(context: Context?)
    fun onMainActivityStarted(context: Context?)
    fun onMainActivityResumed(context: Context?, shouldRepost: Boolean, monitorSettingsChanged: Boolean)
    fun onTimeChanged(context: Context)
    fun dismissEvents(context: Context, db: EventsStorageInterface, events: Collection<EventAlertRecord>, dismissType: EventDismissType, notifyActivity: Boolean)
    fun dismissEvent(context: Context, dismissType: EventDismissType, event: EventAlertRecord)
    fun dismissAndDeleteEvent(context: Context, dismissType: EventDismissType, event: EventAlertRecord): Boolean
    fun dismissEvent(context: Context, dismissType: EventDismissType, eventId: Long, instanceStartTime: Long, notificationId: Int, notifyActivity: Boolean)
    fun restoreEvent(context: Context, event: EventAlertRecord)
    fun moveEvent(context: Context, event: EventAlertRecord, addTime: Long): Boolean
    fun moveAsCopy(context: Context, calendar: CalendarRecord, event: EventAlertRecord, addTime: Long): Long
    fun forceRepostNotifications(context: Context)
    fun postNotificationsAutoDismissedDebugMessage(context: Context)
    fun postNearlyMissedNotificationDebugMessage(context: Context)
    fun isCustomQuietHoursActive(ctx: Context): Boolean
    fun applyCustomQuietHoursForSeconds(ctx: Context, quietForSeconds: Int)
    fun onReminderAlarmLate(context: Context, currentTime: Long, alarmWasExpectedAt: Long)
    fun onSnoozeAlarmLate(context: Context, currentTime: Long, alarmWasExpectedAt: Long)
}

object ApplicationController : ApplicationControllerInterface, EventMovedHandler {

    private const val LOG_TAG = "App"

    private var settings: Settings? = null
    private fun getSettings(ctx: Context): Settings {
        if (settings == null) {
            synchronized(this) {
                if (settings == null)
                    settings = Settings(ctx)
            }
        }
        return settings!!
    }

    val notificationManager: EventNotificationManagerInterface = EventNotificationManager()

    val alarmScheduler: AlarmSchedulerInterface by lazy { AlarmScheduler(clock) }

    private var quietHoursManagerValue: QuietHoursManagerInterface? = null
    private fun getQuietHoursManager(ctx: Context): QuietHoursManagerInterface {
        if (quietHoursManagerValue == null) {
            synchronized(this) {
                if (quietHoursManagerValue == null)
                    quietHoursManagerValue = QuietHoursManager(ctx)
            }
        }
        return quietHoursManagerValue!!
    }

    private val calendarReloadManager: CalendarReloadManagerInterface = CalendarReloadManager

    private val calendarProvider: CalendarProviderInterface = CalendarProvider

    private val calendarChangeManager: CalendarChangeManagerInterface by lazy { CalendarChangeManager(calendarProvider)}

    private val calendarMonitorInternal: CalendarMonitorInterface by lazy { CalendarMonitor(calendarProvider) }

    private val addEventMonitor: CalendarChangeRequestMonitorInterface by lazy { CalendarChangeRequestMonitor() }

    private val tagsManager: TagsManagerInterface by lazy { TagsManager() }

    val CalendarMonitor: CalendarMonitorInterface
        get() = calendarMonitorInternal

    val AddEventMonitorInstance: CalendarChangeRequestMonitorInterface
        get() = addEventMonitor

    // Clock interface for time-related operations
    override val clock: CNPlusClockInterface = CNPlusSystemClock()

//    fun hasActiveEvents(context: Context) =
//            EventsStorage(context).classCustomUse {
//                val settings = Settings(context)
//                it.events.filter { it.snoozedUntil == 0L && it.isNotSpecial && !it.isMuted && !it.isTask }.any()
//            }

    override fun hasActiveEventsToRemind(context: Context) =
            EventsStorage(context).classCustomUse {
                //val settings = Settings(context)
                it.events.filter { it.snoozedUntil == 0L && it.isNotSpecial && !it.isMuted && !it.isTask }.any()
            }


    override fun onEventAlarm(context: Context) {

        DevLog.info(LOG_TAG, "onEventAlarm at ${clock.currentTimeMillis()}");

        val alarmWasExpectedAt = context.persistentState.nextSnoozeAlarmExpectedAt
        val currentTime = clock.currentTimeMillis()

        context.globalState?.lastTimerBroadcastReceived = clock.currentTimeMillis()

        notificationManager.postEventNotifications(context, EventFormatter(context), false, null)
        alarmScheduler.rescheduleAlarms(context, getSettings(context), getQuietHoursManager(context))

        if (currentTime > alarmWasExpectedAt + Consts.ALARM_THRESHOLD) {
            this.onSnoozeAlarmLate(context, currentTime, alarmWasExpectedAt)
        }
    }

    override fun onAppUpdated(context: Context) {

        DevLog.info(LOG_TAG, "Application updated")

        // this will post event notifications for existing known requests
        notificationManager.postEventNotifications(context, EventFormatter(context), isRepost = true)
        alarmScheduler.rescheduleAlarms(context, getSettings(context), getQuietHoursManager(context))

        this.CalendarMonitor.launchRescanService(
                context,
                reloadCalendar = true,
                rescanMonitor = true
        )
    }

    override fun onBootComplete(context: Context) {

        DevLog.info(LOG_TAG, "OS boot is complete")

        // this will post event notifications for existing known requests
        notificationManager.postEventNotifications(context, EventFormatter(context), isRepost = true)

        alarmScheduler.rescheduleAlarms(context, getSettings(context), getQuietHoursManager(context))

        this.CalendarMonitor.launchRescanService(
                context,
                reloadCalendar = true,
                rescanMonitor = true
        )
    }

    override fun onCalendarChanged(context: Context) {

        DevLog.info(LOG_TAG, "onCalendarChanged")
        this.CalendarMonitor.launchRescanService(
                context,
                delayed = 2000,
                reloadCalendar = true,
                rescanMonitor = true
        )
    }

    override fun onCalendarRescanForRescheduledFromService(context: Context, userActionUntil: Long) {

        DevLog.info(LOG_TAG, "onCalendarRescanForRescheduledFromService")

        val changes = EventsStorage(context).classCustomUse {
            db -> calendarReloadManager.rescanForRescheduledEvents(context, db, calendarProvider, this)
        }

        if (changes) {
            notificationManager.postEventNotifications(context,
                    EventFormatter(context),
                    isRepost = true
            )

            alarmScheduler.rescheduleAlarms(context, getSettings(context), getQuietHoursManager(context))

            val isUserAction = (clock.currentTimeMillis() < userActionUntil)
            UINotifier.notify(context, isUserAction)
        }
        else {
            DevLog.debug(LOG_TAG, "No calendar changes detected")
        }
    }

    override fun onCalendarReloadFromService(context: Context, userActionUntil: Long) {

        DevLog.info(LOG_TAG, "calendarReloadFromService")

        val changes = EventsStorage(context).classCustomUse {
            db -> calendarReloadManager.reloadCalendar(context, db, calendarProvider, this)
        }

        DevLog.debug(LOG_TAG, "calendarReloadFromService: ${changes}")

        if (changes) {
            notificationManager.postEventNotifications(
                    context,
                    EventFormatter(context),
                    isRepost = true
            )

            alarmScheduler.rescheduleAlarms(context, getSettings(context), getQuietHoursManager(context))

            val isUserAction = (clock.currentTimeMillis() < userActionUntil)
            UINotifier.notify(context, isUserAction)
        }
        else {
            DevLog.debug(LOG_TAG, "No calendar changes detected")
        }
    }

  override fun onCalendarEventMovedWithinApp(context: Context, oldEvent: EventRecord, newEvent: EventRecord) {

        //  TODO: MAKE SURE YOU MAKE THIS TIME AN OVERRIDEABLE PROPERTY FOR TESTS!
        val newAlertTime = newEvent.nextAlarmTime(clock.currentTimeMillis())

        val shouldAutoDismiss =
                ApplicationController.checkShouldRemoveMovedEvent(
                        context,
                        oldEvent.eventId,
                        oldEvent.startTime,
                        newEvent.startTime,
                        newAlertTime
                )

        if (shouldAutoDismiss) {
            EventsStorage(context).classCustomUse {
                db ->
                val alertRecord = db.getEvent(oldEvent.eventId, oldEvent.startTime)

                if (alertRecord != null) {
                    dismissEvent(
                            context,
                            db,
                            alertRecord,
                            EventDismissType.EventMovedUsingApp,
                            false
                    )
                }
            }
        }

        UINotifier.notify(context, true)
    }

    // some housekeeping that we have to do after firing calendar event
    override fun afterCalendarEventFired(context: Context) {

        alarmScheduler.rescheduleAlarms(context, getSettings(context), getQuietHoursManager(context))
        UINotifier.notify(context, false)
    }

    override fun postEventNotifications(context: Context, events: Collection<EventAlertRecord>) {

        if (events.size == 1)
            notificationManager.onEventAdded(context, EventFormatter(context), events.first())
        else
            notificationManager.postEventNotifications(context, EventFormatter(context))
    }

    override fun shouldMarkEventAsHandledAndSkip(context: Context, event: EventAlertRecord): Boolean {

        val settings = getSettings(context)

        if (event.eventStatus == EventStatus.Cancelled && settings.dontShowCancelledEvents) {
            // indicate that we should mark as handled in the provider and skip
            DevLog.info(LOG_TAG, "Event ${event.eventId}, status Cancelled - ignored")
            return true
        }

        if (event.attendanceStatus == AttendanceStatus.Declined && settings.dontShowDeclinedEvents) {
            // indicate that we should mark as handled in the provider and skip
            DevLog.info(LOG_TAG, "Event ${event.eventId}, status Declined - ignored")
            return true
        }

        if (event.isAllDay && settings.dontShowAllDayEvents) {
            DevLog.info(LOG_TAG, "Event ${event.eventId} is an all day event - ignored per user setting")
            return true
        }

        return false
    }


    override fun registerNewEvent(context: Context, event: EventAlertRecord): Boolean {

        var ret = false

        val settings = getSettings(context)

        if (event.calendarId != -1L && !settings.getCalendarIsHandled(event.calendarId)) {
            DevLog.info(LOG_TAG, "Event ${event.eventId} -> calendar ${event.calendarId} is not handled");
            return ret;
        }

        tagsManager.parseEventTags(context, settings, event)

        DevLog.info(LOG_TAG, "registerNewEvent: Event fired: calId ${event.calendarId}, eventId ${event.eventId}, instanceStart ${event.instanceStartTime}, alertTime ${event.alertTime}, muted: ${event.isMuted}, task: ${event.isTask}")

        // 1st step - save event into DB
        EventsStorage(context).classCustomUse {
            db ->

            if (event.isNotSpecial)
                event.lastStatusChangeTime = clock.currentTimeMillis()
            else
                event.lastStatusChangeTime = Long.MAX_VALUE

            if (event.isRepeating) {
                // repeating event - always simply add
                db.addEvent(event) // ignoring result as we are using other way of validating
                //notificationManager.onEventAdded(context, EventFormatter(context), event)
            }
            else {
                // non-repeating event - make sure we don't create two records with the same eventId
                val oldEvents = db.getEventInstances(event.eventId)

                DevLog.info(LOG_TAG, "Non-repeating event, already have ${oldEvents.size} old requests with same event id ${event.eventId}, removing old")

                try {
                    // delete old instances for the same event id (should be only one, but who knows)
                    notificationManager.onEventsDismissing(context, oldEvents)

                    val formatter = EventFormatter(context)
                    for (oldEvent in oldEvents) {
                        db.deleteEvent(oldEvent)
                        notificationManager.onEventDismissed(context, formatter, oldEvent.eventId, oldEvent.notificationId)
                    }
                }
                catch (ex: Exception) {
                    DevLog.error(LOG_TAG, "exception while removing old requests: ${ex.detailed}");
                }

                // add newly fired event
                db.addEvent(event)
                //notificationManager.onEventAdded(context, EventFormatter(context), event)
            }
        }

        // 2nd step - re-open new DB instance and make sure that event:
        // * is there
        // * is not set as visible
        // * is not snoozed
        EventsStorage(context).classCustomUse {
            db ->

            if (event.isRepeating) {
                // return true only if we can confirm, by reading event again from DB
                // that it is there
                // Caller is using our return value as "safeToRemoveOriginalReminder" flag
                val dbEvent = db.getEvent(event.eventId, event.instanceStartTime)
                ret = dbEvent != null && dbEvent.snoozedUntil == 0L

            }
            else {
                // return true only if we can confirm, by reading event again from DB
                // that it is there
                // Caller is using our return value as "safeToRemoveOriginalReminder" flag
                val dbEvents = db.getEventInstances(event.eventId)
                ret = dbEvents.size == 1 && dbEvents[0].snoozedUntil == 0L
            }
        }

        if (!ret)
            DevLog.error(LOG_TAG, "Error adding event with id ${event.eventId}, cal id ${event.calendarId}, " +
                    "instance st ${event.instanceStartTime}, repeating: " +
                    "${event.isRepeating}, allDay: ${event.isAllDay}, alertTime=${event.alertTime}");
        else {
            DevLog.debug(LOG_TAG, "event added: ${event.eventId} (cal id: ${event.calendarId})")

//            WasHandledCache(context).classCustomUse {
//                cache -> cache.addHandledAlert(event)
//            }
        }

        ReminderState(context).onNewEventFired()

        return ret
    }

    override fun registerNewEvents(
            context: Context,
            //wasHandledCache: WasHandledCacheInterface,
            pairs: List<Pair<MonitorEventAlertEntry, EventAlertRecord>>
    ): ArrayList<Pair<MonitorEventAlertEntry, EventAlertRecord>> {

        val settings = getSettings(context)

        val handledCalendars = calendarProvider.getHandledCalendarsIds(context, settings)

        val handledPairs = pairs.filter {
            (_, event) ->
            handledCalendars.contains(event.calendarId) || event.calendarId == -1L
        }

        val pairsToAdd = arrayListOf<Pair<MonitorEventAlertEntry, EventAlertRecord>>()
        val eventsToDismiss = arrayListOf<EventAlertRecord>()

        var eventsToAdd: List<EventAlertRecord>? = null

        // 1st step - save event into DB
        EventsStorage(context).classCustomUse {
            db ->

            for ((alert, event) in handledPairs) {

                DevLog.info(LOG_TAG, "registerNewEvents: Event fired, calId ${event.calendarId}, eventId ${event.eventId}, instanceStart ${event.instanceStartTime}, alertTime=${event.alertTime}")

                tagsManager.parseEventTags(context, settings, event)

                if (event.isRepeating) {
                    // repeating event - always simply add
                    pairsToAdd.add(Pair(alert, event))
                }
                else {
                    // non-repeating event - make sure we don't create two records with the same eventId
                    val oldEvents = db.getEventInstances(event.eventId)

                    DevLog.info(LOG_TAG, "Non-repeating event, already have ${oldEvents.size} old requests with same event id ${event.eventId}, removing old")

                    try {
                        // delete old instances for the same event id (should be only one, but who knows)
                        eventsToDismiss.addAll(oldEvents)
                    }
                    catch (ex: Exception) {
                        DevLog.error(LOG_TAG, "exception while removing old requests: ${ex.detailed}");
                    }

                    // add newly fired event
                    pairsToAdd.add(Pair(alert, event))
                }
            }

            if (!eventsToDismiss.isEmpty()) {
                // delete old instances for the same event id (should be only one, but who knows)

                notificationManager.onEventsDismissing(context, eventsToDismiss)

                db.deleteEvents(eventsToDismiss)

                val hasActiveEvents = db.events.any { it.snoozedUntil != 0L && !it.isSpecial }

                notificationManager.onEventsDismissed(
                        context,
                        EventFormatter(context),
                        eventsToDismiss,
                        postNotifications = false,   // don't repost notifications at this stage, but only dismiss currently active
                        hasActiveEvents = hasActiveEvents
                )
            }

            if (!pairsToAdd.isEmpty()) {

                var currentTime = clock.currentTimeMillis()
                for ((_, event) in pairsToAdd)
                    event.lastStatusChangeTime = currentTime++

                eventsToAdd = pairsToAdd.map {
                    it.second
                }

                eventsToAdd?.let {
                    db.addEvents(it)  // ignoring result of add - here we are using another way to validate succesfull add
                }
            }
        }

        // 2nd step - re-open new DB instance and make sure that event:
        // * is there
        // * is not set as visible
        // * is not snoozed

        val validPairs = arrayListOf<Pair<MonitorEventAlertEntry, EventAlertRecord>>()

        EventsStorage(context).classCustomUse {
            db ->

            for ((alert, event) in pairsToAdd) {

                if (event.isRepeating) {
                    // return true only if we can confirm, by reading event again from DB
                    // that it is there
                    // Caller is using our return value as "safeToRemoveOriginalReminder" flag
                    val dbEvent = db.getEvent(event.eventId, event.instanceStartTime)

                    if (dbEvent != null && dbEvent.snoozedUntil == 0L) {
                        validPairs.add(Pair(alert, event))
                    }
                    else {
                        DevLog.error(LOG_TAG, "Failed to add event ${event.eventId} ${event.alertTime} ${event.instanceStartTime} into DB properly")
                    }
                }
                else {
                    // return true only if we can confirm, by reading event again from DB
                    // that it is there
                    // Caller is using our return value as "safeToRemoveOriginalReminder" flag
                    val dbEvents = db.getEventInstances(event.eventId)

                    if (dbEvents.size == 1 && dbEvents[0].snoozedUntil == 0L) {
                        validPairs.add(Pair(alert, event))
                    }
                    else {
                        DevLog.error(LOG_TAG, "Failed to add event ${event.eventId} ${event.alertTime} ${event.instanceStartTime} into DB properly")
                    }
                }
            }
        }

        if (pairs.size == validPairs.size) {
            //eventsToAdd?.let { wasHandledCache.addHandledAlerts(it) }
        }
        else {
            DevLog.warn(LOG_TAG, "registerNewEvents: Added ${validPairs.size} requests out of ${pairs.size}")
        }

        ReminderState(context).onNewEventFired()

        return validPairs
    }


//    override fun onEventMoved(
//            context: Context,
//            db: EventsStorageInterface,
//            oldEvent: EventAlertRecord,
//            newEvent: EventRecord,
//            newAlertTime: Long
//    ): Boolean {
//
//        var ret = false
//
//        if (!getSettings(context).notificationAutoDismissOnReschedule)
//            return false
//
//        val oldTime = oldEvent.displayedStartTime
//        val newTime = newEvent.newInstanceStartTime
//
//        if (newTime - oldTime > Consts.EVENT_MOVE_THRESHOLD) {
//            DevLog.info(context, LOG_TAG, "Event ${oldEvent.eventId} moved by ${newTime - oldTime} ms")
//
//            if (newAlertTime > clock.currentTimeMillis() + Consts.ALARM_THRESHOLD) {
//
//                DevLog.info(context, LOG_TAG, "Event ${oldEvent.eventId} - alarm in the future confirmed, at $newAlertTime, auto-dismissing notification")
//
//                dismissEvent(
//                        context,
//                        db,
//                        oldEvent.copy(newInstanceStartTime = newEvent.newInstanceStartTime, newInstanceEndTime =  newEvent.newInstanceEndTime),
//                        EventDismissType.AutoDismissedDueToCalendarMove,
//                        true)
//
//                ret = true
//
//                if (getSettings(context).debugNotificationAutoDismiss)
//                    notificationManager.postNotificationsAutoDismissedDebugMessage(context)
//            }
//        }
//
//        return ret
//    }

    override fun checkShouldRemoveMovedEvent(
            context: Context,
            oldEvent: EventAlertRecord,
            newEvent: EventRecord,
            newAlertTime: Long
    ): Boolean
            = checkShouldRemoveMovedEvent(
                    context,
                    oldEvent.eventId,
                    oldEvent.displayedStartTime,
                    newEvent.startTime,
                    newAlertTime
            )

    override fun checkShouldRemoveMovedEvent(
            context: Context,
            eventId: Long,
            oldStartTime: Long,
            newStartTime: Long,
            newAlertTime: Long
    ): Boolean {
        var ret = false

        DevLog.info(LOG_TAG, "Event ${eventId} - checking if should auto remove, oldStartTime $oldStartTime,  newStartTime $newStartTime,  newAlertTime $newAlertTime, ")

        if (newStartTime - oldStartTime > Consts.EVENT_MOVE_THRESHOLD) {
            if (newAlertTime > clock.currentTimeMillis() + Consts.ALARM_THRESHOLD) {

                DevLog.info(LOG_TAG, "Event ${eventId} - alarm in the future confirmed, at $newAlertTime, marking for auto-dismissal")
                ret = true
            }
            else {
                DevLog.info(LOG_TAG, "Event ${eventId} moved by ${newStartTime - oldStartTime} ms - not enought to auto-dismiss")
            }
        }

        return ret
    }

    fun toggleMuteForEvent(context: Context, eventId: Long, instanceStartTime: Long, muteAction: Int): Boolean {

        var ret: Boolean = false

        //val currentTime = clock.currentTimeMillis()

        val mutedEvent: EventAlertRecord? =
                EventsStorage(context).classCustomUse {
                    db ->
                    var event = db.getEvent(eventId, instanceStartTime)

                    if (event != null) {

                        val (success, newEvent) = db.updateEvent(event, isMuted = muteAction == 0)
                        event = if (success) newEvent else null
                    }

                    event;
                }

        if (mutedEvent != null) {
            ret = true;

            notificationManager.onEventMuteToggled(context, EventFormatter(context), mutedEvent)

            ReminderState(context).onUserInteraction(clock.currentTimeMillis())

            alarmScheduler.rescheduleAlarms(context, getSettings(context), getQuietHoursManager(context))

            DevLog.info(LOG_TAG, "Event ${eventId} / ${instanceStartTime} mute toggled: ${mutedEvent.isMuted}: $ret")
        }
        else {
            DevLog.info(LOG_TAG, "Event ${eventId} / ${instanceStartTime} - failed to snooze evend by $muteAction")
        }

        return ret
    }

    fun snoozeEvent(context: Context, eventId: Long, instanceStartTime: Long, snoozeDelay: Long): SnoozeResult? {

        var ret: SnoozeResult? = null

        val currentTime = clock.currentTimeMillis()

        val snoozedEvent: EventAlertRecord? =
                EventsStorage(context).classCustomUse {
                    db ->
                    var event = db.getEvent(eventId, instanceStartTime)

                    if (event != null) {
                        var snoozedUntil =
                                if (snoozeDelay > 0L)
                                    currentTime + snoozeDelay
                                else
                                    event.displayedStartTime - Math.abs(snoozeDelay) // same as "event.instanceStart + snoozeDelay" but a little bit more readable

                        if (snoozedUntil < currentTime + Consts.ALARM_THRESHOLD) {
                            DevLog.error(LOG_TAG, "snooze: $eventId / $instanceStartTime by $snoozeDelay: new time is in the past, snoozing by 1m instead")
                            snoozedUntil = currentTime + Consts.FAILBACK_SHORT_SNOOZE
                        }

                        val (success, newEvent) = db.updateEvent(event,
                                snoozedUntil = snoozedUntil,
                                lastStatusChangeTime = currentTime,
                                displayStatus = EventDisplayStatus.Hidden)

                        event = if (success) newEvent else null
                    }

                    event;
                }

        if (snoozedEvent != null) {
            notificationManager.onEventSnoozed(context, EventFormatter(context), snoozedEvent.eventId, snoozedEvent.notificationId);

            ReminderState(context).onUserInteraction(clock.currentTimeMillis())

            val quietHoursManager = getQuietHoursManager(context)

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager)

            val silentUntil = quietHoursManager.getSilentUntil(getSettings(context), snoozedEvent.snoozedUntil)

            ret = SnoozeResult(SnoozeType.Snoozed, snoozedEvent.snoozedUntil, silentUntil)

            DevLog.info(LOG_TAG, "Event ${eventId} / ${instanceStartTime} snoozed: by $snoozeDelay: $ret")
        }
        else {
            DevLog.info(LOG_TAG, "Event ${eventId} / ${instanceStartTime} - failed to snooze evend by $snoozeDelay")
        }

        return ret
    }

    fun snoozeEvents(context: Context, filter: (EventAlertRecord)->Boolean, snoozeDelay: Long, isChange: Boolean, onlySnoozeVisible: Boolean): SnoozeResult? {

        var ret: SnoozeResult? = null

        val currentTime = clock.currentTimeMillis()

        var snoozedUntil = 0L

        var allSuccess = true

        EventsStorage(context).classCustomUse {
            db ->
            val events = db.events.filter { it.isNotSpecial && filter(it) }

            // Don't allow requests to have exactly the same "snoozedUntil", so to have
            // predicted sorting order, so add a tiny (0.001s per event) adjust to each
            // snoozed time

            var snoozeAdjust = 0

            for (event in events) {

                val newSnoozeUntil = currentTime + snoozeDelay + snoozeAdjust

                // onlySnoozeVisible

                var snoozeThisEvent: Boolean

                if (!onlySnoozeVisible) {
                    snoozeThisEvent = isChange || event.snoozedUntil == 0L || event.snoozedUntil < newSnoozeUntil
                }
                else {
                    snoozeThisEvent = event.snoozedUntil == 0L
                }

                if (snoozeThisEvent) {
                    val (success, _) =
                            db.updateEvent(
                                    event,
                                    snoozedUntil = newSnoozeUntil,
                                    lastStatusChangeTime = currentTime
                            )

                    allSuccess = allSuccess && success;

                    ++snoozeAdjust

                    snoozedUntil = newSnoozeUntil
                }
            }
        }

        if (allSuccess && snoozedUntil != 0L) {

            notificationManager.onAllEventsSnoozed(context)

            val quietHoursManager = getQuietHoursManager(context)

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager)

            val silentUntil = quietHoursManager.getSilentUntil(getSettings(context), snoozedUntil)

            ret = SnoozeResult(SnoozeType.Snoozed, snoozedUntil, silentUntil)

            DevLog.info(LOG_TAG, "Snooze all by $snoozeDelay: success, $ret")
        }
        else {
            DevLog.info(LOG_TAG, "Snooze all by $snoozeDelay: failed")
        }

        return ret
    }

    fun snoozeAllCollapsedEvents(context: Context, snoozeDelay: Long, isChange: Boolean, onlySnoozeVisible: Boolean): SnoozeResult? {
        return snoozeEvents(context, { it.displayStatus == EventDisplayStatus.DisplayedCollapsed }, snoozeDelay, isChange, onlySnoozeVisible)
    }

    fun snoozeAllEvents(context: Context, snoozeDelay: Long, isChange: Boolean, onlySnoozeVisible: Boolean, searchQuery: String? = null): SnoozeResult? {
        return snoozeEvents(context, { event ->
            searchQuery?.let { query ->
                event.title.contains(query, ignoreCase = true) ||
                event.desc.contains(query, ignoreCase = true)
            } ?: true
        }, snoozeDelay, isChange, onlySnoozeVisible)
    }

    fun fireEventReminder(
            context: Context,
            itIsAfterQuietHoursReminder: Boolean, hasActiveAlarms: Boolean) {
        notificationManager.fireEventReminder(context, itIsAfterQuietHoursReminder, hasActiveAlarms);
    }

    fun cleanupEventReminder(context: Context) {
        notificationManager.cleanupEventReminder(context);
    }

    override fun onMainActivityCreate(context: Context?) {
        if (context != null) {
            val settings = getSettings(context)

            if (settings.versionCodeFirstInstalled == 0L) {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                settings.versionCodeFirstInstalled = pInfo.versionCode.toLong()
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    override fun onMainActivityStarted(context: Context?) {
    }

    override fun onMainActivityResumed(
            context: Context?,
            shouldRepost: Boolean,
            monitorSettingsChanged: Boolean
    ) {

        if (context != null) {

            cleanupEventReminder(context)

            if (shouldRepost) {
                notificationManager.postEventNotifications(context, EventFormatter(context), isRepost = true)
                context.globalState?.lastNotificationRePost = clock.currentTimeMillis()
            }

            alarmScheduler.rescheduleAlarms(context, getSettings(context), getQuietHoursManager(context))

            // this might fire new notifications
            // This would automatically launch the rescan of calendar and monitor
            this.CalendarMonitor.onAppResumed(context, monitorSettingsChanged)

            //checkAndCleanupWasHandledCache(context)
        }
    }

//    fun checkAndCleanupWasHandledCache(context: Context) {
//
//        val prState = context.persistentState
//        val now = clock.currentTimeMillis()
//
//        if (now - prState.lastWasHandledCacheCleanup < Consts.WAS_HANDLED_CACHE_CLEANUP_INTERVALS)
//            return
//
//        WasHandledCache(context).classCustomUse { it.removeOldEntries( Consts.WAS_HANDLED_CACHE_MAX_AGE_MILLIS )}
//
//        prState.lastWasHandledCacheCleanup = now
//    }

    override fun onTimeChanged(context: Context) {
        alarmScheduler.rescheduleAlarms(context, getSettings(context), getQuietHoursManager(context))
        this.CalendarMonitor.onSystemTimeChange(context)
    }

    override fun dismissEvents(
            context: Context,
            db: EventsStorageInterface,
            events: Collection<EventAlertRecord>,
            dismissType: EventDismissType,
            notifyActivity: Boolean
    ) {

        DevLog.info(LOG_TAG, "Dismissing ${events.size}  requests")

        if (dismissType.shouldKeep) {
            DismissedEventsStorage(context).classCustomUse {
                it.addEvents(dismissType, events)
            }
        }

        notificationManager.onEventsDismissing(context, events)

        if (db.deleteEvents(events) == events.size) {

            val hasActiveEvents = db.events.any { it.snoozedUntil != 0L && !it.isSpecial }

            notificationManager.onEventsDismissed(context, EventFormatter(context), events, true, hasActiveEvents);

            ReminderState(context).onUserInteraction(clock.currentTimeMillis())

            alarmScheduler.rescheduleAlarms(context, getSettings(context), getQuietHoursManager(context))

            if (notifyActivity)
                UINotifier.notify(context, true)
        }
    }

    fun anyForDismissAllButRecentAndSnoozed(events: Array<EventAlertRecord>): Boolean {

        val currentTime = clock.currentTimeMillis()

        val ret = events.any {
            event ->
            (event.lastStatusChangeTime < currentTime - Consts.DISMISS_ALL_THRESHOLD) &&
                    (event.snoozedUntil == 0L)
        }

        return ret
    }

    fun dismissAllButRecentAndSnoozed(context: Context, dismissType: EventDismissType) {

        val currentTime = clock.currentTimeMillis()

        EventsStorage(context).classCustomUse {
            db ->
            val eventsToDismiss = db.events.filter {
                event ->
                (event.lastStatusChangeTime < currentTime - Consts.DISMISS_ALL_THRESHOLD) &&
                        (event.snoozedUntil == 0L) &&
                        event.isNotSpecial
            }
            dismissEvents(context, db, eventsToDismiss, dismissType, false)
        }
    }

    fun muteAllVisibleEvents(context: Context) {

        EventsStorage(context).classCustomUse {
            db ->
            val eventsToMute = db.events.filter {
                event -> (event.snoozedUntil == 0L) && event.isNotSpecial && !event.isTask
            }

            if (eventsToMute.isNotEmpty()) {

                val mutedEvents = eventsToMute.map { it.isMuted = true; it }
                db.updateEvents(mutedEvents)

                val formatter = EventFormatter(context)
                for (mutedEvent in mutedEvents)
                    notificationManager.onEventMuteToggled(context, formatter, mutedEvent)

                ReminderState(context).onUserInteraction(clock.currentTimeMillis())

                alarmScheduler.rescheduleAlarms(context, getSettings(context), getQuietHoursManager(context))
            }
        }
    }

    fun dismissEvent(
            context: Context,
            db: EventsStorageInterface,
            event: EventAlertRecord,
            dismissType: EventDismissType,
            notifyActivity: Boolean
    ) {

        DevLog.info(LOG_TAG, "Dismissing event id ${event.eventId} / instance ${event.instanceStartTime}")

        if (dismissType.shouldKeep && event.isNotSpecial) {
            DismissedEventsStorage(context).classCustomUse {
                it.addEvent(dismissType, event)
            }
        }

        notificationManager.onEventDismissing(context, event.eventId, event.notificationId);

        if (db.deleteEvent(event.eventId, event.instanceStartTime)) {

            notificationManager.onEventDismissed(context, EventFormatter(context), event.eventId, event.notificationId);

            ReminderState(context).onUserInteraction(clock.currentTimeMillis())

            alarmScheduler.rescheduleAlarms(context, getSettings(context), getQuietHoursManager(context))

            if (notifyActivity)
                UINotifier.notify(context, true)
        }
        else {
            DevLog.error(LOG_TAG, "Failed to delete event id ${event.eventId} instance start ${event.instanceStartTime} from DB")
            DevLog.error(LOG_TAG, " -- known events / instances: ")
            for (ev in db.events) {
                DevLog.error(LOG_TAG, " -- : ${ev.eventId}, ${ev.instanceStartTime}, ${ev.alertTime}, ${ev.snoozedUntil}")
            }
        }
    }

    override fun dismissEvent(context: Context, dismissType: EventDismissType, event: EventAlertRecord) {
        EventsStorage(context).classCustomUse {
            db ->
            dismissEvent(context, db, event, dismissType, false)
        }
    }

    override fun dismissAndDeleteEvent(context: Context, dismissType: EventDismissType, event: EventAlertRecord): Boolean {
        var ret = false

        if (calendarProvider.deleteEvent(context, event.eventId)) {
            dismissEvent(context, dismissType, event)
            ret = true
        }

        return ret
    }

    @Suppress("UNUSED_PARAMETER")
    override fun dismissEvent(
            context: Context,
            dismissType: EventDismissType,
            eventId: Long,
            instanceStartTime: Long,
            notificationId: Int,
            notifyActivity: Boolean
    ) {

        EventsStorage(context).classCustomUse {
            db ->
            val event = db.getEvent(eventId, instanceStartTime)
            if (event != null) {
                DevLog.info(LOG_TAG, "Dismissing event ${event.eventId} / ${event.instanceStartTime}")
                dismissEvent(context, db, event, dismissType, notifyActivity)
            }
            else {
                DevLog.error(LOG_TAG, "dismissEvent: can't find event $eventId, $instanceStartTime")

                DevLog.error(LOG_TAG, " -- known events / instances: ")
                for (ev in db.events) {
                    DevLog.error(LOG_TAG, " -- : ${ev.eventId}, ${ev.instanceStartTime}, ${ev.alertTime}, ${ev.snoozedUntil}")
                }
            }
        }
    }

    override fun restoreEvent(context: Context, event: EventAlertRecord) {
        // Get backup info for the original calendar
        val calendarBackupInfo = calendarProvider.getCalendarBackupInfo(context, event.calendarId)
        
        // Find matching calendar on the new device
        val newCalendarId = calendarBackupInfo?.let { backupInfo ->
            calendarProvider.findMatchingCalendarId(context, backupInfo)
        } ?: event.calendarId // Fallback to original ID if no match found
        
        DevLog.info(LOG_TAG, "Restoring event ${event.eventId}: original calendar ${event.calendarId}, matched calendar $newCalendarId")
        
        // Create restored event with updated calendar ID
        val toRestore = event.copy(
            notificationId = 0, // re-assign new notification ID since old one might already in use
            displayStatus = EventDisplayStatus.Hidden, // ensure correct visibility is set
            calendarId = newCalendarId // Update to new calendar ID
        )

        val successOnAdd = EventsStorage(context).classCustomUse { db ->
            val ret = db.addEvent(toRestore)
            calendarReloadManager.reloadSingleEvent(context, db, toRestore, calendarProvider, null)
            ret
        }

        if (successOnAdd) {
            notificationManager.onEventRestored(context, EventFormatter(context), toRestore)

            DismissedEventsStorage(context).classCustomUse { db ->
                db.deleteEvent(event)
            }
            
            DevLog.info(LOG_TAG, "Successfully restored event ${event.eventId} to calendar $newCalendarId")
        } else {
            DevLog.error(LOG_TAG, "Failed to restore event ${event.eventId}")
        }
    }

    override fun moveEvent(context: Context, event: EventAlertRecord, addTime: Long): Boolean {

        val moved = calendarChangeManager.moveEvent(context, event, addTime)

        if (moved) {
            DevLog.info(LOG_TAG, "moveEvent: Moved event ${event.eventId} by ${addTime / 1000L} seconds")

            EventsStorage(context).classCustomUse {
                db ->
                dismissEvent(
                        context,
                        db,
                        event,
                        EventDismissType.EventMovedUsingApp,
                        true
                )
            }
        }

        return moved
    }

    override fun moveAsCopy(context: Context, calendar: CalendarRecord, event: EventAlertRecord, addTime: Long): Long {

        val eventId = calendarChangeManager.moveRepeatingAsCopy(context, calendar, event, addTime)

        if (eventId != -1L) {
            DevLog.debug(LOG_TAG, "Event created: id=${eventId}")

            EventsStorage(context).classCustomUse {
                db ->
                dismissEvent(
                        context,
                        db,
                        event,
                        EventDismissType.EventMovedUsingApp,
                        true
                )
            }

        } else {
            DevLog.error(LOG_TAG, "Failed to create event")
        }

        return eventId
    }

    // used for debug purpose
    @Suppress("unused")
    override fun forceRepostNotifications(context: Context) {
        notificationManager.postEventNotifications(context, EventFormatter(context), isRepost = true)
    }

    // used for debug purpose
    @Suppress("unused")
    override fun postNotificationsAutoDismissedDebugMessage(context: Context) {
        notificationManager.postNotificationsAutoDismissedDebugMessage(context)
    }

    override fun postNearlyMissedNotificationDebugMessage(context: Context) {
        notificationManager.postNearlyMissedNotificationDebugMessage(context)
    }

    override fun isCustomQuietHoursActive(ctx: Context): Boolean {
        return getQuietHoursManager(ctx).isCustomQuietHoursActive(getSettings(ctx))
    }

    /// Set quietForSeconds to 0 to disable
    override fun applyCustomQuietHoursForSeconds(ctx: Context, quietForSeconds: Int) {

        val settings = getSettings(ctx)
        val quietHoursManager = getQuietHoursManager(ctx)

        if (quietForSeconds > 0) {
            quietHoursManager.startManualQuietPeriod(
                    settings,
                    clock.currentTimeMillis() + quietForSeconds*1000L
            )
            alarmScheduler.rescheduleAlarms(ctx, getSettings(ctx), quietHoursManager)
        } else {
            quietHoursManager.stopManualQuietPeriod(settings)
            alarmScheduler.rescheduleAlarms(ctx, getSettings(ctx), quietHoursManager)
        }
    }

    override fun onReminderAlarmLate(context: Context, currentTime: Long, alarmWasExpectedAt: Long) {

//        if (getSettings(context).debugAlarmDelays) {
//
            val warningMessage = "Expected: $alarmWasExpectedAt, " +
                    "received: $currentTime, ${(currentTime - alarmWasExpectedAt) / 1000L}s late"
            DevLog.error(LOG_TAG, "Late reminders alarm detected: $warningMessage")
//
//            notificationManager.postNotificationsAlarmDelayDebugMessage(context, "Reminder alarm was late!", warningMessage)
//        }
    }

    override fun onSnoozeAlarmLate(context: Context, currentTime: Long, alarmWasExpectedAt: Long) {

//        if (getSettings(context).debugAlarmDelays) {
//
            val warningMessage = "Expected:   $alarmWasExpectedAt, " +
                    "received: $currentTime, ${(currentTime - alarmWasExpectedAt) / 1000L}s late"

            DevLog.error(LOG_TAG, "Late snooze alarm detected: $warningMessage")

//            notificationManager.postNotificationsSnoozeAlarmDelayDebugMessage(context, "Snooze alarm was late!", warningMessage)
//        }
    }
}
