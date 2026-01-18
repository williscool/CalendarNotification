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
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarProviderInterface
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import com.github.quarck.calnotify.monitorstorage.MonitorStorageInterface
import com.github.quarck.calnotify.upcoming.UpcomingEventsProvider
import com.github.quarck.calnotify.utils.CNPlusSystemClock
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.background

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
            launchPreActionActivity(event)
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
        // Upcoming events are not removed via swipe-to-dismiss like Active events.
        // Instead, pre-actions (dismiss/snooze) go through PreActionActivity which
        // updates MonitorStorage directly. The list refreshes via broadcast receiver.
    }

    override fun onItemRestored(event: EventAlertRecord) {
        // Upcoming events don't support undo/restore from swipe because they use
        // PreActionActivity for all actions. No swipe = no undo needed.
    }

    override fun onScrollPositionChange(newPos: Int) {
        // Not needed
    }
    
    /**
     * Launches PreActionActivity for the given event.
     * Provides full pre-action UI with snooze presets, mute toggle, etc.
     */
    private fun launchPreActionActivity(event: EventAlertRecord) {
        val ctx = context ?: return
        startActivity(PreActionActivity.createIntent(ctx, event))
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
