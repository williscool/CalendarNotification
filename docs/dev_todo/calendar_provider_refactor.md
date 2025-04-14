# Calendar Provider Test Refactoring Plan

## Overview

This document outlines the plan to refactor the `MockCalendarProvider` to use the real `CalendarProvider` implementation while maintaining test functionality. The goal is to make our tests more accurate by using the actual implementation while still maintaining control over external dependencies.

## Current State

- `MockCalendarProvider` was built assuming `CalendarProvider` was part of the Android API
- We now recognize `CalendarProvider` is part of our codebase
- We want to use the real implementation while still mocking heavy external dependencies

## Migration Plan

### Phase 1: Transparent Delegation

1. Modify `MockCalendarProvider` to delegate to real `CalendarProvider`:
   ```kotlin
   class MockCalendarProvider(
       private val contextProvider: MockContextProvider,
       private val timeProvider: MockTimeProvider
   ) {
       private val realProvider = CalendarProvider
       
       // Delegate methods to real provider while maintaining
       // ability to override specific behaviors
   }
   ```

2. Setup delegation in initialization:
   ```kotlin
   private fun setupCalendarProvider() {
       mockkObject(CalendarProvider)
       
       // Default to real implementation
       every { CalendarProvider.getEventReminders(any(), any<Long>()) } answers {
		   callOriginal()
       }   
   }
   ```

### Phase 2: Gradual Method Migration

1. Identify methods that can use real CalendarProvider directly
2. For each method:
   - Implement delegation to real provider
   - Test thoroughly
   - Document test-specific overrides
   - Update tests if needed

3. Priority order for migration:
   - Basic CRUD operations (create/read/update/delete)
   - Query operations
   - Complex operations (event alerts, reminders)
   - Test-specific helper methods

### Phase 3: Test Adaptation

1. Review and update tests:
   - Identify tests relying on mock-specific behavior
   - Update tests to work with real provider
   - Document any necessary test-specific behaviors
   - Verify test coverage remains complete

2. Test categories to review:
   - Calendar creation/modification tests
   - Event management tests
   - Reminder/alert tests
   - Edge case tests
   - Permission-related tests

### Phase 4: Clean Up and Modernize

1. Remove redundant mocking:
   - Remove unnecessary mock implementations
   - Clean up unused test helpers
   - Update documentation

2. Modernize test infrastructure:
   - Use real provider's functionality where possible
   - Keep only necessary test-specific overrides
   - Update test utilities to modern patterns

3. Code cleanup:
   - Remove deprecated methods
   - Update method signatures
   - Improve error handling
   - Update logging

### Phase 5: Interface Alignment

1. Align interfaces:
   - Ensure MockCalendarProvider's interface matches real CalendarProvider
   - Document any necessary differences
   - Plan deprecation of mock-specific methods

2. Update documentation:
   - Document new testing approach
   - Create examples for writing new tests
   - Update existing test documentation

## Success Criteria

1. All tests pass using real CalendarProvider
2. Test coverage remains at or above current levels
3. Test execution time remains reasonable
4. Code is cleaner and more maintainable
5. Documentation is clear and complete

## Risks and Mitigations

1. Risk: Breaking existing tests
   - Mitigation: Gradual migration with thorough testing at each step

2. Risk: Performance impact
   - Mitigation: Monitor test execution time, optimize if needed

3. Risk: Missing edge cases
   - Mitigation: Maintain comprehensive test coverage metrics

4. Risk: Incomplete mocking of external dependencies
   - Mitigation: Careful review of all external interactions

## Future Considerations

1. Consider creating a more robust dependency injection system
2. Evaluate adding more integration tests
3. Consider adding performance benchmarks
4. Plan for future Android API changes

## References

- Current MockCalendarProvider implementation
- CalendarProvider implementation
- Existing test suite
- Clock implementation documentation