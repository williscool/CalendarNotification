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
import android.os.Build
import com.github.quarck.calnotify.R

/**
 * Centralized notification channel management for Android 8+ (API 26+).
 * Channels must be created before posting notifications on Android O and above.
 */
object NotificationChannels {
    
    // Channel IDs - these are user-visible in system settings
    const val CHANNEL_ID_DEFAULT = "calendar_events"
    const val CHANNEL_ID_ALARM = "calendar_alarm"
    const val CHANNEL_ID_SILENT = "calendar_silent"
    const val CHANNEL_ID_REMINDERS = "calendar_reminders"
    const val CHANNEL_ID_ALARM_REMINDERS = "calendar_alarm_reminders"
    
    /**
     * Creates all notification channels. Safe to call multiple times -
     * channels are only created once and subsequent calls update them.
     * No-op on Android < 8.0 (API 26).
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return // Channels not supported before Android 8
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Default channel for calendar events
        val defaultChannel = NotificationChannel(
            CHANNEL_ID_DEFAULT,
            context.getString(R.string.notification_channel_default),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_default_desc)
            enableVibration(true)
            enableLights(true)
        }
        
        // Alarm channel - highest priority for alarm-tagged events
        val alarmChannel = NotificationChannel(
            CHANNEL_ID_ALARM,
            context.getString(R.string.notification_channel_alarm),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_alarm_desc)
            enableVibration(true)
            enableLights(true)
        }
        
        // Silent channel for muted notifications
        val silentChannel = NotificationChannel(
            CHANNEL_ID_SILENT,
            context.getString(R.string.notification_channel_silent),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_silent_desc)
            enableVibration(false)
            setSound(null, null)
        }
        
        // Reminders channel
        val remindersChannel = NotificationChannel(
            CHANNEL_ID_REMINDERS,
            context.getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_reminders_desc)
            enableVibration(true)
            enableLights(true)
        }
        
        // Alarm reminders channel
        val alarmRemindersChannel = NotificationChannel(
            CHANNEL_ID_ALARM_REMINDERS,
            context.getString(R.string.notification_channel_alarm_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_alarm_reminders_desc)
            enableVibration(true)
            enableLights(true)
        }
        
        notificationManager.createNotificationChannels(listOf(
            defaultChannel,
            alarmChannel,
            silentChannel,
            remindersChannel,
            alarmRemindersChannel
        ))
    }
    
    /**
     * Returns the appropriate channel ID based on notification type.
     */
    fun getChannelId(isAlarm: Boolean, isMuted: Boolean, isReminder: Boolean): String {
        return when {
            isMuted -> CHANNEL_ID_SILENT
            isReminder && isAlarm -> CHANNEL_ID_ALARM_REMINDERS
            isReminder -> CHANNEL_ID_REMINDERS
            isAlarm -> CHANNEL_ID_ALARM
            else -> CHANNEL_ID_DEFAULT
        }
    }
}

