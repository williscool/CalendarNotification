package com.github.quarck.calnotify.testutils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.app.CalendarReloadManager
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.ui.DismissedEventsActivity
import com.github.quarck.calnotify.ui.MainActivity
import com.github.quarck.calnotify.ui.SettingsActivity
import com.github.quarck.calnotify.ui.SnoozeAllActivity
import com.github.quarck.calnotify.ui.ViewEventActivityNoRecents
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.robolectric.Shadows.shadowOf

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
    
    /**
     * Sets up the fixture. Call in @Before.
     * Injects mock storage into ApplicationController and UI activities.
     */
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up UITestFixtureRobolectric")
        
        // Grant calendar permissions for Robolectric
        grantCalendarPermissions()
        
        // Clear any existing data
        eventsStorage.clear()
        dismissedEventsStorage.clear()
        
        // Inject mock storage into ApplicationController
        // This ensures any code that uses ApplicationController.getEventsStorage() will get our mock
        ApplicationController.eventsStorageProvider = { eventsStorage }
        
        // Inject mock storage into activities
        MainActivity.dismissedEventsStorageProvider = { dismissedEventsStorage }
        MainActivity.eventsStorageProvider = { eventsStorage }
        DismissedEventsActivity.dismissedEventsStorageProvider = { dismissedEventsStorage }
        ViewEventActivityNoRecents.eventsStorageProvider = { eventsStorage }
        
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
            android.Manifest.permission.WRITE_CALENDAR
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
        
        // Reset ApplicationController and activities to use real storage
        ApplicationController.eventsStorageProvider = null
        MainActivity.dismissedEventsStorageProvider = null
        MainActivity.eventsStorageProvider = null
        DismissedEventsActivity.dismissedEventsStorageProvider = null
        ViewEventActivityNoRecents.eventsStorageProvider = null
        
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
        val snoozedUntil = System.currentTimeMillis() + (snoozeMinutes * Consts.MINUTE_IN_MILLISECONDS)
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
     * Launches MainActivity with ActivityScenario.
     */
    fun launchMainActivity(): ActivityScenario<MainActivity> {
        DevLog.info(LOG_TAG, "Launching MainActivity")
        return ActivityScenario.launch(MainActivity::class.java)
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
     * Launches SettingsActivity.
     */
    fun launchSettingsActivity(): ActivityScenario<SettingsActivity> {
        DevLog.info(LOG_TAG, "Launching SettingsActivity")
        val intent = Intent(context, SettingsActivity::class.java)
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
     * Mocks ApplicationController for isolated UI testing.
     */
    fun mockApplicationController() {
        DevLog.info(LOG_TAG, "Mocking ApplicationController")
        mockkObject(ApplicationController)
    }
    
    companion object {
        private const val LOG_TAG = "UITestFixtureRobolectric"
        
        fun create(): UITestFixtureRobolectric = UITestFixtureRobolectric()
    }
}
