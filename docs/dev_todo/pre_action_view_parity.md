# Pre-Action View Parity with Main Event View

**Issue:** [#249](https://github.com/williscool/CalendarNotification/issues/249)

## Problem

The `PreActionActivity` (upcoming events detail view) has several UI/UX discrepancies compared to `ViewEventActivityNoRecents` (main event detail view). The actions available and their placement differ in ways that are confusing for users who switch between the two views.

## Current State Comparison

| Feature | Main View (`ViewEventActivityNoRecents`) | Upcoming View (`PreActionActivity`) |
|---|---|---|
| **Edit/pencil FAB** | FAB opens `EditEventActivity` or calendar | **Missing** - no FAB at all |
| **Open in Calendar** | In 3-dot menu | Button in main content area ("View in Calendar") |
| **Mute/Unmute** | In 3-dot menu | Button in main content area (toggle) |
| **Dismiss** | In 3-dot menu | In 3-dot menu ("Pre-Dismiss") |
| **Snooze presets** | `settings.snoozePresets` via 6 fixed XML slots | `settings.snoozePresets` via dynamic list (filtered positive-only) |

## Changes Required

### 1. Add Edit/Calendar FAB

**Files:**
- `activity_pre_action.xml` - add `FloatingActionButton`
- `PreActionActivity.kt` - wire up FAB click handler

Add a `FloatingActionButton` to `PreActionActivity` matching the main view's behavior:
- Non-repeating events on writable calendars: open `EditEventActivity`
- Repeating events or `alwaysUseExternalEditor`: open in system calendar app
- Read-only calendars: hide the FAB
- Tint with event color (matching main view behavior)

### 2. Move Mute to 3-dot Menu

**Files:**
- `pre_action.xml` (menu) - add mute/unmute items
- `activity_pre_action.xml` (layout) - remove `pre_action_mute_toggle` button
- `PreActionActivity.kt` - move mute logic to menu handler, add visibility toggling

The 3-dot menu should gain mute/unmute items (hidden/shown based on current mute state), matching the main view's `snooze.xml` menu pattern. Remove the mute toggle button from the main content area.

### 3. Move "View in Calendar" to 3-dot Menu

**Files:**
- `pre_action.xml` (menu) - add "Open in Calendar" item
- `activity_pre_action.xml` (layout) - remove `pre_action_view_calendar` button
- `PreActionActivity.kt` - move calendar-open logic to menu handler

Since we're adding a FAB for edit/calendar (change 1), the separate "View in Calendar" button in the content area becomes redundant. Move it to the 3-dot menu for consistency with the main view, where "Open in Calendar" is a menu item.

### 4. (Verify) Snooze Presets Consistency

Both views already use `settings.snoozePresets` from preferences. The pre-action view filters to positive-only values, which is correct since negative presets ("X min before event") don't apply to events that haven't fired yet.

**Differences that remain acceptable:**
- Dynamic list vs 6 fixed slots (UI layout difference, not behavioral)
- No quiet-hours indicators in pre-action view (quiet hours is deprecated per `docs/dev_todo/deprecated_features.md`)

**Action:** Verify at runtime that both views show the same preset values for the same settings. No code change expected, but worth manual testing.

## Updated 3-Dot Menu (post-changes)

After changes, `pre_action.xml` should contain:
- Pre-Dismiss (existing)
- Pre-Mute / Pre-Unmute (new, toggled by state)
- Open in Calendar (new)

## Implementation Order

1. **Add FAB** - layout + wiring (most visible change)
2. **Move mute to menu** - menu XML + activity logic
3. **Move calendar to menu** - menu XML + activity logic
4. **Remove old buttons** from layout (part of steps 2-3)
5. **Manual test** - verify both views feel consistent

## Out of Scope

- "Change event time" / move buttons (will be removed separately per user preference)
- Quiet-hours indicators (deprecated feature)
- Snooze preset UI layout difference (6 fixed slots vs dynamic list is fine)

## Key Files

| File | Path |
|---|---|
| PreActionActivity | `android/app/src/main/java/com/github/quarck/calnotify/ui/PreActionActivity.kt` |
| ViewEventActivityNoRecents | `android/app/src/main/java/com/github/quarck/calnotify/ui/ViewEventActivityNoRecents.kt` |
| Pre-action layout | `android/app/src/main/res/layout/activity_pre_action.xml` |
| Main view layout | `android/app/src/main/res/layout/activity_view.xml` |
| Pre-action menu | `android/app/src/main/res/menu/pre_action.xml` |
| Main view menu | `android/app/src/main/res/menu/snooze.xml` |
