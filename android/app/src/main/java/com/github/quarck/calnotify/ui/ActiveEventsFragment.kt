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
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.app.UndoManager
import com.github.quarck.calnotify.app.UndoState
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.background
import com.google.android.material.snackbar.Snackbar

/**
 * Fragment for displaying active event notifications.
 * Migrated from MainActivity's event list functionality.
 */
class ActiveEventsFragment : Fragment(), EventListCallback {

    private val settings: Settings by lazy { Settings(requireContext()) }
    
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
        
        emptyView.text = getString(R.string.empty_active)
        
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
            val events = EventsStorage(ctx).classCustomUse { db ->
                db.events.sortedWith(
                    Comparator<EventAlertRecord> { lhs, rhs ->
                        if (lhs.snoozedUntil < rhs.snoozedUntil)
                            return@Comparator -1
                        else if (lhs.snoozedUntil > rhs.snoozedUntil)
                            return@Comparator 1

                        if (lhs.lastStatusChangeTime > rhs.lastStatusChangeTime)
                            return@Comparator -1
                        else if (lhs.lastStatusChangeTime < rhs.lastStatusChangeTime)
                            return@Comparator 1

                        return@Comparator 0
                    }
                ).toTypedArray()
            }
            
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
    
    override fun onItemClick(v: View, position: Int, eventId: Long) {
        DevLog.info(LOG_TAG, "onItemClick, pos=$position, eventId=$eventId")
        
        val event = adapter.getEventAtPosition(position, eventId)
        if (event != null) {
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
        DevLog.info(LOG_TAG, "onItemDismiss, pos=$position, eventId=$eventId")
        
        val event = adapter.getEventAtPosition(position, eventId)
        if (event != null) {
            DevLog.info(LOG_TAG, "Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
            ApplicationController.dismissEvent(requireContext(), EventDismissType.ManuallyDismissedFromActivity, event)
            
            UndoManager.addUndoState(
                UndoState(
                    undo = Runnable { ApplicationController.restoreEvent(requireContext(), event) }
                )
            )
            
            adapter.removeEvent(event)
            updateEmptyState()
            
            view?.let { v ->
                Snackbar.make(v, getString(R.string.event_dismissed), Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.undo)) { 
                        UndoManager.undo()
                        loadEvents()
                    }
                    .show()
            }
        }
    }

    override fun onItemSnooze(v: View, position: Int, eventId: Long) {
        DevLog.info(LOG_TAG, "onItemSnooze, pos=$position, eventId=$eventId")
        
        val event = adapter.getEventAtPosition(position, eventId)
        if (event != null) {
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

    override fun onItemRemoved(event: EventAlertRecord) {
        DevLog.info(LOG_TAG, "onItemRemoved: Removing event id ${event.eventId}")
        ApplicationController.dismissEvent(requireContext(), EventDismissType.ManuallyDismissedFromActivity, event)
        updateEmptyState()
    }

    override fun onItemRestored(event: EventAlertRecord) {
        DevLog.info(LOG_TAG, "onItemRestored, eventId=${event.eventId}")
        ApplicationController.restoreEvent(requireContext(), event)
        updateEmptyState()
    }

    override fun onScrollPositionChange(newPos: Int) {
        // Not needed for fragments - handled within adapter if needed
    }

    companion object {
        private const val LOG_TAG = "ActiveEventsFragment"
    }
}
