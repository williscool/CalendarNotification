package com.github.quarck.calnotify.dismissedeventsstorage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.quarck.calnotify.app.AlarmScheduler // Keep import for mock type
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
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.classCustomUse // Keep import if needed by code under test

/**
 * Test for ApplicationController.dismissEvent functionality using constructor mocking.
 */
@RunWith(AndroidJUnit4::class)
class OriginalEventDismissTest {
  private val LOG_TAG = "OriginalEventDismissTest"

  private lateinit var mockContext: Context
  private lateinit var mockTimeProvider: MockTimeProvider
  private lateinit var mockDb: EventsStorageInterface

  // Keep mock instance for AlarmScheduler verification, though injection isn't handled here
  private lateinit var mockAlarmScheduler: AlarmScheduler

  private lateinit var mockDismissedEventsStorage: DismissedEventsStorage
  private lateinit var mockReminderState: ReminderState

  @Before
  fun setup() {
    DevLog.info(LOG_TAG, "Setting up EventDismissTest with injectable db")

    // Setup mock time provider
    mockTimeProvider = MockTimeProvider(1635724800000) // 2021-11-01 00:00:00 UTC
    mockTimeProvider.setup()

    // Create mock context
    val mockContextProvider = MockContextProvider(mockTimeProvider)
    mockContextProvider.setup()
    mockContext = mockContextProvider.fakeContext

    // 1. Create relaxed mock instances
    mockDb = mockk(relaxed = true)
    mockDismissedEventsStorage = mockk<DismissedEventsStorage>(relaxed = true)
    mockAlarmScheduler = mockk<AlarmScheduler>(relaxed = true) // Keep for verification
    mockReminderState = mockk<ReminderState>(relaxed = true)

    // 2. Define default behavior on mock instances
    every { mockDb.getEvent(any(), any()) } returns null
    // Relaxed mocks handle Unit returns for addEvent, deleteEvent, onUserInteraction

    // Mock UINotifier object
    mockkObject(UINotifier)
    every { UINotifier.notify(any(), any()) } returns Unit
  }

  @After
  fun tearDown() {
    unmockkObject(UINotifier)
    unmockkAll() // Clears mocks and other MockK state
  }

  @Test
  fun testOriginalDismissEventWithValidEvent() {
    // Given
    val event = createTestEvent()
    DevLog.info(LOG_TAG, "Created test event with id=${event.eventId}, instanceStartTime=${event.instanceStartTime}")

    // Override default behavior for this specific test case on the mock instance
    every { mockDb.getEvent(event.eventId, event.instanceStartTime) } returns event
    // Ensure the delete operation succeeds so the inner logic runs
    every { mockDb.deleteEvent(event.eventId, event.instanceStartTime) } returns true

    // When - call the ApplicationController.dismissEvent
    ApplicationController.dismissEvent(
      mockContext,
      EventDismissType.ManuallyDismissedFromActivity,
      event.eventId,
      event.instanceStartTime,
      event.notificationId,
      false, // notifyActivity = false
      db = mockDb, // inject mock db
      dismissedEventsStorage = mockDismissedEventsStorage // Inject mock dismissed storage
    )

    // Then - verify interactions directly on our mock instances
    verify { mockDb.getEvent(event.eventId, event.instanceStartTime) }
    verify { mockDismissedEventsStorage.addEvent(EventDismissType.ManuallyDismissedFromActivity, event) }
    verify { mockDb.deleteEvent(event.eventId, event.instanceStartTime) }
    verify(exactly = 0) { UINotifier.notify(any(), any()) }
  }

  @Test
  fun testOriginalDismissEventWithNonExistentEvent() {
    // Given
    val event = createTestEvent()
    DevLog.info(LOG_TAG, "Created test event with id=${event.eventId}, instanceStartTime=${event.instanceStartTime}")

    // Behavior for non-existent event (getEvent returns null) is the default set in setup()

    // When - call the ApplicationController.dismissEvent
    ApplicationController.dismissEvent(
      mockContext,
      EventDismissType.ManuallyDismissedFromActivity,
      event.eventId,
      event.instanceStartTime,
      event.notificationId,
      false, // notifyActivity = false
      db = mockDb // inject mock
    )

    // Then - verify interactions directly on our mock instances
    verify { mockDb.getEvent(event.eventId, event.instanceStartTime) }
    // Verify these were NOT called
    verify(exactly = 0) { mockDismissedEventsStorage.addEvent(any(), any()) }
    verify(exactly = 0) { mockDb.deleteEvent(any(), any()) }
    verify(exactly = 0) { UINotifier.notify(any(), any()) }
  }

  @Test
  fun testDismissEventsWithValidEvents() {
    // Given
    val event1 = createTestEvent(1)
    val event2 = createTestEvent(2)
    val events = listOf(event1, event2)

    // Setup: db.deleteEvents returns the number of events (success)
    every { mockDb.deleteEvents(events) } returns events.size
    // Setup: db.events returns an empty list for hasActiveEvents check
    every { mockDb.events } returns emptyList()

    // When
    ApplicationController.dismissEvents(
      mockContext,
      mockDb,
      events,
      EventDismissType.ManuallyDismissedFromActivity,
      notifyActivity = false,
      dismissedEventsStorage = mockDismissedEventsStorage // Inject mock dismissed storage
    )

    // Then
    verify { mockDismissedEventsStorage.addEvents(EventDismissType.ManuallyDismissedFromActivity, events) }
    verify { mockDb.deleteEvents(events) }
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
      notificationId = (id % Int.MAX_VALUE).toInt(), // Ensure notificationId is derived
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
