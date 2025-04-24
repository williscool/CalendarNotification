# Event Restoration Behavior

## Background

The application includes a feature to restore dismissed events. This is controlled by the `canBeRestored` property in the `EventDismissType` enum:

```kotlin
val canBeRestored: Boolean
    get() = this != AutoDismissedDueToCalendarMove && this != EventMovedUsingApp && this != AutoDismissedDueToRescheduleConfirmation
```

## Current Implementation

Events can be dismissed in various ways:
- `ManuallyDismissedFromNotification` - User dismissed from notification
- `ManuallyDismissedFromActivity` - User dismissed from within the app
- `AutoDismissedDueToCalendarMove` - System detected the event was moved in calendar
- `EventMovedUsingApp` - Event was moved using the app
- `AutoDismissedDueToRescheduleConfirmation` - Event was dismissed due to rescheduling confirmation

Currently, the last three types are marked as non-restorable, preventing them from being restored by the user.

## Issue

`AutoDismissedDueToRescheduleConfirmation` was recently added to the non-restorable list. However, there are cases where these events could potentially be restored, especially if the original event still exists in the device calendar database.

The `canBeRestored` property appears to be designed for filtering which dismissed events can be shown to users for restoration, but its actual usage in the codebase is minimal.

## Considerations

### Technical Feasibility

- Events dismissed due to rescheduling can technically be restored if they still exist in the calendar database
- Restoration works best when done on the same device where the dismissal occurred

### User Experience

- Allowing restoration of rescheduled events might lead to duplicate events (both original and rescheduled versions)
- Users might expect the ability to undo a rescheduling action if they made a mistake

### Use Cases

Legitimate reasons for restoring a rescheduled event:
- Accidental or incorrect rescheduling
- User changed their mind about the reschedule
- Rescheduling failed but the event was already dismissed

## Options

1. **Keep Current Behavior**: Events dismissed due to rescheduling confirmation cannot be restored.
   - Pros: Cleaner experience, prevents potential duplicates
   - Cons: Users cannot undo mistaken rescheduling actions

2. **Allow Restoration**: Remove `AutoDismissedDueToRescheduleConfirmation` from the non-restorable list.
   - Pros: More flexibility for users, allows recovery from mistakes
   - Cons: Potential for confusion with duplicate events

## Recommendation

Consider the primary use case for the rescheduling confirmation feature:

- If rescheduling is expected to be a definitive action with low error rates, keep it non-restorable
- If users might frequently make mistakes or change their minds about rescheduling, make it restorable

Based on usage patterns and user feedback, the appropriate option can be implemented with a simple code change to the `canBeRestored` property.

## Implementation

To mark events dismissed due to rescheduling confirmation as restorable:

```kotlin
val canBeRestored: Boolean
    get() = this != AutoDismissedDueToCalendarMove && this != EventMovedUsingApp
    // AutoDismissedDueToRescheduleConfirmation removed from non-restorable list
``` 