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
import android.widget.ImageButton
import android.widget.LinearLayout
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
import com.github.quarck.calnotify.calendar.isSpecial
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.background
import com.google.android.material.snackbar.Snackbar

/**
 * Fragment for displaying active event notifications.
 * Migrated from MainActivity's event list functionality.
 */
class ActiveEventsFragment : Fragment(), EventListCallback {

    private lateinit var settings: Settings
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var refreshLayout: SwipeRefreshLayout
    private lateinit var emptyView: TextView
    private lateinit var adapter: EventListAdapter
    private var newUIBanner: LinearLayout? = null
    
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
        
        emptyView.text = getString(R.string.empty_active)
        
        adapter = EventListAdapter(requireContext(), this)
        recyclerView.layoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter
        adapter.recyclerView = recyclerView
        
        refreshLayout.setOnRefreshListener {
            loadEvents()
        }
        
        // Setup new UI banner
        setupNewUIBanner(view)
    }
    
    private fun setupNewUIBanner(view: View) {
        newUIBanner = view.findViewById(R.id.new_ui_banner)
        val bannerText = view.findViewById<TextView>(R.id.new_ui_banner_text)
        val dismissButton = view.findViewById<ImageButton>(R.id.new_ui_banner_dismiss)
        
        // Show banner if not dismissed yet
        if (!settings.newUIBannerDismissed) {
            newUIBanner?.visibility = View.VISIBLE
        }
        
        // Clicking the text opens settings
        bannerText?.setOnClickListener {
            openMiscSettings()
            dismissBanner()
        }
        
        // Dismiss button just hides the banner
        dismissButton?.setOnClickListener {
            dismissBanner()
        }
    }
    
    private fun dismissBanner() {
        settings.newUIBannerDismissed = true
        newUIBanner?.visibility = View.GONE
    }
    
    private fun openMiscSettings() {
        startActivity(
            Intent(requireContext(), SettingsActivityX::class.java)
                .putExtra("pref_fragment", "misc")
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
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
            val events = getEventsStorage(ctx).classCustomUse { db ->
                db.eventsForDisplay.toTypedArray()
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
        
        val ctx = context ?: return
        val event = adapter.getEventAtPosition(position, eventId)
        if (event != null && !event.isSpecial) {
            startActivity(
                Intent(ctx, ViewEventActivity::class.java)
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
        
        val ctx = context ?: return
        val event = adapter.getEventAtPosition(position, eventId)
        if (event != null) {
            DevLog.info(LOG_TAG, "Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
            ApplicationController.dismissEvent(ctx, EventDismissType.ManuallyDismissedFromActivity, event)
            
            // Capture context before creating Runnable to avoid crash if fragment detaches
            UndoManager.addUndoState(
                UndoState(
                    undo = Runnable { ApplicationController.restoreEvent(ctx, event) }
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
        
        val ctx = context ?: return
        val event = adapter.getEventAtPosition(position, eventId)
        if (event != null) {
            startActivity(
                Intent(ctx, ViewEventActivity::class.java)
                    .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                    .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                    .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                    .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    override fun onItemRemoved(event: EventAlertRecord) {
        val ctx = context ?: return
        DevLog.info(LOG_TAG, "onItemRemoved: Removing event id ${event.eventId}")
        ApplicationController.dismissEvent(ctx, EventDismissType.ManuallyDismissedFromActivity, event)
        updateEmptyState()
    }

    override fun onItemRestored(event: EventAlertRecord) {
        val ctx = context ?: return
        DevLog.info(LOG_TAG, "onItemRestored, eventId=${event.eventId}")
        ApplicationController.restoreEvent(ctx, event)
        updateEmptyState()
    }

    override fun onScrollPositionChange(newPos: Int) {
        // Not needed for fragments - handled within adapter if needed
    }

    companion object {
        private const val LOG_TAG = "ActiveEventsFragment"
        
        /** Provider for EventsStorage - enables DI for testing */
        var eventsStorageProvider: ((Context) -> EventsStorageInterface)? = null
        
        /** Gets EventsStorage - uses provider if set, otherwise creates real instance */
        fun getEventsStorage(ctx: Context): EventsStorageInterface =
            eventsStorageProvider?.invoke(ctx) ?: EventsStorage(ctx)
        
        /** Reset providers - call in @After to prevent test pollution */
        fun resetProviders() {
            eventsStorageProvider = null
        }
    }
}
