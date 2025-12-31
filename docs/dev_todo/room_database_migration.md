# Room Database Migration

## Status: Phase 3 IN PROGRESS 🚧 - EventsStorage migration

> **Note:** This document contains implementation details and patterns discovered during migration. For the overall plan, see **[Database Modernization Plan](database_modernization_plan.md)**.

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

### Phase 2: DismissedEventsStorage (Medium Complexity)
1. `DismissedEventsStorage` - Medium complexity, V1→V2 migration
2. Builds confidence with migration patterns before tackling highest-risk database
3. High test coverage (96%) provides safety net

### Phase 3: EventsStorage (Highest Risk - LAST)
1. `EventsStorage` - most important, most complex
2. Tackle LAST after patterns are proven on simpler databases
3. Need to handle existing V6→V7→V8→V9 migration history
4. Room's `Migration` class handles schema versioning

### Deprecated (Skip)
- `CalendarChangeRequestsStorage` - DEPRECATED, remove instead of migrate

### Schema Migration from Current to Room

> ⚠️ **Important:** Standard Room migrations only work for version upgrades AFTER Room has opened the database. For pre-existing SQLiteOpenHelper databases, see the next section.

```kotlin
val MIGRATION_LEGACY_TO_ROOM = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Room can work with existing tables if schema matches
        // Just rename table if needed, or keep as-is
        database.execSQL("ALTER TABLE eventsV9 RENAME TO events")
    }
}
```

### Copy-Based Migration Pattern (Recommended)

Instead of modifying the legacy database in-place, we use a **separate database file** for Room and copy data from the legacy DB on first use. This is safer because:

1. **Legacy DB preserved** - original database is never modified, can always fall back
2. **Retry on failure** - if migration fails, next app version can try again
3. **No schema conflicts** - Room creates its own clean schema in new file
4. **Graceful degradation** - app continues working with legacy storage if Room fails

```
Copy-Based Migration Flow:
┌─────────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│ Room creates new DB │ ──► │ Copy from legacy │ ──► │ Validate counts │
│ (RoomCalendarMonitor)│     │ (CalendarMonitor) │     │ (data integrity)│
└─────────────────────┘     └──────────────────┘     └─────────────────┘
         │                                                    │
         │                                                    ▼
         │                                          ┌─────────────────┐
         │                                          │ On failure:     │
         │                                          │ throw Migration │
         │                                          │ Exception       │
         └──────────────────────────────────────────└─────────────────┘
                                                              │
                                                              ▼
                                                    ┌─────────────────┐
                                                    │ Fall back to    │
                                                    │ LegacyStorage   │
                                                    └─────────────────┘
```

#### Implementation Pattern

```kotlin
// Storage wrapper with delegation
class MonitorStorage private constructor(
    result: Pair<MonitorStorageInterface, Boolean>
) : MonitorStorageInterface by result.first, Closeable {
    
    private val delegate: MonitorStorageInterface = result.first
    val isUsingRoom: Boolean = result.second
    
    constructor(context: Context) : this(createStorage(context))
    
    companion object {
        private fun createStorage(context: Context): Pair<MonitorStorageInterface, Boolean> {
            return try {
                Pair(RoomMonitorStorage(context), true)
            } catch (e: MigrationException) {
                DevLog.error(LOG_TAG, "Room migration failed, falling back: ${e.message}")
                Pair(LegacyMonitorStorage(context), false)
            }
        }
    }
}

// Database with copy-from-legacy logic
@Database(entities = [MonitorAlertEntity::class], version = 1)
abstract class MonitorDatabase : RoomDatabase() {
    companion object {
        const val DATABASE_NAME = "RoomCalendarMonitor"      // NEW Room DB
        const val LEGACY_DATABASE_NAME = "CalendarMonitor"   // OLD legacy DB
        
        fun getInstance(context: Context): MonitorDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = buildDatabase(context)
                copyFromLegacyIfNeeded(context, db)  // Throws MigrationException on failure
                INSTANCE = db
                db
            }
        }
        
        private fun copyFromLegacyIfNeeded(context: Context, roomDb: MonitorDatabase) {
            val legacyFile = context.getDatabasePath(LEGACY_DATABASE_NAME)
            if (!legacyFile.exists()) return  // Fresh install
            
            val dao = roomDb.monitorAlertDao()
            if (dao.getAll().isNotEmpty()) return  // Already migrated
            
            // Read from legacy using old impl, insert into Room
            val legacyData = readFromLegacyDatabase(legacyFile)
            dao.insertAll(legacyData.map { Entity.from(it) })
            
            // Validate row counts match
            if (dao.getAll().size != legacyData.size) {
                throw MigrationException("Row count mismatch - data loss detected!")
            }
        }
    }
}
```

#### Entity with Index Declaration

```kotlin
@Entity(
    tableName = "manualAlertsV1",
    primaryKeys = ["eventId", "alertTime", "instanceStart"],
    indices = [Index(
        value = ["eventId", "alertTime", "instanceStart"],
        unique = true,
        name = "manualAlertsV1IdxV1"  // Match legacy index name
    )]
)
data class MonitorAlertEntity(...)
```

#### MigrationException Handling

Each storage module has its own `MigrationException` type that signals the wrapper to fall back:

- `MigrationException` (MonitorStorage)
- `DismissedEventsMigrationException` (DismissedEventsStorage)

These are caught specifically (not broad `Exception`) to ensure unexpected errors still propagate.

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

## Dependencies (Validated in POC)

```groovy
// Actual versions used - see database_modernization_plan.md for full config
def roomVersion = "2.8.4"

implementation "androidx.room:room-runtime:$roomVersion"
implementation "androidx.room:room-ktx:$roomVersion"
ksp "androidx.room:room-compiler:$roomVersion"

// Required for Room 2.8.x
implementation "androidx.sqlite:sqlite:2.6.2"
implementation "androidx.sqlite:sqlite-framework:2.6.2"
```

**Important:** The app uses `requery:sqlite-android` with cr-sqlite extension. Room integration requires a custom `SupportSQLiteOpenHelper.Factory` - see POC code in `android/app/src/androidTest/java/com/github/quarck/calnotify/database/poc/`.

## Considerations

### Pros
- Significantly less code to maintain
- Compile-time safety for all SQL
- Standard Android Jetpack library (long-term support)
- Built-in testing utilities
- Better migration tooling

### Cons
- Initial migration effort (~50-68 hours total, including POC)
- Need to maintain compatibility during transition
- Existing interfaces (`EventsStorageInterface`, etc.) work well with current DI
- Learning curve if unfamiliar with Room

### Compatibility with Existing Architecture
- `EventsStorageInterface` can remain unchanged
- `ApplicationController.eventsStorageProvider` pattern still works
- Room DAOs implement the same operations, just cleaner

### CR-SQLite Integration (Solved in POC)
The main technical challenge was integrating Room with cr-sqlite extension:

- **Solution:** Custom `SupportSQLiteOpenHelper.Factory` (`CrSqliteRoomFactory`)
- **APK Packaging:** Required `extractNativeLibs="true"` and `useLegacyPackaging=true`
- **Library Naming:** Renamed to `crsqlite_requery.so` to avoid conflict with React Native's version
- **Extension Loading:** Must use `SQLiteCustomExtension`, NOT `load_extension()` SQL

See [CR-SQLite + Room Testing Guide](../testing/crsqlite_room_testing.md) for full details.

## Recommendation

**Priority: Medium-High** | **Status: Phase 3 IN PROGRESS 🚧**

This is a good candidate for migration because:
1. Database code is mission-critical (event notifications)
2. Current code has lots of boilerplate that could hide bugs
3. Type safety would catch issues at compile time
4. Reduces maintenance burden long-term

### Progress (Dec 2025)

#### POC Validated ✅
- ✅ Room works with cr-sqlite extension via custom `SupportSQLiteOpenHelper.Factory`
- ✅ All 8 POC tests pass (CRUD, extension loading, finalize, coexistence)
- ✅ APK packaging requirements documented

#### Phase 1: MonitorStorage Migration ✅
- ✅ Created `MonitorAlertEntity`, `MonitorAlertDao`, `MonitorDatabase`
- ✅ Implemented copy-based migration (separate Room DB, copy from legacy)
- ✅ Fallback to `LegacyMonitorStorage` on `MigrationException`
- ✅ Live upgrade test passed (legacy DB → Room-managed DB, data preserved)
- ✅ Migration tests pass locally (3/3)

**Key files:**
- `monitorstorage/MonitorAlertEntity.kt` - Room entity with index
- `monitorstorage/MonitorAlertDao.kt` - Data access object  
- `monitorstorage/MonitorDatabase.kt` - Database with copy-from-legacy migration
- `monitorstorage/RoomMonitorStorage.kt` - Room implementation
- `monitorstorage/LegacyMonitorStorage.kt` - Fallback SQLiteOpenHelper implementation
- `monitorstorage/MonitorStorage.kt` - Wrapper with delegation pattern

#### Phase 2: DismissedEventsStorage Migration ✅
- ✅ Created `DismissedEventEntity`, `DismissedEventDao`, `DismissedEventsDatabase`
- ✅ Implemented copy-based migration pattern (same as MonitorStorage)
- ✅ Fallback to `LegacyDismissedEventsStorage` on `DismissedEventsMigrationException`
- ✅ Migration tests pass

**Key files:**
- `dismissedeventsstorage/DismissedEventEntity.kt` - Room entity with index
- `dismissedeventsstorage/DismissedEventDao.kt` - Data access object
- `dismissedeventsstorage/DismissedEventsDatabase.kt` - Database with copy-from-legacy migration
- `dismissedeventsstorage/RoomDismissedEventsStorage.kt` - Room implementation
- `dismissedeventsstorage/LegacyDismissedEventsStorage.kt` - Fallback SQLiteOpenHelper implementation
- `dismissedeventsstorage/DismissedEventsStorage.kt` - Wrapper with delegation pattern

#### Phase 3: EventsStorage Migration 🚧
- ✅ Created `EventAlertEntity`, `EventAlertDao`, `EventsDatabase`
- ✅ Implemented copy-based migration pattern (30 columns)
- ✅ Fallback to `LegacyEventsStorage` on `EventsMigrationException`
- 🚧 Testing in progress

**Key files:**
- `eventsstorage/EventAlertEntity.kt` - Room entity with 30 columns
- `eventsstorage/EventAlertDao.kt` - Data access object
- `eventsstorage/EventsDatabase.kt` - Database with copy-from-legacy migration
- `eventsstorage/RoomEventsStorage.kt` - Room implementation
- `eventsstorage/LegacyEventsStorage.kt` - Fallback SQLiteOpenHelper implementation
- `eventsstorage/EventsStorage.kt` - Wrapper with delegation pattern

**Next step:** Test and verify all three migrations work together

**See:** [Database Modernization Plan](database_modernization_plan.md) for the detailed implementation plan.

## References

- [Room documentation](https://developer.android.com/training/data-storage/room)
- [Migrating to Room](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Room with Kotlin Coroutines](https://developer.android.com/training/data-storage/room/async-queries#kotlin)
- [Testing Room](https://developer.android.com/training/data-storage/room/testing-db)

