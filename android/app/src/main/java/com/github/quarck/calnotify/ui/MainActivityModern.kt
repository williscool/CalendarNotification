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

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import com.github.quarck.calnotify.globalState
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.findOrThrow
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Modern MainActivity implementation with fragment-based navigation.
 * Uses BottomNavigationView with separate tabs for Active, Upcoming, and Dismissed events.
 */
class MainActivityModern : MainActivityBase() {

    private var navController: NavController? = null

    private lateinit var floatingAddEvent: FloatingActionButton

    // Visible for testing
    internal var searchView: SearchView? = null
    internal var searchMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
    }

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
        // Mute all, dismiss all, custom quiet not yet implemented for fragments
        menu.findItem(R.id.action_mute_all)?.isVisible = false
        menu.findItem(R.id.action_dismiss_all)?.isVisible = false
        menu.findItem(R.id.action_custom_quiet_interval)?.isVisible = false

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
        }

        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val LOG_TAG = "MainActivityModern"
    }
}
