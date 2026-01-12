package com.github.quarck.calnotify.testutils

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.IdlingRegistry
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.ui.ActiveEventsFragment
import com.github.quarck.calnotify.ui.DismissedEventsActivity
import com.github.quarck.calnotify.ui.DismissedEventsFragment
import com.github.quarck.calnotify.ui.MainActivity
import com.github.quarck.calnotify.ui.SettingsActivityX
import com.github.quarck.calnotify.ui.SnoozeAllActivity
import com.github.quarck.calnotify.ui.ViewEventActivityNoRecents
import com.github.quarck.calnotify.utils.globalAsyncTaskCallback
import androidx.test.espresso.Espresso.pressBackUnconditionally
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockkObject
import io.mockk.unmockkAll

/**
 * Test fixture for UI tests.
 * 
 * Provides utilities for seeding events, launching activities,
 * and managing test state for Espresso UI tests.
 */
class UITestFixture {
    
    val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    
    private var eventIdCounter = 100000L
    private val seededEvents = mutableListOf<EventAlertRecord>()
    
    // Track if calendar reload prevention is active
    private var calendarReloadPrevented = false
    
    // Track if dialogs have been pre-suppressed (no need to dismiss them)
    private var dialogsSuppressed = false
    
    // Track navigation depth for automatic cleanup
    private var navigationDepth = 0
    
    /**
     * Sets up the fixture. Call in @Before.
     * 
     * @param waitForAsyncTasks If true, registers IdlingResource to wait for background tasks.
     *                          Set to false for UI-only tests that don't need data loading.
     * @param preventCalendarReload If true, mocks ApplicationController to prevent calendar reloads
     *                              that would clear test events from EventsStorage. Lightweight alternative
     *                              to full MockCalendarProvider setup.
     * @param grantPermissions If true, grants runtime permissions (calendar, notifications) programmatically.
     *                         This speeds up tests by avoiding permission dialogs.
     * @param suppressBatteryDialog If true, sets SharedPreference to suppress battery optimization dialog.
     *                              Combined with grantPermissions, this eliminates all startup dialogs.
     */
    fun setup(
        waitForAsyncTasks: Boolean = false, 
        preventCalendarReload: Boolean = false,
        grantPermissions: Boolean = false,
        suppressBatteryDialog: Boolean = false
    ) {
        DevLog.info(LOG_TAG, "Setting up UITestFixture (waitForAsyncTasks=$waitForAsyncTasks, preventCalendarReload=$preventCalendarReload, grantPermissions=$grantPermissions, suppressBatteryDialog=$suppressBatteryDialog)")
        
        // CRITICAL: Clear any leftover IdlingResources from previous tests FIRST
        // This prevents accumulation when running the full test suite
        clearIdlingResources()
        
        // Reset dialog flag so each test can handle dialogs if they appear
        startupDialogsDismissed = false
        dialogsSuppressed = false
        navigationDepth = 0
        
        clearAllEvents()
        
        // Suppress battery optimization dialog if requested
        // This must be done BEFORE launching activity
        if (suppressBatteryDialog) {
            suppressBatteryOptimizationDialog()
        }
        
        // Grant runtime permissions programmatically if requested
        // This speeds up tests by avoiding permission dialogs
        if (grantPermissions) {
            grantPermissions()
        }
        
        // If both permissions granted and battery dialog suppressed, no dialogs will appear
        if (grantPermissions && suppressBatteryDialog) {
            dialogsSuppressed = true
            startupDialogsDismissed = true  // Skip dialog dismissal entirely
            DevLog.info(LOG_TAG, "All dialogs suppressed - skipping dialog dismissal")
        }
        
        // Prevent calendar reload if needed - this stops CalendarReloadManager from clearing test events
        if (preventCalendarReload) {
            setupCalendarReloadPrevention()
        }
        
        // Only register IdlingResource if we need to wait for async operations
        if (waitForAsyncTasks) {
            IdlingRegistry.getInstance().register(sharedAsyncTaskIdlingResource)
            globalAsyncTaskCallback = sharedAsyncTaskIdlingResource
            DevLog.info(LOG_TAG, "Registered AsyncTask IdlingResource")
        } else {
            DevLog.info(LOG_TAG, "Skipping AsyncTask IdlingResource for faster UI tests")
        }
    }
    
    /**
     * Suppresses the battery optimization dialog by setting SharedPreference.
     * Must be called before launching MainActivity.
     */
    private fun suppressBatteryOptimizationDialog() {
        try {
            val settings = Settings(context)
            settings.doNotShowBatteryOptimisationWarning = true
            DevLog.info(LOG_TAG, "Suppressed battery optimization dialog via SharedPreference")
        } catch (e: RuntimeException) {
            // SharedPreferences operations can fail in edge cases (storage full, etc.)
            // Log and continue - dialog dismissal code will handle the dialog if it appears
            DevLog.error(LOG_TAG, "Failed to suppress battery dialog: ${e.message}")
        }
    }
    
    /**
     * Grants runtime permissions programmatically for UI tests.
     * This ensures tests work in isolation without requiring permission dialogs.
     * Grants: READ_CALENDAR, WRITE_CALENDAR, POST_NOTIFICATIONS (API 33+)
     */
    private fun grantPermissions() {
        try {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val packageName = instrumentation.targetContext.packageName
            val uiAutomation = instrumentation.uiAutomation
            
            // Grant calendar permissions
            uiAutomation.grantRuntimePermission(
                packageName,
                Manifest.permission.READ_CALENDAR
            )
            uiAutomation.grantRuntimePermission(
                packageName,
                Manifest.permission.WRITE_CALENDAR
            )
            
            // Grant notification permission on Android 13+ (API 33)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                uiAutomation.grantRuntimePermission(
                    packageName,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
            
            DevLog.info(LOG_TAG, "Granted runtime permissions to $packageName")
        } catch (e: SecurityException) {
            DevLog.error(LOG_TAG, "Failed to grant runtime permissions: ${e.message}")
            // Don't throw - some test environments might not support this
        }
    }
    
    /**
     * Sets up lightweight mocking to prevent calendar reloads from clearing test events.
     * This mocks ApplicationController.onMainActivityResumed to skip the calendar rescan.
     * Also mocks alarm-triggered notification posting to prevent flaky tests from stale
     * AlarmManager alarms that persist across test runs on local emulators.
     */
    private fun setupCalendarReloadPrevention() {
        DevLog.info(LOG_TAG, "Setting up calendar reload prevention")
        
        mockkObject(ApplicationController)
        
        // Mock onMainActivityResumed to do nothing - this prevents the background calendar rescan
        // that would query the real CalendarProvider and clear our test events
        every { 
            ApplicationController.onMainActivityResumed(any(), any(), any()) 
        } just Runs
        
        // Also mock onCalendarChanged to prevent calendar change broadcasts from triggering rescans
        every { 
            ApplicationController.onCalendarChanged(any()) 
        } just Runs
        
        // Mock alarm-triggered methods to prevent notifications from stale AlarmManager alarms.
        // Local emulators persist alarms across test runs, causing flaky notification interference.
        // (GitHub Actions uses fresh emulators so this is less common there.)
        every { 
            ApplicationController.onEventAlarm(any()) 
        } just Runs
        
        every { 
            ApplicationController.fireEventReminder(any(), any(), any()) 
        } just Runs
        
        calendarReloadPrevented = true
        DevLog.info(LOG_TAG, "Calendar reload prevention active")
    }
    
    /**
     * Dismisses system dialogs that appear during app startup and steal window focus.
     * Handles: targetSdk warning, notification permission, battery optimization, background running.
     * 
     * Call this after launching an activity. For robust handling, call dismissDialogsIfPresent()
     * before making UI assertions if dialogs might appear asynchronously.
     */
    private fun dismissStartupDialogs() {
        // Quick exit if we've already dismissed dialogs in a previous test
        if (startupDialogsDismissed) {
            DevLog.info(LOG_TAG, "Startup dialogs already dismissed, skipping check")
            return
        }
        
        dismissDialogsIfPresent()
        startupDialogsDismissed = true
    }
    
    /**
     * Dismisses any dialogs that are currently visible.
     * Can be called multiple times - useful when dialogs appear asynchronously.
     * 
     * This handles:
     * - Battery optimization dialogs (DO IT / LATER / DISMISS)
     * - Notification permission rationale (OK / CANCEL)
     * - System permission dialogs (Allow / Don't allow)
     * - targetSdk warning dialogs (OK)
     */
    fun dismissDialogsIfPresent() {
        val configurator = Configurator.getInstance()
        val originalIdleTimeout = configurator.waitForIdleTimeout
        
        try {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            
            // Disable wait-for-idle to speed up dialog detection (default waits 10+ seconds for "idle")
            configurator.waitForIdleTimeout = 0  // Skip idle detection entirely
            
            // Buttons to check and click (in any order since dialogs can appear in different sequences)
            val buttonsToTry = listOf(
                // Notification permission rationale dialog (app-level AlertDialog)
                "OK",                  // Positive button
                "CANCEL",              // Negative button (uppercase as shown in UI)
                "Cancel",              // Negative button (mixed case from strings.xml)
                // Battery optimization dialog
                "DO IT",               // Positive - opens battery settings
                "LATER",               // Neutral - dismisses dialog
                "Later",               // Lowercase variant
                "DISMISS",             // Negative - dismisses and remembers choice
                "Dismiss",             // Lowercase variant
                // System permission dialogs
                "Allow",               // Grant permission
                "ALLOW",               // Uppercase variant
                "Don't allow",         // Deny permission
                "Deny"                 // Alternative deny text
            )
            
            var totalDismissed = 0
            val maxDialogs = 5  // Handle up to 5 chained dialogs
            
            // Known resourceIds for Android 13+ permission dialogs
            val permissionResourceIds = listOf(
                "com.android.permissioncontroller:id/permission_allow_button",
                "com.android.permissioncontroller:id/permission_deny_button"
            )
            
            // Check for dialogs multiple times to handle chained dialogs
            for (attempt in 1..maxDialogs) {
                var foundOne = false
                
                // Wait for dialog to appear - longer on first attempt
                val waitTime = if (attempt == 1) FIRST_DIALOG_TIMEOUT_MS else SUBSEQUENT_DIALOG_TIMEOUT_MS
                Thread.sleep(waitTime)
                
                // First, try known resourceIds for system permission dialogs (Android 13+)
                for (resourceId in permissionResourceIds) {
                    val button = device.findObject(UiSelector().resourceId(resourceId))
                    if (button.exists()) {
                        DevLog.info(LOG_TAG, "Found permission dialog via resourceId: $resourceId")
                        button.click()
                        Thread.sleep(DIALOG_DISMISS_ANIMATION_MS)
                        totalDismissed++
                        foundOne = true
                        break
                    }
                }
                
                // If no resourceId match, try exact text match to avoid false positives
                if (!foundOne) {
                    for (buttonText in buttonsToTry) {
                        val button = device.findObject(UiSelector().text(buttonText))  // Exact match only!
                        if (button.exists()) {
                            DevLog.info(LOG_TAG, "Found dialog with '$buttonText' button, clicking it")
                            button.click()
                            Thread.sleep(DIALOG_DISMISS_ANIMATION_MS)
                            totalDismissed++
                            foundOne = true
                            break
                        }
                    }
                }
                
                if (!foundOne) {
                    // No more dialogs, stop checking
                    break
                }
            }
            
            if (totalDismissed > 0) {
                DevLog.info(LOG_TAG, "Dismissed $totalDismissed dialog(s)")
            }
        } catch (e: RuntimeException) {
            // UiAutomator operations can throw various RuntimeExceptions
            DevLog.error(LOG_TAG, "Error dismissing dialogs: ${e.message}")
        } finally {
            // Always restore the original timeout, even if an exception occurred
            configurator.waitForIdleTimeout = originalIdleTimeout
        }
    }
    
    /**
     * Cleans up after tests. Call in @After.
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up UITestFixture")
        clearAllEvents()
        
        calendarReloadPrevented = false
        dialogsSuppressed = false
        navigationDepth = 0
        
        // Reset battery optimization dialog setting
        try {
            val settings = Settings(context)
            settings.doNotShowBatteryOptimisationWarning = false
        } catch (e: RuntimeException) {
            // SharedPreferences operations can fail - log and continue cleanup
            DevLog.error(LOG_TAG, "Failed to reset battery dialog setting: ${e.message}")
        }
        
        // Clear IdlingResources to ensure clean state for next test
        clearIdlingResources()
        
        unmockkAll()
    }
    
    /**
     * Creates a test event and adds it to storage.
     */
    fun createEvent(
        title: String = "Test Event",
        description: String = "",
        location: String = "",
        startTimeOffset: Long = Consts.HOUR_IN_MILLISECONDS, // 1 hour from now
        durationMillis: Long = Consts.HOUR_IN_MILLISECONDS,
        color: Int = 0xFF6200EE.toInt(),
        isAllDay: Boolean = false,
        isMuted: Boolean = false,
        isTask: Boolean = false,
        snoozedUntil: Long = 0L
    ): EventAlertRecord {
        val currentTime = System.currentTimeMillis()
        val startTime = currentTime + startTimeOffset
        val endTime = startTime + durationMillis
        val eventId = eventIdCounter++
        
        val event = EventAlertRecord(
            calendarId = 1L,
            eventId = eventId,
            isAllDay = isAllDay,
            isRepeating = false,
            alertTime = currentTime,
            notificationId = 0,
            title = title,
            desc = description,
            startTime = startTime,
            endTime = endTime,
            instanceStartTime = startTime,
            instanceEndTime = endTime,
            location = location,
            lastStatusChangeTime = currentTime,
            snoozedUntil = snoozedUntil,
            displayStatus = EventDisplayStatus.Hidden,
            color = color
        )
        // Set flags-based properties after construction
        event.isMuted = isMuted
        event.isTask = isTask
        
        EventsStorage(context).classCustomUse { db ->
            db.addEvent(event)
        }
        
        seededEvents.add(event)
        DevLog.info(LOG_TAG, "Created event: id=$eventId, title=$title")
        return event
    }
    
    /**
     * Seeds multiple test events.
     */
    fun seedEvents(count: Int, titlePrefix: String = "Event"): List<EventAlertRecord> {
        val events = mutableListOf<EventAlertRecord>()
        for (i in 1..count) {
            val event = createEvent(
                title = "$titlePrefix $i",
                startTimeOffset = i * Consts.HOUR_IN_MILLISECONDS
            )
            events.add(event)
        }
        DevLog.info(LOG_TAG, "Seeded $count events")
        return events
    }
    
    /**
     * Creates a snoozed event.
     */
    fun createSnoozedEvent(
        title: String = "Snoozed Event",
        snoozeMinutes: Int = 30
    ): EventAlertRecord {
        val snoozedUntil = System.currentTimeMillis() + (snoozeMinutes * Consts.MINUTE_IN_MILLISECONDS)
        return createEvent(
            title = title,
            snoozedUntil = snoozedUntil
        )
    }
    
    /**
     * Creates a muted event.
     */
    fun createMutedEvent(title: String = "Muted Event"): EventAlertRecord {
        return createEvent(title = title, isMuted = true)
    }
    
    /**
     * Creates a task event.
     */
    fun createTaskEvent(title: String = "Task Event"): EventAlertRecord {
        return createEvent(title = title, isTask = true)
    }
    
    /**
     * Adds an event to the dismissed events storage.
     */
    fun createDismissedEvent(
        title: String = "Dismissed Event",
        dismissType: EventDismissType = EventDismissType.ManuallyDismissedFromNotification
    ): EventAlertRecord {
        val event = createEvent(title = title)
        
        // Remove from active storage
        EventsStorage(context).classCustomUse { db ->
            db.deleteEvent(event.eventId, event.instanceStartTime)
        }
        
        // Add to dismissed storage
        DismissedEventsStorage(context).classCustomUse { db ->
            db.addEvent(dismissType, event)
        }
        
        DevLog.info(LOG_TAG, "Created dismissed event: ${event.eventId}")
        return event
    }
    
    /**
     * Clears all events from storage.
     */
    fun clearAllEvents() {
        EventsStorage(context).classCustomUse { db ->
            db.events.forEach { event ->
                db.deleteEvent(event.eventId, event.instanceStartTime)
            }
        }
        
        DismissedEventsStorage(context).classCustomUse { db ->
            db.events.forEach { entry ->
                db.deleteEvent(entry)
            }
        }
        
        seededEvents.clear()
        DevLog.info(LOG_TAG, "Cleared all events")
    }
    
    /**
     * Cancels all notifications posted by this app.
     * This prevents notifications from interfering with UI tests.
     */
    fun cancelAllNotifications() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        DevLog.info(LOG_TAG, "Cancelled all notifications")
    }
    
    /**
     * Tracks navigation actions (e.g., opening search, expanding a view).
     * Call this when entering UI states that require back presses to exit.
     * Use [clearNavigationStack] at test end to automatically clean up.
     * 
     * @param count Number of navigation levels entered (e.g., 2 for search with keyboard)
     */
    fun pushNavigation(count: Int = 1) {
        navigationDepth += count
        DevLog.info(LOG_TAG, "Navigation depth: $navigationDepth (+$count)")
    }
    
    /**
     * Decrements navigation depth (e.g., after a manual back press in the test).
     */
    fun popNavigation(count: Int = 1) {
        navigationDepth = maxOf(0, navigationDepth - count)
        DevLog.info(LOG_TAG, "Navigation depth: $navigationDepth (-$count)")
    }
    
    /**
     * Presses back for each tracked navigation action.
     * Call at the end of a test to restore initial state.
     */
    fun clearNavigationStack() {
        DevLog.info(LOG_TAG, "Clearing navigation stack (depth=$navigationDepth)")
        repeat(navigationDepth) {
            pressBackUnconditionally()
        }
        navigationDepth = 0
    }
    
    /**
     * Gets all active events from storage.
     */
    fun getActiveEvents(): List<EventAlertRecord> {
        var events: List<EventAlertRecord> = emptyList()
        EventsStorage(context).classCustomUse { db ->
            events = db.events.toList()
        }
        return events
    }
    
    /**
     * Gets event count from storage.
     */
    fun getEventCount(): Int {
        var count = 0
        EventsStorage(context).classCustomUse { db ->
            count = db.events.size
        }
        return count
    }
    
    /**
     * Launches MainActivity with ActivityScenario.
     * 
     * If calendar reload prevention is active, also clears any pre-existing notifications
     * to prevent them from interfering with UI tests (e.g., blocking toolbar buttons).
     */
    fun launchMainActivity(): ActivityScenario<MainActivity> {
        DevLog.info(LOG_TAG, "Launching MainActivity")
        val scenario = ActivityScenario.launch<MainActivity>(MainActivity::class.java)
        // Wait for activity to be created and ready before proceeding
        scenario.onActivity { activity ->
            DevLog.info(LOG_TAG, "MainActivity is ready: ${activity.javaClass.simpleName}")
        }
        dismissStartupDialogs()
        
        // Clear any pre-existing notifications that could interfere with UI tests
        // (e.g., heads-up notifications blocking toolbar buttons)
        if (calendarReloadPrevented) {
            cancelAllNotifications()
        }
        
        return scenario
    }
    
    /**
     * Launches SnoozeAllActivity for a specific event.
     */
    fun launchSnoozeActivityForEvent(event: EventAlertRecord): ActivityScenario<SnoozeAllActivity> {
        DevLog.info(LOG_TAG, "Launching SnoozeAllActivity for event ${event.eventId}")
        val intent = Intent(context, SnoozeAllActivity::class.java).apply {
            putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
            putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
        }
        val scenario = ActivityScenario.launch<SnoozeAllActivity>(intent)
        // Wait for activity to be created and ready before proceeding
        scenario.onActivity { activity ->
            DevLog.info(LOG_TAG, "SnoozeAllActivity is ready: ${activity.javaClass.simpleName}")
        }
        dismissStartupDialogs()
        return scenario
    }
    
    /**
     * Launches SnoozeAllActivity in "snooze all" mode.
     */
    fun launchSnoozeAllActivity(): ActivityScenario<SnoozeAllActivity> {
        DevLog.info(LOG_TAG, "Launching SnoozeAllActivity in snooze-all mode")
        val intent = Intent(context, SnoozeAllActivity::class.java).apply {
            putExtra(Consts.INTENT_SNOOZE_ALL_KEY, true)
        }
        val scenario = ActivityScenario.launch<SnoozeAllActivity>(intent)
        // Wait for activity to be created and ready before proceeding
        scenario.onActivity { activity ->
            DevLog.info(LOG_TAG, "SnoozeAllActivity is ready: ${activity.javaClass.simpleName}")
        }
        dismissStartupDialogs()
        return scenario
    }
    
    /**
     * Launches ViewEventActivity for a specific event.
     */
    fun launchViewEventActivity(event: EventAlertRecord): ActivityScenario<ViewEventActivityNoRecents> {
        DevLog.info(LOG_TAG, "Launching ViewEventActivity for event ${event.eventId}")
        val intent = Intent(context, ViewEventActivityNoRecents::class.java).apply {
            putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
            putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
        }
        val scenario = ActivityScenario.launch<ViewEventActivityNoRecents>(intent)
        // Wait for activity to be created and ready before proceeding
        scenario.onActivity { activity ->
            DevLog.info(LOG_TAG, "ViewEventActivity is ready: ${activity.javaClass.simpleName}")
        }
        dismissStartupDialogs()
        return scenario
    }
    
    /**
     * Launches DismissedEventsActivity.
     */
    fun launchDismissedEventsActivity(): ActivityScenario<DismissedEventsActivity> {
        DevLog.info(LOG_TAG, "Launching DismissedEventsActivity")
        val intent = Intent(context, DismissedEventsActivity::class.java)
        val scenario = ActivityScenario.launch<DismissedEventsActivity>(intent)
        // Wait for activity to be created and ready before proceeding
        scenario.onActivity { activity ->
            DevLog.info(LOG_TAG, "DismissedEventsActivity is ready: ${activity.javaClass.simpleName}")
        }
        // Dismiss warning dialog AFTER activity launch (it appears during onCreate)
        dismissStartupDialogs()
        
        return scenario
    }
    
    /**
     * Launches SettingsActivityX (the new AndroidX-based settings).
     */
    fun launchSettingsActivity(): ActivityScenario<SettingsActivityX> {
        DevLog.info(LOG_TAG, "Launching SettingsActivityX")
        val intent = Intent(context, SettingsActivityX::class.java)
        val scenario = ActivityScenario.launch<SettingsActivityX>(intent)
        // Wait for activity to be created and ready before proceeding
        scenario.onActivity { activity ->
            DevLog.info(LOG_TAG, "SettingsActivityX is ready: ${activity.javaClass.simpleName}")
        }
        // Dismiss warning dialog AFTER activity launch (it appears during onCreate)
        dismissStartupDialogs()
        
        return scenario
    }
    
    /**
     * Launches SnoozeAllActivity with a custom intent.
     */
    fun launchSnoozeAllActivityWithIntent(intent: Intent): ActivityScenario<SnoozeAllActivity> {
        DevLog.info(LOG_TAG, "Launching SnoozeAllActivity with intent")
        val scenario = ActivityScenario.launch<SnoozeAllActivity>(intent)
        // Wait for activity to be created and ready before proceeding
        scenario.onActivity { activity ->
            DevLog.info(LOG_TAG, "SnoozeAllActivity is ready: ${activity.javaClass.simpleName}")
        }
        dismissStartupDialogs()
        return scenario
    }
    
    /**
     * Mocks ApplicationController for isolated UI testing.
     * Call this when you want to verify UI calls methods without side effects.
     */
    fun mockApplicationController() {
        DevLog.info(LOG_TAG, "Mocking ApplicationController")
        mockkObject(ApplicationController)
    }
    
    /**
     * Launches ActiveEventsFragment in a test container.
     */
    fun launchActiveEventsFragment(): FragmentScenario<ActiveEventsFragment> {
        DevLog.info(LOG_TAG, "Launching ActiveEventsFragment")
        return FragmentScenario.launchInContainer(ActiveEventsFragment::class.java)
    }
    
    /**
     * Launches DismissedEventsFragment in a test container.
     */
    fun launchDismissedEventsFragment(): FragmentScenario<DismissedEventsFragment> {
        DevLog.info(LOG_TAG, "Launching DismissedEventsFragment")
        return FragmentScenario.launchInContainer(DismissedEventsFragment::class.java)
    }
    
    companion object {
        private const val LOG_TAG = "UITestFixture"
        
        // Dialog dismissal timing constants (kept short since permissions are usually granted upfront)
        private const val FIRST_DIALOG_TIMEOUT_MS = 200L      // First dialog wait
        private const val SUBSEQUENT_DIALOG_TIMEOUT_MS = 100L // Chained dialogs appear quickly
        private const val DIALOG_DISMISS_ANIMATION_MS = 100L
        private const val DIALOG_CHAIN_WAIT_MS = 50L          // Brief wait between chained dialogs
        private const val MAX_DIALOG_DISMISSAL_ATTEMPTS = 5
        
        // Track if startup dialogs have been dismissed across ALL test instances
        @Volatile
        private var startupDialogsDismissed = false
        
        /**
         * Shared IdlingResource instance to prevent accumulation across tests.
         * Using a singleton ensures we don't register multiple IdlingResources
         * when running the full test suite.
         */
        private val sharedAsyncTaskIdlingResource = AsyncTaskIdlingResource()
        
        /**
         * Clears any leftover IdlingResources from previous test runs.
         * Call at the START of setup to ensure clean state.
         */
        fun clearIdlingResources() {
            // Clear the global callback first
            globalAsyncTaskCallback = null
            
            // Reset the counter to clear any stale async task counts from previous tests
            // This prevents tests from hanging if a previous test terminated with tasks in-flight
            sharedAsyncTaskIdlingResource.reset()
            
            // Unregister our shared IdlingResource if it's registered
            try {
                IdlingRegistry.getInstance().unregister(sharedAsyncTaskIdlingResource)
                DevLog.info(LOG_TAG, "Cleared previously registered IdlingResource")
            } catch (e: IllegalArgumentException) {
                // Not registered, that's fine
            }
        }
        
        /**
         * Creates a fixture instance. Typically used with JUnit rules.
         */
        fun create(): UITestFixture = UITestFixture()
    }
}

