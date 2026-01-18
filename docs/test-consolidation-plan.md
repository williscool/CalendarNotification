# Test Suite Consolidation Plan

**Issue:** [#174 - Test suite getting bloated](https://github.com/williscool/CalendarNotification/issues/174)

**Coverage Baseline:** See `docs/coverage-baseline/BASELINE-SUMMARY.md`

## Current State

| Category | Files | Lines of Code |
|----------|-------|---------------|
| Unit Tests (Robolectric) | ~50 | ~18,582 |
| Integration Tests (Instrumented) | ~45 | ~19,743 |
| **Total** | ~95 | **~38,325** |

## Analysis Summary

### Coverage Baseline (Pre-Consolidation)

| Metric | Unit Tests | Integration Tests |
|--------|------------|-------------------|
| Instruction Coverage | 25.97% | 43.79% |
| Branch Coverage | 21.80% | 31.07% |
| Line Coverage | 24.84% | 45.34% |
| Method Coverage | 34.30% | 46.07% |

### Key Findings

1. **Massive Duplication**: ~20 test classes exist as BOTH Robolectric (unit) AND instrumented (integration) versions with nearly identical tests
2. **Deprecated Tests**: ~5,000 lines in `deprecated_raw_calendarmonitor/` folder that should be removed or archived
3. **Testing Overly Simple Logic**: Some tests verify trivial functionality that could be simplified
4. **Large Monolithic Tests**: Several test files exceed 700+ lines and could be split or simplified

---

## Consolidation Plan

### Phase 1: Remove Deprecated Tests (~5,000 lines reduction)

**Target Directory:** `app/src/androidTest/java/.../deprecated_raw_calendarmonitor/`

| File | Lines | Action |
|------|-------|--------|
| `CalendarMonitorServiceTest.kt` | 2,001 | DELETE - replaced by `FixturedCalendarMonitorServiceTest` |
| `CalendarMonitorServiceTestFirstScanEver.kt` | 2,034 | DELETE - covered by newer tests |
| `CalendarMonitorServiceEventReminderTest.kt` | 966 | DELETE - functionality tested elsewhere |

**Verification:** Run integration tests after removal to ensure no functionality gaps.

---

### Phase 2: Consolidate Duplicate Test Pairs (~5,000-7,000 lines reduction)

For tests that exist in both unit AND integration form with nearly identical logic, consolidate to ONE version (preferring Robolectric for speed, keeping instrumented only where Android runtime is essential).

#### Priority 1 - Direct Duplicates (Pure Logic Tests - Keep Robolectric Only)

These tests verify pure Kotlin/Java logic that doesn't require real Android runtime:

| Test Pair | Unit Lines | Int Lines | Action |
|-----------|------------|-----------|--------|
| `TagsManager*Test.kt` | 206 | 205 | **DELETE integration** - pure string parsing |
| `AlarmScheduler*Test.kt` | 282 | 289 | **KEEP BOTH** - integration verifies real AlarmManager |
| `CalendarReloadManager*Test.kt` | 292 | 283 | **DELETE integration** - logic can be tested with mocks |
| `SnoozeTest.kt` / `SnoozeRobolectricTest.kt` | 334 | 380 | **DELETE integration** - snooze logic testable with mocks |
| `ComponentIsolation*Test.kt` | 319 | 297 | **DELETE integration** - isolation tests work in Robolectric |

**Estimated Reduction:** ~1,400 lines

#### Priority 2 - UI Tests (Keep Integration, Delete/Reduce Robolectric)

UI tests benefit from real Android runtime. Robolectric UI tests are often flaky or limited:

| Test Pair | Unit Lines | Int Lines | Action |
|-----------|------------|-----------|--------|
| `MainActivityModernTest.kt` | 775 | 873 | **REVIEW** - consider keeping integration only |
| `MainActivityTest.kt` | 367 | 460 | **REVIEW** - may consolidate with Modern |
| `ViewEventActivityTest.kt` | 383 | 306 | **REVIEW** - prefer integration for UI |
| `SnoozeAllActivityTest.kt` | N/A | 279 | **KEEP** - no Robolectric version |

**Estimated Reduction:** ~500-1,000 lines (after review)

#### Priority 3 - CalendarProvider Tests (Keep Integration)

CalendarProvider tests require real ContentProvider access:

| Test Pair | Unit Lines | Int Lines | Action |
|-----------|------------|-----------|--------|
| `CalendarProviderBasicTest.kt` | 349 | 270 | **DELETE Robolectric** - needs real ContentProvider |
| `CalendarProviderEventTest.kt` | 314 | 276 | **DELETE Robolectric** - needs real ContentProvider |
| `CalendarProviderReminderTest.kt` | 340 | 315 | **DELETE Robolectric** - needs real ContentProvider |
| `CalendarBackupRestoreTest.kt` | 289 | 333 | **DELETE Robolectric** - needs real storage |

**Estimated Reduction:** ~1,300 lines

#### Priority 4 - Storage/Database Tests (Keep Integration)

Storage tests benefit from real SQLite/Room:

| Test Pair | Unit Lines | Int Lines | Action |
|-----------|------------|-----------|--------|
| `EventDismissTest.kt` | 726 | 735 | **DELETE Robolectric** - needs real Room |
| `OriginalEventDismiss*Test.kt` | 318 | ~300 | **DELETE Robolectric** - needs real Room |

**Estimated Reduction:** ~1,000 lines

#### Priority 5 - Broadcast Receiver Tests (Keep Both, Reduce Overlap)

Some broadcast tests need real Android, others can be unit tested:

| Test Pair | Unit Lines | Int Lines | Action |
|-----------|------------|-----------|--------|
| `BroadcastReceiverTest.kt` | 356 | 315 | **MERGE** - combine unique tests, remove duplicates |
| `ReminderAlarmTest.kt` | 422 | 305 | **MERGE** - keep real alarm behavior in integration |

**Estimated Reduction:** ~300 lines

---

### Phase 3: Simplify Large Tests

#### Tests to Review for Simplification

| File | Lines | Opportunity |
|------|-------|-------------|
| `EventNotificationManagerRobolectricTest.kt` | 1,480 | Extract helper methods, reduce setup boilerplate |
| `NotificationContextInvariantTest.kt` | 999 | Review if all cases are necessary |
| `EventFormatterRobolectricTest.kt` | 956 | Consider parameterized tests |
| `MainActivityModernTest.kt` | 873 | Split into focused feature tests |
| `SettingsBackupManagerRobolectricTest.kt` | 760 | Use shared fixtures |
| `EventDismissTest.kt` | 735 | Consolidate similar test cases |
| `EventsStorageMigrationTest.kt` | 637 | Consider if all migration paths need testing |

**Estimated Reduction:** ~500-1,500 lines through refactoring

---

### Phase 4: Consolidate Test Fixtures

| Fixture Pair | Lines | Action |
|--------------|-------|--------|
| `UITestFixture.kt` | 978 | **MERGE** - combine shared utilities |
| `UITestFixtureRobolectric.kt` | 551 | Into single fixture with platform-specific overrides |
| `CalendarProviderTestFixture.kt` | 414 | **KEEP** - integration only |
| `CalendarMonitorTestFixture.kt` | 398 | **KEEP** - integration only |
| `BaseCalendarTestFixture.kt` | 531 | **KEEP** - base class |

**Estimated Reduction:** ~400 lines

---

## Implementation Order

### Batch 1: Low-Risk Deletions (No coverage loss expected)
1. Delete `deprecated_raw_calendarmonitor/` folder (~5,000 lines)
2. Delete `TagsManagerTest.kt` (integration) - keep Robolectric version
3. Delete CalendarProvider Robolectric tests (keep integration versions)

### Batch 2: Consolidations Requiring Verification
4. Merge BroadcastReceiver test pairs
5. Consolidate EventDismiss test pairs
6. Review and consolidate SnoozeTest pairs

### Batch 3: UI Test Consolidation
7. Review MainActivity test strategy
8. Consolidate UI test fixtures

### Batch 4: Refactoring Large Tests
9. Apply shared fixtures to large test files
10. Use parameterized tests where applicable

---

## Success Criteria

1. **No Coverage Regression**: Coverage percentages should remain at or above baseline
2. **Test Count Reduction**: Target 20-30% reduction in total test lines
3. **Test Speed**: Unit test suite should run faster (fewer duplicate runs)
4. **Maintainability**: Clear separation of unit vs integration test responsibilities

---

## Estimated Total Reduction

| Phase | Lines Removed |
|-------|---------------|
| Phase 1: Deprecated | ~5,000 |
| Phase 2: Duplicates | ~4,500 |
| Phase 3: Simplify | ~1,000 |
| Phase 4: Fixtures | ~400 |
| **Total** | **~10,900 lines (28% reduction)** |

**Post-Consolidation Target:** ~27,000 lines (down from ~38,000)

---

## Notes

- Always verify coverage after each batch
- Run full test suite before and after changes
- Document any intentional coverage reductions with justification
- Keep integration tests for anything that truly requires Android runtime
