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

1. **Intentional Duplication**: Robolectric + Instrumented pairs are **intentional** - Robolectric provides quick PR-blocking sanity checks, while Instrumentation provides real Android verification for releases
2. **Deprecated Tests**: ~5,000 lines in `deprecated_raw_calendarmonitor/` - these are actually **valuable** for their clarity and test core functionality; keep until fixtures are refactored to be equally clear
3. **Testing Overly Simple Logic**: Some tests verify trivial functionality that could be simplified ✅
4. **Large Monolithic Tests**: Several test files exceed 700+ lines and could be split or simplified ✅

### Important Architecture Notes

- **Robolectric tests block PRs** - fast sanity checks
- **Instrumentation tests block releases** - real Android verification
- **SQLite/Room testing MUST use instrumentation** - Robolectric's SQLite support is rudimentary and we use custom extensions
- **Storage is critical** - if storage breaks, nothing works

---

## Revised Consolidation Plan

### ~~Phase 1: Remove Deprecated Tests~~ → DEFERRED

**Status:** ON HOLD

The `deprecated_raw_calendarmonitor/` tests (~5,000 lines) are actually valuable because:
- They're straightforward to follow (easier than fixtures)
- They test core calendar monitoring functionality
- If this breaks, nothing else works

**Prerequisite:** Refactor fixtures to be equally clear and easy to follow before considering removal. Possibly investigate fixture frameworks that could help.

---

### Phase 1 (NEW): Simplify Overly Simple Tests

Focus on tests that verify trivial logic or have excessive boilerplate:

| Area | Opportunity |
|------|-------------|
| Simple getter/setter tests | Remove or consolidate |
| Excessive setup for simple assertions | Reduce boilerplate |
| Duplicate assertions across test methods | Use parameterized tests |

**Approach:** Review tests for low-value assertions that don't catch real bugs.

---

### Phase 2: Refactor Large Monolithic Tests

#### Tests to Refactor

| File | Lines | Opportunity |
|------|-------|-------------|
| `EventNotificationManagerRobolectricTest.kt` | 1,480 | Extract helper methods, reduce setup boilerplate |
| `NotificationContextInvariantTest.kt` | 999 | Review if all cases are necessary |
| `EventFormatterRobolectricTest.kt` | 956 | Consider parameterized tests |
| `MainActivityModernTest.kt` | 873 | Split into focused feature tests |
| `SettingsBackupManagerRobolectricTest.kt` | 760 | Use shared fixtures |
| `EventDismissTest.kt` | 735 | Consolidate similar test cases |

**Goal:** Make tests more readable and maintainable without removing coverage.

---

### Phase 3: Improve Test Fixtures (Prerequisite for Deprecated Test Removal)

Current fixtures are powerful but hard to follow. Options to explore:

1. **Add documentation** to existing fixtures explaining the flow
2. **Investigate fixture frameworks** (e.g., test containers, better base classes)
3. **Create "example" tests** that show how to use fixtures clearly
4. **Simplify fixture APIs** where possible

Once fixtures are as clear as the deprecated tests, we can revisit Phase 1.

---

### ~~Phase 2: Consolidate Duplicate Test Pairs~~ → NOT RECOMMENDED

**Status:** KEEP AS-IS

The Robolectric + Instrumentation duplication is **intentional**:
- Different purposes (PR blocking vs release blocking)
- Different capabilities (Robolectric can't test real SQLite)
- Defense in depth for critical functionality

**Exception:** If specific test pairs are truly identical with no value difference, those can be consolidated case-by-case.

---

## Implementation Order

### Batch 1: Low-Hanging Fruit (Safe Simplifications)
1. Review and remove overly simple tests that don't catch real bugs
2. Consolidate duplicate assertions using parameterized tests
3. Extract common setup code into helper methods

### Batch 2: Large Test Refactoring
4. Refactor `EventNotificationManagerRobolectricTest.kt` (1,480 lines)
5. Review `NotificationContextInvariantTest.kt` (999 lines) for necessary cases
6. Apply parameterized tests to `EventFormatterRobolectricTest.kt` (956 lines)

### Batch 3: Fixture Improvements (Prerequisite for Future Work)
7. Document existing fixtures with clear explanations
8. Research fixture frameworks that could help
9. Create example tests showing proper fixture usage

### Batch 4: Future Consideration (After Fixtures Improved)
10. Revisit deprecated tests once fixtures are equally clear
11. Case-by-case review of any truly redundant test pairs

---

## Success Criteria

1. **No Coverage Regression**: Coverage percentages should remain at or above baseline
2. **Improved Readability**: Tests should be easier to understand and maintain
3. **Faster Feedback**: Reduce test execution time where possible without losing coverage
4. **Preserve Defense in Depth**: Keep intentional Robolectric + Instrumentation separation

---

## Revised Estimated Reduction

| Phase | Lines Removed |
|-------|---------------|
| Phase 1: Simplify overly simple tests | ~500-1,000 |
| Phase 2: Refactor large tests | ~500-1,500 |
| Phase 3: Fixture improvements | ~0 (readability focus) |
| **Conservative Total** | **~1,000-2,500 lines (3-7% reduction)** |

**Note:** This is a more conservative estimate that respects the intentional architecture choices. The focus shifts from "delete code" to "improve code quality."

---

## Key Decisions

### Keep (Intentional Design)
- ✅ Robolectric + Instrumentation pairs (different purposes)
- ✅ Deprecated calendar monitor tests (clear, test core functionality)
- ✅ All storage/SQLite instrumentation tests (Robolectric can't handle custom SQLite)

### Remove/Simplify
- ❌ Overly simple tests that don't catch bugs
- ❌ Excessive boilerplate in test setup
- ❌ Duplicate assertions that could be parameterized

### Improve
- 📝 Fixture documentation and clarity
- 📝 Large test file organization
- 📝 Helper method extraction

---

## Quick Wins Implemented

The following improvements have been implemented:

### 1. Truth Assertions Library
Added `com.google.truth:truth:1.4.2` for better assertion failure messages.

```kotlin
// Instead of:
assertEquals(expected, actual)
assertTrue(list.contains(item))

// Use:
assertThat(actual).isEqualTo(expected)
assertThat(list).contains(item)
```

### 2. Object Mother Pattern
Created centralized test data factories in `testutils/`:
- `EventMother.kt` - Factory for `EventAlertRecord` test data
- `MonitorAlertMother.kt` - Factory for `MonitorEventAlertEntry` test data

```kotlin
// Usage:
val event = EventMother.createDefault()
val mutedEvent = EventMother.createMuted()
val snoozedEvent = EventMother.createSnoozed(until = tomorrow)
```

### 3. Robolectric PAUSED Looper Mode
Created `robolectric.properties` with explicit configuration:
- `looperMode=PAUSED` for realistic async testing
- `sdk=34` for Android 14 behavior

### 4. ShadowAlarmManager Helper
Created `AlarmManagerTestHelper.kt` for cleaner alarm testing:

```kotlin
val helper = AlarmManagerTestHelper()
val nextAlarm = helper.getNextScheduledAlarm()
helper.assertExactAlarmScheduledAt(expectedTime)
```

---

## Future Improvements (Potential)

Based on Android testing best practices research (see `docs/android-testing-best-practices.md`):

### Medium Effort

| Improvement | Description | Benefit |
|-------------|-------------|---------|
| **Robot Pattern** | Encapsulate UI interactions in screen-specific classes | More readable UI tests |
| **Test Data Builders** | Fluent builders for complex test objects | Flexible test data creation |
| **Parameterized Tests** | JUnit 4 `@Parameterized` for repetitive tests | Reduce test duplication |

### Investigate

| Improvement | Description | When to Consider |
|-------------|-------------|------------------|
| **Gradle Managed Devices** | Reproducible emulator configs in CI | If CI flakiness increases |
| **Test Orchestrator** | Run each test in isolated process | If cross-test pollution occurs |
| **Kaspresso** | Alternative UI testing framework | If Ultron becomes limiting |
| **Turbine** | Flow/Coroutine testing | If adopting more Kotlin Flows |

### Not Recommended

| Improvement | Reason |
|-------------|--------|
| **Hilt Testing** | Requires architectural changes to adopt Hilt |
| **JUnit 5** | Limited Android support |
| **SQLite in Robolectric** | Custom extensions require real database |

---

## Notes

- Always verify coverage after each change
- Run full test suite before and after changes
- Instrumentation test coverage is particularly important (blocks releases)
- When in doubt, keep the test - false confidence is worse than bloat
