# Notification Architecture

## Overview

This document describes the notification system in Calendar Notifications Plus, including notification channels, posting modes, sound/vibration logic, and the muting system.

## Notification Channels

Android 8+ (API 26) requires notification channels. The app defines 5 channels in `NotificationChannels.kt`:

| Channel ID | Name | Importance | Sound/Vibrate | Purpose |
|------------|------|------------|---------------|---------|
| `calendar_events` | DEFAULT | HIGH | ✅ Yes | Regular calendar event notifications |
| `calendar_alarm` | ALARM | HIGH | ✅ Yes | Alarm-tagged events (high priority) |
| `calendar_silent` | SILENT | LOW | ❌ No | Muted notifications |
| `calendar_reminders` | REMINDERS | HIGH | ✅ Yes | Periodic reminder notifications |
| `calendar_alarm_reminders` | ALARM_REMINDERS | HIGH | ✅ Yes | Alarm event reminders |

### Channel Selection Logic

The `NotificationChannels.getChannelId()` function determines the appropriate channel:

```kotlin
fun getChannelId(isAlarm: Boolean, isMuted: Boolean, isReminder: Boolean): String {
    return when {
        isMuted -> CHANNEL_ID_SILENT              // Muted takes precedence
        isReminder && isAlarm -> CHANNEL_ID_ALARM_REMINDERS
        isReminder -> CHANNEL_ID_REMINDERS
        isAlarm -> CHANNEL_ID_ALARM
        else -> CHANNEL_ID_DEFAULT
    }
}
```

**Key principle**: Muted status always takes precedence. A muted alarm uses the silent channel, not the alarm channel.

## Notification Posting Modes

The `EventNotificationManager` supports multiple notification display modes based on settings and event count:

### 1. Individual Notifications (`postNotification`)

Each event gets its own notification. Used when:
- Event count ≤ `MAX_NUM_EVENTS_BEFORE_COLLAPSING_EVERYTHING` (user setting)
- "Collapse everything" is NOT enabled

**Channel selection**: Uses `NotificationChannels.getChannelId()` per-event with:
- `isAlarm = event.isAlarm || forceAlarmStream`
- `isMuted = event.isMuted`
- `isReminder` = whether this is a reminder re-post

### 2. Everything Collapsed (`postEverythingCollapsed`)

All events collapsed into a single notification. Used when:
- "Collapse everything" is enabled, OR
- Event count > `MAX_NUM_EVENTS_BEFORE_COLLAPSING_EVERYTHING`

**Channel selection**: Uses `computeCollapsedChannelId()`:
- `hasAlarms` = any event has `isAlarm && !isMuted`
- `allEventsMuted` = all events have `isMuted = true`
- `isReminder` = whether triggered by reminder alarm

### 3. Partial Collapse (`collapseDisplayedNotifications` + `postNumNotificationsCollapsed`)

Recent events shown individually, older events collapsed into "X more events" summary. Used when individual mode is active but some events are older.

**Channel selection**: Uses `computePartialCollapseChannelId()`:
- Returns `SILENT` if all collapsed events are muted
- Returns `DEFAULT` otherwise

### 4. Group Summary (`postGroupNotification`)

Android bundled notification summary. This is a passive grouping mechanism - individual notifications handle their own sound/channel.

**Channel**: Always `DEFAULT` with `setOnlyAlertOnce(true)` - never makes sound.

## Sound and Vibration Logic

### The `shouldPlayAndVibrate` Determination

For collapsed notifications, sound/vibrate is determined in two phases:

#### Phase 1: Event Loop

The loop checks each event's status:

```kotlin
for (event in events) {
    var shouldBeQuiet = false
    
    if (force) {
        shouldBeQuiet = true  // Force repost = silent
    } else if (event.displayStatus == DisplayedNormal) {
        shouldBeQuiet = true  // Already shown = silent
    } else if (isQuietPeriodActive) {
        // Quiet period logic (deprecated feature)
        shouldBeQuiet = ...
    }
    
    shouldBeQuiet = shouldBeQuiet || event.isMuted  // Muted = silent
    
    shouldPlayAndVibrate = shouldPlayAndVibrate || !shouldBeQuiet
}
```

#### Phase 2: Reminder Sound Override

After the loop, reminder notifications can override (but only for alarms):

```kotlin
// Production code calls this helper
shouldPlayAndVibrate = applyReminderSoundOverride(shouldPlayAndVibrate, playReminderSound, hasAlarms)
```

The `applyReminderSoundOverride` function:

```kotlin
fun applyReminderSoundOverride(
    currentShouldPlayAndVibrate: Boolean,
    playReminderSound: Boolean,
    hasAlarms: Boolean
): Boolean {
    return if (playReminderSound) {
        currentShouldPlayAndVibrate || hasAlarms  // Only alarms can override
    } else {
        currentShouldPlayAndVibrate
    }
}
```

**Bug fixed (2025)**: Previously this line was:
```kotlin
currentShouldPlayAndVibrate || !isQuietPeriodActive || hasAlarms
```
This incorrectly forced sound when NOT in quiet period, ignoring muted status.

### The `setOnlyAlertOnce` Flag

To prevent re-alerting on notification updates:

| Scenario | `setOnlyAlertOnce` | Rationale |
|----------|-------------------|-----------|
| `shouldPlayAndVibrate = false` | `true` | Don't alert for silent updates |
| `shouldPlayAndVibrate = true` | `false` | Allow alert for new/reminder |
| Force repost | `true` | Don't re-alert on force refresh |
| Expanding from collapsed | `true` | Don't re-alert when expanding |

## Muting System

### Per-Event Muting

Events can be individually muted via the notification action button. Muted status is stored in `EventAlertRecord.flags`:

```kotlin
val isMuted: Boolean
    get() = (flags and EventAlertFlags.IS_MUTED) != 0L
```

### Mute Toggle Flow

1. User taps mute button on notification
2. `NotificationActionMuteToggleService` receives intent
3. `ApplicationController.toggleMuteForEvent()` updates the event flags
4. `ApplicationController.onEventMuteToggled()` reposts notifications with updated channel

### Mute Behavior Summary

| Scenario | Channel | Sound |
|----------|---------|-------|
| Single unmuted event | DEFAULT/ALARM | ✅ |
| Single muted event | SILENT | ❌ |
| All events muted (collapsed) | SILENT | ❌ |
| Some events muted (collapsed) | DEFAULT/ALARM/REMINDERS | ✅ |
| Muted alarm event | SILENT | ❌ |

## Testable Helper Functions

The following companion object functions in `EventNotificationManager` are extracted for testability and called by production code:

| Function | Used By | Purpose |
|----------|---------|---------|
| `applyReminderSoundOverride()` | `postEverythingCollapsed` | Determines if reminder should force sound |
| `computeCollapsedChannelId()` | `postEverythingCollapsed` | Channel for fully collapsed notification |
| `computePartialCollapseChannelId()` | `postNumNotificationsCollapsed` | Channel for "X more events" summary |
| `computeShouldPlayAndVibrateForCollapsed()` | Tests | Simplified loop + override for testing |

### Testing the Notification Logic

Tests in `EventNotificationManagerRobolectricTest.kt` verify:

1. **Channel selection** - correct channel based on muted/alarm/reminder status
2. **Sound logic** - `shouldPlayAndVibrate` respects muted status
3. **Regression tests** - verify bugs don't reappear

Example test:

```kotlin
@Test
fun `applyReminderSoundOverride - muted events stay silent when playReminderSound is true`() {
    val loopResult = false  // all muted events
    val playReminderSound = true
    val hasAlarms = false
    
    val result = EventNotificationManager.applyReminderSoundOverride(
        currentShouldPlayAndVibrate = loopResult,
        playReminderSound = playReminderSound,
        hasAlarms = hasAlarms
    )
    
    assertFalse("All muted events should stay silent", result)
}
```

## File Organization

| File | Purpose |
|------|---------|
| `NotificationChannels.kt` | Channel definitions and creation |
| `EventNotificationManager.kt` | Main notification posting logic |
| `EventNotificationManagerInterface.kt` | Interface for DI/testing |
| `NotificationActionMuteToggleService.kt` | Mute toggle intent handler |
| `NotificationActionDismissService.kt` | Dismiss intent handler |
| `NotificationActionSnoozeService.kt` | Snooze intent handler |

## Notification IDs

| Constant | Value | Purpose |
|----------|-------|---------|
| `NOTIFICATION_ID_COLLAPSED` | Fixed ID | Collapsed summary notification |
| `event.notificationId` | Per-event | Individual event notifications |
| `NOTIFICATION_ID_DEBUG*` | Debug IDs | Debug notifications |

## Related Documentation

- [Calendar Monitoring](calendar_monitoring.md) - How events trigger notifications
- [Clock Implementation](clock_implementation.md) - Time handling in notifications

