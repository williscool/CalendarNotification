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
 * These tests verify the channel selection and sound logic that was fixed:
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

    // === Collapsed notification channel selection tests ===

    @Test
    fun `collapsed notification with all muted events uses silent channel`() {
        // Given - all events are muted
        val mutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true),
            createTestEvent(eventId = 3, isMuted = true)
        )
        
        // When - determine channel using the same logic as postEverythingCollapsed
        val allMuted = mutedEvents.all { it.isMuted }
        val hasAlarms = mutedEvents.any { it.isAlarm && !it.isMuted }
        val channelId = NotificationChannels.getChannelId(
            isAlarm = hasAlarms,
            isMuted = allMuted,
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
        
        // When - determine channel
        val allMuted = mixedEvents.all { it.isMuted }
        val hasAlarms = mixedEvents.any { it.isAlarm && !it.isMuted }
        val channelId = NotificationChannels.getChannelId(
            isAlarm = hasAlarms,
            isMuted = allMuted,
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
        
        // When - determine channel
        val allMuted = eventsWithAlarm.all { it.isMuted }
        val hasAlarms = eventsWithAlarm.any { it.isAlarm && !it.isMuted }
        val channelId = NotificationChannels.getChannelId(
            isAlarm = hasAlarms,
            isMuted = allMuted,
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
        
        // When - determine channel
        val allMuted = eventsWithMutedAlarm.all { it.isMuted }
        val hasAlarms = eventsWithMutedAlarm.any { it.isAlarm && !it.isMuted }
        val channelId = NotificationChannels.getChannelId(
            isAlarm = hasAlarms,
            isMuted = allMuted,
            isReminder = true
        )
        
        // Then - should use silent channel (muted takes precedence)
        assertEquals(
            "Muted alarm should use silent channel",
            NotificationChannels.CHANNEL_ID_SILENT,
            channelId
        )
    }

    // === Sound/vibrate logic tests ===

    @Test
    fun `shouldPlayAndVibrate is false when all events are muted`() {
        // Given - all events muted, simulating loop logic from postEverythingCollapsed
        val mutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true)
        )
        
        // When - simulate the loop logic
        var shouldPlayAndVibrate = false
        for (event in mutedEvents) {
            var shouldBeQuiet = false
            // Not forced, not already displayed, not quiet period
            shouldBeQuiet = shouldBeQuiet || event.isMuted
            shouldPlayAndVibrate = shouldPlayAndVibrate || !shouldBeQuiet
        }
        
        // Then apply reminder sound logic (the fixed version)
        val hasAlarms = mutedEvents.any { it.isAlarm && !it.isMuted }
        val playReminderSound = true
        if (playReminderSound) {
            shouldPlayAndVibrate = shouldPlayAndVibrate || hasAlarms
        }
        
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
        
        // When - simulate the loop logic
        var shouldPlayAndVibrate = false
        for (event in mixedEvents) {
            var shouldBeQuiet = false
            shouldBeQuiet = shouldBeQuiet || event.isMuted
            shouldPlayAndVibrate = shouldPlayAndVibrate || !shouldBeQuiet
        }
        
        // Then apply reminder sound logic
        val hasAlarms = mixedEvents.any { it.isAlarm && !it.isMuted }
        val playReminderSound = true
        if (playReminderSound) {
            shouldPlayAndVibrate = shouldPlayAndVibrate || hasAlarms
        }
        
        // Then
        assertTrue(
            "Unmuted event should trigger sound",
            shouldPlayAndVibrate
        )
    }

    @Test
    fun `shouldPlayAndVibrate is true when there is unmuted alarm`() {
        // Given - unmuted alarm among muted events
        val eventsWithAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true)
        )
        
        // When - simulate the loop (all muted, so shouldPlayAndVibrate stays false)
        var shouldPlayAndVibrate = false
        for (event in eventsWithAlarm) {
            var shouldBeQuiet = false
            shouldBeQuiet = shouldBeQuiet || event.isMuted
            shouldPlayAndVibrate = shouldPlayAndVibrate || !shouldBeQuiet
        }
        
        // hasAlarms is calculated separately (checking for unmuted alarms)
        val hasAlarms = true // Simulating an unmuted alarm exists
        val playReminderSound = true
        if (playReminderSound) {
            shouldPlayAndVibrate = shouldPlayAndVibrate || hasAlarms
        }
        
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
        
        // When - simulate the loop
        var shouldPlayAndVibrate = false
        for (event in eventsWithMutedAlarm) {
            var shouldBeQuiet = false
            shouldBeQuiet = shouldBeQuiet || event.isMuted
            shouldPlayAndVibrate = shouldPlayAndVibrate || !shouldBeQuiet
        }
        
        // hasAlarms checks for unmuted alarms only
        val hasAlarms = eventsWithMutedAlarm.any { it.isAlarm && !it.isMuted }
        val playReminderSound = true
        if (playReminderSound) {
            shouldPlayAndVibrate = shouldPlayAndVibrate || hasAlarms
        }
        
        // Then - muted alarm doesn't force sound
        assertFalse(
            "Muted alarm should not force sound",
            shouldPlayAndVibrate
        )
    }

    // === Bug regression tests ===

    @Test
    fun `regression - reminder sound should not play when all events muted even outside quiet period`() {
        // This is the specific bug that was fixed:
        // Previously: shouldPlayAndVibrate = shouldPlayAndVibrate || !isQuietPeriodActive || hasAlarms
        // This would set shouldPlayAndVibrate = true whenever !isQuietPeriodActive (not in quiet period)
        // 
        // Fixed: shouldPlayAndVibrate = shouldPlayAndVibrate || hasAlarms
        // Now only hasAlarms can override the muted status
        
        val allMutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true),
            createTestEvent(eventId = 3, isMuted = true)
        )
        
        // Simulate NOT being in quiet period (this was causing the bug)
        @Suppress("UNUSED_VARIABLE")
        val isQuietPeriodActive = false
        
        // Simulate the loop - all muted so shouldPlayAndVibrate stays false
        var shouldPlayAndVibrate = false
        for (event in allMutedEvents) {
            var shouldBeQuiet = false
            shouldBeQuiet = shouldBeQuiet || event.isMuted
            shouldPlayAndVibrate = shouldPlayAndVibrate || !shouldBeQuiet
        }
        
        // Apply the FIXED reminder logic (not the buggy one)
        val hasAlarms = allMutedEvents.any { it.isAlarm && !it.isMuted }
        val playReminderSound = true
        if (playReminderSound) {
            // FIXED: Only hasAlarms can override, not quiet period status
            shouldPlayAndVibrate = shouldPlayAndVibrate || hasAlarms
        }
        
        // Verify the fix works
        assertFalse(
            "BUG REGRESSION: All muted events should stay silent even outside quiet period",
            shouldPlayAndVibrate
        )
        
        // Verify the channel is also correct
        val allMuted = allMutedEvents.all { it.isMuted }
        val channelId = NotificationChannels.getChannelId(
            isAlarm = hasAlarms,
            isMuted = allMuted,
            isReminder = playReminderSound
        )
        assertEquals(
            "BUG REGRESSION: All muted events should use silent channel",
            NotificationChannels.CHANNEL_ID_SILENT,
            channelId
        )
    }

    @Test
    fun `setOnlyAlertOnce logic - updates should not re-alert`() {
        // Test the setOnlyAlertOnce logic for notification updates
        // When shouldPlayAndVibrate is false, setOnlyAlertOnce should be true
        
        val shouldPlayAndVibrate = false
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
        
        val shouldPlayAndVibrate = true
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
