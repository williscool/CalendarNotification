# Test Fixture Refactoring Plan

## Overview

This document outlines the implementation plan for refactoring the calendar monitoring test fixtures into reusable components. The goal is to create a modular, extensible, and maintainable test infrastructure that reduces duplication and makes writing new tests easier.

## Components

### 1. BaseCalendarTestFixture

The `BaseCalendarTestFixture` in `testutils/BaseCalendarTestFixture.kt` will provide:
- Core test infrastructure including context, timer, and permissions setup
- Configurable mock components that can be selectively included
- Helper methods for common testing operations
- Resource management and cleanup

### 2. MockContextProvider

The `MockContextProvider` will handle:
- Creating and configuring the mock context
- Setting up SharedPreferences with persistent storage
- Managing system services like AlarmManager
- Simulating context operations like startService() and startActivity()

### 3. MockTimeProvider

The `MockTimeProvider` will offer:
- Test clock implementation that extends CNPlusTestClock
- Methods to advance time and trigger scheduled tasks
- Utilities for setting specific time points and verifying timing behavior
- Integration with MockTimer for scheduling operations

### 4. MockCalendarProvider

The `MockCalendarProvider` will include:
- Methods to create test calendars and events
- Configurable event attributes (title, start time, etc.)
- Support for different event types (regular, all-day, recurring)
- Mock implementations of CalendarProvider methods

### 5. MockApplicationComponents

The `MockApplicationComponents` will provide:
- Mock ApplicationController with configurable behavior
- Mock notification handling
- Mock alarm scheduling
- Mock event formatting

## Implementation Steps

### Phase 1: Foundation

1. Create the base structure with core interfaces and abstract classes
2. Extract mock context provider with SharedPreferences support
3. Extract test clock and timer management
4. Implement basic resource cleanup

### Phase 2: Calendar and Event Support

1. Extract calendar creation and management
2. Extract event creation and configuration
3. Implement mock CalendarProvider responses
4. Add storage verification utilities

### Phase 3: Application Components

1. Extract ApplicationController mocking
2. Extract notification management
3. Extract alarm scheduling
4. Implement verification helpers

### Phase 4: Builder Pattern

1. Create builder interface for flexible configuration
2. Implement specialized builders for common test scenarios
3. Add documentation and examples
4. Create cleanup mechanisms

### Phase 5: Test Migration

1. Refactor one test class at a time to use the new fixture
2. Verify tests continue to pass throughout migration
3. Remove duplicate code
4. Add additional test helpers as needed

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

## Migration Strategy

The migration to the new fixture system should be gradual to ensure tests continue to work:

1. First implement the foundation components without changing existing tests
2. Create simple utility methods that existing tests can start using
3. Refactor one test class at a time, starting with the simpler tests
4. Add more specialized fixtures as patterns emerge during migration
5. Run tests after each migration step to ensure functionality is preserved

This incremental approach minimizes risks while improving the test codebase over time.

``` mermaid
flowchart TD
    A[Create Foundation Components] --> B[Extract Context & Time Providers]
    B --> C[Extract Calendar Provider]
    C --> D[Extract Application Components]
    D --> E[Implement Builder Pattern]
    E --> F[Migrate Direct Reminder Tests]
    F --> G[Migrate Calendar Monitor Tests]
    G --> H[Create Specialized Fixtures]
    H --> I[Document Usage Patterns]
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