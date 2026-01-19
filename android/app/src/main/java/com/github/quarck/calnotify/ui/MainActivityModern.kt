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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.github.quarck.calnotify.BuildConfig
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.globalState
import com.github.quarck.calnotify.utils.DateTimeUtils
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.findOrThrow
import com.github.quarck.calnotify.utils.truncateForChip
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton

// FilterState, StatusOption, TimeFilter are defined in FilterState.kt

/**
 * Modern MainActivity implementation with fragment-based navigation.
 * Uses BottomNavigationView with separate tabs for Active, Upcoming, and Dismissed events.
 */
class MainActivityModern : MainActivityBase() {

    private var navController: NavController? = null

    private lateinit var floatingAddEvent: FloatingActionButton
    
    // Filter state - in-memory only, clears on tab switch and app restart
    private var filterState = FilterState()
    private var chipGroup: ChipGroup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Restore filter state if available (survives rotation)
        savedInstanceState?.let { restoreFilterState(it) }
        
        setupUI()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveFilterState(outState)
    }
    
    private fun saveFilterState(outState: Bundle) {
        // Save calendar filter (null = all, empty = none, set = specific)
        filterState.selectedCalendarIds?.let { 
            outState.putLongArray(STATE_CALENDAR_IDS, it.toLongArray())
        } ?: outState.putBoolean(STATE_CALENDAR_NULL, true)
        
        // Save status filters as ordinal array
        outState.putIntArray(STATE_STATUS_FILTERS, filterState.statusFilters.map { it.ordinal }.toIntArray())
        
        // Save time filter
        outState.putInt(STATE_TIME_FILTER, filterState.timeFilter.ordinal)
    }
    
    private fun restoreFilterState(savedState: Bundle) {
        // Restore calendar filter
        val calendarIds: Set<Long>? = if (savedState.getBoolean(STATE_CALENDAR_NULL, false)) {
            null
        } else {
            savedState.getLongArray(STATE_CALENDAR_IDS)?.toSet()
        }
        
        // Restore status filters
        val statusFilters = savedState.getIntArray(STATE_STATUS_FILTERS)
            ?.toList()
            ?.mapNotNull { ordinal -> StatusOption.entries.getOrNull(ordinal) }
            ?.toSet() ?: emptySet()
        
        // Restore time filter
        val timeFilter = TimeFilter.entries.getOrNull(
            savedState.getInt(STATE_TIME_FILTER, 0)
        ) ?: TimeFilter.ALL
        
        filterState = FilterState(
            selectedCalendarIds = calendarIds,
            statusFilters = statusFilters,
            timeFilter = timeFilter
        )
    }
    
    /** Get current filter state for fragments to use */
    fun getCurrentFilterState(): FilterState = filterState

    private fun setupUI() {
        setContentView(R.layout.activity_main)
        val toolbar = find<Toolbar?>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Set status bar color explicitly to match toolbar (fixes edge-to-edge displays)
        window.statusBarColor = getColor(R.color.primary_dark)

        // Set status bar spacer height (pinned, doesn't scroll with toolbar)
        val statusBarSpacer = findViewById<View>(R.id.status_bar_spacer)
        statusBarSpacer?.let { spacer ->
            ViewCompat.setOnApplyWindowInsetsListener(spacer) { view, insets ->
                val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                view.layoutParams.height = statusBarInset
                view.requestLayout()
                insets
            }
            ViewCompat.requestApplyInsets(spacer)
        }

        // Set up navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val bottomNav = find<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav?.setupWithNavController(navController!!)

        // Apply nav bar inset to bottom navigation only
        // (AppBarLayout handles status bar via fitsSystemWindows in XML)
        bottomNav?.let { nav ->
            ViewCompat.setOnApplyWindowInsetsListener(nav) { view, insets ->
                val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                view.setPadding(0, 0, 0, navBarInset)
                insets
            }
            ViewCompat.requestApplyInsets(nav)
        }

        // Setup filter chips
        chipGroup = find<ChipGroup>(R.id.filter_chips)
        
        // Setup Fragment Result listeners for bottom sheets (survives config changes)
        setupFilterResultListeners()

        // Update toolbar title based on current destination
        navController?.addOnDestinationChangedListener { _, destination, _ ->
            supportActionBar?.title = when (destination.id) {
                R.id.activeEventsFragment -> getString(R.string.title_active)
                R.id.upcomingEventsFragment -> getString(R.string.title_upcoming)
                R.id.dismissedEventsFragment -> getString(R.string.title_dismissed)
                else -> getString(R.string.app_name)
            }
            // Clear search when switching tabs
            searchView?.setQuery("", false)
            searchMenuItem?.collapseActionView()
            // Clear filters when switching tabs (same behavior as search)
            filterState = FilterState()
            updateFilterChipsForCurrentTab()
            // Update menu items based on current tab (e.g., hide snooze/dismiss all on non-active tabs)
            invalidateOptionsMenu()
        }

        // FAB for adding events
        floatingAddEvent = findOrThrow<FloatingActionButton>(R.id.action_btn_add_event)
        floatingAddEvent.setOnClickListener {
            startActivity(
                Intent(this, EditEventActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }

        shouldForceRepost = (clock.currentTimeMillis() - (globalState?.lastNotificationRePost ?: 0L)) > Consts.MIN_FORCE_REPOST_INTERVAL
        shouldRemindForEventsWithNoReminders = settings.shouldRemindForEventsWithNoReminders
        calendarRescanEnabled = settings.enableCalendarRescan
    }

    /**
     * Get the currently visible fragment that implements SearchableFragment.
     */
    private fun getCurrentSearchableFragment(): SearchableFragment? {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        return navHostFragment?.childFragmentManager?.primaryNavigationFragment as? SearchableFragment
    }

    // === Menu handling ===

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)

        val currentFragment = getCurrentSearchableFragment()

        // Dismissed events is now a tab, not a menu item
        menu.findItem(R.id.action_dismissed_events)?.isVisible = false
        // Custom quiet hours is deprecated
        menu.findItem(R.id.action_custom_quiet_interval)?.isVisible = false

        // Show mute all only for fragments that support it (Active events)
        val muteAllMenuItem = menu.findItem(R.id.action_mute_all)
        val supportsMuteAll = currentFragment?.supportsMuteAll() == true
        muteAllMenuItem?.isVisible = supportsMuteAll && settings.enableNotificationMute
        muteAllMenuItem?.isEnabled = currentFragment?.anyForMuteAll() == true

        // Show dismiss all only for fragments that support it (Active events)
        val dismissAllMenuItem = menu.findItem(R.id.action_dismiss_all)
        val supportsDismissAll = currentFragment?.supportsDismissAll() == true
        dismissAllMenuItem?.isVisible = supportsDismissAll
        dismissAllMenuItem?.isEnabled = currentFragment?.anyForDismissAll() == true

        // Show snooze all only for fragments that support it (Active events)
        val snoozeAllMenuItem = menu.findItem(R.id.action_snooze_all)
        val supportsSnoozeAll = currentFragment?.supportsSnoozeAll() == true
        val hasEvents = (currentFragment?.getDisplayedEventCount() ?: 0) > 0
        snoozeAllMenuItem?.isVisible = supportsSnoozeAll
        snoozeAllMenuItem?.isEnabled = hasEvents
        if (supportsSnoozeAll) {
            snoozeAllMenuItem?.title = resources.getString(
                if (currentFragment?.hasActiveEvents() == true) R.string.snooze_all else R.string.change_all
            )
        }

        // Set up search for new nav UI
        searchMenuItem = menu.findItem(R.id.action_search)
        searchMenuItem?.isVisible = true
        searchMenuItem?.isEnabled = true

        searchMenuItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                if (searchView?.hasFocus() == true) {
                    searchView?.clearFocus()
                    return false
                }
                getCurrentSearchableFragment()?.setSearchQuery(null)
                return true
            }
        })

        searchView = searchMenuItem?.actionView as? SearchView
        val manager = getSystemService(Context.SEARCH_SERVICE) as SearchManager

        val count = currentFragment?.getEventCount() ?: 0
        searchView?.queryHint = resources.getQuantityString(R.plurals.search_placeholder, count, count)
        searchView?.setSearchableInfo(manager.getSearchableInfo(componentName))

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                getCurrentSearchableFragment()?.setSearchQuery(query)
                searchView?.clearFocus()
                searchView?.setQuery(query, false)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                getCurrentSearchableFragment()?.setSearchQuery(newText)
                return true
            }
        })

        val closebutton: View? = searchView?.findViewById(androidx.appcompat.R.id.search_close_btn)
        closebutton?.setOnClickListener {
            searchView?.setQuery("", false)
            searchView?.clearFocus()
            getCurrentSearchableFragment()?.setSearchQuery(null)
            searchMenuItem?.collapseActionView()
        }

        // DEV_PAGE_ENABLED is set via local.properties (gitignored, can't leak to releases)
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
                val fragment = getCurrentSearchableFragment()
                val isChange = fragment?.hasActiveEvents() != true
                val searchQuery = fragment?.getSearchQuery()
                val eventCount = fragment?.getDisplayedEventCount() ?: 0

                startActivity(
                    Intent(this, SnoozeAllActivity::class.java)
                        .putExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, isChange)
                        .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                        .putExtra(Consts.INTENT_SEARCH_QUERY, searchQuery)
                        .putExtra(Consts.INTENT_SEARCH_QUERY_EVENT_COUNT, eventCount)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }

            R.id.action_mute_all -> onMuteAll()

            R.id.action_dismiss_all -> onDismissAll()
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
        getCurrentSearchableFragment()?.onDismissAllComplete()
        invalidateOptionsMenu()
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
        getCurrentSearchableFragment()?.onMuteAllComplete()
        invalidateOptionsMenu()
    }
    
    // === Filter Chips ===
    
    private fun updateFilterChipsForCurrentTab() {
        chipGroup?.removeAllViews()
        
        val currentDestination = navController?.currentDestination?.id ?: return
        
        when (currentDestination) {
            R.id.activeEventsFragment -> {
                // Active tab: Calendar, Status, Time
                addCalendarChip()
                addStatusChip()
                addTimeChip(TimeFilterBottomSheet.TabType.ACTIVE)
            }
            R.id.upcomingEventsFragment -> {
                // Upcoming tab: Calendar, Status (Time filter deferred - needs lookahead integration)
                addCalendarChip()
                addStatusChip()
            }
            R.id.dismissedEventsFragment -> {
                // Dismissed tab: Calendar, Time
                addCalendarChip()
                addTimeChip(TimeFilterBottomSheet.TabType.DISMISSED)
            }
        }
    }
    
    private fun addStatusChip() {
        // Chip requires MaterialComponents theme - wrap context
        val materialContext = ContextThemeWrapper(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight)
        val chip = Chip(materialContext).apply {
            text = getStatusChipText()
            isCheckable = false
            isChipIconVisible = false
            isCloseIconVisible = true
            closeIcon = getDrawable(R.drawable.ic_arrow_drop_down)
            setOnClickListener { showStatusFilterPopup(it) }
            setOnCloseIconClickListener { showStatusFilterPopup(it) }
        }
        chipGroup?.addView(chip)
    }
    
    private fun getStatusChipText(): String {
        val filters = filterState.statusFilters
        return when {
            filters.isEmpty() -> getString(R.string.filter_status)
            filters.size == 1 -> filters.first().toDisplayString()
            filters.size == 2 -> {
                // Two filters - show both, but truncate if combined too long
                val combined = filters.joinToString(", ") { it.toDisplayString() }
                if (combined.length <= MAX_COMBINED_STATUS_CHIP_LENGTH) {
                    combined
                } else {
                    getString(R.string.filter_name_plus_count, filters.first().toDisplayString(), 1)
                }
            }
            else -> {
                // Show first filter + count of remaining
                val first = filters.first().toDisplayString()
                getString(R.string.filter_name_plus_count, first, filters.size - 1)
            }
        }
    }
    
    private fun StatusOption.toDisplayString(): String = when (this) {
        StatusOption.SNOOZED -> getString(R.string.filter_status_snoozed)
        StatusOption.ACTIVE -> getString(R.string.filter_status_active)
        StatusOption.MUTED -> getString(R.string.filter_status_muted)
        StatusOption.RECURRING -> getString(R.string.filter_status_recurring)
    }
    
    private fun showStatusFilterPopup(anchor: View) {
        val isUpcoming = navController?.currentDestination?.id == R.id.upcomingEventsFragment
        
        PopupMenu(this, anchor).apply {
            // Add "All" option with special ID
            menu.add(Menu.NONE, -1, 0, R.string.filter_status_all).isCheckable = true
            
            // Add status options - exclude SNOOZED and ACTIVE for Upcoming tab (not applicable)
            StatusOption.entries.forEachIndexed { index, option ->
                if (isUpcoming && option in listOf(StatusOption.SNOOZED, StatusOption.ACTIVE)) return@forEachIndexed
                menu.add(Menu.NONE, option.ordinal, index + 1, option.toDisplayString()).isCheckable = true
            }
            
            // Set current checked states
            val currentFilters = filterState.statusFilters
            menu.findItem(-1)?.isChecked = currentFilters.isEmpty()
            StatusOption.entries.forEach { option ->
                menu.findItem(option.ordinal)?.isChecked = option in currentFilters
            }
            
            setOnMenuItemClickListener { item ->
                if (item.itemId == -1) {
                    // "All" selected - clear all filters
                    filterState = filterState.copy(statusFilters = emptySet())
                } else {
                    val option = StatusOption.entries[item.itemId]
                    val newFilters = if (option in filterState.statusFilters) {
                        filterState.statusFilters - option
                    } else {
                        filterState.statusFilters + option
                    }
                    filterState = filterState.copy(statusFilters = newFilters)
                }
                updateFilterChipsForCurrentTab()
                notifyCurrentFragmentFilterChanged()
                true
            }
            show()
        }
    }
    
    private fun notifyCurrentFragmentFilterChanged() {
        getCurrentSearchableFragment()?.onFilterChanged()
    }
    
    // === Time Filter Chip ===
    
    private fun addTimeChip(tabType: TimeFilterBottomSheet.TabType) {
        val materialContext = ContextThemeWrapper(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight)
        val chip = Chip(materialContext).apply {
            text = getTimeChipText()
            isCheckable = false
            isChipIconVisible = false
            isCloseIconVisible = true
            closeIcon = getDrawable(R.drawable.ic_arrow_drop_down)
            setOnClickListener { showTimeFilterBottomSheet(tabType) }
            setOnCloseIconClickListener { showTimeFilterBottomSheet(tabType) }
        }
        chipGroup?.addView(chip)
    }
    
    private fun getTimeChipText(): String {
        return when (filterState.timeFilter) {
            TimeFilter.ALL -> getString(R.string.filter_time)
            TimeFilter.STARTED_TODAY -> getString(R.string.filter_time_started_today)
            TimeFilter.STARTED_THIS_WEEK -> getString(R.string.filter_time_started_this_week)
            TimeFilter.PAST -> getString(R.string.filter_time_past)
            TimeFilter.STARTED_THIS_MONTH -> getString(R.string.filter_time_started_this_month)
        }
    }
    
    private fun showTimeFilterBottomSheet(tabType: TimeFilterBottomSheet.TabType) {
        val bottomSheet = TimeFilterBottomSheet.newInstance(filterState.timeFilter, tabType)
        bottomSheet.show(supportFragmentManager, "TimeFilterBottomSheet")
    }
    
    // === Calendar Filter Chip ===
    
    private fun addCalendarChip() {
        val materialContext = ContextThemeWrapper(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight)
        val chip = Chip(materialContext).apply {
            text = getCalendarChipText()
            isCheckable = false
            isChipIconVisible = false
            isCloseIconVisible = true
            closeIcon = getDrawable(R.drawable.ic_arrow_drop_down)
            setOnClickListener { showCalendarFilterBottomSheet() }
            setOnCloseIconClickListener { showCalendarFilterBottomSheet() }
        }
        chipGroup?.addView(chip)
    }
    
    private fun getCalendarChipText(): String {
        val selectedIds = filterState.selectedCalendarIds
        // null = no filter (all calendars), empty = none selected
        if (selectedIds == null) return getString(R.string.filter_calendar)
        
        val selectedCount = selectedIds.size
        return when {
            selectedCount == 0 -> getString(R.string.filter_calendar_none)
            selectedCount == 1 -> {
                // Single calendar - show name (already truncated by getCalendarName)
                val calendarId = selectedIds.first()
                getCalendarName(calendarId) ?: getString(R.string.filter_calendar)
            }
            selectedCount == 2 -> {
                // Two calendars - show both names if combined fits, else first + count
                val names = selectedIds.mapNotNull { getCalendarName(it) }
                if (names.size == 2) {
                    val combined = names.joinToString(", ")
                    if (combined.length <= MAX_COMBINED_CALENDAR_CHIP_LENGTH) {
                        combined
                    } else {
                        getString(R.string.filter_name_plus_count, names.first(), 1)
                    }
                } else {
                    getString(R.string.filter_calendar_count, selectedCount)
                }
            }
            else -> {
                // 3+ calendars - show first name + count
                val firstName = getCalendarName(selectedIds.first())
                if (firstName != null) {
                    getString(R.string.filter_name_plus_count, firstName, selectedCount - 1)
                } else {
                    getString(R.string.filter_calendar_count, selectedCount)
                }
            }
        }
    }
    
    private fun getCalendarName(calendarId: Long): String? {
        val calendar = CalendarProvider.getCalendarById(this, calendarId)
        val name = calendar?.displayName?.takeIf { it.isNotEmpty() } ?: calendar?.name
        return name?.truncateForChip()
    }
    
    private fun showCalendarFilterBottomSheet() {
        val bottomSheet = CalendarFilterBottomSheet.newInstance(filterState.selectedCalendarIds ?: emptySet())
        bottomSheet.show(supportFragmentManager, "CalendarFilterBottomSheet")
    }
    
    /** Setup Fragment Result listeners for bottom sheets (survives config changes) */
    private fun setupFilterResultListeners() {
        // Time filter result
        supportFragmentManager.setFragmentResultListener(
            TimeFilterBottomSheet.REQUEST_KEY, this
        ) { _, bundle ->
            val filterOrdinal = bundle.getInt(TimeFilterBottomSheet.RESULT_FILTER, 0)
            val selectedFilter = TimeFilter.entries.getOrNull(filterOrdinal) ?: TimeFilter.ALL
            filterState = filterState.copy(timeFilter = selectedFilter)
            updateFilterChipsForCurrentTab()
            notifyCurrentFragmentFilterChanged()
        }
        
        // Calendar filter result
        supportFragmentManager.setFragmentResultListener(
            CalendarFilterBottomSheet.REQUEST_KEY, this
        ) { _, bundle ->
            val calendarArray = bundle.getLongArray(CalendarFilterBottomSheet.RESULT_CALENDARS)
            val selectedCalendars: Set<Long>? = calendarArray?.toSet()
            filterState = filterState.copy(selectedCalendarIds = selectedCalendars)
            updateFilterChipsForCurrentTab()
            notifyCurrentFragmentFilterChanged()
        }
    }

    companion object {
        private const val LOG_TAG = "MainActivityModern"
        
        /** Max length for combined status filter names before showing "+N" */
        private const val MAX_COMBINED_STATUS_CHIP_LENGTH = 20
        
        /** Max length for combined calendar names before showing "+N" */
        private const val MAX_COMBINED_CALENDAR_CHIP_LENGTH = 25
        
        // SavedInstanceState keys for filter persistence across rotation
        private const val STATE_CALENDAR_IDS = "filter_calendar_ids"
        private const val STATE_CALENDAR_NULL = "filter_calendar_null"
        private const val STATE_STATUS_FILTERS = "filter_status"
        private const val STATE_TIME_FILTER = "filter_time"
    }
}
