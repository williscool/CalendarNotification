# Test Fixture Infrastructure

## Overview

This document describes the test fixture infrastructure for calendar monitoring tests. The fixtures provide a modular, extensible, and maintainable test infrastructure that reduces duplication and makes writing new tests easier.

## Components

### 1. BaseCalendarTestFixture

The `BaseCalendarTestFixture` in `testutils/BaseCalendarTestFixture.kt` provides:
- Core test infrastructure including context, timer, and permissions setup
- Configurable mock components that can be selectively included
- Helper methods for common testing operations
- Resource management and cleanup

### 2. MockContextProvider

The `MockContextProvider` handles:
- Creating and configuring the mock context
- Setting up SharedPreferences with persistent storage
- Managing system services like AlarmManager
- Simulating context operations like startService() and startActivity()

### 3. MockTimeProvider

The `MockTimeProvider` offers:
- Test clock implementation that extends CNPlusTestClock
- Methods to advance time and trigger scheduled tasks
- Utilities for setting specific time points and verifying timing behavior
- Integration with MockTimer for scheduling operations

### 4. MockCalendarProvider

The `MockCalendarProvider` includes:
- Methods to create test calendars and events
- Configurable event attributes (title, start time, etc.)
- Support for different event types (regular, all-day, recurring)
- Delegation to real CalendarProvider where possible

### 5. MockApplicationComponents

The `MockApplicationComponents` provides:
- Mock ApplicationController with configurable behavior
- Mock notification handling
- Mock alarm scheduling
- Mock event formatting

## Architecture

The fixture system was built in phases:

### Foundation Layer
- Base structure with core interfaces and abstract classes
- Mock context provider with SharedPreferences support
- Test clock and timer management via `CNPlusTestClock`
- Resource cleanup in `@After` methods

### Calendar and Event Support
- Calendar creation and management utilities
- Event creation with configurable attributes
- Real CalendarProvider delegation (not pure mocks)
- Storage verification utilities

### Application Components
- ApplicationController mocking with provider injection
- Notification management mocks
- Alarm scheduling mocks
- Event formatting utilities

### Specialized Fixtures
- `CalendarProviderTestFixture` - For calendar/event tests
- `CalendarMonitorTestFixture` - For monitoring flow tests
- `DirectReminderTestFixture` - For reminder broadcast tests
- `UITestFixture` - For UI/Activity tests with Espresso

## Usage Patterns

### Basic Test Setup

```kotlin
@Test
fun testBasicEventProcessing() {
    // Configure and build the test fixture
    val fixture = BaseCalendarTestFixture.Builder()
        .withTestCalendar("Test Calendar")
        .withTestEvent(title = "Test Event")
        .build()
        
    // Simulate calendar monitoring
    fixture.triggerCalendarMonitoring()
    
    // Advance time and verify results
    fixture.advanceTime(1000)
    fixture.verifyEventProcessed(fixture.testEventId)
}
```

### Specialized Test Scenarios

```kotlin
@Test
fun testDirectReminderProcessing() {
    // Use specialized fixture for direct reminders
    val fixture = DirectReminderTestFixture()
        .withTestEvent(title = "Direct Reminder Test")
        .build()
        
    // Simulate direct reminder broadcast
    fixture.simulateReminderBroadcast(fixture.reminderTime)
    
    // Verify processing
    fixture.verifyDirectReminderProcessed(fixture.testEventId)
}
```

## Benefits

1. **Reduced Duplication**: Common setup and verification code maintained in one place
2. **Improved Readability**: Tests focus on behavior under test, not setup mechanics
3. **Easier Maintenance**: Changes to mocking approach only need to be made in one place
4. **Faster Test Writing**: New tests can be created quickly using the fixture
5. **Better Isolation**: Each test can precisely configure only what it needs
6. **Consistent Verification**: Standard verification methods ensure consistent checks
7. **Flexible Configuration**: Builder pattern allows precise control over mock behavior
8. **Resource Management**: Centralized cleanup of mocks and resources

## Development History

The fixture system was developed incrementally:

1. Foundation components were created without changing existing tests
2. Simple utility methods allowed existing tests to adopt fixtures gradually
3. Test classes were refactored one at a time, starting with simpler tests
4. Specialized fixtures emerged as patterns became clear during migration
5. Tests were run after each step to ensure functionality was preserved

``` mermaid
flowchart TD
    A[Foundation Components] --> B[Context & Time Providers]
    B --> C[Calendar Provider]
    C --> D[Application Components]
    D --> E[Specialized Fixtures]
    E --> F[Direct Reminder Tests]
    F --> G[Calendar Monitor Tests]
    G --> H[UI Tests]
    H --> I[Documentation]
```

## Component Relationships

``` mermaid
classDiagram
    class BaseCalendarTestFixture {
        +MockContextProvider contextProvider
        +MockTimeProvider timeProvider
        +MockCalendarProvider calendarProvider
        +MockApplicationComponents appComponents
        +setup()
        +cleanup()
        +advanceTime()
        +verifyEventProcessed()
    }
    
    class MockContextProvider {
        +createMockContext()
        +setupSharedPreferences()
        +getMockService()
    }
    
    class MockTimeProvider {
        +CNPlusTestClock testClock
        +advanceTime()
        +executeScheduledTasks()
        +setSpecificTime()
    }
    
    class MockCalendarProvider {
        +createTestCalendar()
        +createTestEvent()
        +mockEventDetails()
        +mockEventReminders()
    }
    
    class MockApplicationComponents {
        +mockApplicationController()
        +mockNotifications()
        +mockAlarmScheduler()
        +mockFormatter()
    }
    
    BaseCalendarTestFixture --> MockContextProvider
    BaseCalendarTestFixture --> MockTimeProvider
    BaseCalendarTestFixture --> MockCalendarProvider
    BaseCalendarTestFixture --> MockApplicationComponents
    MockTimeProvider --> "uses" CNPlusTestClock
    MockContextProvider --> "creates" MockService
``` 