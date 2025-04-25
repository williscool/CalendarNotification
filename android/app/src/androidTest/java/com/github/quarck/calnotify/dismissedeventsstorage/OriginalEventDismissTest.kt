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
        DevLog.info(LOG_TAG, "Created test event with id=${event.eventId}, instanceStartTime=${event.instanceStartTime}")
        
        // Now, instead of mocking the constructor, we'll use our mockDb directly
        val mockDismissedEventsDb = mockk<DismissedEventsStorageInterface>(relaxed = true)
        
        // Mock the classCustomUse extension function to provide our mock database
        mockkStatic("com.github.quarck.calnotify.database.SQLiteDatabaseExtensions")
        
        // Use type-safe lambda invocation to avoid AbstractMethodError
        every {
            any<EventsStorage>().classCustomUse<EventsStorage, Any>(any())
        } answers {
            val lambda = firstArg<Function1<EventsStorageInterface, Any>>()
            lambda.invoke(mockDb)
        }

        every {
            any<DismissedEventsStorage>().classCustomUse<DismissedEventsStorage, Any>(any())
        } answers {
            val lambda = firstArg<Function1<DismissedEventsStorageInterface, Any>>()
            lambda.invoke(mockDismissedEventsDb)
        }
        
        // Set up the mock responses
        every { mockDb.getEvent(any(), any()) } returns event
        every { mockDb.deleteEvent(any(), any()) } returns true
        
        // When - call the real dismissEvent method
        ApplicationController.dismissEvent(
            mockContext,
            EventDismissType.ManuallyDismissedFromActivity,
            event.eventId,
            event.instanceStartTime,
            0,
            false
        )
        
        // Then - verify the key interactions happened on our mocks
        verify { mockDb.getEvent(any(), any()) }
        verify { mockDb.deleteEvent(any(), any()) }
        verify { mockDismissedEventsDb.addEvent(any(), any()) }
    }
    
    @Test
    fun testOriginalDismissEventWithNonExistentEvent() {
        // Given
        val event = createTestEvent()
        DevLog.info(LOG_TAG, "Created test event with id=${event.eventId}, instanceStartTime=${event.instanceStartTime}")
        
        // Now, instead of mocking the constructor, we'll use our mockDb directly
        val mockDismissedEventsDb = mockk<DismissedEventsStorageInterface>(relaxed = true)
        
        // Mock the classCustomUse extension function to provide our mock database
        mockkStatic("com.github.quarck.calnotify.database.SQLiteDatabaseExtensions")
        
        // Use type-safe lambda invocation to avoid AbstractMethodError
        every {
            any<EventsStorage>().classCustomUse<EventsStorage, Any>(any())
        } answers {
            val lambda = firstArg<Function1<EventsStorageInterface, Any>>()
            lambda.invoke(mockDb)
        }

        every {
            any<DismissedEventsStorage>().classCustomUse<DismissedEventsStorage, Any>(any())
        } answers {
            val lambda = firstArg<Function1<DismissedEventsStorageInterface, Any>>()
            lambda.invoke(mockDismissedEventsDb)
        }
        
        // Set up the mock responses - event not found
        every { mockDb.getEvent(any(), any()) } returns null
        
        // When - call the real dismissEvent method
        ApplicationController.dismissEvent(
            mockContext,
            EventDismissType.ManuallyDismissedFromActivity,
            event.eventId,
            event.instanceStartTime,
            0,
            false
        )
        
        // Then - verify the key interactions
        verify { mockDb.getEvent(any(), any()) }
        verify(exactly = 0) { mockDb.deleteEvent(any(), any()) }
        verify(exactly = 0) { mockDismissedEventsDb.addEvent(any(), any()) }
    }
    
    
    private fun createTestEvent(id: Long = 1L): EventAlertRecord {
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        DevLog.info(LOG_TAG, "Creating test event with fixed time: $currentTime")
        
        return EventAlertRecord(
            calendarId = 1L,
            eventId = id,
            isAllDay = false,
            isRepeating = false,
            alertTime = currentTime,
            notificationId = 0,
            title = "Test Event $id",
            desc = "Test Description",
            startTime = currentTime,
            endTime = currentTime + 3600000,
            instanceStartTime = currentTime,
            instanceEndTime = currentTime + 3600000,
            location = "",
            lastStatusChangeTime = currentTime,
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0xffff0000.toInt(),
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = currentTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
    }
}
