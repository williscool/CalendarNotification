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

import android.content.Intent
import android.os.Bundle
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
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import com.github.quarck.calnotify.upcoming.UpcomingEventsProvider
import com.github.quarck.calnotify.utils.CNPlusSystemClock
import com.github.quarck.calnotify.utils.background

/**
 * Fragment for displaying upcoming events (before their notification fires).
 * Shows events within the lookahead window that haven't been handled yet.
 * 
 * In Milestone 1, this is a read-only view. Pre-actions (snooze, mute, dismiss)
 * will be added in Milestone 2.
 */
class UpcomingEventsFragment : Fragment(), EventListCallback {

    private val settings: Settings by lazy { Settings(requireContext()) }
    private val clock = CNPlusSystemClock()
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var refreshLayout: SwipeRefreshLayout
    private lateinit var emptyView: TextView
    private lateinit var adapter: EventListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_event_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recyclerView = view.findViewById(R.id.recycler_view)
        refreshLayout = view.findViewById(R.id.refresh_layout)
        emptyView = view.findViewById(R.id.empty_view)
        
        emptyView.text = getString(R.string.empty_upcoming)
        
        // Use EventListAdapter in read-only mode for now
        // Pre-actions will be added in Milestone 2
        adapter = EventListAdapter(requireContext(), this)
        recyclerView.layoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter
        adapter.recyclerView = recyclerView
        
        refreshLayout.setOnRefreshListener {
            loadEvents()
        }
    }

    override fun onResume() {
        super.onResume()
        loadEvents()
    }

    private fun loadEvents() {
        val ctx = context ?: return
        background {
            val provider = UpcomingEventsProvider(
                context = ctx,
                settings = settings,
                clock = clock,
                monitorStorage = MonitorStorage(ctx),
                calendarProvider = CalendarProvider
            )
            
            val events = provider.getUpcomingEvents().toTypedArray()
            
            activity?.runOnUiThread {
                adapter.setEventsToDisplay(events)
                updateEmptyState()
                refreshLayout.isRefreshing = false
            }
        }
    }

    private fun updateEmptyState() {
        emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    // EventListCallback implementation
    // In Milestone 1, clicking opens the event details (read-only)
    // Dismiss/Snooze will be implemented in Milestone 2 as pre-actions
    
    override fun onItemClick(v: View, position: Int, eventId: Long) {
        DevLog.info(LOG_TAG, "onItemClick, pos=$position, eventId=$eventId")
        
        val event = adapter.getEventAtPosition(position, eventId)
        if (event != null) {
            // Open event in view mode (no snooze for now - that's Milestone 2)
            startActivity(
                Intent(requireContext(), ViewEventActivity::class.java)
                    .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                    .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                    .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                    .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    override fun onItemDismiss(v: View, position: Int, eventId: Long) {
        // Pre-dismiss will be implemented in Milestone 2
        DevLog.info(LOG_TAG, "onItemDismiss (not implemented in M1), pos=$position, eventId=$eventId")
    }

    override fun onItemSnooze(v: View, position: Int, eventId: Long) {
        // Pre-snooze will be implemented in Milestone 2
        DevLog.info(LOG_TAG, "onItemSnooze (not implemented in M1), pos=$position, eventId=$eventId")
    }

    override fun onItemRemoved(event: EventAlertRecord) {
        // Not used for upcoming events in Milestone 1
    }

    override fun onItemRestored(event: EventAlertRecord) {
        // Not used for upcoming events in Milestone 1
    }

    override fun onScrollPositionChange(newPos: Int) {
        // Not needed
    }

    companion object {
        private const val LOG_TAG = "UpcomingEventsFragment"
    }
}
