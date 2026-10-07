package com.github.quarck.calnotify.calendarmonitor

import android.content.Context
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.MockApplicationComponents
import com.github.quarck.calnotify.testutils.MockCalendarProvider
import com.github.quarck.calnotify.testutils.MockContextProvider
import com.github.quarck.calnotify.testutils.MockTimeProvider
import com.github.quarck.calnotify.testutils.TestStorageFactory
import io.mockk.MockKAnnotations
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Robolectric version of SimpleCalendarMonitoringTest
 * 
 * Tests the REAL ApplicationController.registerNewEvent method with injected mock storage.
 * This provides actual code coverage of the event registration logic without native SQLite.
 * 
 * **What This Tests (Real Code):**
 * - ApplicationController.registerNewEvent orchestration logic
 * - Calendar handling check (getCalendarIsHandled)
 * - Non-repeating event replacement behavior
 * - Repeating event addition behavior
 * - Event storage verification
 * 
 * **Implementation:** Uses optional `db` parameter added to registerNewEvent
 * (following the pattern from safeDismissEvents and restoreEvent).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class SimpleCalendarMonitoringRobolectricTest {
    private val LOG_TAG = "SimpleCalMonRobolectricTest"
    
    private lateinit var mockTimeProvider: MockTimeProvider
    private lateinit var mockContextProvider: MockContextProvider
    private lateinit var mockCalendarProvider: MockCalendarProvider
    private lateinit var mockComponents: MockApplicationComponents
    
    private val context: Context
        get() = mockContextProvider.fakeContext!!
    
    private var testCalendarId: Long = -1
    
    @Before
    fun setup() {
        ShadowLog.stream = System.out
        DevLog.info(LOG_TAG, "Setting up test environment")
        
        MockKAnnotations.init(this)
        unmockkAll()
        
        // Reset storage factory to ensure clean state
        TestStorageFactory.reset()
        
        mockTimeProvider = MockTimeProvider()
        mockTimeProvider.setup()
        
        mockContextProvider = MockContextProvider(mockTimeProvider)
        mockContextProvider.setup()
        
        mockCalendarProvider = MockCalendarProvider(mockContextProvider, mockTimeProvider)
        mockCalendarProvider.setup()
        
        mockComponents = MockApplicationComponents(
            mockContextProvider,
            mockTimeProvider,
            mockCalendarProvider
        )
        mockComponents.setup()
        
        // Create a test calendar
        testCalendarId = mockCalendarProvider.createTestCalendar(
            context,
            "Test Calendar",
            "test@example.com",
            "test@example.com"
        )
        assertTrue("Test calendar should be created", testCalendarId > 0)
        
        // Initialize both storages to set up ApplicationController providers
        TestStorageFactory.getMonitorStorage()
        TestStorageFactory.getEventsStorage()
    }
    
    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test environment")
        TestStorageFactory.reset()
        mockCalendarProvider.cleanup()
        mockContextProvider.cleanup()
        mockTimeProvider.cleanup()
        unmockkAll()
    }
    
    /**
     * Tests the REAL registerNewEvent method with injected mock storage.
     * 
     * This tests actual ApplicationController code, not simulated flow.
     */
    @Test
    fun testRegisterNewEventWithInjectedStorage() {
        DevLog.info(LOG_TAG, "Running testRegisterNewEventWithInjectedStorage")
        
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val startTime = currentTime + 60000 // 1 minute from now
        val alertTime = startTime - 30000    // 30 seconds before
        
        // Create test event in calendar
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Test Event",
            description = "Test Description",
            startTime = startTime,
            duration = 60000,
            reminderMinutes = 0
        )
        assertTrue("Event should be created", eventId > 0)
        
        // Get mock events storage
        val eventsStorage = TestStorageFactory.getEventsStorage()
        assertEquals("Should start with no events in storage", 0, eventsStorage.eventCount)
        
        // Create event record for registration
        val eventRecord = EventAlertRecord(
            calendarId = testCalendarId,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = alertTime,
            notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
            title = "Test Event",
            desc = "Test Description",
            startTime = startTime,
            endTime = startTime + 60000,
            instanceStartTime = startTime,
            instanceEndTime = startTime + 60000,
            location = "",
            lastStatusChangeTime = currentTime,
            displayStatus = EventDisplayStatus.Hidden,
            color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = currentTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
        
        // Call REAL registerNewEvent with injected storage
        val result = ApplicationController.registerNewEvent(context, eventRecord, eventsStorage)
        
        // Verify the result
        assertTrue("registerNewEvent should return true", result)
        
        // Verify event was added to storage by the real code
        assertEquals("Should have 1 event in storage", 1, eventsStorage.eventCount)
        val storedEvent = eventsStorage.getEvent(eventId, startTime)
        assertNotNull("Event should be in storage", storedEvent)
        assertEquals("Event title should match", "Test Event", storedEvent?.title)
        assertEquals("Event should be in correct calendar", testCalendarId, storedEvent?.calendarId)
        
        DevLog.info(LOG_TAG, "testRegisterNewEventWithInjectedStorage completed successfully")
    }
    
    /**
     * Tests registerNewEvent with a repeating event
     */
    @Test
    fun testRegisterRepeatingEvent() {
        DevLog.info(LOG_TAG, "Running testRegisterRepeatingEvent")
        
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val startTime = currentTime + 120000 // 2 minutes from now
        val alertTime = startTime - 60000     // 1 minute before
        
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Repeating Test Event",
            description = "Test Description",
            startTime = startTime,
            duration = 60000,
            repeatingRule = "FREQ=DAILY;COUNT=5"
        )
        assertTrue("Event should be created", eventId > 0)
        
        val eventsStorage = TestStorageFactory.getEventsStorage()
        
        // Create repeating event record
        val eventRecord = EventAlertRecord(
            calendarId = testCalendarId,
            eventId = eventId,
            isAllDay = false,
            isRepeating = true,  // Repeating!
            alertTime = alertTime,
            notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
            title = "Repeating Test Event",
            desc = "Test Description",
            startTime = startTime,
            endTime = startTime + 60000,
            instanceStartTime = startTime,
            instanceEndTime = startTime + 60000,
            location = "",
            lastStatusChangeTime = currentTime,
            displayStatus = EventDisplayStatus.Hidden,
            color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = currentTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
        
        // Call REAL registerNewEvent
        val result = ApplicationController.registerNewEvent(context, eventRecord, eventsStorage)
        
        assertTrue("registerNewEvent should return true for repeating event", result)
        assertEquals("Should have 1 event in storage", 1, eventsStorage.eventCount)
        
        val storedEvent = eventsStorage.getEvent(eventId, startTime)
        assertNotNull("Repeating event should be in storage", storedEvent)
        assertTrue("Event should be marked as repeating", storedEvent!!.isRepeating)
        
        DevLog.info(LOG_TAG, "testRegisterRepeatingEvent completed successfully")
    }
    
    /**
     * Tests that non-repeating event replaces old instance with same eventId
     */
    @Test
    fun testRegisterNonRepeatingEventReplacesOld() {
        DevLog.info(LOG_TAG, "Running testRegisterNonRepeatingEventReplacesOld")
        
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val startTime = currentTime + 60000
        val alertTime = startTime - 30000
        val eventId = 12345L
        
        val eventsStorage = TestStorageFactory.getEventsStorage()
        
        // Pre-populate with an "old" event with same eventId
        val oldEvent = EventAlertRecord(
            calendarId = testCalendarId,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = alertTime - 1000,  // Different alert time
            notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
            title = "Old Event",
            desc = "Old Description",
            startTime = startTime - 1000,
            endTime = startTime + 59000,
            instanceStartTime = startTime - 1000,
            instanceEndTime = startTime + 59000,
            location = "",
            lastStatusChangeTime = currentTime - 1000,
            displayStatus = EventDisplayStatus.Hidden,
            color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = currentTime - 1000,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
        eventsStorage.addEvent(oldEvent)
        assertEquals("Should start with 1 old event", 1, eventsStorage.eventCount)
        
        // Register new event with same eventId
        val newEvent = EventAlertRecord(
            calendarId = testCalendarId,
            eventId = eventId,  // Same eventId!
            isAllDay = false,
            isRepeating = false,
            alertTime = alertTime,
            notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
            title = "New Event",
            desc = "New Description",
            startTime = startTime,
            endTime = startTime + 60000,
            instanceStartTime = startTime,
            instanceEndTime = startTime + 60000,
            location = "",
            lastStatusChangeTime = currentTime,
            displayStatus = EventDisplayStatus.Hidden,
            color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = currentTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
        
        // Call REAL registerNewEvent
        val result = ApplicationController.registerNewEvent(context, newEvent, eventsStorage)
        
        assertTrue("registerNewEvent should return true", result)
        assertEquals("Should have 1 event (old replaced)", 1, eventsStorage.eventCount)
        
        val storedEvent = eventsStorage.getEvent(eventId, startTime)
        assertNotNull("New event should be in storage", storedEvent)
        assertEquals("Should have new title", "New Event", storedEvent?.title)
        
        DevLog.info(LOG_TAG, "testRegisterNonRepeatingEventReplacesOld completed successfully")
    }
}

