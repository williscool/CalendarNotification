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

- **Pin/unpin in ViewEventActivity** — Deferred; the detail view is out of scope for this iteration.
- **Pin-related filter pill on Dismissed tab** — Dismissed tab has no Status chip today; stays that way.
- **Notification action button for pin/unpin** — Adding a "Pin" action on individual notifications is future work.
- **Dismiss All exclusion** — Dismiss All doesn't support any filtering today ([#215 context](./snooze_all_filter_pills.md)). Pinning can be added there when Dismiss All gets filter support.
- **`#pin` hashtag integration** — [#185](https://github.com/williscool/CalendarNotification/issues/185) extended hashtag system is a separate feature.
- **Pin state sync to cloud** — Pin state lives in the existing `flags` column which already syncs via cr-sqlite if enabled.

## Key Decisions Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Storage mechanism | **`IS_PINNED` flag on existing `flags` bitmask** | No DB migration, consistent with `isMuted`/`isTask`/`isAlarm` pattern |
| Batch exclusion scope | **`snoozeAllEvents` + `snoozeAllCollapsedEvents` + `muteAllVisibleEvents`** | Covers UI, notification, and mute-all paths; `snoozeSelectedEvents` NOT excluded (explicit selection overrides pin) |
| Pin/unpin UI | **Icon toggle on event card + overflow menu "Pin All"** | Icon for individual toggle (same pattern as mute indicator); menu item for batch pin (same pattern as "Mute all visible") |
| Filter integration | **Add `PINNED` to `StatusOption` on Active + Upcoming tabs** | Uses existing filter pill infrastructure; OR logic with other status options |
| Event count in Snooze All | **Show "excluding N pinned" in confirmation** | Transparent UX — user sees exactly what's happening |

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

### Pin icon layout: coexisting with mute, task, alarm icons

The event card layout (`event_card_compact.xml`) has a horizontal `LinearLayout` aligned to the bottom-right that already holds up to 3 icons: mute, task, alarm. Each is `wrap_content` with `scaleX/Y="0.75"` and `visibility="gone"` by default.

Adding pin as a 4th icon in this same LinearLayout works cleanly:
- Icons flow horizontally, each ~18dp effective width at 0.75 scale
- Worst case (all 4 visible): ~72dp total — fits comfortably in the card width
- Pin icon goes **first** in the row (leftmost) since it's the most user-relevant status indicator
- Order: pin, mute, task, alarm

When an event is both pinned and muted, both icons show side by side — no special layout handling needed.

### Pin/unpin mechanisms: icon toggle + overflow menu

**Individual toggle:** The pin icon on the event card acts as both indicator and toggle — single tap pins/unpins. Same interaction pattern the user already understands from the list.

**Batch pin via overflow menu:** Following the exact pattern of "Mute all visible" (`R.id.action_mute_all`):
- Add "Pin all visible" (`R.id.action_pin_all`) to `main.xml` overflow menu
- Show only on Active tab (same as mute all), only when there are unpinned events
- Confirmation dialog → `ApplicationController.pinAllVisibleEvents()`
- Also add "Unpin all visible" (`R.id.action_unpin_all`) for the reverse operation
- Both skip `isNotSpecial`/task events to match mute behavior

A future phase can add pin/unpin to ViewEventActivityNoRecents for discoverability, but the list icon + overflow menu is sufficient for MVP.

### Filter pill: PINNED as StatusOption on Active + Upcoming

`StatusOption` is multi-select with OR logic. Adding `PINNED` means:
- Select only PINNED → shows pinned events
- Select PINNED + SNOOZED → shows events that are pinned OR snoozed
- Select nothing → shows all events (existing behavior)

This integrates cleanly with the existing `FilterState.matchesStatus()` without special-casing.

**Per-tab availability:** The status filter popup in `showStatusFilterPopup()` already gates options by tab (Upcoming excludes SNOOZED/ACTIVE). PINNED is relevant on both tabs:
- **Active tab:** All options including PINNED
- **Upcoming tab:** Muted, Recurring, PINNED (Snoozed/Active still excluded — don't apply to unfired events)
- **Dismissed tab:** Still no Status chip (no change)

## Implementation Plan

### Phase 1: Storage — `IS_PINNED` Flag

**Goal:** Add the pinned flag to the domain model.

**Changes:**
- `EventAlertRecord.kt`: Add `IS_PINNED = 8L` to `EventAlertFlags`, add `isPinned` computed property (same pattern as `isMuted`)
- No changes to `EventAlertEntity.kt` or Room schema — `flags`/`i1` column already exists and maps correctly

**Verification:** Unit test that `isPinned` reads/writes bit 3 correctly, doesn't interfere with other flags, and round-trips through `EventAlertEntity`.

### Phase 2: Batch Exclusion — Snooze/Mute All Skips Pinned

**Goal:** Pinned events are excluded from all batch operations.

**Changes:**
- `ApplicationController.kt`: Add `!event.isPinned` to the filter in:
  - `snoozeAllEvents()` — before search/filterState checks
  - `snoozeAllCollapsedEvents()` — alongside the existing displayStatus check
  - `muteAllVisibleEvents()` — alongside the existing snoozedUntil/isNotSpecial/isTask checks
- `snoozeSelectedEvents()` — **no change** (explicit selection overrides pin)

**Verification:** Tests confirming:
- Pinned events survive snooze-all while unpinned events get snoozed
- Pinned events survive snooze-all-collapsed
- Pinned events survive mute-all
- Pinned events ARE snoozed when explicitly selected via `snoozeSelectedEvents`
- Notification-path snooze-all (no search/filter) still excludes pinned

### Phase 3: Event List UI — Pin Indicator + Toggle

**Goal:** Users can see which events are pinned and toggle pin state from the event list.

**Changes:**
- `event_card_compact.xml`: Add `imageview_is_pinned_indicator` as first child in the icon row LinearLayout (before mute, task, alarm). Same sizing pattern: `wrap_content`, `scaleX/Y="0.75"`, `visibility="gone"`, Material `ic_push_pin` drawable.
- `EventListAdapter.kt`:
  - Add `pinImage` ViewHolder field for `imageview_is_pinned_indicator`
  - Set visibility based on `event.isPinned` (same pattern as `muteImage` / `event.isMuted`)
  - Set click listener on pin icon to invoke a callback for toggling
- `ActiveEventsFragment.kt`: Handle pin toggle callback — load event from `EventsStorage`, toggle `isPinned`, save back, refresh list
- Pin icon drawable: Material `ic_push_pin_24dp` tinted with `@color/primary_text`

**Verification:** Manual testing — pin icon appears for pinned events, tapping toggles state, list refreshes. Both pin + mute icons visible simultaneously when applicable.

### Phase 4: Overflow Menu — Pin All / Unpin All

**Goal:** Batch pin/unpin from the overflow menu, same pattern as "Mute all visible".

**Changes:**
- `main.xml` (menu): Add `action_pin_all` and `action_unpin_all` menu items (same `orderInCategory` region as mute, `showAsAction="never"`, `visible="false"`)
- `MainActivityModern.kt`:
  - `onCreateOptionsMenu()`: Show `action_pin_all` on Active tab when there are unpinned visible events; show `action_unpin_all` when there are pinned events. Follow the exact pattern of `action_mute_all` visibility gating.
  - `onOptionsItemSelected()`: Wire up `onPinAll()` / `onUnpinAll()` with confirmation dialogs (same pattern as `onMuteAll()`)
  - `doPinAll()` / `doUnpinAll()`: Call `ApplicationController.pinAllVisibleEvents()` / `unpinAllVisibleEvents()`
- `ApplicationController.kt`: Add `pinAllVisibleEvents()` and `unpinAllVisibleEvents()` following the exact pattern of `muteAllVisibleEvents()` — load events, filter to visible non-special, set `isPinned`, update, notify
- `SearchableFragment.kt`: Add `supportsPinAll()` and `anyForPinAll()` / `anyForUnpinAll()` (return true for Active tab, false for others)
- `strings.xml`: Add `pin_all`, `unpin_all`, `pin_all_events_question`, `unpin_all_events_question`

**Verification:** Pin All sets `isPinned` on all visible non-special events. Unpin All clears it. Menu items appear/hide correctly based on pin state.

### Phase 5: Filter Pill — PINNED Status Option

**Goal:** Users can filter the Active and Upcoming event lists to show only pinned events.

**Changes:**
- `FilterState.kt` / `StatusOption`: Add `PINNED` enum value with `matches` = `event.isPinned`
- `FilterState.toDisplayString()`: Add display string for PINNED
- `MainActivityModern.kt`:
  - `showStatusFilterPopup()`: PINNED appears on both Active and Upcoming tabs (it is NOT in the Upcoming exclusion list alongside SNOOZED/ACTIVE)
  - `StatusOption.toDisplayString()`: Add PINNED case
- `strings.xml`: Add `filter_status_pinned` string resource

**Verification:** Selecting PINNED in status filter shows only pinned events on both Active and Upcoming tabs. Combining with other status options works (OR logic).

### Phase 6: Snooze All Confirmation — "Excluding N Pinned"

**Goal:** The SnoozeAllActivity confirmation clearly communicates that pinned events are excluded.

**Changes:**
- `MainActivityModern.kt`: When launching SnoozeAllActivity, pass an additional `INTENT_PINNED_EVENT_COUNT` extra with the count of pinned events in the current view
- `Consts.kt`: Add `INTENT_PINNED_EVENT_COUNT` constant
- `SnoozeAllActivity.kt`:
  - Read `pinnedCount` from intent
  - In `getConfirmationMessage()`: When `pinnedCount > 0`, append "\n(N pinned event(s) excluded)" to the confirmation message
  - In the count display: Show snoozable count (total displayed minus pinned) as the primary number
- `strings.xml`: Add `pinned_events_excluded` plurals string — e.g. "(%1$d pinned event excluded)" / "(%1$d pinned events excluded)"

**Example confirmation messages:**
- No pinned: "Snooze all notifications?" (unchanged)
- 2 pinned: "Snooze all notifications?\n(2 pinned events excluded)"
- With filters + pinned: "Snooze all events matching Snoozed filter?\n(1 pinned event excluded)"

**Verification:** Confirmation dialog shows correct pinned exclusion count. Count matches reality.

### Milestone Checkpoint

After Phase 6, the complete pinning feature works end-to-end:
- Users can pin/unpin individual events from the event list icon
- Users can batch pin/unpin via overflow menu
- Pinned events are visually indicated with a pin icon
- Snooze All / Change All / Snooze All Collapsed / Mute All skip pinned events
- Users can filter to see only pinned events (Active + Upcoming tabs)
- Snooze All confirmation transparently shows pinned exclusion count

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
| `ApplicationController.kt` | 2, 4 | Add `!event.isPinned` to batch snooze/mute filters; add `pinAllVisibleEvents()` / `unpinAllVisibleEvents()` |
| `event_card_compact.xml` | 3 | Add `imageview_is_pinned_indicator` as first child in icon row |
| `EventListAdapter.kt` | 3 | Pin indicator visibility + tap handler |
| `ActiveEventsFragment.kt` | 3 | Pin toggle callback handler |
| `main.xml` (menu) | 4 | Add `action_pin_all` and `action_unpin_all` items |
| `MainActivityModern.kt` | 4, 5 | Pin all/unpin all menu handling; PINNED in status filter popup; pass pinned count to SnoozeAllActivity |
| `SearchableFragment.kt` | 4 | Add `supportsPinAll()`, `anyForPinAll()`, `anyForUnpinAll()` |
| `FilterState.kt` / `StatusOption` | 5 | Add `PINNED` enum value |
| `Consts.kt` | 6 | Add `INTENT_PINNED_EVENT_COUNT` |
| `SnoozeAllActivity.kt` | 6 | Read pinned count, show "(N pinned excluded)" in confirmation |
| `strings.xml` | 3, 4, 5, 6 | Pin-related string resources |

## Testing Plan

### Unit Tests (Robolectric)

**EventAlertRecord pin flag:**
- `isPinned` defaults to false for new records
- Setting `isPinned = true` sets bit 3 in flags
- `isPinned` does not interfere with `isMuted`, `isTask`, `isAlarm` (all 4 can be true simultaneously)
- Flag round-trips through `EventAlertEntity.fromRecord()` / `toRecord()`

**Batch exclusion (`ApplicationController`):**
- `snoozeAllEvents` with mix of pinned/unpinned: only unpinned get snoozed
- `snoozeAllEvents` with all pinned: returns null (no events snoozed)
- `snoozeAllEvents` with search query + pinned: pinned excluded even when matching search
- `snoozeAllEvents` with FilterState + pinned: pinned excluded even when matching filter
- `snoozeAllCollapsedEvents` with pinned collapsed event: pinned excluded
- `muteAllVisibleEvents` with pinned visible event: pinned excluded from mute
- `snoozeSelectedEvents` with pinned event in selection: pinned IS snoozed (explicit selection overrides)

**Batch pin/unpin (`ApplicationController`):**
- `pinAllVisibleEvents` pins all visible non-special non-task events
- `pinAllVisibleEvents` skips already-pinned events (idempotent)
- `unpinAllVisibleEvents` unpins all pinned events
- Neither operation affects snoozed events (matching mute-all behavior)

**StatusOption.PINNED:**
- `PINNED.matches()` returns true for pinned events, false for unpinned
- `FilterState.matchesStatus()` with PINNED in set: filters correctly
- `FilterState.toDisplayString()` includes "Pinned" when PINNED selected
- `FilterState.toBundle()` / `fromBundle()` round-trips correctly with PINNED in statusFilters

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
7. **Pin indicator in Upcoming tab** — Show pin icon on upcoming events (read-only, since upcoming events use MonitorStorage not EventsStorage)

## Notes

### Backward compatibility

Existing events have `flags` bit 3 = 0, so `isPinned = false` by default. No migration, no data fixup. Users start with zero pinned events and pin explicitly.

### Pin state vs. `isNotSpecial`

`EventAlertRecord.isNotSpecial` (checked in `snoozeEvents` base) is for system-level exclusions. Pinning is user-controlled and checked in the caller-specific filter lambdas (`snoozeAllEvents`, `snoozeAllCollapsedEvents`), keeping the two concerns separate.

### Pin icon placement on event card

The icon row LinearLayout in `event_card_compact.xml` already handles multiple icons (mute, task, alarm) as `wrap_content` with `visibility="gone"`. Adding pin as the first child means the icon order is: pin, mute, task, alarm. At `scaleX/Y="0.75"` each icon is ~18dp wide, so even with all 4 visible (~72dp) there's no overflow concern.

### Pin All / Unpin All menu pattern

Follows the exact pattern established by "Mute all visible":
- Menu item defined in `main.xml` with `visible="false"`
- `onCreateOptionsMenu()` gates visibility based on fragment support + whether there are eligible events
- Click handler shows confirmation `AlertDialog`, then calls `ApplicationController` method
- Fragment exposes `supportsPinAll()` / `anyForPinAll()` / `anyForUnpinAll()` through `SearchableFragment` interface

## Related Work

- [Milestone 3: Filter Pills](./event_lookahead_milestone3_filter_pills.md) — The filter infrastructure this builds on
- [Snooze All with Filter Pills](./snooze_all_filter_pills.md) — How FilterState integrates with batch snooze
- [Multi-select batch operations](./multi_select_batch_operations.md) — `snoozeSelectedEvents` that should NOT exclude pinned
- [Extended hashtag system (#185)](https://github.com/williscool/CalendarNotification/issues/185) — Future `#pin` auto-pin integration
