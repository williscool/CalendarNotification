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

package com.github.quarck.calnotify.upcoming

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.monitorstorage.MonitorStorageInterface
import com.github.quarck.calnotify.utils.CNPlusUnitTestClock
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for UpcomingEventsProvider - fetching and enriching upcoming alerts
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class UpcomingEventsProviderTest {

    private lateinit var context: Context
    private lateinit var settings: Settings
    private lateinit var clock: CNPlusUnitTestClock
    private lateinit var monitorStorage: MonitorStorageInterface
    private lateinit var calendarProvider: CalendarProviderInterface

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settings = Settings(context)
        clock = CNPlusUnitTestClock()
        monitorStorage = mockk()
        calendarProvider = mockk()
        
        // Default settings
        settings.upcomingEventsMode = UpcomingEventsLookahead.MODE_FIXED
        settings.upcomingEventsFixedHours = 8
    }

    @Test
    fun testGetUpcomingEvents_noAlerts() {
        val now = 1000000L
        clock.setCurrentTime(now)
        
        // No alerts in range
        every { monitorStorage.getAlertsForAlertRange(any(), any()) } returns emptyList()
        
        val provider = UpcomingEventsProvider(context, settings, clock, monitorStorage, calendarProvider)
        val events = provider.getUpcomingEvents()
        
        assertTrue("Should return empty list when no alerts", events.isEmpty())
    }

    @Test
    fun testGetUpcomingEvents_filtersHandledAlerts() {
        val now = 1000000L
        clock.setCurrentTime(now)
        
        val alerts = listOf(
            createAlert(eventId = 1, alertTime = now + 1000, wasHandled = false),
            createAlert(eventId = 2, alertTime = now + 2000, wasHandled = true),  // Should be filtered
            createAlert(eventId = 3, alertTime = now + 3000, wasHandled = false)
        )
        
        every { monitorStorage.getAlertsForAlertRange(any(), any()) } returns alerts
        every { calendarProvider.getEvent(any(), 1) } returns createEventRecord(1)
        every { calendarProvider.getEvent(any(), 3) } returns createEventRecord(3)
        
        val provider = UpcomingEventsProvider(context, settings, clock, monitorStorage, calendarProvider)
        val events = provider.getUpcomingEvents()
        
        assertEquals("Should return 2 unhandled events", 2, events.size)
        assertEquals("First event should be eventId 1", 1L, events[0].eventId)
        assertEquals("Second event should be eventId 3", 3L, events[1].eventId)
    }

    @Test
    fun testGetUpcomingEvents_sortedByAlertTime() {
        val now = 1000000L
        clock.setCurrentTime(now)
        
        val alerts = listOf(
            createAlert(eventId = 1, alertTime = now + 3000, wasHandled = false),
            createAlert(eventId = 2, alertTime = now + 1000, wasHandled = false),
            createAlert(eventId = 3, alertTime = now + 2000, wasHandled = false)
        )
        
        every { monitorStorage.getAlertsForAlertRange(any(), any()) } returns alerts
        every { calendarProvider.getEvent(any(), 1) } returns createEventRecord(1)
        every { calendarProvider.getEvent(any(), 2) } returns createEventRecord(2)
        every { calendarProvider.getEvent(any(), 3) } returns createEventRecord(3)
        
        val provider = UpcomingEventsProvider(context, settings, clock, monitorStorage, calendarProvider)
        val events = provider.getUpcomingEvents()
        
        assertEquals("Should return 3 events", 3, events.size)
        assertEquals("First event should be eventId 2 (earliest alertTime)", 2L, events[0].eventId)
        assertEquals("Second event should be eventId 3", 3L, events[1].eventId)
        assertEquals("Third event should be eventId 1 (latest alertTime)", 1L, events[2].eventId)
    }

    @Test
    fun testGetUpcomingEvents_skipsDeletedEvents() {
        val now = 1000000L
        clock.setCurrentTime(now)
        
        val alerts = listOf(
            createAlert(eventId = 1, alertTime = now + 1000, wasHandled = false),
            createAlert(eventId = 2, alertTime = now + 2000, wasHandled = false)  // Will be "deleted"
        )
        
        every { monitorStorage.getAlertsForAlertRange(any(), any()) } returns alerts
        every { calendarProvider.getEvent(any(), 1) } returns createEventRecord(1)
        every { calendarProvider.getEvent(any(), 2) } returns null  // Event deleted
        
        val provider = UpcomingEventsProvider(context, settings, clock, monitorStorage, calendarProvider)
        val events = provider.getUpcomingEvents()
        
        assertEquals("Should return 1 event (other was deleted)", 1, events.size)
        assertEquals("Should be eventId 1", 1L, events[0].eventId)
    }

    @Test
    fun testGetUpcomingEvents_enrichesWithEventDetails() {
        val now = 1000000L
        clock.setCurrentTime(now)
        
        val alert = createAlert(eventId = 1, alertTime = now + 1000, wasHandled = false)
        
        every { monitorStorage.getAlertsForAlertRange(any(), any()) } returns listOf(alert)
        every { calendarProvider.getEvent(any(), 1) } returns EventRecord(
            calendarId = 100,
            eventId = 1,
            details = CalendarEventDetails(
                title = "Test Event",
                desc = "Test Description",
                location = "Test Location",
                timezone = "UTC",
                startTime = now + 10000,
                endTime = now + 20000,
                isAllDay = false,
                reminders = emptyList(),
                repeatingRule = "",
                repeatingRDate = "",
                repeatingExRule = "",
                repeatingExRDate = "",
                color = 0xFF0000
            ),
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.Accepted
        )
        
        val provider = UpcomingEventsProvider(context, settings, clock, monitorStorage, calendarProvider)
        val events = provider.getUpcomingEvents()
        
        assertEquals("Should return 1 event", 1, events.size)
        val event = events[0]
        assertEquals("Should have correct title", "Test Event", event.title)
        assertEquals("Should have correct description", "Test Description", event.desc)
        assertEquals("Should have correct location", "Test Location", event.location)
        assertEquals("Should have correct calendarId", 100L, event.calendarId)
        assertEquals("Should have correct color", 0xFF0000, event.color)
    }

    @Test
    fun testGetUpcomingEvents_propagatesPreMutedFlag() {
        val now = 1000000L
        clock.setCurrentTime(now)
        
        val alert = createAlert(
            eventId = 1,
            alertTime = now + 1000,
            wasHandled = false,
            flags = MonitorEventAlertEntry.PRE_MUTED_FLAG  // Pre-muted
        )
        
        every { monitorStorage.getAlertsForAlertRange(any(), any()) } returns listOf(alert)
        every { calendarProvider.getEvent(any(), 1) } returns createEventRecord(1)
        
        val provider = UpcomingEventsProvider(context, settings, clock, monitorStorage, calendarProvider)
        val events = provider.getUpcomingEvents()
        
        assertEquals("Should return 1 event", 1, events.size)
        assertTrue("Event should be muted", events[0].isMuted)
    }

    // === Helper Functions ===
    
    private fun createAlert(
        eventId: Long,
        alertTime: Long,
        wasHandled: Boolean,
        flags: Long = 0
    ): MonitorEventAlertEntry {
        return MonitorEventAlertEntry(
            eventId = eventId,
            isAllDay = false,
            alertTime = alertTime,
            instanceStartTime = alertTime + Consts.HOUR_IN_MILLISECONDS,
            instanceEndTime = alertTime + (2 * Consts.HOUR_IN_MILLISECONDS),
            alertCreatedByUs = false,
            wasHandled = wasHandled,
            flags = flags
        )
    }
    
    private fun createEventRecord(eventId: Long): EventRecord {
        return EventRecord(
            calendarId = 1,
            eventId = eventId,
            details = CalendarEventDetails(
                title = "Event $eventId",
                desc = "",
                location = "",
                timezone = "UTC",
                startTime = 0,
                endTime = 0,
                isAllDay = false,
                reminders = emptyList(),
                repeatingRule = "",
                repeatingRDate = "",
                repeatingExRule = "",
                repeatingExRDate = "",
                color = 0
            ),
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None
        )
    }
}
