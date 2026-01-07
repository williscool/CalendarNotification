# Long-Lived Dismissed Events Storage

## Background

The dismissed events storage (the "Bin") currently auto-purges events older than 3 days. This is hardcoded in `Consts.kt`:

```kotlin
const val BIN_KEEP_HISTORY_DAYS = 3L
const val BIN_KEEP_HISTORY_MILLISECONDS = BIN_KEEP_HISTORY_DAYS * DAY_IN_MILLISECONDS
```

Purging happens in `MainActivity.reloadData()`:

```kotlin
getDismissedEventsStorage(this).classCustomUse { 
    it.purgeOld(clock.currentTimeMillis(), Consts.BIN_KEEP_HISTORY_MILLISECONDS) 
}
```

## Goal

Add a user-configurable setting to control how long dismissed events are retained, including a "forever" option. This enables future features like:
- "What you got done last week/month" productivity reports
- Long-term event history for reference
- Better undo capability for accidentally dismissed events

## Scale Analysis: 10,000 Events

Assuming ~1 event/hour for an entire year (8,760 hours) rounds to ~10,000 events as an extreme upper bound.

| Metric | Value | Impact |
|--------|-------|--------|
| **Database Size** | 5-20 MB | ✅ SQLite handles millions of rows easily |
| **Memory (full load)** | 10-40 MB | ⚠️ Noticeable on low-RAM devices when viewing Bin |
| **getAll() query** | <100ms | ✅ Fast enough |
| **In-memory sort** | 100-500ms without index | ⚠️ Could lag when opening Bin |
| **RecyclerView rendering** | Fine with recycling | ✅ No problem |
| **App startup** | No impact (purge is async) | ✅ No problem |

### Conclusion

10,000 events is manageable with current architecture. Adding a `dismissTime` index and date-range queries would make it performant for any realistic usage.

## Implementation Plan

### Phase 1: Core Setting (Minimum Viable)

#### 1.1 Add Setting Property to `Settings.kt`

```kotlin
// In companion object:
private const val KEEP_HISTORY_DAYS_KEY = "keep_history_days"
private const val DEFAULT_KEEP_HISTORY_DAYS = 3

// Property:
val keepHistoryDays: Int
    get() = getInt(KEEP_HISTORY_DAYS_KEY, DEFAULT_KEEP_HISTORY_DAYS)

val keepHistoryMillis: Long
    get() {
        val days = keepHistoryDays
        return if (days <= 0) Long.MAX_VALUE else days * Consts.DAY_IN_MILLISECONDS
    }
```

Special value: `0` or `-1` means "forever" (returns `Long.MAX_VALUE` which effectively disables purging)

#### 1.2 Update `MainActivity.kt` Purge Logic

```kotlin
// In reloadData():
val settings = Settings(this)
val keepTime = settings.keepHistoryMillis
if (keepTime < Long.MAX_VALUE) {
    getDismissedEventsStorage(this).classCustomUse { 
        it.purgeOld(clock.currentTimeMillis(), keepTime) 
    }
}
```

#### 1.3 Add Storage Consistency Check (Addresses event_deletion_issues.md)

When events fail to delete from `EventsStorage` during dismissal, they can end up in both storages - creating an inconsistent state. Add a cleanup step:

```kotlin
// In reloadData(), after purge:
cleanupOrphanedEvents(this)

// New function:
private fun cleanupOrphanedEvents(context: Context) {
    background {
        try {
            val dismissedStorage = getDismissedEventsStorage(context)
            val eventsStorage = getEventsStorage(context)
            
            dismissedStorage.classCustomUse { dismissed ->
                eventsStorage.classCustomUse { active ->
                    // Get IDs of dismissed events
                    val dismissedKeys = dismissed.events.map { 
                        Pair(it.event.eventId, it.event.instanceStartTime) 
                    }.toSet()
                    
                    // Find any active events that are also in dismissed storage
                    val orphaned = active.events.filter { event ->
                        dismissedKeys.contains(Pair(event.eventId, event.instanceStartTime))
                    }
                    
                    if (orphaned.isNotEmpty()) {
                        DevLog.warn(LOG_TAG, "Found ${orphaned.size} orphaned events in both storages, cleaning up")
                        active.deleteEvents(orphaned)
                    }
                }
            }
        } catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Error during orphaned event cleanup: ${ex.message}")
        }
    }
}
```

This addresses issues from `docs/dev_todo/event_deletion_issues.md`:
- **Issue #3**: Events in both active and dismissed storage
- **Issue #5**: Accumulation of stale events
- **Issue #6**: Automatic cleanup process

#### 1.4 Add Preference UI to `misc_preferences.xml`

```xml
<ListPreference
    android:key="keep_history_days"
    android:title="@string/bin_size"
    android:summary="@string/bin_size_summary"
    android:entries="@array/keep_history_entries"
    android:entryValues="@array/keep_history_values"
    android:defaultValue="3" />
```

#### 1.5 Add String Resources to `strings.xml`

```xml
<!-- Already exists: -->
<string name="bin_size">Bin size</string>
<string name="bin_size_summary">Number of days to keep dismissed notifications</string>

<!-- New arrays: -->
<string-array name="keep_history_entries">
    <item>3 days</item>
    <item>7 days</item>
    <item>14 days</item>
    <item>30 days</item>
    <item>90 days</item>
    <item>1 year</item>
    <item>Forever</item>
</string-array>

<string-array name="keep_history_values">
    <item>3</item>
    <item>7</item>
    <item>14</item>
    <item>30</item>
    <item>90</item>
    <item>365</item>
    <item>0</item>
</string-array>
```

### Phase 2: Performance Optimization (Recommended)

#### 2.1 Add Index on `dismissTime`

Update `DismissedEventEntity.kt`:

```kotlin
@Entity(
    tableName = DismissedEventEntity.TABLE_NAME,
    primaryKeys = [...],
    indices = [
        Index(value = [...], unique = true, name = INDEX_NAME),
        Index(value = [COL_DISMISS_TIME], name = "idx_dismiss_time")  // NEW
    ]
)
```

This requires a Room database migration (increment version).

#### 2.2 Add Date-Range Queries to `DismissedEventDao.kt`

```kotlin
@Query("SELECT * FROM ${TABLE_NAME} WHERE ${COL_DISMISS_TIME} >= :startTime AND ${COL_DISMISS_TIME} <= :endTime ORDER BY ${COL_DISMISS_TIME} DESC")
fun getEventsBetween(startTime: Long, endTime: Long): List<DismissedEventEntity>

@Query("SELECT * FROM ${TABLE_NAME} ORDER BY ${COL_DISMISS_TIME} DESC LIMIT :limit")
fun getRecentEvents(limit: Int): List<DismissedEventEntity>

@Query("SELECT COUNT(*) FROM ${TABLE_NAME} WHERE ${COL_DISMISS_TIME} >= :startTime AND ${COL_DISMISS_TIME} <= :endTime")
fun countEventsBetween(startTime: Long, endTime: Long): Int
```

#### 2.3 Update `DismissedEventsStorageInterface.kt`

```kotlin
fun getEventsBetween(startTime: Long, endTime: Long): List<DismissedEventAlertRecord>
fun getRecentEvents(limit: Int): List<DismissedEventAlertRecord>
fun countEventsBetween(startTime: Long, endTime: Long): Int
```

### Phase 3: UI Improvements (Future)

#### 3.1 Paginated Loading in `DismissedEventsActivity`

- Load most recent 100 events initially
- Add "Load more" or infinite scroll
- Show count badge in menu

#### 3.2 Date Grouping

- Group events by day/week/month
- Collapsible sections

#### 3.3 "What You Got Done" Report

- Summary view: events per day/week
- Calendar-style heatmap
- Export functionality

## Files to Modify

### Phase 1 (Minimum):
- `android/app/src/main/java/com/github/quarck/calnotify/Settings.kt`
- `android/app/src/main/java/com/github/quarck/calnotify/ui/MainActivity.kt`
- `android/app/src/main/res/xml/misc_preferences.xml`
- `android/app/src/main/res/values/strings.xml`

### Phase 2 (Performance):
- `android/app/src/main/java/com/github/quarck/calnotify/dismissedeventsstorage/DismissedEventEntity.kt`
- `android/app/src/main/java/com/github/quarck/calnotify/dismissedeventsstorage/DismissedEventDao.kt`
- `android/app/src/main/java/com/github/quarck/calnotify/dismissedeventsstorage/DismissedEventsStorageInterface.kt`
- `android/app/src/main/java/com/github/quarck/calnotify/dismissedeventsstorage/RoomDismissedEventsStorage.kt`
- `android/app/src/main/java/com/github/quarck/calnotify/dismissedeventsstorage/DismissedEventsDatabase.kt` (migration)

## Testing Considerations

1. **Settings persistence**: Verify setting saves and loads correctly
2. **Purge behavior**: Test that events are/aren't purged based on setting
3. **Forever option**: Confirm no purging occurs when "Forever" selected
4. **Migration**: Test database migration for new index
5. **Performance**: Load test with 1000+ dismissed events
6. **Edge cases**: Empty bin, setting change with existing data
7. **Consistency check**: Create orphaned state (event in both storages), verify cleanup
8. **Error handling**: Verify cleanup errors don't crash app or block UI

## Migration Path

1. Implement Phase 1 - users get the setting immediately
2. Implement Phase 2 before "What You Got Done" feature
3. Phase 3 as needed based on user feedback

## Related Issues

This work partially addresses `docs/dev_todo/event_deletion_issues.md`:

| Issue | Status | How Addressed |
|-------|--------|---------------|
| #3 State Inconsistency | ✅ Addressed | Cleanup function removes events from both storages |
| #5 Memory/Performance | ✅ Addressed | Periodic cleanup prevents accumulation |
| #6 Error Recovery | ✅ Partial | Automatic cleanup process added |
| #1 Notification Mismatch | ❌ Not addressed | Requires notification system changes |
| #2 Alarm Scheduling | ❌ Not addressed | Requires alarm scheduler changes |
| #4 UI Inconsistency | ✅ Partial | Cleanup runs before UI refresh |

## Notes

- Existing string resources `bin_size` and `bin_size_summary` can be reused
- The commented-out `KEEP_HISTORY_DAYS_KEY` in Settings.kt suggests this was originally planned
- Consistency check runs in background, won't block UI

