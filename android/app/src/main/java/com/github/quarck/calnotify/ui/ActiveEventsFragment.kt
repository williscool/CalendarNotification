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
class ActiveEventsFragment : Fragment(), EventListCallback, SearchableFragment {

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
        
        // Show banner if enabled in settings
        if (settings.showNewUIBanner) {
            newUIBanner?.visibility = View.VISIBLE
        }
        
        // Clicking the text opens Navigation settings
        bannerText?.setOnClickListener {
            openNavigationSettings()
            dismissBanner()
        }
        
        // Dismiss button hides the banner and turns off the setting
        dismissButton?.setOnClickListener {
            dismissBanner()
        }
    }
    
    private fun dismissBanner() {
        settings.showNewUIBanner = false
        newUIBanner?.visibility = View.GONE
    }
    
    private fun openNavigationSettings() {
        startActivity(
            Intent(requireContext(), SettingsActivityX::class.java)
                .putExtra(SettingsActivityX.EXTRA_PREF_FRAGMENT, SettingsActivityX.PREF_FRAGMENT_NAVIGATION)
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
        
        // Update banner visibility (may have changed in settings)
        newUIBanner?.visibility = if (settings.showNewUIBanner) View.VISIBLE else View.GONE
        
        loadEvents()
    }
    
    override fun onPause() {
        super.onPause()
        context?.unregisterReceiver(dataUpdatedReceiver)
    }

    private fun loadEvents() {
        val ctx = context ?: return
        background {
            val events = getEventsStorage(ctx).use { db ->
                db.eventsForDisplay.toTypedArray()
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

    // SearchableFragment implementation
    
    override fun setSearchQuery(query: String?) {
        adapter.setSearchText(query)
        updateEmptyState()
    }
    
    override fun getSearchQuery(): String? = adapter.searchString
    
    override fun getEventCount(): Int = adapter.getAllItemCount()
    
    override fun getDisplayedEventCount(): Int = adapter.itemCount
    
    override fun hasActiveEvents(): Boolean = adapter.hasActiveEvents
    
    override fun supportsSnoozeAll(): Boolean = true
    
    override fun supportsMuteAll(): Boolean = true
    
    override fun supportsDismissAll(): Boolean = true
    
    override fun anyForMuteAll(): Boolean = adapter.anyForMute
    
    override fun anyForDismissAll(): Boolean = adapter.anyForDismissAllButRecentAndSnoozed
    
    override fun onMuteAllComplete() {
        loadEvents()
    }
    
    override fun onDismissAllComplete() {
        loadEvents()
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
