# Storage Lifecycle and the `.use {}` Pattern

## Overview

The app uses three storage interfaces that all extend `Closeable`:
- `EventsStorageInterface`
- `DismissedEventsStorageInterface`  
- `MonitorStorageInterface`

Each has two implementations:
1. **Room-based** (current default) - Uses Room with cr-sqlite
2. **Legacy** (fallback) - Uses SQLiteOpenHelper

## The `.use {}` Pattern

Throughout the codebase, storage is accessed via Kotlin's `.use {}` extension:

```kotlin
EventsStorage(context).use { db ->
    db.addEvent(event)
    db.getEvent(eventId, instanceStartTime)
}
```

This pattern ensures `close()` is called when the block completes.

## Why close() Behavior Differs

### Room Storage (RoomEventsStorage, etc.)

```kotlin
override fun close() {
    // No-op - Room databases are singletons
}
```

Room databases are **singletons** obtained via `getInstance(context)`. You don't close singletons - Room manages their lifecycle. Closing would break other code using the same database.

### Legacy Storage (LegacyEventsStorage, etc.)

```kotlin
override fun close() {
    super.close()  // SQLiteOpenHelper.close()
}
```

Legacy storage extends `SQLiteOpenHelper`, which **does** need closing to release database connections.

## Important Implications

### The `.use {}` Pattern is for Legacy Compatibility

Since Room's `close()` is a no-op, `.use {}` doesn't actually do anything for Room storage. It exists because:
1. The app may fall back to legacy storage if Room migration fails
2. Legacy storage requires closing
3. The abstraction should work for both implementations

### Double `.use {}` on Same Variable is a Bug

Even though it doesn't crash (because Room's close is no-op), this is conceptually wrong:

```kotlin
// BAD - would fail with legacy storage!
val storage = EventsStorage(context)
storage.use { db -> db.addEvent(event) }
storage.use { db -> db.getEvent(...) }  // Storage already "closed"

// GOOD - either merge into one block or create fresh instances
EventsStorage(context).use { db ->
    db.addEvent(event)
    db.getEvent(...)
}
```

### Mock Storage Enforces Closed State

The test mocks (`MockEventsStorage`, etc.) track closed state and throw `IllegalStateException` if used after close. This catches logical bugs even though production Room storage wouldn't crash.

## Future Cleanup

When legacy storage is removed:
1. Remove `Closeable` from storage interfaces
2. Remove `.use {}` calls throughout codebase (or keep as scope functions)
3. Simplify storage instantiation

## Related Files

- `EventsStorage.kt` - Wrapper that chooses Room vs Legacy
- `RoomEventsStorage.kt` - Room implementation (close is no-op)
- `LegacyEventsStorage.kt` - SQLiteOpenHelper implementation (close matters)
- `MockEventsStorage.kt` - Test mock with closed-state enforcement
