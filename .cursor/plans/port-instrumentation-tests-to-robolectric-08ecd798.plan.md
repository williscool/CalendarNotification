<!-- 08ecd798-44b9-4ea9-bddd-c496d0f46a9d 8d948f95-1c9e-4584-955a-c9e1391c8113 -->
# Plan: Port Instrumentation Tests to Robolectric

## Test Files to Port (8 files, ~41 tests)

### Already Done

- `dismissedeventsstorage/EventDismissRobolectricTest.kt` ✓
- `dismissedeventsstorage/OriginalEventDismissRobolectricTest.kt` ✓

### To Port

**Calendar Provider** (28 tests):

- `calendar/CalendarProviderBasicTest.kt` (8 tests)
- `calendar/CalendarProviderEventTest.kt` (7 tests)
- `calendar/CalendarProviderReminderTest.kt` (8 tests)
- `calendar/CalendarBackupRestoreTest.kt` (5 tests)

**Calendar Monitor** (13 tests):

- `calendarmonitor/ComponentIsolationTest.kt` (4 tests) - easiest, already mocked
- `calendarmonitor/SimpleCalendarMonitoringTest.kt` (2 tests)
- `calendarmonitor/FixturedCalendarMonitorServiceTest.kt` (5 tests)
- `calendarmonitor/CalendarTestFixtureExampleTest.kt` (2 tests)

## Implementation Approach

### Step 1: Extend MockCalendarProvider

**File**: `android/app/src/test/java/com/github/quarck/calnotify/testutils/MockCalendarProvider.kt`

Add methods to mock CalendarContract operations:

- `createTestCalendar()` - return mocked calendar ID
- `createTestEvent()` - return mocked event ID
- `mockCalendarQuery()` - stub calendar queries
- `mockEventQuery()` - stub event queries

Use MockK to stub `CalendarProvider` methods called by tests (already partially done).

### Step 2: Port Tests Using Existing Pattern

**Pattern** (from `EventDismissRobolectricTest.kt`):

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(manifest="AndroidManifest.xml", sdk = [24])
class XyzRobolectricTest {
    private lateinit var mockComponents: MockApplicationComponents
    private lateinit var mockTimeProvider: MockTimeProvider
    
    @Before
    fun setup() {
        mockTimeProvider = MockTimeProvider()
        mockTimeProvider.setup()
        
        val mockContextProvider = MockContextProvider(mockTimeProvider)
        mockContextProvider.setup()
        
        val mockCalendarProvider = MockCalendarProvider(mockContextProvider, mockTimeProvider)
        mockCalendarProvider.setup()
        
        mockComponents = MockApplicationComponents(...)
        mockComponents.setup()
    }
}
```

**For each test**:

1. Create Robolectric version with `RobolectricTest` suffix
2. Use same setup pattern
3. Replace real calendar operations with mocked ones
4. Keep assertions identical to instrumentation tests

### Step 3: Port Order (by complexity)

1. **ComponentIsolationRobolectricTest** - already uses mocks, minimal changes
2. **CalendarProviderBasicRobolectricTest** - basic calendar operations
3. **CalendarProviderEventRobolectricTest** - event operations
4. **CalendarProviderReminderRobolectricTest** - reminder operations
5. **CalendarBackupRestoreRobolectricTest** - backup/restore
6. **SimpleCalendarMonitoringRobolectricTest** - monitoring basics
7. **FixturedCalendarMonitorServiceRobolectricTest** - service tests
8. **CalendarTestFixtureExampleRobolectricTest** - fixture examples

## Files to Create (8 new test files)

All in `android/app/src/test/java/com/github/quarck/calnotify/`:

- `calendarmonitor/ComponentIsolationRobolectricTest.kt`
- `calendar/CalendarProviderBasicRobolectricTest.kt`
- `calendar/CalendarProviderEventRobolectricTest.kt`
- `calendar/CalendarProviderReminderRobolectricTest.kt`
- `calendar/CalendarBackupRestoreRobolectricTest.kt`
- `calendarmonitor/SimpleCalendarMonitoringRobolectricTest.kt`
- `calendarmonitor/FixturedCalendarMonitorServiceRobolectricTest.kt`
- `calendarmonitor/CalendarTestFixtureExampleRobolectricTest.kt`

## Key Principles

1. **Reuse existing infrastructure**: MockApplicationComponents, MockContextProvider, MockTimeProvider already exist
2. **Mock, don't replicate**: Use MockK to stub CalendarProvider methods, not custom ContentProvider shadows
3. **Match assertions exactly**: Keep test assertions identical to instrumentation tests (test current behavior)
4. **No code changes**: Only test changes (follow workspace rules)
5. **Follow EventDismissRobolectricTest pattern**: Proven working approach

## What NOT to Create

- No custom Robolectric shadows
- No separate fixture classes (extend MockCalendarProvider instead)
- No ContentProvider implementations
- Just stub CalendarProvider methods as needed per test

### To-dos

- [ ] Extend MockCalendarProvider with calendar/event creation methods
- [ ] Port ComponentIsolationTest (easiest - already mocked)
- [ ] Port CalendarProviderBasicTest
- [ ] Port CalendarProviderEventTest
- [ ] Port CalendarProviderReminderTest
- [ ] Port CalendarBackupRestoreTest
- [ ] Port SimpleCalendarMonitoringTest
- [ ] Port FixturedCalendarMonitorServiceTest
- [ ] Port CalendarTestFixtureExampleTest