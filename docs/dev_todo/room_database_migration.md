# Room Database Migration

## Status: Under Consideration

## Overview

The app currently uses raw SQLite with hand-written cursor parsing, ContentValues, and manual schema management. [Room](https://developer.android.com/training/data-storage/room) is the modern Android Jetpack library that provides:

- **Compile-time SQL verification** - catch typos before runtime
- **Type-safe queries** - no more `cursor.getInt(PROJECTION_KEY_FOO)` index magic
- **Automatic migrations** - schema versioning made easier
- **Coroutines/Flow support** - reactive data access
- **Much less boilerplate** - ~60-70% less code

## Current Database Classes

| Database | File | Purpose | Complexity |
|----------|------|---------|------------|
| **EventsStorage** | `eventsstorage/EventsStorage.kt` | Active event notifications | High - 600+ lines in impl |
| **DismissedEventsStorage** | `dismissedeventsstorage/DismissedEventsStorage.kt` | Dismissed event history | Medium |
| **MonitorStorage** | `monitorstorage/MonitorStorage.kt` | Calendar alert monitoring | Medium |
| **CalendarChangeRequestsStorage** | `calendareditor/storage/` | Pending calendar edits | Low - **DEPRECATED** |

**Note:** `BTCarModeStorage` was previously listed here but is NOT a SQLite database - it uses SharedPreferences via `PersistentStorageBase`. No Room migration needed.

## Current Pain Points

### 1. Manual Cursor Mapping (Error-Prone)
```kotlin
// Current: Magic index numbers, easy to get wrong
private fun cursorToEventRecord(cursor: Cursor): EventAlertRecord {
    return EventAlertRecord(
        calendarId = cursor.getLong(PROJECTION_KEY_CALENDAR_ID),  // index 0
        eventId = cursor.getLong(PROJECTION_KEY_EVENTID),          // index 1
        alertTime = cursor.getLong(PROJECTION_KEY_ALERT_TIME),     // index 2
        // ... 20+ more fields with magic indices
    )
}
```

### 2. String-Based SQL (No Compile-Time Checking)
```kotlin
// Current: Typos only caught at runtime
val CREATE_TABLE = "CREATE TABLE $TABLE_NAME ( " +
    "$KEY_CALENDAR_ID INTEGER, " +
    "$KEY_EVENTID INTEGER, " +
    // ... concatenated strings
```

### 3. Manual ContentValues (Verbose)
```kotlin
// Current: ~50 lines for one entity
private fun eventRecordToContentValues(event: EventAlertRecord): ContentValues {
    val values = ContentValues()
    values.put(KEY_CALENDAR_ID, event.calendarId)
    values.put(KEY_EVENTID, event.eventId)
    values.put(KEY_ALERT_TIME, event.alertTime)
    // ... 25+ more fields
    return values
}
```

### 4. Reserved Fields for Future Use
```kotlin
// Current: Wasteful schema design from pre-Room era
values.put(KEY_RESERVED_INT2, 0)
values.put(KEY_RESERVED_INT3, 0)
// ... 8 reserved int fields, 2 reserved string fields
```

## What Room Migration Would Look Like

### Entity Definition
```kotlin
@Entity(
    tableName = "events",
    primaryKeys = ["eventId", "instanceStartTime"]
)
data class EventAlertEntity(
    val calendarId: Long,
    val eventId: Long,
    val alertTime: Long,
    val notificationId: Int,
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long,
    val instanceStartTime: Long,
    val instanceEndTime: Long,
    val location: String,
    val snoozedUntil: Long,
    val lastStatusChangeTime: Long,
    val displayStatus: Int,
    val color: Int,
    val isRepeating: Boolean,
    val isAllDay: Boolean,
    val origin: Int,
    val timeFirstSeen: Long,
    val eventStatus: Int,
    val attendanceStatus: Int,
    val flags: Long
)
```

### DAO (Data Access Object)
```kotlin
@Dao
interface EventAlertDao {
    @Query("SELECT * FROM events")
    fun getAllEvents(): List<EventAlertEntity>
    
    @Query("SELECT * FROM events WHERE eventId = :eventId AND instanceStartTime = :instanceStart")
    fun getEvent(eventId: Long, instanceStart: Long): EventAlertEntity?
    
    @Query("SELECT * FROM events WHERE eventId = :eventId")
    fun getEventInstances(eventId: Long): List<EventAlertEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEvent(event: EventAlertEntity): Long
    
    @Update
    fun updateEvent(event: EventAlertEntity): Int
    
    @Delete
    fun deleteEvent(event: EventAlertEntity): Int
    
    @Query("DELETE FROM events")
    fun deleteAll()
    
    // Reactive version (optional, for future use)
    @Query("SELECT * FROM events")
    fun observeAllEvents(): Flow<List<EventAlertEntity>>
}
```

### Database Class
```kotlin
@Database(
    entities = [
        EventAlertEntity::class,
        DismissedEventEntity::class,
        MonitorAlertEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventAlertDao(): EventAlertDao
    abstract fun dismissedEventDao(): DismissedEventDao
    abstract fun monitorAlertDao(): MonitorAlertDao
}
```

## Migration Strategy

### Phase 1: Add Room Alongside Existing (Low Risk)
1. Add Room dependencies
2. Create Entity and DAO for one storage (start with simplest SQLite: `MonitorStorage`)
3. Run both implementations in parallel, verify data matches
4. Switch to Room implementation

**Note:** Originally suggested BTCarModeStorage but it's SharedPreferences, not SQLite. MonitorStorage is the actual simplest SQLite database (V1 only, no migration history).

### Phase 2: Migrate Critical Storage
1. `EventsStorage` - most important, most complex
2. Need to handle existing V6→V7→V8→V9 migration history
3. Room's `Migration` class handles this cleanly

### Phase 3: Complete Migration
1. `DismissedEventsStorage`
2. `MonitorStorage`
3. `CalendarChangeRequestsStorage`

### Schema Migration from Current to Room
```kotlin
val MIGRATION_LEGACY_TO_ROOM = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Room can work with existing tables if schema matches
        // Just rename table if needed, or keep as-is
        database.execSQL("ALTER TABLE eventsV9 RENAME TO events")
    }
}
```

## Effort Estimate

| Task | Time | Risk |
|------|------|------|
| Add dependencies, setup | 1-2 hours | Low |
| MonitorStorage (pilot) | 6-8 hours | Low |
| EventsStorage | 8-12 hours | Medium - most complex |
| DismissedEventsStorage | 4-6 hours | Low |
| MonitorStorage | 4-6 hours | Low |
| Testing & verification | 8-12 hours | Medium |
| **Total** | **~30-40 hours** | Medium |

## Benefits Summary

| Current | With Room |
|---------|-----------|
| ~600 lines for EventsStorageImplV9 | ~100 lines (Entity + DAO) |
| Runtime SQL errors | Compile-time verification |
| Manual cursor index tracking | Automatic mapping |
| Manual ContentValues | Automatic serialization |
| Custom migration code | Built-in migration support |
| No reactive support | Flow/LiveData integration |

## Dependencies to Add

```kotlin
// build.gradle.kts (app)
dependencies {
    val roomVersion = "2.6.1"
    
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")  // Coroutines support
    ksp("androidx.room:room-compiler:$roomVersion")  // Use KSP, not kapt
    
    testImplementation("androidx.room:room-testing:$roomVersion")
}
```

Note: The app already uses `io.requery.android.database.sqlite.SQLiteDatabase` (requery's SQLite wrapper), not the standard Android SQLite. Room uses `androidx.sqlite` under the hood, which is compatible.

## Considerations

### Pros
- Significantly less code to maintain
- Compile-time safety for all SQL
- Standard Android Jetpack library (long-term support)
- Built-in testing utilities
- Better migration tooling

### Cons
- Initial migration effort (~30-40 hours)
- Need to maintain compatibility during transition
- Existing interfaces (`EventsStorageInterface`, etc.) work well with current DI
- Learning curve if unfamiliar with Room

### Compatibility with Existing Architecture
- `EventsStorageInterface` can remain unchanged
- `ApplicationController.eventsStorageProvider` pattern still works
- Room DAOs implement the same operations, just cleaner

## Recommendation

**Priority: Medium-High**

This is a good candidate for migration because:
1. Database code is mission-critical (event notifications)
2. Current code has lots of boilerplate that could hide bugs
3. Type safety would catch issues at compile time
4. Reduces maintenance burden long-term

**Suggested approach:** Start with `MonitorStorage` as a pilot (simplest SQLite database, V1 only), then `DismissedEventsStorage` (V1→V2), and finally `EventsStorage` (V6→V9).

**See also:** [Database Modernization Plan](database_modernization_plan.md) for the detailed implementation plan with risk mitigation strategies.

## References

- [Room documentation](https://developer.android.com/training/data-storage/room)
- [Migrating to Room](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Room with Kotlin Coroutines](https://developer.android.com/training/data-storage/room/async-queries#kotlin)
- [Testing Room](https://developer.android.com/training/data-storage/room/testing-db)

