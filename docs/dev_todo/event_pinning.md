# Feature: Pinned Notifications Excluded from Batch Snooze

**GitHub Issue:** [#186](https://github.com/williscool/CalendarNotification/issues/186)

## Background

When managing many reminders, users often use "Snooze All" or "Change All" to quickly push everything back. However, certain critical events (flight check-in, medication, imminent meeting) should not be accidentally snoozed this way — they need to stay visible and active.

The app already has a bitmask `flags` field on `EventAlertRecord` (with `IS_MUTED`, `IS_TASK`, `IS_ALARM`), an `isNotSpecial` exclusion in `snoozeEvents()`, and a mature filter pill system (`FilterState`, `StatusOption`). Pinning builds naturally on all of these.

### Batch snooze paths today

| Entry Point | Method Called | Filter Applied |
|-------------|-------------|---------------|
| MainActivityModern menu → SnoozeAllActivity | `snoozeAllEvents(…, searchQuery, filterState)` | search + FilterState |
| Notification group swipe/action | `snoozeAllEvents(ctx, delay, false, true)` | none (onlySnoozeVisible) |
| Notification collapsed group swipe | `snoozeAllCollapsedEvents(ctx, delay, false, true)` | displayStatus == Collapsed |
| Multi-select → SnoozeAllActivity | `snoozeSelectedEvents(ctx, keys, delay, isChange)` | explicit key set |

## Goal

Add the ability to "pin" event notifications so they are excluded from batch snooze operations (Snooze All, Change All, Snooze All Collapsed). Pinned events can still be individually snoozed/dismissed. A visual indicator and filter pill make pinned state visible and filterable in the event list.

## Non-Goals

- **Pin/unpin in ViewEventActivity** — Deferred; the detail view is out of scope for this iteration. Pin/unpin happens from the event list.
- **Pin-related filter pill on Upcoming or Dismissed tabs** — Only the Active tab's Status filter gets the PINNED option.
- **Notification action button for pin/unpin** — Adding a "Pin" action on individual notifications is future work.
- **Dismiss All exclusion** — Dismiss All doesn't support any filtering today ([#215 context](./snooze_all_filter_pills.md)). Pinning can be added there when Dismiss All gets filter support.
- **`#pin` hashtag integration** — [#185](https://github.com/williscool/CalendarNotification/issues/185) extended hashtag system is a separate feature.
- **Pin state sync to cloud** — Pin state lives in the existing `flags` column which already syncs via cr-sqlite if enabled.

## Key Decisions Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Storage mechanism | **`IS_PINNED` flag on existing `flags` bitmask** | No DB migration, consistent with `isMuted`/`isTask`/`isAlarm` pattern |
| Batch exclusion scope | **`snoozeAllEvents` + `snoozeAllCollapsedEvents`** | Covers both UI and notification paths; `snoozeSelectedEvents` NOT excluded (explicit selection overrides pin) |
| Pin/unpin UI | **Tap pin icon on event card in list** | Discoverable, no new screens, works without detail view |
| Filter integration | **Add `PINNED` to `StatusOption`** | Uses existing filter pill infrastructure; OR logic with other status options |
| Event count in Snooze All | **Exclude pinned from count** | Count should match what actually gets snoozed |

## Current Architecture

### EventAlertFlags (bitmask on `flags: Long`)

```
Bit 0 (1L): IS_MUTED
Bit 1 (2L): IS_TASK  
Bit 2 (4L): IS_ALARM
Bit 3 (8L): IS_PINNED  ← new
```

The `flags` field maps to Room column `i1` in `EventAlertEntity`. No migration needed — adding a new bit to an existing `Long` column is backward-compatible (existing rows have bit 3 = 0 → `isPinned = false`).

### Batch snooze flow

```
snoozeAllEvents() / snoozeAllCollapsedEvents()
    ↓ builds filter lambda
snoozeEvents(context, filter, ...)
    ↓ loads events
db.events.filter { it.isNotSpecial && filter(it) }
    ↓ for each matching event
db.updateEvent(event, snoozedUntil = ..., displayStatus = Hidden)
```

Pinned exclusion goes in the filter lambda of `snoozeAllEvents` and `snoozeAllCollapsedEvents`, NOT in the base `snoozeEvents`, so `snoozeSelectedEvents` (explicit multi-select) is unaffected.

## Design Decisions

### Why filter lambda, not `snoozeEvents` base?

`snoozeEvents()` is the shared workhorse for all batch operations including `snoozeSelectedEvents()`. If we add `!isPinned` in the base, explicitly selected pinned events would be silently skipped — bad UX. By adding the check only in `snoozeAllEvents()` and `snoozeAllCollapsedEvents()`, explicit selection always works, but "snooze everything" respects pinning.

### Pin/unpin from event list (not detail view)

The event card already has a mute indicator (`imageview_is_muted_indicator`). Adding a tappable pin icon follows the same pattern. The icon acts as both indicator and toggle — tap to pin/unpin. This keeps the interaction in the list without requiring navigation to the detail view.

A future phase can add pin/unpin to ViewEventActivityNoRecents for discoverability, but the list is sufficient for MVP.

### Filter pill: PINNED as StatusOption

`StatusOption` is multi-select with OR logic. Adding `PINNED` means:
- Select only PINNED → shows pinned events
- Select PINNED + SNOOZED → shows events that are pinned OR snoozed
- Select nothing → shows all events (existing behavior)

This integrates cleanly with the existing `FilterState.matchesStatus()` without special-casing.

## Implementation Plan

### Phase 1: Storage — `IS_PINNED` Flag

**Goal:** Add the pinned flag to the domain model.

**Changes:**
- `EventAlertRecord.kt`: Add `IS_PINNED = 8L` to `EventAlertFlags`, add `isPinned` computed property (same pattern as `isMuted`)
- No changes to `EventAlertEntity.kt` or Room schema — `flags`/`i1` column already exists and maps correctly

**Verification:** Unit test that `isPinned` reads/writes bit 3 correctly, doesn't interfere with other flags, and round-trips through `EventAlertEntity`.

### Phase 2: Batch Exclusion — Snooze All Skips Pinned

**Goal:** Pinned events are excluded from all "snooze all" operations.

**Changes:**
- `ApplicationController.kt`: Add `!event.isPinned` to the filter lambda in:
  - `snoozeAllEvents()` — before search/filterState checks
  - `snoozeAllCollapsedEvents()` — alongside the existing displayStatus check
- `snoozeSelectedEvents()` — **no change** (explicit selection overrides pin)

**Verification:** Tests confirming:
- Pinned events survive snooze-all while unpinned events get snoozed
- Pinned events survive snooze-all-collapsed
- Pinned events ARE snoozed when explicitly selected via `snoozeSelectedEvents`
- Notification-path snooze-all (no search/filter) still excludes pinned

### Phase 3: Event List UI — Pin Indicator + Toggle

**Goal:** Users can see which events are pinned and toggle pin state from the event list.

**Changes:**
- `EventListAdapter.kt`: 
  - Show pin icon (`imageview_is_pinned_indicator`) when `event.isPinned` — similar to existing mute indicator
  - Make pin icon tappable to toggle pin state (callback to fragment)
- Event card layout XML: Add pin icon ImageView near the existing mute indicator
- `ActiveEventsFragment.kt`: Handle pin toggle callback — update event in `EventsStorage`, refresh list
- Pin icon drawable: Use Material `ic_push_pin` (filled when pinned, outlined when not, or visible/gone)

**Verification:** Manual testing that pin icon appears for pinned events, tapping toggles state, and list refreshes.

### Phase 4: Filter Pill — PINNED Status Option

**Goal:** Users can filter the active event list to show only pinned events.

**Changes:**
- `FilterState.kt` / `StatusOption`: Add `PINNED` enum value with `matches` = `event.isPinned`
- `FilterState.toDisplayString()`: Add display string for PINNED
- `MainActivityModern.kt` status filter popup: Include PINNED option
- `strings.xml`: Add `filter_status_pinned` string resource

**Verification:** Selecting PINNED in status filter shows only pinned events. Combining with other status options works (OR logic).

### Phase 5: Snooze All Count Accuracy

**Goal:** The event count shown in SnoozeAllActivity excludes pinned events.

**Changes:**
- `ActiveEventsFragment.kt`: `getDisplayedEventCount()` should return count excluding pinned events (or add a separate `getSnoozableEventCount()`)
- `SnoozeAllActivity.kt`: Optionally show "(N pinned excluded)" hint when pinned events exist

**Verification:** Count in Snooze All dialog matches the number of events that actually get snoozed.

### Milestone Checkpoint

After Phase 5, the complete pinning feature works end-to-end:
- Users can pin/unpin from the event list
- Pinned events are visually indicated
- Snooze All / Change All / Snooze All Collapsed skip pinned events
- Users can filter to see only pinned events
- Snooze count accurately reflects what will be snoozed

## Files to Modify/Create

### New Files

| File | Purpose |
|------|---------|
| Pin icon drawable(s) | Material push_pin icon (filled + outlined, or single with visibility toggle) |
| Tests (see Testing Plan) | Robolectric tests for flag, batch exclusion, filter |

### Modified Files

| File | Phase | Changes |
|------|-------|---------|
| `EventAlertRecord.kt` | 1 | Add `IS_PINNED = 8L`, `isPinned` property |
| `ApplicationController.kt` | 2 | Add `!event.isPinned` to `snoozeAllEvents` and `snoozeAllCollapsedEvents` filter lambdas |
| `EventListAdapter.kt` | 3 | Pin indicator visibility + tap handler |
| Event card layout XML | 3 | Add pin icon ImageView |
| `ActiveEventsFragment.kt` | 3, 5 | Pin toggle handler, count adjustment |
| `FilterState.kt` / `StatusOption` | 4 | Add `PINNED` enum value |
| `MainActivityModern.kt` | 4 | Include PINNED in status filter popup |
| `SnoozeAllActivity.kt` | 5 | Optional "(N pinned excluded)" hint |
| `strings.xml` | 3, 4, 5 | Pin-related string resources |

## Testing Plan

### Unit Tests (Robolectric)

**EventAlertRecord pin flag:**
- `isPinned` defaults to false for new records
- Setting `isPinned = true` sets bit 3 in flags
- `isPinned` does not interfere with `isMuted`, `isTask`, `isAlarm` (all 4 can be true simultaneously)
- Flag round-trips through `EventAlertEntity.fromRecord()` / `toRecord()`

**Batch snooze exclusion (`ApplicationController`):**
- `snoozeAllEvents` with mix of pinned/unpinned: only unpinned get snoozed
- `snoozeAllEvents` with all pinned: returns null (no events snoozed)
- `snoozeAllEvents` with search query + pinned: pinned excluded even when matching search
- `snoozeAllEvents` with FilterState + pinned: pinned excluded even when matching filter
- `snoozeAllCollapsedEvents` with pinned collapsed event: pinned excluded
- `snoozeSelectedEvents` with pinned event in selection: pinned IS snoozed (explicit selection overrides)

**StatusOption.PINNED:**
- `PINNED.matches()` returns true for pinned events, false for unpinned
- `FilterState.matchesStatus()` with PINNED in set: filters correctly
- `FilterState.toDisplayString()` includes "Pinned" when PINNED selected

### Instrumentation Tests

- Pin flag persists across `EventsStorage` close/reopen (Room round-trip with real SQLite)
- Pin toggle from event list updates storage and refreshes UI

## Future Enhancements

1. **Pin/unpin in ViewEventActivityNoRecents** — Add toggle button in the event detail view for discoverability
2. **Notification "Pin" action** — Add a Pin/Unpin action button on individual event notifications
3. **Dismiss All exclusion** — When Dismiss All gets filter support, exclude pinned events (configurable)
4. **Pin count badge** — Show count of pinned events somewhere in the UI (toolbar, filter chip)
5. **`#pin` hashtag auto-pin** — Integration with [#185](https://github.com/williscool/CalendarNotification/issues/185) extended hashtag system: events with `#pin` in title are automatically pinned
6. **Multi-select pin/unpin** — Add Pin/Unpin action to the multi-select toolbar in ActiveEventsFragment

## Notes

### Backward compatibility

Existing events have `flags` bit 3 = 0, so `isPinned = false` by default. No migration, no data fixup. Users start with zero pinned events and pin explicitly.

### Pin state vs. `isNotSpecial`

`EventAlertRecord.isNotSpecial` (checked in `snoozeEvents` base) is for system-level exclusions. Pinning is user-controlled and checked in the caller-specific filter lambdas (`snoozeAllEvents`, `snoozeAllCollapsedEvents`), keeping the two concerns separate.

### Pin icon placement on event card

The pin icon should be near the top-right of the event card, next to or near the mute indicator. When both pinned and muted, both icons show. The pin icon doubles as the toggle button — single tap pins/unpins without navigating away from the list.

## Related Work

- [Milestone 3: Filter Pills](./event_lookahead_milestone3_filter_pills.md) — The filter infrastructure this builds on
- [Snooze All with Filter Pills](./snooze_all_filter_pills.md) — How FilterState integrates with batch snooze
- [Multi-select batch operations](./multi_select_batch_operations.md) — `snoozeSelectedEvents` that should NOT exclude pinned
- [Extended hashtag system (#185)](https://github.com/williscool/CalendarNotification/issues/185) — Future `#pin` auto-pin integration
