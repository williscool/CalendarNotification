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

package com.github.quarck.calnotify.ui

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.ToggleButton
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.CalendarEventDetails
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.EventAlertFlags
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventReminderRecord
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusSystemClock
import com.github.quarck.calnotify.utils.findOrThrow
import com.github.quarck.calnotify.utils.toLongOrNull
import java.util.*


// TODO: add current UTC offset into the log

// TODO: add event button (off by default)

// TODO: option to run rescan service in foreground (could be helpful for ongoing android versions, off by default)
//       ...see Android O background limitations: https://github.com/quarck/CalendarNotification/issues/220

// TODO: help and feedback: add notice about possible delay in responce and mention that app is non-commercial 
// and etc..

class TestActivity : Activity() {

    private val settings by lazy { Settings(this) }

    val clock: CNPlusClockInterface = CNPlusSystemClock()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        findOrThrow<TextView>(R.id.todo).visibility = View.VISIBLE;
        //findOrThrow<ToggleButton>(R.id.buttonTestToggleDebugMonitor).isChecked = settings.enableMonitorDebug
    }


    fun randomTitle(currentTime: Long): String {

        val wikipediaQuote =
                """ Some predictions of general relativity differ significantly from those of classical
            physics, especially concerning the passage of time, the geometry of space, the motion of
            bodies in free fall, and the propagation of light. Examples of such differences include
            gravitational time dilation, gravitational lensing, the gravitational redshift of light,
            and the gravitational time delay. The predictions of general relativity have been
            confirmed in all observations and experiments to date. Although general relativity is
            not the only relativistic theory of gravity, it is the simplest theory that is consistent
            with experimental data. However, unanswered questions remain, the most fundamental being
            how general relativity can be reconciled with the laws of quantum physics to produce a
            complete and self-consistent theory of quantum gravity."""

        val dict =
                wikipediaQuote.split(' ', '\r', '\n', '\t').filter { !it.isEmpty() }

        val sb = StringBuilder();
        val r = Random(currentTime);

        val len = r.nextInt(10);

        var prev = -1;
        for (i in 0..len) {
            var new: Int
            do {
                new = r.nextInt(dict.size)
            } while (new == prev)
            sb.append(dict[new])
            sb.append(" ")
            prev = new
        }

        return sb.toString();
    }

    private var cnt = 0;

    private val filterText: String
        get() = findOrThrow<EditText>(R.id.edittext_debug_event_id).text.toString()

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonViewClick(v: View) {
        val id = filterText.toLongOrNull()
        if (id != null) {
            startActivity(
                    Intent(Intent.ACTION_VIEW).setData(
                            ContentUris.withAppendedId(
                                    CalendarContract.Events.CONTENT_URI,
                                    id)))
        }
    }

    fun clr(r: Int, g: Int, b: Int) = 0xff.shl(24) or r.shl(16) or g.shl(8) or b

    fun addDemoEvent(
            currentTime: Long, eventId: Long, title: String,
            minutesFromMidnight: Long, duration: Long, location: String,
            color: Int, allDay: Boolean) {

        val nextDay = ((currentTime / (Consts.DAY_IN_SECONDS * 1000L)) + 1) * (Consts.DAY_IN_SECONDS * 1000L)

        val start = nextDay + minutesFromMidnight * 60L * 1000L
        val end = start + duration * 60L * 1000L

        val event = EventAlertRecord(
                -1L,
                eventId,
                allDay,
                false,
                currentTime,
                0,
                title,
                "",
                start,
                end,
                start,
                end,
                location,
                currentTime,
                0L, // snoozed
                EventDisplayStatus.Hidden,
                color
        )
        ApplicationController.postEventNotifications(this, listOf(event))
        ApplicationController.registerNewEvent(this, event);

        ApplicationController.afterCalendarEventFired(this)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonStrEvClick(v: View) {

        var currentTime = clock.currentTimeMillis()
        var eventId = currentTime
        addDemoEvent(currentTime, eventId, "Publish new version to play store", 18 * 60L, 30L, "", -11958553, false)
        eventId++
        currentTime += 10
        addDemoEvent(currentTime, eventId, "Take laptop to work", 6 * 60L, 15L, "", -11958553, false)
        eventId++
        currentTime += 10
        addDemoEvent(currentTime, eventId, "Holidays in Spain", (4 * 24 + 8) * 60L, 7 * 24 * 60L, "Costa Dorada Salou", -18312, true)
        eventId++
        currentTime += 10
        addDemoEvent(currentTime, eventId, "Meeting with John", (15 * 24 + 8) * 60L, 15L, "", -11958553, false)
        eventId++
        currentTime += 10
        addDemoEvent(currentTime, eventId, "Check for new documentation releases", 8 * 60L, 15L, "", -11958553, false)
        eventId++
        currentTime += 10
        addDemoEvent(currentTime, eventId, "Call parents", 12 * 60L, 15L, "", -11958553, false)
        eventId++
        currentTime += 10
        addDemoEvent(currentTime, eventId, "Submit VHI claim", 19 * 60L, 15L, "", -2380289, false)
        eventId++
        currentTime += 10
        addDemoEvent(currentTime, eventId, "Charge phone!", 23 * 60L, 15L, "", -11958553, false)
        eventId++
        currentTime += 10
        addDemoEvent(currentTime, eventId, "Take vitamin", 13 * 60L, 15L, "", -2380289, false)
        eventId++
        currentTime += 10
        addDemoEvent(currentTime, eventId, "Collect parcel", 15 * 60L, 15L, "GPO Post Office", -18312, false)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonAddRandomEventClick(v: View) {

        val currentTime = clock.currentTimeMillis();

        val eventId = 10000000L + (currentTime % 1000L)

        val event = EventAlertRecord(
                -1L,
                eventId,
                false,
                false,
                clock.currentTimeMillis(),
                0,
                randomTitle(currentTime) + " " + ((currentTime / 100) % 10000).toString(),
                "",
                currentTime + 3600L * 1000L,
                currentTime + 2 * 3600L * 1000L,
                currentTime + 3600L * 1000L,
                currentTime + 2 * 3600L * 1000L,
                if ((cnt % 2) == 0) "" else "Hawthorne, California, U.S.",
                clock.currentTimeMillis(),
                0L,
                EventDisplayStatus.Hidden,
                0xff660066.toInt()
        )

        cnt++;

        ApplicationController.registerNewEvent(this, event)
        ApplicationController.postEventNotifications(this, listOf(event))
        ApplicationController.afterCalendarEventFired(this)
    }

    /**
     * Add upcoming events to MonitorStorage for testing the Upcoming tab.
     * Creates real calendar events first, then adds alerts for them.
     */
    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonAddUpcomingEventsClick(v: View) {
        val LOG_TAG = "TestActivity"
        
        val calendars = CalendarProvider.getCalendars(this)
        val calendar = calendars.firstOrNull()
        
        if (calendar == null) {
            DevLog.error(LOG_TAG, "No calendars available - cannot create upcoming events")
            return
        }
        
        val currentTime = clock.currentTimeMillis()
        val entries = mutableListOf<MonitorEventAlertEntry>()
        
        // Create upcoming events with alerts in the future
        // Config: title, alertInMinutes, durationMinutes, color
        val upcomingConfigs = listOf(
            arrayOf("Upcoming Meeting", 30, 60, 0xff4285f4.toInt()),     // Alert in 30 min
            arrayOf("Call with Client", 60, 30, 0xffea4335.toInt()),    // Alert in 1 hour
            arrayOf("Code Review", 120, 45, 0xff34a853.toInt()),        // Alert in 2 hours  
            arrayOf("Team Standup", 180, 60, 0xfffbbc04.toInt()),       // Alert in 3 hours
            arrayOf("Lunch Break", 240, 90, 0xffff6d01.toInt()),        // Alert in 4 hours
            arrayOf("Project Planning", 360, 60, 0xff46bdc6.toInt()),   // Alert in 6 hours
        )
        
        for (config in upcomingConfigs) {
            val title = config[0] as String
            val alertInMinutes = config[1] as Int
            val durationMinutes = config[2] as Int
            val color = config[3] as Int
            
            val alertTime = currentTime + alertInMinutes * 60 * 1000L
            val instanceStart = alertTime + 15 * 60 * 1000L  // Event starts 15min after alert
            val instanceEnd = instanceStart + durationMinutes * 60 * 1000L
            
            // Create real calendar event
            val details = CalendarEventDetails(
                title = title,
                desc = "Test upcoming event",
                location = "",
                timezone = java.util.TimeZone.getDefault().id,
                startTime = instanceStart,
                endTime = instanceEnd,
                isAllDay = false,
                reminders = listOf(EventReminderRecord.minutes(15)),
                color = color
            )
            
            val eventId = CalendarProvider.createEvent(this, calendar.calendarId, calendar.owner, details)
            if (eventId == -1L) {
                DevLog.error(LOG_TAG, "Failed to create calendar event: $title")
                continue
            }
            
            // Add alert to MonitorStorage
            val entry = MonitorEventAlertEntry(
                eventId = eventId,
                isAllDay = false,
                alertTime = alertTime,
                instanceStartTime = instanceStart,
                instanceEndTime = instanceEnd,
                alertCreatedByUs = false,
                wasHandled = false,
                flags = 0
            )
            entries.add(entry)
            DevLog.info(LOG_TAG, "Created event $eventId: $title (alert in ${alertInMinutes}m)")
        }
        
        // Add to MonitorStorage
        if (entries.isNotEmpty()) {
            MonitorStorage(this).use { storage ->
                storage.addAlerts(entries)
                DevLog.info(LOG_TAG, "Added ${entries.size} upcoming events to MonitorStorage")
            }
        }
        
        DevLog.info(LOG_TAG, "✅ Created ${entries.size} upcoming events! Check the Upcoming tab.")
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonAddReminderInEventClick(v: View) {
        val LOG_TAG = "TestActivity"
        
        // Enable the displayNextGCalReminder setting (default is true but be explicit)
        settings.displayNextGCalReminder = true
        settings.displayNextAppAlert = false
        DevLog.info(LOG_TAG, "Enabled displayNextGCalReminder setting")
        
        // Find a calendar to use
        val calendars = CalendarProvider.getCalendars(this)
        val calendar = calendars.firstOrNull()
        
        if (calendar == null) {
            DevLog.error(LOG_TAG, "No calendars available - cannot create test event")
            return
        }
        
        val currentTime = clock.currentTimeMillis()
        // Event starts 2 hours from now
        val eventStart = currentTime + 2 * Consts.HOUR_IN_MILLISECONDS
        val eventEnd = eventStart + Consts.HOUR_IN_MILLISECONDS
        
        // Create reminders at 15min, 30min, and 60min before event
        // These will be at 1h45m, 1h30m, and 1h from now respectively
        val reminders = listOf(
            EventReminderRecord.minutes(15),  // fires 1h45m from now
            EventReminderRecord.minutes(30),  // fires 1h30m from now  
            EventReminderRecord.minutes(60)   // fires 1h from now
        )
        
        val details = CalendarEventDetails(
            title = "Test Reminder In Feature - ${System.currentTimeMillis() % 10000}",
            desc = "This event tests the next notification indicator feature",
            location = "",
            timezone = java.util.TimeZone.getDefault().id,
            startTime = eventStart,
            endTime = eventEnd,
            isAllDay = false,
            reminders = reminders,
            color = 0xff00aa00.toInt()
        )
        
        val eventId = CalendarProvider.createEvent(this, calendar.calendarId, calendar.owner, details)
        
        if (eventId == -1L) {
            DevLog.error(LOG_TAG, "Failed to create calendar event")
            return
        }
        
        DevLog.info(LOG_TAG, "Created calendar event $eventId with ${reminders.size} reminders")
        
        // Create and post a notification for this event
        val event = EventAlertRecord(
            calendarId = calendar.calendarId,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = currentTime,
            notificationId = 0,
            title = details.title,
            desc = details.desc,
            startTime = eventStart,
            endTime = eventEnd,
            instanceStartTime = eventStart,
            instanceEndTime = eventEnd,
            location = details.location,
            lastStatusChangeTime = currentTime,
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = details.color
        )
        
        ApplicationController.registerNewEvent(this, event)
        ApplicationController.postEventNotifications(this, listOf(event))
        ApplicationController.afterCalendarEventFired(this)
        
        DevLog.info(LOG_TAG, "Posted notification - look for '📅 in X' in the notification text!")
    }
    
    /**
     * Test collapsed notifications with next alert indicator.
     * Creates 10 events to trigger collapsed view (exceeds maxNotifications).
     */
    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonTestCollapsedNextAlertClick(v: View) {
        val LOG_TAG = "TestActivity"
        
        // Enable settings
        settings.displayNextGCalReminder = true
        settings.displayNextAppAlert = false
        // Note: collapseEverything is read-only, so we create enough events to naturally trigger collapse
        DevLog.info(LOG_TAG, "Testing collapsed notifications with next alert indicator")
        
        val calendars = CalendarProvider.getCalendars(this)
        val calendar = calendars.firstOrNull()
        
        if (calendar == null) {
            DevLog.error(LOG_TAG, "No calendars available - cannot create test events")
            return
        }
        
        val currentTime = clock.currentTimeMillis()
        val events = mutableListOf<EventAlertRecord>()
        
        // Create 10 events to exceed maxNotifications and trigger collapse
        // Event 1: reminder in 30m (soonest)
        // Events 2-10: various reminder times
        val eventConfigs = listOf(
            Triple("Event 1 (30m reminder)", 2 * Consts.HOUR_IN_MILLISECONDS, listOf(EventReminderRecord.minutes(90))),  // fires in 30m
            Triple("Event 2 (1h reminder)", 3 * Consts.HOUR_IN_MILLISECONDS, listOf(EventReminderRecord.minutes(120))),  // fires in 1h
            Triple("Event 3 (2h reminder)", 4 * Consts.HOUR_IN_MILLISECONDS, listOf(EventReminderRecord.minutes(120))),  // fires in 2h
            Triple("Event 4 (3h reminder)", 5 * Consts.HOUR_IN_MILLISECONDS, listOf(EventReminderRecord.minutes(120))),  // fires in 3h
            Triple("Event 5", 6 * Consts.HOUR_IN_MILLISECONDS, listOf(EventReminderRecord.minutes(120))),
            Triple("Event 6", 7 * Consts.HOUR_IN_MILLISECONDS, listOf(EventReminderRecord.minutes(120))),
            Triple("Event 7", 8 * Consts.HOUR_IN_MILLISECONDS, listOf(EventReminderRecord.minutes(120))),
            Triple("Event 8", 9 * Consts.HOUR_IN_MILLISECONDS, listOf(EventReminderRecord.minutes(120))),
            Triple("Event 9", 10 * Consts.HOUR_IN_MILLISECONDS, listOf(EventReminderRecord.minutes(120))),
            Triple("Event 10 (no future reminder)", -Consts.HOUR_IN_MILLISECONDS, listOf(EventReminderRecord.minutes(15)))  // started 1h ago, reminder already fired
        )
        
        for ((title, startOffset, reminders) in eventConfigs) {
            val eventStart = currentTime + startOffset
            val eventEnd = eventStart + Consts.HOUR_IN_MILLISECONDS
            
            val details = CalendarEventDetails(
                title = "$title - ${System.currentTimeMillis() % 10000}",
                desc = "Collapsed notification test",
                location = "",
                timezone = java.util.TimeZone.getDefault().id,
                startTime = eventStart,
                endTime = eventEnd,
                isAllDay = false,
                reminders = reminders,
                color = 0xff0066aa.toInt()
            )
            
            val eventId = CalendarProvider.createEvent(this, calendar.calendarId, calendar.owner, details)
            
            if (eventId != -1L) {
                val event = EventAlertRecord(
                    calendarId = calendar.calendarId,
                    eventId = eventId,
                    isAllDay = false,
                    isRepeating = false,
                    alertTime = currentTime,
                    notificationId = 0,
                    title = details.title,
                    desc = details.desc,
                    startTime = eventStart,
                    endTime = eventEnd,
                    instanceStartTime = eventStart,
                    instanceEndTime = eventEnd,
                    location = details.location,
                    lastStatusChangeTime = currentTime,
                    snoozedUntil = 0L,
                    displayStatus = EventDisplayStatus.Hidden,
                    color = details.color
                )
                events.add(event)
                ApplicationController.registerNewEvent(this, event)
            }
        }
        
        ApplicationController.postEventNotifications(this, events)
        ApplicationController.afterCalendarEventFired(this)
        
        DevLog.info(LOG_TAG, "Posted ${events.size} collapsed notifications - look for '(📅 30m)' in the title!")
    }
    
    /**
     * Test muted event with next alert indicator.
     * Creates a muted event to show the 🔇 prefix.
     */
    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonTestMutedNextAlertClick(v: View) {
        val LOG_TAG = "TestActivity"
        
        settings.displayNextGCalReminder = true
        settings.displayNextAppAlert = false
        DevLog.info(LOG_TAG, "Testing muted event with next alert indicator")
        
        val calendars = CalendarProvider.getCalendars(this)
        val calendar = calendars.firstOrNull()
        
        if (calendar == null) {
            DevLog.error(LOG_TAG, "No calendars available - cannot create test event")
            return
        }
        
        val currentTime = clock.currentTimeMillis()
        val eventStart = currentTime + 2 * Consts.HOUR_IN_MILLISECONDS
        val eventEnd = eventStart + Consts.HOUR_IN_MILLISECONDS
        
        val reminders = listOf(EventReminderRecord.minutes(60))  // fires in 1h
        
        val details = CalendarEventDetails(
            title = "Muted Test Event - ${System.currentTimeMillis() % 10000}",
            desc = "This event is muted - should show 🔇 📅 in X",
            location = "",
            timezone = java.util.TimeZone.getDefault().id,
            startTime = eventStart,
            endTime = eventEnd,
            isAllDay = false,
            reminders = reminders,
            color = 0xffaa0000.toInt()
        )
        
        val eventId = CalendarProvider.createEvent(this, calendar.calendarId, calendar.owner, details)
        
        if (eventId == -1L) {
            DevLog.error(LOG_TAG, "Failed to create calendar event")
            return
        }
        
        // Create muted event
        val event = EventAlertRecord(
            calendarId = calendar.calendarId,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = currentTime,
            notificationId = 0,
            title = details.title,
            desc = details.desc,
            startTime = eventStart,
            endTime = eventEnd,
            instanceStartTime = eventStart,
            instanceEndTime = eventEnd,
            location = details.location,
            lastStatusChangeTime = currentTime,
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = details.color,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = currentTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = EventAlertFlags.IS_MUTED
        )
        
        ApplicationController.registerNewEvent(this, event)
        ApplicationController.postEventNotifications(this, listOf(event))
        ApplicationController.afterCalendarEventFired(this)
        
        DevLog.info(LOG_TAG, "Posted muted notification - look for '🔇 📅 in X' in the notification text!")
    }

    /**
     * Test app alert indicator (🔔).
     * Creates an event with NO future GCal reminders so only the app alert shows.
     * Requires reminders to be enabled in settings.
     */
    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonTestAppAlertClick(v: View) {
        val LOG_TAG = "TestActivity"
        
        // Enable app alert, disable GCal reminder
        settings.displayNextGCalReminder = false
        settings.displayNextAppAlert = true
        DevLog.info(LOG_TAG, "Testing app alert indicator (🔔)")
        
        if (!settings.remindersEnabled) {
            DevLog.warn(LOG_TAG, "Reminders are disabled - enable them in settings to see app alert!")
        }
        
        val calendars = CalendarProvider.getCalendars(this)
        val calendar = calendars.firstOrNull()
        
        if (calendar == null) {
            DevLog.error(LOG_TAG, "No calendars available - cannot create test event")
            return
        }
        
        val currentTime = clock.currentTimeMillis()
        // Event started 1 hour ago (so no future GCal reminders)
        val eventStart = currentTime - Consts.HOUR_IN_MILLISECONDS
        val eventEnd = eventStart + Consts.HOUR_IN_MILLISECONDS
        
        // No reminders - the event has already started
        val reminders = listOf<EventReminderRecord>()
        
        val details = CalendarEventDetails(
            title = "App Alert Test - ${System.currentTimeMillis() % 10000}",
            desc = "This event shows the app alert indicator (🔔). Reminders must be enabled!",
            location = "",
            timezone = java.util.TimeZone.getDefault().id,
            startTime = eventStart,
            endTime = eventEnd,
            isAllDay = false,
            reminders = reminders,
            color = 0xff0066cc.toInt()
        )
        
        val eventId = CalendarProvider.createEvent(this, calendar.calendarId, calendar.owner, details)
        
        if (eventId == -1L) {
            DevLog.error(LOG_TAG, "Failed to create calendar event")
            return
        }
        
        val event = EventAlertRecord(
            calendarId = calendar.calendarId,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = currentTime,
            notificationId = 0,
            title = details.title,
            desc = details.desc,
            startTime = eventStart,
            endTime = eventEnd,
            instanceStartTime = eventStart,
            instanceEndTime = eventEnd,
            location = details.location,
            lastStatusChangeTime = currentTime,
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = details.color
        )
        
        ApplicationController.registerNewEvent(this, event)
        ApplicationController.postEventNotifications(this, listOf(event))
        ApplicationController.afterCalendarEventFired(this)
        
        DevLog.info(LOG_TAG, "Posted notification - look for '(🔔 Xm)' based on reminder interval!")
    }

    /**
     * TEST REMINDER SOUND ON COLLAPSED EVENTS
     * 
     * This tests the bug fix where reminders on collapsed events wouldn't play sound.
     * 
     * Steps:
     * 1. First click "TEST NEXT ALERT (COLLAPSED)" to create collapsed events
     * 2. Then click THIS button to trigger a reminder
     * 3. Sound SHOULD play (after the fix)
     * 
     * Before the fix, the displayStatus=DisplayedCollapsed check would skip the
     * sound logic when force=false (the reminder path).
     */
    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonTestReminderOnCollapsedClick(v: View) {
        val LOG_TAG = "TestActivity"
        
        DevLog.info(LOG_TAG, "=== TESTING REMINDER SOUND ON COLLAPSED EVENTS ===")
        
        // Check if there are any active events
        if (!ApplicationController.hasActiveEventsToRemind(this)) {
            DevLog.warn(LOG_TAG, "No active events! First click 'TEST NEXT ALERT (COLLAPSED)' to create some")
            return
        }
        
        // Fire the reminder - this calls the same code path as ReminderAlarmBroadcastReceiver
        DevLog.info(LOG_TAG, "Firing reminder (itIsAfterQuietHoursReminder=false, hasActiveAlarms=false)")
        ApplicationController.fireEventReminder(this, itIsAfterQuietHoursReminder = false, hasActiveAlarms = false)
        
        DevLog.info(LOG_TAG, "Reminder fired! Sound should have played if the fix is working correctly.")
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonAddProvierEventClick(v: View) {

        startActivity(Intent(this, EditEventActivity::class.java))

//        val currentTime = clock.currentTimeMillis()
//
//        val cal = CalendarProvider.getCalendars(this).filter { it.isPrimary }.firstOrNull()
//
//        if (cal != null) {
//            val id = CalendarProvider.createTestEvent(this,
//                    CalendarProvider.getCalendars(this)[2].calendarId,
//                    randomTitle(currentTime),
//                    currentTime + 3600000L, currentTime + 7200000L, 15000L)

//            startActivity(
//                    Intent(Intent.ACTION_VIEW).setData(
//                            ContentUris.withAppendedId(
//                                    CalendarContract.Events.CONTENT_URI,
//                                    id)))



//        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonToggleRemoveClick(v: View) {
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonToggleAutoDismissDebugClick(v: View) {
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonToggleAlarmDelayDebugClick(v: View) {
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonToggleDebugMonitorClick(v: View) {
        //settings.enableMonitorDebug = findOrThrow<ToggleButton>(R.id.buttonTestToggleDebugMonitor).isChecked
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonDisableDevPage(v: View) {
        Settings(this).devModeEnabled = false
    }
}
