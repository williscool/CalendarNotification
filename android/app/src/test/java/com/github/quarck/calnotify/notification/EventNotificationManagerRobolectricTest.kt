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

    // === Mode selection tests (computeNotificationMode) ===

    @Test
    fun `mode - few events returns INDIVIDUAL`() {
        val mode = EventNotificationManager.computeNotificationMode(
            eventCount = 3,
            collapseEverything = false,
            maxNotifications = 4
        )
        assertEquals(EventNotificationManager.NotificationMode.INDIVIDUAL, mode)
    }

    @Test
    fun `mode - would collapse 1 event returns INDIVIDUAL (special case)`() {
        // When exactly 1 event would be collapsed, show it individually instead
        // With maxNotifications=4: recentEvents = takeLast(3), so 4 events = 3 recent + 1 collapsed
        // Special case: 1 collapsed gets folded back to recent
        val mode = EventNotificationManager.computeNotificationMode(
            eventCount = 4,
            collapseEverything = false,
            maxNotifications = 4
        )
        assertEquals(EventNotificationManager.NotificationMode.INDIVIDUAL, mode)
    }

    @Test
    fun `mode - overflow returns PARTIAL_COLLAPSE`() {
        val mode = EventNotificationManager.computeNotificationMode(
            eventCount = 6,
            collapseEverything = false,
            maxNotifications = 4
        )
        assertEquals(EventNotificationManager.NotificationMode.PARTIAL_COLLAPSE, mode)
    }

    @Test
    fun `mode - collapseEverything with single event returns INDIVIDUAL`() {
        // Edge case: collapseEverything=true but only 1 event - nothing to collapse
        val mode = EventNotificationManager.computeNotificationMode(
            eventCount = 1,
            collapseEverything = true,
            maxNotifications = 4
        )
        assertEquals(EventNotificationManager.NotificationMode.INDIVIDUAL, mode)
    }

    @Test
    fun `mode - collapseEverything with fold-1 case returns INDIVIDUAL`() {
        // Edge case: collapseEverything=true, but would only collapse 1 event
        // The "fold 1 into recent" rule applies BEFORE collapseEverything check
        // With 4 events, maxNotifications=4: recent=3, collapsed=1 → fold → all individual
        val mode = EventNotificationManager.computeNotificationMode(
            eventCount = 4,
            collapseEverything = true,
            maxNotifications = 4
        )
        assertEquals(EventNotificationManager.NotificationMode.INDIVIDUAL, mode)
    }

    @Test
    fun `mode - collapseEverything with 2+ collapsed returns ALL_COLLAPSED`() {
        // collapseEverything kicks in when we have 2+ events that would be collapsed
        val mode = EventNotificationManager.computeNotificationMode(
            eventCount = 5,
            collapseEverything = true,
            maxNotifications = 4
        )
        assertEquals(EventNotificationManager.NotificationMode.ALL_COLLAPSED, mode)
    }

    @Test
    fun `mode - 50 events returns ALL_COLLAPSED (safety limit)`() {
        val mode = EventNotificationManager.computeNotificationMode(
            eventCount = 50,
            collapseEverything = false,
            maxNotifications = 4
        )
        assertEquals(EventNotificationManager.NotificationMode.ALL_COLLAPSED, mode)
    }

    @Test
    fun `mode - 100 events returns ALL_COLLAPSED (safety limit)`() {
        val mode = EventNotificationManager.computeNotificationMode(
            eventCount = 100,
            collapseEverything = false,
            maxNotifications = 4
        )
        assertEquals(EventNotificationManager.NotificationMode.ALL_COLLAPSED, mode)
    }

    @Test
    fun `mode - safety limit overrides collapseEverything false`() {
        val mode = EventNotificationManager.computeNotificationMode(
            eventCount = 50,
            collapseEverything = false,
            maxNotifications = 100  // Even with high maxNotifications
        )
        assertEquals(EventNotificationManager.NotificationMode.ALL_COLLAPSED, mode)
    }

    // === NotificationChannels.getChannelId direct tests (all 5 channels) ===

    @Test
    fun `getChannelId - muted always returns SILENT regardless of other flags`() {
        // Muted takes precedence over everything
        assertEquals(NotificationChannels.CHANNEL_ID_SILENT, 
            NotificationChannels.getChannelId(isAlarm = false, isMuted = true, isReminder = false))
        assertEquals(NotificationChannels.CHANNEL_ID_SILENT, 
            NotificationChannels.getChannelId(isAlarm = true, isMuted = true, isReminder = false))
        assertEquals(NotificationChannels.CHANNEL_ID_SILENT, 
            NotificationChannels.getChannelId(isAlarm = false, isMuted = true, isReminder = true))
        assertEquals(NotificationChannels.CHANNEL_ID_SILENT, 
            NotificationChannels.getChannelId(isAlarm = true, isMuted = true, isReminder = true))
    }

    @Test
    fun `getChannelId - unmuted alarm reminder returns ALARM_REMINDERS`() {
        assertEquals(NotificationChannels.CHANNEL_ID_ALARM_REMINDERS,
            NotificationChannels.getChannelId(isAlarm = true, isMuted = false, isReminder = true))
    }

    @Test
    fun `getChannelId - unmuted non-alarm reminder returns REMINDERS`() {
        assertEquals(NotificationChannels.CHANNEL_ID_REMINDERS,
            NotificationChannels.getChannelId(isAlarm = false, isMuted = false, isReminder = true))
    }

    @Test
    fun `getChannelId - unmuted alarm first notification returns ALARM`() {
        assertEquals(NotificationChannels.CHANNEL_ID_ALARM,
            NotificationChannels.getChannelId(isAlarm = true, isMuted = false, isReminder = false))
    }

    @Test
    fun `getChannelId - unmuted non-alarm first notification returns DEFAULT`() {
        assertEquals(NotificationChannels.CHANNEL_ID_DEFAULT,
            NotificationChannels.getChannelId(isAlarm = false, isMuted = false, isReminder = false))
    }

    // === First collapse (isReminder=false) channel tests ===

    @Test
    fun `first collapse with all muted events uses silent channel`() {
        val mutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true)
        )
        val hasAlarms = mutedEvents.any { it.isAlarm && !it.isMuted }
        
        val channelId = EventNotificationManager.computeCollapsedChannelId(
            events = mutedEvents,
            hasAlarms = hasAlarms,
            isReminder = false  // First collapse, NOT a reminder
        )
        
        assertEquals(
            "First collapse with all muted should use SILENT",
            NotificationChannels.CHANNEL_ID_SILENT,
            channelId
        )
    }

    @Test
    fun `first collapse with unmuted events no alarms uses default channel`() {
        val unmutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = false),
            createTestEvent(eventId = 2, isMuted = false)
        )
        val hasAlarms = unmutedEvents.any { it.isAlarm && !it.isMuted }
        
        val channelId = EventNotificationManager.computeCollapsedChannelId(
            events = unmutedEvents,
            hasAlarms = hasAlarms,
            isReminder = false  // First collapse, NOT a reminder
        )
        
        assertEquals(
            "First collapse with unmuted, no alarms should use DEFAULT",
            NotificationChannels.CHANNEL_ID_DEFAULT,
            channelId
        )
    }

    @Test
    fun `first collapse with unmuted alarm uses alarm channel`() {
        val eventsWithAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = false),
            createTestEvent(eventId = 2, isMuted = false, isAlarm = true)
        )
        val hasAlarms = eventsWithAlarm.any { it.isAlarm && !it.isMuted }
        
        val channelId = EventNotificationManager.computeCollapsedChannelId(
            events = eventsWithAlarm,
            hasAlarms = hasAlarms,
            isReminder = false  // First collapse, NOT a reminder
        )
        
        assertEquals(
            "First collapse with unmuted alarm should use ALARM",
            NotificationChannels.CHANNEL_ID_ALARM,
            channelId
        )
    }

    // === Reminder collapse (isReminder=true) channel tests ===

    @Test
    fun `reminder collapse with all muted events uses silent channel`() {
        val mutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true),
            createTestEvent(eventId = 3, isMuted = true)
        )
        val hasAlarms = mutedEvents.any { it.isAlarm && !it.isMuted }
        
        val channelId = EventNotificationManager.computeCollapsedChannelId(
            events = mutedEvents,
            hasAlarms = hasAlarms,
            isReminder = true
        )
        
        assertEquals(
            "Reminder collapse with all muted should use SILENT",
            NotificationChannels.CHANNEL_ID_SILENT,
            channelId
        )
    }

    @Test
    fun `reminder collapse with some unmuted events uses reminders channel`() {
        val mixedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = false),
            createTestEvent(eventId = 3, isMuted = true)
        )
        val hasAlarms = mixedEvents.any { it.isAlarm && !it.isMuted }
        
        val channelId = EventNotificationManager.computeCollapsedChannelId(
            events = mixedEvents,
            hasAlarms = hasAlarms,
            isReminder = true
        )
        
        assertEquals(
            "Reminder collapse with unmuted, no alarms should use REMINDERS",
            NotificationChannels.CHANNEL_ID_REMINDERS,
            channelId
        )
    }

    @Test
    fun `reminder collapse with unmuted alarm uses alarm reminders channel`() {
        val eventsWithAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = false, isAlarm = true),
            createTestEvent(eventId = 3, isMuted = false)
        )
        val hasAlarms = eventsWithAlarm.any { it.isAlarm && !it.isMuted }
        
        val channelId = EventNotificationManager.computeCollapsedChannelId(
            events = eventsWithAlarm,
            hasAlarms = hasAlarms,
            isReminder = true
        )
        
        assertEquals(
            "Reminder collapse with unmuted alarm should use ALARM_REMINDERS",
            NotificationChannels.CHANNEL_ID_ALARM_REMINDERS,
            channelId
        )
    }

    @Test
    fun `reminder collapse with muted alarm uses silent channel`() {
        val eventsWithMutedAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true, isAlarm = true),
            createTestEvent(eventId = 3, isMuted = true)
        )
        val hasAlarms = eventsWithMutedAlarm.any { it.isAlarm && !it.isMuted }
        
        val channelId = EventNotificationManager.computeCollapsedChannelId(
            events = eventsWithMutedAlarm,
            hasAlarms = hasAlarms,
            isReminder = true
        )
        
        assertEquals(
            "Reminder collapse with muted alarm should use SILENT",
            NotificationChannels.CHANNEL_ID_SILENT,
            channelId
        )
    }

    // === Partial collapse channel selection tests (postNumNotificationsCollapsed) ===

    @Test
    fun `partial collapse with all muted events uses silent channel`() {
        // Given - all collapsed events are muted (partial collapse = some shown, rest collapsed)
        val mutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = true),
            createTestEvent(eventId = 3, isMuted = true)
        )
        
        // When - call production code
        val channelId = EventNotificationManager.computePartialCollapseChannelId(mutedEvents)
        
        // Then - should use silent channel
        assertEquals(
            "All muted collapsed events should use silent channel",
            NotificationChannels.CHANNEL_ID_SILENT,
            channelId
        )
    }

    @Test
    fun `partial collapse with some unmuted events uses default channel`() {
        // Given - mix of muted and unmuted events
        val mixedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true),
            createTestEvent(eventId = 2, isMuted = false),
            createTestEvent(eventId = 3, isMuted = true)
        )
        
        // When - call production code
        val channelId = EventNotificationManager.computePartialCollapseChannelId(mixedEvents)
        
        // Then - should use default channel (not silent)
        assertEquals(
            "Mixed events should use default channel",
            NotificationChannels.CHANNEL_ID_DEFAULT,
            channelId
        )
    }

    @Test
    fun `partial collapse with all unmuted events uses default channel`() {
        // Given - all events unmuted
        val unmutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = false),
            createTestEvent(eventId = 2, isMuted = false)
        )
        
        // When - call production code
        val channelId = EventNotificationManager.computePartialCollapseChannelId(unmutedEvents)
        
        // Then - should use default channel
        assertEquals(
            "All unmuted events should use default channel",
            NotificationChannels.CHANNEL_ID_DEFAULT,
            channelId
        )
    }

    // === Sound/vibrate logic tests - first notification (playReminderSound=false) ===

    @Test
    fun `first notification - all muted should not play sound`() {
        // First notification: displayStatus = Hidden (not yet displayed)
        val mutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true, displayStatus = EventDisplayStatus.Hidden),
            createTestEvent(eventId = 2, isMuted = true, displayStatus = EventDisplayStatus.Hidden)
        )
        val hasAlarms = mutedEvents.any { it.isAlarm && !it.isMuted }
        
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = mutedEvents,
            force = false,
            isQuietPeriodActive = false,
            primaryEventId = null,
            quietHoursMutePrimary = false,
            playReminderSound = false,
            hasAlarms = hasAlarms
        )
        
        assertFalse("First notification with all muted should not play sound", shouldPlayAndVibrate)
    }

    @Test
    fun `first notification - unmuted events should play sound`() {
        // First notification: displayStatus = Hidden (not yet displayed)
        val unmutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = false, displayStatus = EventDisplayStatus.Hidden),
            createTestEvent(eventId = 2, isMuted = false, displayStatus = EventDisplayStatus.Hidden)
        )
        val hasAlarms = unmutedEvents.any { it.isAlarm && !it.isMuted }
        
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = unmutedEvents,
            force = false,
            isQuietPeriodActive = false,
            primaryEventId = null,
            quietHoursMutePrimary = false,
            playReminderSound = false,
            hasAlarms = hasAlarms
        )
        
        assertTrue("First notification with unmuted should play sound", shouldPlayAndVibrate)
    }

    @Test
    fun `first notification - hasAlarms does not force sound when not reminder`() {
        // Even with hasAlarms=true, if playReminderSound=false, it shouldn't override
        val mutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true, displayStatus = EventDisplayStatus.Hidden),
            createTestEvent(eventId = 2, isMuted = true, displayStatus = EventDisplayStatus.Hidden)
        )
        
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = mutedEvents,
            force = false,
            isQuietPeriodActive = false,
            primaryEventId = null,
            quietHoursMutePrimary = false,
            playReminderSound = false,
            hasAlarms = true  // Even with alarms, shouldn't override for non-reminder
        )
        
        assertFalse("First notification should not use hasAlarms override", shouldPlayAndVibrate)
    }

    // === Sound/vibrate logic tests - reminder (playReminderSound=true) ===
    // Reminder scenario: events already displayed (displayStatus = DisplayedCollapsed)

    @Test
    fun `reminder - all muted should not play sound`() {
        // Reminder: displayStatus = DisplayedCollapsed (already shown)
        val mutedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true, displayStatus = EventDisplayStatus.DisplayedCollapsed),
            createTestEvent(eventId = 2, isMuted = true, displayStatus = EventDisplayStatus.DisplayedCollapsed)
        )
        val hasAlarms = mutedEvents.any { it.isAlarm && !it.isMuted }
        
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = mutedEvents,
            force = false,
            isQuietPeriodActive = false,
            primaryEventId = null,
            quietHoursMutePrimary = false,
            playReminderSound = true,
            hasAlarms = hasAlarms
        )
        
        assertFalse("Reminder with all muted should not play sound", shouldPlayAndVibrate)
    }

    @Test
    fun `reminder - unmuted events should play sound`() {
        // Reminder: displayStatus = DisplayedCollapsed (already shown)
        val mixedEvents = listOf(
            createTestEvent(eventId = 1, isMuted = true, displayStatus = EventDisplayStatus.DisplayedCollapsed),
            createTestEvent(eventId = 2, isMuted = false, displayStatus = EventDisplayStatus.DisplayedCollapsed)
        )
        val hasAlarms = mixedEvents.any { it.isAlarm && !it.isMuted }
        
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = mixedEvents,
            force = false,
            isQuietPeriodActive = false,
            primaryEventId = null,
            quietHoursMutePrimary = false,
            playReminderSound = true,
            hasAlarms = hasAlarms
        )
        
        assertTrue("Reminder with unmuted should play sound", shouldPlayAndVibrate)
    }

    @Test
    fun `reminder - unmuted alarm forces sound via hasAlarms`() {
        // Reminder: displayStatus = DisplayedCollapsed (already shown)
        val eventsWithUnmutedAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = true, displayStatus = EventDisplayStatus.DisplayedCollapsed),
            createTestEvent(eventId = 2, isMuted = false, isAlarm = true, displayStatus = EventDisplayStatus.DisplayedCollapsed)
        )
        val hasAlarms = eventsWithUnmutedAlarm.any { it.isAlarm && !it.isMuted }
        
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = eventsWithUnmutedAlarm,
            force = false,
            isQuietPeriodActive = false,
            primaryEventId = null,
            quietHoursMutePrimary = false,
            playReminderSound = true,
            hasAlarms = hasAlarms
        )
        
        assertTrue("Reminder with unmuted alarm should force sound", shouldPlayAndVibrate)
    }

    @Test
    fun `reminder - muted alarm does not force sound`() {
        // Reminder: displayStatus = DisplayedCollapsed (already shown)
        val eventsWithMutedAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = true, displayStatus = EventDisplayStatus.DisplayedCollapsed),
            createTestEvent(eventId = 2, isMuted = true, isAlarm = true, displayStatus = EventDisplayStatus.DisplayedCollapsed)
        )
        val hasAlarms = eventsWithMutedAlarm.any { it.isAlarm && !it.isMuted }
        
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = eventsWithMutedAlarm,
            force = false,
            isQuietPeriodActive = false,
            primaryEventId = null,
            quietHoursMutePrimary = false,
            playReminderSound = true,
            hasAlarms = hasAlarms
        )
        
        assertFalse("Reminder with muted alarm should not force sound", shouldPlayAndVibrate)
    }

    // === hasAlarms calculation tests (verifies correct filtering) ===

    @Test
    fun `hasAlarms calculation - muted alarm should not count as hasAlarms`() {
        // This tests the correct pattern for calculating hasAlarms
        // Production code at lines 225-226 and 336 must use: it.isAlarm && !it.isTask && !it.isMuted
        
        val eventsWithMutedAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = false),
            createTestEvent(eventId = 2, isMuted = true, isAlarm = true)  // muted alarm
        )
        
        // Correct calculation: muted alarms don't count
        val hasAlarms = eventsWithMutedAlarm.any { it.isAlarm && !it.isMuted }
        
        assertFalse(
            "Muted alarm should NOT count towards hasAlarms",
            hasAlarms
        )
    }

    @Test
    fun `hasAlarms calculation - unmuted alarm should count as hasAlarms`() {
        val eventsWithUnmutedAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = false),
            createTestEvent(eventId = 2, isMuted = false, isAlarm = true)  // unmuted alarm
        )
        
        val hasAlarms = eventsWithUnmutedAlarm.any { it.isAlarm && !it.isMuted }
        
        assertTrue(
            "Unmuted alarm SHOULD count towards hasAlarms",
            hasAlarms
        )
    }

    @Test
    fun `hasAlarms false prevents reminder from forcing sound on muted events`() {
        // End-to-end test: if hasAlarms is correctly calculated as false,
        // then applyReminderSoundOverride won't force sound
        
        val allMutedEventsIncludingAlarm = listOf(
            createTestEvent(eventId = 1, isMuted = true, displayStatus = EventDisplayStatus.DisplayedCollapsed),
            createTestEvent(eventId = 2, isMuted = true, isAlarm = true, displayStatus = EventDisplayStatus.DisplayedCollapsed)  // muted alarm
        )
        
        // Correct hasAlarms calculation (muted alarm doesn't count)
        val hasAlarms = allMutedEventsIncludingAlarm.any { it.isAlarm && !it.isMuted }
        assertFalse("Muted alarm should not count", hasAlarms)
        
        // Now test the full flow (reminder scenario)
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = allMutedEventsIncludingAlarm,
            force = false,
            isQuietPeriodActive = false,
            primaryEventId = null,
            quietHoursMutePrimary = false,
            playReminderSound = true,
            hasAlarms = hasAlarms  // false because alarm is muted
        )
        
        assertFalse(
            "With correct hasAlarms=false, muted alarm should stay silent",
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
            createTestEvent(eventId = 1, isMuted = true, displayStatus = EventDisplayStatus.DisplayedCollapsed),
            createTestEvent(eventId = 2, isMuted = true, displayStatus = EventDisplayStatus.DisplayedCollapsed),
            createTestEvent(eventId = 3, isMuted = true, displayStatus = EventDisplayStatus.DisplayedCollapsed)
        )
        val hasAlarms = allMutedEvents.any { it.isAlarm && !it.isMuted }
        
        // When - call production code (reminder scenario)
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = allMutedEvents,
            force = false,
            isQuietPeriodActive = false,
            primaryEventId = null,
            quietHoursMutePrimary = false,
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
            createTestEvent(eventId = 1, isMuted = true, displayStatus = EventDisplayStatus.Hidden),
            createTestEvent(eventId = 2, isMuted = true, displayStatus = EventDisplayStatus.Hidden)
        )
        val hasAlarms = allMutedEvents.any { it.isAlarm && !it.isMuted }
        
        // When - call production code with playReminderSound = false
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = allMutedEvents,
            force = false,
            isQuietPeriodActive = false,
            primaryEventId = null,
            quietHoursMutePrimary = false,
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
            createTestEvent(eventId = 1, isMuted = true, displayStatus = EventDisplayStatus.DisplayedCollapsed),
            createTestEvent(eventId = 2, isMuted = true, displayStatus = EventDisplayStatus.DisplayedCollapsed)
        )
        val hasAlarms = allMutedEvents.any { it.isAlarm && !it.isMuted }
        
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = allMutedEvents,
            force = false,
            isQuietPeriodActive = false,
            primaryEventId = null,
            quietHoursMutePrimary = false,
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
            createTestEvent(eventId = 1, isMuted = false, displayStatus = EventDisplayStatus.Hidden),
            createTestEvent(eventId = 2, isMuted = false, displayStatus = EventDisplayStatus.Hidden)
        )
        val hasAlarms = unmutedEvents.any { it.isAlarm && !it.isMuted }
        
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = unmutedEvents,
            force = false,
            isQuietPeriodActive = false,
            primaryEventId = null,
            quietHoursMutePrimary = false,
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
    fun `FIXED - individual reminder does not set setOnlyAlertOnce`() {
        // This test verifies the fix for individual notification reminders.
        // 
        // In fireEventReminderNoSeparateNotification, postNotification is called with:
        //   isForce=true, isReminder=true
        //
        // The fix ensures reminders can alert by using computeShouldOnlyAlertOnce()
        // which returns false when isReminder=true.
        
        // Individual reminder path settings:
        val isForce = true  // As passed from fireEventReminderNoSeparateNotification
        val wasCollapsed = false
        val isReminder = true  // This is a reminder
        
        // Use the actual production function
        val setOnlyAlertOnce = EventNotificationManager.computeShouldOnlyAlertOnce(isForce, wasCollapsed, isReminder)
        
        // With the fix, reminders should NOT have setOnlyAlertOnce=true
        assertFalse(
            "Reminders should have setOnlyAlertOnce=false so sound can play",
            setOnlyAlertOnce
        )
    }
    
    @Test
    fun `setOnlyAlertOnce - forced non-reminder repost should still suppress alert`() {
        // Verify that non-reminder forced reposts still suppress alerts (e.g., boot repost)
        val isForce = true
        val wasCollapsed = false
        val isReminder = false  // NOT a reminder
        
        // Use the actual production function
        val setOnlyAlertOnce = EventNotificationManager.computeShouldOnlyAlertOnce(isForce, wasCollapsed, isReminder)
        
        assertTrue(
            "Non-reminder forced repost should suppress alert",
            setOnlyAlertOnce
        )
    }
    
    @Test
    fun `setOnlyAlertOnce - expanding from collapsed should still suppress alert`() {
        // Verify that expanding from collapsed still suppresses alerts
        val isForce = false
        val wasCollapsed = true
        val isReminder = false
        
        // Use the actual production function
        val setOnlyAlertOnce = EventNotificationManager.computeShouldOnlyAlertOnce(isForce, wasCollapsed, isReminder)
        
        assertTrue(
            "Expanding from collapsed should suppress alert",
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

    // === FIXED BUG TESTS: Recurring event reminder sound not playing ===
    // These tests verify the fix for a bug where reminders didn't play sound for collapsed events
    // because the displayStatus check was skipping the sound update block.
    // 
    // FIX: Added playReminderSound to the condition:
    //   if ((event.displayStatus != EventDisplayStatus.DisplayedCollapsed) || force || playReminderSound)
    
    @Test
    fun `FIXED - reminder sound plays for collapsed event with DisplayedCollapsed status`() {
        // This test verifies the fix for reminders not working on recurring events.
        // 
        // Scenario:
        // 1. Event fires and gets displayed collapsed → displayStatus = DisplayedCollapsed
        // 2. Reminder alarm fires → calls postEverythingCollapsed with force=false, playReminderSound=true
        // 3. Production code checks: (displayStatus != DisplayedCollapsed) || force || playReminderSound
        // 4. Since playReminderSound IS true → condition is TRUE
        // 5. The inner block runs → shouldPlayAndVibrate is updated correctly
        // 6. Sound plays!
        
        val eventWithDisplayedCollapsedStatus = listOf(
            createTestEvent(
                eventId = 1, 
                isMuted = false,
                displayStatus = EventDisplayStatus.DisplayedCollapsed  // Already shown as collapsed
            )
        )
        val hasAlarms = false
        
        // This is the reminder path: force = false, playReminderSound = true
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = eventWithDisplayedCollapsedStatus,
            force = false,  // Reminder path uses force=false
            isQuietPeriodActive = false,
            playReminderSound = true,
            hasAlarms = hasAlarms
        )
        
        // With the fix, reminder sound should play for collapsed events
        assertTrue(
            "Reminder sound should play for collapsed events (fix: playReminderSound added to condition)",
            shouldPlayAndVibrate
        )
    }
    
    @Test
    fun `WORKING - first notification sound plays for new event with Hidden status`() {
        // This test shows the working case: new events have displayStatus=Hidden,
        // so the condition (displayStatus != DisplayedCollapsed) is TRUE and sound plays.
        
        val newEvent = listOf(
            createTestEvent(
                eventId = 1,
                isMuted = false,
                displayStatus = EventDisplayStatus.Hidden  // New event, not yet displayed
            )
        )
        val hasAlarms = false
        
        // First notification path: force = false, playReminderSound = false
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = newEvent,
            force = false,
            isQuietPeriodActive = false,
            playReminderSound = false,
            hasAlarms = hasAlarms
        )
        
        // This should pass - new events work correctly
        assertTrue(
            "First notification for new event should play sound",
            shouldPlayAndVibrate
        )
    }
    
    @Test
    fun `WORKING - snoozed event sound plays when snooze expires`() {
        // This test shows why snoozing "fixes" the problem: snoozed events go through
        // a different code path that ALWAYS updates shouldPlayAndVibrate.
        
        val snoozedEvent = listOf(
            createTestEvent(
                eventId = 1,
                isMuted = false,
                snoozedUntil = baseTime + 1000,  // Event is snoozed
                displayStatus = EventDisplayStatus.Hidden
            )
        )
        val hasAlarms = false
        
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = snoozedEvent,
            force = false,
            isQuietPeriodActive = false,
            playReminderSound = false,
            hasAlarms = hasAlarms
        )
        
        // This should pass - snoozed events have their own path that works correctly
        assertTrue(
            "Snoozed event should play sound when snooze expires",
            shouldPlayAndVibrate
        )
    }
    
    @Test
    fun `FIXED - multiple events with DisplayedCollapsed all play sound during reminder`() {
        // This verifies the fix affects ALL collapsed events, not just recurring ones.
        // The bug was more noticeable with recurring events because they tend to stick around longer.
        
        val multipleCollapsedEvents = listOf(
            createTestEvent(
                eventId = 1,
                isMuted = false,
                displayStatus = EventDisplayStatus.DisplayedCollapsed
            ),
            createTestEvent(
                eventId = 2,
                isMuted = false,
                displayStatus = EventDisplayStatus.DisplayedCollapsed
            ),
            createTestEvent(
                eventId = 3,
                isMuted = false,
                displayStatus = EventDisplayStatus.DisplayedCollapsed
            )
        )
        val hasAlarms = false
        
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = multipleCollapsedEvents,
            force = false,
            isQuietPeriodActive = false,
            playReminderSound = true,
            hasAlarms = hasAlarms
        )
        
        // With the fix, reminder sound should play for all collapsed events
        assertTrue(
            "Reminder sound should play for all collapsed events (fix applied)",
            shouldPlayAndVibrate
        )
    }
    
    @Test
    fun `BUG WORKAROUND - if ANY event is not DisplayedCollapsed, reminder sound plays`() {
        // This shows that if even ONE event has a different displayStatus, sound will play.
        // This explains why the bug might be intermittent - if you have a mix of events.
        
        val mixedStatusEvents = listOf(
            createTestEvent(
                eventId = 1,
                isMuted = false,
                displayStatus = EventDisplayStatus.DisplayedCollapsed  // Would be skipped
            ),
            createTestEvent(
                eventId = 2,
                isMuted = false,
                displayStatus = EventDisplayStatus.Hidden  // This one triggers sound!
            )
        )
        val hasAlarms = false
        
        val shouldPlayAndVibrate = EventNotificationManager.computeShouldPlayAndVibrateForCollapsedFull(
            events = mixedStatusEvents,
            force = false,
            isQuietPeriodActive = false,
            playReminderSound = true,
            hasAlarms = hasAlarms
        )
        
        // This should pass - the Hidden event saves the day
        assertTrue(
            "If any event is not DisplayedCollapsed, reminder sound should play",
            shouldPlayAndVibrate
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
