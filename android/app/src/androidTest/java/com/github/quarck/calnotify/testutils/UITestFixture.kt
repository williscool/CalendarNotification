package com.github.quarck.calnotify.testutils

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
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
    
    /**
     * Sets up the fixture. Call in @Before.
     */
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up UITestFixture")
        clearAllEvents()
    }
    
    /**
     * Cleans up after tests. Call in @After.
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up UITestFixture")
        clearAllEvents()
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
     * Call this when you want to verify UI calls methods without side effects.
     */
    fun mockApplicationController() {
        DevLog.info(LOG_TAG, "Mocking ApplicationController")
        mockkObject(ApplicationController)
    }
    
    companion object {
        private const val LOG_TAG = "UITestFixture"
        
        /**
         * Creates a fixture instance. Typically used with JUnit rules.
         */
        fun create(): UITestFixture = UITestFixture()
    }
}

