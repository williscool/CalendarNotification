# Investigate: Snooze displayStatus inconsistency

## Status: INVESTIGATED — minor data hygiene bug, no behavioral impact

## Summary

Individual snooze and batch snooze handle `displayStatus` differently. The batch snooze path forgot to set `displayStatus = Hidden`. This has been present since the original 2016 code and is **not intentional** — just an oversight that never caused problems because snoozed events are filtered by `snoozedUntil` before `displayStatus` matters.

## What is displayStatus for?

`displayStatus` is a **notification tray cache** — it tracks the current presentation state of each event in Android's notification manager so `postEventNotifications` can skip redundant work:

| Value | Meaning | Effect |
|-------|---------|--------|
| `Hidden` (0) | No notification currently in tray | Manager will post a new notification |
| `DisplayedNormal` (1) | Has its own individual notification | `shouldPostIndividualNotification` returns false (skip) |
| `DisplayedCollapsed` (2) | Represented in "X more" summary | `postEverythingCollapsed` skips re-posting |

It's an **idempotency/performance optimization** — without it, every call to `postEventNotifications` would tear down and rebuild every notification. It has nothing to do with whether an event is "active" or "dismissed" (that's determined by which table the event lives in: `eventsV9` vs `dismissedEventsV2`).

## The Inconsistency

### Individual snooze (`snoozeEvent`) — line 825-828

```kotlin
val (success, newEvent) = db.updateEvent(event,
        snoozedUntil = snoozedUntil,
        lastStatusChangeTime = currentTime,
        displayStatus = EventDisplayStatus.Hidden)  // ← Correctly reflects "no notification in tray"
```

Then calls `onEventSnoozed` → removes the individual notification → calls `postEventNotifications`.

### Batch snooze (`snoozeEvents`) — line 894-899

```kotlin
val (success, _) =
        db.updateEvent(
                event,
                snoozedUntil = newSnoozeUntil,
                lastStatusChangeTime = currentTime
        )  // ← Leaves displayStatus as DisplayedCollapsed even though cancelAll() removes all notifications
```

Then calls `onAllEventsSnoozed` → `cancelAll()` (removes all notifications from tray).

## Git history

- `displayStatus` was created in June 2016 (`fe921bb8`) by Sergey Parshin
- `snoozeEvent` has **always** set `displayStatus = Hidden` — present from the earliest version (`a2a8e9a1`)
- `snoozeEvents` (batch) has **never** set it — the omission has been there since the function was created
- No commit message or code comment suggests this difference was intentional
- The batch snooze was extracted from `snoozeAllEvents` in `98634331` ("Back port from Calendar Notifications NG") — the omission carried over

## Investigation: Does it affect behavior?

**No.** Every code path that reads `displayStatus` for snoozed events is guarded by `snoozedUntil` checks:

1. **`isReminderEvent`** — `displayStatus != Hidden || snoozedUntil != 0L` → snoozed events always match via the `snoozedUntil` clause regardless of `displayStatus`

2. **`computeHasNewTriggeringEvent`** — requires `snoozedUntil == 0L`, so snoozed events never qualify regardless of `displayStatus`

3. **`shouldPostIndividualNotification`** — `isReturningFromSnooze` (checks `snoozedUntil != 0L`) is the first case and takes priority

4. **`shouldBeQuietForEvent` parameters** — every `displayStatus`-dependent value is ANDed with `!isReturningFromSnooze`:
   ```kotlin
   isAlreadyDisplayed = wasCollapsed && !isReturningFromSnooze,  // always false for snooze returns
   isPrimaryEvent = isPrimaryEvent && !isReturningFromSnooze,    // always false for snooze returns
   ```

5. **Database overwrite on snooze fire** — when snooze expires, `postEventNotifications` always sets `displayStatus` to `DisplayedNormal` or `DisplayedCollapsed`, overwriting whatever was there

The `snoozedUntil` field is the real gatekeeper for snoozed event behavior. `displayStatus` is irrelevant while an event is snoozed.

## The Fix

Add `displayStatus = EventDisplayStatus.Hidden` to the batch snooze path for data hygiene:

**File:** `android/app/src/main/java/com/github/quarck/calnotify/app/ApplicationController.kt`

```kotlin
// In snoozeEvents, around line 894-899:
val (success, _) =
        db.updateEvent(
                event,
                snoozedUntil = newSnoozeUntil,
                lastStatusChangeTime = currentTime,
                displayStatus = EventDisplayStatus.Hidden
        )
```

### Why

- Makes DB state semantically correct (snoozed = no notification in tray = Hidden)
- Consistent with individual snooze path
- Zero behavioral change — this is purely data hygiene
- Prevents future confusion if someone queries the DB and sees "DisplayedCollapsed" for events with no notification

## Related

- `docs/dev_todo/fix_sync_display_status_filter.md` — the sync filter bug that surfaced this inconsistency
