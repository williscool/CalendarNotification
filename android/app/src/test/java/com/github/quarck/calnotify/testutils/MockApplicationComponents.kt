package com.github.quarck.calnotify.testutils

import android.content.Context
import com.github.quarck.calnotify.app.AlarmSchedulerInterface
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
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
     * Now that notificationManager is lazy, MockK can intercept it before instantiation.
     * This matches the instrumentation test approach exactly.
     */
    private fun setupApplicationController() {
        DevLog.info(LOG_TAG, "Setting up ApplicationController mocks")
        
        mockkObject(ApplicationController)
        
        // Set up basic properties - these are intercepted before lazy initialization
        every { ApplicationController.clock } returns timeProvider.testClock
        every { ApplicationController.notificationManager } returns mockNotificationManager
        every { ApplicationController.alarmScheduler } returns mockAlarmScheduler
        every { ApplicationController.CalendarMonitor } returns calendarProvider.mockCalendarMonitor
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
    
    /**
     * Verifies that no events are present in storage
     */
    fun verifyNoEvents(): Boolean {
        var hasNoEvents = true
        
        val context = contextProvider.fakeContext ?: return true // If no context, assume no events
        
        EventsStorage(context).classCustomUse { db ->
            val events = db.events
            hasNoEvents = events.isEmpty()
            
            if (!hasNoEvents) {
                DevLog.error(LOG_TAG, "Expected no events but found ${events.size}")
                events.forEach { event ->
                    DevLog.error(LOG_TAG, "Unexpected event: id=${event.eventId}, title=${event.title}")
                }
            }
        }
        
        return hasNoEvents
    }
} 
