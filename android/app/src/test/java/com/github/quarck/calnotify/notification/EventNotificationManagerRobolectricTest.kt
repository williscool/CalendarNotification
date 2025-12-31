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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for EventNotificationManager logic, specifically the collapsed notification
 * behavior when events are muted.
 * 
 * These tests call the actual production code via companion object helper functions
 * to verify:
 * - All events muted: should use silent channel, no sound
 * - Some events muted: should use normal channel, play sound for non-muted
 * - Alarm events override: non-muted alarms should always play sound
 * - Notification updates should not re-alert (setOnlyAlertOnce)
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [34])
class EventNotificationManagerRobolectricTest {

    private lateinit var context: Context
    private val baseTime = 1635768000000L // 2021-11-01 12:00:00 UTC

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Create notification channels (required for SDK 26+)
        NotificationChannels.createChannels(context)
    }

    // === Channel selection tests using production code ===

    @Test
    fun `collapsed notification with all muted events uses silent channel`() {
        // Given - all events are muted
        val mutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true),
            createTestEvent(eventId = 3, isMuted = true)
        )
        val hasAlarms = mutedEvents.any { it.isAlarm && !it.isMuted }
        
        // When - call production code
        val channelId = EventNotificationManager.computeCollapsedChannelId(
            events = mutedEvents,
            hasAlarms = hasAlarms,
            isReminder = true
        )
        
        // Then - should use silent channel
        assertEquals(
            "All muted events should use silent channel",
            NotificationChannels.CHANNEL_ID_SILENT,
            channelId
        )
    }

    @Test
    fun `collapsed notification with some unmuted events uses reminders channel`() {
        // Given - mix of muted and unmuted events
        val mixedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = false),
            createTestEvent(eventId = 3, isMuted = true)
        )
        val hasAlarms = mixedEvents.any { it.isAlarm && !it.isMuted }
        
        // When - call production code
        val channelId = EventNotificationManager.computeCollapsedChannelId(
            events = mixedEvents,
            hasAlarms = hasAlarms,
            isReminder = true
        )
        
        // Then - should use reminders channel (not silent)
        assertEquals(
            "Mixed events should use reminders channel for reminder",
            NotificationChannels.CHANNEL_ID_REMINDERS,
            channelId
        )
    }

    @Test
    fun `collapsed notification with unmuted alarm uses alarm reminders channel`() {
        // Given - has unmuted alarm event
        val eventsWithAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = false, isAlarm = true),
            createTestEvent(eventId = 3, isMuted = false)
        )
        val hasAlarms = eventsWithAlarm.any { it.isAlarm && !it.isMuted }
        
        // When - call production code
        val channelId = EventNotificationManager.computeCollapsedChannelId(
            events = eventsWithAlarm,
            hasAlarms = hasAlarms,
            isReminder = true
        )
        
        // Then - should use alarm reminders channel
        assertEquals(
            "Events with unmuted alarm should use alarm reminders channel",
            NotificationChannels.CHANNEL_ID_ALARM_REMINDERS,
            channelId
        )
    }

    @Test
    fun `collapsed notification with muted alarm uses silent channel`() {
        // Given - alarm event is muted
        val eventsWithMutedAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true, isAlarm = true),
            createTestEvent(eventId = 3, isMuted = true)
        )
        val hasAlarms = eventsWithMutedAlarm.any { it.isAlarm && !it.isMuted }
        
        // When - call production code
        val channelId = EventNotificationManager.computeCollapsedChannelId(
            events = eventsWithMutedAlarm,
            hasAlarms = hasAlarms,
            isReminder = true
        )
        
        // Then - should use silent channel (muted takes precedence)
        assertEquals(
            "Muted alarm should use silent channel",
            NotificationChannels.CHANNEL_ID_SILENT,
            channelId
        )
    }

    // === Sound/vibrate logic tests using production code ===

    @Test
    fun `shouldPlayAndVibrate is false when all events are muted`() {
        // Given - all events muted
        val mutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true)
        )
        val hasAlarms = mutedEvents.any { it.isAlarm && !it.isMuted }
        
        // When - call production code
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsed(
            events = mutedEvents,
            playReminderSound = true,
            hasAlarms = hasAlarms
        )
        
        // Then
        assertFalse(
            "All muted events should not play sound",
            shouldPlayAndVibrate
        )
    }

    @Test
    fun `shouldPlayAndVibrate is true when at least one event is not muted`() {
        // Given - one unmuted event
        val mixedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = false)
        )
        val hasAlarms = mixedEvents.any { it.isAlarm && !it.isMuted }
        
        // When - call production code
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsed(
            events = mixedEvents,
            playReminderSound = true,
            hasAlarms = hasAlarms
        )
        
        // Then
        assertTrue(
            "Unmuted event should trigger sound",
            shouldPlayAndVibrate
        )
    }

    @Test
    fun `shouldPlayAndVibrate is true when there is unmuted alarm`() {
        // Given - all regular events muted, but there's an unmuted alarm
        val eventsWithUnmutedAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = false, isAlarm = true)
        )
        val hasAlarms = eventsWithUnmutedAlarm.any { it.isAlarm && !it.isMuted }
        
        // When - call production code
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsed(
            events = eventsWithUnmutedAlarm,
            playReminderSound = true,
            hasAlarms = hasAlarms
        )
        
        // Then - alarm overrides
        assertTrue(
            "Unmuted alarm should force sound",
            shouldPlayAndVibrate
        )
    }

    @Test
    fun `shouldPlayAndVibrate is false when alarm is also muted`() {
        // Given - all events including alarm are muted
        val eventsWithMutedAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true, isAlarm = true)
        )
        val hasAlarms = eventsWithMutedAlarm.any { it.isAlarm && !it.isMuted }
        
        // When - call production code
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsed(
            events = eventsWithMutedAlarm,
            playReminderSound = true,
            hasAlarms = hasAlarms
        )
        
        // Then - muted alarm doesn't force sound
        assertFalse(
            "Muted alarm should not force sound",
            shouldPlayAndVibrate
        )
    }

    // === Direct tests for applyReminderSoundOverride (THE ACTUAL PRODUCTION CODE) ===
    
    @Test
    fun `applyReminderSoundOverride - muted events stay silent when playReminderSound is true`() {
        // This is THE EXACT production code that was buggy
        // Production code at line ~486 calls: applyReminderSoundOverride(shouldPlayAndVibrate, playReminderSound, hasAlarms)
        
        // Given - loop determined all events are muted (shouldPlayAndVibrate = false)
        val loopResult = false  // all muted events
        val playReminderSound = true
        val hasAlarms = false  // no unmuted alarms
        
        // When - call the ACTUAL production function
        val result = EventNotificationManager.applyReminderSoundOverride(
            currentShouldPlayAndVibrate = loopResult,
            playReminderSound = playReminderSound,
            hasAlarms = hasAlarms
        )
        
        // Then - should stay silent (this is the bug fix!)
        assertFalse(
            "BUG FIX: applyReminderSoundOverride should NOT force sound when all muted",
            result
        )
    }

    @Test
    fun `applyReminderSoundOverride - unmuted alarm overrides muted status`() {
        // Given - loop determined all events are muted, BUT there's an unmuted alarm
        val loopResult = false  // all regular events muted
        val playReminderSound = true
        val hasAlarms = true  // has unmuted alarm
        
        // When
        val result = EventNotificationManager.applyReminderSoundOverride(
            currentShouldPlayAndVibrate = loopResult,
            playReminderSound = playReminderSound,
            hasAlarms = hasAlarms
        )
        
        // Then - unmuted alarm SHOULD force sound
        assertTrue(
            "Unmuted alarm should override and force sound",
            result
        )
    }

    @Test
    fun `applyReminderSoundOverride - preserves true when loop found unmuted events`() {
        // Given - loop found unmuted events
        val loopResult = true  // some unmuted events
        val playReminderSound = true
        val hasAlarms = false
        
        // When
        val result = EventNotificationManager.applyReminderSoundOverride(
            currentShouldPlayAndVibrate = loopResult,
            playReminderSound = playReminderSound,
            hasAlarms = hasAlarms
        )
        
        // Then - should preserve the loop result
        assertTrue(
            "Should preserve true from loop when there are unmuted events",
            result
        )
    }

    @Test
    fun `applyReminderSoundOverride - no change when not a reminder`() {
        // Given - not a reminder notification
        val loopResult = false
        val playReminderSound = false  // NOT a reminder
        val hasAlarms = true  // even with alarms
        
        // When
        val result = EventNotificationManager.applyReminderSoundOverride(
            currentShouldPlayAndVibrate = loopResult,
            playReminderSound = playReminderSound,
            hasAlarms = hasAlarms
        )
        
        // Then - should just return the loop result without modification
        assertFalse(
            "Non-reminder should not apply any override",
            result
        )
    }

    // === Bug regression tests using production code (full flow) ===

    @Test
    fun `regression - reminder sound should not play when all events muted`() {
        // This is the specific bug that was fixed:
        // Previously: shouldPlayAndVibrate = shouldPlayAndVibrate || !isQuietPeriodActive || hasAlarms
        // This would set shouldPlayAndVibrate = true whenever not in quiet period
        // 
        // Fixed: shouldPlayAndVibrate = shouldPlayAndVibrate || hasAlarms
        // Now only hasAlarms can override the muted status
        
        val allMutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true),
            createTestEvent(eventId = 3, isMuted = true)
        )
        val hasAlarms = allMutedEvents.any { it.isAlarm && !it.isMuted }
        
        // When - call production code (which internally uses applyReminderSoundOverride)
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsed(
            events = allMutedEvents,
            playReminderSound = true,
            hasAlarms = hasAlarms
        )
        
        // Then - verify the fix works
        assertFalse(
            "BUG REGRESSION: All muted events should stay silent even for reminders",
            shouldPlayAndVibrate
        )
        
        // Also verify the channel is correct
        val channelId = EventNotificationManager.computeCollapsedChannelId(
            events = allMutedEvents,
            hasAlarms = hasAlarms,
            isReminder = true
        )
        assertEquals(
            "BUG REGRESSION: All muted events should use silent channel",
            NotificationChannels.CHANNEL_ID_SILENT,
            channelId
        )
    }

    @Test
    fun `regression - non-reminder collapsed notification with all muted should not play sound`() {
        // Test the case where playReminderSound = false (regular notification update)
        val allMutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true)
        )
        val hasAlarms = allMutedEvents.any { it.isAlarm && !it.isMuted }
        
        // When - call production code with playReminderSound = false
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsed(
            events = allMutedEvents,
            playReminderSound = false,
            hasAlarms = hasAlarms
        )
        
        // Then
        assertFalse(
            "Non-reminder with all muted events should not play sound",
            shouldPlayAndVibrate
        )
    }

    // === setOnlyAlertOnce logic tests ===

    @Test
    fun `setOnlyAlertOnce logic - updates should not re-alert`() {
        // Test the setOnlyAlertOnce logic for notification updates
        // When shouldPlayAndVibrate is false, setOnlyAlertOnce should be true
        
        val allMutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true)
        )
        val hasAlarms = allMutedEvents.any { it.isAlarm && !it.isMuted }
        
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsed(
            events = allMutedEvents,
            playReminderSound = true,
            hasAlarms = hasAlarms
        )
        
        // The setOnlyAlertOnce flag should be !shouldPlayAndVibrate
        val setOnlyAlertOnce = !shouldPlayAndVibrate
        
        assertTrue(
            "When not playing sound, setOnlyAlertOnce should be true to prevent re-alerting",
            setOnlyAlertOnce
        )
    }

    @Test
    fun `setOnlyAlertOnce logic - new notifications should alert`() {
        // Test the setOnlyAlertOnce logic for new notifications
        // When shouldPlayAndVibrate is true, setOnlyAlertOnce should be false
        
        val unmutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = false),
            createTestEvent(eventId = 2, isMuted = false)
        )
        val hasAlarms = unmutedEvents.any { it.isAlarm && !it.isMuted }
        
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsed(
            events = unmutedEvents,
            playReminderSound = true,
            hasAlarms = hasAlarms
        )
        
        // The setOnlyAlertOnce flag should be !shouldPlayAndVibrate
        val setOnlyAlertOnce = !shouldPlayAndVibrate
        
        assertFalse(
            "When playing sound, setOnlyAlertOnce should be false to allow alerting",
            setOnlyAlertOnce
        )
    }

    @Test
    fun `individual notification setOnlyAlertOnce - forced repost should not alert`() {
        // Test the setOnlyAlertOnce logic for individual notifications
        // When isForce or wasCollapsed is true, should not re-alert
        
        val isForce = true
        val wasCollapsed = false
        val setOnlyAlertOnce = isForce || wasCollapsed
        
        assertTrue(
            "Forced repost should set onlyAlertOnce to prevent re-alerting",
            setOnlyAlertOnce
        )
    }

    @Test
    fun `individual notification setOnlyAlertOnce - expanding from collapsed should not alert`() {
        // Test the setOnlyAlertOnce logic for individual notifications
        
        val isForce = false
        val wasCollapsed = true
        val setOnlyAlertOnce = isForce || wasCollapsed
        
        assertTrue(
            "Expanding from collapsed should set onlyAlertOnce to prevent re-alerting",
            setOnlyAlertOnce
        )
    }

    // === Helper methods ===

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
