package com.github.quarck.calnotify.testutils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.IdlingRegistry
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.ui.DismissedEventsActivity
import com.github.quarck.calnotify.ui.MainActivity
import com.github.quarck.calnotify.ui.SettingsActivity
import com.github.quarck.calnotify.ui.SnoozeAllActivity
import com.github.quarck.calnotify.ui.ViewEventActivityNoRecents
import com.github.quarck.calnotify.utils.globalAsyncTaskCallback
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
    
    // IdlingResource for AsyncTask tracking
    private val asyncTaskIdlingResource = AsyncTaskIdlingResource()
    
    // Track if calendar reload prevention is active
    private var calendarReloadPrevented = false
    
    /**
     * Sets up the fixture. Call in @Before.
     * 
     * @param waitForAsyncTasks If true, registers IdlingResource to wait for background tasks.
     *                          Set to false for UI-only tests that don't need data loading.
     * @param preventCalendarReload If true, mocks ApplicationController to prevent calendar reloads
     *                              that would clear test events from EventsStorage. Lightweight alternative
     *                              to full MockCalendarProvider setup.
     * @param grantCalendarPermissions If true, grants calendar permissions programmatically.
     *                                 Set to false to test permission dialogs (e.g., MainActivityTest).
     */
    fun setup(
        waitForAsyncTasks: Boolean = false, 
        preventCalendarReload: Boolean = false,
        grantCalendarPermissions: Boolean = false
    ) {
        DevLog.info(LOG_TAG, "Setting up UITestFixture (waitForAsyncTasks=$waitForAsyncTasks, preventCalendarReload=$preventCalendarReload, grantCalendarPermissions=$grantCalendarPermissions)")
        
        // Reset dialog flag so each test can handle dialogs if they appear
        startupDialogsDismissed = false
        
        clearAllEvents()
        
        // Grant calendar permissions programmatically if requested
        // Only use this for tests that need permissions but shouldn't test the permission dialog flow
        if (grantCalendarPermissions) {
            grantCalendarPermissions()
        }
        
        // Prevent calendar reload if needed - this stops CalendarReloadManager from clearing test events
        if (preventCalendarReload) {
            setupCalendarReloadPrevention()
        }
        
        // Only register IdlingResource if we need to wait for async operations
        if (waitForAsyncTasks) {
            IdlingRegistry.getInstance().register(asyncTaskIdlingResource)
            globalAsyncTaskCallback = asyncTaskIdlingResource
            DevLog.info(LOG_TAG, "Registered AsyncTask IdlingResource")
        } else {
            DevLog.info(LOG_TAG, "Skipping AsyncTask IdlingResource for faster UI tests")
        }
    }
    
    /**
     * Grants calendar permissions programmatically for UI tests.
     * This ensures tests work in isolation without requiring permission dialogs.
     */
    private fun grantCalendarPermissions() {
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
            
            DevLog.info(LOG_TAG, "Granted calendar permissions to $packageName")
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Failed to grant calendar permissions: ${e.message}")
            // Don't throw - some test environments might not support this
        }
    }
    
    /**
     * Sets up lightweight mocking to prevent calendar reloads from clearing test events.
     * This mocks ApplicationController.onMainActivityResumed to skip the calendar rescan.
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
        
        calendarReloadPrevented = true
        DevLog.info(LOG_TAG, "Calendar reload prevention active")
    }
    
    /**
     * Dismisses system dialogs that appear during app startup and steal window focus.
     * Handles: targetSdk warning, notification permission, battery optimization, background running.
     */
    private fun dismissTargetSdkWarningDialog() {
        // Quick exit if we've already dismissed dialogs in a previous test
        if (startupDialogsDismissed) {
            DevLog.info(LOG_TAG, "Startup dialogs already dismissed, skipping check")
            return
        }
        
        try {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            
            // Disable wait-for-idle to speed up dialog detection (default waits 10+ seconds for "idle")
            val configurator = Configurator.getInstance()
            val originalIdleTimeout = configurator.waitForIdleTimeout
            configurator.waitForIdleTimeout = 0  // Skip idle detection entirely
            
            // Buttons to check and click (in any order since dialogs can appear in different sequences)
            // Use textContains() instead of exact text match for flexibility
            val buttonsToTry = listOf(
                "OK",                  // targetSdk warning
                "Allow",               // Notification & background permissions  
                "ALLOW",               // Uppercase variant
                "DISMISS",             // Battery optimization (skip it)
                "Dismiss",             // Lowercase variant
                "LATER",               // Battery optimization alternative
                "Later",               // Lowercase variant
                "Don't allow"          // Permission denial fallback
            )
            
            var totalDismissed = 0
            val maxDialogs = 3  // Handle up to 3 chained dialogs
            
            // Known resourceIds for Android 13+ permission dialogs
            val permissionResourceIds = listOf(
                "com.android.permissioncontroller:id/permission_allow_button",
                "com.android.permissioncontroller:id/permission_deny_button"
            )
            
            // Check for dialogs up to 3 times (notification, battery, background)
            for (attempt in 1..maxDialogs) {
                var foundOne = false
                
                // Brief wait only on first attempt to let dialog appear
                if (attempt == 1) {
                    Thread.sleep(FIRST_DIALOG_TIMEOUT_MS)
                }
                
                // First, try known resourceIds for permission dialogs (Android 13+)
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
                
                // If no resourceId match, try textContains() for flexibility
                if (!foundOne) {
                    for (buttonText in buttonsToTry) {
                        val button = device.findObject(UiSelector().textContains(buttonText))
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
                DevLog.info(LOG_TAG, "Dismissed $totalDismissed system dialog(s)")
            } else {
                DevLog.info(LOG_TAG, "No system dialogs found")
            }
            
            configurator.waitForIdleTimeout = originalIdleTimeout
            startupDialogsDismissed = true
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Error dismissing dialogs: ${e.message}")
        }
    }
    
    /**
     * Cleans up after tests. Call in @After.
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up UITestFixture")
        clearAllEvents()
        
        calendarReloadPrevented = false
        
        // Unregister IdlingResource if it was registered
        try {
            IdlingRegistry.getInstance().unregister(asyncTaskIdlingResource)
            globalAsyncTaskCallback = null
        } catch (e: Exception) {
            // Ignore if not registered
        }
        
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
     */
    fun launchMainActivity(): ActivityScenario<MainActivity> {
        DevLog.info(LOG_TAG, "Launching MainActivity")
        val scenario = ActivityScenario.launch<MainActivity>(MainActivity::class.java)
        dismissTargetSdkWarningDialog()
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
        dismissTargetSdkWarningDialog()
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
        dismissTargetSdkWarningDialog()
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
        dismissTargetSdkWarningDialog()
        return scenario
    }
    
    /**
     * Launches DismissedEventsActivity.
     */
    fun launchDismissedEventsActivity(): ActivityScenario<DismissedEventsActivity> {
        DevLog.info(LOG_TAG, "Launching DismissedEventsActivity")
        val intent = Intent(context, DismissedEventsActivity::class.java)
        val scenario = ActivityScenario.launch<DismissedEventsActivity>(intent)
        
        // Dismiss warning dialog AFTER activity launch (it appears during onCreate)
        dismissTargetSdkWarningDialog()
        
        return scenario
    }
    
    /**
     * Launches SettingsActivity.
     */
    fun launchSettingsActivity(): ActivityScenario<SettingsActivity> {
        DevLog.info(LOG_TAG, "Launching SettingsActivity")
        val intent = Intent(context, SettingsActivity::class.java)
        val scenario = ActivityScenario.launch<SettingsActivity>(intent)
        
        // Dismiss warning dialog AFTER activity launch (it appears during onCreate)
        dismissTargetSdkWarningDialog()
        
        return scenario
    }
    
    /**
     * Launches SnoozeAllActivity with a custom intent.
     */
    fun launchSnoozeAllActivityWithIntent(intent: Intent): ActivityScenario<SnoozeAllActivity> {
        DevLog.info(LOG_TAG, "Launching SnoozeAllActivity with intent")
        val scenario = ActivityScenario.launch<SnoozeAllActivity>(intent)
        dismissTargetSdkWarningDialog()
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
    
    companion object {
        private const val LOG_TAG = "UITestFixture"
        
        // Dialog dismissal timing constants
        private const val FIRST_DIALOG_TIMEOUT_MS = 200L      // First dialog might take time to appear
        private const val SUBSEQUENT_DIALOG_TIMEOUT_MS = 100L // Chained dialogs appear quickly
        private const val DIALOG_DISMISS_ANIMATION_MS = 100L
        private const val DIALOG_CHAIN_WAIT_MS = 50L          // Brief wait between chained dialogs
        private const val MAX_DIALOG_DISMISSAL_ATTEMPTS = 5
        
        // Track if startup dialogs have been dismissed across ALL test instances
        @Volatile
        private var startupDialogsDismissed = false
        
        /**
         * Creates a fixture instance. Typically used with JUnit rules.
         */
        fun create(): UITestFixture = UITestFixture()
    }
}

