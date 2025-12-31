# Domain Model

## Overview

This document describes the core domain concepts in Calendar Notifications Plus and how they relate to each other.

## Domain Concepts

### Calendar Events (External)

**Source:** Android Calendar Provider (not stored by us)

These are the actual calendar events on the user's device. We query them but don't own them.

```
┌─────────────────────────────────────────┐
│           Calendar Provider              │
│  (Google Calendar, Exchange, etc.)       │
│                                          │
│  - eventId, title, description           │
│  - startTime, endTime                    │
│  - location, attendees                   │
│  - reminder settings (e.g., 15min before)│
└─────────────────────────────────────────┘
```

---

### Event Alerts (EventsStorage)

**What it is:** An active notification that the app is managing.

**Lifecycle:** Created when an alert fires → Displayed to user → Snoozed or Dismissed

This is a **composite entity** combining:
- **Event data** (copied from calendar): title, location, times, description
- **Alert state** (managed by us): alertTime, snoozedUntil, displayStatus, notificationId

```kotlin
// The "alert" represents: "User should be notified about this event"
data class EventAlertRecord(
    // Event identity
    val eventId: Long,
    val instanceStartTime: Long,  // For recurring events, identifies which instance
    
    // Event data (snapshot from calendar)
    val title: String,
    val description: String,
    val location: String,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean,
    val isRepeating: Boolean,
    val color: Int,
    
    // Alert state (our data)
    val alertTime: Long,          // When the alert was triggered
    val notificationId: Int,      // Android notification ID
    val snoozedUntil: Long,       // 0 if not snoozed
    val displayStatus: EventDisplayStatus,
    val lastStatusChangeTime: Long,
    // ...
)
```

**Why "Alert" in the name?** Because this isn't just event data - it's specifically about the *notification alert* for that event. The same calendar event could generate multiple alerts (e.g., 1 hour before, 15 min before).

**Storage:** `EventsStorage` (Room: `eventsV9` table)

---

### Dismissed Event Alerts (DismissedEventsStorage)

**What it is:** A historical record that the user dismissed an alert.

**Purpose:**
- Undo support (restore accidentally dismissed alerts)
- Analytics/history
- Preventing duplicate notifications

```kotlin
// Records: "User dismissed the alert for this event at this time"
data class DismissedEventAlertRecord(
    val eventId: Long,
    val instanceStartTime: Long,
    val dismissTime: Long,        // When user dismissed
    val dismissType: EventDismissType,  // Manual, auto, swiped, etc.
    
    // Snapshot of event data at dismissal time
    val title: String,
    val startTime: Long,
    // ...
)
```

**Why "Alert" in the name?** Because what the user dismissed was the *alert/notification*, not the calendar event itself (that still exists in the calendar).

**Storage:** `DismissedEventsStorage` (Room: `dismissedEventsV2` table)

---

### Monitor Alerts (MonitorStorage)

**What it is:** Alerts being tracked for manual calendar scanning mode.

**Context:** Android provides `EVENT_REMINDER` broadcasts when alerts should fire, but these can be unreliable. Manual scan mode proactively queries the calendar and tracks upcoming alerts.

```kotlin
// Tracks: "This alert should fire at this time for this event instance"
data class MonitorEventAlertEntry(
    val eventId: Long,
    val alertTime: Long,          // When alert should fire
    val instanceStartTime: Long,  // Which instance of recurring event
    val instanceEndTime: Long,
    val isAllDay: Boolean,
    val alertCreatedByUs: Boolean, // Did we create this or did Android?
    val wasHandled: Boolean,       // Has alert already been processed?
)
```

**Why "Alert" in the name?** Because we're monitoring *alerts* (notification triggers), not events. One event can have multiple alerts (reminders at different times).

**Storage:** `MonitorStorage` (Room: `manualAlertsV1` table)

---

## Relationships

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Calendar Provider                                │
│                    (External - Android System)                           │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │
                                 │ queried by CalendarProvider wrapper
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Calendar Monitor                                  │
│           (watches for alerts that should fire)                          │
└─────────────┬───────────────────────────────────────────────────────────┘
              │
              │ tracks upcoming alerts in
              ▼
┌──────────────────────────┐
│   Monitor Alerts         │
│   (MonitorStorage)       │
│                          │
│   "Alert X should fire   │
│    at time Y for event Z"│
└──────────────────────────┘
              │
              │ when alert fires, creates
              ▼
┌──────────────────────────┐         ┌──────────────────────────┐
│   Event Alerts           │         │   Dismissed Event Alerts │
│   (EventsStorage)        │  ────►  │   (DismissedEventsStorage)│
│                          │ dismiss │                          │
│   "Active notification   │         │   "User dismissed alert  │
│    for event X"          │         │    for event X at time Y"│
└──────────────────────────┘         └──────────────────────────┘
```

---

## Naming Conventions

| Concept | Data Class | Room Entity | Storage |
|---------|------------|-------------|---------|
| Active notification | `EventAlertRecord` | `EventAlertEntity` | `EventsStorage` |
| Dismissed notification | `DismissedEventAlertRecord` | `DismissedEventEntity` | `DismissedEventsStorage` |
| Monitored upcoming alert | `MonitorEventAlertEntry` | `MonitorAlertEntity` | `MonitorStorage` |

**Pattern:** 
- `*Record` / `*Entry` = Domain data class
- `*Entity` = Room database entity
- `*Storage` = Repository/DAO wrapper

---

## Other Domain Concepts

### Settings (SharedPreferences)
User preferences: theme, notification sounds, snooze durations, etc.

### Persistent State (SharedPreferences)
App state that survives restarts: last scan time, pending operations, etc.

### Notification Channels (Android System)
Required for Android 8+. Categories like "Event Alerts", "Reminders", etc.

---

## Future Considerations

### Calendar Change Requests (DEPRECATED)
Previously stored pending edits to calendar events. Being removed - see `deprecated_features.md`.

### CR-SQLite Replication
Room databases use cr-sqlite extension for potential future sync capabilities. See `database_modernization_plan.md`.

