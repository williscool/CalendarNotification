# MainActivity Split Refactoring Plan

## Status: Complete

## Problem

The `MainActivity.kt` (~1040 lines) has a significant code smell: it handles two completely different UI paradigms controlled by a runtime flag (`useNewNavigationUI`). This creates:

- Conditional branching throughout (`if (useNewNavigationUI)`)
- Mixed concerns (legacy RecyclerView + fragment navigation)
- Methods that only apply to one mode (e.g., `reloadData()`, `onDataUpdated()`, `adapter` field)
- Difficulty testing and maintaining either UI independently

## Solution: Delegating Router Pattern

Based on Android best practices for separating UI implementations:

1. **Keep `MainActivity` as the launcher** (minimal AndroidManifest changes)
2. **Make `MainActivity` a lightweight router** that delegates to the appropriate implementation
3. **Create `MainActivityLegacy`** with all legacy UI code
4. **Create `MainActivityModern`** with all new navigation UI code
5. **Extract shared code** into a base class

## Implementation Phases

### Phase 1: Create Shared Foundation

**1.1 Create `MainActivityBase.kt`** - Abstract base class with shared functionality:
- Permission checking (calendar, notifications, battery optimization)
- `ApplicationController` lifecycle hooks (`onMainActivityCreate/Started/Resumed`)
- Reminder last fired refresh
- Clock interface
- Companion object utilities (`cleanupOrphanedEvents`, storage providers)

### Phase 2: Extract Legacy Activity

**2.1 Create `MainActivityLegacy.kt`**:
- Extends `MainActivityBase`
- Uses `activity_main_legacy.xml` layout
- Contains all legacy-specific code:
  - `adapter`, `recyclerView`, `staggeredLayoutManager` fields
  - `reloadData()` method
  - `onDataUpdated()` method  
  - `onDismissAll()`, `onMuteAll()`, `onCustomQuietHours()` methods
  - Legacy `onCreateOptionsMenu()` logic
  - All `EventListCallback` implementations
  - Legacy search handling

### Phase 3: Extract Modern Activity

**3.1 Create `MainActivityModern.kt`**:
- Extends `MainActivityBase`
- Uses `activity_main.xml` layout
- Contains all new navigation UI code:
  - `navController`, fragment coordination
  - New `onCreateOptionsMenu()` logic (fragment delegation)
  - `getCurrentSearchableFragment()` helper
  - Navigation destination change listener

### Phase 4: Simplify MainActivity as Router

**4.1 Modify `MainActivity.kt`**:
- Check `useNewNavigationUI` setting
- Launch appropriate activity and finish immediately

### Phase 5: Update References

**5.1 AndroidManifest.xml**:
- Keep `MainActivity` as launcher (no change to intent filters)
- Add `MainActivityLegacy` and `MainActivityModern` declarations

**5.2 NavigationSettingsFragmentX.kt**:
- Update to launch appropriate activity directly

**5.3 DataUpdatedReceiver**:
- Move to appropriate location (base class or separate file)

### Phase 6: Test Preservation

**6.1 Test fixtures**:
- Update to launch specific activity classes directly
- Tests for legacy UI → `MainActivityLegacy`
- Tests for modern UI → `MainActivityModern`

## File Structure After Refactoring

```
ui/
├── MainActivity.kt              (Router - ~50 lines)
├── MainActivityBase.kt          (Shared - ~300 lines)
├── MainActivityLegacy.kt        (Legacy - ~500 lines)
├── MainActivityModern.kt        (Modern - ~300 lines)
└── ... (other existing files)
```

## Key Benefits

1. **Clear separation of concerns** - Each activity handles one UI paradigm
2. **Easier testing** - Test each activity independently
3. **Simpler maintenance** - Changes to one UI don't affect the other
4. **Future-ready** - `MainActivityModern` can be migrated to Jetpack Compose without touching legacy
5. **Minimal behavior change** - Tests continue to work with same patterns

## Migration Safety

- Router pattern ensures existing intent launches still work
- Storage providers and companion object utilities remain accessible
- Test fixtures require minimal changes (just class name updates)
- Settings-based UI switching continues to work

## Completion Notes

**Completed: January 2025**

All phases implemented successfully:
- `MainActivityBase.kt` created with ~280 lines of shared functionality
- `MainActivityLegacy.kt` created with ~470 lines for legacy UI
- `MainActivityModern.kt` created with ~230 lines for modern UI
- `MainActivity.kt` simplified to ~100 lines (router + backward compatibility aliases)
- Test fixtures updated to launch activities directly
- `launchMainActivityModern()` added for tests needing modern UI

The backward compatibility aliases in `MainActivity.companion` delegate to `MainActivityBase.companion`, ensuring existing code that references storage providers continues to work without changes.
