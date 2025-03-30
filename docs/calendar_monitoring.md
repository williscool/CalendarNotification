# How Calendar Monitoring works


## testCalendarMonitoringDirectReminder

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

## testCalendarMonitoringManualRescan
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