# Fix: Remove displayStatus filter from PowerSync event sync

## Summary

The sync logic incorrectly filters out events with `displayStatus = 0 (Hidden)`, causing some events to not sync to the remote database.

## Background / Research

### Problem Discovery

When comparing a local backup (`tmp/cs_backup.ab` with 158 events) against the remote database (155 events), 3 events were missing. Investigation revealed:

1. All 3 missing events had `dsts = 0` (displayStatus = Hidden)
2. None of the 3 events were in the `dismissedEventsV2` table
3. All 3 events were **snoozed** with future snooze times:

| Event | snoozedUntil | Status |
|-------|-------------|--------|
| (d) Content strategy | Feb 27, 2026 | Snoozed 6 days out |
| (d) Healthcare - ... | Feb 23, 2026 | Snoozed 2 days out |
| (d) Valentine's Day - ... | Feb 23, 2026 | Snoozed 2 days out |

### Root Cause Analysis

The sync code in `src/lib/orm/index.ts` filters events:

```typescript
// Line 25-28
const query = tableName === 'eventsV9' 
  ? `SELECT * FROM ${tableName} WHERE dsts != 0`
  : `SELECT * FROM ${tableName}`;
```

This filter was based on a **misunderstanding** of what `displayStatus` means.

### What displayStatus Actually Means

`displayStatus` (stored as `dsts` in the database) is the **notification presentation state**, NOT the event's active/dismissed status:

| Value | Enum Name | Meaning |
|-------|-----------|---------|
| 0 | `Hidden` | Not currently showing in notification tray |
| 1 | `DisplayedNormal` | Shown as individual notification |
| 2 | `DisplayedCollapsed` | Shown in "X more events" summary |

This field is defined in:
- `android/app/src/main/java/com/github/quarck/calnotify/calendar/EventDisplayStatus.kt`

**Key insight:** A better name would be `NotificationDisplayStatus` - it only governs whether the event is currently visible in the Android notification manager, not whether the user considers the event "active" or "done".

### When is displayStatus = Hidden (0)?

1. **New events** - Default state when first detected (before notification is posted)
2. **Snoozed events** - Set back to Hidden when snoozed, stays Hidden until snooze expires
3. **Restored events** - Set to Hidden via `onEventRestored()` before re-posting notification

The notification manager transitions events from Hidden → DisplayedCollapsed/DisplayedNormal when posting notifications. Events stay Hidden while waiting (e.g., for snooze to expire).

### What Actually Determines "Active" vs "Dismissed"

- **Active events** → In `eventsV9` table
- **Dismissed events** → In `dismissedEventsV2` table

These are separate tables. All events in `eventsV9` are "active" regardless of their `displayStatus`.

## The Fix

### Code Change

**File:** `src/lib/orm/index.ts`

```typescript
// BEFORE (buggy):
const query = tableName === 'eventsV9' 
  ? `SELECT * FROM ${tableName} WHERE dsts != 0`
  : `SELECT * FROM ${tableName}`;

// AFTER (correct):
const query = `SELECT * FROM ${tableName}`;
```

Simply remove the `dsts` filter. All events in `eventsV9` should be synced.

### Why This Is Safe

1. **Dismissed events are in a separate table** - If we wanted to exclude dismissed events, we'd exclude the `dismissedEventsV2` table from sync, not filter by `dsts`
2. **No data loss** - This change syncs MORE data, not less
3. **Matches user expectations** - If an event is in the snooze list, it should sync to remote

## Testing

1. Create a backup with snoozed events
2. Verify snoozed events have `dsts = 0` and `snz > currentTime`
3. Trigger sync
4. Verify all events (including snoozed) appear in remote database
5. Verify count matches: local `eventsV9` count == remote count

## Related Files

- `src/lib/orm/index.ts` - Sync query logic (THE FIX)
- `src/lib/powersync/Connector.ts` - PowerSync connector
- `android/app/src/main/java/com/github/quarck/calnotify/calendar/EventDisplayStatus.kt` - Enum definition
- `android/app/src/main/java/com/github/quarck/calnotify/notification/EventNotificationManager.kt` - Where displayStatus transitions happen
