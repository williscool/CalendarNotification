package com.github.quarck.calnotify.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.utils.CNPlusUnitTestClock
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for CalendarReloadManager - calendar event change handling
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class CalendarReloadManagerRobolectricTest {

    private lateinit var context: Context
    private lateinit var testClock: CNPlusUnitTestClock
    private lateinit var mockCalendarProvider: CalendarProviderInterface
    private lateinit var mockEventsStorage: EventsStorageInterface

    private val baseTime = 1635724800000L // 2021-11-01 00:00:00 UTC

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testClock = CNPlusUnitTestClock(baseTime)

        mockCalendarProvider = mockk(relaxed = true)
        mockEventsStorage = mockk(relaxed = true)

        // Mock ApplicationController to prevent side effects
        mockkObject(ApplicationController)
        every { ApplicationController.dismissEvents(any(), any(), any(), any(), any()) } just Runs
    }

    @After
    fun cleanup() {
        unmockkAll()
    }

    private fun createTestEvent(
        eventId: Long = 1L,
        title: String = "Test Event",
        startTime: Long = baseTime + 3600000,
        endTime: Long = baseTime + 7200000,
        instanceStartTime: Long = baseTime + 3600000,
        instanceEndTime: Long = baseTime + 7200000,
        isRepeating: Boolean = false
    ): EventAlertRecord {
        return EventAlertRecord(
            calendarId = 1L,
            eventId = eventId,
            isAllDay = false,
            isRepeating = isRepeating,
            alertTime = baseTime,
            notificationId = eventId.toInt(),
            title = title,
            desc = "Test Description",
            startTime = startTime,
            endTime = endTime,
            instanceStartTime = instanceStartTime,
            instanceEndTime = instanceEndTime,
            location = "",
            lastStatusChangeTime = baseTime,
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = baseTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
    }

    // === ReloadCalendarResultCode tests ===

    @Test
    fun testResultCodeValues() {
        // Verify all result codes exist
        assertEquals(4, CalendarReloadManager.ReloadCalendarResultCode.values().size)
        assertNotNull(CalendarReloadManager.ReloadCalendarResultCode.NoChange)
        assertNotNull(CalendarReloadManager.ReloadCalendarResultCode.EventMovedShouldAutoDismiss)
        assertNotNull(CalendarReloadManager.ReloadCalendarResultCode.EventDetailsUpdatedShouldUpdate)
        assertNotNull(CalendarReloadManager.ReloadCalendarResultCode.EventInstanceMovedShouldUpdate)
    }

    // === checkCalendarAlertHasChanged tests ===

    @Test
    fun testCheckCalendarAlertNoChange() {
        // Given - identical events
        val event = createTestEvent(eventId = 100L)
        val newAlert = createTestEvent(eventId = 100L)

        // When
        val result = CalendarReloadManager.checkCalendarAlertHasChanged(context, event, newAlert)

        // Then
        assertEquals(CalendarReloadManager.ReloadCalendarResultCode.NoChange, result.code)
    }

    @Test
    fun testCheckCalendarAlertTitleChanged() {
        // Given - event with changed title
        val event = createTestEvent(eventId = 101L, title = "Original Title")
        val newAlert = createTestEvent(eventId = 101L, title = "Updated Title")

        // When
        val result = CalendarReloadManager.checkCalendarAlertHasChanged(context, event, newAlert)

        // Then
        assertEquals(CalendarReloadManager.ReloadCalendarResultCode.EventDetailsUpdatedShouldUpdate, result.code)
        assertEquals("Updated Title", result.event.title)
    }

    @Test
    fun testCheckCalendarAlertInstanceTimeMoved() {
        // Given - event with moved instance time (non-repeating)
        val originalInstanceStart = baseTime + 3600000
        val newInstanceStart = baseTime + 7200000 // Moved 1 hour later

        val event = createTestEvent(
            eventId = 102L,
            instanceStartTime = originalInstanceStart,
            instanceEndTime = originalInstanceStart + 3600000,
            isRepeating = false
        )
        val newAlert = createTestEvent(
            eventId = 102L,
            instanceStartTime = newInstanceStart,
            instanceEndTime = newInstanceStart + 3600000,
            isRepeating = false
        )

        // When
        val result = CalendarReloadManager.checkCalendarAlertHasChanged(context, event, newAlert)

        // Then
        assertEquals(CalendarReloadManager.ReloadCalendarResultCode.EventInstanceMovedShouldUpdate, result.code)
        assertEquals(newInstanceStart, result.newInstanceStartTime)
    }

    @Test
    fun testCheckCalendarAlertRepeatingEventUpdated() {
        // Given - repeating event with changed details
        val event = createTestEvent(eventId = 103L, title = "Repeating Original", isRepeating = true)
        val newAlert = createTestEvent(eventId = 103L, title = "Repeating Updated", isRepeating = true)

        // When
        val result = CalendarReloadManager.checkCalendarAlertHasChanged(context, event, newAlert)

        // Then
        // Repeating events should just be updated, not have instance time changes
        assertEquals(CalendarReloadManager.ReloadCalendarResultCode.EventDetailsUpdatedShouldUpdate, result.code)
    }

    // === reloadCalendar tests ===

    @Test
    fun testReloadCalendarNoEvents() {
        // Given - no events in storage
        every { mockEventsStorage.events } returns emptyList()

        // When
        val changed = CalendarReloadManager.reloadCalendar(
            context,
            mockEventsStorage,
            mockCalendarProvider,
            null
        )

        // Then
        assertFalse("No changes expected with empty event list", changed)
    }

    @Test
    fun testReloadCalendarWithUnchangedEvent() {
        // Given - event that hasn't changed
        val event = createTestEvent(eventId = 200L)
        every { mockEventsStorage.events } returns listOf(event)

        // Mock calendar provider to return same event
        every { mockCalendarProvider.getAlertByEventIdAndTime(any(), event.eventId, event.alertTime) } returns event

        // When
        val changed = CalendarReloadManager.reloadCalendar(
            context,
            mockEventsStorage,
            mockCalendarProvider,
            null
        )

        // Then
        assertFalse("No changes expected when event unchanged", changed)
    }

    @Test
    fun testReloadCalendarWithUpdatedEvent() {
        // Given - event that was updated
        val event = createTestEvent(eventId = 201L, title = "Original")
        val updatedEvent = createTestEvent(eventId = 201L, title = "Updated")
        every { mockEventsStorage.events } returns listOf(event)

        // Mock calendar provider to return updated event
        every { mockCalendarProvider.getAlertByEventIdAndTime(any(), event.eventId, event.alertTime) } returns updatedEvent

        // When
        val changed = CalendarReloadManager.reloadCalendar(
            context,
            mockEventsStorage,
            mockCalendarProvider,
            null
        )

        // Then
        assertTrue("Changes expected when event updated", changed)
        verify { mockEventsStorage.updateEvents(any()) }
    }

    // === reloadSingleEvent tests ===

    @Test
    fun testReloadSingleEventNoChange() {
        // Given
        val event = createTestEvent(eventId = 300L)

        // Mock calendar provider to return same event
        every { mockCalendarProvider.getAlertByEventIdAndTime(any(), event.eventId, event.alertTime) } returns event

        // When
        val changed = CalendarReloadManager.reloadSingleEvent(
            context,
            mockEventsStorage,
            event,
            mockCalendarProvider,
            null
        )

        // Then
        assertFalse("No changes expected", changed)
    }

    // === ReloadCalendarResult data class tests ===

    @Test
    fun testReloadCalendarResultCreation() {
        val event = createTestEvent(eventId = 400L)
        
        val result = CalendarReloadManager.ReloadCalendarResult(
            code = CalendarReloadManager.ReloadCalendarResultCode.EventDetailsUpdatedShouldUpdate,
            event = event,
            newInstanceStartTime = baseTime + 7200000,
            newInstanceEndTime = baseTime + 10800000,
            setDisplayStatusHidden = true
        )

        assertEquals(CalendarReloadManager.ReloadCalendarResultCode.EventDetailsUpdatedShouldUpdate, result.code)
        assertEquals(event, result.event)
        assertEquals(baseTime + 7200000, result.newInstanceStartTime)
        assertEquals(baseTime + 10800000, result.newInstanceEndTime)
        assertTrue(result.setDisplayStatusHidden)
    }

    @Test
    fun testReloadCalendarResultDefaults() {
        val event = createTestEvent(eventId = 401L)
        
        val result = CalendarReloadManager.ReloadCalendarResult(
            code = CalendarReloadManager.ReloadCalendarResultCode.NoChange,
            event = event
        )

        assertNull(result.newInstanceStartTime)
        assertNull(result.newInstanceEndTime)
        assertTrue(result.setDisplayStatusHidden) // default is true
    }
}

