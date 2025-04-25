# Event Deletion Issues and Cleanup

## Current Issues

### Failed Event Deletions
When an event fails to delete from EventsStorage during dismissal, several issues can occur:

1. **Notification State Mismatch**
   - The notification system continues to think the event is active
   - Can lead to duplicate or stale notifications

2. **Alarm Scheduling Issues**
   - Alarms for the event continue to be scheduled
   - Results in unnecessary notifications/reminders

3. **State Inconsistency**
   - Events might be added to DismissedEventsStorage even if not deleted from EventsStorage
   - Creates inconsistent state where event exists in both active and dismissed storage

4. **UI Inconsistency**
   - UI might be notified of changes even though event wasn't actually deleted
   - Can lead to incorrect display of events

5. **Memory and Performance Impact**
   - Failed deletions can lead to accumulation of stale events
   - Impacts performance as database grows with invalid entries

6. **Error Recovery**
   - Current error handling only logs issues
   - No automatic retry mechanism or cleanup process
   - No comprehensive recovery system for failed deletions

## TODO Items

### 1. Implement purgeDismissed Functionality
Similar to `purgeOld` in DismissedEventsStorage, we need a cleanup mechanism for EventsStorage:

```kotlin
// TODO: Implement similar to DismissedEventsStorage.purgeOld
fun purgeDismissed(context: Context) {
    // Should:
    // 1. Find events that failed to delete
    // 2. Attempt to clean them up
    // 3. Log any persistent issues
    // 4. Potentially notify user of cleanup results
}
```

### 2. Improve Error Recovery
- Add retry mechanism for failed deletions
- Implement automatic cleanup of orphaned events
- Add better error reporting to UI

### 3. Add State Validation
- Add periodic validation of EventsStorage and DismissedEventsStorage consistency
- Implement repair mechanisms for detected inconsistencies

### 4. Improve Error Handling
- Add more detailed error reporting
- Implement proper rollback mechanisms for failed operations
- Add user notification for critical failures 