# Milestone 2: Pre-Actions Implementation Plan

**Parent Doc:** [events_view_lookahead.md](./events_view_lookahead.md)

## Overview

Milestone 1 delivered read-only upcoming events display. Milestone 2 adds the ability to act on upcoming events before their notifications fire.

**Design Principle:** Any action available on active events should also work on upcoming events.

## Current State (Milestone 1 Complete)

✅ **Already in place:**
- `MonitorEventAlertEntry` with `preMuted` flag and `withPreMuted()` method
- `MonitorAlertEntity` with `flags` column mapping to DB column `i1`
- `UpcomingEventsFragment` with read-only event display
- `UpcomingEventsProvider` enriches alerts from MonitorStorage
- Navigation infrastructure (`MainActivityModern`, bottom nav, fragments)
- DI patterns for testing (`monitorStorageProvider`, `calendarProviderProvider`)
- Robolectric tests for the fragment

## Implementation Order (By Complexity)

| Phase | Action | Complexity | Status |
|-------|--------|------------|--------|
| **6.1** | Pre-Mute | Easiest | ✅ Done |
| **6.2** | Pre-Snooze | Medium | ✅ Done |
| **6.3** | Pre-Dismiss | Hardest | ✅ Done |
| **6.4** | Unsnooze to Upcoming | Easy | ✅ Done |

---

## Phase 6.1: Pre-Mute (EASIEST) - ✅ IMPLEMENTED

**Goal:** Allow users to mark an upcoming event to fire silently (no sound/vibration).

### Why It's Easiest
- Single storage update (MonitorStorage only)
- No data movement between storages
- Flag infrastructure already built (`preMuted`, `withPreMuted()`)
- No alarm changes needed
- Simple toggle (mute/unmute)
- No undo complexity

### Implementation Steps

#### 6.1.1 Add String Resources

```xml
<!-- In strings.xml -->
<string name="pre_mute">Mute when it fires</string>
<string name="pre_unmute">Unmute</string>
<string name="event_will_be_muted">Event will be muted when it fires</string>
<string name="event_unmuted">Event will no longer be muted</string>
```

#### 6.1.2 Update UpcomingEventsFragment - Add Mute Handlers

```kotlin
// In UpcomingEventsFragment.kt

private fun handlePreMute(event: EventAlertRecord) {
    val ctx = context ?: return
    background {
        var success = false
        getMonitorStorage(ctx).use { storage ->
            val alert = storage.getAlert(event.eventId, event.alertTime, event.instanceStartTime)
            if (alert != null) {
                storage.updateAlert(alert.withPreMuted(true))
                success = true
            }
        }
        
        activity?.runOnUiThread {
            if (success) {
                loadEvents() // Refresh to show mute indicator
                view?.let { v ->
                    Snackbar.make(v, R.string.event_will_be_muted, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
}

private fun handleUnPreMute(event: EventAlertRecord) {
    val ctx = context ?: return
    background {
        var success = false
        getMonitorStorage(ctx).use { storage ->
            val alert = storage.getAlert(event.eventId, event.alertTime, event.instanceStartTime)
            if (alert != null) {
                storage.updateAlert(alert.withPreMuted(false))
                success = true
            }
        }
        
        activity?.runOnUiThread {
            if (success) {
                loadEvents()
                view?.let { v ->
                    Snackbar.make(v, R.string.event_unmuted, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
}
```

#### 6.1.3 Update Item Click to Show Action Dialog

```kotlin
// In UpcomingEventsFragment.kt

override fun onItemClick(v: View, position: Int, eventId: Long) {
    DevLog.info(LOG_TAG, "onItemClick, pos=$position, eventId=$eventId")
    
    val ctx = context ?: return
    val event = adapter.getEventAtPosition(position, eventId)
    if (event != null) {
        showUpcomingEventActionDialog(event)
    }
}

private fun showUpcomingEventActionDialog(event: EventAlertRecord) {
    val ctx = context ?: return
    val isMuted = event.isMuted
    
    AlertDialog.Builder(ctx)
        .setTitle(event.title)
        .setItems(arrayOf(
            getString(if (isMuted) R.string.pre_unmute else R.string.pre_mute),
            getString(R.string.view_in_calendar)
            // Snooze and Dismiss will be added in later phases
        )) { _, which ->
            when (which) {
                0 -> if (isMuted) handleUnPreMute(event) else handlePreMute(event)
                1 -> CalendarIntents.viewCalendarEvent(ctx, event)
            }
        }
        .show()
}
```

#### 6.1.4 Update ApplicationController to Apply Pre-Mute on Fire

```kotlin
// In ApplicationController.kt - registerNewEvents()

for ((alert, event) in handledPairs) {
    // Check if this alert was pre-muted
    if (alert.preMuted) {
        event.isMuted = true
        DevLog.info(LOG_TAG, "Event ${event.eventId} was pre-muted, applying mute flag")
    }
    
    tagsManager.parseEventTags(context, settings, event)
    // ... rest of existing logic
}
```

#### 6.1.5 Verify Mute Indicator Display

The `EventListAdapter` should already show mute indicator for events with `isMuted = true`. Verify this works for upcoming events:

```kotlin
// In EventListAdapter - check that mute icon is shown
// The UpcomingEventsProvider already sets flags = EventAlertFlags.IS_MUTED when alert.preMuted
// So event.isMuted should be true for pre-muted events
```

### Testing

#### Robolectric Tests (`MonitorStoragePreMutedRobolectricTest.kt`)

```kotlin
@Test
fun `updateAlert with preMuted true persists flag`()

@Test
fun `new alerts default to preMuted false`()

@Test
fun `preMuted flag survives storage round-trip`()

@Test
fun `different instances of same event have independent preMuted flags`()
```

#### Robolectric UI Tests (`UpcomingEventsFragmentRobolectricTest.kt`)

```kotlin
@Test
fun `clicking event shows action dialog`()

@Test
fun `mute action calls handlePreMute`()

@Test
fun `pre-muted event shows mute indicator in list`()
```

#### Instrumentation Tests (`PreMuteIntegrationTest.kt`)

```kotlin
@Test
fun `pre-muted event fires with mute flag set`()

@Test
fun `preMuted flag cleared after event fires`() // or retained, depends on design

@Test
fun `un-pre-mute clears flag before event fires`()
```

### Files to Create/Modify

| File | Changes |
|------|---------|
| `strings.xml` | Add `pre_mute`, `pre_unmute`, `event_will_be_muted`, `event_unmuted` |
| `UpcomingEventsFragment.kt` | Add `handlePreMute()`, `handleUnPreMute()`, `showUpcomingEventActionDialog()` |
| `ApplicationController.kt` | Check `alert.preMuted` in `registerNewEvents()` |
| `MonitorStoragePreMutedRobolectricTest.kt` (new) | Unit tests for flag storage |
| `UpcomingEventsFragmentRobolectricTest.kt` | Add mute UI tests |
| `PreMuteIntegrationTest.kt` (new) | Integration tests |

---

## Phase 6.2: Pre-Snooze (MEDIUM) - ✅ IMPLEMENTED

**Goal:** Allow users to snooze an upcoming event to a specific time before its notification fires.

### Why It's Medium Complexity
- Requires time picker UI
- Data moves from "conceptually upcoming" to "active snoozed"
- Must mark `wasHandled = true` in MonitorStorage
- Must add to EventsStorage with `snoozedUntil` set
- Must reschedule alarms

### Implementation (Completed)

#### New Files Created

1. **`PreActionActivity.kt`** - Dedicated activity for pre-actions on upcoming events
   - Shows event details (title, time, when alert fires)
   - Displays snooze presets from user settings
   - Custom snooze time picker
   - Mute toggle (also allows pre-mute from this screen)
   - View in Calendar action
   - Named "PreAction" (not "PreSnooze") to support future actions like dismiss

2. **`activity_pre_action.xml`** - Layout for PreActionActivity

#### Files Modified

- **`AndroidManifest.xml`** - Added PreActionActivity registration
- **`Consts.kt`** - Added intent keys for passing event data
- **`strings.xml`** - Added `alert_fires_at`, `error` strings
- **`UpcomingEventsFragment.kt`** - Updated to launch PreActionActivity

#### How It Works

1. User taps event in Upcoming tab → action dialog appears
2. User selects "Snooze until…" → `PreActionActivity` launches
3. User sees full snooze UI with:
   - Event title and time info
   - "Alert fires at X:XX" indicator
   - Snooze presets (15m, 1h, 4h, etc.)
   - Custom time option
   - Mute toggle and View in Calendar
4. User selects snooze duration:
   - Alert marked as `wasHandled = true` in MonitorStorage
   - Event added to EventsStorage with `snoozedUntil` set
   - Alarms rescheduled
   - Activity finishes with toast confirmation

#### Code Flow in PreActionActivity

```kotlin
private fun executePreSnooze(snoozeUntil: Long) {
    background {
        // 1. Mark as handled in MonitorStorage
        MonitorStorage(this).use { storage ->
            val alert = storage.getAlert(eventId, alertTime, instanceStartTime)
            if (alert != null) {
                storage.updateAlert(alert.copy(wasHandled = true))
            }
        }
        
        // 2. Add to EventsStorage as snoozed
        val snoozedEvent = event.copy(
            snoozedUntil = snoozeUntil,
            lastStatusChangeTime = clock.currentTimeMillis()
        )
        EventsStorage(ctx).classCustomUse { db ->
            db.addEvent(snoozedEvent)
        }
        
        // 3. Reschedule alarms
        ApplicationController.afterCalendarEventFired(ctx)
        
        activity?.runOnUiThread {
            loadEvents() // Event disappears from Upcoming
            view?.let { v ->
                Snackbar.make(v, R.string.event_snoozed, Snackbar.LENGTH_SHORT).show()
            }
        }
    }
}
```

### Testing

#### Robolectric UI Tests

```kotlin
@Test
fun `snooze action shows time picker`()

@Test  
fun `selecting snooze time calls handlePreSnooze`()
```

#### Instrumentation Tests (`PreSnoozeIntegrationTest.kt`)

```kotlin
@Test
fun `pre-snoozed event appears in EventsStorage`()

@Test
fun `pre-snoozed event marked as handled in MonitorStorage`()

@Test
fun `pre-snoozed event disappears from Upcoming tab`()

@Test
fun `pre-snoozed event appears in Active tab as snoozed`()
```

### Files to Create/Modify

| File | Changes |
|------|---------|
| `strings.xml` | Add `pre_snooze`, `event_snoozed` |
| `UpcomingEventsFragment.kt` | Add `showPreSnoozePicker()`, `handlePreSnooze()` |
| `UpcomingEventsFragmentRobolectricTest.kt` | Add snooze UI tests |
| `PreSnoozeIntegrationTest.kt` (new) | Integration tests |

---

## Phase 6.3: Pre-Dismiss (HARDEST) - ✅ IMPLEMENTED

**Goal:** Allow users to dismiss an upcoming event entirely—goes straight to Dismissed storage.

### Implementation (Completed)

Added dismiss functionality to `PreActionActivity`:

1. **New UI**: Added "Dismiss" button to `activity_pre_action.xml`
2. **Logic in `executePreDismiss()`**:
   - Marks alert as `wasHandled = true` in MonitorStorage
   - Adds event to DismissedEventsStorage with `EventDismissType.ManuallyDismissedFromActivity`
   - Dismisses native calendar alert via `CalendarProvider.dismissNativeEventAlert()`
   - Shows toast and finishes activity

**Note**: Undo functionality was descoped for initial implementation. Event goes directly to Dismissed tab.

---

### Original Plan (for reference)

### Why It's Hardest
- Two storage operations that must be coordinated
- Must support undo (reverse both operations)
- Should dismiss native calendar alert too
- Most state to track and manage

### Implementation Steps

#### 6.3.1 Add String Resources

```xml
<string name="pre_dismiss">Dismiss</string>
<string name="event_dismissed">Event dismissed</string>
```

#### 6.3.2 Add Dismiss Option to Action Dialog

```kotlin
// Update showUpcomingEventActionDialog()
.setItems(arrayOf(
    getString(R.string.pre_snooze),
    getString(if (isMuted) R.string.pre_unmute else R.string.pre_mute),
    getString(R.string.pre_dismiss),  // NEW
    getString(R.string.view_in_calendar)
)) { _, which ->
    when (which) {
        0 -> showPreSnoozePicker(event)
        1 -> if (isMuted) handleUnPreMute(event) else handlePreMute(event)
        2 -> handlePreDismiss(event)  // NEW
        3 -> CalendarIntents.viewCalendarEvent(ctx, event)
    }
}
```

#### 6.3.3 Implement handlePreDismiss

```kotlin
private fun handlePreDismiss(event: EventAlertRecord) {
    val ctx = context ?: return
    background {
        // 1. Mark as handled in MonitorStorage
        getMonitorStorage(ctx).use { storage ->
            val alert = storage.getAlert(event.eventId, event.alertTime, event.instanceStartTime)
            if (alert != null) {
                storage.updateAlert(alert.copy(wasHandled = true))
            }
        }
        
        // 2. Add to DismissedEventsStorage
        DismissedEventsStorage(ctx).use { dismissedDb ->
            dismissedDb.addEvent(EventDismissType.ManuallyDismissedFromActivity, event)
        }
        
        // 3. Dismiss native calendar alert
        CalendarProvider.dismissNativeEventAlert(ctx, event.eventId)
        
        activity?.runOnUiThread {
            loadEvents() // Event disappears from Upcoming
            view?.let { v ->
                Snackbar.make(v, R.string.event_dismissed, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo) {
                        undoPreDismiss(event)
                    }
                    .show()
            }
        }
    }
}
```

#### 6.3.4 Implement undoPreDismiss

```kotlin
private fun undoPreDismiss(event: EventAlertRecord) {
    val ctx = context ?: return
    background {
        // 1. Remove from DismissedEventsStorage
        DismissedEventsStorage(ctx).use { dismissedDb ->
            dismissedDb.deleteEvent(event)
        }
        
        // 2. Unmark as handled in MonitorStorage
        getMonitorStorage(ctx).use { storage ->
            val alert = storage.getAlert(event.eventId, event.alertTime, event.instanceStartTime)
            if (alert != null) {
                storage.updateAlert(alert.copy(wasHandled = false))
            }
        }
        
        activity?.runOnUiThread {
            loadEvents() // Event reappears in Upcoming
        }
    }
}
```

#### 6.3.5 Optional: Enable Swipe-to-Dismiss

```kotlin
// In UpcomingEventsFragment.onViewCreated()
// Change from:
adapter = EventListAdapter(requireContext(), this, swipeEnabled = false)
// To:
adapter = EventListAdapter(requireContext(), this, swipeEnabled = true)

// And implement onItemDismiss to call handlePreDismiss
override fun onItemDismiss(v: View, position: Int, eventId: Long) {
    val event = adapter.getEventAtPosition(position, eventId)
    if (event != null) {
        handlePreDismiss(event)
    }
}
```

### Testing

#### Robolectric UI Tests

```kotlin
@Test
fun `dismiss action removes event from list`()

@Test
fun `undo snackbar appears after dismiss`()

@Test
fun `clicking undo restores event to list`()
```

#### Instrumentation Tests (`PreDismissIntegrationTest.kt`)

```kotlin
@Test
fun `pre-dismissed event goes to DismissedEventsStorage`()

@Test
fun `pre-dismissed event marked as handled in MonitorStorage`()

@Test
fun `pre-dismissed event does not fire notification`()

@Test
fun `undo pre-dismiss restores event to upcoming`()

@Test
fun `undo pre-dismiss sets wasHandled back to false`()
```

### Files to Create/Modify

| File | Changes |
|------|---------|
| `strings.xml` | Add `pre_dismiss`, `event_dismissed`, `view_in_calendar` |
| `UpcomingEventsFragment.kt` | Add `handlePreDismiss()`, `undoPreDismiss()`, update `onItemDismiss()` |
| `UpcomingEventsFragmentRobolectricTest.kt` | Add dismiss/undo UI tests |
| `PreDismissIntegrationTest.kt` (new) | Integration tests |

---

## Phase 6.4: Unsnooze to Upcoming (EASY)

**Goal:** Allow users to "unsnooze" a pre-snoozed event back to the Upcoming tab, but only if the original alert time hasn't passed yet.

### Why It's Easy
- Reverse of pre-snooze logic (already understood)
- Simple condition: `alertTime > currentTimeMillis()`
- No new UI patterns needed (add option to existing snooze/event detail view)

### Condition for Showing Option
Only show "Unsnooze (back to upcoming)" when:
```kotlin
event.alertTime > clock.currentTimeMillis()
```

If the original alert time has passed, the event can't go "back" to upcoming—it would have fired anyway.

### Implementation Steps

#### 6.4.1 Add String Resources

```xml
<string name="unsnooze_to_upcoming">Unsnooze (back to upcoming)</string>
<string name="event_restored_to_upcoming">Event restored to upcoming</string>
```

#### 6.4.2 Add Option to Active Event Actions

In the snooze detail view or Active event action menu, add the option conditionally:

```kotlin
// Only show for events where the original alert time hasn't passed
if (event.alertTime > clock.currentTimeMillis()) {
    // Add "Unsnooze (back to upcoming)" option
}
```

#### 6.4.3 Implement handleUnsnoozeToUpcoming

```kotlin
private fun handleUnsnoozeToUpcoming(event: EventAlertRecord) {
    val ctx = context ?: return
    background {
        // 1. Delete from EventsStorage
        EventsStorage(ctx).use { db ->
            db.deleteEvent(event.eventId, event.instanceStartTime)
        }
        
        // 2. Unmark as handled in MonitorStorage
        MonitorStorage(ctx).use { storage ->
            val alert = storage.getAlert(event.eventId, event.alertTime, event.instanceStartTime)
            if (alert != null) {
                storage.updateAlert(alert.copy(wasHandled = false))
            }
        }
        
        // 3. Reschedule alarms
        ApplicationController.afterCalendarEventFired(ctx)
        
        activity?.runOnUiThread {
            loadEvents() // Event disappears from Active, reappears in Upcoming
            view?.let { v ->
                Snackbar.make(v, R.string.event_restored_to_upcoming, Snackbar.LENGTH_SHORT).show()
            }
        }
    }
}
```

### Testing

#### Robolectric UI Tests

```kotlin
@Test
fun `unsnooze option shown for events before alert time`()

@Test
fun `unsnooze option hidden for events after alert time`()
```

#### Instrumentation Tests (`UnsnoozeToUpcomingIntegrationTest.kt`)

```kotlin
@Test
fun `unsnooze removes event from EventsStorage`()

@Test
fun `unsnooze sets wasHandled false in MonitorStorage`()

@Test
fun `unsnooze event reappears in Upcoming tab`()

@Test
fun `unsnooze not available after alert time passes`()
```

### Files to Create/Modify

| File | Changes |
|------|---------|
| `strings.xml` | Add `unsnooze_to_upcoming`, `event_restored_to_upcoming` |
| `ActiveEventsFragment.kt` or `SnoozeActivity.kt` | Add unsnooze option conditionally |
| `UnsnoozeToUpcomingIntegrationTest.kt` (new) | Integration tests |

---

## Complete File Summary

### New Files

| File | Phase | Purpose |
|------|-------|---------|
| `MonitorStoragePreMutedRobolectricTest.kt` | 6.1 | Unit tests for preMuted flag |
| `PreMuteIntegrationTest.kt` | 6.1 | Integration tests for pre-mute flow |
| `PreSnoozeIntegrationTest.kt` | 6.2 | Integration tests for pre-snooze flow (TODO: rename to PreActionIntegrationTest) |
| `PreDismissIntegrationTest.kt` | 6.3 | Integration tests for pre-dismiss flow |
| `UnsnoozeToUpcomingIntegrationTest.kt` | 6.4 | Integration tests for unsnooze flow |

### Modified Files

| File | Phases | Changes |
|------|--------|---------|
| `strings.xml` | All | Add all new strings |
| `UpcomingEventsFragment.kt` | All | Add action dialog, all handlers |
| `ApplicationController.kt` | 6.1 | Check `preMuted` in `registerNewEvents()` |
| `UpcomingEventsFragmentRobolectricTest.kt` | All | Add UI tests for all actions |

---

## Acceptance Criteria

### Phase 6.1: Pre-Mute - ✅ IMPLEMENTED
- [x] Tapping upcoming event shows PreActionActivity
- [x] "Mute when it fires" option sets preMuted flag
- [x] Pre-muted events show mute indicator in list
- [x] When pre-muted event fires, it's muted (no sound/vibration)
- [x] "Unmute" option clears preMuted flag
- [ ] All tests pass

### Phase 6.2: Pre-Snooze - ✅ IMPLEMENTED
- [x] Snooze presets shown in PreActionActivity
- [x] Selecting snooze time moves event to Active tab (snoozed)
- [x] Event disappears from Upcoming tab
- [x] MonitorStorage alert marked as handled
- [ ] All tests pass

### Phase 6.3: Pre-Dismiss
- [x] "Dismiss" removes event from Upcoming
- [x] Event goes to Dismissed storage
- [x] Smart restore: if alertTime > now → restore to Upcoming; else → restore to Active
- [x] Event does not fire notification after dismiss (wasHandled=true)
- [ ] All tests pass

### Phase 6.4: Unsnooze to Upcoming - ✅ IMPLEMENTED
- [x] "Back to upcoming" option appears for snoozed events before alert time
- [x] Option hidden for events past their original alert time
- [x] Unsnooze removes event from Active/EventsStorage
- [x] Unsnooze sets wasHandled=false in MonitorStorage
- [x] Event reappears in Upcoming tab
- [ ] All tests pass

---

## Questions Resolved

| Question | Decision |
|----------|----------|
| Complexity order? | Mute → Snooze → Dismiss → Unsnooze (easiest to hardest) |
| Snooze UI? | Reuse existing snooze presets in simple dialog |
| Swipe for upcoming? | Start with dialog-only, can add swipe in 6.3 |
| Pre-mute indicator? | Verify EventListAdapter already handles `isMuted` |
| Unsnooze scope? | Added as Phase 6.4, only for events before original alert time |
| Pre-dismiss restore behavior? | Smart restore: if alertTime > now → Upcoming; else → Active |