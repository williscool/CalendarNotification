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
    // Invariant 8: Static helper computeHasAlarms
    // =============================================================================

    @Test
    fun `invariant 8 - computeHasAlarms excludes muted alarms`() {
        val mutedAlarm = createTestEvent(eventId = 1, isMuted = true, isAlarm = true)
        val unmutedAlarm = createTestEvent(eventId = 2, isMuted = false, isAlarm = true)
        val normalEvent = createTestEvent(eventId = 3, isMuted = false, isAlarm = false)
        
        assertFalse(
            "Muted alarm alone should not count",
            NotificationContext.computeHasAlarms(listOf(mutedAlarm))
        )
        assertTrue(
            "Unmuted alarm should count",
            NotificationContext.computeHasAlarms(listOf(unmutedAlarm))
        )
        assertFalse(
            "Normal event should not count as alarm",
            NotificationContext.computeHasAlarms(listOf(normalEvent))
        )
        assertTrue(
            "Mixed list with unmuted alarm should return true",
            NotificationContext.computeHasAlarms(listOf(mutedAlarm, unmutedAlarm, normalEvent))
        )
    }

    @Test
    fun `invariant 8 - computeHasAlarms excludes task alarms`() {
        // Create a task alarm (isAlarm=true, isTask=true)
        val taskAlarm = createTestEventWithTask(eventId = 1, isAlarm = true, isTask = true)
        
        assertFalse(
            "Task alarms should not count towards hasAlarms",
            NotificationContext.computeHasAlarms(listOf(taskAlarm))
        )
    }

    @Test
    fun `invariant 8 - computeHasAlarms empty list returns false`() {
        assertFalse(
            "Empty list should return false",
            NotificationContext.computeHasAlarms(emptyList())
        )
    }

    // =============================================================================
    // Invariant 9: Static helper computeAllMuted
    // =============================================================================

    @Test
    fun `invariant 9 - computeAllMuted returns true only when all muted`() {
        val muted1 = createTestEvent(eventId = 1, isMuted = true)
        val muted2 = createTestEvent(eventId = 2, isMuted = true)
        val unmuted = createTestEvent(eventId = 3, isMuted = false)
        
        assertTrue(
            "All muted events should return true",
            NotificationContext.computeAllMuted(listOf(muted1, muted2))
        )
        assertFalse(
            "Mixed list should return false",
            NotificationContext.computeAllMuted(listOf(muted1, unmuted))
        )
        assertFalse(
            "Single unmuted should return false",
            NotificationContext.computeAllMuted(listOf(unmuted))
        )
    }

    @Test
    fun `invariant 9 - computeAllMuted empty list returns false`() {
        assertFalse(
            "Empty list should return false (not 'all muted')",
            NotificationContext.computeAllMuted(emptyList())
        )
    }

    // =============================================================================
    // Invariant 10: Static helper computeHasNewTriggeringEvent
    // =============================================================================

    @Test
    fun `invariant 10 - computeHasNewTriggeringEvent requires Hidden + not snoozed + not muted`() {
        // Valid triggering event: Hidden, snoozedUntil=0, not muted
        val validTrigger = createTestEvent(
            eventId = 1,
            displayStatus = EventDisplayStatus.Hidden,
            snoozedUntil = 0L,
            isMuted = false
        )
        assertTrue(
            "Hidden, not snoozed, not muted should trigger",
            NotificationContext.computeHasNewTriggeringEvent(listOf(validTrigger))
        )
        
        // Muted hidden event should NOT trigger
        val mutedHidden = createTestEvent(
            eventId = 2,
            displayStatus = EventDisplayStatus.Hidden,
            snoozedUntil = 0L,
            isMuted = true
        )
        assertFalse(
            "Muted hidden event should not trigger",
            NotificationContext.computeHasNewTriggeringEvent(listOf(mutedHidden))
        )
        
        // Snoozed hidden event should NOT trigger
        val snoozedHidden = createTestEvent(
            eventId = 3,
            displayStatus = EventDisplayStatus.Hidden,
            snoozedUntil = baseTime + 3600000L,
            isMuted = false
        )
        assertFalse(
            "Snoozed hidden event should not trigger",
            NotificationContext.computeHasNewTriggeringEvent(listOf(snoozedHidden))
        )
        
        // Already displayed event should NOT trigger
        val displayed = createTestEvent(
            eventId = 4,
            displayStatus = EventDisplayStatus.DisplayedNormal,
            snoozedUntil = 0L,
            isMuted = false
        )
        assertFalse(
            "Already displayed event should not trigger",
            NotificationContext.computeHasNewTriggeringEvent(listOf(displayed))
        )
    }

    // =============================================================================
    // Invariant 11: computeShouldBeQuietForEvent
    // =============================================================================

    @Test
    fun `invariant 11 - computeShouldBeQuietForEvent force always quiet`() {
        val event = createTestEvent(isMuted = false)
        
        assertTrue(
            "Force should always be quiet",
            NotificationContext.shouldBeQuietForEvent(
                event = event,
                force = true,
                isAlreadyDisplayed = false,
                isQuietPeriodActive = false,
                isPrimaryEvent = false,
                quietHoursMutePrimary = false
            )
        )
    }

    @Test
    fun `invariant 11 - computeShouldBeQuietForEvent already displayed always quiet`() {
        val event = createTestEvent(isMuted = false)
        
        assertTrue(
            "Already displayed should be quiet",
            NotificationContext.shouldBeQuietForEvent(
                event = event,
                force = false,
                isAlreadyDisplayed = true,
                isQuietPeriodActive = false,
                isPrimaryEvent = false,
                quietHoursMutePrimary = false
            )
        )
    }

    @Test
    fun `invariant 11 - computeShouldBeQuietForEvent muted always quiet`() {
        val mutedEvent = createTestEvent(isMuted = true)
        
        assertTrue(
            "Muted event should always be quiet",
            NotificationContext.shouldBeQuietForEvent(
                event = mutedEvent,
                force = false,
                isAlreadyDisplayed = false,
                isQuietPeriodActive = false,
                isPrimaryEvent = false,
                quietHoursMutePrimary = false
            )
        )
    }

    @Test
    fun `invariant 11 - computeShouldBeQuietForEvent quiet period non-primary always quiet`() {
        val event = createTestEvent(isMuted = false)
        
        assertTrue(
            "Quiet period + non-primary should be quiet",
            NotificationContext.shouldBeQuietForEvent(
                event = event,
                force = false,
                isAlreadyDisplayed = false,
                isQuietPeriodActive = true,
                isPrimaryEvent = false,
                quietHoursMutePrimary = false
            )
        )
    }

    @Test
    fun `invariant 11 - computeShouldBeQuietForEvent quiet period primary respects setting`() {
        val event = createTestEvent(isMuted = false)
        
        // Primary event with quietHoursMutePrimary=true should be quiet
        assertTrue(
            "Quiet period + primary + mute setting should be quiet",
            NotificationContext.shouldBeQuietForEvent(
                event = event,
                force = false,
                isAlreadyDisplayed = false,
                isQuietPeriodActive = true,
                isPrimaryEvent = true,
                quietHoursMutePrimary = true
            )
        )
        
        // Primary event with quietHoursMutePrimary=false should NOT be quiet
        assertFalse(
            "Quiet period + primary + no mute setting should play sound",
            NotificationContext.shouldBeQuietForEvent(
                event = event,
                force = false,
                isAlreadyDisplayed = false,
                isQuietPeriodActive = true,
                isPrimaryEvent = true,
                quietHoursMutePrimary = false
            )
        )
    }

    @Test
    fun `invariant 11 - computeShouldBeQuietForEvent alarm overrides quiet period primary mute`() {
        val alarmEvent = createTestEvent(isMuted = false, isAlarm = true)
        
        // Alarm should play even when quietHoursMutePrimary=true
        assertFalse(
            "Alarm should override quiet period primary mute",
            NotificationContext.shouldBeQuietForEvent(
                event = alarmEvent,
                force = false,
                isAlreadyDisplayed = false,
                isQuietPeriodActive = true,
                isPrimaryEvent = true,
                quietHoursMutePrimary = true
            )
        )
    }

    @Test
    fun `invariant 11 - computeShouldBeQuietForEvent normal case plays sound`() {
        val event = createTestEvent(isMuted = false)
        
        assertFalse(
            "Normal event with no quiet conditions should play sound",
            NotificationContext.shouldBeQuietForEvent(
                event = event,
                force = false,
                isAlreadyDisplayed = false,
                isQuietPeriodActive = false,
                isPrimaryEvent = false,
                quietHoursMutePrimary = false
            )
        )
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
        return createTestEventWithTask(eventId, isMuted, isAlarm, isTask = false, snoozedUntil, displayStatus)
    }

    private fun createTestEventWithTask(
        eventId: Long = 1L,
        isMuted: Boolean = false,
        isAlarm: Boolean = false,
        isTask: Boolean = false,
        snoozedUntil: Long = 0L,
        displayStatus: EventDisplayStatus = EventDisplayStatus.Hidden
    ): EventAlertRecord {
        // Build flags from isMuted, isAlarm, and isTask
        // IS_MUTED = 1L, IS_TASK = 2L, IS_ALARM = 4L (from EventAlertFlags)
        var flags = 0L
        if (isMuted) flags = flags or 1L  // IS_MUTED
        if (isTask) flags = flags or 2L   // IS_TASK
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

    // =========================================================================
    // Invariant 12: isReminderEvent() helper
    // =========================================================================

    @Test
    fun `invariant 12 - new event (Hidden, not snoozed) is NOT a reminder`() {
        val newEvent = createTestEvent(
            displayStatus = EventDisplayStatus.Hidden,
            snoozedUntil = 0L
        )
        
        assertFalse(
            "New event (Hidden, snoozedUntil=0) should NOT be a reminder",
            NotificationContext.isReminderEvent(newEvent)
        )
    }

    @Test
    fun `invariant 12 - snoozed event (Hidden, snoozedUntil non-zero) IS a reminder`() {
        val snoozedEvent = createTestEvent(
            displayStatus = EventDisplayStatus.Hidden,
            snoozedUntil = baseTime + 60000L  // Was snoozed
        )
        
        assertTrue(
            "Snoozed event returning should be a reminder",
            NotificationContext.isReminderEvent(snoozedEvent)
        )
    }

    @Test
    fun `invariant 12 - collapsed event (DisplayedCollapsed) IS a reminder`() {
        val collapsedEvent = createTestEvent(
            displayStatus = EventDisplayStatus.DisplayedCollapsed,
            snoozedUntil = 0L
        )
        
        assertTrue(
            "Event expanding from collapsed should be a reminder",
            NotificationContext.isReminderEvent(collapsedEvent)
        )
    }

    @Test
    fun `invariant 12 - normal event (DisplayedNormal) IS a reminder`() {
        val normalEvent = createTestEvent(
            displayStatus = EventDisplayStatus.DisplayedNormal,
            snoozedUntil = 0L
        )
        
        assertTrue(
            "Previously displayed normal event should be a reminder",
            NotificationContext.isReminderEvent(normalEvent)
        )
    }

    @Test
    fun `invariant 12 - isReminderEvent is inverse of hasNewTriggeringEvent logic for unmuted`() {
        // For an unmuted event, isReminderEvent should be the opposite of what 
        // hasNewTriggeringEvent would return for a single-event list
        val newEvent = createTestEvent(
            displayStatus = EventDisplayStatus.Hidden,
            snoozedUntil = 0L,
            isMuted = false
        )
        
        val isReminder = NotificationContext.isReminderEvent(newEvent)
        val hasNewTriggering = NotificationContext.computeHasNewTriggeringEvent(listOf(newEvent))
        
        assertEquals(
            "For unmuted event, isReminderEvent should be inverse of hasNewTriggeringEvent",
            !isReminder,
            hasNewTriggering
        )
    }

    // =========================================================================
    // Invariant 13: partialCollapseChannelId() helper
    // =========================================================================

    @Test
    fun `invariant 13 - partialCollapseChannelId all muted returns SILENT`() {
        val mutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true)
        )
        
        assertEquals(
            "All muted should return SILENT channel",
            NotificationChannels.CHANNEL_ID_SILENT,
            NotificationContext.partialCollapseChannelId(mutedEvents)
        )
    }

    @Test
    fun `invariant 13 - partialCollapseChannelId any unmuted returns DEFAULT`() {
        val mixedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = false)
        )
        
        assertEquals(
            "Any unmuted should return DEFAULT channel",
            NotificationChannels.CHANNEL_ID_DEFAULT,
            NotificationContext.partialCollapseChannelId(mixedEvents)
        )
    }

    @Test
    fun `invariant 13 - partialCollapseChannelId empty list returns DEFAULT`() {
        // Empty list = computeAllMuted returns false = DEFAULT channel
        assertEquals(
            "Empty list should return DEFAULT channel",
            NotificationChannels.CHANNEL_ID_DEFAULT,
            NotificationContext.partialCollapseChannelId(emptyList())
        )
    }
}
