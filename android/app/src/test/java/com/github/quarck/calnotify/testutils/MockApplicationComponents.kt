package com.github.quarck.calnotify.testutils

import android.content.Context
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.AlarmSchedulerInterface
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissResult
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.notification.EventNotificationManager
import com.github.quarck.calnotify.notification.EventNotificationManagerInterface
import com.github.quarck.calnotify.textutils.EventFormatterInterface
import io.mockk.*

/**
 * Provides application component mock functionality for Robolectric tests
 *
 * This class creates minimal application component mocks for Robolectric tests
 */
class MockApplicationComponents(
    val contextProvider: MockContextProvider,
    val timeProvider: MockTimeProvider,
    val calendarProvider: MockCalendarProvider
) {
    private val LOG_TAG = "MockApplicationComponents"
    
    // Core components
    lateinit var mockFormatter: EventFormatterInterface
        private set
        
    lateinit var mockNotificationManager: EventNotificationManagerInterface
        private set
        
    lateinit var mockAlarmScheduler: AlarmSchedulerInterface
        private set
    
    // Track initialization state
    private var isInitialized = false
    
    /**
     * Sets up all application components
     */
    fun setup() {
        if (isInitialized) {
            DevLog.info(LOG_TAG, "MockApplicationComponents already initialized, skipping setup")
            return
        }
        
        DevLog.info(LOG_TAG, "Setting up MockApplicationComponents")
        
        try {
            // Set up components in order
            setupMockFormatter()
            setupMockNotificationManager()
            setupMockAlarmScheduler()
            setupApplicationController()
            
            isInitialized = true
            DevLog.info(LOG_TAG, "MockApplicationComponents setup complete!")
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Exception during MockApplicationComponents setup: ${e.message}")
            throw e  // Re-throw to fail the test
        }
    }
    
    /**
     * Sets up a mock text formatter
     */
    private fun setupMockFormatter() {
        DevLog.info(LOG_TAG, "Setting up mock formatter")
        
        mockFormatter = mockk<EventFormatterInterface>(relaxed = true)
    }
    
    /**
     * Sets up a mock notification manager
     * 
     * Uses mockk with explicit stubs to prevent any real implementation from being called.
     * This is more reliable in Robolectric than spyk on anonymous subclasses.
     */
    private fun setupMockNotificationManager() {
        DevLog.info(LOG_TAG, "Setting up mock notification manager")
        
        // Use mockk with explicit stubs instead of spyk to ensure no real code runs
        mockNotificationManager = mockk<EventNotificationManagerInterface>(relaxed = true) {
            // Explicitly stub methods that could cause database access
            every { 
                onEventsDismissed(
                    any(),
                    any(),
                    any<Collection<EventAlertRecord>>(),
                    any(),
                    any()
                ) 
            } answers {
                DevLog.info(LOG_TAG, "Mock onEventsDismissed called for ${thirdArg<Collection<EventAlertRecord>>().size} events")
                // Don't call postEventNotifications which would access database
            }
            
            every { 
                onEventsDismissing(any(), any<Collection<EventAlertRecord>>()) 
            } answers {
                DevLog.info(LOG_TAG, "Mock onEventsDismissing called")
            }
            
            every { 
                postEventNotifications(any(), any(), any(), any()) 
            } answers {
                DevLog.info(LOG_TAG, "Mock postEventNotifications called")
                // Don't access database - just log
            }
        }
    }
    
    /**
     * Sets up a mock alarm scheduler
     */
    private fun setupMockAlarmScheduler() {
        DevLog.info(LOG_TAG, "Setting up mock alarm scheduler")
        
        mockAlarmScheduler = mockk<AlarmSchedulerInterface>(relaxed = true)
    }
    
    /**
     * Sets up the ApplicationController mock
     * 
     * CRITICAL FOR ROBOLECTRIC: mockkObject doesn't intercept property access from within
     * real method implementations. We must mock the safeDismissEvents methods themselves
     * to prevent them from accessing the real notificationManager property.
     */
    private fun setupApplicationController() {
        DevLog.info(LOG_TAG, "Setting up ApplicationController mocks")
        
        mockkObject(ApplicationController)
        
        // Set up basic properties
        every { ApplicationController.clock } returns timeProvider.testClock
        every { ApplicationController.notificationManager } returns mockNotificationManager
        every { ApplicationController.alarmScheduler } returns mockAlarmScheduler
        every { ApplicationController.CalendarMonitor } returns calendarProvider.mockCalendarMonitor
        
        // CRITICAL: Mock safeDismissEvents methods to use our mocked notification manager
        // This prevents the real implementation from accessing the real notificationManager property
        every {
            ApplicationController.safeDismissEvents(any(), any(), any(), any(), any(), any())
        } answers {
            val context = arg<Context>(0)
            val db = arg<EventsStorageInterface>(1)
            val events = arg<Collection<EventAlertRecord>>(2)
            val dismissType = arg<EventDismissType>(3)
            val notifyActivity = arg<Boolean>(4)
            val dismissedEventsStorage = arg<DismissedEventsStorage?>(5)
            
            // Reimplementation that uses our mocked dependencies
            safeDismissEventsWithMockedDependencies(
                context, db, events, dismissType, notifyActivity, dismissedEventsStorage
            )
        }
        
        every {
            ApplicationController.safeDismissEventsById(any(), any(), any(), any(), any(), any())
        } answers {
            val context = arg<Context>(0)
            val db = arg<EventsStorageInterface>(1)
            val eventIds = arg<Collection<Long>>(2)
            val dismissType = arg<EventDismissType>(3)
            val notifyActivity = arg<Boolean>(4)
            val dismissedEventsStorage = arg<DismissedEventsStorage?>(5)
            
            // Reimplementation that uses our mocked dependencies
            safeDismissEventsByIdWithMockedDependencies(
                context, db, eventIds, dismissType, notifyActivity, dismissedEventsStorage
            )
        }
    }
    
    /**
     * Reimplementation of safeDismissEvents that uses mocked dependencies
     */
    private fun safeDismissEventsWithMockedDependencies(
        context: Context,
        db: EventsStorageInterface,
        events: Collection<EventAlertRecord>,
        dismissType: EventDismissType,
        notifyActivity: Boolean,
        dismissedEventsStorage: DismissedEventsStorage?
    ): List<Pair<EventAlertRecord, EventDismissResult>> {
        val results = mutableListOf<Pair<EventAlertRecord, EventDismissResult>>()
        
        try {
            // Validate events exist
            val validEvents = events.filter { event ->
                val exists = db.getEvent(event.eventId, event.instanceStartTime) != null
                results.add(Pair(event, if (exists) EventDismissResult.Success else EventDismissResult.EventNotFound))
                exists
            }
            
            if (validEvents.isEmpty()) {
                return results
            }
            
            // Store dismissed events if needed
            val successfullyStoredEvents = if (dismissType.shouldKeep) {
                try {
                    val storage = dismissedEventsStorage ?: DismissedEventsStorage(context)
                    storage.classCustomUse {
                        it.addEvents(dismissType, validEvents)
                    }
                    validEvents
                } catch (ex: Exception) {
                    validEvents.forEach { event ->
                        val index = results.indexOfFirst { it.first == event }
                        if (index != -1) {
                            results[index] = Pair(event, EventDismissResult.StorageError)
                        }
                    }
                    return results
                }
            } else {
                validEvents
            }
            
            // Notify about dismissing - USE MOCKED NOTIFICATION MANAGER
            try {
                mockNotificationManager.onEventsDismissing(context, successfullyStoredEvents)
            } catch (ex: Exception) {
                successfullyStoredEvents.forEach { event ->
                    val index = results.indexOfFirst { it.first == event }
                    if (index != -1) {
                        results[index] = Pair(event, EventDismissResult.NotificationError)
                    }
                }
                return results
            }
            
            // Try to delete events
            val deleteSuccess = try {
                db.deleteEvents(successfullyStoredEvents) == successfullyStoredEvents.size
            } catch (ex: Exception) {
                false
            }
            
            if (!deleteSuccess) {
                successfullyStoredEvents.forEach { event ->
                    val index = results.indexOfFirst { it.first == event }
                    if (index != -1) {
                        results[index] = Pair(event, EventDismissResult.DeletionWarning)
                    }
                }
            }
            
            // Notify about dismissal - USE MOCKED NOTIFICATION MANAGER
            try {
                val hasActiveEvents = db.events.any { it.snoozedUntil != 0L && !it.isSpecial }
                mockNotificationManager.onEventsDismissed(
                    context,
                    mockFormatter,
                    successfullyStoredEvents,
                    true,
                    hasActiveEvents
                )
            } catch (ex: Exception) {
                // Continue - notification error is not critical
            }
            
        } catch (ex: Exception) {
            events.forEach { event ->
                val index = results.indexOfFirst { it.first == event }
                if (index != -1) {
                    results[index] = Pair(event, EventDismissResult.DatabaseError)
                }
            }
        }
        
        return results
    }
    
    /**
     * Reimplementation of safeDismissEventsById that uses mocked dependencies
     */
    private fun safeDismissEventsByIdWithMockedDependencies(
        context: Context,
        db: EventsStorageInterface,
        eventIds: Collection<Long>,
        dismissType: EventDismissType,
        notifyActivity: Boolean,
        dismissedEventsStorage: DismissedEventsStorage?
    ): List<Pair<Long, EventDismissResult>> {
        val results = mutableListOf<Pair<Long, EventDismissResult>>()
        
        try {
            // Get all events for these IDs
            val events = eventIds.mapNotNull { eventId ->
                val event = db.getEventInstances(eventId).firstOrNull()
                results.add(Pair(eventId, if (event != null) EventDismissResult.Success else EventDismissResult.EventNotFound))
                event
            }
            
            if (events.isEmpty()) {
                return results
            }
            
            // Call the other version with the found events
            val dismissResults = safeDismissEventsWithMockedDependencies(
                context, db, events, dismissType, notifyActivity, dismissedEventsStorage
            )
            
            // Update our results based on the dismiss results
            dismissResults.forEach { (event, result) ->
                val index = results.indexOfFirst { it.first == event.eventId }
                if (index != -1) {
                    results[index] = Pair(event.eventId, result)
                }
            }
        } catch (ex: Exception) {
            eventIds.forEach { eventId ->
                val index = results.indexOfFirst { it.first == eventId }
                if (index != -1) {
                    results[index] = Pair(eventId, EventDismissResult.DatabaseError)
                }
            }
        }
        
        return results
    }
    
    /**
     * Gets the list of Toast messages that would have been shown
     */
    fun getToastMessages(): List<String> = contextProvider.getToastMessages()
    
    /**
     * Clears the list of Toast messages
     */
    fun clearToastMessages() {
        contextProvider.clearToastMessages()
    }
    
    /**
     * Directly adds an event to the storage for testing
     */
    fun addEventToStorage(
        event: EventAlertRecord
    ) {
        DevLog.info(LOG_TAG, "Directly adding event to storage: id=${event.eventId}, title=${event.title}")
        
        // This is just a stub for Robolectric tests - actual implementation would use EventsStorage
    }
    
    /**
     * Cleans up resources
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up MockApplicationComponents")
        isInitialized = false
    }
    
    /**
     * Shows a toast message (simulated)
     */
    fun showToast(message: String, longDuration: Boolean = false) {
        contextProvider.showToast(message, longDuration)
    }
} 
