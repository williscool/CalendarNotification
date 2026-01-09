//
//   Calendar Notifications Plus
//   Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//

package com.github.quarck.calnotify.calendar

import android.app.Activity
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric tests for CalendarIntents.
 * 
 * Tests intent creation for viewing calendar events and the fallback behavior
 * when events are not found in the system calendar.
 * 
 * @see <a href="https://github.com/williscool/CalendarNotification/issues/66">Issue #66</a>
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class CalendarIntentsRobolectricTest {

    // === viewCalendarAtTime Tests ===

    @Test
    fun viewCalendarAtTime_creates_intent_with_correct_action() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val testTime = 1704067200000L // Jan 1, 2024 00:00:00 UTC

        CalendarIntents.viewCalendarAtTime(activity, testTime)

        val shadow = shadowOf(activity)
        val intent = shadow.nextStartedActivity

        assertNotNull("Intent should be started", intent)
        assertEquals("Should use ACTION_VIEW", Intent.ACTION_VIEW, intent.action)
    }

    @Test
    fun viewCalendarAtTime_creates_intent_with_correct_uri() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val testTime = 1704067200000L // Jan 1, 2024 00:00:00 UTC

        CalendarIntents.viewCalendarAtTime(activity, testTime)

        val shadow = shadowOf(activity)
        val intent = shadow.nextStartedActivity

        assertNotNull("Intent should be started", intent)
        assertEquals(
            "URI should be calendar time URI",
            "content://com.android.calendar/time/$testTime",
            intent.data.toString()
        )
    }

    @Test
    fun viewCalendarAtTime_works_with_different_timestamps() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val testTime = 1609459200000L // Jan 1, 2021 00:00:00 UTC

        CalendarIntents.viewCalendarAtTime(activity, testTime)

        val shadow = shadowOf(activity)
        val intent = shadow.nextStartedActivity

        assertEquals(
            "content://com.android.calendar/time/1609459200000",
            intent.data.toString()
        )
    }

    // === viewCalendarEventWithFallback Tests ===

    @Test
    fun viewCalendarEventWithFallback_returns_true_when_event_exists() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val mockProvider = mockk<CalendarProviderInterface>()
        val event = createTestEvent(eventId = 12345L)

        // Event exists in system calendar
        every { mockProvider.getEvent(any(), event.eventId) } returns mockk<EventRecord>()

        val result = CalendarIntents.viewCalendarEventWithFallback(activity, mockProvider, event)

        assertTrue("Should return true when event exists", result)
    }

    @Test
    fun viewCalendarEventWithFallback_opens_event_directly_when_found() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val mockProvider = mockk<CalendarProviderInterface>()
        val event = createTestEvent(eventId = 12345L)

        every { mockProvider.getEvent(any(), event.eventId) } returns mockk<EventRecord>()

        CalendarIntents.viewCalendarEventWithFallback(activity, mockProvider, event)

        val shadow = shadowOf(activity)
        val intent = shadow.nextStartedActivity

        assertNotNull("Intent should be started", intent)
        assertTrue(
            "URI should contain event ID",
            intent.data.toString().contains("/events/12345")
        )
    }

    @Test
    fun viewCalendarEventWithFallback_returns_false_when_event_not_found() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val mockProvider = mockk<CalendarProviderInterface>()
        val event = createTestEvent(eventId = 99999L, instanceStartTime = 1704067200000L)

        // Event NOT found in system calendar
        every { mockProvider.getEvent(any(), event.eventId) } returns null

        val result = CalendarIntents.viewCalendarEventWithFallback(activity, mockProvider, event)

        assertFalse("Should return false when event not found", result)
    }

    @Test
    fun viewCalendarEventWithFallback_opens_time_view_when_event_not_found() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val mockProvider = mockk<CalendarProviderInterface>()
        val instanceStartTime = 1704067200000L
        val event = createTestEvent(eventId = 99999L, instanceStartTime = instanceStartTime)

        every { mockProvider.getEvent(any(), event.eventId) } returns null

        CalendarIntents.viewCalendarEventWithFallback(activity, mockProvider, event)

        val shadow = shadowOf(activity)
        val intent = shadow.nextStartedActivity

        assertNotNull("Intent should be started", intent)
        assertEquals(
            "Should open calendar at event time",
            "content://com.android.calendar/time/$instanceStartTime",
            intent.data.toString()
        )
    }

    @Test
    fun viewCalendarEventWithFallback_uses_instance_start_time_for_fallback() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val mockProvider = mockk<CalendarProviderInterface>()
        // Different start time and instance start time (like a repeating event)
        val event = createTestEvent(
            eventId = 12345L,
            startTime = 1700000000000L,
            instanceStartTime = 1704067200000L
        )

        every { mockProvider.getEvent(any(), event.eventId) } returns null

        CalendarIntents.viewCalendarEventWithFallback(activity, mockProvider, event)

        val shadow = shadowOf(activity)
        val intent = shadow.nextStartedActivity

        // Should use instanceStartTime (via displayedStartTime), not startTime
        assertEquals(
            "content://com.android.calendar/time/1704067200000",
            intent.data.toString()
        )
    }

    @Test
    fun viewCalendarEventWithFallback_falls_back_to_start_time_when_instance_start_is_zero() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val mockProvider = mockk<CalendarProviderInterface>()
        // instanceStartTime is 0, should fall back to startTime (via displayedStartTime)
        val event = createTestEvent(
            eventId = 12345L,
            startTime = 1700000000000L,
            instanceStartTime = 0L
        )

        every { mockProvider.getEvent(any(), event.eventId) } returns null

        CalendarIntents.viewCalendarEventWithFallback(activity, mockProvider, event)

        val shadow = shadowOf(activity)
        val intent = shadow.nextStartedActivity

        // Should use startTime since instanceStartTime is 0
        assertEquals(
            "content://com.android.calendar/time/1700000000000",
            intent.data.toString()
        )
    }

    // === Helper Functions ===

    private fun createTestEvent(
        eventId: Long = 1L,
        calendarId: Long = 1L,
        title: String = "Test Event",
        startTime: Long = System.currentTimeMillis() + 3600000,
        endTime: Long = System.currentTimeMillis() + 7200000,
        instanceStartTime: Long = startTime,
        instanceEndTime: Long = endTime
    ): EventAlertRecord {
        return EventAlertRecord(
            calendarId = calendarId,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = System.currentTimeMillis(),
            notificationId = 0,
            title = title,
            desc = "",
            startTime = startTime,
            endTime = endTime,
            instanceStartTime = instanceStartTime,
            instanceEndTime = instanceEndTime,
            location = "",
            lastStatusChangeTime = System.currentTimeMillis(),
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0xFF6200EE.toInt()
        )
    }
}
