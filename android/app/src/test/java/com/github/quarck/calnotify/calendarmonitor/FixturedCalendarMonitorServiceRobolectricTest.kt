package com.github.quarck.calnotify.calendarmonitor

import android.content.Context
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
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
 * Robolectric version of FixturedCalendarMonitorServiceTest
 * 
 * Tests REAL ApplicationController event registration with injected mock storage.
 * Covers: manual rescan flow, batch processing, edge cases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class FixturedCalendarMonitorServiceRobolectricTest {
    private val LOG_TAG = "FixturedMonRoboTest"
    
    private lateinit var mockTimeProvider: MockTimeProvider
    private lateinit var mockContextProvider: MockContextProvider
    private lateinit var mockCalendarProvider: MockCalendarProvider
    private lateinit var mockComponents: MockApplicationComponents
    
    private var testCalendarId: Long = 0L
    private val context: Context get() = mockContextProvider.fakeContext!!
    
    @Before
    fun setup() {
        ShadowLog.stream = System.out
        DevLog.info(LOG_TAG, "Setting up test environment")
        
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
            displayName = "Fixtured Monitor Test Calendar",
            accountName = "test@example.com",
            ownerAccount = "com.google"
        )
        mockContextProvider.setCalendarHandlingStatusDirectly(testCalendarId, true)
        
        // Reset storage
        TestStorageFactory.reset()
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
     * Tests calendar monitoring through manual rescan.
     * Uses real ApplicationController.registerNewEvent with injected storage.
     */
    @Test
    fun testCalendarMonitoringManualRescan() {
        DevLog.info(LOG_TAG, "Running testCalendarMonitoringManualRescan")
        
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val startTime = currentTime + 60000 // 1 minute from now
        val alertTime = startTime - 30000    // 30 seconds before
        
        // Create test event
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Test Monitor Event",
            description = "Test Description",
            startTime = startTime,
            duration = 60000,
            reminderMinutes = 0
        )
        
        val eventsStorage = TestStorageFactory.getEventsStorage()
        val monitorStorage = TestStorageFactory.getMonitorStorage()
        
        // Step 1: Verify no alerts exist
        assertEquals("Should start with no alerts", 0, monitorStorage.alertCount)
        
        // Step 2: Simulate scan - add alert to monitor storage
        val alertEntry = MonitorEventAlertEntry(
            eventId = eventId,
            isAllDay = false,
            alertTime = alertTime,
            instanceStartTime = startTime,
            instanceEndTime = startTime + 60000,
            alertCreatedByUs = false,
            wasHandled = false
        )
        monitorStorage.addAlert(alertEntry)
        
        // Step 3: Verify alerts added but not handled
        assertEquals("Should have 1 alert", 1, monitorStorage.alertCount)
        assertFalse("Alert should not be handled", monitorStorage.alerts.first().wasHandled)
        
        // Step 4: Process - mark handled and register event
        monitorStorage.updateAlert(alertEntry.copy(wasHandled = true))
        
        val eventRecord = EventAlertRecord(
            calendarId = testCalendarId,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = alertTime,
            notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
            title = "Test Monitor Event",
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
        
        // Step 5: Verify
        assertTrue("registerNewEvent should succeed", result)
        assertTrue("Alert should be handled", monitorStorage.alerts.first().wasHandled)
        assertNotNull("Event should be in storage", eventsStorage.getEvent(eventId, startTime))
        
        DevLog.info(LOG_TAG, "testCalendarMonitoringManualRescan completed")
    }
    
    /**
     * Tests batch processing of multiple events (like calendar reload).
     */
    @Test
    fun testFixturedCalendarReload() {
        DevLog.info(LOG_TAG, "Running testFixturedCalendarReload")
        
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val eventCount = 3
        val eventsStorage = TestStorageFactory.getEventsStorage()
        val monitorStorage = TestStorageFactory.getMonitorStorage()
        
        val eventIds = mutableListOf<Long>()
        val eventRecords = mutableListOf<EventAlertRecord>()
        
        // Create multiple test events
        for (i in 0 until eventCount) {
            val hourOffset = i + 1
            val eventStartTime = currentTime + (hourOffset * 3600000L)
            val alertTime = eventStartTime - (15 * 60 * 1000)
            
            val eventId = mockCalendarProvider.createTestEvent(
                context,
                testCalendarId,
                title = "Test Event $i",
                description = "Test Description $i",
                startTime = eventStartTime,
                duration = 3600000,
                reminderMinutes = 15
            )
            eventIds.add(eventId)
            
            // Add alert
            monitorStorage.addAlert(MonitorEventAlertEntry(
                eventId = eventId,
                isAllDay = false,
                alertTime = alertTime,
                instanceStartTime = eventStartTime,
                instanceEndTime = eventStartTime + 3600000,
                alertCreatedByUs = false,
                wasHandled = false
            ))
            
            // Create event record
            eventRecords.add(EventAlertRecord(
                calendarId = testCalendarId,
                eventId = eventId,
                isAllDay = false,
                isRepeating = false,
                alertTime = alertTime,
                notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM + i,
                title = "Test Event $i",
                desc = "Test Description $i",
                startTime = eventStartTime,
                endTime = eventStartTime + 3600000,
                instanceStartTime = eventStartTime,
                instanceEndTime = eventStartTime + 3600000,
                location = "",
                lastStatusChangeTime = currentTime,
                displayStatus = EventDisplayStatus.Hidden,
                color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
                origin = EventOrigin.ProviderBroadcast,
                timeFirstSeen = currentTime,
                eventStatus = EventStatus.Confirmed,
                attendanceStatus = AttendanceStatus.None,
                flags = 0
            ))
        }
        
        // Verify alerts added
        assertEquals("Should have $eventCount alerts", eventCount, monitorStorage.alertCount)
        
        // Process each event using REAL registerNewEvent
        for ((index, record) in eventRecords.withIndex()) {
            val result = ApplicationController.registerNewEvent(context, record, eventsStorage)
            assertTrue("Event $index should register successfully", result)
            
            // Mark alert as handled
            val alert = monitorStorage.getAlert(record.eventId, record.alertTime, record.instanceStartTime)
            if (alert != null) {
                monitorStorage.updateAlert(alert.copy(wasHandled = true))
            }
        }
        
        // Verify all events processed
        assertEquals("Should have $eventCount events in storage", eventCount, eventsStorage.eventCount)
        for (i in 0 until eventCount) {
            val hourOffset = i + 1
            val eventStartTime = currentTime + (hourOffset * 3600000L)
            val storedEvent = eventsStorage.getEvent(eventIds[i], eventStartTime)
            assertNotNull("Event $i should be in storage", storedEvent)
            assertEquals("Event $i title should match", "Test Event $i", storedEvent?.title)
        }
        
        DevLog.info(LOG_TAG, "testFixturedCalendarReload completed")
    }
    
    /**
     * Tests settings integration.
     */
    @Test
    fun testCalendarMonitoringSettings() {
        DevLog.info(LOG_TAG, "Running testCalendarMonitoringSettings")
        
        val settings = Settings(context)
        
        // Verify calendar handling
        assertTrue("Calendar should be handled", settings.getCalendarIsHandled(testCalendarId))
        
        // Test unhandled calendar
        val unhandledCalendarId = mockCalendarProvider.createTestCalendar(
            context,
            displayName = "Unhandled Calendar",
            accountName = "test2@example.com",
            ownerAccount = "com.google"
        )
        mockContextProvider.setCalendarHandlingStatusDirectly(unhandledCalendarId, false)
        
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val eventsStorage = TestStorageFactory.getEventsStorage()
        
        // Try to register event from unhandled calendar
        val eventRecord = EventAlertRecord(
            calendarId = unhandledCalendarId,
            eventId = 999L,
            isAllDay = false,
            isRepeating = false,
            alertTime = currentTime,
            notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
            title = "Unhandled Calendar Event",
            desc = "",
            startTime = currentTime + 60000,
            endTime = currentTime + 120000,
            instanceStartTime = currentTime + 60000,
            instanceEndTime = currentTime + 120000,
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
        
        // Call REAL registerNewEvent - should return false for unhandled calendar
        val result = ApplicationController.registerNewEvent(context, eventRecord, eventsStorage)
        
        assertFalse("Event from unhandled calendar should not register", result)
        assertEquals("No events should be in storage", 0, eventsStorage.eventCount)
        
        DevLog.info(LOG_TAG, "testCalendarMonitoringSettings completed")
    }
    
    /**
     * Tests delayed processing behavior.
     */
    @Test
    fun testDelayedProcessing() {
        DevLog.info(LOG_TAG, "Running testDelayedProcessing")
        
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val startTime = currentTime + 60000
        val alertTime = startTime - 30000
        
        val eventId = mockCalendarProvider.createTestEvent(
            context, testCalendarId,
            title = "Delayed Test Event",
            description = "Test",
            startTime = startTime,
            duration = 60000
        )
        
        val eventsStorage = TestStorageFactory.getEventsStorage()
        val monitorStorage = TestStorageFactory.getMonitorStorage()
        
        // Add unhandled alert
        monitorStorage.addAlert(MonitorEventAlertEntry(
            eventId = eventId,
            isAllDay = false,
            alertTime = alertTime,
            instanceStartTime = startTime,
            instanceEndTime = startTime + 60000,
            alertCreatedByUs = false,
            wasHandled = false
        ))
        
        // Verify alert exists but event not processed
        assertEquals("Alert should exist", 1, monitorStorage.alertCount)
        assertEquals("No events yet", 0, eventsStorage.eventCount)
        
        // Advance time past alert
        mockTimeProvider.testClock.setCurrentTime(alertTime + 1000)
        
        // Now process
        val eventRecord = EventAlertRecord(
            calendarId = testCalendarId,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = alertTime,
            notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
            title = "Delayed Test Event",
            desc = "Test",
            startTime = startTime,
            endTime = startTime + 60000,
            instanceStartTime = startTime,
            instanceEndTime = startTime + 60000,
            location = "",
            lastStatusChangeTime = mockTimeProvider.testClock.currentTimeMillis(),
            displayStatus = EventDisplayStatus.Hidden,
            color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = currentTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
        
        val result = ApplicationController.registerNewEvent(context, eventRecord, eventsStorage)
        
        assertTrue("Should register after delay", result)
        assertEquals("Event should be in storage", 1, eventsStorage.eventCount)
        
        DevLog.info(LOG_TAG, "testDelayedProcessing completed")
    }
}

