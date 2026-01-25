//
//   Calendar Notifications Plus  
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
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
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.text.format.DateUtils
import android.view.View
import android.widget.*
import com.google.android.material.snackbar.Snackbar
import com.github.quarck.calnotify.app.*
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.maps.MapsIntents
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.textutils.EventFormatterInterface
import com.github.quarck.calnotify.utils.*
import com.github.quarck.calnotify.utils.setupStatusBarSpacer
import java.util.*
import com.github.quarck.calnotify.*
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Handler
import androidx.core.content.ContextCompat
import android.text.method.ScrollingMovementMethod
// TODO: add repeating rule and calendar name somewhere on the snooze activity

enum class ViewEventActivityStateCode(val code: Int) {
    Normal(0),
    CustomSnoozeOpened(1),
    SnoozeUntilOpenedDatePicker(2),
    SnoozeUntilOpenedTimePicker(3);

    companion object {
        fun fromInt(v: Int): ViewEventActivityStateCode {
            return values()[v];
        }
    }
}

data class ViewEventActivityState(
        var state: ViewEventActivityStateCode = ViewEventActivityStateCode.Normal,
        var timeAMillis: Long = 0L,
        var timeBMillis: Long = 0L
) {
    fun toBundle(bundle: Bundle) {
        bundle.putInt(KEY_STATE_CODE, state.code)
        bundle.putLong(KEY_TIME_A, timeAMillis)
        bundle.putLong(KEY_TIME_B, timeBMillis)
    }

    companion object {
        fun fromBundle(bundle: Bundle): ViewEventActivityState {

            val code = bundle.getInt(KEY_STATE_CODE, 0)
            val timeA = bundle.getLong(KEY_TIME_A, 0L)
            val timeB = bundle.getLong(KEY_TIME_B, 0L)

            return ViewEventActivityState(ViewEventActivityStateCode.fromInt(code), timeA, timeB)
        }

        const val KEY_STATE_CODE = "code"
        const val KEY_TIME_A = "timeA"
        const val KEY_TIME_B = "timeB"
    }
}

open class ViewEventActivityNoRecents : AppCompatActivity() {

    var state = ViewEventActivityState()

    lateinit var event: EventAlertRecord

    lateinit var calendar: CalendarRecord

    lateinit var snoozePresets: LongArray

    lateinit var settings: Settings

    lateinit var formatter: EventFormatterInterface

    val calendarReloadManager: CalendarReloadManagerInterface = CalendarReloadManager
    val calendarProvider: CalendarProviderInterface = CalendarProvider

    val handler = Handler()

//    var snoozeAllIsChange = false

    var snoozeFromMainActivity = false

    val snoozePresetControlIds = intArrayOf(
            R.id.snooze_view_snooze_present1,
            R.id.snooze_view_snooze_present2,
            R.id.snooze_view_snooze_present3,
            R.id.snooze_view_snooze_present4,
            R.id.snooze_view_snooze_present5,
            R.id.snooze_view_snooze_present6
    )

    val snoozePresentQuietTimeReminderControlIds = intArrayOf(
            R.id.snooze_view_snooze_present1_quiet_time_notice,
            R.id.snooze_view_snooze_present2_quiet_time_notice,
            R.id.snooze_view_snooze_present3_quiet_time_notice,
            R.id.snooze_view_snooze_present4_quiet_time_notice,
            R.id.snooze_view_snooze_present5_quiet_time_notice,
            R.id.snooze_view_snooze_present6_quiet_time_notice
    )

    var baselineIds = intArrayOf(
            R.id.snooze_view_snooze_present1_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present2_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present3_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present4_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present5_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present6_quiet_time_notice_baseline
    )

    private val undoManager by lazy { UndoManager }

    // These dialog controls moved here so saveInstanceState could store current time selection
    var customSnooze_TimeIntervalPickerController: TimeIntervalPickerController? = null

    lateinit var calendarNameTextView: TextView
    lateinit var calendarAccountTextView: TextView

    val clock: CNPlusClockInterface = CNPlusSystemClock()

    private var pendingSnoozeUntilDateSelectionUtcMillis: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PermissionsManager.hasAllCalendarPermissions(this)) {
            finish()
            return
        }

        if (savedInstanceState != null)
            state = ViewEventActivityState.fromBundle(savedInstanceState)

        pendingSnoozeUntilDateSelectionUtcMillis =
            savedInstanceState?.getLong(BUNDLE_PENDING_SNOOZE_UNTIL_DATE_UTC_MILLIS)
                ?.takeIf { it != 0L }

        setContentView(R.layout.activity_view)

        val currentTime = clock.currentTimeMillis()

        settings = Settings(this)
        formatter = EventFormatter(this)

        // Populate event details
        val eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)
        val instanceStartTime = intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1L)

        //snoozeAllIsChange = intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, false)

        snoozeFromMainActivity = intent.getBooleanExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, false)

        find<Toolbar?>(R.id.toolbar)?.visibility = View.GONE

        // load event if it is not a "snooze all"
        getEventsStorage(this).use {
            db ->

            var dbEvent = db.getEvent(eventId, instanceStartTime)

            if (dbEvent != null) {
                val eventDidChange = calendarReloadManager.reloadSingleEvent(this, db, dbEvent, calendarProvider, null)
                if (eventDidChange) {
                    val newDbEvent = db.getEvent(eventId, instanceStartTime)
                    if (newDbEvent != null) {
                        dbEvent = newDbEvent
                    } else {
                        DevLog.error(LOG_TAG, "ViewActivity: cannot find event after calendar reload, event $eventId, inst $instanceStartTime")
                    }
                }
            }

            if (dbEvent == null) {
                DevLog.error(LOG_TAG, "ViewActivity started for non-existing eveng id $eventId, st $instanceStartTime")
                finish()
//                return
            }

          if (dbEvent != null) {
            event = dbEvent
          }
        }

        calendar = calendarProvider.getCalendarById(this, event.calendarId)
                ?: calendarProvider.createCalendarNotFoundCal(this)

        calendarNameTextView = findOrThrow<TextView>(R.id.view_event_calendar_name)
        calendarNameTextView.text = calendar.displayName

        calendarAccountTextView = findOrThrow<TextView>(R.id.view_event_calendar_account)
        calendarAccountTextView.text = calendar.accountName

        snoozePresets = settings.snoozePresets

        // remove "MM minutes before event" snooze presents for "Snooze All"
        // and when event time has passed already
        if (event.displayedStartTime < currentTime)
            snoozePresets = snoozePresets.filter { it > 0L }.toLongArray()

        val isQuiet =
                QuietHoursManager(this).isInsideQuietPeriod(
                        settings,
                        snoozePresets.map { it -> currentTime + it }.toLongArray())

        // Populate snooze controls
        for ((idx, id) in snoozePresetControlIds.withIndex()) {
            val snoozeLable = findOrThrow<TextView>(id);
            val quietTimeNotice = findOrThrow<TextView>(snoozePresentQuietTimeReminderControlIds[idx])
            val quietTimeNoticeBaseline = findOrThrow<TextView>(baselineIds[idx])

            if (idx < snoozePresets.size) {
                snoozeLable.text = formatPreset(snoozePresets[idx])
                snoozeLable.visibility = View.VISIBLE;
                quietTimeNoticeBaseline.visibility = View.VISIBLE

                if (isQuiet[idx])
                    quietTimeNotice.visibility = View.VISIBLE
                else
                    quietTimeNotice.visibility = View.GONE
            }
            else {
                snoozeLable.visibility = View.GONE;
                quietTimeNotice.visibility = View.GONE
                quietTimeNoticeBaseline.visibility = View.GONE
            }
        }

        // need to hide these guys
        val showCustomSnoozeVisibility = View.VISIBLE
        findOrThrow<TextView>(R.id.snooze_view_snooze_custom).visibility = showCustomSnoozeVisibility
        val snoozeCustom = find<TextView?>(R.id.snooze_view_snooze_until)
        if (snoozeCustom != null)
            snoozeCustom.visibility = showCustomSnoozeVisibility

        val location = event.location;
        if (location != "") {
            findOrThrow<View>(R.id.snooze_view_location_layout).visibility = View.VISIBLE;
            val locationView = findOrThrow<TextView>(R.id.snooze_view_location)
            locationView.text = location;
            locationView.setOnClickListener { MapsIntents.openLocation(this, event.location) }
        }

        val title = findOrThrow<TextView>(R.id.snooze_view_title)
        title.text = if (event.title.isNotEmpty()) event.title else this.resources.getString(R.string.empty_title);

        val (line1, line2) = formatter.formatDateTimeTwoLines(event);

        val dateTimeFirstLine = findOrThrow<TextView>(R.id.snooze_view_event_date_line1)
        val dateTimeSecondLine = findOrThrow<TextView>(R.id.snooze_view_event_date_line2)

        dateTimeFirstLine.text = line1;

        if (line2.isEmpty())
            dateTimeSecondLine.visibility = View.GONE
        else
            dateTimeSecondLine.text = line2;

        dateTimeFirstLine.isClickable = false
        dateTimeSecondLine.isClickable = false
        title.isClickable = false

        title.setMovementMethod(ScrollingMovementMethod())
        title.post {
            val y = title.getLayout()?.getLineTop(0)
            if (y != null)
                title.scrollTo(0, y)
        }
        title.setTextIsSelectable(true)

        if (event.desc.isNotEmpty() && !settings.snoozeHideEventDescription) {
            // Show the event desc
            findOrThrow<RelativeLayout>(R.id.layout_event_description).visibility = View.VISIBLE
            findOrThrow<TextView>(R.id.snooze_view_event_description).text = event.desc
        }

        var color: Int = event.color.adjustCalendarColor()
        if (color == 0)
            color = ContextCompat.getColor(this, R.color.primary)

        val colorDrawable = ColorDrawable(color)
        findOrThrow<RelativeLayout>(R.id.snooze_view_event_details_layout).background = colorDrawable

        window.statusBarColor = color.scaleColor(0.7f)

        // Set status bar spacer height (for edge-to-edge displays)
        setupStatusBarSpacer()

//        val shouldOfferMove = (!event.isRepeating) && (DateTimeUtils.isUTCTodayOrInThePast(event.startTime))
        val shouldOfferMove = (DateTimeUtils.isUTCTodayOrInThePast(event.startTime))
        if (shouldOfferMove) {
            findOrThrow<RelativeLayout>(R.id.snooze_reschedule_layout).visibility = View.VISIBLE
            if (event.isRepeating) {
                findOrThrow<TextView>(R.id.snooze_reschedule_for).text = getString(R.string.change_event_time_repeating_event)
            }
        }
        else {
            find<View?>(R.id.snooze_view_inter_view_divider)?.visibility = View.GONE
        }

        if (event.snoozedUntil != 0L) {
            findOrThrow<TextView>(R.id.snooze_snooze_for).text = resources.getString(R.string.change_snooze_to)
        }

        val nextReminderLayout: RelativeLayout? = find<RelativeLayout>(R.id.layout_next_reminder)
        val nextReminderText: TextView? = find<TextView>(R.id.snooze_view_next_reminder)

        if (nextReminderLayout != null && nextReminderText != null) {

            val nextReminder = calendarProvider.getNextEventReminderTime(this, event)

            if (nextReminder != 0L) {
                nextReminderLayout.visibility = View.VISIBLE
                nextReminderText.visibility = View.VISIBLE

                val format = this.resources.getString(R.string.next_reminder_fmt)

                nextReminderText.text = format.format(formatter.formatTimePoint(nextReminder))
            }
        }


        val fab = findOrThrow<FloatingActionButton>(R.id.floating_edit_button)

        if (!calendar.isReadOnly) {
            if (!event.isRepeating && !settings.alwaysUseExternalEditor) {

                fab.setOnClickListener { _ ->
                    val intent = Intent(this, EditEventActivity::class.java)
                    intent.putExtra(EditEventActivity.EVENT_ID, event.eventId)
                    startActivity(intent)
                    finish()
                }

            } else {
                fab.setOnClickListener { _ ->
                    openEventInCalendar(event)
                    finish()
                }
            }

            val states = arrayOf(intArrayOf(android.R.attr.state_enabled), // enabled
                    intArrayOf(android.R.attr.state_pressed)  // pressed
            )

            val colors = intArrayOf(
                    event.color.adjustCalendarColor(false),
                    event.color.adjustCalendarColor(true)
            )

            fab.backgroundTintList = ColorStateList(states, colors)
        }
        else  {
            fab.visibility = View.GONE
        }

        val menuButton = find<ImageView?>(R.id.snooze_view_menu)
        menuButton?.setOnClickListener { showDismissEditPopup(menuButton) }

        ApplicationController.cleanupEventReminder(this)

        rewireSnoozeUntilPickersIfPresent()
        restoreState(state)
    }

    fun showDismissEditPopup(v: View) {
        val popup = PopupMenu(this, v)
        val inflater = popup.menuInflater
        inflater.inflate(R.menu.snooze, popup.menu)

        val menuItem = popup.menu.findItem(R.id.action_delete_event)
        if (menuItem != null) {
            menuItem.isVisible = !event.isRepeating
        }

        val menuItemMute = popup.menu.findItem(R.id.action_mute_event)
        if (menuItemMute != null) {
            menuItemMute.isVisible = !event.isMuted && !event.isTask
        }

        val menuItemUnMute = popup.menu.findItem(R.id.action_unmute_event)
        if (menuItemUnMute != null) {
            menuItemUnMute.isVisible = event.isMuted
        }

        // Show "Back to upcoming" for snoozed events whose alert time hasn't passed
        val menuItemUnsnooze = popup.menu.findItem(R.id.action_unsnooze_to_upcoming)
        if (menuItemUnsnooze != null) {
            menuItemUnsnooze.isVisible = event.canUnsnoozeToUpcoming(clock.currentTimeMillis())
        }

        if (event.isTask) {
            val menuItemDismiss = popup.menu.findItem(R.id.action_dismiss_event)
            val menuItemDone = popup.menu.findItem(R.id.action_done_event)
            if (menuItemDismiss != null && menuItemDone != null) {
                menuItemDismiss.isVisible = false
                menuItemDone.isVisible = true
            }
        }

        /*    <item
        android:id="@+id/action_mute_event"
        android:title="@string/mute_notification"
        android:visible="false"
        />

    <item
        android:id="@+id/action_unmute_event"
        android:title="@string/un_mute_notification"
        android:visible="false"
        />*/

        popup.setOnMenuItemClickListener {
            item ->

            when (item.itemId) {
                R.id.action_dismiss_event, R.id.action_done_event -> {
                    ApplicationController.dismissEvent(this, EventDismissType.ManuallyDismissedFromActivity, event)
                    undoManager.addUndoState(
                            UndoState(undo = Runnable { ApplicationController.restoreEvent(applicationContext, event) }))
                    finish()
                    true
                }

                R.id.action_delete_event -> {

                    MaterialAlertDialogBuilder(this)
                            .setMessage(getString(R.string.delete_event_question))
                            .setCancelable(false)
                            .setPositiveButton(android.R.string.yes) { _, _ ->

                                DevLog.info(LOG_TAG, "Deleting event ${event.eventId} per user request")

                                val success = ApplicationController.dismissAndDeleteEvent(
                                        this, EventDismissType.ManuallyDismissedFromActivity, event
                                )
                                if (success) {
                                    undoManager.addUndoState(
                                            UndoState(undo = Runnable { ApplicationController.restoreEvent(applicationContext, event) }))
                                }
                                finish()
                            }
                            .setNegativeButton(R.string.cancel) { _, _ ->
                            }
                            .create()
                            .show()

                    true
                }

                R.id.action_mute_event -> {
                    ApplicationController.toggleMuteForEvent(this, event.eventId, event.instanceStartTime, 0)
                    event.isMuted = true

                    true
                }

                R.id.action_unmute_event -> {
                    ApplicationController.toggleMuteForEvent(this, event.eventId, event.instanceStartTime, 1)
                    event.isMuted = false
                    true
                }

                R.id.action_open_in_calendar -> {
                    openEventInCalendar(event)
                    finish()
                    true
                }

                R.id.action_unsnooze_to_upcoming -> {
                    val success = ApplicationController.unsnoozeToUpcoming(this, event)
                    if (success) {
                        Snackbar.make(findViewById(android.R.id.content), R.string.event_restored_to_upcoming, Snackbar.LENGTH_SHORT).show()
                    }
                    finish()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun formatPreset(preset: Long): String {
        val num: Long
        val unit: String
        val presetSeconds = preset / 1000L;

        if (presetSeconds == 0L)
            return resources.getString(R.string.until_event_time)

        if (presetSeconds % Consts.DAY_IN_SECONDS == 0L) {
            num = presetSeconds / Consts.DAY_IN_SECONDS;
            unit =
                    if (num != 1L)
                        resources.getString(R.string.days)
                    else
                        resources.getString(R.string.day)
        }
        else if (presetSeconds % Consts.HOUR_IN_SECONDS == 0L) {
            num = presetSeconds / Consts.HOUR_IN_SECONDS;
            unit =
                    if (num != 1L)
                        resources.getString(R.string.hours)
                    else
                        resources.getString(R.string.hour)
        }
        else {
            num = presetSeconds / Consts.MINUTE_IN_SECONDS;
            unit =
                    if (num != 1L)
                        resources.getString(R.string.minutes)
                    else
                        resources.getString(R.string.minute)
        }

        if (num <= 0) {
            val beforeEventString = resources.getString(R.string.before_event)
            return "${-num} $unit $beforeEventString"
        }
        return "$num $unit"
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonCancelClick(v: View?) {
        finish();
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonEventDetailsClick(v: View?) {
        openEventInCalendar(event)
    }

    /**
     * Opens an event in the calendar with fallback to time-based view if event not found.
     * Shows a toast if the event was not found in the system calendar.
     * 
     * @see <a href="https://github.com/williscool/CalendarNotification/issues/66">Issue #66</a>
     */
    private fun openEventInCalendar(event: EventAlertRecord) {
        val found = CalendarIntents.viewCalendarEventWithFallback(this, calendarProvider, event)
        if (!found) {
            Snackbar.make(findViewById(android.R.id.content), R.string.event_not_found_opening_calendar_at_time, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun snoozeEvent(snoozeDelay: Long) {
        DevLog.debug(LOG_TAG, "Snoozing event id ${event.eventId}, snoozeDelay=${snoozeDelay / 1000L}")

        val result = ApplicationController.snoozeEvent(this, event.eventId, event.instanceStartTime, snoozeDelay);
        if (result != null) {
            result.toast(this)
        }
        finish()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonSnoozeClick(v: View?) {
        if (v == null)
            return

        for ((idx, id) in snoozePresetControlIds.withIndex()) {
            if (id == v.id) {
                snoozeEvent(snoozePresets[idx]);
                break;
            }
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        when (state.state) {
            ViewEventActivityStateCode.Normal -> {
            }
            ViewEventActivityStateCode.CustomSnoozeOpened -> {
                state.timeAMillis = customSnooze_TimeIntervalPickerController?.intervalMilliseconds ?: 0L
            }
            // Material pickers handle their own state via FragmentManager
            ViewEventActivityStateCode.SnoozeUntilOpenedDatePicker,
            ViewEventActivityStateCode.SnoozeUntilOpenedTimePicker -> {
                // No-op: MaterialDatePicker/MaterialTimePicker are DialogFragments
                // and restore themselves automatically
            }
        }
        state.toBundle(outState)

        outState.putLong(
            BUNDLE_PENDING_SNOOZE_UNTIL_DATE_UTC_MILLIS,
            pendingSnoozeUntilDateSelectionUtcMillis ?: 0L
        )
    }

    private fun restoreState(state: ViewEventActivityState) {
        when (state.state) {
            ViewEventActivityStateCode.Normal -> {
            }
            ViewEventActivityStateCode.CustomSnoozeOpened -> {
                customSnoozeShowDialog(state.timeAMillis)
            }
            // Material pickers restore themselves via FragmentManager
            ViewEventActivityStateCode.SnoozeUntilOpenedDatePicker,
            ViewEventActivityStateCode.SnoozeUntilOpenedTimePicker -> {
                // No-op: MaterialDatePicker/MaterialTimePicker restore automatically
            }
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonCustomSnoozeClick(v: View?) {
        customSnoozeShowSimplifiedDialog(persistentState.lastCustomSnoozeIntervalMillis)
    }

    fun customSnoozeShowSimplifiedDialog(initialTimeValue: Long) {

        val intervalNames: Array<String> = this.resources.getStringArray(R.array.default_snooze_intervals)
        val intervalValues = this.resources.getIntArray(R.array.default_snooze_intervals_seconds_values)

        val builder = MaterialAlertDialogBuilder(this)

        val adapter = ArrayAdapter<String>(this, R.layout.simple_list_item_medium)

        adapter.addAll(intervalNames.toMutableList())

        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            _, which ->
            if (which in 0..intervalValues.size-1) {

                val intervalSeconds = intervalValues[which].toLong()
                if (intervalSeconds != -1L) {
                    snoozeEvent(intervalSeconds * 1000L)
                } else {
                    customSnoozeShowDialog(initialTimeValue)
                }
            }
        }

        builder.show()
    }

    fun customSnoozeShowDialog(initialTimeValue: Long) {

        val dialogView = this.layoutInflater.inflate(R.layout.dialog_interval_picker, null);

        val timeIntervalPicker = TimeIntervalPickerController(dialogView, R.string.snooze_for, 0, false)
        timeIntervalPicker.intervalMilliseconds = initialTimeValue

        state.state = ViewEventActivityStateCode.CustomSnoozeOpened
        customSnooze_TimeIntervalPickerController = timeIntervalPicker

        val builder = MaterialAlertDialogBuilder(this)

        builder.setView(dialogView)

        builder.setPositiveButton(R.string.snooze) {
            _: DialogInterface?, _: Int ->

            val intervalMilliseconds = timeIntervalPicker.intervalMilliseconds
            this.persistentState.lastCustomSnoozeIntervalMillis = intervalMilliseconds

            snoozeEvent(intervalMilliseconds)

            state.state = ViewEventActivityStateCode.Normal
            customSnooze_TimeIntervalPickerController = null
        }

        builder.setNegativeButton(R.string.cancel) {
            _: DialogInterface?, _: Int ->

            state.state = ViewEventActivityStateCode.Normal
            customSnooze_TimeIntervalPickerController = null
        }

        builder.create().show()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonSnoozeUntilClick(v: View?) {
        val initialDate = if (event.snoozedUntil != 0L) event.snoozedUntil else clock.currentTimeMillis()
        
        val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.choose_date)
                .setSelection(initialDate)
                .build()

        datePicker.addOnPositiveButtonClickListener { dateSelection ->
            pendingSnoozeUntilDateSelectionUtcMillis = dateSelection
            showSnoozeUntilTimePicker(dateSelection, initialDate)
        }

        datePicker.show(supportFragmentManager, TAG_SNOOZE_UNTIL_DATE_PICKER)
    }

    private fun showSnoozeUntilTimePicker(dateSelection: Long, initialTimeValue: Long) {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(this)
        
        val cal = Calendar.getInstance()
        if (initialTimeValue != 0L) {
            cal.timeInMillis = initialTimeValue
        }

        val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(if (is24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(cal.get(Calendar.HOUR_OF_DAY))
                .setMinute(cal.get(Calendar.MINUTE))
                .setTitleText(getString(R.string.choose_time) + " - " + 
                        DateUtils.formatDateTime(this, localMidnightMillisFromUtcDateSelection(dateSelection), DateUtils.FORMAT_SHOW_DATE))
                .build()

        timePicker.addOnPositiveButtonClickListener {
            onSnoozeUntilTimePicked(dateSelection, timePicker.hour, timePicker.minute)
        }

        timePicker.show(supportFragmentManager, TAG_SNOOZE_UNTIL_TIME_PICKER)
    }

    private fun rewireSnoozeUntilPickersIfPresent() {
        (supportFragmentManager.findFragmentByTag(TAG_SNOOZE_UNTIL_DATE_PICKER) as? MaterialDatePicker<Long>)?.let { picker ->
            val initialDate = if (event.snoozedUntil != 0L) event.snoozedUntil else clock.currentTimeMillis()
            picker.addOnPositiveButtonClickListener { dateSelection ->
                pendingSnoozeUntilDateSelectionUtcMillis = dateSelection
                showSnoozeUntilTimePicker(dateSelection, initialDate)
            }
        }

        (supportFragmentManager.findFragmentByTag(TAG_SNOOZE_UNTIL_TIME_PICKER) as? MaterialTimePicker)?.let { picker ->
            picker.addOnPositiveButtonClickListener {
                val dateSelection = pendingSnoozeUntilDateSelectionUtcMillis ?: return@addOnPositiveButtonClickListener
                onSnoozeUntilTimePicked(dateSelection, picker.hour, picker.minute)
            }
        }
    }

    private fun onSnoozeUntilTimePicked(dateSelection: Long, hour: Int, minute: Int) {
        val date = localCalendarFromUtcDateSelection(dateSelection, hour, minute)

        val snoozeFor = date.timeInMillis - clock.currentTimeMillis() + Consts.ALARM_THRESHOLD
        if (snoozeFor > 0L) {
            snoozeEvent(snoozeFor)
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.selected_time_is_in_the_past)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun localMidnightMillisFromUtcDateSelection(dateSelectionUtcMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.timeInMillis = dateSelectionUtcMillis

        val local = Calendar.getInstance()
        local.set(Calendar.YEAR, utc.get(Calendar.YEAR))
        local.set(Calendar.MONTH, utc.get(Calendar.MONTH))
        local.set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        local.set(Calendar.HOUR_OF_DAY, 0)
        local.set(Calendar.MINUTE, 0)
        local.set(Calendar.SECOND, 0)
        local.set(Calendar.MILLISECOND, 0)
        return local.timeInMillis
    }

    private fun localCalendarFromUtcDateSelection(dateSelectionUtcMillis: Long, hour: Int, minute: Int): Calendar {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.timeInMillis = dateSelectionUtcMillis

        val local = Calendar.getInstance()
        local.set(Calendar.YEAR, utc.get(Calendar.YEAR))
        local.set(Calendar.MONTH, utc.get(Calendar.MONTH))
        local.set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        local.set(Calendar.HOUR_OF_DAY, hour)
        local.set(Calendar.MINUTE, minute)
        local.set(Calendar.SECOND, 0)
        local.set(Calendar.MILLISECOND, 0)
        return local
    }

    fun reschedule(addTime: Long) {

        DevLog.info(LOG_TAG, "Moving event ${event.eventId} by ${addTime / 1000L} seconds, isRepeating = ${event.isRepeating}");

        if (!event.isRepeating) {
            val moved = ApplicationController.moveEvent(this, event, addTime)

            if (moved) {
                // Show
                if (Settings(this).viewAfterEdit) {
                    handler.postDelayed({
                        openEventInCalendar(event)
                        finish()
                    }, 100)
                }
                else {
                    SnoozeResult(SnoozeType.Moved, event.startTime, 0L).toast(this)
                    // terminate ourselves
                    finish();
                }
            } else {
                DevLog.info(LOG_TAG, "snooze: Failed to move event ${event.eventId} by ${addTime / 1000L} seconds")
            }
        }
        else {
            val newEventId = ApplicationController.moveAsCopy(this, calendar, event, addTime)

            if (newEventId != -1L) {
                // Show
                if (Settings(this).viewAfterEdit) {
                    handler.postDelayed({
                        CalendarIntents.viewCalendarEvent(this, newEventId)
                        finish()
                    }, 100)
                }
                else {
                    SnoozeResult(SnoozeType.Moved, event.startTime, 0L).toast(this)
                    // terminate ourselves
                    finish();
                }
            } else {
                DevLog.info(LOG_TAG, "snooze: Failed to move event ${event.eventId} by ${addTime / 1000L} seconds")
            }
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonRescheduleClick(v: View?) {
        if (v == null)
            return

        when (v.id) {
            R.id.snooze_view_reschedule_present1 ->
                reschedule(Consts.HOUR_IN_SECONDS * 1000L)
            R.id.snooze_view_reschedule_present2 ->
                reschedule(Consts.DAY_IN_SECONDS * 1000L)
            R.id.snooze_view_reschedule_present3 ->
                reschedule(Consts.DAY_IN_SECONDS * 7L * 1000L)
            R.id.snooze_view_reschedule_present4 ->
                reschedule(Consts.DAY_IN_SECONDS * 28L * 1000L)
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonRescheduleCustomClick(v: View?) {
    }

    companion object {
        private const val LOG_TAG = "ActivitySnooze"
        private const val TAG_SNOOZE_UNTIL_DATE_PICKER = "snoozeUntilDatePicker"
        private const val TAG_SNOOZE_UNTIL_TIME_PICKER = "snoozeUntilTimePicker"
        private const val BUNDLE_PENDING_SNOOZE_UNTIL_DATE_UTC_MILLIS = "pendingSnoozeUntilDateUtcMillis"

        const val CUSTOM_SNOOZE_SNOOZE_FOR_IDX = 0
        const val CUSTOM_SNOOZE_SNOOZE_UNTIL_IDX = 1
        
        /**
         * Provider for EventsStorage to enable dependency injection in tests.
         */
        var eventsStorageProvider: ((android.content.Context) -> EventsStorageInterface)? = null
        
        /**
         * Gets EventsStorage - uses provider if set, otherwise creates real instance.
         */
        fun getEventsStorage(context: android.content.Context): EventsStorageInterface {
            return eventsStorageProvider?.invoke(context) ?: EventsStorage(context)
        }

    }

}
