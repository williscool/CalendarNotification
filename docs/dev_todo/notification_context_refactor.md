# RFC: NotificationContext Refactor

## Status: Proposed

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

## Proposed Solution

### Phase 1: NotificationContext Data Class

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

### Phase 2: Invariant Tests

Instead of testing ~30 scenarios individually, test properties that must ALWAYS hold:

| Invariant | Description |
|-----------|-------------|
| 1 | `allMuted` context always produces `SILENT` channel |
| 2 | New triggering events use EVENTS/ALARM (not REMINDERS) |
| 3 | `hasAlarms` switches to alarm channel variant |
| 4 | Impossible states (allMuted + hasAlarms) throw |
| 5 | Factory `fromEvents()` always produces valid contexts |

This reduces test surface from ~30 cases to ~10 invariants that cover ALL valid states.

### Phase 3: Wire Into Production (Future)

Gradually migrate production code to use `NotificationContext`:

```kotlin
// Before (scattered calculations)
val hasAlarms = events.any { it.isAlarm && !it.isTask && !it.isMuted }
val allEventsMuted = events.all { it.isMuted }
val channelId = computeCollapsedChannelId(events, hasAlarms, playReminderSound, hasNewTriggeringEvent)

// After (single context)
val ctx = NotificationContext.fromEvents(events, mode, playReminderSound, isQuietPeriodActive)
val channelId = ctx.collapsedChannel.toChannelId()
```

Existing helper functions remain working during migration.

## Benefits

| Aspect | Before | After |
|--------|--------|-------|
| Constraints | Implicit, scattered | Explicit in `init {}` |
| Derived state | Computed multiple times | Computed once |
| Testing | ~30 scenario tests | ~10 invariant tests |
| Impossible states | Silently wrong | Throw immediately |
| Documentation | Truth tables | Self-documenting `when` |

## Effort Estimate

| Phase | Effort | Dependencies |
|-------|--------|--------------|
| Phase 1 | 1-2 hours | None |
| Phase 2 | 2-3 hours | Phase 1 |
| Phase 3 | 4-6 hours | Phase 1+2, can defer |

## Files Affected

**New files:**
- `android/app/src/main/java/com/github/quarck/calnotify/notification/NotificationContext.kt`
- `android/app/src/test/java/com/github/quarck/calnotify/notification/NotificationContextInvariantTest.kt`

**Modified (Phase 3 only):**
- `android/app/src/main/java/com/github/quarck/calnotify/notification/EventNotificationManager.kt`

## Decision

- [ ] Approve Phase 1+2 (context class + invariant tests)
- [ ] Approve Phase 3 (production wiring)
- [ ] Reject / Needs changes
