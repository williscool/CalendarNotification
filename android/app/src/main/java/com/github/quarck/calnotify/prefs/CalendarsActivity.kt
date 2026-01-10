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
package com.github.quarck.calnotify.prefs

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings as AppSettings
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.findOrThrow

enum class CalendarListEntryType {Header, Calendar, Divider }

class CalendarListEntry(
        val type: CalendarListEntryType,
        val headerTitle: String? = null,
        val calendar: CalendarRecord? = null,
        var isHandled: Boolean = true,
        val upcomingEventCount: Int = 0
)


class CalendarListAdapter(val context: Context, var entries: Array<CalendarListEntry>)
    : RecyclerView.Adapter<CalendarListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.calendar_view, parent, false);
        return ViewHolder(view);
    }

    inner class ViewHolder(itemView: View)
        : RecyclerView.ViewHolder(itemView) {

        var entry: CalendarListEntry? = null
        var view: LinearLayout
        var calendarAccountName: TextView
        var checkboxCalendarName: CheckBox
        var colorView: View
        var calendarEntryLayout: LinearLayout
        var spacingView: View

        init {
            view = itemView.findOrThrow<LinearLayout>(R.id.linearLyaoutCalendarView)

            calendarAccountName = view.findOrThrow<TextView>(R.id.textViewCalendarOwner)
            checkboxCalendarName = view.findOrThrow<CheckBox>(R.id.checkBoxCalendarSelection)
            colorView = view.findOrThrow<View>(R.id.viewCalendarColor)
            calendarEntryLayout = view.findOrThrow<LinearLayout>(R.id.linearLayoutCalendarEntry)
            spacingView = view.findOrThrow<View>(R.id.viewCalendarsSpacing)

            checkboxCalendarName.setOnClickListener {
                view ->
                val action = onItemChanged
                val ent = entry

                if (ent != null && ent.calendar != null) {
                    ent.isHandled = checkboxCalendarName.isChecked
                    if (action != null)
                        action(view, ent.calendar.calendarId, ent.isHandled)
                }
            }
        }
    }

    var onItemChanged: ((View, Long, Boolean) -> Unit)? = null;

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        if (position >= 0 && position < entries.size) {

            val entry = entries[position]

            holder.entry = entry

            when (entry.type) {
                CalendarListEntryType.Header -> {
                    holder.calendarAccountName.text = entry.headerTitle
                    holder.calendarAccountName.visibility = View.VISIBLE
                    holder.calendarEntryLayout.visibility = View.GONE
                    holder.spacingView.visibility = View.GONE
                }

                CalendarListEntryType.Calendar -> {
                    val calendarName = entry.calendar?.name ?: ""
                    // Show upcoming event count for unhandled calendars with events
                    val displayText = if (!entry.isHandled && entry.upcomingEventCount > 0) {
                        context.getString(R.string.calendar_name_with_event_count, calendarName, entry.upcomingEventCount)
                    } else {
                        calendarName
                    }
                    holder.checkboxCalendarName.text = displayText
                    holder.calendarAccountName.visibility = View.GONE
                    holder.calendarEntryLayout.visibility = View.VISIBLE
                    holder.colorView.background = ColorDrawable(entry.calendar?.color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR)
                    holder.checkboxCalendarName.isChecked = entry.isHandled
                    holder.spacingView.visibility = View.GONE
                }

                CalendarListEntryType.Divider -> {
                    holder.calendarEntryLayout.visibility = View.GONE
                    holder.calendarAccountName.visibility = View.GONE
                    holder.spacingView.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return entries.size;
    }
}


class CalendarsActivity : AppCompatActivity() {

    private lateinit var adapter: CalendarListAdapter
    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var noCalendarsText: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DevLog.debug(LOG_TAG, "onCreate")

        setContentView(R.layout.activity_calendars)
        setSupportActionBar(find<Toolbar?>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        settings = AppSettings(this)

        adapter = CalendarListAdapter(this, arrayOf<CalendarListEntry>())

        adapter.onItemChanged = {
            _, calendarId, isEnabled ->
            DevLog.debug(LOG_TAG, "Item has changed: $calendarId $isEnabled");

            settings.setCalendarIsHandled(calendarId, isEnabled)
        }

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = findOrThrow<RecyclerView>(R.id.list_calendars)
        recyclerView.layoutManager = staggeredLayoutManager;
        recyclerView.adapter = adapter;

        noCalendarsText = findOrThrow<TextView>(R.id.no_calendars_text)

        swipeRefreshLayout = findOrThrow<SwipeRefreshLayout>(R.id.swipe_refresh_calendars)
        swipeRefreshLayout.setOnRefreshListener {
            requestCalendarSyncAndRefresh()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_calendars, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh_calendars -> {
                swipeRefreshLayout.isRefreshing = true
                requestCalendarSyncAndRefresh()
                true
            }
            R.id.action_calendar_sync_help -> {
                showCalendarSyncHelp()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun requestCalendarSyncAndRefresh() {
        background {
            try {
                val extras = Bundle().apply {
                    putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                }
                ContentResolver.requestSync(null, CalendarContract.AUTHORITY, extras)
                DevLog.info(LOG_TAG, "Requested calendar sync")

                // Wait for sync to start/complete
                Thread.sleep(SYNC_WAIT_MS)
            } catch (ex: SecurityException) {
                DevLog.error(LOG_TAG, "SecurityException requesting sync: ${ex.message}")
            }

            loadCalendars()

            runOnUiThread {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun showCalendarSyncHelp() {
        AlertDialog.Builder(this)
            .setTitle(R.string.calendar_sync_help_title)
            .setMessage(R.string.calendar_sync_help_message)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.open_sync_settings) { _, _ ->
                openSyncSettings()
            }
            .show()
    }

    private fun openSyncSettings() {
        try {
            val intent = Intent(Settings.ACTION_SYNC_SETTINGS)
            startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            DevLog.error(LOG_TAG, "Could not open sync settings: ${ex.message}")
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun loadCalendars() {
        val calendars = CalendarProvider.getCalendars(this).toTypedArray()
        val upcomingCounts = CalendarProvider.getUpcomingEventCountsByCalendar(this, UPCOMING_EVENTS_DAYS)

        val entries = mutableListOf<CalendarListEntry>()

        // Arrange entries by accountName calendar
        for ((accountName, type) in calendars.map { Pair(it.accountName, it.accountType) }.toSet()) {

            // Add group title
            entries.add(CalendarListEntry(type = CalendarListEntryType.Header, headerTitle = accountName))

            // Add all the calendars for this accountName
            entries.addAll(
                    calendars
                            .filter { it.accountName == accountName && it.accountType == type }
                            .sortedBy { it.calendarId }
                            .map {
                                CalendarListEntry(
                                        type = CalendarListEntryType.Calendar,
                                        calendar = it,
                                        isHandled = settings.getCalendarIsHandled(it.calendarId),
                                        upcomingEventCount = upcomingCounts[it.calendarId] ?: 0)
                            })

            // Add a divider
            entries.add(CalendarListEntry(type = CalendarListEntryType.Divider))
        }

        // remove last divider
        if (entries.size >= 1 && entries[entries.size - 1].type == CalendarListEntryType.Divider)
            entries.removeAt(entries.size - 1)

        val entriesFinal = entries.toTypedArray()

        runOnUiThread {
            noCalendarsText.visibility = if (entriesFinal.isNotEmpty()) View.GONE else View.VISIBLE

            adapter.entries = entriesFinal
            adapter.notifyDataSetChanged();
        }
    }

    override fun onResume() {
        super.onResume()

        background {
            loadCalendars()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val LOG_TAG = "CalendarsActivity"
        private const val SYNC_WAIT_MS = 2000L
        private const val UPCOMING_EVENTS_DAYS = 7
    }
}
