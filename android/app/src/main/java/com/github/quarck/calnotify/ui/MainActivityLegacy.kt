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

import android.app.AlertDialog
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.github.quarck.calnotify.BuildConfig
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.app.UndoState
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.isSpecial
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.globalState
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.findOrThrow
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

/**
 * BroadcastReceiver for data update events in Legacy UI.
 */
class DataUpdatedReceiverLegacy(val activity: MainActivityLegacy) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val isUserCaused = intent?.getBooleanExtra(Consts.INTENT_IS_USER_ACTION, false) ?: false
        activity.onDataUpdated(causedByUser = isUserCaused)
    }
}

/**
 * Legacy MainActivity implementation.
 * Uses the original monolithic UI with a single RecyclerView showing all events.
 */
class MainActivityLegacy : MainActivityBase(), EventListCallback {

    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var reloadLayout: RelativeLayout

    private lateinit var newStyleMessageLayout: View
    private lateinit var quietHoursLayout: RelativeLayout
    private lateinit var quietHoursTextView: TextView
    private var refreshLayout: SwipeRefreshLayout? = null

    private lateinit var floatingAddEvent: FloatingActionButton

    private lateinit var adapter: EventListAdapter

    private var lastEventDismissalScrollPosition: Int? = null

    private val undoDisappearSensitivity: Float by lazy {
        resources.getDimension(R.dimen.undo_dismiss_sensitivity)
    }

    private val dataUpdatedReceiver = DataUpdatedReceiverLegacy(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        setContentView(R.layout.activity_main_legacy)
        val toolbar = find<Toolbar?>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Set status bar color explicitly to match toolbar (fixes edge-to-edge displays)
        window.statusBarColor = getColor(R.color.primary_dark)

        // Set status bar spacer height
        findViewById<View>(R.id.status_bar_spacer)?.let { spacer ->
            ViewCompat.setOnApplyWindowInsetsListener(spacer) { view, insets ->
                val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                view.layoutParams.height = statusBarInset
                view.requestLayout()
                insets
            }
            ViewCompat.requestApplyInsets(spacer)
        }

        // Apply nav bar inset to FAB bottom margin
        findViewById<FloatingActionButton>(R.id.action_btn_add_event)?.let { fab ->
            ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
                val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                val params = view.layoutParams as CoordinatorLayout.LayoutParams
                val defaultMargin = resources.getDimensionPixelSize(R.dimen.fab_margin)
                params.bottomMargin = defaultMargin + navBarInset
                view.layoutParams = params
                insets
            }
            ViewCompat.requestApplyInsets(fab)
        }

        shouldForceRepost = (clock.currentTimeMillis() - (globalState?.lastNotificationRePost ?: 0L)) > Consts.MIN_FORCE_REPOST_INTERVAL

        refreshLayout = find<SwipeRefreshLayout?>(R.id.cardview_refresh_layout)

        refreshLayout?.setOnRefreshListener {
            reloadLayout.visibility = View.GONE
            reloadData()
        }

        shouldRemindForEventsWithNoReminders = settings.shouldRemindForEventsWithNoReminders

        adapter = EventListAdapter(this, this)

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = findOrThrow<RecyclerView>(R.id.list_events)
        recyclerView.layoutManager = staggeredLayoutManager
        recyclerView.adapter = adapter
        adapter.recyclerView = recyclerView

        // Apply nav bar inset as bottom padding so last item isn't hidden
        recyclerView.clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { view, insets ->
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navBarInset)
            insets
        }
        ViewCompat.requestApplyInsets(recyclerView)

        reloadLayout = findOrThrow<RelativeLayout>(R.id.activity_main_reload_layout)

        quietHoursLayout = findOrThrow<RelativeLayout>(R.id.activity_main_quiet_hours_info_layout)
        quietHoursTextView = findOrThrow<TextView>(R.id.activity_main_quiet_hours)

        newStyleMessageLayout = findOrThrow<View>(R.id.activity_main_new_style_message_layout)

        calendarRescanEnabled = settings.enableCalendarRescan

        floatingAddEvent = findOrThrow<FloatingActionButton>(R.id.action_btn_add_event)

        floatingAddEvent.setOnClickListener {
            startActivity(
                Intent(this, EditEventActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    override fun onResume() {
        super.onResume()

        // Android 13+ (API 33) requires specifying receiver export flags for runtime-registered receivers
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dataUpdatedReceiver, IntentFilter(Consts.DATA_UPDATED_BROADCAST), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dataUpdatedReceiver, IntentFilter(Consts.DATA_UPDATED_BROADCAST))
        }

        reloadData()

        if (undoManager.canUndo) {
            val coordinatorLayout = findOrThrow<CoordinatorLayout>(R.id.main_activity_coordinator)
            Snackbar.make(coordinatorLayout, resources.getString(R.string.event_dismissed), Snackbar.LENGTH_LONG)
                .setAction(resources.getString(R.string.undo)) { onUndoButtonClick(null) }
                .show()
        }
    }

    override fun onPause() {
        unregisterReceiver(dataUpdatedReceiver)
        super.onPause()
    }

    private fun reloadData() {
        background {
            // Purge old dismissed events based on user setting (skip if "forever")
            val keepHistoryMillis = settings.keepHistoryMillis
            if (keepHistoryMillis < Long.MAX_VALUE) {
                getDismissedEventsStorage(this).classCustomUse {
                    it.purgeOld(clock.currentTimeMillis(), keepHistoryMillis)
                }
            }

            // Clean up any orphaned events (events in both storages due to failed deletions)
            cleanupOrphanedEvents(this)

            val events = getEventsStorage(this).classCustomUse { db ->
                db.eventsForDisplay.toTypedArray()
            }

            val quietPeriodUntil = QuietHoursManager(this).getSilentUntil(settings)

            runOnUiThread {
                adapter.setEventsToDisplay(events)
                onNumEventsUpdated()

                if (quietPeriodUntil > 0L) {
                    quietHoursTextView.text = String.format(
                        resources.getString(R.string.quiet_hours_main_activity_status),
                        DateUtils.formatDateTime(
                            this, quietPeriodUntil,
                            if (DateUtils.isToday(quietPeriodUntil))
                                DateUtils.FORMAT_SHOW_TIME
                            else
                                DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE
                        )
                    )
                    quietHoursLayout.visibility = View.VISIBLE
                } else {
                    quietHoursLayout.visibility = View.GONE
                }

                refreshLayout?.isRefreshing = false
            }
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onUndoButtonClick(v: View?) {
        undoManager.undo()
        reloadData()
    }

    private fun onNumEventsUpdated() {
        val hasEvents = adapter.itemCount > 0
        findOrThrow<TextView>(R.id.empty_view).visibility = if (hasEvents) View.GONE else View.VISIBLE
        this.invalidateOptionsMenu()
    }

    fun onDataUpdated(causedByUser: Boolean) {
        if (causedByUser)
            reloadData()
        else
            runOnUiThread { reloadLayout.visibility = View.VISIBLE }
    }

    // === Menu handling ===

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)

        searchMenuItem = menu.findItem(R.id.action_search)
        searchMenuItem?.isVisible = true
        searchMenuItem?.isEnabled = true

        // Intercept back press collapse to implement two-stage search dismissal
        searchMenuItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                // If SearchView has focus (keyboard visible), just hide keyboard
                if (searchView?.hasFocus() == true) {
                    searchView?.clearFocus()
                    return false  // Prevent collapse
                }
                // If there's an active filter, clear it and allow collapse
                if (!adapter.searchString.isNullOrEmpty()) {
                    searchView?.setQuery("", false)
                    adapter.setSearchText(null)
                    adapter.setEventsToDisplay()
                }
                return true  // Allow collapse
            }
        })

        searchView = searchMenuItem?.actionView as? SearchView
        val manager = getSystemService(Context.SEARCH_SERVICE) as SearchManager

        searchView?.queryHint = resources.getQuantityString(R.plurals.search_placeholder, adapter.getAllItemCount(), adapter.getAllItemCount())
        searchView?.setSearchableInfo(manager.getSearchableInfo(componentName))

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                adapter.setSearchText(query)
                searchView?.clearFocus()  // Hide keyboard but keep SearchView expanded
                searchView?.setQuery(query, false)
                adapter.setEventsToDisplay()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.setSearchText(newText)
                adapter.setEventsToDisplay()
                return true
            }
        })

        val closebutton: View? = searchView?.findViewById(androidx.appcompat.R.id.search_close_btn)
        closebutton?.setOnClickListener {
            searchView?.setQuery("", false)
            searchView?.clearFocus()
            adapter.setSearchText(null)
            adapter.setEventsToDisplay()
            searchMenuItem?.collapseActionView()
        }

        val menuItem = menu.findItem(R.id.action_snooze_all)
        if (menuItem != null) {
            menuItem.isEnabled = adapter.itemCount > 0
            menuItem.title = resources.getString(
                if (adapter.hasActiveEvents) R.string.snooze_all else R.string.change_all
            )
        }

        val muteAllMenuItem = menu.findItem(R.id.action_mute_all)
        if (muteAllMenuItem != null) {
            muteAllMenuItem.isVisible = settings.enableNotificationMute
            muteAllMenuItem.isEnabled = adapter.anyForMute
        }

        val dismissedEventsMenuItem = menu.findItem(R.id.action_dismissed_events)
        if (dismissedEventsMenuItem != null) {
            dismissedEventsMenuItem.isEnabled = true
            dismissedEventsMenuItem.isVisible = true
        }

        val dismissAll = menu.findItem(R.id.action_dismiss_all)
        if (dismissAll != null) {
            dismissAll.isEnabled = adapter.anyForDismissAllButRecentAndSnoozed
        }

        val customQuiet = menu.findItem(R.id.action_custom_quiet_interval)
        if (customQuiet != null) {
            customQuiet.isVisible = true
            customQuiet.title = resources.getString(
                if (ApplicationController.isCustomQuietHoursActive(this))
                    R.string.stop_quiet_hours
                else
                    R.string.start_quiet_hours
            )
        }

        // DEV_PAGE_ENABLED is set via local.properties (gitignored, can't leak to releases)
        // devModeEnabled is the easter egg fallback (tap 13x in Report a Bug)
        menu.findItem(R.id.action_test_page)?.isVisible = BuildConfig.DEV_PAGE_ENABLED || settings.devModeEnabled

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Try common handlers first
        if (handleCommonMenuItem(item)) {
            return true
        }

        refreshReminderLastFired()

        when (item.itemId) {
            R.id.action_snooze_all -> {
                startActivity(
                    Intent(this, SnoozeAllActivity::class.java)
                        .putExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, !adapter.hasActiveEvents)
                        .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                        .putExtra(Consts.INTENT_SEARCH_QUERY, adapter.searchString)
                        .putExtra(Consts.INTENT_SEARCH_QUERY_EVENT_COUNT, adapter.itemCount)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }

            R.id.action_mute_all -> onMuteAll()

            R.id.action_dismissed_events -> {
                startActivity(
                    Intent(this, DismissedEventsActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }

            R.id.action_dismiss_all -> onDismissAll()

            R.id.action_custom_quiet_interval -> onCustomQuietHours()
        }

        return super.onOptionsItemSelected(item)
    }

    // === Bulk actions ===

    private fun onDismissAll() {
        AlertDialog.Builder(this)
            .setMessage(R.string.dismiss_all_events_confirmation)
            .setCancelable(false)
            .setPositiveButton(android.R.string.yes) { _, _ ->
                doDismissAll()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .create()
            .show()
    }

    private fun doDismissAll() {
        ApplicationController.dismissAllButRecentAndSnoozed(
            this, EventDismissType.ManuallyDismissedFromActivity
        )
        reloadData()
        lastEventDismissalScrollPosition = null
        onNumEventsUpdated()
    }

    private fun onMuteAll() {
        AlertDialog.Builder(this)
            .setMessage(R.string.mute_all_events_question)
            .setCancelable(false)
            .setPositiveButton(android.R.string.yes) { _, _ ->
                doMuteAll()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .create()
            .show()
    }

    private fun doMuteAll() {
        ApplicationController.muteAllVisibleEvents(this)
        reloadData()
        lastEventDismissalScrollPosition = null
        onNumEventsUpdated()
    }

    private fun onCustomQuietHours() {
        if (!ApplicationController.isCustomQuietHoursActive(this)) {
            val intervalValues: IntArray = resources.getIntArray(R.array.custom_quiet_hours_interval_values)
            val intervalNames: Array<String> = resources.getStringArray(R.array.custom_quiet_hours_interval_names)

            val builder = AlertDialog.Builder(this)
            val listAdapter = ArrayAdapter<String>(this, R.layout.simple_list_item_large)

            builder.setTitle(getString(R.string.start_quiet_hours_dialog_title))
            listAdapter.addAll(intervalNames.toMutableList())
            builder.setCancelable(true)
            builder.setAdapter(listAdapter) { _, which ->
                if (which in intervalValues.indices) {
                    ApplicationController.applyCustomQuietHoursForSeconds(this, intervalValues[which])
                    reloadData()
                }
            }

            builder.show()
        } else {
            ApplicationController.applyCustomQuietHoursForSeconds(this, 0)
            reloadData()
        }
    }

    // === EventListCallback implementation ===

    override fun onItemClick(v: View, position: Int, eventId: Long) {
        DevLog.info(LOG_TAG, "onItemClick, pos=$position, eventId=$eventId")

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null && !event.isSpecial) {
            startActivity(
                Intent(this, ViewEventActivity::class.java)
                    .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                    .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                    .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                    .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    override fun onItemRemoved(event: EventAlertRecord) {
        DevLog.info(LOG_TAG, "onItemRemoved: Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
        ApplicationController.dismissEvent(this, EventDismissType.ManuallyDismissedFromActivity, event)
        lastEventDismissalScrollPosition = adapter.scrollPosition
        onNumEventsUpdated()
    }

    override fun onItemRestored(event: EventAlertRecord) {
        DevLog.info(LOG_TAG, "onItemRestored, eventId=${event.eventId}")
        ApplicationController.restoreEvent(this, event)
        onNumEventsUpdated()
    }

    override fun onScrollPositionChange(newPos: Int) {
        val undoSense = lastEventDismissalScrollPosition
        if (undoSense != null) {
            if (Math.abs(undoSense - newPos) > undoDisappearSensitivity) {
                lastEventDismissalScrollPosition = null
                adapter.clearUndoState()
            }
        }
    }

    companion object {
        private const val LOG_TAG = "MainActivityLegacy"
    }
}
