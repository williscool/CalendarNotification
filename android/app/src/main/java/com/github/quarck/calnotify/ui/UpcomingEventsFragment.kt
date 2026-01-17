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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.CalendarIntents
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarProviderInterface
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import com.github.quarck.calnotify.monitorstorage.MonitorStorageInterface
import com.github.quarck.calnotify.prefs.PreferenceUtils
import com.github.quarck.calnotify.upcoming.UpcomingEventsProvider
import com.github.quarck.calnotify.utils.CNPlusSystemClock
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.background
import com.google.android.material.snackbar.Snackbar

/**
 * Fragment for displaying upcoming events (before their notification fires).
 * Shows events within the lookahead window that haven't been handled yet.
 * 
 * Pre-actions available:
 * - Pre-mute: Mark event to fire silently (Milestone 2, Phase 6.1)
 * - Pre-snooze: Snooze before notification fires (Milestone 2, Phase 6.2)
 * - Pre-dismiss: Dismiss without ever firing (Milestone 2, Phase 6.3)
 */
class UpcomingEventsFragment : Fragment(), EventListCallback, SearchableFragment {

    private lateinit var settings: Settings
    private val clock = CNPlusSystemClock()
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var refreshLayout: SwipeRefreshLayout
    private lateinit var emptyView: TextView
    private lateinit var adapter: EventListAdapter
    
    private val dataUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadEvents()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_event_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize settings synchronously while fragment is attached
        settings = Settings(requireContext())
        
        recyclerView = view.findViewById(R.id.recycler_view)
        refreshLayout = view.findViewById(R.id.refresh_layout)
        emptyView = view.findViewById(R.id.empty_view)
        
        emptyView.text = getString(R.string.empty_upcoming)
        
        // Use EventListAdapter in read-only mode - disable swipe for Milestone 1
        // Pre-actions (snooze, dismiss, mute) will be added in Milestone 2
        adapter = EventListAdapter(requireContext(), this, swipeEnabled = false)
        recyclerView.layoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter
        adapter.recyclerView = recyclerView
        
        refreshLayout.setOnRefreshListener {
            loadEvents()
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Register for data update broadcasts
        val ctx = context ?: return
        ContextCompat.registerReceiver(
            ctx,
            dataUpdatedReceiver,
            IntentFilter(Consts.DATA_UPDATED_BROADCAST),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        
        loadEvents()
    }
    
    override fun onPause() {
        super.onPause()
        context?.unregisterReceiver(dataUpdatedReceiver)
    }

    private fun loadEvents() {
        val ctx = context ?: return
        background {
            val events = getMonitorStorage(ctx).use { storage ->
                val provider = UpcomingEventsProvider(
                    context = ctx,
                    settings = settings,
                    clock = clock,
                    monitorStorage = storage,
                    calendarProvider = getCalendarProvider()
                )
                provider.getUpcomingEvents().toTypedArray()
            }
            
            activity?.runOnUiThread {
                adapter.setEventsToDisplay(events)
                updateEmptyState()
                refreshLayout.isRefreshing = false
                // Update search hint with new event count
                activity?.invalidateOptionsMenu()
            }
        }
    }

    private fun updateEmptyState() {
        emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    // EventListCallback implementation
    
    override fun onItemClick(v: View, position: Int, eventId: Long) {
        DevLog.info(LOG_TAG, "onItemClick, pos=$position, eventId=$eventId")
        
        val event = adapter.getEventAtPosition(position, eventId)
        if (event != null) {
            showUpcomingEventActionDialog(event)
        }
    }

    override fun onItemDismiss(v: View, position: Int, eventId: Long) {
        // Pre-dismiss will be implemented in Phase 6.3
        DevLog.info(LOG_TAG, "onItemDismiss (not yet implemented), pos=$position, eventId=$eventId")
    }

    override fun onItemSnooze(v: View, position: Int, eventId: Long) {
        // Pre-snooze will be implemented in Phase 6.2
        DevLog.info(LOG_TAG, "onItemSnooze (not yet implemented), pos=$position, eventId=$eventId")
    }

    override fun onItemRemoved(event: EventAlertRecord) {
        // Not used for upcoming events
    }

    override fun onItemRestored(event: EventAlertRecord) {
        // Not used for upcoming events
    }

    override fun onScrollPositionChange(newPos: Int) {
        // Not needed
    }
    
    // === Pre-action handlers ===
    
    /**
     * Shows the action dialog for an upcoming event.
     * Available actions: Snooze, Mute/Unmute, View in Calendar
     * (Dismiss will be added in Phase 6.3)
     */
    private fun showUpcomingEventActionDialog(event: EventAlertRecord) {
        val ctx = context ?: return
        val isMuted = event.isMuted
        
        val actions = arrayOf(
            getString(R.string.pre_snooze),
            getString(if (isMuted) R.string.pre_unmute else R.string.pre_mute),
            getString(R.string.view_in_calendar)
            // Dismiss will be added in Phase 6.3
        )
        
        AlertDialog.Builder(ctx)
            .setTitle(event.title.ifEmpty { getString(R.string.empty_title) })
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showPreSnoozePicker(event)
                    1 -> if (isMuted) handleUnPreMute(event) else handlePreMute(event)
                    2 -> CalendarIntents.viewCalendarEvent(ctx, event)
                }
            }
            .show()
    }
    
    /**
     * Marks an upcoming event to be muted when its notification fires.
     * Sets the preMuted flag in MonitorStorage.
     */
    private fun handlePreMute(event: EventAlertRecord) {
        val ctx = context ?: return
        background {
            var success = false
            getMonitorStorage(ctx).use { storage ->
                val alert = storage.getAlert(event.eventId, event.alertTime, event.instanceStartTime)
                if (alert != null) {
                    storage.updateAlert(alert.withPreMuted(true))
                    success = true
                    DevLog.info(LOG_TAG, "Pre-muted event ${event.eventId}")
                } else {
                    DevLog.warn(LOG_TAG, "Could not find alert for event ${event.eventId} to pre-mute")
                }
            }
            
            activity?.runOnUiThread {
                if (success) {
                    loadEvents() // Refresh to show mute indicator
                    view?.let { v ->
                        Snackbar.make(v, R.string.event_will_be_muted, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    /**
     * Removes the pre-muted flag from an upcoming event.
     * Clears the preMuted flag in MonitorStorage.
     */
    private fun handleUnPreMute(event: EventAlertRecord) {
        val ctx = context ?: return
        background {
            var success = false
            getMonitorStorage(ctx).use { storage ->
                val alert = storage.getAlert(event.eventId, event.alertTime, event.instanceStartTime)
                if (alert != null) {
                    storage.updateAlert(alert.withPreMuted(false))
                    success = true
                    DevLog.info(LOG_TAG, "Un-pre-muted event ${event.eventId}")
                } else {
                    DevLog.warn(LOG_TAG, "Could not find alert for event ${event.eventId} to un-pre-mute")
                }
            }
            
            activity?.runOnUiThread {
                if (success) {
                    loadEvents() // Refresh to remove mute indicator
                    view?.let { v ->
                        Snackbar.make(v, R.string.event_unmuted, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    /**
     * Shows a picker dialog with snooze preset durations.
     * Only shows positive presets (not "X minutes before event" presets).
     */
    private fun showPreSnoozePicker(event: EventAlertRecord) {
        val ctx = context ?: return
        
        // Get snooze presets, filter to only positive values (not "before event" presets)
        val presets = settings.snoozePresets.filter { it > 0L }.toLongArray()
        
        if (presets.isEmpty()) {
            DevLog.warn(LOG_TAG, "No positive snooze presets available")
            return
        }
        
        val labels = presets.map { PreferenceUtils.formatSnoozePreset(it) }.toTypedArray()
        
        AlertDialog.Builder(ctx)
            .setTitle(R.string.pre_snooze)
            .setItems(labels) { _, which ->
                val snoozeUntil = getClock().currentTimeMillis() + presets[which]
                handlePreSnooze(event, snoozeUntil)
            }
            .show()
    }
    
    /**
     * Pre-snoozes an upcoming event.
     * - Marks alert as handled in MonitorStorage
     * - Adds event to EventsStorage with snoozedUntil set
     * - Reschedules alarms
     */
    private fun handlePreSnooze(event: EventAlertRecord, snoozeUntil: Long) {
        val ctx = context ?: return
        background {
            var success = false
            
            // 1. Mark as handled in MonitorStorage (so it won't fire normally)
            getMonitorStorage(ctx).use { storage ->
                val alert = storage.getAlert(event.eventId, event.alertTime, event.instanceStartTime)
                if (alert != null) {
                    storage.updateAlert(alert.copy(wasHandled = true))
                    DevLog.info(LOG_TAG, "Marked alert as handled for pre-snooze: event ${event.eventId}")
                } else {
                    DevLog.warn(LOG_TAG, "Could not find alert for event ${event.eventId} to mark as handled")
                }
            }
            
            // 2. Add to EventsStorage as snoozed
            val currentTime = getClock().currentTimeMillis()
            val snoozedEvent = event.copy(
                snoozedUntil = snoozeUntil,
                lastStatusChangeTime = currentTime,
                displayStatus = EventDisplayStatus.Hidden
            )
            
            EventsStorage(ctx).use { db ->
                success = db.addEvent(snoozedEvent)
                if (success) {
                    DevLog.info(LOG_TAG, "Pre-snoozed event ${event.eventId} until $snoozeUntil")
                } else {
                    DevLog.error(LOG_TAG, "Failed to add pre-snoozed event ${event.eventId} to storage")
                }
            }
            
            // 3. Reschedule alarms
            if (success) {
                ApplicationController.afterCalendarEventFired(ctx)
            }
            
            activity?.runOnUiThread {
                if (success) {
                    loadEvents() // Event disappears from Upcoming
                    view?.let { v ->
                        Snackbar.make(v, R.string.event_pre_snoozed, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // SearchableFragment implementation
    
    override fun setSearchQuery(query: String?) {
        adapter.setSearchText(query)
        updateEmptyState()
    }
    
    override fun getSearchQuery(): String? = adapter.searchString
    
    override fun getEventCount(): Int = adapter.getAllItemCount()

    companion object {
        private const val LOG_TAG = "UpcomingEventsFragment"
        
        /** Provider for MonitorStorage - enables DI for testing */
        var monitorStorageProvider: ((Context) -> MonitorStorageInterface)? = null
        
        /** Provider for CalendarProvider - enables DI for testing */
        var calendarProviderProvider: (() -> CalendarProviderInterface)? = null
        
        /** Provider for Clock - enables DI for testing */
        var clockProvider: (() -> CNPlusClockInterface)? = null
        
        /** Gets MonitorStorage - uses provider if set, otherwise creates real instance */
        fun getMonitorStorage(ctx: Context): MonitorStorageInterface =
            monitorStorageProvider?.invoke(ctx) ?: MonitorStorage(ctx)
        
        /** Gets CalendarProvider - uses provider if set, otherwise returns real instance */
        fun getCalendarProvider(): CalendarProviderInterface =
            calendarProviderProvider?.invoke() ?: CalendarProvider
        
        /** Gets Clock - uses provider if set, otherwise returns real instance */
        fun getClock(): CNPlusClockInterface =
            clockProvider?.invoke() ?: CNPlusSystemClock()
        
        /** Reset providers - call in @After to prevent test pollution */
        fun resetProviders() {
            monitorStorageProvider = null
            calendarProviderProvider = null
            clockProvider = null
        }
    }
}
