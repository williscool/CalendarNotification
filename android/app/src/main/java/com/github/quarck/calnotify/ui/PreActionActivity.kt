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

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
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
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import com.github.quarck.calnotify.prefs.PreferenceUtils
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.textutils.EventFormatterInterface
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusSystemClock
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.setupStatusBarSpacer

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
        
        // DI providers for testing (Pattern B - Companion Object Provider)
        var clockProvider: (() -> CNPlusClockInterface)? = null
        var monitorStorageProvider: ((Context) -> MonitorStorage)? = null
        var eventsStorageProvider: ((Context) -> EventsStorage)? = null
        var dismissedEventsStorageProvider: ((Context) -> DismissedEventsStorage)? = null
        
        private fun getClock(): CNPlusClockInterface =
            clockProvider?.invoke() ?: CNPlusSystemClock()
        
        private fun getMonitorStorage(context: Context): MonitorStorage =
            monitorStorageProvider?.invoke(context) ?: MonitorStorage(context)
        
        private fun getEventsStorage(context: Context): EventsStorage =
            eventsStorageProvider?.invoke(context) ?: EventsStorage(context)
        
        private fun getDismissedEventsStorage(context: Context): DismissedEventsStorage =
            dismissedEventsStorageProvider?.invoke(context) ?: DismissedEventsStorage(context)
        
        /** Reset all providers - call in @After to prevent test pollution */
        fun resetProviders() {
            clockProvider = null
            monitorStorageProvider = null
            eventsStorageProvider = null
            dismissedEventsStorageProvider = null
        }
        
        /**
         * Creates an intent to launch PreActionActivity for the given event.
         */
        fun createIntent(context: Context, event: EventAlertRecord): Intent {
            return Intent(context, PreActionActivity::class.java).apply {
                putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                putExtra(Consts.INTENT_CALENDAR_ID_KEY, event.calendarId)
                putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                putExtra(Consts.INTENT_ALERT_TIME_KEY, event.alertTime)
                putExtra(Consts.INTENT_EVENT_TITLE_KEY, event.title)
                putExtra(Consts.INTENT_EVENT_DESC_KEY, event.desc)
                putExtra(Consts.INTENT_EVENT_START_TIME_KEY, event.startTime)
                putExtra(Consts.INTENT_EVENT_END_TIME_KEY, event.endTime)
                putExtra(Consts.INTENT_EVENT_INSTANCE_END_TIME_KEY, event.instanceEndTime)
                putExtra(Consts.INTENT_EVENT_ALL_DAY_KEY, event.isAllDay)
                putExtra(Consts.INTENT_EVENT_IS_REPEATING_KEY, event.isRepeating)
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
    private var calendarId: Long = -1L
    private var instanceStartTime: Long = -1L
    private var instanceEndTime: Long = -1L
    private var alertTime: Long = -1L
    private var eventTitle: String = ""
    private var eventDesc: String = ""
    private var eventStartTime: Long = 0L
    private var eventEndTime: Long = 0L
    private var eventAllDay: Boolean = false
    private var eventIsRepeating: Boolean = false
    private var eventLocation: String = ""
    private var eventColor: Int = 0
    private var eventIsMuted: Boolean = false
    
    private lateinit var snoozePresets: LongArray

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pre_action)
        setupStatusBarSpacer()
        
        settings = Settings(this)
        formatter = EventFormatter(this)
        
        // Load event data from intent
        eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1L)
        calendarId = intent.getLongExtra(Consts.INTENT_CALENDAR_ID_KEY, -1L)
        instanceStartTime = intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1L)
        instanceEndTime = intent.getLongExtra(Consts.INTENT_EVENT_INSTANCE_END_TIME_KEY, -1L)
        alertTime = intent.getLongExtra(Consts.INTENT_ALERT_TIME_KEY, -1L)
        eventTitle = intent.getStringExtra(Consts.INTENT_EVENT_TITLE_KEY) ?: ""
        eventDesc = intent.getStringExtra(Consts.INTENT_EVENT_DESC_KEY) ?: ""
        eventStartTime = intent.getLongExtra(Consts.INTENT_EVENT_START_TIME_KEY, 0L)
        eventEndTime = intent.getLongExtra(Consts.INTENT_EVENT_END_TIME_KEY, 0L)
        eventAllDay = intent.getBooleanExtra(Consts.INTENT_EVENT_ALL_DAY_KEY, false)
        eventIsRepeating = intent.getBooleanExtra(Consts.INTENT_EVENT_IS_REPEATING_KEY, false)
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
        
        // 3-dot menu
        findViewById<View>(R.id.pre_action_menu).setOnClickListener { v ->
            showMenu(v)
        }
    }
    
    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.pre_action, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_pre_dismiss -> {
                    executePreDismiss()
                    true
                }
                else -> false
            }
        }
        
        popup.show()
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
        // Use centralized snooze intervals from resources (same as ViewEventActivityNoRecents)
        val intervalNames = resources.getStringArray(R.array.default_snooze_intervals)
        val intervalValues = resources.getIntArray(R.array.default_snooze_intervals_seconds_values)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.for_a_custom_time)
            .setItems(intervalNames) { _, which ->
                val intervalSeconds = intervalValues.getOrNull(which)?.toLong() ?: return@setItems
                if (intervalSeconds > 0) {
                    val snoozeUntil = getClock().currentTimeMillis() + intervalSeconds * 1000L
                    executePreSnooze(snoozeUntil)
                }
            }
            .show()
    }
    
    private fun executePreSnooze(snoozeUntil: Long) {
        background {
            var monitorSuccess = false
            var storageSuccess = false
            
            // 1. Mark as handled in MonitorStorage - must succeed before proceeding
            getMonitorStorage(this).use { storage ->
                monitorSuccess = storage.setWasHandled(eventId, alertTime, instanceStartTime)
                if (monitorSuccess) {
                    DevLog.info(LOG_TAG, "Marked alert as handled for pre-snooze: event $eventId")
                } else {
                    DevLog.error(LOG_TAG, "Could not find alert for event $eventId - aborting pre-snooze")
                }
            }
            
            // Only proceed if MonitorStorage update succeeded
            if (monitorSuccess) {
                // 2. Add to EventsStorage as snoozed
                val currentTime = getClock().currentTimeMillis()
                val snoozedEvent = createEventRecord(snoozeUntil, currentTime)
                
                getEventsStorage(this).use { db ->
                    storageSuccess = db.addEvent(snoozedEvent)
                    if (storageSuccess) {
                        DevLog.info(LOG_TAG, "Pre-snoozed event $eventId until $snoozeUntil")
                    } else {
                        DevLog.error(LOG_TAG, "Failed to add pre-snoozed event $eventId")
                    }
                }
                
                // Rollback MonitorStorage if EventsStorage failed to prevent event loss
                // Note: Cross-database transactions aren't possible with Room, so we use manual rollback
                if (!storageSuccess) {
                    getMonitorStorage(this).use { storage ->
                        storage.clearWasHandled(eventId, alertTime, instanceStartTime)
                        DevLog.info(LOG_TAG, "Rolled back wasHandled for event $eventId after storage failure")
                    }
                }
                
                // 3. Reschedule alarms
                if (storageSuccess) {
                    ApplicationController.afterCalendarEventFired(this)
                }
            }
            
            runOnUiThread {
                if (monitorSuccess && storageSuccess) {
                    Snackbar.make(findViewById(android.R.id.content), R.string.event_pre_snoozed, Snackbar.LENGTH_SHORT).show()
                    finish()
                } else {
                    Snackbar.make(findViewById(android.R.id.content), R.string.error, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun createEventRecord(snoozeUntil: Long, currentTime: Long): EventAlertRecord {
        return EventAlertRecord(
            calendarId = calendarId,
            eventId = eventId,
            isAllDay = eventAllDay,
            isRepeating = eventIsRepeating,
            alertTime = alertTime,
            notificationId = 0,
            title = eventTitle,
            desc = eventDesc,
            startTime = eventStartTime,
            endTime = eventEndTime,
            instanceStartTime = instanceStartTime,
            instanceEndTime = instanceEndTime,
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
        // Null check in case activity is destroyed while background task runs
        val muteView = findViewById<TextView>(R.id.pre_action_mute_toggle) ?: return
        muteView.text = getString(if (eventIsMuted) R.string.pre_unmute else R.string.pre_mute)
    }
    
    private fun toggleMute() {
        background {
            val newMutedState = getMonitorStorage(this).use { storage ->
                storage.togglePreMuted(eventId, alertTime, instanceStartTime)
            }
            
            if (newMutedState != null) {
                eventIsMuted = newMutedState
                DevLog.info(LOG_TAG, "Toggled pre-mute for event $eventId to $newMutedState")
            }
            
            runOnUiThread {
                if (newMutedState != null) {
                    updateMuteButton()
                    val msgRes = if (eventIsMuted) R.string.event_will_be_muted else R.string.event_unmuted
                    Snackbar.make(findViewById(android.R.id.content), msgRes, Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(findViewById(android.R.id.content), R.string.error, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun executePreDismiss() {
        val currentTime = getClock().currentTimeMillis()
        val eventRecord = createEventRecord(0, currentTime)
        
        background {
            val success = ApplicationController.preDismissEvent(this, eventRecord)
            
            runOnUiThread {
                if (success) {
                    Snackbar.make(findViewById(android.R.id.content), R.string.event_pre_dismissed, Snackbar.LENGTH_SHORT).show()
                    finish()
                } else {
                    Snackbar.make(findViewById(android.R.id.content), R.string.error, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun viewInCalendar() {
        CalendarIntents.viewCalendarEvent(this, eventId)
    }
}
