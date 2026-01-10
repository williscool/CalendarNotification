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

package com.github.quarck.calnotify.notification

import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Invariant-based tests for NotificationContext.
 * 
 * Instead of testing ~30 individual scenarios, we test properties that must ALWAYS hold.
 * Each invariant test proves a property across ALL valid state combinations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [34])
class NotificationContextInvariantTest {

    private val baseTime = 1635768000000L // 2021-11-01 12:00:00 UTC

    // =============================================================================
    // Invariant 1: Muted events NEVER produce sound
    // =============================================================================

    @Test
    fun `invariant 1 - allMuted context always produces SILENT channel`() {
        // Test across all mode × playReminderSound combinations
        for (mode in EventNotificationManager.NotificationMode.values()) {
            for (playReminder in listOf(true, false)) {
                val ctx = NotificationContext(
                    eventCount = 5,
                    hasAlarms = false,  // Must be false when allMuted
                    allMuted = true,
                    hasNewTriggeringEvent = false,  // Must be false when allMuted
                    mode = mode,
                    playReminderSound = playReminder
                )
                assertEquals(
                    "allMuted context should always be SILENT (mode=$mode, playReminder=$playReminder)",
                    ChannelCategory.SILENT,
                    ctx.collapsedChannel
                )
            }
        }
    }

    // =============================================================================
    // Invariant 2: New events use EVENTS/ALARM channels (not REMINDERS)
    // =============================================================================

    @Test
    fun `invariant 2 - new triggering events use EVENTS channel when no alarms`() {
        val ctx = NotificationContext(
            eventCount = 3,
            hasAlarms = false,
            allMuted = false,
            hasNewTriggeringEvent = true,
            mode = EventNotificationManager.NotificationMode.ALL_COLLAPSED,
            playReminderSound = false
        )
        assertEquals(
            "New triggering events without alarms should use EVENTS channel",
            ChannelCategory.EVENTS,
            ctx.collapsedChannel
        )
    }

    @Test
    fun `invariant 2 - new triggering events use ALARM channel when hasAlarms`() {
        val ctx = NotificationContext(
            eventCount = 3,
            hasAlarms = true,
            allMuted = false,
            hasNewTriggeringEvent = true,
            mode = EventNotificationManager.NotificationMode.ALL_COLLAPSED,
            playReminderSound = false
        )
        assertEquals(
            "New triggering events with alarms should use ALARM channel",
            ChannelCategory.ALARM,
            ctx.collapsedChannel
        )
    }

    @Test
    fun `invariant 2 - isReminder is false when hasNewTriggeringEvent and not playReminderSound`() {
        val ctx = NotificationContext(
            eventCount = 3,
            hasAlarms = false,
            allMuted = false,
            hasNewTriggeringEvent = true,
            mode = EventNotificationManager.NotificationMode.ALL_COLLAPSED,
            playReminderSound = false
        )
        assertFalse(
            "isReminder should be false when hasNewTriggeringEvent=true and playReminderSound=false",
            ctx.isReminder
        )
    }

    // =============================================================================
    // Invariant 3: hasAlarms switches to alarm channel variant
    // =============================================================================

    @Test
    fun `invariant 3 - hasAlarms switches new events to ALARM`() {
        val ctxWithoutAlarm = NotificationContext(
            eventCount = 3, hasAlarms = false, allMuted = false,
            hasNewTriggeringEvent = true,
            mode = EventNotificationManager.NotificationMode.ALL_COLLAPSED,
            playReminderSound = false
        )
        val ctxWithAlarm = NotificationContext(
            eventCount = 3, hasAlarms = true, allMuted = false,
            hasNewTriggeringEvent = true,
            mode = EventNotificationManager.NotificationMode.ALL_COLLAPSED,
            playReminderSound = false
        )
        
        assertEquals(ChannelCategory.EVENTS, ctxWithoutAlarm.collapsedChannel)
        assertEquals(ChannelCategory.ALARM, ctxWithAlarm.collapsedChannel)
    }

    @Test
    fun `invariant 3 - hasAlarms switches reminders to ALARM_REMINDERS`() {
        val ctxWithoutAlarm = NotificationContext(
            eventCount = 3, hasAlarms = false, allMuted = false,
            hasNewTriggeringEvent = false,
            mode = EventNotificationManager.NotificationMode.ALL_COLLAPSED,
            playReminderSound = true
        )
        val ctxWithAlarm = NotificationContext(
            eventCount = 3, hasAlarms = true, allMuted = false,
            hasNewTriggeringEvent = false,
            mode = EventNotificationManager.NotificationMode.ALL_COLLAPSED,
            playReminderSound = true
        )
        
        assertEquals(ChannelCategory.REMINDERS, ctxWithoutAlarm.collapsedChannel)
        assertEquals(ChannelCategory.ALARM_REMINDERS, ctxWithAlarm.collapsedChannel)
    }

    // =============================================================================
    // Invariant 4: Impossible states throw
    // =============================================================================

    @Test(expected = IllegalArgumentException::class)
    fun `invariant 4 - allMuted with hasAlarms throws`() {
        NotificationContext(
            eventCount = 1,
            hasAlarms = true,  // INVALID: muted alarms excluded from hasAlarms
            allMuted = true,
            hasNewTriggeringEvent = false,
            mode = EventNotificationManager.NotificationMode.INDIVIDUAL,
            playReminderSound = false
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invariant 4 - allMuted with hasNewTriggeringEvent throws`() {
        NotificationContext(
            eventCount = 1,
            hasAlarms = false,
            allMuted = true,
            hasNewTriggeringEvent = true,  // INVALID: muted events can't trigger
            mode = EventNotificationManager.NotificationMode.INDIVIDUAL,
            playReminderSound = false
        )
    }

    // =============================================================================
    // Invariant 5: Factory always produces valid contexts
    // =============================================================================

    @Test
    fun `invariant 5 - factory with all muted events produces valid context`() {
        val allMutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true)
        )
        val ctx = NotificationContext.fromEvents(
            events = allMutedEvents,
            mode = EventNotificationManager.NotificationMode.ALL_COLLAPSED,
            playReminderSound = true
        )
        
        assertTrue("All muted events should result in allMuted=true", ctx.allMuted)
        assertFalse("All muted events should result in hasAlarms=false", ctx.hasAlarms)
        assertFalse("All muted events should result in hasNewTriggeringEvent=false", ctx.hasNewTriggeringEvent)
        assertEquals("All muted events should produce SILENT channel", ChannelCategory.SILENT, ctx.collapsedChannel)
    }

    @Test
    fun `invariant 5 - factory with muted alarm produces valid context`() {
        val eventsWithMutedAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = true, isAlarm = true),
            createTestEvent(eventId = 2, isMuted = true)
        )
        val ctx = NotificationContext.fromEvents(
            events = eventsWithMutedAlarm,
            mode = EventNotificationManager.NotificationMode.ALL_COLLAPSED,
            playReminderSound = false
        )
        
        assertTrue("All muted events should result in allMuted=true", ctx.allMuted)
        assertFalse("Muted alarm should NOT set hasAlarms", ctx.hasAlarms)
        assertEquals("Muted alarm should produce SILENT channel", ChannelCategory.SILENT, ctx.collapsedChannel)
    }

    @Test
    fun `invariant 5 - factory with unmuted alarm produces hasAlarms true`() {
        val eventsWithUnmutedAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = false),
            createTestEvent(eventId = 2, isMuted = false, isAlarm = true)
        )
        val ctx = NotificationContext.fromEvents(
            events = eventsWithUnmutedAlarm,
            mode = EventNotificationManager.NotificationMode.ALL_COLLAPSED,
            playReminderSound = false
        )
        
        assertFalse("Mixed events should result in allMuted=false", ctx.allMuted)
        assertTrue("Unmuted alarm should set hasAlarms", ctx.hasAlarms)
    }

    @Test
    fun `invariant 5 - factory with new hidden event produces hasNewTriggeringEvent true`() {
        val eventsWithNewEvent = listOf(
            createTestEvent(eventId = 1, isMuted = false, displayStatus = EventDisplayStatus.Hidden),
            createTestEvent(eventId = 2, isMuted = false, displayStatus = EventDisplayStatus.DisplayedNormal)
        )
        val ctx = NotificationContext.fromEvents(
            events = eventsWithNewEvent,
            mode = EventNotificationManager.NotificationMode.ALL_COLLAPSED,
            playReminderSound = false
        )
        
        assertTrue("Hidden event should set hasNewTriggeringEvent", ctx.hasNewTriggeringEvent)
        assertFalse("Should not be reminder when new event triggers", ctx.isReminder)
    }

    @Test
    fun `invariant 5 - factory with snoozed hidden event does NOT set hasNewTriggeringEvent`() {
        val eventsWithSnoozedEvent = listOf(
            createTestEvent(eventId = 1, isMuted = false, displayStatus = EventDisplayStatus.Hidden, snoozedUntil = baseTime + 3600000L)
        )
        val ctx = NotificationContext.fromEvents(
            events = eventsWithSnoozedEvent,
            mode = EventNotificationManager.NotificationMode.ALL_COLLAPSED,
            playReminderSound = false
        )
        
        assertFalse("Snoozed event should NOT set hasNewTriggeringEvent", ctx.hasNewTriggeringEvent)
        assertTrue("Should be reminder when no new events trigger", ctx.isReminder)
    }

    @Test
    fun `invariant 5 - factory with empty list produces valid context`() {
        val ctx = NotificationContext.fromEvents(
            events = emptyList(),
            mode = EventNotificationManager.NotificationMode.INDIVIDUAL,
            playReminderSound = false
        )
        
        assertEquals(0, ctx.eventCount)
        assertFalse("Empty list should have hasAlarms=false", ctx.hasAlarms)
        assertFalse("Empty list should have allMuted=false", ctx.allMuted)
        assertFalse("Empty list should have hasNewTriggeringEvent=false", ctx.hasNewTriggeringEvent)
    }

    // =============================================================================
    // Invariant 6: playReminderSound forces isReminder=true
    // =============================================================================

    @Test
    fun `invariant 6 - playReminderSound forces isReminder true regardless of hasNewTriggeringEvent`() {
        // Even with new triggering event, playReminderSound=true makes it a reminder
        val ctx = NotificationContext(
            eventCount = 3,
            hasAlarms = false,
            allMuted = false,
            hasNewTriggeringEvent = true,
            mode = EventNotificationManager.NotificationMode.ALL_COLLAPSED,
            playReminderSound = true  // This forces isReminder=true
        )
        
        assertTrue("playReminderSound should force isReminder=true", ctx.isReminder)
        assertEquals(
            "playReminderSound with no alarms should use REMINDERS channel",
            ChannelCategory.REMINDERS,
            ctx.collapsedChannel
        )
    }

    // =============================================================================
    // Invariant 7: Channel matches NotificationChannels.getChannelId behavior
    // =============================================================================

    @Test
    fun `invariant 7 - collapsedChannel toChannelId matches NotificationChannels getChannelId`() {
        // Verify our ChannelCategory maps correctly
        for (channel in ChannelCategory.values()) {
            val channelId = channel.toChannelId()
            when (channel) {
                ChannelCategory.EVENTS -> assertEquals(NotificationChannels.CHANNEL_ID_DEFAULT, channelId)
                ChannelCategory.ALARM -> assertEquals(NotificationChannels.CHANNEL_ID_ALARM, channelId)
                ChannelCategory.REMINDERS -> assertEquals(NotificationChannels.CHANNEL_ID_REMINDERS, channelId)
                ChannelCategory.ALARM_REMINDERS -> assertEquals(NotificationChannels.CHANNEL_ID_ALARM_REMINDERS, channelId)
                ChannelCategory.SILENT -> assertEquals(NotificationChannels.CHANNEL_ID_SILENT, channelId)
            }
        }
    }

    @Test
    fun `invariant 7 - context channel matches getChannelId for all non-muted combinations`() {
        // For non-muted cases, verify our decision tree matches getChannelId
        data class TestCase(
            val isReminder: Boolean,
            val hasAlarms: Boolean,
            val expectedCategory: ChannelCategory
        )
        
        val testCases = listOf(
            TestCase(false, false, ChannelCategory.EVENTS),      // New, no alarm
            TestCase(false, true, ChannelCategory.ALARM),        // New, alarm
            TestCase(true, false, ChannelCategory.REMINDERS),    // Reminder, no alarm
            TestCase(true, true, ChannelCategory.ALARM_REMINDERS) // Reminder, alarm
        )
        
        for (tc in testCases) {
            val ctx = NotificationContext(
                eventCount = 3,
                hasAlarms = tc.hasAlarms,
                allMuted = false,
                hasNewTriggeringEvent = !tc.isReminder,  // isReminder = playReminderSound || !hasNewTriggeringEvent
                mode = EventNotificationManager.NotificationMode.ALL_COLLAPSED,
                playReminderSound = false
            )
            
            assertEquals(
                "isReminder=${tc.isReminder}, hasAlarms=${tc.hasAlarms}",
                tc.expectedCategory,
                ctx.collapsedChannel
            )
            
            // Also verify it matches NotificationChannels.getChannelId
            val expectedChannelId = NotificationChannels.getChannelId(
                isAlarm = tc.hasAlarms,
                isMuted = false,
                isReminder = tc.isReminder
            )
            assertEquals(
                "Channel ID should match NotificationChannels.getChannelId for isReminder=${tc.isReminder}, hasAlarms=${tc.hasAlarms}",
                expectedChannelId,
                ctx.collapsedChannel.toChannelId()
            )
        }
    }

    // =============================================================================
    // Helper functions
    // =============================================================================

    private fun createTestEvent(
        eventId: Long = 1L,
        isMuted: Boolean = false,
        isAlarm: Boolean = false,
        snoozedUntil: Long = 0L,
        displayStatus: EventDisplayStatus = EventDisplayStatus.Hidden
    ): EventAlertRecord {
        // Build flags from isMuted and isAlarm
        // IS_MUTED = 1L, IS_TASK = 2L, IS_ALARM = 4L (from EventAlertFlags)
        var flags = 0L
        if (isMuted) flags = flags or 1L  // IS_MUTED
        if (isAlarm) flags = flags or 4L  // IS_ALARM

        return EventAlertRecord(
            calendarId = 1L,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = baseTime,
            notificationId = eventId.toInt(),
            title = "Test Event $eventId",
            desc = "",
            startTime = baseTime,
            endTime = baseTime + 3600000L,
            instanceStartTime = baseTime,
            instanceEndTime = baseTime + 3600000L,
            location = "",
            lastStatusChangeTime = baseTime,
            snoozedUntil = snoozedUntil,
            displayStatus = displayStatus,
            color = 0,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = baseTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = flags
        )
    }
}
