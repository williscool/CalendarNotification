# Investigate: Snooze displayStatus inconsistency

## Summary

Individual snooze and batch snooze handle `displayStatus` differently. Need to investigate whether this is intentional or a bug.

## Discovery

While investigating the sync filter bug (see `fix_sync_display_status_filter.md`), we found that snoozed events had inconsistent `displayStatus` values:

- 3 events had `dsts = 0` (Hidden)
- 155 events had `dsts = 2` (DisplayedCollapsed)

All 158 events were snoozed with future snooze times. The difference was **how** they were snoozed.

## The Inconsistency

### Individual snooze (`snoozeEvent`)

**File:** `android/app/src/main/java/com/github/quarck/calnotify/app/ApplicationController.kt`
**Lines:** 825-828

```kotlin
val (success, newEvent) = db.updateEvent(event,
        snoozedUntil = snoozedUntil,
        lastStatusChangeTime = currentTime,
        displayStatus = EventDisplayStatus.Hidden)  // ← Explicitly sets Hidden
```

### Batch snooze (`snoozeEvents`)

**File:** `android/app/src/main/java/com/github/quarck/calnotify/app/ApplicationController.kt`
**Lines:** 894-899

```kotlin
val (success, _) =
        db.updateEvent(
                event,
                snoozedUntil = newSnoozeUntil,
                lastStatusChangeTime = currentTime
        )  // ← Does NOT change displayStatus
```

## Questions to Investigate

1. **Is this intentional?** Was there a reason to set Hidden on individual snooze but not batch?

2. **What's the semantic meaning?** 
   - Does `displayStatus = Hidden` affect anything when `snoozedUntil > 0`?
   - When the snooze alarm fires, does it matter what the prior `displayStatus` was?

3. **What should happen when snooze fires?**
   - The notification manager will post the event and set `displayStatus = DisplayedNormal` or `DisplayedCollapsed`
   - Does the starting state matter for sound/vibration behavior?

4. **NotificationContext implications:**
   - `isReminderEvent()` returns `true` if `displayStatus != Hidden || snoozedUntil != 0`
   - So snoozed events are considered "reminder events" regardless of displayStatus
   - This suggests displayStatus might not matter for snoozed events?

## Related Code

```kotlin
// NotificationContext.kt line 184-185
fun isReminderEvent(event: EventAlertRecord): Boolean =
    event.displayStatus != EventDisplayStatus.Hidden || event.snoozedUntil != 0L
```

The `|| snoozedUntil != 0L` part means snoozed events are always treated as reminder events regardless of displayStatus. This might mean the inconsistency has no practical effect.

## Next Steps

1. Review git history to see if there's context on why these differ
2. Trace what happens when a snooze alarm fires - does displayStatus affect behavior?
3. Decide if both should set Hidden, both should preserve, or the current behavior is fine
