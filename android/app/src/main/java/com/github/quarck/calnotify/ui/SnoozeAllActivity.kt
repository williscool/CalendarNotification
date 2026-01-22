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

import android.annotation.SuppressLint
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
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
import com.github.quarck.calnotify.app.*
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
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
import androidx.core.content.ContextCompat
import android.text.method.ScrollingMovementMethod


open class SnoozeAllActivity : AppCompatActivity() {

    var state = ViewEventActivityState()

    lateinit var snoozePresets: LongArray

    lateinit var settings: Settings

    lateinit var formatter: EventFormatterInterface

    val calendarReloadManager: CalendarReloadManagerInterface = CalendarReloadManager
    val calendarProvider: CalendarProviderInterface = CalendarProvider

    var snoozeAllIsChange = false

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
    private var searchQuery: String? = null
    private var filterState: FilterState? = null
    private var selectedEventKeys: Set<String>? = null  // For multi-select mode

    val clock: CNPlusClockInterface = CNPlusSystemClock()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null)
            state = ViewEventActivityState.fromBundle(savedInstanceState)

        setContentView(R.layout.activity_snooze_all)
        setupStatusBarSpacer()

        val currentTime = clock.currentTimeMillis()

        settings = Settings(this)
        formatter = EventFormatter(this)

        snoozeAllIsChange = intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, false)

        snoozeFromMainActivity = intent.getBooleanExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, false)

      searchQuery = intent.getStringExtra(Consts.INTENT_SEARCH_QUERY)
      filterState = FilterState.fromBundle(intent.getBundleExtra(Consts.INTENT_FILTER_STATE))
      
      // Check for multi-select mode
      val selectedKeysArray = intent.getStringArrayExtra(ActiveEventsFragment.INTENT_SELECTED_EVENT_KEYS)
      selectedEventKeys = selectedKeysArray?.toSet()

      val toolbar = find<Toolbar?>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)


        // remove "MM minutes before event" snooze presents for "Snooze All"
        // and when event time has passed already
        snoozePresets = settings.snoozePresets.filter { it > 0L }.toLongArray()

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

        // Set the label text based on mode (multi-select vs all)
        val isMultiSelect = selectedEventKeys != null
        findOrThrow<TextView>(R.id.snooze_snooze_for).text = when {
            isMultiSelect && !snoozeAllIsChange -> getString(R.string.snooze_selected_events_to)
            isMultiSelect && snoozeAllIsChange -> getString(R.string.change_selected_events_to)
            !snoozeAllIsChange -> getString(R.string.snooze_all_events)
            else -> getString(R.string.change_all_events)
        }

        find<ImageView?>(R.id.snooze_view_img_custom_period)?.visibility = View.VISIBLE
        find<ImageView?>(R.id.snooze_view_img_until)?.visibility = View.VISIBLE

        // Set the title based on mode
        this.title = if (isMultiSelect) {
            val count = selectedEventKeys!!.size
            if (!snoozeAllIsChange)
                resources.getQuantityString(R.plurals.snooze_selected_title, count, count)
            else
                resources.getQuantityString(R.plurals.change_selected_title, count, count)
        } else {
            if (!snoozeAllIsChange)
                resources.getString(R.string.snooze_all_title)
            else
                resources.getString(R.string.change_all_title)
        }

        val count = intent.getIntExtra(Consts.INTENT_SEARCH_QUERY_EVENT_COUNT,  0)
        val totalFilteredCount = intent.getIntExtra(ActiveEventsFragment.INTENT_TOTAL_FILTERED_COUNT, 0)

        val snoozeCountTextView = findViewById<TextView>(R.id.snooze_count_text)
        
        // Multi-select mode: show count with filter context if applicable
        if (selectedEventKeys != null) {
            val filterDescription = filterState?.toDisplayString(this)
            val hasSearch = !searchQuery.isNullOrEmpty()
            val hasFilter = filterState?.hasActiveFilters() == true && filterDescription != null
            
            snoozeCountTextView.visibility = View.VISIBLE
            
            when {
                (hasSearch || hasFilter) && totalFilteredCount > 0 -> {
                    // Show "X selected from Y matching: filter description"
                    val filterDesc = when {
                        hasSearch && hasFilter -> getString(R.string.filter_description_search_and_filters, searchQuery, filterDescription)
                        hasSearch -> "'$searchQuery'"
                        else -> filterDescription ?: ""
                    }
                    snoozeCountTextView.text = resources.getQuantityString(
                        R.plurals.selection_count_from_filtered, count, count, totalFilteredCount, filterDesc
                    )
                }
                else -> {
                    // Simple count without filter context
                    snoozeCountTextView.text = resources.getQuantityString(
                        R.plurals.selection_count, count, count
                    )
                }
            }
        } else {
            val filterDescription = filterState?.toDisplayString(this)
            val hasSearch = !searchQuery.isNullOrEmpty()
            val hasFilter = filterState?.hasActiveFilters() == true && filterDescription != null
            
            when {
                hasSearch && hasFilter -> {
                    // Both search and filters: "3 events matching 'query', Filter1, Filter2 will be snoozed"
                    val combined = getString(R.string.filter_description_search_and_filters, searchQuery, filterDescription)
                    snoozeCountTextView.visibility = View.VISIBLE
                    snoozeCountTextView.text = resources.getQuantityString(
                        R.plurals.snooze_count_text_filtered, count, count, combined
                    )
                }
                hasSearch -> {
                    // Search only (existing behavior)
                    snoozeCountTextView.visibility = View.VISIBLE
                    snoozeCountTextView.text = resources.getQuantityString(
                        R.plurals.snooze_count_text, count, count, searchQuery
                    )
                }
                hasFilter -> {
                    // Filters only
                    snoozeCountTextView.visibility = View.VISIBLE
                    snoozeCountTextView.text = resources.getQuantityString(
                        R.plurals.snooze_count_text_filtered, count, count, filterDescription
                    )
                }
                else -> {
                    snoozeCountTextView.visibility = View.GONE
                }
            }
        }

        restoreState(state)
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

    private fun snoozeEvent(snoozeDelay: Long) {
        MaterialAlertDialogBuilder(this)
                .setMessage(getConfirmationMessage())
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes) {
                    _, _ ->

                    DevLog.debug(LOG_TAG, "Snoozing (change=$snoozeAllIsChange) requests, snoozeDelay=${snoozeDelay / 1000L}")

                    val result = if (selectedEventKeys != null) {
                        // Multi-select mode: snooze only selected events
                        ApplicationController.snoozeSelectedEvents(
                            this,
                            selectedEventKeys!!,
                            snoozeDelay,
                            snoozeAllIsChange
                        )
                    } else {
                        // Normal mode: snooze all (with optional filter/search)
                        ApplicationController.snoozeAllEvents(
                            this, 
                            snoozeDelay, 
                            snoozeAllIsChange, 
                            false, 
                            searchQuery,
                            filterState
                        )
                    }
                    if (result != null) {
                        result.toast(this)
                    }
                    finish()
                }
                .setNegativeButton(R.string.cancel) {
                    _, _ ->
                }
                .create()
                .show()
    }
    
    private fun getConfirmationMessage(): String {
        // Multi-select mode: simple confirmation with count
        if (selectedEventKeys != null) {
            val count = selectedEventKeys!!.size
            return if (snoozeAllIsChange) {
                resources.getQuantityString(R.plurals.change_selected_confirmation, count, count)
            } else {
                resources.getQuantityString(R.plurals.snooze_selected_confirmation, count, count)
            }
        }
        
        val filterDescription = filterState?.toDisplayString(this)
        val hasSearch = !searchQuery.isNullOrEmpty()
        val hasFilter = filterState?.hasActiveFilters() == true && filterDescription != null
        
        return when {
            hasSearch || hasFilter -> {
                val combined = when {
                    hasSearch && hasFilter -> getString(R.string.filter_description_search_and_filters, searchQuery, filterDescription)
                    hasSearch -> "\"$searchQuery\""
                    else -> filterDescription!!
                }
                if (snoozeAllIsChange)
                    getString(R.string.change_filtered_with_filter_confirmation, combined)
                else
                    getString(R.string.snooze_filtered_with_filter_confirmation, combined)
            }
            snoozeAllIsChange -> getString(R.string.change_all_notification)
            else -> getString(R.string.snooze_all_confirmation)
        }
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
        val initialDate = clock.currentTimeMillis()
        
        val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.choose_date)
                .setSelection(initialDate)
                .build()

        datePicker.addOnPositiveButtonClickListener { dateSelection ->
            showSnoozeUntilTimePicker(dateSelection)
        }

        datePicker.show(supportFragmentManager, "snoozeUntilDatePicker")
    }

    private fun showSnoozeUntilTimePicker(dateSelection: Long) {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(this)
        val cal = Calendar.getInstance()

        val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(if (is24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(cal.get(Calendar.HOUR_OF_DAY))
                .setMinute(cal.get(Calendar.MINUTE))
                .setTitleText(getString(R.string.choose_time) + " - " + 
                        DateUtils.formatDateTime(this, dateSelection, DateUtils.FORMAT_SHOW_DATE))
                .build()

        timePicker.addOnPositiveButtonClickListener {
            val date = Calendar.getInstance()
            date.timeInMillis = dateSelection
            date.set(Calendar.HOUR_OF_DAY, timePicker.hour)
            date.set(Calendar.MINUTE, timePicker.minute)

            val snoozeFor = date.timeInMillis - clock.currentTimeMillis() + Consts.ALARM_THRESHOLD

            if (snoozeFor > 0L) {
                snoozeEvent(snoozeFor)
            } else {
                MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.selected_time_is_in_the_past)
                        .setNegativeButton(R.string.cancel, null)
                        .show()
            }
        }

        timePicker.show(supportFragmentManager, "snoozeUntilTimePicker")
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonRescheduleCustomClick(v: View?) {
    }

    companion object {
        private const val LOG_TAG = "ActivitySnoozeAll"
    }

}
