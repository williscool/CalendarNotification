# System.currentTimeMillis() Removal Plan

## Status: COMPLETED

## Problem

`System.currentTimeMillis()` causes test flakiness because:
1. Tests running near midnight can cross day boundaries mid-test
2. Time-dependent comparisons become non-deterministic
3. The codebase already has `CNPlusClockInterface` infrastructure to solve this

**Example flake:** A test checking `isToday()` passes 99.9% of the time but fails when run exactly at midnight.

**Fix pattern (from commit 377a3a64):** Use fixed times (e.g., noon on a specific date) rather than live system time.

## Solution

Create a standard test time constant and replace all `System.currentTimeMillis()` calls with either:
1. The constant directly (for simple cases)
2. A test clock that starts at the constant (for tests needing time manipulation)
3. Clock interface injection (for production code)

## New Repo Rule (After Completion)

**NO `System.currentTimeMillis()` ALLOWED** - Use `CNPlusClockInterface` instead.
- Production code: Inject `CNPlusSystemClock` or access via interface
- Test code: Use `TestTimeConstants.STANDARD_TEST_TIME` or `CNPlusTestClock`

## Implementation

### Phase 1: Create TestTimeConstants

Create `TestTimeConstants.kt` with:
```kotlin
object TestTimeConstants {
    // December 23, 2023 at noon UTC - safely in middle of day, avoids all boundary issues
    val STANDARD_TEST_TIME: Long = 1703332800000L  // Pre-calculated for consistency
}
```

### Phase 2: Files to Update

#### Test Utilities (High Impact - Affects Many Tests)

| File | Location | Changes |
|------|----------|---------|
| `MockTimeProvider.kt` | androidTest/testutils | Change default param to STANDARD_TEST_TIME |
| `MockTimeProvider.kt` | test/testutils | Change default param to STANDARD_TEST_TIME |
| `UITestFixture.kt` | androidTest/testutils | Use test clock instead of System.currentTimeMillis() |
| `UITestFixtureRobolectric.kt` | test/testutils | Use test clock instead of System.currentTimeMillis() |
| `BaseCalendarTestFixture.kt` | androidTest/testutils | Use STANDARD_TEST_TIME |
| `CalendarProviderTestFixture.kt` | androidTest/testutils | Use STANDARD_TEST_TIME |
| `MockContextProvider.kt` | androidTest/testutils | Use test clock from fixture |
| `MockDismissedEventsStorage.kt` | test/testutils | Accept clock parameter |

#### Robolectric Tests (test/)

| File | Changes |
|------|---------|
| `DateTimeUtilsTest.kt` | Replace remaining System.currentTimeMillis() with fixed times |
| `FilterStateTest.kt` | Use fixed time in createEvent() and test methods |
| `MonitorEventAlertEntryPreMutedTest.kt` | Use fixed time in createTestAlert() defaults |
| `CalendarIntentsRobolectricTest.kt` | Use fixed time in createTestEvent() defaults |

#### Instrumentation Tests (androidTest/)

| File | Changes |
|------|---------|
| `EventDismissTest.kt` | Use test clock in createTestEvent() |
| `PreDismissIntegrationTest.kt` | Use STANDARD_TEST_TIME for baseTime |
| `PreSnoozeIntegrationTest.kt` | Use STANDARD_TEST_TIME for baseTime |
| `PreMuteIntegrationTest.kt` | Use STANDARD_TEST_TIME for baseTime |
| `ApplicationControllerCoreTest.kt` | Use STANDARD_TEST_TIME and test clock |
| `TagsManagerTest.kt` | Use fixed time in createTestEvent() |
| `CalendarMonitorServiceTest.kt` | Use STANDARD_TEST_TIME for testClock init |
| `CalendarMonitorServiceTestFirstScanEver.kt` | Use STANDARD_TEST_TIME for testClock init |
| `CalendarMonitorServiceEventReminderTest.kt` | Use STANDARD_TEST_TIME for testClock init |
| `RoomCrSqlitePocTest.kt` | Use fixed time for entity timestamps |

#### Production Code

| File | Changes |
|------|---------|
| `SettingsBackupManager.kt` | Inject clock interface for exportedAt timestamp |
| `TestActivity.kt` | Use existing clock property instead of System.currentTimeMillis() |

#### Excluded (Acceptable Uses)

| File | Reason |
|------|--------|
| `build.gradle` | Build timestamp, not test-sensitive |
| `TestResult.java` / `TestRunResult.java` | Third-party test listener, measures actual wall-clock test duration |

## Verification

After completion, run:
```bash
grep -r "System.currentTimeMillis()" android/app/src/
```

### Expected Results (All Acceptable)
- `build.gradle` - OK (build timestamp)
- `TestResult.java` / `TestRunResult.java` - OK (third-party test runner)
- Comments in test files - OK (explaining what NOT to do)
- Everything else - **NONE** ✅

### Verification Complete
As of January 2026, all application code (both production and test) has been updated to use:
- `CNPlusClockInterface` / `CNPlusSystemClock` for production code
- `TestTimeConstants.STANDARD_TEST_TIME` for test code
- `CNPlusTestClock` for tests requiring time manipulation

## Related Documentation

- `docs/architecture/clock_implementation.md` - CNPlusClockInterface design
- `docs/testing/dependency_injection_patterns.md` - DI patterns for testability
