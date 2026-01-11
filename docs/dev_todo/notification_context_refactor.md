# RFC: NotificationContext Refactor

## Status: Complete

## Problem

The notification system has significant combinatorial complexity:

**Input variables:**
- `collapseEverything`: 2 states (true/false)
- Event count bucket: 3 states (≤ max, > max but < 50, ≥ 50)
- `hasNewTriggeringEvent`: 2 states (new events vs only already-tracked)
- `playReminderSound`: 2 states (periodic reminder vs not)
- Muted aggregate: 2 states (all muted, any unmuted)
- `hasAlarms`: 2 states

**Theoretical combinations:** 2 × 3 × 2 × 2 × 2 × 2 = **96 states**

After constraint reduction (e.g., `allMuted` implies `hasAlarms=false`), there are ~25-30 valid states. However:

1. **Constraints are implicit** - scattered across helper functions
2. **State computed multiple times** - `hasAlarms`, `allMuted` recalculated in different places
3. **Testing covers scenarios, not invariants** - we test ~30 specific cases but don't prove properties

Additionally, `EventNotificationManager` has significant code duplication:
- 3× `hasAlarms` calculation (`events.any { it.isAlarm && !it.isTask && !it.isMuted }`)
- 2× `allMuted` calculation (`events.all { it.isMuted }`)
- 2× nearly identical `shouldBeQuiet` logic blocks (~30 lines each)

## Proposed Solution

### Phase 1: NotificationContext Data Class ✅ COMPLETE

Create a single data class that:
- Computes all derived properties once from events list
- Enforces constraints in `init {}` block (impossible states throw)
- Provides decision properties (`collapsedChannel`, `isReminder`)

```kotlin
data class NotificationContext(
    // Derived from events
    val eventCount: Int,
    val hasAlarms: Boolean,        // any(isAlarm && !isTask && !isMuted)
    val allMuted: Boolean,         // all(isMuted)
    val hasNewTriggeringEvent: Boolean, // any(Hidden && snoozedUntil==0 && !muted)
    
    // From settings/caller
    val mode: NotificationMode,
    val playReminderSound: Boolean,
    val isQuietPeriodActive: Boolean = false
) {
    init {
        require(!allMuted || !hasAlarms) { 
            "Invariant violated: allMuted=true but hasAlarms=true" 
        }
        require(!allMuted || !hasNewTriggeringEvent) { 
            "Invariant violated: allMuted=true but hasNewTriggeringEvent=true" 
        }
    }
    
    val isReminder: Boolean 
        get() = playReminderSound || !hasNewTriggeringEvent
    
    val collapsedChannel: ChannelCategory
        get() = when {
            allMuted -> ChannelCategory.SILENT
            isReminder && hasAlarms -> ChannelCategory.ALARM_REMINDERS
            isReminder -> ChannelCategory.REMINDERS
            hasAlarms -> ChannelCategory.ALARM
            else -> ChannelCategory.EVENTS
        }
    
    companion object {
        fun fromEvents(
            events: List<EventAlertRecord>,
            mode: NotificationMode,
            playReminderSound: Boolean,
            isQuietPeriodActive: Boolean = false
        ): NotificationContext { /* ... */ }
    }
}

enum class ChannelCategory {
    EVENTS,           // New from calendar (DEFAULT channel)
    ALARM,            // New alarm
    REMINDERS,        // Already tracked
    ALARM_REMINDERS,  // Already tracked alarm
    SILENT;           // Muted
    
    fun toChannelId(): String = /* maps to NotificationChannels.CHANNEL_ID_* */
}
```

### Phase 2: Invariant Tests ✅ COMPLETE

Instead of testing ~30 scenarios individually, test properties that must ALWAYS hold:

| Invariant | Description |
|-----------|-------------|
| 1 | `allMuted` context always produces `SILENT` channel |
| 2 | New triggering events use EVENTS/ALARM (not REMINDERS) |
| 3 | `hasAlarms` switches to alarm channel variant |
| 4 | Impossible states (allMuted + hasAlarms) throw |
| 5 | Factory `fromEvents()` always produces valid contexts |
| 6 | `playReminderSound` forces `isReminder=true` |
| 7 | Channel matches `NotificationChannels.getChannelId` behavior |

This reduces test surface from ~30 cases to ~10 invariants that cover ALL valid states.

### Phase 3: Production Cleanup ✅ COMPLETE

#### Phase 3A: Add Static Helper Methods to NotificationContext

Add reusable calculation methods that can be used standalone:

```kotlin
companion object {
    /** Computes whether any event has an unmuted, non-task alarm */
    fun computeHasAlarms(events: List<EventAlertRecord>): Boolean =
        events.any { it.isAlarm && !it.isTask && !it.isMuted }
    
    /** Computes whether all events are muted (empty list returns false) */
    fun computeAllMuted(events: List<EventAlertRecord>): Boolean =
        events.isNotEmpty() && events.all { it.isMuted }
    
    /** Computes whether any new triggering event exists */
    fun computeHasNewTriggeringEvent(events: List<EventAlertRecord>): Boolean =
        events.any {
            it.displayStatus == EventDisplayStatus.Hidden &&
            it.snoozedUntil == 0L &&
            !it.isMuted
        }
}
```

#### Phase 3B: Add shouldBeQuiet Helper to EventNotificationManager

Extract the duplicated ~30-line blocks into a single helper:

```kotlin
/**
 * Computes whether a single event should be quiet (no sound/vibration).
 * Consolidates duplicated logic from postDisplayedEventNotifications 
 * and computeShouldPlayAndVibrateForCollapsedFull.
 */
fun computeShouldBeQuietForEvent(
    event: EventAlertRecord,
    force: Boolean,
    isAlreadyDisplayed: Boolean,
    isQuietPeriodActive: Boolean,
    isPrimaryEvent: Boolean,
    quietHoursMutePrimary: Boolean
): Boolean {
    val baseQuiet = when {
        force -> true
        isAlreadyDisplayed -> true
        isQuietPeriodActive && isPrimaryEvent -> quietHoursMutePrimary && !event.isAlarm
        isQuietPeriodActive -> true
        else -> false
    }
    return baseQuiet || event.isMuted
}
```

#### Phase 3C: Replace Inline hasAlarms Calculations

Replace 3 occurrences:
- Line 239-240: `recentEvents.any { ... } || collapsedEvents.any { ... }`
- Line 351: `activeEvents.any { ... }`

With: `NotificationContext.computeHasAlarms(events)`

#### Phase 3D: Replace Duplicated shouldBeQuiet Blocks

Replace the duplicated logic in:
- `postDisplayedEventNotifications` (lines 673-710)
- `computeShouldPlayAndVibrateForCollapsedFull` (lines 1689-1721)

With calls to the new `computeShouldBeQuietForEvent()` helper.

#### Phase 3E: Add Tests for New Helpers

Add invariant tests for:
- `computeHasAlarms()` - muted alarms excluded, tasks excluded
- `computeAllMuted()` - empty list handling
- `computeShouldBeQuietForEvent()` - all quiet conditions

### Phase 4: Further Consolidation ✅ COMPLETE

Additional cleanup to fully leverage NotificationContext and eliminate remaining duplication.

#### Phase 4A: Move `computeIsReminderForEvent` to NotificationContext

The single-event reminder check duplicates logic already in NotificationContext:

```kotlin
// Current (EventNotificationManager)
fun computeIsReminderForEvent(event: EventAlertRecord): Boolean =
    event.displayStatus != EventDisplayStatus.Hidden || event.snoozedUntil != 0L

// Move to NotificationContext as:
fun isReminderEvent(event: EventAlertRecord): Boolean =
    event.displayStatus != EventDisplayStatus.Hidden || event.snoozedUntil != 0L
```

#### Phase 4B: Delete `computeCollapsedChannelId`

This function duplicates what `NotificationContext.collapsedChannel` already computes:

```kotlin
// Current - DELETE THIS
fun computeCollapsedChannelId(events, hasAlarms, playReminderSound, hasNewTriggeringEvent): String {
    val allEventsMuted = events.all { it.isMuted }  // recomputed!
    val isReminder = playReminderSound || !hasNewTriggeringEvent
    return NotificationChannels.getChannelId(...)
}

// Replace usage with:
NotificationContext.fromEvents(events, mode, playReminderSound).collapsedChannel.toChannelId()
```

#### Phase 4C: Delete `computePartialCollapseChannelId`

Simple logic that can use existing helper:

```kotlin
// Current - DELETE THIS
fun computePartialCollapseChannelId(events: List<EventAlertRecord>): String {
    val allEventsMuted = events.all { it.isMuted }
    return if (allEventsMuted) CHANNEL_ID_SILENT else CHANNEL_ID_DEFAULT
}

// Replace with inline:
if (NotificationContext.computeAllMuted(events)) CHANNEL_ID_SILENT else CHANNEL_ID_DEFAULT
```

#### Phase 4D: Update Tests

- Remove tests for deleted helpers
- Add tests for `isReminderEvent`
- Update any tests that called the deleted helpers

### Phase 5: Deep Consolidation ✅ COMPLETE

Move ALL pure notification computation logic into NotificationContext, making it the single source of truth for "what should a notification look/sound like".

**Philosophy:** EventNotificationManager handles orchestration (when to post, DB updates, logging). NotificationContext handles computation (channels, sound decisions, alert flags).

#### Phase 5A: Move `computeShouldBeQuietForEvent` to NotificationContext

```kotlin
// Move from EventNotificationManager.Companion to NotificationContext.Companion
fun shouldBeQuietForEvent(
    event: EventAlertRecord,
    force: Boolean,
    isAlreadyDisplayed: Boolean,
    isQuietPeriodActive: Boolean,
    isPrimaryEvent: Boolean,
    quietHoursMutePrimary: Boolean
): Boolean
```

#### Phase 5B: Move `computeShouldOnlyAlertOnce` to NotificationContext

```kotlin
// Move from EventNotificationManager.Companion to NotificationContext.Companion
fun shouldOnlyAlertOnce(isForce: Boolean, wasCollapsed: Boolean, isReminder: Boolean): Boolean
```

#### Phase 5C: Move `applyReminderSoundOverride` to NotificationContext

```kotlin
// Move from EventNotificationManager.Companion to NotificationContext.Companion
fun applyReminderSoundOverride(
    currentShouldPlayAndVibrate: Boolean,
    playReminderSound: Boolean,
    hasAlarms: Boolean
): Boolean
```

#### Phase 5D: Add `individualChannelId()` to NotificationContext

Extract channel selection logic from `postNotification`:

```kotlin
fun individualChannelId(
    event: EventAlertRecord,
    isReminder: Boolean,
    forceAlarmStream: Boolean = false
): String = NotificationChannels.getChannelId(
    isAlarm = event.isAlarm || forceAlarmStream,
    isMuted = event.isMuted,
    isReminder = isReminder
)
```

#### Phase 5E: Move `computeShouldPlayAndVibrateForCollapsedFull` to NotificationContext

Rename to `computeShouldPlayAndVibrate`:

```kotlin
fun computeShouldPlayAndVibrate(
    events: List<EventAlertRecord>,
    force: Boolean,
    isQuietPeriodActive: Boolean,
    primaryEventId: Long?,
    quietHoursMutePrimary: Boolean,
    playReminderSound: Boolean,
    hasAlarms: Boolean
): Pair<Boolean, Boolean>
```

#### Phase 5F: Update all call sites and tests

- Update EventNotificationManager to call `NotificationContext.*` instead of `companion object` methods
- Update tests to use new locations

### Phase 6: Simplify Individual Notification Loop ✅ COMPLETE

Final cleanup of `postDisplayedEventNotifications` to eliminate nested if/else.

#### Phase 6A: Add State Predicates to NotificationContext

```kotlin
fun isReturningFromSnooze(event: EventAlertRecord): Boolean =
    event.snoozedUntil != 0L

fun wasCollapsed(event: EventAlertRecord): Boolean =
    event.displayStatus == EventDisplayStatus.DisplayedCollapsed

fun shouldPostIndividualNotification(event: EventAlertRecord, force: Boolean): Boolean = when {
    isReturningFromSnooze(event) -> true
    event.displayStatus != EventDisplayStatus.DisplayedNormal -> true
    force -> true
    else -> false
}
```

#### Phase 6B: Flatten the Loop Structure

Replace nested `if (snoozedUntil == 0) { if (displayStatus != Normal) { ... } else { ... } } else { ... }`
with flat early-continue structure:

```kotlin
for (event in events) {
    if (!NotificationContext.shouldPostIndividualNotification(event, force)) {
        continue
    }
    
    val isReturningFromSnooze = NotificationContext.isReturningFromSnooze(event)
    val wasCollapsed = NotificationContext.wasCollapsed(event)
    val isReminder = NotificationContext.isReminderEvent(event)
    
    // Unified shouldBeQuiet calculation
    // Unified postNotification call
    // Unified DB update
}
```

#### Phase 6C: Add Invariant Tests

- `isReturningFromSnooze` tests
- `wasCollapsed` tests  
- `shouldPostIndividualNotification` tests with all status combinations

## Benefits

| Aspect | Before | After |
|--------|--------|-------|
| Constraints | Implicit, scattered | Explicit in `init {}` |
| Derived state | Computed 3× in different places | Computed once via helper |
| shouldBeQuiet | 2× ~30 line blocks | 1× ~10 line helper |
| Channel helpers | 3 separate functions | 1 unified context |
| Sound/vibrate logic | In EventNotificationManager | In NotificationContext |
| Testing | ~30 scenario tests | ~20 invariant tests |
| Impossible states | Silently wrong | Throw immediately |
| Documentation | Truth tables | Self-documenting `when` |

**Estimated reduction:** ~120 lines moved from EventNotificationManager to NotificationContext

## Effort Estimate

| Phase | Effort | Status |
|-------|--------|--------|
| Phase 1 | 1-2 hours | ✅ Complete |
| Phase 2 | 2-3 hours | ✅ Complete |
| Phase 3A | 30 min | ✅ Complete |
| Phase 3B | 30 min | ✅ Complete |
| Phase 3C | 30 min | ✅ Complete |
| Phase 3D | 1 hour | ✅ Complete |
| Phase 3E | 30 min | ✅ Complete |
| Phase 4A | 15 min | ✅ Complete |
| Phase 4B | 15 min | ✅ Complete |
| Phase 4C | 15 min | ✅ Complete |
| Phase 4D | 30 min | ✅ Complete |
| Phase 5A | 15 min | ✅ Complete |
| Phase 5B | 10 min | ✅ Complete |
| Phase 5C | 10 min | ✅ Complete |
| Phase 5D | 15 min | ✅ Complete |
| Phase 5E | 20 min | ✅ Complete |
| Phase 5F | 30 min | ✅ Complete |
| Phase 6A | 15 min | ✅ Complete |
| Phase 6B | 20 min | ✅ Complete |
| Phase 6C | 15 min | ✅ Complete |

## Files Affected

**New files (Phase 1+2):**
- `android/app/src/main/java/com/github/quarck/calnotify/notification/NotificationContext.kt` ✅
- `android/app/src/test/java/com/github/quarck/calnotify/notification/NotificationContextInvariantTest.kt` ✅

**Modified (Phase 3):**
- `android/app/src/main/java/com/github/quarck/calnotify/notification/NotificationContext.kt`
- `android/app/src/main/java/com/github/quarck/calnotify/notification/EventNotificationManager.kt`
- `android/app/src/test/java/com/github/quarck/calnotify/notification/NotificationContextInvariantTest.kt`

## Decision

- [x] Approve Phase 1+2 (context class + invariant tests)
- [x] Approve Phase 3 (production cleanup)
- [ ] Reject / Needs changes

## Results

**Lines changed (Phase 3):**
- `NotificationContext.kt`: Added ~50 lines (static helpers + documentation)
- `EventNotificationManager.kt`: Net reduction of ~40 lines (removed duplicate shouldBeQuiet blocks)
- `NotificationContextInvariantTest.kt`: Added ~230 lines (comprehensive invariant tests)

**Lines changed (Phase 4):**
- `NotificationContext.kt`: Added `isReminderEvent()` helper (~20 lines)
- `EventNotificationManager.kt`: Removed `computeIsReminderForEvent`, `computeCollapsedChannelId`, `computePartialCollapseChannelId` (~60 lines)
- Production code now uses `NotificationContext.fromEvents().collapsedChannel.toChannelId()`

**Lines changed (Phase 5):**
- `NotificationContext.kt`: Added ~130 lines (all sound/vibration helpers)
- `EventNotificationManager.kt`: Removed ~150 lines (moved helpers to NotificationContext)
- Tests updated to use `NotificationContext.*` instead of `EventNotificationManager.*`

**Lines changed (Phase 6):**
- `NotificationContext.kt`: Added ~35 lines (state predicates)
- `EventNotificationManager.kt`: Reduced ~25 lines (flattened loop structure)
- `NotificationContextInvariantTest.kt`: Added ~100 lines (12 new tests)

**Benefits achieved:**
- Single source of truth for ALL notification decisions in `NotificationContext`
- `EventNotificationManager` now focuses purely on orchestration (when to post, DB, logging)
- All computation is testable in isolation via `NotificationContextInvariantTest`
- Channel selection unified: `collapsedChannel`, `partialCollapseChannelId`, `individualChannelId`
- Sound/vibration logic unified: `shouldBeQuietForEvent`, `shouldOnlyAlertOnce`, `computeShouldPlayAndVibrate`
- Event state predicates: `isReturningFromSnooze`, `wasCollapsed`, `shouldPostIndividualNotification`
- Flat loop structure in `postDisplayedEventNotifications` - easier to follow
- Removed nested if/else in favor of early-continue pattern
- 30+ invariant tests covering all edge cases
- Explicit constraints that throw on impossible states
