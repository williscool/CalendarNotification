package com.github.quarck.calnotify.testutils

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.CalendarEventDetails
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventRecord
import com.github.quarck.calnotify.app.CalendarReloadManager
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.ui.ActiveEventsFragment
import com.github.quarck.calnotify.ui.DismissedEventsActivity
import com.github.quarck.calnotify.ui.DismissedEventsFragment
import com.github.quarck.calnotify.ui.MainActivity
import com.github.quarck.calnotify.ui.MainActivityLegacy
import com.github.quarck.calnotify.ui.MainActivityModern
import com.github.quarck.calnotify.ui.SettingsActivityX
import com.github.quarck.calnotify.ui.SnoozeAllActivity
import com.github.quarck.calnotify.ui.UpcomingEventsFragment
import com.github.quarck.calnotify.ui.ViewEventActivityNoRecents
import com.github.quarck.calnotify.utils.AsyncTaskCallback
import com.github.quarck.calnotify.utils.CNPlusUnitTestClock
import com.github.quarck.calnotify.utils.globalAsyncTaskCallback
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test fixture for Robolectric UI tests.
 * 
 * Uses MockEventsStorage and MockDismissedEventsStorage injected into ApplicationController
 * to avoid SQLite native library issues in Robolectric.
 * 
 * This follows the same injection pattern used by other passing Robolectric tests
 * (see ReminderAlarmRobolectricTest, SnoozeRobolectricTest, etc.)
 */
class UITestFixtureRobolectric {
    
    val context: Context
        get() = ApplicationProvider.getApplicationContext()
    
    private var eventIdCounter = 100000L
    
    // In-memory storage for tests
    val eventsStorage = MockEventsStorage()
    val dismissedEventsStorage = MockDismissedEventsStorage()
    val monitorStorage = MockMonitorStorage()
    
    // Test clock for deterministic time
    val testClock = CNPlusUnitTestClock(TestTimeConstants.STANDARD_TEST_TIME)
    
    /**
     * Sets up the fixture. Call in @Before.
     * Injects mock storage into ApplicationController and UI activities.
     */
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up UITestFixtureRobolectric")
        
        // Grant calendar permissions for Robolectric
        grantCalendarPermissions()
        
        // Use legacy UI for MainActivity tests (new navigation UI is fragment-based)
        Settings(context).useNewNavigationUI = false
        
        // Configure upcoming events lookahead to use fixed mode with 24 hours
        // This ensures seeded events are always within range regardless of test run time
        Settings(context).upcomingEventsMode = "fixed"
        Settings(context).upcomingEventsFixedHours = 24
        
        // Clear any existing data
        eventsStorage.clear()
        dismissedEventsStorage.clear()
        monitorStorage.clear()
        
        // Inject mock storage into ApplicationController
        // This ensures any code that uses ApplicationController.getEventsStorage() will get our mock
        ApplicationController.eventsStorageProvider = { eventsStorage }
        
        // Inject mock storage into activities
        MainActivity.dismissedEventsStorageProvider = { dismissedEventsStorage }
        MainActivity.eventsStorageProvider = { eventsStorage }
        DismissedEventsActivity.dismissedEventsStorageProvider = { dismissedEventsStorage }
        ViewEventActivityNoRecents.eventsStorageProvider = { eventsStorage }
        
        // Inject mock storage into fragments
        ActiveEventsFragment.eventsStorageProvider = { eventsStorage }
        DismissedEventsFragment.dismissedEventsStorageProvider = { dismissedEventsStorage }
        UpcomingEventsFragment.monitorStorageProvider = { monitorStorage }
        
        // Inject test clock into fragments for deterministic time behavior
        ActiveEventsFragment.clockProvider = { testClock }
        UpcomingEventsFragment.clockProvider = { testClock }
        DismissedEventsFragment.clockProvider = { testClock }
        
        // Mock PermissionsManager to return true for calendar permissions
        mockkObject(PermissionsManager)
        every { PermissionsManager.hasAllCalendarPermissions(any()) } returns true
        
        // Mock CalendarProvider to avoid native calendar access
        mockkObject(CalendarProvider)
        every { CalendarProvider.getCalendarById(any(), any()) } returns CalendarRecord(
            calendarId = 1L,
            owner = "test@test.com",
            accountName = "test@test.com",
            accountType = "com.google",
            name = "Test Calendar",
            color = 0xFF6200EE.toInt(),
            isVisible = true,
          displayName = "deez",
          timeZone = "montreal",
          isReadOnly = false,
            isPrimary = true,
          isSynced = true
        )
        every { CalendarProvider.getNextEventReminderTime(any(), any()) } returns 0L
        
        // Mock CalendarProvider.getEvent for UpcomingEventsProvider enrichment
        every { CalendarProvider.getEvent(any(), any()) } answers {
            val eventId = arg<Long>(1)
            val now = TestTimeConstants.STANDARD_TEST_TIME
            EventRecord(
                calendarId = 1L,
                eventId = eventId,
                details = CalendarEventDetails(
                    title = "Test Event $eventId",
                    desc = "Description for event $eventId",
                    location = "Test Location",
                    timezone = "UTC",
                    startTime = now + Consts.HOUR_IN_MILLISECONDS,
                    endTime = now + 2 * Consts.HOUR_IN_MILLISECONDS,
                    isAllDay = false,
                    reminders = listOf(),
                    color = 0xFF6200EE.toInt()
                )
            )
        }
        
        // Mock CalendarReloadManager to prevent reloading from real calendar
        mockkObject(CalendarReloadManager)
        every { CalendarReloadManager.reloadSingleEvent(any(), any(), any(), any(), any()) } returns false
        
        DevLog.info(LOG_TAG, "Injected mock storage, granted permissions, and mocked calendar components")
    }
    
    /**
     * Grants calendar permissions in Robolectric shadow application.
     */
    private fun grantCalendarPermissions() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = shadowOf(app)
        shadowApp.grantPermissions(
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR,
            // Required for ContextCompat.registerReceiver with RECEIVER_NOT_EXPORTED
            "com.github.quarck.calnotify.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        )
    }
    
    /**
     * Cleans up after tests. Call in @After.
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up UITestFixtureRobolectric")
        
        // Clear storage
        eventsStorage.clear()
        dismissedEventsStorage.clear()
        monitorStorage.clear()
        
        // Reset ApplicationController and activities to use real storage
        ApplicationController.eventsStorageProvider = null
        MainActivity.dismissedEventsStorageProvider = null
        MainActivity.eventsStorageProvider = null
        DismissedEventsActivity.dismissedEventsStorageProvider = null
        ViewEventActivityNoRecents.eventsStorageProvider = null
        
        // Reset fragment providers
        ActiveEventsFragment.resetProviders()
        DismissedEventsFragment.resetProviders()
        UpcomingEventsFragment.resetProviders()
        
        unmockkAll()
    }
    
    /**
     * Creates a test event and adds it to mock storage.
     */
    fun createEvent(
        title: String = "Test Event",
        description: String = "",
        location: String = "",
        startTimeOffset: Long = Consts.HOUR_IN_MILLISECONDS,
        durationMillis: Long = Consts.HOUR_IN_MILLISECONDS,
        color: Int = 0xFF6200EE.toInt(),
        isAllDay: Boolean = false,
        isMuted: Boolean = false,
        isTask: Boolean = false,
        snoozedUntil: Long = 0L
    ): EventAlertRecord {
        val currentTime = TestTimeConstants.STANDARD_TEST_TIME
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
        event.isMuted = isMuted
        event.isTask = isTask
        
        // Add to mock storage
        eventsStorage.addEvent(event)
        
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
        val snoozedUntil = TestTimeConstants.STANDARD_TEST_TIME + (snoozeMinutes * Consts.MINUTE_IN_MILLISECONDS)
        return createEvent(title = title, snoozedUntil = snoozedUntil)
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
     * Creates a dismissed event (removes from active, adds to dismissed storage).
     */
    fun createDismissedEvent(
        title: String = "Dismissed Event",
        dismissType: EventDismissType = EventDismissType.ManuallyDismissedFromNotification
    ): EventAlertRecord {
        val event = createEvent(title = title)
        
        // Remove from active storage
        eventsStorage.deleteEvent(event.eventId, event.instanceStartTime)
        
        // Add to dismissed storage
        dismissedEventsStorage.addEvent(dismissType, event)
        
        DevLog.info(LOG_TAG, "Created dismissed event: ${event.eventId}")
        return event
    }
    
    /**
     * Creates an upcoming event (adds alert to MonitorStorage).
     * The alert will be in the future and unhandled, so it shows in UpcomingEventsFragment.
     * 
     * Note: The actual event title/details come from CalendarProvider which is mocked
     * to return a generic EventRecord. For tests, the eventId can be used to distinguish events.
     */
    fun createUpcomingEvent(
        title: String = "Upcoming Event",
        alertTimeOffsetMinutes: Int = 30,
        startTimeOffsetMinutes: Int = 60,
        durationMinutes: Int = 60,
        isAllDay: Boolean = false
    ): MonitorEventAlertEntry {
        val currentTime = TestTimeConstants.STANDARD_TEST_TIME
        val alertTime = currentTime + (alertTimeOffsetMinutes * Consts.MINUTE_IN_MILLISECONDS)
        val instanceStart = currentTime + (startTimeOffsetMinutes * Consts.MINUTE_IN_MILLISECONDS)
        val instanceEnd = instanceStart + (durationMinutes * Consts.MINUTE_IN_MILLISECONDS)
        val eventId = eventIdCounter++
        
        val alert = MonitorEventAlertEntry(
            eventId = eventId,
            isAllDay = isAllDay,
            alertTime = alertTime,
            instanceStartTime = instanceStart,
            instanceEndTime = instanceEnd,
            alertCreatedByUs = false,
            wasHandled = false
        )
        
        monitorStorage.addAlert(alert)
        
        DevLog.info(LOG_TAG, "Created upcoming event: eventId=$eventId, title=$title, alertTime=$alertTime")
        return alert
    }
    
    /**
     * Seeds multiple upcoming events.
     */
    fun seedUpcomingEvents(count: Int, titlePrefix: String = "Upcoming"): List<MonitorEventAlertEntry> {
        val alerts = mutableListOf<MonitorEventAlertEntry>()
        for (i in 1..count) {
            val alert = createUpcomingEvent(
                title = "$titlePrefix $i",
                alertTimeOffsetMinutes = i * 30,  // Stagger alerts
                startTimeOffsetMinutes = i * 60
            )
            alerts.add(alert)
        }
        DevLog.info(LOG_TAG, "Seeded $count upcoming events")
        return alerts
    }
    
    /**
     * Clears all events from mock storage.
     */
    fun clearAllEvents() {
        eventsStorage.clear()
        dismissedEventsStorage.clear()
        DevLog.info(LOG_TAG, "Cleared all events")
    }
    
    /**
     * Gets all active events from mock storage.
     */
    fun getActiveEvents(): List<EventAlertRecord> = eventsStorage.events
    
    /**
     * Gets event count from mock storage.
     */
    fun getEventCount(): Int = eventsStorage.eventCount
    
    /**
     * Launches MainActivityLegacy with ActivityScenario.
     * 
     * This directly launches the legacy activity (bypassing the MainActivity router)
     * since tests are configured with useNewNavigationUI = false.
     */
    fun launchMainActivity(): ActivityScenario<MainActivityLegacy> {
        DevLog.info(LOG_TAG, "Launching MainActivityLegacy")
        return ActivityScenario.launch(MainActivityLegacy::class.java)
    }
    
    /**
     * Launches MainActivityModern with ActivityScenario.
     * 
     * This directly launches the modern activity with fragment-based navigation.
     * Use this for tests that need the new navigation UI with bottom tabs.
     */
    fun launchMainActivityModern(): ActivityScenario<MainActivityModern> {
        DevLog.info(LOG_TAG, "Launching MainActivityModern")
        return ActivityScenario.launch(MainActivityModern::class.java)
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
        return ActivityScenario.launch(intent)
    }
    
    /**
     * Launches SnoozeAllActivity in "snooze all" mode.
     */
    fun launchSnoozeAllActivity(): ActivityScenario<SnoozeAllActivity> {
        DevLog.info(LOG_TAG, "Launching SnoozeAllActivity in snooze-all mode")
        val intent = Intent(context, SnoozeAllActivity::class.java).apply {
            putExtra(Consts.INTENT_SNOOZE_ALL_KEY, true)
        }
        return ActivityScenario.launch(intent)
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
        return ActivityScenario.launch(intent)
    }
    
    /**
     * Launches DismissedEventsActivity.
     */
    fun launchDismissedEventsActivity(): ActivityScenario<DismissedEventsActivity> {
        DevLog.info(LOG_TAG, "Launching DismissedEventsActivity")
        val intent = Intent(context, DismissedEventsActivity::class.java)
        return ActivityScenario.launch(intent)
    }
    
    /**
     * Launches SettingsActivityX.
     */
    fun launchSettingsActivity(): ActivityScenario<SettingsActivityX> {
        DevLog.info(LOG_TAG, "Launching SettingsActivityX")
        val intent = Intent(context, SettingsActivityX::class.java)
        return ActivityScenario.launch(intent)
    }
    
    /**
     * Launches SnoozeAllActivity with a custom intent.
     */
    fun launchSnoozeAllActivityWithIntent(intent: Intent): ActivityScenario<SnoozeAllActivity> {
        DevLog.info(LOG_TAG, "Launching SnoozeAllActivity with intent")
        return ActivityScenario.launch(intent)
    }
    
    /**
     * Launches ActiveEventsFragment in a test container.
     */
    fun launchActiveEventsFragment(): FragmentScenario<ActiveEventsFragment> {
        DevLog.info(LOG_TAG, "Launching ActiveEventsFragment")
        return FragmentScenario.launchInContainer(
            ActiveEventsFragment::class.java,
            themeResId = R.style.AppTheme
        )
    }
    
    /**
     * Launches DismissedEventsFragment in a test container.
     */
    fun launchDismissedEventsFragment(): FragmentScenario<DismissedEventsFragment> {
        DevLog.info(LOG_TAG, "Launching DismissedEventsFragment")
        return FragmentScenario.launchInContainer(
            DismissedEventsFragment::class.java,
            themeResId = R.style.AppTheme
        )
    }
    
    /**
     * Launches UpcomingEventsFragment in a test container.
     */
    fun launchUpcomingEventsFragment(): FragmentScenario<UpcomingEventsFragment> {
        DevLog.info(LOG_TAG, "Launching UpcomingEventsFragment")
        return FragmentScenario.launchInContainer(
            UpcomingEventsFragment::class.java,
            themeResId = R.style.AppTheme
        )
    }
    
    /**
     * Mocks ApplicationController for isolated UI testing.
     */
    fun mockApplicationController() {
        DevLog.info(LOG_TAG, "Mocking ApplicationController")
        mockkObject(ApplicationController)
    }
    
    /**
     * Waits for all async tasks to complete.
     * 
     * Uses the globalAsyncTaskCallback mechanism to track pending tasks
     * with a CountDownLatch for efficient blocking (no busy-wait).
     * Then idles the main looper to process onPostExecute callbacks.
     */
    fun waitForAsyncTasks() {
        // Wait for any pending async tasks to complete (with timeout)
        if (pendingTaskCount.get() > 0) {
            completionLatch?.await(5, TimeUnit.SECONDS)
        }
        // Idle the main looper to process onPostExecute callbacks
        shadowOf(Looper.getMainLooper()).idle()
    }
    
    companion object {
        private const val LOG_TAG = "UITestFixtureRobolectric"
        
        /** Tracks number of async tasks currently in flight */
        private val pendingTaskCount = AtomicInteger(0)
        
        /** Latch that signals when all tasks complete */
        @Volatile
        private var completionLatch: CountDownLatch? = null
        
        /** Callback installed to track async task lifecycle */
        private val taskTrackingCallback = object : AsyncTaskCallback {
            override fun onTaskStarted() {
                val count = pendingTaskCount.incrementAndGet()
                if (count == 1) {
                    // First task started - create a fresh latch
                    completionLatch = CountDownLatch(1)
                }
            }
            override fun onTaskCompleted() {
                val count = pendingTaskCount.decrementAndGet()
                if (count == 0) {
                    // All tasks done - signal the latch
                    completionLatch?.countDown()
                }
            }
        }
        
        init {
            // Install the callback globally to track all background { } calls
            globalAsyncTaskCallback = taskTrackingCallback
        }
        
        fun create(): UITestFixtureRobolectric = UITestFixtureRobolectric()
    }
}
