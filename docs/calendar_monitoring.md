# How Calendar Monitoring works

## testCalendarMonitoringDirectReminder

Follows `android.intent.action.EVENT_REMINDER` through the code to registerNewEvent

Calendar Provider EVENT_REMINDER Broadcast
    │
    ▼
CalendarMonitor.onProviderReminderBroadcast
    │
    ├──► Extract alertTime from URI
    │
    ▼
CalendarProvider.getAlertByTime
    │
    ├──► Returns EventAlertRecord
    │
    ▼
Should mark event as handled and skip?
    │
    ├──► No ──────────────────────┐
    │                             ▼
    │                     ApplicationController.registerNewEvent
    │                             │
    │                             ▼
    │                     EventsStorage.addEvent
    │                             │
    │                             ▼
    │                     EventNotificationManager.postEventNotifications
    │                             │
    │                             ▼
    │                     Mark alert as handled
    │                             │
    ├──► Yes ──────────────┐      │
    │                      ▼      │
    │              Mark as handled silently
    │                      │      │
    │                      ▼      ▼
    └──────────────► CalendarProvider.dismissNativeEventAlert
                            │
                            ▼
                    ApplicationController.afterCalendarEventFired
                            │
                            ▼
                    Reschedule alarms and notify UI

``` mermaid
flowchart TD
    A[Calendar Provider EVENT_REMINDER Broadcast] --> B[CalendarMonitor.onProviderReminderBroadcast]
    
    B --> C[Extract alertTime from URI]
    
    C --> D[CalendarProvider.getAlertByTime]
    D -->|Returns EventAlertRecord| E{Should mark event<br/>as handled and skip?}
    
    E -->|No| F[ApplicationController.registerNewEvent]
    F --> G[EventsStorage.addEvent]
    
    G --> H[EventNotificationManager.postEventNotifications]
    
    H --> I[Mark alert as handled]
    I --> J[CalendarProvider.dismissNativeEventAlert]
    
    J --> K[ApplicationController.afterCalendarEventFired]
    K --> L[Reschedule alarms and notify UI]

    E -->|Yes| M[Mark as handled silently]
    M --> J
```

Note: The broadcast receiver for EVENT_REMINDER is registered with the highest possible priority (2147483647) to ensure reliable event handling.

## testCalendarMonitoringManualRescan

Follows `android.intent.action.PROVIDER_CHANGED` through the code to registerNewEvent

onCalendarChanged
    │
    ▼
CalendarMonitor.launchRescanService
    │
    ├──► Intent[CalendarMonitorService]
    │    Parameters:
    │    - start_delay=2000
    │    - rescan_monitor=true
    │    - reload_calendar=true
    │    - user_action_until=0
    │
    ▼
CalendarMonitorService.onHandleIntent
    │
    ├──► CalendarMonitor.onRescanFromService
    │    │
    │    ├──► CalendarMonitorManual.scanNextEvent
    │    │    │
    │    │    ├──► CalendarProvider.getEventAlertsForInstancesRange
    │    │    │
    │    │    ├──► filterAndMergeAlerts
    │    │    │    │
    │    │    │    ├──► MonitorStorage.getAlertsForInstanceStartRange
    │    │    │    │
    │    │    │    └──► Update MonitorStorage with new/disappeared alerts
    │    │    │
    │    │    └──► Set next alarm time
    │    │
    │    └──► CalendarMonitor.setOrCancelAlarm
    │
    ▼
Time advances past reminder time
    │
    ▼
ManualEventAlarmBroadcastReceiver receives alarm
    │
    ├──► CalendarMonitor.onAlarmBroadcast
    │    │
    │    ├──► Check timing condition
    │    │    (nextEventFireFromScan < currentTime + ALARM_THRESHOLD)
    │    │
    │    └──► CalendarMonitorManual.manualFireEventsAt_NoHousekeeping
    │         │
    │         ├──► MonitorStorage.getAlertsAt/getAlertsForAlertRange
    │         │
    │         ├──► registerFiredEventsInDB
    │         │    │
    │         │    └──► ApplicationController.registerNewEvents
    │         │
    │         └──► markAlertsAsHandledInDB
    │
    ▼
CalendarMonitorService processes final intent
    Parameters:
    - alert_time=reminderTime
    - rescan_monitor=true
    - reload_calendar=false
    - start_delay=0

Note: The CalendarMonitorService uses a wake lock during the rescan process to ensure reliable operation, especially when processing calendar changes and firing events.

``` mermaid
flowchart TD
    A[onCalendarChanged] --> B[CalendarMonitor.launchRescanService]
    B --> C[Intent: CalendarMonitorService]
    C -->|Parameters:<br/>start_delay=2000<br/>rescan_monitor=true<br/>reload_calendar=true<br/>user_action_until=0| D[CalendarMonitorService.onHandleIntent]
    
    D --> E[CalendarMonitor.onRescanFromService]
    E --> F[CalendarMonitorManual.scanNextEvent]
    
    F --> G[CalendarProvider.getEventAlertsForInstancesRange]
    F --> H[filterAndMergeAlerts]
    H --> I[MonitorStorage.getAlertsForInstanceStartRange]
    H --> J[Update MonitorStorage]
    F --> K[Set next alarm time]
    
    E --> L[CalendarMonitor.setOrCancelAlarm]
    
    M[Time advances past reminder time] --> N[ManualEventAlarmBroadcastReceiver]
    N --> O[CalendarMonitor.onAlarmBroadcast]
    
    O --> P{Check timing condition<br/>nextEventFireFromScan <br/> currentTime + ALARM_THRESHOLD}
    P -->|true| Q[manualFireEventsAt_NoHousekeeping]
    
    Q --> R[MonitorStorage.getAlertsAt]
    Q --> S[registerFiredEventsInDB]
    S --> T[ApplicationController.registerNewEvents]
    Q --> U[markAlertsAsHandledInDB]
    
    V[CalendarMonitorService final intent] -->|Parameters:<br/>alert_time=reminderTime<br/>rescan_monitor=true<br/>reload_calendar=false<br/>start_delay=0| D
```

## Additional Calendar Monitoring Triggers

Besides the two main flows above, the Calendar Monitor can be triggered through several other paths:

### System Boot

BOOT_COMPLETED Broadcast
    │
    ▼
BootCompleteBroadcastReceiver.onReceive
    │
    ▼
ApplicationController.onBootComplete
    │
    ├──► Post notifications for existing events
    │
    ├──► Reschedule alarms
    │
    ▼
CalendarMonitor.launchRescanService
    (Same flow as PROVIDER_CHANGED)

### Application Update

MY_PACKAGE_REPLACED Broadcast
    │
    ▼
AppUpdatedBroadcastReceiver.onReceive
    │
    ▼
ApplicationController.onAppUpdated
    │
    ├──► Post notifications for existing events
    │
    ├──► Reschedule alarms
    │
    ▼
CalendarMonitor.launchRescanService
    (Same flow as PROVIDER_CHANGED)

### Time or Timezone Changes

TIME_SET or TIMEZONE_CHANGED Broadcast
    │
    ▼
TimeSetBroadcastReceiver.onReceive
    │
    ▼
ApplicationController.onTimeChanged
    │
    ├──► Reschedule alarms
    │
    ▼
CalendarMonitor.onSystemTimeChange
    │
    ▼
CalendarMonitor.launchRescanService
    (Same flow as PROVIDER_CHANGED)

### Periodic Rescan

System-scheduled Alarm
    │
    ▼
ManualEventAlarmPeriodicRescanBroadcastReceiver.onReceive
    │
    ▼
CalendarMonitor.onPeriodicRescanBroadcast
    │
    ▼
CalendarMonitor.launchRescanService
    (Same flow as PROVIDER_CHANGED)