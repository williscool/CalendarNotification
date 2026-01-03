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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for NotificationChannels at SDK 34 (API 26+ required for notification channels).
 * These tests verify that notification channels and groups are created correctly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [34])
class NotificationChannelsRobolectricTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // === Channel creation tests ===

    @Test
    fun `createChannels creates all required channels`() {
        // Act
        NotificationChannels.createChannels(context)

        // Assert - verify all channels exist
        val channels = notificationManager.notificationChannels
        val channelIds = channels.map { it.id }

        assertTrue("Default channel should exist", 
            channelIds.contains(NotificationChannels.CHANNEL_ID_DEFAULT))
        assertTrue("Alarm channel should exist", 
            channelIds.contains(NotificationChannels.CHANNEL_ID_ALARM))
        assertTrue("Silent channel should exist", 
            channelIds.contains(NotificationChannels.CHANNEL_ID_SILENT))
        assertTrue("Reminders channel should exist", 
            channelIds.contains(NotificationChannels.CHANNEL_ID_REMINDERS))
        assertTrue("Alarm reminders channel should exist", 
            channelIds.contains(NotificationChannels.CHANNEL_ID_ALARM_REMINDERS))
    }

    @Test
    fun `createChannels is idempotent - can be called multiple times`() {
        // Act - call twice
        NotificationChannels.createChannels(context)
        NotificationChannels.createChannels(context)

        // Assert - should still have exactly 5 channels (no duplicates)
        val channels = notificationManager.notificationChannels
        assertEquals("Should have exactly 5 channels", 5, channels.size)
    }

    @Test
    fun `default channel has high importance`() {
        // Act
        NotificationChannels.createChannels(context)

        // Assert
        val defaultChannel = notificationManager.getNotificationChannel(NotificationChannels.CHANNEL_ID_DEFAULT)
        assertNotNull("Default channel should exist", defaultChannel)
        assertEquals("Default channel should have high importance",
            NotificationManager.IMPORTANCE_HIGH, defaultChannel?.importance)
    }

    @Test
    fun `silent channel has low importance`() {
        // Act
        NotificationChannels.createChannels(context)

        // Assert
        val silentChannel = notificationManager.getNotificationChannel(NotificationChannels.CHANNEL_ID_SILENT)
        assertNotNull("Silent channel should exist", silentChannel)
        assertEquals("Silent channel should have low importance",
            NotificationManager.IMPORTANCE_LOW, silentChannel?.importance)
    }

    // === Channel group tests ===

    @Test
    fun `createChannels creates all required channel groups`() {
        // Act
        NotificationChannels.createChannels(context)

        // Assert - verify all groups exist
        val groups = notificationManager.notificationChannelGroups
        val groupIds = groups.map { it.id }

        assertTrue("Main group should exist",
            groupIds.contains(NotificationChannels.GROUP_ID_MAIN))
        assertTrue("Alarm group should exist",
            groupIds.contains(NotificationChannels.GROUP_ID_ALARM))
        assertTrue("Silent group should exist",
            groupIds.contains(NotificationChannels.GROUP_ID_SILENT))
        assertEquals("Should have exactly 3 groups", 3, groups.size)
    }

    @Test
    fun `default channel is assigned to main group`() {
        // Act
        NotificationChannels.createChannels(context)

        // Assert
        val channel = notificationManager.getNotificationChannel(NotificationChannels.CHANNEL_ID_DEFAULT)
        assertEquals("Default channel should be in Main group",
            NotificationChannels.GROUP_ID_MAIN, channel?.group)
    }

    @Test
    fun `reminders channel is assigned to main group`() {
        // Act
        NotificationChannels.createChannels(context)

        // Assert
        val channel = notificationManager.getNotificationChannel(NotificationChannels.CHANNEL_ID_REMINDERS)
        assertEquals("Reminders channel should be in Main group",
            NotificationChannels.GROUP_ID_MAIN, channel?.group)
    }

    @Test
    fun `alarm channel is assigned to alarm group`() {
        // Act
        NotificationChannels.createChannels(context)

        // Assert
        val channel = notificationManager.getNotificationChannel(NotificationChannels.CHANNEL_ID_ALARM)
        assertEquals("Alarm channel should be in Alarm group",
            NotificationChannels.GROUP_ID_ALARM, channel?.group)
    }

    @Test
    fun `alarm reminders channel is assigned to alarm group`() {
        // Act
        NotificationChannels.createChannels(context)

        // Assert
        val channel = notificationManager.getNotificationChannel(NotificationChannels.CHANNEL_ID_ALARM_REMINDERS)
        assertEquals("Alarm reminders channel should be in Alarm group",
            NotificationChannels.GROUP_ID_ALARM, channel?.group)
    }

    @Test
    fun `silent channel is assigned to silent group`() {
        // Act
        NotificationChannels.createChannels(context)

        // Assert
        val channel = notificationManager.getNotificationChannel(NotificationChannels.CHANNEL_ID_SILENT)
        assertEquals("Silent channel should be in Silent group",
            NotificationChannels.GROUP_ID_SILENT, channel?.group)
    }

    // === getChannelId tests ===

    @Test
    fun `getChannelId returns correct channel for alarm events`() {
        val channelId = NotificationChannels.getChannelId(
            isAlarm = true,
            isMuted = false,
            isReminder = false
        )
        assertEquals(NotificationChannels.CHANNEL_ID_ALARM, channelId)
    }

    @Test
    fun `getChannelId returns correct channel for muted events`() {
        // Muted should take precedence over alarm
        val channelId = NotificationChannels.getChannelId(
            isAlarm = true,
            isMuted = true,
            isReminder = false
        )
        assertEquals(NotificationChannels.CHANNEL_ID_SILENT, channelId)
    }

    @Test
    fun `getChannelId returns correct channel for reminders`() {
        val channelId = NotificationChannels.getChannelId(
            isAlarm = false,
            isMuted = false,
            isReminder = true
        )
        assertEquals(NotificationChannels.CHANNEL_ID_REMINDERS, channelId)
    }

    @Test
    fun `getChannelId returns correct channel for alarm reminders`() {
        val channelId = NotificationChannels.getChannelId(
            isAlarm = true,
            isMuted = false,
            isReminder = true
        )
        assertEquals(NotificationChannels.CHANNEL_ID_ALARM_REMINDERS, channelId)
    }

    @Test
    fun `getChannelId returns default channel for regular events`() {
        val channelId = NotificationChannels.getChannelId(
            isAlarm = false,
            isMuted = false,
            isReminder = false
        )
        assertEquals(NotificationChannels.CHANNEL_ID_DEFAULT, channelId)
    }

    // === getChannelSoundUri tests ===

    @Test
    fun `getChannelSoundUri returns null for nonexistent channel`() {
        // Don't create channels
        val uri = NotificationChannels.getChannelSoundUri(context, "nonexistent_channel")
        assertNull("Should return null for nonexistent channel", uri)
    }

    @Test
    fun `getChannelSoundUri returns null for silent channel`() {
        // Act
        NotificationChannels.createChannels(context)
        val uri = NotificationChannels.getChannelSoundUri(context, NotificationChannels.CHANNEL_ID_SILENT)

        // Assert - silent channel has no sound
        assertNull("Silent channel should have no sound URI", uri)
    }

    @Test
    fun `getChannelSoundTitle returns Silent for channel with no sound`() {
        // Act
        NotificationChannels.createChannels(context)
        val title = NotificationChannels.getChannelSoundTitle(context, NotificationChannels.CHANNEL_ID_SILENT)

        // Assert
        assertEquals("Silent", title)
    }

    // === String resource sync tests ===
    // These tests ensure the Kotlin constants match the string resources used in XML preferences

    @Test
    fun `channel ID constants match string resources - default`() {
        val stringResource = context.getString(com.github.quarck.calnotify.R.string.channel_id_default)
        assertEquals("Channel ID constant must match string resource",
            NotificationChannels.CHANNEL_ID_DEFAULT, stringResource)
    }

    @Test
    fun `channel ID constants match string resources - alarm`() {
        val stringResource = context.getString(com.github.quarck.calnotify.R.string.channel_id_alarm)
        assertEquals("Channel ID constant must match string resource",
            NotificationChannels.CHANNEL_ID_ALARM, stringResource)
    }

    @Test
    fun `channel ID constants match string resources - silent`() {
        val stringResource = context.getString(com.github.quarck.calnotify.R.string.channel_id_silent)
        assertEquals("Channel ID constant must match string resource",
            NotificationChannels.CHANNEL_ID_SILENT, stringResource)
    }

    @Test
    fun `channel ID constants match string resources - reminders`() {
        val stringResource = context.getString(com.github.quarck.calnotify.R.string.channel_id_reminders)
        assertEquals("Channel ID constant must match string resource",
            NotificationChannels.CHANNEL_ID_REMINDERS, stringResource)
    }

    @Test
    fun `channel ID constants match string resources - alarm reminders`() {
        val stringResource = context.getString(com.github.quarck.calnotify.R.string.channel_id_alarm_reminders)
        assertEquals("Channel ID constant must match string resource",
            NotificationChannels.CHANNEL_ID_ALARM_REMINDERS, stringResource)
    }
}

