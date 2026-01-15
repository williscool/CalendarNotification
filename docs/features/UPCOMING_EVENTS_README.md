# Upcoming Events View

See what's coming up on your calendar before notifications fire—so you can plan your day and never be surprised by imminent events.

## Overview

The Upcoming Events view displays calendar events that are scheduled to fire reminders within a configurable lookahead window. This lets you:

- **See what's coming** - Preview your schedule before reminders fire
- **Plan ahead** - Know what meetings or events are approaching
- **Never be caught off guard** - No more "I forgot about that meeting in 5 minutes!"

Events appear in the Upcoming tab based on when their reminder is scheduled to fire, not the event start time. Once a reminder fires, the event moves to the Active tab.

## How to Access

The app uses a bottom navigation bar with three tabs:

| Tab | Icon | Description |
|-----|------|-------------|
| **Active** | 🔔 | Events with fired reminders (notifications you've received) |
| **Upcoming** | ⏰ | Events with reminders scheduled to fire soon |
| **Dismissed** | 🗑️ | Events you've dismissed (the "Bin") |

![Bottom Navigation](images/bottom-navigation.png)
*Bottom navigation showing the three tabs*

Tap the clock icon (⏰) to view upcoming events.

## The Upcoming Tab

![Upcoming Events Tab](images/upcoming-events-tab.png)
*The Upcoming tab showing events scheduled to fire within your lookahead window*

Events are sorted by alert time (when the reminder will fire). Each event card shows:
- Event title and time
- Calendar color indicator
- How long until the reminder fires

## Configuring the Lookahead Window

You can customize how far ahead the app looks for upcoming events. Go to **Settings → Navigation & UI → Upcoming Events**.

![Lookahead Settings](images/lookahead-settings.png)
*Configure how far ahead to show upcoming events*

### Lookahead Modes

#### Fixed Hours (Default)

Shows events with reminders scheduled to fire within a fixed number of hours from now.

| Setting | What You'll See |
|---------|-----------------|
| 4 hours | Events firing in the next 4 hours |
| 8 hours (default) | Events firing in the next 8 hours |
| 24 hours | Events firing in the next day |

**Best for:** Most users who want simple, predictable behavior.

#### Day Boundary Mode

Shows events until a configurable "day boundary" time. This is designed for people who think in terms of "today" vs "tomorrow" rather than hours.

**How it works:**
- Before the boundary hour: Show events until the boundary (you're "still in yesterday")
- After the boundary hour: Show events until tomorrow's boundary (your "new day" has begun)

**Example with 4 AM boundary:**

| Current Time | You'll See Events Until | Why |
|--------------|-------------------------|-----|
| 1:00 AM | 4:00 AM today | Still "last night" mentally |
| 5:00 AM | 4:00 AM tomorrow | "Today" has begun |
| 10:00 PM | 4:00 AM tomorrow | Winding down the day |

**Best for:** Night owls who stay up past midnight but don't want to see "tomorrow's" events until they've actually slept.

## Switching Between Classic and New UI

If you prefer the original single-list view without tabs, you can switch back:

1. Go to **Settings → Navigation & UI**
2. Tap **"Switch to Classic View"**
3. Confirm the restart

![Switch View Setting](images/switch-view-setting.png)
*Toggle between new tabbed UI and classic view*

To switch back to the new tabbed view later, use **"Switch to New View"** in the same settings screen.

## Tips

- **Pull down to refresh** - Swipe down on any tab to refresh the event list
- **Events update automatically** - As time passes, events move from Upcoming → Active as their reminders fire
- **Calendar colors** - Events show their calendar's color for easy identification

## Coming Soon

Future updates will add:
- **Pre-snooze** - Snooze upcoming events before they fire
- **Pre-mute** - Mute an event's notification in advance
- **Pre-dismiss** - Skip an event entirely before the reminder
- **Calendar filtering** - Show only events from specific calendars

## Troubleshooting

### "No upcoming events" but I know I have events

1. **Check the lookahead window** - Your events may be outside the configured lookahead. Try increasing the hours in Settings → Navigation & UI → Hours to Look Ahead.

2. **Check calendar selection** - Make sure the calendar containing your events is enabled in Settings → Handled Calendars.

3. **Pull down to refresh** - Swipe down on the Upcoming tab to trigger a fresh scan.

### Events not appearing in the right order

Events are sorted by **reminder time**, not event start time. An event at 5 PM with a 1-hour reminder will appear before an event at 3 PM with a 15-minute reminder.

## Related Documentation

- [Data Sync Setup](../DATA_SYNC_README.md) - Sync events across devices
- [Calendar Monitoring Architecture](../architecture/calendar_monitoring.md) - Technical details on how events are tracked
