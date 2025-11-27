# Test Development Chronology

This document summarizes the order in which tests were introduced to the CalendarNotification project, based on commit history and documentation. This is intended to help understand the test suite one file at a time.

## Overview

The test suite was developed incrementally, starting with basic backup/restore tests and evolving into a comprehensive testing infrastructure with fixtures and mock providers. The tests focus on being faithful to the real code while only mocking out core Android APIs that the instrumentation test suite doesn't work well with.

## Chronological Order of Test Introduction

### Phase 1: Initial Test Infrastructure (PR #60 - March 2025)

**Commit:** `feat: reassociate after backup restore (#60)` - March 24, 2025

**Files Introduced:**
1. `android/app/src/androidTest/java/com/github/quarck/calnotify/calendar/CalendarBackupRestoreTest.kt`

**Purpose:** First test file introduced to verify backup and restore functionality related to calendar data. Tests:
- Retrieving backup information for a calendar
- Finding a matching calendar on a "restored" device based on backup info
- Restoring an event alert record into local storage

**Key Learning:** Use unique identifiers (UUID suffix) for calendar names and accounts during setup to prevent intermittent failures due to calendar ID drift or conflicts with pre-existing data.

---

### Phase 2: Core Feature Test Harness (PR #62 - April 3, 2025)

**Commit:** `test: core feature test harness setup (#62)` - April 3, 2025

**Files Introduced:**
1. `android/app/src/androidTest/java/com/github/quarck/calnotify/deprecated_raw_calendarmonitor/CalendarMonitorServiceTest.kt`
2. `android/app/src/androidTest/java/com/github/quarck/calnotify/deprecated_raw_calendarmonitor/CalendarMonitorServiceEventReminderTest.kt`

**Purpose:** First comprehensive test harness for CalendarMonitorService. These tests verify:
- Calendar event monitoring
- Event processing
- Delayed event handling functionality

**Key Achievements:**
- First tests using mock objects to simulate calendar provider interactions
- Established the pattern of using MockK for mocking
- Created the initial documentation in `docs/calendar_monitoring.md`

---

### Phase 3: Clocks Refactor (PR #71 - April 5, 2025)

**Commit:** `test: Clocks Refactor and More Testing Of Calendar Monitoring Code (#71)` - April 5, 2025

**Files Introduced:**
1. `android/app/src/androidTest/java/com/github/quarck/calnotify/deprecated_raw_calendarmonitor/CalendarMonitorServiceTestFirstScanEver.kt`

**Infrastructure Added:**
- `CNPlusClockInterface` - Interface for time operations
- `CNPlusSystemClock` - Production implementation
- `CNPlusTestClock` - Test implementation with time control

**Purpose:** Refactored the codebase to use injectable clocks, making time-dependent code much easier to test. Key improvements:
- Replaced direct `System.currentTimeMillis()` calls
- Enabled deterministic testing of time-dependent behavior
- Added `advanceAndExecuteTasks()` and `executeAllPendingTasks()` methods

**Documentation Added:** `docs/clock_implementation.md`

---

### Phase 4: Test Fixture Suite (PR #72 - April 7, 2025)

**Commit:** `test: suite refactor all the repeated mock stuff out of everything (#72)` - April 7, 2025

**Files Introduced:**
1. `android/app/src/androidTest/java/com/github/quarck/calnotify/testutils/BaseCalendarTestFixture.kt`
2. `android/app/src/androidTest/java/com/github/quarck/calnotify/testutils/MockContextProvider.kt`
3. `android/app/src/androidTest/java/com/github/quarck/calnotify/testutils/MockTimeProvider.kt`
4. `android/app/src/androidTest/java/com/github/quarck/calnotify/testutils/MockCalendarProvider.kt`
5. `android/app/src/androidTest/java/com/github/quarck/calnotify/testutils/MockApplicationComponents.kt`
6. `android/app/src/androidTest/java/com/github/quarck/calnotify/calendar/CalendarProviderBasicTest.kt`
7. `android/app/src/androidTest/java/com/github/quarck/calnotify/calendar/CalendarProviderEventTest.kt`
8. `android/app/src/androidTest/java/com/github/quarck/calnotify/calendar/CalendarProviderReminderTest.kt`
9. `android/app/src/androidTest/java/com/github/quarck/calnotify/calendarmonitor/ComponentIsolationTest.kt`

**Purpose:** Major refactoring to extract repeated mock setup into reusable fixtures. Created a modular, extensible test infrastructure.

**Documentation Added:** `docs/test_fixture_refactoring.md`

---

### Phase 5: Real Calendar Provider (PR #74 - April 23, 2025)

**Commit:** `test: use real calendar provider where possible (#74)` - April 23, 2025

**Files Introduced:**
1. `android/app/src/androidTest/java/com/github/quarck/calnotify/testutils/CalendarProviderTestFixture.kt`
2. `android/app/src/androidTest/java/com/github/quarck/calnotify/testutils/CalendarMonitorTestFixture.kt`
3. `android/app/src/androidTest/java/com/github/quarck/calnotify/testutils/DirectReminderTestFixture.kt`
4. `android/app/src/androidTest/java/com/github/quarck/calnotify/calendarmonitor/FixturedCalendarMonitorServiceTest.kt`
5. `android/app/src/androidTest/java/com/github/quarck/calnotify/calendarmonitor/SimpleCalendarMonitoringTest.kt`
6. `android/app/src/androidTest/java/com/github/quarck/calnotify/calendarmonitor/CalendarTestFixtureExampleTest.kt`

**Purpose:** Refactored MockCalendarProvider to delegate to real CalendarProvider implementation. Tests now use real code wherever possible, only mocking out heavy external dependencies.

**Key Insight:** CalendarProvider is part of the codebase (not Android API), so it should be tested with real implementation.

**Documentation Added:** `docs/dev_completed/mock_calendar_provider_refactor.md`

---

### Phase 6: Event Dismissal Tests (PR #73 & #82 - April 2025)

**Commit:** `feat!: dismiss from reschedule confirmations (#73)` - April 25, 2025
**Commit:** `fix: safeDismissEventsFromRescheduleConfirmations fast follows (#82)` - April 28, 2025

**Files Introduced:**
1. `android/app/src/androidTest/java/com/github/quarck/calnotify/dismissedeventsstorage/EventDismissTest.kt`
2. `android/app/src/androidTest/java/com/github/quarck/calnotify/dismissedeventsstorage/OriginalEventDismissTest.kt`

**Purpose:** Comprehensive event dismissal testing covering:
- Single event dismissal (valid and non-existent cases)
- Multiple event dismissal (valid, mixed, and all invalid)
- Deletion warnings (when DB deletion fails)
- Storage errors (when dismissed events storage fails)
- Dismissal by event IDs
- Dismissal from reschedule confirmations

**Documentation Added:** 
- `docs/dev_completed/event_dissmimal_testing_notes.md`
- `docs/dev_completed/constructor-mocking-android.md`

---

## Test File Directory Structure

```
android/app/src/androidTest/java/com/github/quarck/calnotify/
├── calendar/
│   ├── CalendarBackupRestoreTest.kt      # Phase 1 - First test file
│   ├── CalendarProviderBasicTest.kt      # Phase 4 - Basic provider tests
│   ├── CalendarProviderEventTest.kt      # Phase 4 - Event operations
│   └── CalendarProviderReminderTest.kt   # Phase 4 - Reminder tests
├── calendarmonitor/
│   ├── FixturedCalendarMonitorServiceTest.kt  # Phase 5 - Fixtured tests
│   ├── SimpleCalendarMonitoringTest.kt        # Phase 5 - Simple examples
│   ├── CalendarTestFixtureExampleTest.kt      # Phase 5 - Example usage
│   └── ComponentIsolationTest.kt              # Phase 4 - Isolation tests
├── deprecated_raw_calendarmonitor/
│   ├── CalendarMonitorServiceTest.kt          # Phase 2 - Original tests
│   ├── CalendarMonitorServiceEventReminderTest.kt  # Phase 2 - Reminder flow
│   └── CalendarMonitorServiceTestFirstScanEver.kt  # Phase 3 - First scan
├── dismissedeventsstorage/
│   ├── EventDismissTest.kt                    # Phase 6 - Safe dismiss
│   └── OriginalEventDismissTest.kt            # Phase 6 - Original dismiss
├── testutils/
│   ├── BaseCalendarTestFixture.kt             # Phase 4 - Base fixture
│   ├── MockContextProvider.kt                 # Phase 4 - Context mocking
│   ├── MockTimeProvider.kt                    # Phase 4 - Time mocking
│   ├── MockCalendarProvider.kt                # Phase 4 - Calendar mocking
│   ├── MockApplicationComponents.kt           # Phase 4 - App components
│   ├── CalendarProviderTestFixture.kt         # Phase 5 - Provider fixture
│   ├── CalendarMonitorTestFixture.kt          # Phase 5 - Monitor fixture
│   └── DirectReminderTestFixture.kt           # Phase 5 - Reminder fixture
└── utils/
    └── (utility tests)
```

## Recommended Reading Order

For understanding the test suite one file at a time, follow this order:

### 1. Start with Documentation
1. `docs/calendar_monitoring.md` - Understand the flows being tested
2. `docs/clock_implementation.md` - Understand time control in tests
3. `docs/dev_completed/constructor-mocking-android.md` - Key mocking limitations

### 2. Test Utilities (Foundation)
1. `testutils/MockTimeProvider.kt` - Time control
2. `testutils/MockContextProvider.kt` - Context mocking
3. `testutils/MockCalendarProvider.kt` - Calendar operations
4. `testutils/MockApplicationComponents.kt` - App-level mocking
5. `testutils/BaseCalendarTestFixture.kt` - Base fixture class

### 3. Specialized Fixtures
1. `testutils/CalendarProviderTestFixture.kt`
2. `testutils/CalendarMonitorTestFixture.kt`
3. `testutils/DirectReminderTestFixture.kt`

### 4. Actual Tests (by complexity)
1. `calendar/CalendarProviderBasicTest.kt` - Simple provider tests
2. `calendar/CalendarProviderEventTest.kt` - Event operations
3. `calendar/CalendarProviderReminderTest.kt` - Reminder tests
4. `calendar/CalendarBackupRestoreTest.kt` - Backup/restore
5. `calendarmonitor/SimpleCalendarMonitoringTest.kt` - Simple monitoring
6. `calendarmonitor/FixturedCalendarMonitorServiceTest.kt` - Complex monitoring
7. `dismissedeventsstorage/EventDismissTest.kt` - Dismissal logic

### 5. Deprecated Tests (for reference)
1. `deprecated_raw_calendarmonitor/CalendarMonitorServiceTest.kt`
2. `deprecated_raw_calendarmonitor/CalendarMonitorServiceEventReminderTest.kt`
3. `deprecated_raw_calendarmonitor/CalendarMonitorServiceTestFirstScanEver.kt`

## Key Testing Principles

From the documentation and commit history, these principles emerged:

1. **Faithful Testing**: Use real code wherever possible, only mock out core Android APIs that instrumentation tests don't work well with (e.g., push notifications)

2. **No Constructor Mocking**: MockK's `mockkConstructor` and `mockkStatic` almost always fail in Android instrumentation tests. Use dependency injection patterns instead.

3. **Optional Parameter Injection**: For legacy code, add optional dependency parameters to methods for testability while preserving production behavior.

4. **Time Control**: Use `CNPlusTestClock` for deterministic time-based testing.

5. **Unique Identifiers**: Use UUIDs or unique suffixes for test data to prevent conflicts between test runs.

6. **Incremental Development**: Build tests one file at a time, verify they pass, then move on.
