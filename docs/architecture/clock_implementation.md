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

All time-dependent components have been updated to use the CNPlusClockInterface:

- Calendar monitoring components:
  - `CalendarMonitorInterface`: Added a clock property implemented by all monitors
  - `CalendarMonitor`: Replaced `System.currentTimeMillis()` calls with `clock.currentTimeMillis()`
  - `CalendarMonitorManual`: Added clock parameter and replaced time calls
  - `CalendarMonitorService`: Added clock property and updated sleep implementation
  
- Alarm management components:
  - `AlarmSchedulerInterface`: Added a clock property implemented by all schedulers
  - `AlarmScheduler`: Uses the clock for all time-related operations
  
- Application control components:
  - `ApplicationControllerInterface`: Added a clock property
  - `ApplicationController`: Uses CNPlusSystemClock for production code
  
- Broadcast receivers and notification components:
  - All alarm-related broadcast receivers now use the clock interface
  - Notification managers use the clock for time calculations and display
  
- UI components:
  - Time-based UI elements use the clock for consistent time representation

### 4. Test Updates

The test classes have been updated to:
- Use `CNPlusTestClock` instead of mocking time methods
- Control time advancement explicitly in tests
- Support both unit tests and Android instrumentation tests with the same test clock implementation
- Provide advanced time manipulation features in the test clock implementation:
  - `advanceAndExecuteTasks()`: Advances time and runs scheduled tasks
  - `executeAllPendingTasks()`: Executes all pending tasks immediately

## Benefits

1. **Standard API**: Uses the standard `java.time.Clock` under the hood
2. **Improved Testability**: Tests can now control time flow, allowing for deterministic testing of time-dependent behavior
3. **Reduced Flakiness**: Tests are less dependent on actual system time
4. **Simpler Time Manipulation**: The CNPlusTestClock provides methods to explicitly set or advance time
5. **Better Time Control**: Complex time-based scenarios can be tested more easily
6. **Better Code Organization**: Separate files for interface and implementations improve readability
7. **Direct Clock Access**: The test implementation exposes the underlying clock for direct manipulation in tests
8. **Consistent Time Operations**: All components use the same time source, reducing timing-related bugs
9. **Task Execution Control**: The test implementation supports tracking and executing scheduled tasks

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

// Advanced task execution control
testClock.advanceAndExecuteTasks(1000) // Advance time by 1000ms and execute any tasks due during that period
testClock.executeAllPendingTasks() // Execute all pending tasks immediately
```

## Usage Examples

### Production Code Example (ApplicationController)

The `ApplicationController` implements the `CNPlusClockInterface` property and uses it consistently throughout its implementation:

```kotlin
object ApplicationController : ApplicationControllerInterface, EventMovedHandler {
    // Clock interface for time-related operations
    override val clock: CNPlusClockInterface = CNPlusSystemClock()
    
    override fun onEventAlarm(context: Context) {
        DevLog.info(LOG_TAG, "onEventAlarm at ${clock.currentTimeMillis()}");
        
        val alarmWasExpectedAt = context.persistentState.nextSnoozeAlarmExpectedAt
        val currentTime = clock.currentTimeMillis()
        
        context.globalState?.lastTimerBroadcastReceived = clock.currentTimeMillis()
        
        // ... other implementation details ...
        
        if (currentTime > alarmWasExpectedAt + Consts.ALARM_THRESHOLD) {
            this.onSnoozeAlarmLate(context, currentTime, alarmWasExpectedAt)
        }
    }
    
    // ... other methods ...
    
    fun snoozeEvent(context: Context, eventId: Long, instanceStartTime: Long, snoozeDelay: Long): SnoozeResult? {
        var ret: SnoozeResult? = null
        val currentTime = clock.currentTimeMillis()
        
        val snoozedEvent: EventAlertRecord? =
            EventsStorage(context).classCustomUse {
                db ->
                var event = db.getEvent(eventId, instanceStartTime)
                
                if (event != null) {
                    var snoozedUntil =
                        if (snoozeDelay > 0L)
                            currentTime + snoozeDelay
                        else
                            event.displayedStartTime - Math.abs(snoozeDelay)
                            
                    if (snoozedUntil < currentTime + Consts.ALARM_THRESHOLD) {
                        DevLog.error(LOG_TAG, "snooze: $eventId / $instanceStartTime by $snoozeDelay: new time is in the past, snoozing by 1m instead")
                        snoozedUntil = currentTime + Consts.FAILBACK_SHORT_SNOOZE
                    }
                    
                    // ... update event ...
                }
                
                event;
            }
            
        // ... rest of implementation ...
        
        return ret
    }
}
```

### Test Code Example (CalendarMonitorServiceTest)

The `CalendarMonitorServiceTest` shows how to use `CNPlusTestClock` to control time in tests:

```kotlin
@RunWith(AndroidJUnit4::class)
class CalendarMonitorServiceTest {
    // ... other test setup ...
    
    @MockK
    private lateinit var mockTimer: ScheduledExecutorService
    
    private lateinit var testClock: CNPlusTestClock
    
    @Before
    fun setup() {
        // ... other setup ...
        
        // Create CNPlusTestClock with mockTimer
        testClock = CNPlusTestClock(System.currentTimeMillis(), mockTimer)
        
        // ... mock service setup ...
        mockService = spyk(CalendarMonitorService()) {
            // ... other mocking ...
            every { clock } returns testClock // Set the test clock
        }
    }
    
    // Test that uses time advancement
    @Test
    fun testCalendarMonitoringManualRescan() {
        // Use consistent time method
        testClock.setCurrentTime(System.currentTimeMillis())
        val startTime = testClock.currentTimeMillis()
        
        // Calculate event times 
        eventStartTime = startTime + 60000 // 1 minute from start time
        reminderTime = eventStartTime - 30000 // 30 seconds before start
        
        // ... test setup ...
        
        // Advance time past the reminder time
        DevLog.info(LOG_TAG, "Advancing time past reminder time...")
        val advanceAmount = reminderTime - startTime + Consts.ALARM_THRESHOLD
        advanceTimer(advanceAmount)
        
        // Helper function that uses the test clock
        private fun advanceTimer(milliseconds: Long) {
            val oldTime = testClock.currentTimeMillis()
            val executedTasks = testClock.advanceAndExecuteTasks(milliseconds)
            val newTime = testClock.currentTimeMillis()
            
            DevLog.info(LOG_TAG, "Advanced time from $oldTime to $newTime (by $milliseconds ms)")
            
            if (executedTasks.isNotEmpty()) {
                DevLog.info(LOG_TAG, "Executed ${executedTasks.size} tasks due at or before $newTime")
            }
        }
        
        // ... verification ...
    }
}
```

## Current Implementation Status

The clock refactoring has been successfully completed across the entire application:

1. **Core Interfaces**: All core interfaces define clock properties to be implemented by concrete classes
2. **Alarm Management**: All alarm-related components use the clock interface consistently
3. **Calendar Monitoring**: All calendar monitoring components use the clock interface
4. **Application Controller**: Uses the clock interface for all time-related operations
5. **UI Components**: Time-based UI elements use the clock interface
6. **Broadcast Receivers**: All broadcast receivers use the clock interface
7. **Notification Components**: All notification-related components use the clock interface
8. **Test Infrastructure**: Comprehensive test support with advanced time manipulation capabilities

## Potential Future Enhancements

1. Add more detailed logging for time-related operations to aid in debugging (some exists, could be expanded)
2. Create a ClockProvider singleton for components where lambda injection is awkward (currently using `clockProvider` lambdas which work well)