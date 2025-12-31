# Domain Model

## Overview

Core domain concepts in Calendar Notifications Plus and how they relate to each other.

## Data Flow

```
┌─────────────────────────────────────────┐
│           Calendar Provider              │
│  (Google Calendar, Exchange, etc.)       │
└────────────────────┬────────────────────┘
                     │ queried by CalendarProvider wrapper
                     ▼
┌─────────────────────────────────────────┐
│            Calendar Monitor              │
│     (watches for alerts that should fire)│
└─────────────┬───────────────────────────┘
              │ tracks upcoming alerts in
              ▼
┌──────────────────────────┐
│   Monitor Alerts         │
│   (MonitorStorage)       │
└──────────────────────────┘
              │ when alert fires, creates
              ▼
┌──────────────────────────┐         ┌──────────────────────────┐
│   Event Alerts           │         │   Dismissed Event Alerts │
│   (EventsStorage)        │  ────►  │   (DismissedEventsStorage)│
└──────────────────────────┘ dismiss └──────────────────────────┘
```

<details>
<summary>Mermaid version</summary>

```mermaid
flowchart TD
    CP[Calendar Provider] --> CM[Calendar Monitor]
    CM --> MS[(MonitorStorage)]
    MS --> ES[(EventsStorage)]
    ES -->|dismiss| DS[(DismissedEventsStorage)]
```

</details>

## Domain Concepts

### Calendar Events (External)

**Source:** Android Calendar Provider (not stored by us)

These are the actual calendar events on the user's device. We query them but don't own them.

Query types:
- `CalendarRecord` - calendar account (id, owner, displayName, color)
- `EventRecord` - full event details (used to populate/update EventAlertRecord)

### Event Alerts (EventsStorage)

**What it is:** An active notification that the app is managing.

**Lifecycle:** Created when an alert fires → Displayed to user → Snoozed or Dismissed

**Snoozing:** When snoozed, `snoozedUntil` is set to the wake time. An alarm reschedules the notification. The alert stays in EventsStorage (not moved to DismissedEventsStorage).

```kotlin
data class EventAlertRecord(
    // Identity
    val eventId: Long,
    val instanceStartTime: Long,  // For recurring events, identifies which instance
    
    // Event data (snapshot from calendar)
    val title: String,
    val desc: String,
    val location: String,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean,
    val isRepeating: Boolean,
    val color: Int,
    
    // Alert state (our data)
    val alertTime: Long,
    val notificationId: Int,
    val snoozedUntil: Long,
    val displayStatus: EventDisplayStatus,
    val lastStatusChangeTime: Long,
    // ...
)
```

**Storage:** `EventsStorage` (Room: `eventsV9` table)

### Dismissed Event Alerts (DismissedEventsStorage)

**What it is:** A record that the user dismissed an alert.

**Purpose:** Undo support, preventing duplicate notifications.

```kotlin
data class DismissedEventAlertRecord(
    val event: EventAlertRecord,  // The dismissed event's data
    val dismissTime: Long,
    val dismissType: EventDismissType
)
```

**Storage:** `DismissedEventsStorage` (Room: `dismissedEventsV2` table)

### Monitor Alerts (MonitorStorage)

**What it is:** Alerts being tracked for manual calendar scanning mode.

**Context:** Android's `EVENT_REMINDER` broadcasts can be unreliable. Manual scan mode proactively queries the calendar and tracks upcoming alerts.

```kotlin
data class MonitorEventAlertEntry(
    val eventId: Long,
    val alertTime: Long,
    val instanceStartTime: Long,
    val instanceEndTime: Long,
    val isAllDay: Boolean,
    val alertCreatedByUs: Boolean,
    val wasHandled: Boolean
)
```

**Storage:** `MonitorStorage` (Room: `manualAlertsV1` table)

## Relationships

```
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────────┐
│ MonitorStorage  │      │  EventsStorage  │      │DismissedEventsStorage│
├─────────────────┤      ├─────────────────┤      ├─────────────────────┤
│ eventId (PK)    │      │ eventId (PK)    │      │ eventId (PK)        │
│ alertTime (PK)  │─────►│ instanceStart(PK)│─────►│ instanceStart (PK)  │
│ instanceStart(PK)│      │ title           │      │ dismissTime         │
│ wasHandled      │      │ alertTime       │      │ dismissType         │
└─────────────────┘      │ snoozedUntil    │      └─────────────────────┘
                         │ displayStatus   │
                         └─────────────────┘
```

<details>
<summary>Mermaid version</summary>

```mermaid
erDiagram
    MonitorStorage {
        long eventId PK
        long alertTime PK
        long instanceStartTime PK
        boolean wasHandled
    }
    
    EventsStorage {
        long eventId PK
        long instanceStartTime PK
        string title
        long alertTime
        long snoozedUntil
        int displayStatus
    }
    
    DismissedEventsStorage {
        long eventId PK
        long instanceStartTime PK
        long dismissTime
        int dismissType
    }
    
    MonitorStorage ||--o| EventsStorage : "fires"
    EventsStorage ||--o| DismissedEventsStorage : "dismissed"
```

</details>

## Naming Conventions

| Concept | Domain Class | Room Entity | Storage |
|---------|--------------|-------------|---------|
| Active notification | `EventAlertRecord` | `EventAlertEntity` | `EventsStorage` |
| Dismissed notification | `DismissedEventAlertRecord` | `DismissedEventEntity` | `DismissedEventsStorage` |
| Monitored upcoming alert | `MonitorEventAlertEntry` | `MonitorAlertEntity` | `MonitorStorage` |

## Supporting Types

### Enums

| Enum | Purpose |
|------|---------|
| `EventDisplayStatus` | Hidden, DisplayedNormal, DisplayedCollapsed |
| `EventDismissType` | How alert was dismissed (manual, auto, etc.) |
| `EventOrigin` | How alert was created (broadcast vs manual scan) |
| `EventStatus` | Calendar event status (Tentative, Confirmed, Cancelled) |
| `AttendanceStatus` | User's RSVP status |

## Persistent State (SharedPreferences)

| Class | Purpose |
|-------|---------|
| `Settings` | User preferences (theme, sounds, snooze durations) |
| `PersistentState` | App state (last notification time, snooze alarms) |
| `ReminderState` | Reminder firing state (fire count, pattern index) |
| `CalendarMonitorState` | Scan state (next fire time, first scan flag) |
| `BTCarModeStorage` | Bluetooth car mode trigger devices |
