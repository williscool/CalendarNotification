package com.github.quarck.calnotify.dismissedeventsstorage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.quarck.calnotify.app.AlarmSchedulerInterface
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.notification.EventNotificationManagerInterface
import com.github.quarck.calnotify.testutils.MockApplicationComponents
import com.github.quarck.calnotify.testutils.MockCalendarProvider
import com.github.quarck.calnotify.testutils.MockContextProvider
import com.github.quarck.calnotify.testutils.MockTimeProvider
import expo.modules.mymodule.JsRescheduleConfirmationObject
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Ignore


@RunWith(AndroidJUnit4::class)
class OriginalEventDismissTest {
    private val LOG_TAG = "OriginalEventDismissTest"
    
    private lateinit var mockContext: Context
    private lateinit var mockDb: EventsStorageInterface
    private lateinit var mockComponents: MockApplicationComponents
    private lateinit var mockTimeProvider: MockTimeProvider
    
    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up EventDismissTest")

        // Setup mock time provider
        mockTimeProvider = MockTimeProvider(1635724800000) // 2021-11-01 00:00:00 UTC
        mockTimeProvider.setup()
        
        // Setup mock database
        mockDb = mockk<EventsStorageInterface>(relaxed = true)
        
        // Setup mock providers
        val mockContextProvider = MockContextProvider(mockTimeProvider)
        mockContextProvider.setup()
        val mockCalendarProvider = MockCalendarProvider(mockContextProvider, mockTimeProvider)
        mockCalendarProvider.setup()
        
        // Setup mock components
        mockComponents = MockApplicationComponents(
            contextProvider = mockContextProvider,
            timeProvider = mockTimeProvider,
            calendarProvider = mockCalendarProvider
        )
        mockComponents.setup()

        mockContext = mockContextProvider.fakeContext   
    }
    
    @Test
    fun testOriginalDismissEventWithValidEvent() {
        // Given
        val event = createTestEvent()
        
        // First, mock the EventsStorage constructor to return a predefined instance
        val mockEventsStorageInstance = mockk<EventsStorage>(relaxed = true)
        
        mockkConstructor(EventsStorage::class)
        every { anyConstructed<EventsStorage>().getEvent(event.eventId, event.instanceStartTime) } returns event
        every { anyConstructed<EventsStorage>().deleteEvent(event.eventId, event.instanceStartTime) } returns true
        
        // Also mock DismissedEventsStorage
        mockkConstructor(DismissedEventsStorage::class)
        every { anyConstructed<DismissedEventsStorage>().addEvent(any(), any(), any()) } just Runs
        
        // Mock all required components used inside dismissEvent
        mockkObject(ApplicationController, recordPrivateCalls = true)
        
        // Mock notification manager methods
        every { ApplicationController.notificationManager.onEventDismissing(any(), any(), any()) } just Runs
        every { ApplicationController.notificationManager.onEventDismissed(any(), any(), any(), any()) } just Runs
        
        // Mock ReminderState
        mockkConstructor(com.github.quarck.calnotify.reminders.ReminderState::class)
        every { anyConstructed<com.github.quarck.calnotify.reminders.ReminderState>().onUserInteraction(any()) } just Runs
        
        // Mock AlarmScheduler
        val mockAlarmScheduler = mockk<AlarmSchedulerInterface>(relaxed = true)
        every { ApplicationController.alarmScheduler } returns mockAlarmScheduler
        every { mockAlarmScheduler.rescheduleAlarms(any(), any(), any()) } just Runs
        
        // Mock UINotifier
        mockkObject(com.github.quarck.calnotify.ui.UINotifier)
        every { com.github.quarck.calnotify.ui.UINotifier.notify(any(), any()) } just Runs
        
        // When - call the real dismissEvent method
        ApplicationController.dismissEvent(
            mockContext,
            EventDismissType.ManuallyDismissedFromActivity,
            event.eventId,
            event.instanceStartTime,
            0,
            false
        )
        
        // Then
        verify { anyConstructed<EventsStorage>().getEvent(event.eventId, event.instanceStartTime) }
        verify { anyConstructed<EventsStorage>().deleteEvent(event.eventId, event.instanceStartTime) }
        verify { anyConstructed<DismissedEventsStorage>().addEvent(EventDismissType.ManuallyDismissedFromActivity, any(), event) }
    }
    
    @Test
    fun testOriginalDismissEventWithNonExistentEvent() {
        // Given
        val event = createTestEvent()
        
        // First, mock the EventsStorage constructor to return a predefined instance
        mockkConstructor(EventsStorage::class)
        every { anyConstructed<EventsStorage>().getEvent(event.eventId, event.instanceStartTime) } returns null
        
        // Also mock DismissedEventsStorage (should not be called)
        mockkConstructor(DismissedEventsStorage::class)
        
        // Mock all required components used inside dismissEvent
        mockkObject(ApplicationController, recordPrivateCalls = true)
        
        // Mock notification manager methods
        every { ApplicationController.notificationManager.onEventDismissing(any(), any(), any()) } just Runs
        every { ApplicationController.notificationManager.onEventDismissed(any(), any(), any(), any()) } just Runs
        
        // Mock ReminderState
        mockkConstructor(com.github.quarck.calnotify.reminders.ReminderState::class)
        every { anyConstructed<com.github.quarck.calnotify.reminders.ReminderState>().onUserInteraction(any()) } just Runs
        
        // Mock AlarmScheduler
        val mockAlarmScheduler = mockk<AlarmSchedulerInterface>(relaxed = true)
        every { ApplicationController.alarmScheduler } returns mockAlarmScheduler
        
        // Mock UINotifier
        mockkObject(com.github.quarck.calnotify.ui.UINotifier)
        every { com.github.quarck.calnotify.ui.UINotifier.notify(any(), any()) } just Runs
        
        // When - call the real dismissEvent method
        ApplicationController.dismissEvent(
            mockContext,
            EventDismissType.ManuallyDismissedFromActivity,
            event.eventId,
            event.instanceStartTime,
            0,
            false
        )
        
        // Then
        verify { anyConstructed<EventsStorage>().getEvent(event.eventId, event.instanceStartTime) }
        verify(exactly = 0) { anyConstructed<EventsStorage>().deleteEvent(any(), any()) }
        verify(exactly = 0) { anyConstructed<DismissedEventsStorage>().addEvent(any(), any(), any()) }
    }
    
    
    private fun createTestEvent(id: Long = 1L): EventAlertRecord {
        return EventAlertRecord(
            calendarId = 1L,
            eventId = id,
            isAllDay = false,
            isRepeating = false,
            alertTime = System.currentTimeMillis(),
            notificationId = 0,
            title = "Test Event $id",
            desc = "Test Description",
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + 3600000,
            instanceStartTime = System.currentTimeMillis(),
            instanceEndTime = System.currentTimeMillis() + 3600000,
            location = "",
            lastStatusChangeTime = System.currentTimeMillis(),
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0xffff0000.toInt(),
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = System.currentTimeMillis(),
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
    }
} 
