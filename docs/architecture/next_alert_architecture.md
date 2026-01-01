# Next Alert Indicator Architecture

This document describes the "next alert indicator" feature that shows users when they'll receive their next notification for an event.

## Overview

The next alert indicator displays in notifications to help users understand:
1. When their next calendar reminder will fire (GCal reminders)
2. When the app's re-remind feature will alert them (App alerts)

## Display Format

| Muted? | Alert Type | Display |
|--------|------------|---------|
| No | GCal reminder | `(📅 7m)` |
| No | App alert | `(🔔 7m)` |
| Yes | GCal reminder | `(🔇 📅 2d)` |
| Yes | App alert | `(🔇 🔔 7m)` |

**Time format**: Uses compact notation (7m, 2h 30m, 1d 5h) via `EventFormatter.formatDurationCompact()`.

**Note**: The format omits "in" to make it clear this is a snapshot of when the next alert was calculated, not a live countdown. The time shown refreshes whenever the notification is reposted (e.g., when app reminders fire).

## Settings

| Setting | Key | Default | Description |
|---------|-----|---------|-------------|
| `displayNextGCalReminder` | `pref_display_next_gcal_reminder` | **true** | Show next calendar-level reminder |
| `displayNextAppAlert` | `pref_display_next_app_alert` | **false** | Show next app reminder alert |

## Two Types of "Next Alert"

### 1. GCal Reminders (Calendar-level)

These are reminders set per-event in Google Calendar (e.g., "15 minutes before", "1 hour before").

**Source**: `EventRecord.reminders` → `getNextAlertTimeAfter(currentTime)`

```
Event starts at 3:00 PM
User set reminders: 15min before, 1hr before
Currently 2:00 PM

→ Next GCal reminder: 2:00 PM (1hr before) - fires NOW
→ After that: 2:45 PM (15min before)
```

### 2. App Alerts (CN+ re-remind feature)

The app's "reminder" feature that re-notifies every X minutes about active events.

**Source**: `ReminderState.nextFireExpectedAt`

**Conditions**:
- Only shown if `settings.remindersEnabled` is true
- Only for unmuted events (muted events don't trigger app alerts)

## Logic Flow

```
┌─────────────────────────────────────────────────────────────┐
│ calculateNextNotification(gcalTime, appTime, current, muted)│
├─────────────────────────────────────────────────────────────┤
│ 1. Filter out past times (time <= currentTime)              │
│ 2. If both null → return null (nothing to show)             │
│ 3. If only one → return that one                            │
│ 4. If both → return sooner one (GCal wins ties)             │
│ 5. Attach isMuted flag for display                          │
└─────────────────────────────────────────────────────────────┘
```

**Tie-breaker**: GCal always wins when times are equal. Rationale: calendar reminders are more "intentional" (user explicitly set them).

## Individual vs Collapsed Notifications

### Individual Notifications

Shows in the notification secondary text after the date/time:

```
"6:05 - 7:05 PM (📅 1h)"
"Location: Conference Room A"
```

**Implementation**: `EventFormatter.formatNextNotificationIndicator()`

### Collapsed Notifications

Shows in the notification **title** after the event count:

```
"5 calendar events (📅 30m)"
```

For collapsed, we find the **soonest** next alert across all events:

```kotlin
val soonestGCalTime = events
    .mapNotNull { getNextGCalReminder(it) }
    .minOrNull()
```

**Muted handling**: Shows 🔇 prefix only if ALL collapsed events are muted.

**Implementation**: `EventFormatter.formatNextNotificationIndicatorForCollapsed()`

## Key Components

### Data Classes

```kotlin
enum class NextNotificationType {
    GCAL_REMINDER,
    APP_ALERT
}

data class NextNotificationInfo(
    val type: NextNotificationType,
    val timeUntilMillis: Long,
    val isMuted: Boolean
)
```

### EventFormatter Methods

| Method | Purpose |
|--------|---------|
| `calculateNextNotificationInfo()` | Calculates next alert for a single event |
| `formatNextNotificationIndicator()` | Formats the indicator string for individual notifications |
| `formatNextNotificationIndicatorForCollapsed()` | Formats for collapsed notifications (finds soonest) |
| `calculateNextNotification()` | **Companion object** - Pure calculation function for testing |

### String Resources

```xml
<string name="next_gcal_indicator">📅 %s</string>
<string name="next_app_indicator">🔔 %s</string>
<string name="muted_prefix">🔇</string>
```

Note: The format omits "in" to make it clear this is a snapshot timestamp, not a live countdown.

## Testing

### TestActivity Buttons

1. **TEST NEXT ALERT (INDIVIDUAL)** - Single event with GCal reminders
2. **TEST NEXT ALERT (COLLAPSED)** - 5 events triggering collapse mode
3. **TEST NEXT ALERT (MUTED)** - Muted event showing 🔇 prefix

### Robolectric Tests

The companion object function `calculateNextNotification()` is pure and easily testable:

```kotlin
@Test
fun `calculateNextNotification - tie goes to gcal`() {
    val result = EventFormatter.calculateNextNotification(
        nextGCalTime = 5000L,
        nextAppTime = 5000L,  // Same time
        currentTime = 1000L,
        isMuted = false
    )
    assertEquals(NextNotificationType.GCAL_REMINDER, result!!.type)
}
```

## Related Documentation

- [Notification Architecture](notification_architecture.md) - Overall notification system
- [Calendar Monitoring](calendar_monitoring.md) - How calendar events are detected

