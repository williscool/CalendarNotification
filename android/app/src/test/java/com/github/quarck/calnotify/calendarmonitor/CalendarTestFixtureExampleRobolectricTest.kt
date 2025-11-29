package com.github.quarck.calnotify.calendarmonitor

import android.content.Context
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.MockApplicationComponents
import com.github.quarck.calnotify.testutils.MockCalendarProvider
import com.github.quarck.calnotify.testutils.MockContextProvider
import com.github.quarck.calnotify.testutils.MockTimeProvider
import com.github.quarck.calnotify.testutils.TestStorageFactory
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
 * Robolectric version of CalendarTestFixtureExampleTest
 * 
 * Demonstrates fixture usage and tests event creation/processing flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class CalendarTestFixtureExampleRobolectricTest {
    private val LOG_TAG = "FixtureExampleRoboTest"
    
    private lateinit var mockTimeProvider: MockTimeProvider
    private lateinit var mockContextProvider: MockContextProvider
    private lateinit var mockCalendarProvider: MockCalendarProvider
    private lateinit var mockComponents: MockApplicationComponents
    
    private var testCalendarId: Long = 0L
    private val context: Context get() = mockContextProvider.fakeContext!!
    
    @Before
    fun setup() {
        ShadowLog.stream = System.out
        DevLog.info(LOG_TAG, "Setting up example test")
        
        mockTimeProvider = MockTimeProvider()
        mockTimeProvider.setup()
        
        mockContextProvider = MockContextProvider(mockTimeProvider)
        mockContextProvider.setup()
        
        mockCalendarProvider = MockCalendarProvider(mockContextProvider, mockTimeProvider)
        mockCalendarProvider.setup()
        
        mockComponents = MockApplicationComponents(mockContextProvider, mockTimeProvider, mockCalendarProvider)
        mockComponents.setup()
        
        // Create test calendar
        testCalendarId = mockCalendarProvider.createTestCalendar(
            context,
            displayName = "Example Test Calendar",
            accountName = "example@test.com",
            ownerAccount = "com.google"
        )
        mockContextProvider.setCalendarHandlingStatusDirectly(testCalendarId, true)
        
        TestStorageFactory.reset()
    }
    
    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up example test")
        TestStorageFactory.reset()
        mockCalendarProvider.cleanup()
        mockContextProvider.cleanup()
        mockTimeProvider.cleanup()
        unmockkAll()
    }
    
    /**
     * Tests base fixture event creation - verifies event is created but not yet processed.
     */
    @Test
    fun testBaseFixtureEventCreation() {
        DevLog.info(LOG_TAG, "Running testBaseFixtureEventCreation")
        
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        
        // Create a test event
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            "Test Base Fixture Event",
            "Test Description",
            currentTime + 60000 // 1 minute from now
        )
        
        // Verify the event was created in calendar
        assertTrue("Event should have valid ID", eventId > 0)
        
        // Verify event not yet in events storage
        val eventsStorage = TestStorageFactory.getEventsStorage()
        assertEquals("Event should not be processed yet", 0, eventsStorage.eventCount)
        
        DevLog.info(LOG_TAG, "testBaseFixtureEventCreation completed")
    }
    
    /**
     * Tests direct reminder flow - event is created and immediately processed.
     */
    @Test
    fun testDirectReminderFixture() {
        DevLog.info(LOG_TAG, "Running testDirectReminderFixture")
        
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val startTime = currentTime + 60000
        val alertTime = startTime - 30000
        
        // Create event
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            "Direct Reminder Test Event",
            "Test Description",
            startTime,
            duration = 60000,
            reminderMinutes = 0
        )
        
        val eventsStorage = TestStorageFactory.getEventsStorage()
        
        // Create event record (simulating what broadcast receiver would do)
        val eventRecord = EventAlertRecord(
            calendarId = testCalendarId,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = alertTime,
            notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
            title = "Direct Reminder Test Event",
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
        
        // Simulate direct reminder - call REAL registerNewEvent
        val result = ApplicationController.registerNewEvent(context, eventRecord, eventsStorage)
        
        // Verify event was processed
        assertTrue("Event should be registered", result)
        assertEquals("Event should be in storage", 1, eventsStorage.eventCount)
        
        val storedEvent = eventsStorage.getEvent(eventId, startTime)
        assertNotNull("Event should be retrievable", storedEvent)
        assertEquals("Title should match", "Direct Reminder Test Event", storedEvent?.title)
        
        DevLog.info(LOG_TAG, "testDirectReminderFixture completed")
    }
}

