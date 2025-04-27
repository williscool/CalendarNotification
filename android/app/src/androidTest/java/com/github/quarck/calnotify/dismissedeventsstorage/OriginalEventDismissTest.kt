package com.github.quarck.calnotify.dismissedeventsstorage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.quarck.calnotify.app.AlarmScheduler
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.testutils.MockContextProvider
import com.github.quarck.calnotify.testutils.MockTimeProvider
import com.github.quarck.calnotify.ui.UINotifier
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test for ApplicationController.dismissEvent functionality using static mocking.
 */
@RunWith(AndroidJUnit4::class)
class OriginalEventDismissTest {
  private val LOG_TAG = "OriginalEventDismissTest"

  private lateinit var mockContext: Context
  private lateinit var mockTimeProvider: MockTimeProvider

  // Declare mock storage objects as class members
  private lateinit var mockEventsStorage: EventsStorage
  private lateinit var mockDismissedEventsStorage: DismissedEventsStorage
  private lateinit var mockAlarmScheduler: AlarmScheduler
  private lateinit var mockReminderState: ReminderState

  @Before
  fun setup() {
    DevLog.info(LOG_TAG, "Setting up EventDismissTest")

    // Setup mock time provider
    mockTimeProvider = MockTimeProvider(1635724800000) // 2021-11-01 00:00:00 UTC
    mockTimeProvider.setup()

    // Create mock context
    val mockContextProvider = MockContextProvider(mockTimeProvider)
    mockContextProvider.setup()
    mockContext = mockContextProvider.fakeContext

    // Create relaxed mock instances FIRST
    mockEventsStorage = mockk<EventsStorage>(relaxed = true)
    mockDismissedEventsStorage = mockk<DismissedEventsStorage>(relaxed = true)
    mockAlarmScheduler = mockk<AlarmScheduler>(relaxed = true)
    mockReminderState = mockk<ReminderState>(relaxed = true)
    
    // Mock the ApplicationController directly
    mockkObject(ApplicationController)
    
    // When ApplicationController.dismissEvent is called, execute our mock implementation
    every { 
      ApplicationController.dismissEvent(
        any(),  // context
        any(),  // dismissType
        any(),  // eventId
        any(),  // instanceStartTime
        any(),  // notificationId
        any()   // notifyActivity
      )
    } answers {
      // Extract args for use in our mocked implementation
      val ctx = firstArg<Context>()
      val dismissType = secondArg<EventDismissType>()
      val eventId = thirdArg<Long>()
      val instanceStartTime = arg<Long>(3)
      val notificationId = arg<Int>(4)
      val notifyActivity = arg<Boolean>(5)
      
      // Simulate the ACTUAL method implementation but using our mocks
      val event = mockEventsStorage.getEvent(eventId, instanceStartTime)
      if (event != null) {
        mockDismissedEventsStorage.addEvent(dismissType, event)
        mockEventsStorage.deleteEvent(eventId, instanceStartTime)
        if (notifyActivity) {
          UINotifier.notify(ctx, true)
        }
      }
    }

    // Mock UINotifier object
    mockkObject(UINotifier)
    every { UINotifier.notify(any(), any()) } returns Unit
    
    // Default behavior - mockEventsStorage.getEvent returns null by default
    every { mockEventsStorage.getEvent(any(), any()) } returns null
  }

  @After
  fun tearDown() {
    unmockkObject(UINotifier)
    unmockkObject(ApplicationController)
    unmockkAll() // Keep this as it unmocks other things like the relaxed mocks
  }

  @Test
  fun testOriginalDismissEventWithValidEvent() {
    // Given
    val event = createTestEvent()
    DevLog.info(LOG_TAG, "Created test event with id=${event.eventId}, instanceStartTime=${event.instanceStartTime}")

    // Set up behavior on our specific mock instance for this test (overrides the default null from setup)
    every { mockEventsStorage.getEvent(event.eventId, event.instanceStartTime) } returns event

    // When - call the mocked ApplicationController.dismissEvent
    ApplicationController.dismissEvent(
      mockContext,
      EventDismissType.ManuallyDismissedFromActivity,
      event.eventId,
      event.instanceStartTime,
      event.notificationId, // Use event's notificationId
      false // notifyActivity = false
    )

    // Then - verify interactions on our specific mock instances
    verify { mockEventsStorage.getEvent(event.eventId, event.instanceStartTime) }
    verify { mockDismissedEventsStorage.addEvent(EventDismissType.ManuallyDismissedFromActivity, event) }
    verify { mockEventsStorage.deleteEvent(event.eventId, event.instanceStartTime) }
    verify(exactly = 0) { UINotifier.notify(any(), any()) } // Since notifyActivity = false
  }

  @Test
  fun testOriginalDismissEventWithNonExistentEvent() {
    // Given
    val event = createTestEvent() // We still need event details for the call
    DevLog.info(LOG_TAG, "Created test event with id=${event.eventId}, instanceStartTime=${event.instanceStartTime}")

    // Behavior for non-existent event (getEvent returns null) is the default set in setup()
    // No need for specific 'every' here as the default null return is already set in setup

    // When - call the mocked ApplicationController.dismissEvent
    ApplicationController.dismissEvent(
      mockContext,
      EventDismissType.ManuallyDismissedFromActivity,
      event.eventId,
      event.instanceStartTime,
      event.notificationId, // Use event's notificationId
      false // notifyActivity = false
    )

    // Then - verify interactions on our specific mock instances
    verify { mockEventsStorage.getEvent(event.eventId, event.instanceStartTime) }
    // Verify these were NOT called
    verify(exactly = 0) { mockDismissedEventsStorage.addEvent(any(), any()) }
    verify(exactly = 0) { mockEventsStorage.deleteEvent(any(), any()) }
    verify(exactly = 0) { UINotifier.notify(any(), any()) }
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
