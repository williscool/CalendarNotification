# Clock Implementation for Calendar Notification

## Overview

This document outlines the implementation of a `CNPlusClockInterface` in the Calendar Notification application, which wraps around the standard `java.time.Clock` to replace direct calls to `System.currentTimeMillis()` and `Thread.sleep()`. This abstraction improves testability by allowing time to be controlled in tests.

## Components

### 1. CNPlusClockInterface

The `CNPlusClockInterface` in `utils/CNPlusClockInterface.kt` extends the functionality of `java.time.Clock` with the following:
- `currentTimeMillis()`: Returns the current time in milliseconds (wrapper around `Clock.millis()`)
- `sleep(millis: Long)`: Sleeps for the specified duration in milliseconds
- `underlying()`: Returns the underlying `java.time.Clock` instance

### 2. Implementations

Two implementations are provided in separate files for better organization:
- `CNPlusSystemClock` (in `CNPlusSystemClock.kt`): Uses `Clock.systemUTC()` for production code
- `CNPlusTestClock` (in `CNPlusTestClock.kt`): Controllable clock for tests that allows setting and advancing time, with access to the underlying clock instance for direct manipulation

The "CNPlus" prefix is used to distinguish our clock implementations and prevent naming conflicts.

### 3. Modified Components

The following components have been updated to use the CNPlusClockInterface:

- `CalendarMonitorInterface`: Added a clock property to be implemented by all monitors
- `CalendarMonitor`: Replaced `System.currentTimeMillis()` calls with `clock.currentTimeMillis()`
- `CalendarMonitorManual`: Added clock parameter and replaced time calls
- `CalendarMonitorService`: Added clock property and updated sleep implementation
- `AlarmSchedulerInterface`: Added a clock property to be implemented by all schedulers
- `AlarmScheduler`: Uses the clock for all time-related operations
- `ApplicationControllerInterface`: Added a clock property
- `ApplicationController`: Uses CNPlusSystemClock for production code

### 4. Test Updates

The test classes have been updated to:
- Use `CNPlusTestClock` instead of mocking time methods
- Control time advancement explicitly in tests
- Support both unit tests and Android instrumentation tests with the same test clock implementation

## Benefits

1. **Standard API**: Uses the standard `java.time.Clock` under the hood
2. **Improved Testability**: Tests can now control time flow, allowing for deterministic testing of time-dependent behavior
3. **Reduced Flakiness**: Tests are less dependent on actual system time
4. **Simpler Time Manipulation**: The CNPlusTestClock provides methods to explicitly set or advance time
5. **Better Time Control**: Complex time-based scenarios can be tested more easily
6. **Better Code Organization**: Separate files for interface and implementations improve readability
7. **Direct Clock Access**: The test implementation exposes the underlying clock for direct manipulation in tests
8. **Consistent Time Operations**: All components use the same time source, reducing timing-related bugs

## Usage in Tests

```kotlin
// Create a test clock with a specific start time
val testClock = CNPlusTestClock(specificStartTime)

// Create components with the test clock
val calendarMonitor = CalendarMonitor(calendarProvider, testClock)
val alarmScheduler = AlarmScheduler(testClock)

// Advance time in tests
testClock.advanceBy(1000) // Advance by 1 second

// Set specific time points
testClock.setCurrentTime(specificTimePoint)

// Access the underlying java.time.Clock if needed
val underlyingClock = testClock.underlying()

// The fixedClock property allows direct access to the clock instance (read-only)
val fixedClock = testClock.fixedClock

// Manually refresh the clock after changing time (done automatically in setCurrentTime/advanceBy)
testClock.refreshClock()

// Support for timer-based sleep in tests (optional)
val mockTimer = Executors.newScheduledThreadPool(1)
val testClockWithTimer = CNPlusTestClock(specificStartTime, mockTimer)
```

## Current Implementation Status

The CNPlusClockInterface has been successfully implemented in several core components:

1. **Alarm Management**: AlarmScheduler now uses the clock interface for scheduling and calculating alarm times
2. **Calendar Monitoring**: Calendar monitors use the clock for checking event times
3. **Application Controller**: Uses the clock interface for all time-related operations

## Next Steps

1. ✓ Update `ApplicationController` to use the CNPlusClockInterface
2. Update remaining components that directly use System.currentTimeMillis():
   - Broadcast receivers for alarms
   - Notification-related classes
   - UI components that display time or have time-based actions
3. Create more comprehensive time-based tests leveraging the new interface
4. Consider creating a ClockProvider to avoid passing the clock to every component 