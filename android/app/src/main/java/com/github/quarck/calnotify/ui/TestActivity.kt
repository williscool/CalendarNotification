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
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.utils.findOrThrow
import com.github.quarck.calnotify.utils.toLongOrNull
import java.util.*
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.widget.Toast
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusSystemClock
import com.github.quarck.calnotify.utils.find


// TODO: add current UTC offset into the log

// TODO: add event button (off by default)

// TODO: option to run rescan service in foreground (could be helpful for ongoing android versions, off by default)
//       ...see Android O background limitations: https://github.com/quarck/CalendarNotification/issues/220

// TODO: help and feedback: add notice about possible delay in responce and mention that app is non-commercial 
// and etc..

class TestActivity : AppCompatActivity() {

    private val settings by lazy { Settings(this) }
    private var cnt: Int = 0;
    private var easterEggCount = 0;
    private var firstClick = 0L;
    
    // Use the system clock for the test activity
    private val clock: CNPlusClockInterface = CNPlusSystemClock()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        findOrThrow<TextView>(R.id.todo).visibility = View.VISIBLE;
        //findOrThrow<ToggleButton>(R.id.buttonTestToggleDebugMonitor).isChecked = settings.enableMonitorDebug

        setSupportActionBar(find<Toolbar?>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        DevLog.debug(LOG_TAG, "onCreate")
    }

    override fun onResume() {
        super.onResume()
    }

    fun randomTitle(seed: Long): String {
        val Random = Random(seed)

        val words = this.resources.getStringArray(R.array.test_words)
        val size = Random.nextInt(5) + 1

        val ret = StringBuilder("")

        for (i in 0 until size) {
            if (i > 0)
                ret.append(' ')
            ret.append(words[Random.nextInt(words.size)])
        }

        return ret.toString()
    }

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

    fun addDemoEvent(createdAt: Long, eventId: Long, title: String, minutes: Long, alertMinutesBefore: Long, location: String, color: Int, isReminders: Boolean)
    {
        val startTime = createdAt + ((minutes * 60 - alertMinutesBefore) * 1000)
        val alertTimeTime = startTime - alertMinutesBefore * 60 * 1000
        val endTime = startTime + 30 * 60 * 1000

        val event = EventAlertRecord(
                0x0fffL,
                eventId,
                false,
                isReminders,
                alertTimeTime,
                0,
                title,
                "",
                startTime,
                endTime,
                startTime,
                endTime,
                location,
                clock.currentTimeMillis(),
                0L,
                EventDisplayStatus.Hidden,
                color,
                flags = 0L
        )

        ApplicationController.registerNewEvent(this, event)
        ApplicationController.postEventNotifications(this, listOf(event))
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

        val currentTime = clock.currentTimeMillis()

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

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonAddProvierEventClick(v: View) {

        startActivity(Intent(this, EditEventActivity::class.java))

        //val currentTime = clock.currentTimeMillis()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonEasterEgg(v: View) {
        if (easterEggCount == 0) {
            firstClick = clock.currentTimeMillis()
        }

        if (++easterEggCount > 13) {
            if (clock.currentTimeMillis() - firstClick < 5000L) {
                Settings(this).devModeEnabled = true
                Toast.makeText(this, "Developer Mode Enabled", Toast.LENGTH_LONG).show()
            }
            else {
                easterEggCount = 0;
                firstClick = 0L;
            }
        }
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
    
    companion object {
        private const val LOG_TAG = "TestActivity"
    }
}
