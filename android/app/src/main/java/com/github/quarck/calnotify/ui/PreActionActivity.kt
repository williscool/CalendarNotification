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

package com.github.quarck.calnotify.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.CalendarIntents
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import com.github.quarck.calnotify.prefs.PreferenceUtils
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.textutils.EventFormatterInterface
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusSystemClock
import com.github.quarck.calnotify.utils.background

/**
 * Activity for pre-actions on upcoming events.
 * 
 * Provides UI for:
 * - Pre-snooze: Snooze event before its notification fires
 * - Pre-mute: Mark event to fire silently
 * - Pre-dismiss: (Future) Dismiss event before it fires
 * 
 * When a snooze option is selected:
 * 1. Marks alert as handled in MonitorStorage
 * 2. Adds event to EventsStorage with snoozedUntil set
 * 3. Reschedules alarms
 */
class PreActionActivity : AppCompatActivity() {

    companion object {
        private const val LOG_TAG = "PreActionActivity"
        
        // For DI in tests
        var clockProvider: (() -> CNPlusClockInterface)? = null
        
        private fun getClock(): CNPlusClockInterface =
            clockProvider?.invoke() ?: CNPlusSystemClock()
        
        /**
         * Creates an intent to launch PreActionActivity for the given event.
         */
        fun createIntent(context: Context, event: EventAlertRecord): Intent {
            return Intent(context, PreActionActivity::class.java).apply {
                putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                putExtra(Consts.INTENT_ALERT_TIME_KEY, event.alertTime)
                putExtra(Consts.INTENT_EVENT_TITLE_KEY, event.title)
                putExtra(Consts.INTENT_EVENT_START_TIME_KEY, event.startTime)
                putExtra(Consts.INTENT_EVENT_END_TIME_KEY, event.endTime)
                putExtra(Consts.INTENT_EVENT_ALL_DAY_KEY, event.isAllDay)
                putExtra(Consts.INTENT_EVENT_LOCATION_KEY, event.location)
                putExtra(Consts.INTENT_EVENT_COLOR_KEY, event.color)
                putExtra(Consts.INTENT_EVENT_IS_MUTED_KEY, event.isMuted)
            }
        }
    }

    private lateinit var settings: Settings
    private lateinit var formatter: EventFormatterInterface
    
    // Event data from intent
    private var eventId: Long = -1L
    private var instanceStartTime: Long = -1L
    private var alertTime: Long = -1L
    private var eventTitle: String = ""
    private var eventStartTime: Long = 0L
    private var eventEndTime: Long = 0L
    private var eventAllDay: Boolean = false
    private var eventLocation: String = ""
    private var eventColor: Int = 0
    private var eventIsMuted: Boolean = false
    
    private lateinit var snoozePresets: LongArray

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pre_action)
        
        settings = Settings(this)
        formatter = EventFormatter(this)
        
        // Load event data from intent
        eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1L)
        instanceStartTime = intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1L)
        alertTime = intent.getLongExtra(Consts.INTENT_ALERT_TIME_KEY, -1L)
        eventTitle = intent.getStringExtra(Consts.INTENT_EVENT_TITLE_KEY) ?: ""
        eventStartTime = intent.getLongExtra(Consts.INTENT_EVENT_START_TIME_KEY, 0L)
        eventEndTime = intent.getLongExtra(Consts.INTENT_EVENT_END_TIME_KEY, 0L)
        eventAllDay = intent.getBooleanExtra(Consts.INTENT_EVENT_ALL_DAY_KEY, false)
        eventLocation = intent.getStringExtra(Consts.INTENT_EVENT_LOCATION_KEY) ?: ""
        eventColor = intent.getIntExtra(Consts.INTENT_EVENT_COLOR_KEY, 0)
        eventIsMuted = intent.getBooleanExtra(Consts.INTENT_EVENT_IS_MUTED_KEY, false)
        
        if (eventId == -1L || instanceStartTime == -1L || alertTime == -1L) {
            DevLog.error(LOG_TAG, "PreActionActivity started with invalid event data")
            finish()
            return
        }
        
        setupUI()
    }
    
    private fun setupUI() {
        // Back button
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        
        // Header color
        val header = findViewById<View>(R.id.pre_action_header)
        if (eventColor != 0) {
            header.setBackgroundColor(eventColor or 0xFF000000.toInt())
        }
        
        // Event title
        val titleView = findViewById<TextView>(R.id.pre_action_title)
        titleView.text = eventTitle.ifEmpty { getString(R.string.empty_title) }
        
        // Event time - create a temporary record for formatting
        val tempRecord = createEventRecord(0, 0)  // snoozeUntil/currentTime don't affect formatting
        val dateLine1 = findViewById<TextView>(R.id.pre_action_date_line1)
        val dateLine2 = findViewById<TextView>(R.id.pre_action_date_line2)
        val alertTimeView = findViewById<TextView>(R.id.pre_action_alert_time)
        
        val (line1, line2) = formatter.formatDateTimeTwoLines(tempRecord)
        dateLine1.text = line1
        if (line2.isEmpty()) {
            dateLine2.visibility = View.GONE
        } else {
            dateLine2.text = line2
        }
        
        // Show when alert will fire
        alertTimeView.text = getString(R.string.alert_fires_at, formatter.formatTimePoint(alertTime))
        
        // Setup snooze presets
        setupSnoozePresets()
        
        // Custom snooze
        findViewById<TextView>(R.id.pre_action_custom).setOnClickListener {
            showCustomSnoozeDialog()
        }
        
        // Mute toggle
        updateMuteButton()
        findViewById<TextView>(R.id.pre_action_mute_toggle).setOnClickListener {
            toggleMute()
        }
        
        // View in calendar
        findViewById<TextView>(R.id.pre_action_view_calendar).setOnClickListener {
            viewInCalendar()
        }
    }
    
    private fun setupSnoozePresets() {
        // Get presets, filter to only positive values (not "before event" presets)
        snoozePresets = settings.snoozePresets.filter { it > 0L }.toLongArray()
        
        val container = findViewById<LinearLayout>(R.id.pre_action_presets_container)
        container.removeAllViews()
        
        for ((index, preset) in snoozePresets.withIndex()) {
            val presetView = layoutInflater.inflate(
                android.R.layout.simple_list_item_1, 
                container, 
                false
            ) as TextView
            
            presetView.text = PreferenceUtils.formatSnoozePreset(preset)
            presetView.setPadding(
                resources.getDimensionPixelSize(R.dimen.snooze_view_padding_start),
                resources.getDimensionPixelSize(R.dimen.snooze_view_spacing),
                resources.getDimensionPixelSize(R.dimen.snooze_view_padding_end),
                resources.getDimensionPixelSize(R.dimen.snooze_view_spacing)
            )
            presetView.setTextAppearance(android.R.style.TextAppearance_Medium)
            presetView.setTextColor(resources.getColor(R.color.primary_text, theme))
            // Use TypedValue to resolve the selectableItemBackground attribute
            val outValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            presetView.setBackgroundResource(outValue.resourceId)
            presetView.isClickable = true
            presetView.isFocusable = true
            
            presetView.setOnClickListener {
                val snoozeUntil = getClock().currentTimeMillis() + preset
                executePreSnooze(snoozeUntil)
            }
            
            container.addView(presetView)
        }
    }
    
    private fun showCustomSnoozeDialog() {
        // Simple duration options for custom snooze
        val durations = arrayOf(
            Pair("5 minutes", 5 * 60 * 1000L),
            Pair("10 minutes", 10 * 60 * 1000L),
            Pair("30 minutes", 30 * 60 * 1000L),
            Pair("1 hour", 60 * 60 * 1000L),
            Pair("2 hours", 2 * 60 * 60 * 1000L),
            Pair("4 hours", 4 * 60 * 60 * 1000L),
            Pair("8 hours", 8 * 60 * 60 * 1000L),
            Pair("1 day", 24 * 60 * 60 * 1000L)
        )
        
        val labels = durations.map { it.first }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle(R.string.for_a_custom_time)
            .setItems(labels) { _, which ->
                val snoozeUntil = getClock().currentTimeMillis() + durations[which].second
                executePreSnooze(snoozeUntil)
            }
            .show()
    }
    
    private fun executePreSnooze(snoozeUntil: Long) {
        background {
            var success = false
            
            // 1. Mark as handled in MonitorStorage
            MonitorStorage(this).use { storage ->
                val alert = storage.getAlert(eventId, alertTime, instanceStartTime)
                if (alert != null) {
                    storage.updateAlert(alert.copy(wasHandled = true))
                    DevLog.info(LOG_TAG, "Marked alert as handled for pre-snooze: event $eventId")
                } else {
                    DevLog.warn(LOG_TAG, "Could not find alert for event $eventId")
                }
            }
            
            // 2. Add to EventsStorage as snoozed
            val currentTime = getClock().currentTimeMillis()
            val snoozedEvent = createEventRecord(snoozeUntil, currentTime)
            
            EventsStorage(this).use { db ->
                success = db.addEvent(snoozedEvent)
                if (success) {
                    DevLog.info(LOG_TAG, "Pre-snoozed event $eventId until $snoozeUntil")
                } else {
                    DevLog.error(LOG_TAG, "Failed to add pre-snoozed event $eventId")
                }
            }
            
            // 3. Reschedule alarms
            if (success) {
                ApplicationController.afterCalendarEventFired(this)
            }
            
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, R.string.event_pre_snoozed, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun createEventRecord(snoozeUntil: Long, currentTime: Long): EventAlertRecord {
        return EventAlertRecord(
            calendarId = -1L,  // Bypass calendar filter
            eventId = eventId,
            isAllDay = eventAllDay,
            isRepeating = false,
            alertTime = alertTime,
            notificationId = 0,
            title = eventTitle,
            desc = "",
            startTime = eventStartTime,
            endTime = eventEndTime,
            instanceStartTime = instanceStartTime,
            instanceEndTime = eventEndTime,
            location = eventLocation,
            lastStatusChangeTime = currentTime,
            snoozedUntil = snoozeUntil,
            displayStatus = EventDisplayStatus.Hidden,
            color = eventColor,
            origin = EventOrigin.ProviderManual,
            timeFirstSeen = currentTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = if (eventIsMuted) com.github.quarck.calnotify.calendar.EventAlertFlags.IS_MUTED else 0L
        )
    }
    
    private fun updateMuteButton() {
        val muteView = findViewById<TextView>(R.id.pre_action_mute_toggle)
        // Update text but keep the same icon - icon shows the action (mute/unmute)
        muteView.text = getString(if (eventIsMuted) R.string.pre_unmute else R.string.pre_mute)
    }
    
    private fun toggleMute() {
        background {
            var success = false
            MonitorStorage(this).use { storage ->
                val alert = storage.getAlert(eventId, alertTime, instanceStartTime)
                if (alert != null) {
                    val newMuted = !eventIsMuted
                    storage.updateAlert(alert.withPreMuted(newMuted))
                    eventIsMuted = newMuted
                    success = true
                    DevLog.info(LOG_TAG, "Toggled pre-mute for event $eventId to $newMuted")
                }
            }
            
            runOnUiThread {
                if (success) {
                    updateMuteButton()
                    val msgRes = if (eventIsMuted) R.string.event_will_be_muted else R.string.event_unmuted
                    Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun viewInCalendar() {
        CalendarIntents.viewCalendarEvent(this, eventId)
    }
}
