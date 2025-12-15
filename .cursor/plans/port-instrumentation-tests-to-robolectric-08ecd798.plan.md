---
name: "Plan: Port Instrumentation Tests to Robolectric"
overview: ""
todos:
  - id: 4ca9ab62-3f91-476d-aa0e-791e21b60575
    content: Extend MockCalendarProvider with calendar/event creation methods
    status: pending
  - id: 84a83df5-ed53-403d-8e4d-08f2b3bc6dfc
    content: Port ComponentIsolationTest (easiest - already mocked)
    status: pending
  - id: 51011228-2107-404a-a66d-5b7f2f6cce1c
    content: Port CalendarProviderBasicTest
    status: pending
  - id: 483907bf-3049-4021-90e4-bb1586b76214
    content: Port CalendarProviderEventTest
    status: pending
  - id: c6032be8-dae2-4f78-b696-37bab2edc24a
    content: Port CalendarProviderReminderTest
    status: pending
  - id: 77207cfd-7faa-45af-acb6-43ebb040a57c
    content: Port CalendarBackupRestoreTest
    status: pending
  - id: 4659fb72-b7d0-4f82-a872-b48ab5e2af46
    content: Port SimpleCalendarMonitoringTest
    status: pending
  - id: 72b8ce3a-4c96-403a-a748-f9a14a3e46bc
    content: Port FixturedCalendarMonitorServiceTest
    status: pending
  - id: e20ca439-bf5b-480a-8a9a-e65b58fd0a36
    content: Port CalendarTestFixtureExampleTest
    status: pending
---

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