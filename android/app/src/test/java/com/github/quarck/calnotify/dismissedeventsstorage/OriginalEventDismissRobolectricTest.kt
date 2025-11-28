package com.github.quarck.calnotify.dismissedeventsstorage

import android.content.Context
import com.github.quarck.calnotify.app.AlarmScheduler
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.testutils.MockApplicationComponents
import com.github.quarck.calnotify.testutils.MockCalendarProvider
import com.github.quarck.calnotify.testutils.MockContextProvider
import com.github.quarck.calnotify.testutils.MockTimeProvider
import com.github.quarck.calnotify.ui.UINotifier
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSQLiteConnection
import java.util.Locale

/**
 * Robolectric version of OriginalEventDismissTest for testing event dismissal functionality.
 * 
 * This test class uses Robolectric to simulate the Android environment, allowing us to test
 * components that interact with Android framework classes without requiring a device or emulator.
 * 
 * The test focuses on ApplicationController.dismissEvent functionality using dependency injection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class OriginalEventDismissRobolectricTest {
    private val LOG_TAG = "OrigEventDismissRoboTest"

    private lateinit var mockContext: Context
    private lateinit var mockTimeProvider: MockTimeProvider
    private lateinit var mockDb: EventsStorageInterface
    private lateinit var mockComponents: MockApplicationComponents

    // Keep mock instance for AlarmScheduler verification
    private lateinit var mockAlarmScheduler: AlarmScheduler

    private lateinit var mockDismissedEventsStorage: DismissedEventsStorage
    private lateinit var mockReminderState: ReminderState

    private var originalLocale: Locale? = null

    @Before
    fun setup() {
        // Enable Robolectric logging
        ShadowLog.stream = System.out

        // Configure Robolectric SQLite to use in-memory database
        ShadowSQLiteConnection.setUseInMemoryDatabase(true)

        // Store original locale and set a fixed one for the test
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)

        DevLog.info(LOG_TAG, "Setting up OriginalEventDismissRobolectricTest with injectable db")

        // Setup mock time provider
        mockTimeProvider = MockTimeProvider(1635724800000) // 2021-11-01 00:00:00 UTC
        mockTimeProvider.setup()

        // Setup mock database
        mockDb = mockk<EventsStorageInterface>(relaxed = true)

        // Setup mock providers (will use Robolectric context internally)
        val mockContextProvider = MockContextProvider(mockTimeProvider)
        mockContextProvider.setup() // This will get Robolectric context automatically

        val mockCalendarProvider = MockCalendarProvider(mockContextProvider, mockTimeProvider)
        mockCalendarProvider.setup()

        // Setup mock components - this mocks ApplicationController.notificationManager to prevent database access
        mockComponents = MockApplicationComponents(
            contextProvider = mockContextProvider,
            timeProvider = mockTimeProvider,
            calendarProvider = mockCalendarProvider
        )
        mockComponents.setup()

        mockContext = mockContextProvider.fakeContext!!

        // 1. Create relaxed mock instances
        mockDismissedEventsStorage = mockk<DismissedEventsStorage>(relaxed = true)
        mockAlarmScheduler = mockk<AlarmScheduler>(relaxed = true)
        mockReminderState = mockk<ReminderState>(relaxed = true)

        // 2. Define default behavior on mock instances
        every { mockDb.getEvent(any(), any()) } returns null

        // Mock UINotifier object
        mockkObject(UINotifier)
        every { UINotifier.notify(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        // Restore original locale
        originalLocale?.let { Locale.setDefault(it) }

        unmockkObject(UINotifier)
        unmockkAll()
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

    @Test
    fun testDismissEventWithNotifyActivity() {
        // Given
        val event = createTestEvent()
        every { mockDb.getEvent(event.eventId, event.instanceStartTime) } returns event
        every { mockDb.deleteEvent(event.eventId, event.instanceStartTime) } returns true

        // When - call with notifyActivity = true
        ApplicationController.dismissEvent(
            mockContext,
            EventDismissType.ManuallyDismissedFromActivity,
            event.eventId,
            event.instanceStartTime,
            event.notificationId,
            true, // notifyActivity = true
            db = mockDb,
            dismissedEventsStorage = mockDismissedEventsStorage
        )

        // Then - verify UINotifier was called
        verify { mockDb.getEvent(event.eventId, event.instanceStartTime) }
        verify { mockDismissedEventsStorage.addEvent(EventDismissType.ManuallyDismissedFromActivity, event) }
        verify { mockDb.deleteEvent(event.eventId, event.instanceStartTime) }
        verify { UINotifier.notify(any(), any()) }
    }

    @Test
    fun testDismissEventsWithEmptyList() {
        // Given
        val events = emptyList<EventAlertRecord>()

        // When
        ApplicationController.dismissEvents(
            mockContext,
            mockDb,
            events,
            EventDismissType.ManuallyDismissedFromActivity,
            notifyActivity = false,
            dismissedEventsStorage = mockDismissedEventsStorage
        )

        // Then - verify nothing was called since the list is empty
        verify(exactly = 0) { mockDismissedEventsStorage.addEvents(any(), any()) }
        verify(exactly = 0) { mockDb.deleteEvents(any()) }
        verify(exactly = 0) { UINotifier.notify(any(), any()) }
    }

    @Test
    fun testDismissEventWithDifferentDismissTypes() {
        // Test each dismiss type
        val dismissTypes = listOf(
            EventDismissType.ManuallyDismissedFromActivity,
            EventDismissType.ManuallyDismissedFromNotification,
            EventDismissType.AutoDismissedDueToCalendarMove,
            EventDismissType.AutoDismissedDueToRescheduleConfirmation
        )

        dismissTypes.forEach { dismissType ->
            // Given
            val event = createTestEvent(dismissType.ordinal.toLong())
            every { mockDb.getEvent(event.eventId, event.instanceStartTime) } returns event
            every { mockDb.deleteEvent(event.eventId, event.instanceStartTime) } returns true

            // When
            ApplicationController.dismissEvent(
                mockContext,
                dismissType,
                event.eventId,
                event.instanceStartTime,
                event.notificationId,
                false,
                db = mockDb,
                dismissedEventsStorage = mockDismissedEventsStorage
            )

            // Then
            verify { mockDismissedEventsStorage.addEvent(dismissType, event) }
        }
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
            notificationId = (id % Int.MAX_VALUE).toInt(),
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
