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

#### 1.4 Move Sort from Memory to Database (Performance Quick Win)

Currently `DismissedEventsActivity` loads all events then sorts in memory:
```kotlin
// Current - slow at scale
db.events.sortedByDescending { it.dismissTime }.toTypedArray()
```

Move sorting to the database query for better performance:

**Update `DismissedEventDao.kt`:**
```kotlin
// Change from:
@Query("SELECT * FROM ${TABLE_NAME}")
fun getAll(): List<DismissedEventEntity>

// To:
@Query("SELECT * FROM ${DismissedEventEntity.TABLE_NAME} ORDER BY ${DismissedEventEntity.COL_DISMISS_TIME} DESC")
fun getAll(): List<DismissedEventEntity>
```

**Update `DismissedEventsActivity.kt`:**
```kotlin
// Change from:
db.events.sortedByDescending { it.dismissTime }.toTypedArray()

// To (sorting now happens in DB):
db.events.toTypedArray()
```

**Performance at 10,000 events:**
| Approach | Time |
|----------|------|
| In-memory sort | 100-500ms |
| DB sort (no index) | 50-100ms |
| DB sort (with index) | <10ms |

#### 1.5 Add Preference UI to `misc_preferences.xml`

```xml
<ListPreference
    android:key="keep_history_days"
    android:title="@string/bin_size"
    android:summary="@string/bin_size_summary"
    android:entries="@array/keep_history_entries"
    android:entryValues="@array/keep_history_values"
    android:defaultValue="3" />
```

#### 1.6 Add String Resources to `strings.xml`

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

<!-- Update existing (add proper warning): -->
<string name="remove_all_confirmation">Remove all dismissed events history?\n\nThis will permanently delete all dismissed event records.\n\nCANNOT BE UNDONE!</string>
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

### Phase 3: Search in Dismissed Events

Port the search functionality from `MainActivity` to `DismissedEventsActivity`.

#### 3.1 Add Search to Menu (`dismissed_main.xml`)

```xml
<item
    android:id="@+id/action_search"
    android:icon="@android:drawable/ic_menu_search"
    android:orderInCategory="90"
    android:title="@string/search"
    app:showAsAction="ifRoom|collapseActionView"
    app:actionViewClass="androidx.appcompat.widget.SearchView"/>

<item
    android:id="@+id/action_remove_all"
    android:orderInCategory="100"
    android:title="@string/remove_all"
    app:showAsAction="never"/>
```

#### 3.2 Add Search Logic to `DismissedEventsActivity.kt`

Port from MainActivity:
- Add `searchView` and `searchMenuItem` fields
- Setup `SearchView` in `onCreateOptionsMenu()`
- Handle `OnQueryTextListener` for filtering
- Implement two-stage dismissal (clear focus first, then close)

```kotlin
private var searchView: SearchView? = null
private var searchMenuItem: MenuItem? = null

override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.dismissed_main, menu)
    
    searchMenuItem = menu.findItem(R.id.action_search)
    searchView = searchMenuItem?.actionView as? SearchView
    
    searchView?.queryHint = "Search ${adapter.itemCount} events..."
    searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            adapter.setSearchText(query)
            searchView?.clearFocus()
            return true
        }
        override fun onQueryTextChange(newText: String?): Boolean {
            adapter.setSearchText(newText)
            return true
        }
    })
    // ... close button handling
    return true
}
```

#### 3.3 Add Filter Support to `DismissedEventListAdapter.kt`

```kotlin
// Add fields:
private var allEntries = arrayOf<DismissedEventAlertRecord>()
var searchString: String? = null
    private set

// Add method:
fun setSearchText(query: String?) {
    searchString = query
    applyFilter()
}

private fun applyFilter() {
    entries = if (searchString.isNullOrEmpty()) {
        allEntries
    } else {
        val query = searchString!!.lowercase()
        allEntries.filter { entry ->
            entry.event.title.lowercase().contains(query) ||
            entry.event.desc.lowercase().contains(query) ||
            entry.event.location.lowercase().contains(query)
        }.toTypedArray()
    }
    notifyDataSetChanged()
}

// Update setEventsToDisplay:
fun setEventsToDisplay(newEntries: Array<DismissedEventAlertRecord>) = synchronized(this) {
    allEntries = newEntries
    applyFilter()
}
```

#### 3.4 Files to Modify

- `android/app/src/main/res/menu/dismissed_main.xml`
- `android/app/src/main/java/com/github/quarck/calnotify/ui/DismissedEventsActivity.kt`
- `android/app/src/main/java/com/github/quarck/calnotify/ui/DismissedEventListAdapter.kt`

### Phase 4: UI Improvements (Future)

#### 4.1 Paginated Loading in `DismissedEventsActivity`

- Load most recent 100 events initially
- Add "Load more" or infinite scroll
- Show count badge in menu

#### 4.2 Date Grouping

- Group events by day/week/month
- Collapsible sections

#### 4.3 "What You Got Done" Report

- Summary view: events per day/week
- Calendar-style heatmap
- Export functionality

## Files to Modify

### Phase 1 - Production Code:
- `android/app/src/main/java/com/github/quarck/calnotify/Settings.kt`
- `android/app/src/main/java/com/github/quarck/calnotify/ui/MainActivity.kt`
- `android/app/src/main/java/com/github/quarck/calnotify/ui/DismissedEventsActivity.kt` (remove in-memory sort)
- `android/app/src/main/java/com/github/quarck/calnotify/dismissedeventsstorage/DismissedEventDao.kt` (add ORDER BY)
- `android/app/src/main/res/xml/misc_preferences.xml`
- `android/app/src/main/res/values/strings.xml`

### Phase 1 - Test Code:
- `android/app/src/androidTest/java/com/github/quarck/calnotify/SettingsTest.kt` (update)
- `android/app/src/test/java/com/github/quarck/calnotify/SettingsRobolectricTest.kt` (update)
- `android/app/src/androidTest/java/com/github/quarck/calnotify/dismissedeventsstorage/DismissedEventsPurgeTest.kt` (NEW)
- `android/app/src/androidTest/java/com/github/quarck/calnotify/dismissedeventsstorage/StorageConsistencyTest.kt` (NEW)
- `android/app/src/test/java/com/github/quarck/calnotify/testutils/MockEventsStorage.kt` (NEW)

### Phase 2 (Performance):
- `android/app/src/main/java/com/github/quarck/calnotify/dismissedeventsstorage/DismissedEventEntity.kt`
- `android/app/src/main/java/com/github/quarck/calnotify/dismissedeventsstorage/DismissedEventDao.kt`
- `android/app/src/main/java/com/github/quarck/calnotify/dismissedeventsstorage/DismissedEventsStorageInterface.kt`
- `android/app/src/main/java/com/github/quarck/calnotify/dismissedeventsstorage/RoomDismissedEventsStorage.kt`
- `android/app/src/main/java/com/github/quarck/calnotify/dismissedeventsstorage/DismissedEventsDatabase.kt` (migration)

## Testing Plan

### Phase 1 Tests

#### 1. Settings Tests (add to `SettingsTest.kt` / `SettingsRobolectricTest.kt`)

```kotlin
// === Keep History Settings Tests ===

@Test
fun testKeepHistoryDaysDefaultValue() {
    // Default should be 3 days
    assertEquals(3, settings.keepHistoryDays)
}

@Test
fun testKeepHistoryMillisDefaultValue() {
    // 3 days in milliseconds
    assertEquals(3 * 24 * 60 * 60 * 1000L, settings.keepHistoryMillis)
}

@Test
fun testKeepHistoryMillisForeverValue() {
    // When keepHistoryDays is 0, should return Long.MAX_VALUE
    // (Requires setting the preference to 0 first)
    // settings.setInt("keep_history_days", 0) 
    // assertEquals(Long.MAX_VALUE, settings.keepHistoryMillis)
}

@Test
fun testKeepHistoryMillisVariousValues() {
    // Test 7, 14, 30, 90, 365 days
    val testCases = listOf(7, 14, 30, 90, 365)
    testCases.forEach { days ->
        val expectedMillis = days * 24L * 60L * 60L * 1000L
        // Set and verify
    }
}
```

#### 2. Purge Behavior Tests (new file: `DismissedEventsPurgeTest.kt`)

Use `MockDismissedEventsStorage` pattern for in-memory testing:

```kotlin
@RunWith(AndroidJUnit4::class)
class DismissedEventsPurgeTest {
    private lateinit var mockDismissedStorage: MockDismissedEventsStorage
    private lateinit var mockTimeProvider: MockTimeProvider
    
    @Before
    fun setup() {
        mockDismissedStorage = MockDismissedEventsStorage()
        mockTimeProvider = MockTimeProvider(baseTime)
    }
    
    @Test
    fun testPurgeOldRemovesExpiredEvents() {
        // Given: Events from 1, 2, 5, 10 days ago
        // When: Purge with 3-day retention
        // Then: Only events from 1, 2 days ago remain
    }
    
    @Test
    fun testPurgeOldWithForeverSetting() {
        // Given: Events from various times
        // When: keepHistoryMillis = Long.MAX_VALUE (forever)
        // Then: No purge call made, all events remain
    }
    
    @Test
    fun testPurgeOldWithEmptyStorage() {
        // Given: Empty storage
        // When: Purge
        // Then: No errors, storage still empty
    }
    
    @Test
    fun testPurgeOldPreservesRecentEvents() {
        // Given: Events from 1 day ago
        // When: Purge with 3-day retention
        // Then: All events remain
    }
}
```

#### 3. Consistency Check Tests (new file: `StorageConsistencyTest.kt`)

```kotlin
@RunWith(AndroidJUnit4::class)
class StorageConsistencyTest {
    private lateinit var mockDismissedStorage: MockDismissedEventsStorage
    private lateinit var mockEventsStorage: MockEventsStorage // Need to create
    private lateinit var mockTimeProvider: MockTimeProvider
    
    @Test
    fun testCleanupOrphanedEventsRemovesDuplicates() {
        // Given: Event exists in BOTH storages (orphaned state)
        val event = createTestEvent(1L)
        mockDismissedStorage.addEvent(EventDismissType.ManuallyDismissedFromActivity, event)
        mockEventsStorage.addEvent(event)
        
        // When: cleanupOrphanedEvents runs
        // Then: Event is removed from EventsStorage, remains in DismissedStorage
        assertEquals(1, mockDismissedStorage.eventCount)
        assertEquals(0, mockEventsStorage.eventCount)
    }
    
    @Test
    fun testCleanupOrphanedEventsPreservesValidState() {
        // Given: Different events in each storage (normal state)
        val dismissedEvent = createTestEvent(1L)
        val activeEvent = createTestEvent(2L)
        mockDismissedStorage.addEvent(EventDismissType.ManuallyDismissedFromActivity, dismissedEvent)
        mockEventsStorage.addEvent(activeEvent)
        
        // When: cleanupOrphanedEvents runs
        // Then: Both remain unchanged
        assertEquals(1, mockDismissedStorage.eventCount)
        assertEquals(1, mockEventsStorage.eventCount)
    }
    
    @Test
    fun testCleanupOrphanedEventsWithEmptyStorages() {
        // Given: Both storages empty
        // When: cleanupOrphanedEvents runs
        // Then: No errors
    }
    
    @Test
    fun testCleanupOrphanedEventsHandlesErrors() {
        // Given: Storage throws exception
        // When: cleanupOrphanedEvents runs
        // Then: Error is logged, no crash
    }
    
    @Test
    fun testCleanupOrphanedEventsWithMultipleOrphans() {
        // Given: 5 events in both storages
        // When: cleanupOrphanedEvents runs
        // Then: All 5 removed from EventsStorage
    }
}
```

#### 4. MockEventsStorage (new file: `testutils/MockEventsStorage.kt`)

Need to create this for consistency check tests:

```kotlin
/**
 * In-memory implementation of EventsStorageInterface for tests.
 */
class MockEventsStorage : EventsStorageInterface {
    private val eventsMap = mutableMapOf<EventKey, EventAlertRecord>()
    
    data class EventKey(val eventId: Long, val instanceStartTime: Long)
    
    // Implement interface methods...
    
    override val events: List<EventAlertRecord>
        get() = eventsMap.values.toList()
        
    override fun deleteEvents(events: Collection<EventAlertRecord>): Int {
        var deleted = 0
        events.forEach { 
            val key = EventKey(it.eventId, it.instanceStartTime)
            if (eventsMap.remove(key) != null) deleted++
        }
        return deleted
    }
    
    // ... other methods
}
```

### Test Files Summary

| File | Type | Purpose |
|------|------|---------|
| `SettingsTest.kt` | androidTest | Add `keepHistoryDays/Millis` tests |
| `SettingsRobolectricTest.kt` | test | Add `keepHistoryDays/Millis` tests |
| `DismissedEventsPurgeTest.kt` | androidTest (NEW) | Purge behavior tests |
| `StorageConsistencyTest.kt` | androidTest (NEW) | Orphaned event cleanup tests |
| `MockEventsStorage.kt` | testutils (NEW) | In-memory EventsStorage for tests |

### Phase 2 Tests (Future)

- `DismissedEventDaoTest.kt` - Test new date-range queries
- Performance tests with 1000+ events
- Database migration tests

### Phase 3 Tests (Future)

- `DismissedEventsActivityTest.kt` - Search UI tests
- `DismissedEventListAdapterTest.kt` - Filter logic tests
  - Filter by title
  - Filter by description
  - Filter by location
  - Empty query returns all
  - Case-insensitive matching

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

