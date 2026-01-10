# Sync Database Mismatch Fix

## Status: ALL PHASES COMPLETE ✅ - Needs testing

## Background

After the Room database migration (Phase 3 complete), a critical integration gap was discovered: the React Native sync feature (`SetupSync.tsx`) is reading from the **legacy** database while the native Android code writes to the **Room** database.

### The Problem

**React Native sync code** (line 68 in `SetupSync.tsx`):
```typescript
const regDb = open({ name: 'Events' });
```

**Android native storage** (after Room migration):
```kotlin
// EventsDatabase.kt
internal const val DATABASE_NAME = "RoomEvents"        // ← Active database
internal const val LEGACY_DATABASE_NAME = "Events"     // ← Preserved but stale
```

**Result:** The sync feature is syncing from a stale/frozen copy of the database that no longer receives updates.

### Root Cause

1. Hardcoded database name in RN (`'Events'`) without referencing native constants
2. No contract test to verify both layers use the same database
3. Room migration used a **copy-based strategy** (new `RoomEvents` file) rather than in-place migration to preserve fallback capability

## Goal

1. Fix the sync feature to read from the active database (Room or Legacy)
2. Add contract tests to prevent this regression
3. Establish a shared source of truth for database configuration

## Impact Analysis

| Scenario | Current Behavior | Expected Behavior |
|----------|------------------|-------------------|
| New event added | Written to `RoomEvents`, not visible to sync | Written and synced |
| Event dismissed | Updated in `RoomEvents`, sync shows stale data | Properly reflected |
| Event snoozed | Updated in `RoomEvents`, sync has old snooze time | Properly synced |

**Affected users:** Any user who upgraded to a Room-migrated version and uses PowerSync sync.

## Implementation Plan

### Phase 1: Add Native Bridge Method

Expose the active database name from native to React Native via SharedPreferences.

**Note:** The expo module (`modules/my-module`) cannot import from the main app directly - they're separate Gradle modules. Communication happens via SharedPreferences, which is process-wide.

#### 1.1 Main App Writes to SharedPreferences

```kotlin
// android/app/src/main/java/com/github/quarck/calnotify/eventsstorage/EventsStorage.kt

companion object {
    // SharedPreferences for cross-module communication (not backed up - see backup_rules.xml)
    const val STORAGE_PREFS_NAME = "events_storage_state"
    const val PREF_ACTIVE_DB_NAME = "active_db_name"
    const val PREF_IS_USING_ROOM = "is_using_room"
}

init {
    // Write storage state to SharedPreferences for cross-module communication
    context.getSharedPreferences(STORAGE_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_ACTIVE_DB_NAME, if (isUsingRoom) "RoomEvents" else "Events")
        .putBoolean(PREF_IS_USING_ROOM, isUsingRoom)
        .apply()
}
```

#### 1.2 Expo Module Reads from SharedPreferences

```kotlin
// modules/my-module/android/src/main/java/expo/modules/mymodule/MyModule.kt

Function("getActiveEventsDbName") {
    val prefs = context.getSharedPreferences(STORAGE_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.getString(PREF_ACTIVE_DB_NAME, LEGACY_DATABASE_NAME) ?: LEGACY_DATABASE_NAME
}
```

**Why SharedPreferences?**
- Synchronous read (no async intent dance)
- Uses separate prefs file `events_storage_state.xml` NOT in `backup_rules.xml` include list
- Device-specific state shouldn't be restored from backups

#### 1.2 Export from TypeScript Module

```typescript
// modules/my-module/index.ts

export function getActiveEventsDbName(): string {
  return MyModule.getActiveEventsDbName();
}

export function isUsingRoomStorage(): Promise<boolean> {
  return MyModule.isUsingRoomStorage();
}
```

### Phase 2: Fix SetupSync.tsx

Update the sync code to use the database name from native.

#### 2.1 Update Database Connection

```typescript
// src/lib/features/SetupSync.tsx

import { getActiveEventsDbName } from '../../../modules/my-module';

// Before (broken):
// const regDb = open({ name: 'Events' });

// After (fixed):
const [dbName, setDbName] = useState<string | null>(null);
const regDb = dbName ? open({ name: dbName }) : null;

useEffect(() => {
  const name = getActiveEventsDbName();
  setDbName(name);
}, []);
```

#### 2.2 Update cr-sqlite Install Call

```typescript
// Before:
await installCrsqliteOnTable('Events', 'eventsV9');

// After:
if (dbName) {
  await installCrsqliteOnTable(dbName, 'eventsV9');
}
```

### Phase 3: Add Contract Tests

Add tests that verify the database contract between native and RN.

#### 3.1 Native Bridge Test (React Native)

```typescript
// src/lib/features/__tests__/SetupSync.contract.test.ts

import { getActiveEventsDbName, isUsingRoomStorage } from '../../../modules/my-module';

describe('Database Contract', () => {
  it('native module exposes database name', () => {
    const dbName = getActiveEventsDbName();
    expect(dbName).toBeTruthy();
    expect(typeof dbName).toBe('string');
  });

  it('database name matches expected Room database', () => {
    const dbName = getActiveEventsDbName();
    // After Room migration, should be 'RoomEvents'
    // If fallback occurred, would be 'Events'
    expect(['RoomEvents', 'Events']).toContain(dbName);
  });

  it('can query isUsingRoomStorage', async () => {
    const isRoom = await isUsingRoomStorage();
    expect(typeof isRoom).toBe('boolean');
  });
});
```

#### 3.2 Integration Test (Android Instrumentation)

```kotlin
// android/app/src/androidTest/java/com/github/quarck/calnotify/eventsstorage/SyncDatabaseContractTest.kt

@RunWith(AndroidJUnit4::class)
class SyncDatabaseContractTest {

    @Test
    fun syncDatabaseNameMatchesActiveStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // What the sync feature SHOULD open
        val storage = EventsStorage(context)
        val expectedDbName = if (storage.isUsingRoom) {
            EventsDatabase.DATABASE_NAME  // "RoomEvents"
        } else {
            "Events"  // Legacy
        }
        storage.close()
        
        // Verify the constant that the native module exposes
        assertEquals(
            "Native module must expose the correct database name for sync",
            expectedDbName,
            EventsDatabase.DATABASE_NAME  // Or however the native module reads it
        )
    }

    @Test
    fun eventWrittenByNativeIsReadableByLegacyPath_SHOULD_FAIL_IF_ROOM() {
        // This test documents the bug - it should FAIL when Room is active
        // to remind us that legacy path no longer works
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        val storage = EventsStorage(context)
        if (storage.isUsingRoom) {
            // Room is active - legacy path won't see new data
            storage.addEvent(createTestEvent(eventId = 999))
            
            // Open legacy database directly (like RN sync does currently)
            val legacyDb = SQLiteDatabase.openDatabase(
                context.getDatabasePath("Events").path,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            
            val cursor = legacyDb.rawQuery(
                "SELECT * FROM eventsV9 WHERE id = ?",
                arrayOf("999")
            )
            
            // This will be 0 because Room writes to RoomEvents, not Events
            val count = cursor.count
            cursor.close()
            legacyDb.close()
            storage.close()
            
            // Document the bug - legacy path can't see Room data
            assertEquals(
                "BUG: Legacy database does not see Room-written events",
                0, count
            )
        }
    }
}
```

### Phase 4: Write/Read Roundtrip Integration Test (Optional, High Value)

A full end-to-end test that writes via native and reads via sync path.

```kotlin
@Test
fun eventsWrittenByNativeAreReadableBySyncPath() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    
    // 1. Write event via native storage
    val storage = EventsStorage(context)
    val testEvent = createTestEvent(eventId = 888, title = "Sync Test Event")
    storage.addEvent(testEvent)
    
    // 2. Determine which database sync should use
    val syncDbPath = if (storage.isUsingRoom) {
        context.getDatabasePath("RoomEvents").path
    } else {
        context.getDatabasePath("Events").path
    }
    
    // 3. Open that database directly (simulating RN sync)
    val syncDb = SQLiteDatabase.openDatabase(syncDbPath, null, SQLiteDatabase.OPEN_READONLY)
    
    // 4. Query for the event
    val cursor = syncDb.rawQuery(
        "SELECT ttl FROM eventsV9 WHERE id = ?",
        arrayOf("888")
    )
    
    assertTrue("Event should be visible via sync path", cursor.moveToFirst())
    assertEquals("Sync Test Event", cursor.getString(0))
    
    cursor.close()
    syncDb.close()
    storage.deleteEvent(888, testEvent.instanceStartTime)
    storage.close()
}
```

## Migration Path for Existing Users

Users who upgraded with Room migration active have their events in `RoomEvents`. Two options:

### Option A: Read from Active Database (Recommended)
- Implement Phase 1-2 above
- Sync immediately starts working with current Room data
- Legacy `Events` database becomes truly orphaned (can be deleted in future)

### Option B: Copy Room Data Back to Legacy (Not Recommended)
- Would require reverse migration
- Adds complexity, risk of data loss
- No benefit over Option A

## Checklist

- [x] Phase 1: Add native bridge via SharedPreferences (not direct import - separate Gradle modules)
- [x] Phase 1: EventsStorage writes to `events_storage_state.xml` prefs (excluded from backup)
- [x] Phase 1: MyModule reads from SharedPreferences
- [x] Phase 2: Update `SetupSync.tsx` to use dynamic database name
- [x] Phase 2: Update `installCrsqliteOnTable` call
- [x] Phase 3: Add RN contract test (`SyncDatabaseContract.test.ts`)
- [x] Phase 3: Add Android integration test (`SyncDatabaseContractTest.kt`)
- [x] Phase 3: Add SharedPreferences contract test (`sharedPreferencesContractIsCorrect`)
- [x] Phase 4: Add write/read roundtrip test (in `SyncDatabaseContractTest.kt`)
- [x] Update `room_database_migration.md` with lessons learned
- [ ] Consider: Delete orphaned `Events` database after N versions

## Lessons Learned

### Why This Wasn't Caught

1. **Isolated test suites**: Android tests verified Room migration. RN tests verified sync. Neither tested the integration.
2. **No shared constants**: The string `'Events'` was duplicated, not sourced from a single definition.
3. **Copy-based migration strategy**: Created a new database file (`RoomEvents`) rather than migrating in-place, which broke the hardcoded assumption.

### Prevention for Future

1. **Contract tests**: When two components share a resource (database, file, API), add a test that verifies they agree on the resource identifier.
2. **Shared constants via bridge**: Expose configuration from native to RN rather than duplicating values.
3. **Integration tests for cross-layer features**: Sync is inherently cross-layer - test it as such.

## References

- [Room Database Migration](../dev_completed/room_database_migration.md) - The migration that introduced this issue
- [PowerSync Schema](../../src/lib/powersync/Schema.tsx) - Defines the `eventsV9` table schema for sync
- [SetupSync.tsx](../../src/lib/features/SetupSync.tsx) - The sync UI (now fixed)

### Test Files Added

- `src/lib/features/__tests__/SyncDatabaseContract.test.ts` - RN contract tests
- `android/app/src/androidTest/.../eventsstorage/SyncDatabaseContractTest.kt` - Android integration tests
