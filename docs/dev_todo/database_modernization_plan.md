# Database Modernization Plan

## Status: Phase 1 COMPLETE ✅ - Ready for Phase 2

## Executive Summary

This document outlines a **high-risk, high-reward** database modernization effort to migrate from raw SQLite with manual cursor handling to Android Room ORM. Given the critical nature of database code (notifications, event tracking), this plan prioritizes **safety and incremental validation** over speed.

**Risk Level: HIGH** - Database corruption or data loss would break core app functionality.

---

## Library Evaluation

### Candidates Evaluated

| Library | Type | Target Platform | Verdict |
|---------|------|-----------------|---------|
| **Room** | ORM | Android-specific | ✅ **RECOMMENDED** |
| **jOOQ** | SQL DSL | JVM Server-side | ❌ Not for Android |
| **SQLDelight** | SQL-first | Kotlin Multiplatform | ⚠️ Alternative option |
| **Exposed** | Kotlin DSL | JVM Server-side | ❌ Not for Android |

### Why Room (Not jOOQ)

While [jOOQ](https://www.jooq.org/) is an excellent library for type-safe SQL on the JVM, it's designed for **server-side applications**, not mobile:

| Factor | Room | jOOQ |
|--------|------|------|
| Android-specific design | ✅ Yes | ❌ No |
| Mobile footprint | ✅ Minimal | ❌ Large (many deps) |
| Android lifecycle integration | ✅ LiveData, Flow, ViewModel | ❌ None |
| Official Google support | ✅ Yes (Jetpack) | ❌ No |
| Migration tooling for SQLite | ✅ Excellent | ⚠️ Generic |
| License | ✅ Apache 2.0 | ⚠️ Commercial for some DBs |
| Long-term Android support | ✅ Guaranteed | ❌ Not prioritized |

**Conclusion:** Room is the clear choice for Android database modernization.

---

## Current Database Inventory

### Actual SQLite Databases (Require Migration)

| Database | File | Version History | Complexity | Coverage | Priority |
|----------|------|-----------------|------------|----------|----------|
| **MonitorStorage** | `monitorstorage/` | V1 only | Low | 730/925 (79%) | **1st (Pilot)** ✅ |
| **DismissedEventsStorage** | `dismissedeventsstorage/` | V1→V2 | Medium | 597/623 (96%) | **2nd** |
| **EventsStorage** | `eventsstorage/` | V6→V7→V8→V9 | **High** | 1015/1137 (89%) | **3rd (Last)** |

### NOT SQLite (Skip)

| Storage | Type | Notes |
|---------|------|-------|
| **BTCarModeStorage** | SharedPreferences | Uses `PersistentStorageBase`, not SQLite |

### Deprecated (Do NOT Migrate)

| Database | Status | Action |
|----------|--------|--------|
| **CalendarChangeRequestsStorage** | `@Deprecated` | Remove entirely (see `dev_todo/deprecated_features.md`) |

---

## Current Technical Context

### SQLite Stack
- **Wrapper:** `requery:sqlite-android:3.45.0` (not standard Android SQLite)
- **Extension:** cr-sqlite (`sqlite3_crsqlite_init`) for conflict-free replication
- **Custom:** `SQLiteOpenHelper` wrapper with `customUse` pattern

### Key Interfaces (Must Preserve)
```kotlin
// These interfaces are the public API - implementations change, interfaces don't
interface EventsStorageInterface { ... }
interface MonitorStorageInterface { ... }
interface DismissedEventsStorageInterface { ... }
```

### Dependency Injection
- `ApplicationController.eventsStorageProvider` pattern
- Easy to swap implementations behind interfaces

---

## Risk Mitigation Strategy

### 1. Parallel Implementation Pattern
```kotlin
// During migration, both implementations exist
class MonitorStorage(context: Context) : MonitorStorageInterface {
    // Feature flag or config determines which to use
    private val impl: MonitorStorageInterface = if (useRoom) {
        RoomMonitorStorage(context)
    } else {
        LegacyMonitorStorage(context)  // Renamed from MonitorStorageImplV1
    }
    
    // Delegate all calls
    override val alerts get() = impl.alerts
    // ...
}
```

### 2. Data Verification Layer
```kotlin
// Run both implementations and compare results during testing
class VerifyingMonitorStorage(context: Context) : MonitorStorageInterface {
    private val legacy = LegacyMonitorStorage(context)
    private val room = RoomMonitorStorage(context)
    
    override val alerts: List<MonitorEventAlertEntry>
        get() {
            val legacyResult = legacy.alerts
            val roomResult = room.alerts
            require(legacyResult == roomResult) { 
                "Data mismatch! Legacy: $legacyResult, Room: $roomResult" 
            }
            return roomResult
        }
}
```

### 3. Migration Tests (Required Before Any Migration)
```kotlin
@Test
fun testMigrationFromV1ToRoom() {
    // 1. Create V1 database with known test data
    // 2. Run Room migration
    // 3. Verify all data accessible via Room
    // 4. Verify data matches original
}

@Test
fun testRoundTripDataIntegrity() {
    // 1. Add data via legacy implementation
    // 2. Read via Room implementation
    // 3. Verify equality
}
```

### 4. Backup Before Migration
```kotlin
// Production migration includes backup
fun migrateToRoom(context: Context) {
    // 1. Backup existing DB file
    val dbFile = context.getDatabasePath("CalendarMonitor")
    val backupFile = File(context.filesDir, "CalendarMonitor.backup")
    dbFile.copyTo(backupFile, overwrite = true)
    
    // 2. Run migration
    // 3. Verify
    // 4. Only delete backup after successful verification
}
```

---

## Phase 0: POC Validation ✅ COMPLETE

### Objective
Prove that Room can work with cr-sqlite extension before committing to full migration.

### What We Built
- `CrSqliteRoomFactory` - Uses `RequerySQLiteOpenHelperFactory.ConfigurationOptions` to add cr-sqlite
- `CrSqliteFinalizeWrapper` - Thin wrapper ensuring `crsql_finalize()` is called on close
- `RoomPocDatabase` / `RoomPocEntity` / `RoomPocDao` - Minimal Room setup for testing

Note: We leverage requery's built-in Room support (`RequerySQLiteOpenHelperFactory`) rather than
implementing the entire `SupportSQLiteOpenHelper` interface ourselves. See [requery docs](https://github.com/requery/sqlite-android#support-library-compatibility).

### Tests Implemented (All Pass ✅)
1. **Diagnostic test** - cr-sqlite works without Room (validates base setup)
2. **Room CRUD** - Basic create/read/update/delete operations
3. **cr-sqlite extension loaded** - `crsql_db_version()` works through Room
4. **Bulk operations** - insertAll, deleteAll, queries with LIKE
5. **crsql_finalize on close** - Proper cleanup on database close
6. **Behavior parity** - Standard Room vs cr-sqlite Room produce identical results
7. **Raw SQL execution** - Can execute arbitrary SQL (needed for CRDT ops)
8. **cr-sqlite functions** - `crsql_db_version()`, `crsql_site_id()` work

### Key Technical Findings

#### APK Packaging Requirements
```xml
<!-- AndroidManifest.xml -->
<application android:extractNativeLibs="true" ...>
```

```groovy
// build.gradle
packagingOptions {
    jniLibs { useLegacyPackaging = true }
    pickFirst '**/*.so'
}
```

#### Library Naming
- Renamed our cr-sqlite build to `crsqlite_requery.so` to avoid conflict with React Native's `crsqlite.so`
- These are built against different SQLite versions and are NOT interchangeable

#### Extension Loading
- ✅ Use `SQLiteCustomExtension("crsqlite_requery", "sqlite3_crsqlite_init")` in `createConfiguration()`
- ❌ Do NOT use `load_extension()` SQL - fails with "error during initialization"

#### Room Version
- Using Room 2.8.4 with KSP (not KAPT)
- Requires `androidx.sqlite:sqlite:2.6.2` and `androidx.sqlite:sqlite-framework:2.6.2`

### Documentation
- Full setup guide: `docs/testing/crsqlite_room_testing.md`
- POC test code: `android/app/src/androidTest/java/com/github/quarck/calnotify/database/poc/`

### Known Issues (Non-Blocking)
- `sqlite3_close failed: 5` warnings during test teardown (Room's connection pooling vs cr-sqlite finalization)
- Tests pass despite warnings; GC cleans up properly

---

## Phase 1: MonitorStorage (Pilot)

### Why MonitorStorage First?
1. **Simplest schema** - Single version (V1), no migration history
2. **Lower risk** - Not the primary event storage
3. **Good test coverage** - 79% instruction coverage already
4. **Isolated** - Minimal dependencies on other components

### Current Schema Analysis

```kotlin
// Current: MonitorStorageImplV1.kt
TABLE_NAME = "monitorV1"
PRIMARY KEY (eventId, alertTime, instanceStartTime)

Columns:
- KEY_EVENTID (INTEGER)
- KEY_ALERT_TIME (INTEGER)
- KEY_ALL_DAY (INTEGER) 
- KEY_INSTANCE_START (INTEGER)
- KEY_INSTANCE_END (INTEGER)
- KEY_MANUAL_ALERT (INTEGER)
- KEY_WAS_HANDLED (INTEGER)
```

### Room Entity Design

```kotlin
@Entity(
    tableName = "monitor_alerts",
    primaryKeys = ["eventId", "alertTime", "instanceStartTime"]
)
data class MonitorAlertEntity(
    val eventId: Long,
    val alertTime: Long,
    val isAllDay: Boolean,  // Room handles Boolean ↔ Integer
    val instanceStartTime: Long,
    val instanceEndTime: Long,
    val alertCreatedByUs: Boolean,
    val wasHandled: Boolean
) {
    // Conversion functions to/from MonitorEventAlertEntry
    fun toAlertEntry() = MonitorEventAlertEntry(
        eventId = eventId,
        alertTime = alertTime,
        isAllDay = isAllDay,
        instanceStartTime = instanceStartTime,
        instanceEndTime = instanceEndTime,
        alertCreatedByUs = alertCreatedByUs,
        wasHandled = wasHandled
    )
    
    companion object {
        fun fromAlertEntry(entry: MonitorEventAlertEntry) = MonitorAlertEntity(
            eventId = entry.eventId,
            alertTime = entry.alertTime,
            isAllDay = entry.isAllDay,
            instanceStartTime = entry.instanceStartTime,
            instanceEndTime = entry.instanceEndTime,
            alertCreatedByUs = entry.alertCreatedByUs,
            wasHandled = entry.wasHandled
        )
    }
}
```

### Room DAO Design

```kotlin
@Dao
interface MonitorAlertDao {
    @Query("SELECT * FROM monitor_alerts")
    fun getAllAlerts(): List<MonitorAlertEntity>
    
    @Query("SELECT * FROM monitor_alerts WHERE eventId = :eventId AND alertTime = :alertTime AND instanceStartTime = :instanceStart")
    fun getAlert(eventId: Long, alertTime: Long, instanceStart: Long): MonitorAlertEntity?
    
    @Query("SELECT * FROM monitor_alerts WHERE eventId = :eventId AND instanceStartTime = :instanceStart")
    fun getInstanceAlerts(eventId: Long, instanceStart: Long): List<MonitorAlertEntity>
    
    @Query("SELECT MIN(alertTime) FROM monitor_alerts WHERE alertTime > :since")
    fun getNextAlertTime(since: Long): Long?
    
    @Query("SELECT * FROM monitor_alerts WHERE alertTime = :time")
    fun getAlertsAt(time: Long): List<MonitorAlertEntity>
    
    @Query("SELECT * FROM monitor_alerts WHERE instanceStartTime BETWEEN :scanFrom AND :scanTo")
    fun getAlertsForInstanceStartRange(scanFrom: Long, scanTo: Long): List<MonitorAlertEntity>
    
    @Query("SELECT * FROM monitor_alerts WHERE alertTime BETWEEN :scanFrom AND :scanTo")
    fun getAlertsForAlertRange(scanFrom: Long, scanTo: Long): List<MonitorAlertEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(alert: MonitorAlertEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(alerts: List<MonitorAlertEntity>)
    
    @Update
    fun update(alert: MonitorAlertEntity)
    
    @Update
    fun updateAll(alerts: List<MonitorAlertEntity>)
    
    @Delete
    fun delete(alert: MonitorAlertEntity)
    
    @Delete
    fun deleteAll(alerts: List<MonitorAlertEntity>)
    
    @Query("DELETE FROM monitor_alerts WHERE eventId = :eventId AND alertTime = :alertTime AND instanceStartTime = :instanceStart")
    fun deleteByKey(eventId: Long, alertTime: Long, instanceStart: Long)
}
```

### Phase 1 Tasks

1. **Add Room dependencies** (low risk)
2. **Create Entity and DAO** (low risk)
3. **Create `RoomMonitorStorage` implementing `MonitorStorageInterface`** (low risk)
4. **Write migration tests** (medium risk - validates approach)
5. **Write data verification tests** (medium risk)
6. **Create `VerifyingMonitorStorage` for parallel validation** (low risk)
7. **Run integration tests with verification enabled** (validation)
8. **Switch to Room implementation** (high risk - final cutover)
9. **Remove legacy implementation after validation period** (cleanup)

---

## Phase 2: DismissedEventsStorage

### Why DismissedEventsStorage Second?
1. **Medium complexity** - Only V1→V2 migration (simpler than EventsStorage)
2. **High test coverage** - 96% already covered
3. **Builds confidence** - Establishes patterns before tackling high-risk storage
4. **Lower risk than EventsStorage** - Not the primary notification database

### Migration Complexity: Medium
- Has V1→V2 migration history
- Need Room migration from V2

### Approach
- Same parallel implementation pattern as MonitorStorage
- Room schema matches V2
- Pre-Room migration if needed (check for NOT NULL on PKs)
- Migration class handles legacy→Room transition

---

## Phase 3: EventsStorage (Most Critical - LAST)

### Why EventsStorage Last?
1. **Highest complexity** - V6→V7→V8→V9 migration history
2. **Highest risk** - Core notification functionality depends on this
3. **Patterns established** - By now we've migrated 2 databases successfully
4. **Confidence built** - Pre-Room migration pattern proven, edge cases known

### Migration Complexity: HIGH
- V6→V7→V8→V9 migration history
- Most complex schema (20+ fields)
- Reserved fields for future use
- Composite primary key (eventId, instanceStartTime)
- **Core app functionality depends on this**

### Special Considerations
1. **Extended test coverage period** - Run parallel validation for longer
2. **Staged rollout** - Consider feature flag for gradual migration
3. **Comprehensive backup** - Full database backup before any migration
4. **Rollback plan** - Ability to revert to legacy if issues found

---

## Dependencies (Validated in POC)

```groovy
// build.gradle (root)
classpath "com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.1.20-1.0.31"

// build.gradle (app)
plugins {
    id 'com.google.devtools.ksp'
}

dependencies {
    def roomVersion = "2.8.4"
    
    implementation "androidx.room:room-runtime:$roomVersion"
    implementation "androidx.room:room-ktx:$roomVersion"
    ksp "androidx.room:room-compiler:$roomVersion"
    
    // Required for Room 2.8.x
    implementation "androidx.sqlite:sqlite:2.6.2"
    implementation "androidx.sqlite:sqlite-framework:2.6.2"
    
    // Testing
    androidTestImplementation "androidx.room:room-testing:$roomVersion"
}

// Force sqlite versions to avoid conflicts
configurations.all {
    resolutionStrategy {
        force 'androidx.sqlite:sqlite:2.6.2'
        force 'androidx.sqlite:sqlite-framework:2.6.2'
    }
}
```

### CR-SQLite Integration (VALIDATED ✅)

Room works with cr-sqlite via custom `SupportSQLiteOpenHelper.Factory`:

```kotlin
// See: CrSqliteSupportHelper.kt (contains CrSqliteRoomFactory + CrSqliteFinalizeWrapper)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, "app_database")
                .openHelperFactory(CrSqliteRoomFactory())  // Uses RequerySQLiteOpenHelperFactory + cr-sqlite
                .build()
        }
    }
}
```

---

## Success Criteria

### Phase 0 (POC) ✅ COMPLETE
- [x] Room CRUD operations work with custom factory
- [x] cr-sqlite extension loads correctly  
- [x] `crsql_finalize()` called on close
- [x] Room and legacy implementations can coexist
- [x] Raw SQL execution works
- [x] All 8 POC tests pass

### Phase 1 (MonitorStorage)
- [ ] All existing MonitorStorage tests pass with Room implementation
- [ ] Parallel verification shows 0 data mismatches
- [ ] Migration from V1 schema works correctly
- [ ] Performance is equivalent or better
- [ ] No regressions in calendar monitoring functionality

### Phase 2 (DismissedEventsStorage)
- [ ] V1→V2→Room migration works
- [ ] All dismissed event tests pass
- [ ] Dismissed events UI displays correctly

### Phase 3 (EventsStorage)
- [ ] V6→V7→V8→V9→Room migration works
- [ ] All event notification tests pass
- [ ] Snooze/dismiss functionality works
- [ ] No data loss during migration
- [ ] Performance equivalent or better

---

## Timeline Estimate

| Phase | Effort | Risk | Status |
|-------|--------|------|--------|
| Phase 0: POC Validation | ~8 hours | Low | ✅ COMPLETE |
| Phase 1: MonitorStorage | 8-12 hours | Low | Ready to start |
| Phase 2: DismissedEventsStorage | 6-8 hours | Medium | Pending |
| Phase 3: EventsStorage | 16-24 hours | **High** | Pending |
| Testing & Verification | 12-16 hours | Medium | Ongoing |
| **Total** | **~50-68 hours** | Medium-High | In Progress |

**Note:** Phase 0 took longer than expected due to APK packaging issues with native libraries, but these learnings are now documented and won't recur.

---

## Rollback Plan

If issues are discovered after migration:

1. **Immediate:** Switch back to legacy implementation via feature flag
2. **Data Recovery:** Restore from pre-migration backup
3. **Analysis:** Debug Room implementation with data that caused issues
4. **Retry:** Fix issues and re-attempt migration

---

## Questions Resolved by POC

1. ✅ **CR-SQLite Integration:** Yes, Room works with cr-sqlite via `RequerySQLiteOpenHelperFactory.ConfigurationOptions` (`CrSqliteRoomFactory`)

2. ✅ **Test Environment:** Yes, Room tests work. Note: Required `extractNativeLibs="true"` and `useLegacyPackaging=true` for native libs.

## Questions Remaining

3. **Migration Validation:** How do we validate migration on existing user databases without risking their data?

4. **Deprecation Timing:** Should we remove CalendarChangeRequestsStorage before or after Room migration?

---

## References

### Internal Documentation
- [cr-sqlite + Room Testing Guide](../testing/crsqlite_room_testing.md) - Setup and troubleshooting
- [CR-SQLite Build Guide](../build/CR_SQLITE_BUILD.md) - Building the extension

### External Documentation
- [Room Documentation](https://developer.android.com/training/data-storage/room)
- [Migrating to Room](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Room Testing](https://developer.android.com/training/data-storage/room/testing-db)
- [Room with Kotlin Coroutines](https://developer.android.com/training/data-storage/room/async-queries#kotlin)
- [Custom SupportSQLiteOpenHelper](https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteOpenHelper)

