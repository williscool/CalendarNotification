# Upcoming Events

See which reminders are about to fire, and deal with them before they do.

The **Upcoming** tab lists calendar events whose reminder hasn't fired yet. Events are placed by **when the reminder fires**, not when the event starts, and are sorted soonest-first. When a reminder fires, the event leaves Upcoming and appears in **Active** as a normal notification.

Upcoming is part of the tabbed UI (on by default). The classic single-list view has no Upcoming tab.

## The tabs

The bottom bar has three tabs, in this order:

| Tab | What's in it |
|-----|--------------|
| **Active** | Reminders that have fired (your notifications), including snoozed ones |
| **Upcoming** | Reminders that haven't fired yet, within the upcoming window |
| **Dismissed** | Events you've dismissed (the "Bin") |

Switching tabs clears the search box and filters.

## What each row shows

- Event title, date and time
- **Alert fires at …** — when the reminder will fire
- Calendar color bar
- A mute icon if you've pre-muted the event

## Choosing how far ahead to look

Tap the time chip above the list to open **Upcoming Window**. Your choice is saved and stays until you change it.

- **Presets** — show reminders firing within the next 4 hours, 8 hours, 1 day, 3 days or 1 week. **8 hours** is the default.
- **Day boundary** — show reminders until your "new day" starts (4 AM by default):
  - Before the boundary hour, you see reminders up to today's boundary.
  - At or after it, you see reminders up to tomorrow's boundary.

  With a 4 AM boundary:

  | Now | Shows reminders until | |
  |-----|----------------------|---|
  | 1:00 AM | 4:00 AM today | It's still "last night" |
  | 5:00 AM | 4:00 AM tomorrow | Your day has started |
  | 10:00 PM | 4:00 AM tomorrow | Winding down |

  Handy if you stay up past midnight and don't want tomorrow's reminders showing up yet.

Change the presets or the boundary hour under **Settings → Navigation & UI → Upcoming Events**:

- **Lookahead interval presets** — comma-separated, e.g. `4h, 8h, 1d, 3d, 1w`. Up to 30 days ahead.
- **Day Boundary Hour** — midnight to 10 AM.

## Acting on an upcoming event

**Tap** an event to open it. From there you can:

- **Pre-snooze** — choose a snooze preset, a custom period, or a specific date and time. The event moves to Active as snoozed and won't notify until then.
- **Mute when it fires** (in the ⋮ menu) — the reminder still fires, but silently. The event stays in Upcoming with a mute icon. Choose **Unmute** to undo.
- **Dismiss** (in the ⋮ menu) — skip the reminder entirely. The event goes to Dismissed.
- **Open in Calendar**, or **Edit** the event (Edit is hidden for read-only calendars).

**Swipe** an event left or right to dismiss it. Tap **UNDO** on the row to bring it back.

### Changing your mind

- **Pre-snoozed:** open the event in Active and choose **Unsnooze (to Upcoming)**. This is only offered while the original reminder time is still in the future.
- **Dismissed:** open the event in the Dismissed tab (it's labelled "Dismissed from Upcoming") and choose **Restore notification**. It returns to Upcoming if its reminder hasn't fired yet, otherwise to Active.

## Filtering and search

The chips above the list filter what you see:

- **Calendar** — show only some calendars.
- **Status** — All, Muted, Recurring, Pinned or Unpinned.
- **Time** — the upcoming window, described above.

The search icon filters by title and description.

## Refreshing

The list reloads when you open the tab, when you pull down on it, when you change a filter, and when the app's event data changes. It doesn't refresh on a timer, so if the app has been sitting open for a while, pull down to refresh.

## Troubleshooting

### "No upcoming events", but I know I have some

1. **Widen the window.** The reminder may fire after the current window ends. Try a longer preset from the time chip.
2. **Check the calendar is handled.** Only calendars enabled under **Settings → Handled Calendars** appear.
3. **Check your filters.** A Calendar or Status filter may be hiding it.
4. **Pull down to refresh.**

### The order looks wrong

Events are sorted by **reminder time**, not start time. A 5 PM event with a 2-hour reminder (fires at 3 PM) is listed before a 4 PM event with a 15-minute reminder (fires at 3:45 PM).

### I don't see the Upcoming tab

Turn on **Settings → Navigation & UI → New Navigation UI**, or use **Switch to New View** on the same screen. The app restarts to apply it.

## Related documentation

- [Calendar Monitoring Architecture](../architecture/calendar_monitoring.md) — how the app finds upcoming reminders
