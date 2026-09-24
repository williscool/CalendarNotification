# Android Testing Best Practices Research

This document captures research on Android testing best practices, comparing against the current CalendarNotification Plus test setup, and identifies opportunities for improvement.

## Current Setup Summary

### Dependencies Used
| Category | Library | Version | Notes |
|----------|---------|---------|-------|
| Unit Testing | JUnit | 4.13.2 | Standard |
| Mocking | MockK | 1.13.9 | Kotlin-first mocking |
| Mocking | Mockito | 5.16.1 | Also available |
| Robolectric | Robolectric | 4.16 | Latest stable |
| UI Testing | Ultron | 2.3.1 | With Allure integration |
| UI Testing | Espresso | 3.4.0 | Older version for Ultron compat |
| AndroidX Test | Various | 1.5.x | Standard |
| Coverage | JaCoCo | 0.8.14 | Latest |
| Database | Room Testing | 2.8.4 | Latest |

### Current Fixture Architecture
- `BaseCalendarTestFixture` - Builder pattern for test setup
- `MockCalendarProvider` - In-memory calendar simulation
- `MockTimeProvider` / `CNPlusTestClock` - Time control
- `MockContextProvider` - Context/SharedPreferences mocking
- `MockApplicationComponents` - App controller mocking
- `UITestFixture` - UI test helpers with IdlingResource support
- `TestStorageFactory` - Singleton storage for Robolectric tests

---

## Best Practices Research

### 1. Test Organization & Architecture

#### Google's Testing Pyramid Recommendation
```
        /\
       /  \  E2E Tests (10%)
      /----\
     /      \  Integration Tests (20%)
    /--------\
   /          \  Unit Tests (70%)
  /------------\
```

**Your Current Split:**
- Unit (Robolectric): ~18,582 lines
- Integration (Instrumented): ~19,743 lines  
- Ratio: ~48% / 52% (should be more unit-heavy)

**Recommendation:** The near 50/50 split suggests potential for moving more tests to Robolectric where appropriate. However, your note about SQLite limitations is valid - storage tests must stay instrumented.

---

### 2. Fixture Libraries to Consider

#### a) **Hilt Testing** (Recommended for DI-heavy apps)
```kotlin
@HiltAndroidTest
class MyTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var repository: Repository
}
```
**Status:** Not currently using Hilt. Would require architectural changes.
**Verdict:** Low priority unless moving to Hilt for production code.

#### b) **Truth Assertions** (Google's assertion library)
```kotlin
// Instead of:
assertEquals(expected, actual)
assertTrue(list.contains(item))

// Use:
assertThat(actual).isEqualTo(expected)
assertThat(list).contains(item)
```
**Benefits:**
- More readable failure messages
- Fluent API
- Better collection assertions

**Recommendation:** ✅ Easy win - add `com.google.truth:truth:1.1.5` for better assertions.

#### c) **Turbine** (For Flow/Coroutine testing)
```kotlin
@Test
fun testFlow() = runTest {
    viewModel.uiState.test {
        assertEquals(Initial, awaitItem())
        viewModel.loadData()
        assertEquals(Loading, awaitItem())
        assertEquals(Success(data), awaitItem())
    }
}
```
**Status:** Not using Flows heavily in current codebase.
**Verdict:** Consider if adopting more Kotlin Flows.

#### d) **Kaspresso** (UI testing framework)
```kotlin
@Test
fun test() = run {
    step("Open main screen") {
        MainScreen {
            title.isVisible()
            button.click()
        }
    }
    step("Check result") {
        ResultScreen {
            message.hasText("Success")
        }
    }
}
```
**Status:** Currently using Ultron (similar purpose).
**Verdict:** Ultron is fine, but Kaspresso has better documentation. Consider if Ultron becomes problematic.

---

### 3. Robolectric Features You May Not Be Using

#### a) **Qualifiers for Configuration Testing**
```kotlin
@Config(qualifiers = "w820dp-h1180dp-land")
@Test
fun testLandscapeTablet() {
    // Test tablet landscape layout
}

@Config(qualifiers = "night")
@Test
fun testDarkMode() {
    // Test dark theme
}
```
**Use Case:** Testing different screen sizes, orientations, locales.

#### b) **Shadow Classes for System Services**
```kotlin
val shadowAlarmManager = Shadows.shadowOf(alarmManager)
val nextAlarm = shadowAlarmManager.nextScheduledAlarm
assertThat(nextAlarm.triggerAtTime).isEqualTo(expectedTime)
```
**Current:** You're mocking AlarmManager manually.
**Recommendation:** ✅ Use `ShadowAlarmManager` instead of MockK for cleaner alarm testing.

#### c) **Robolectric's ContentProvider Support**
```kotlin
@Config(shadows = [ShadowContentResolver::class])
class CalendarProviderTest {
    @Test
    fun testQueryCalendars() {
        val shadowResolver = Shadows.shadowOf(contentResolver)
        shadowResolver.registerProvider(CalendarContract.AUTHORITY, 
            InMemoryCalendarProvider())
        // Test with in-memory provider
    }
}
```
**Current:** You use real ContentProvider in instrumented tests.
**Note:** This may not work well with your custom SQLite extensions - keep instrumented.

#### d) **Paused Looper Mode** (Modern Robolectric)
```kotlin
@LooperMode(LooperMode.Mode.PAUSED)
class MyTest {
    @Test
    fun testAsyncBehavior() {
        // Async code runs on paused looper
        viewModel.loadData()
        
        // Advance time explicitly
        shadowOf(getMainLooper()).idle()
        
        // Now verify results
    }
}
```
**Status:** Check if using `LEGACY` mode (older behavior).
**Recommendation:** ✅ Ensure using `PAUSED` mode for more realistic async testing.

#### e) **AndroidX Test with Robolectric**
```kotlin
@RunWith(AndroidJUnit4::class)  // Works with both Robolectric and real device
class UnifiedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)
}
```
**Status:** You're already using some AndroidX Test APIs.
**Recommendation:** Ensure consistent use of `ActivityScenario` vs manual launching.

---

### 4. Instrumented Test Features You May Not Be Using

#### a) **Test Orchestrator** (Isolation)
```groovy
android {
    testOptions {
        execution 'ANDROIDX_TEST_ORCHESTRATOR'
    }
}
dependencies {
    androidTestUtil 'androidx.test:orchestrator:1.4.2'
}
```
**Benefits:**
- Each test runs in its own Instrumentation instance
- Tests can't affect each other through static state
- Better crash isolation

**Cost:** Slower test execution.
**Recommendation:** Consider for flaky test debugging.

#### b) **Managed Virtual Devices** (Gradle Managed Devices)
```groovy
android {
    testOptions {
        managedDevices {
            devices {
                pixel2Api30(com.android.build.api.dsl.ManagedVirtualDevice) {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp"
                }
            }
        }
    }
}
```
**Benefits:**
- Reproducible emulator configurations in CI
- No need to manually manage emulators
- Better caching

**Recommendation:** ✅ Good for CI stability - investigate for GitHub Actions.

#### c) **Macrobenchmark for Performance Tests**
```kotlin
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = "com.github.quarck.calnotify",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }
}
```
**Use Case:** Measuring startup time, scroll performance.
**Verdict:** Nice-to-have for performance regression testing.

---

### 5. Fixture Patterns to Consider

#### a) **Object Mother Pattern**
```kotlin
object EventMother {
    fun createDefault() = EventAlertRecord(
        calendarId = 1L,
        eventId = 1L,
        title = "Test Event",
        // ... defaults
    )
    
    fun createMuted() = createDefault().copy(flags = MUTED_FLAG)
    fun createTask() = createDefault().copy(flags = TASK_FLAG)
    fun createWithReminder(minutes: Int) = createDefault().copy(
        alertTime = System.currentTimeMillis() - minutes * 60000
    )
}

// Usage:
val event = EventMother.createMuted()
```
**Benefits:** Centralized test data creation, self-documenting.
**Status:** You have `createTestEvent()` methods scattered across tests.
**Recommendation:** ✅ Consolidate into Object Mother classes.

#### b) **Test Data Builders** (More flexible than Object Mother)
```kotlin
class EventBuilder {
    private var title = "Test Event"
    private var isMuted = false
    private var startTime = System.currentTimeMillis()
    
    fun withTitle(title: String) = apply { this.title = title }
    fun muted() = apply { this.isMuted = true }
    fun startingAt(time: Long) = apply { this.startTime = time }
    
    fun build() = EventAlertRecord(
        title = title,
        // ... map all fields
    )
}

// Usage:
val event = EventBuilder()
    .withTitle("Meeting")
    .muted()
    .startingAt(tomorrow)
    .build()
```
**Current:** Your `BaseCalendarTestFixture.Builder` is similar.
**Recommendation:** Extract event/calendar builders from fixtures.

#### c) **Robot Pattern for UI Tests**
```kotlin
class MainActivityRobot {
    fun clickDismissAll(): MainActivityRobot {
        onView(withId(R.id.dismiss_all)).perform(click())
        return this
    }
    
    fun verifyEventCount(count: Int): MainActivityRobot {
        onView(withId(R.id.event_list))
            .check(matches(hasChildCount(count)))
        return this
    }
}

// Usage:
MainActivityRobot()
    .clickDismissAll()
    .verifyEventCount(0)
```
**Benefits:** Encapsulates UI interactions, readable tests.
**Status:** UITestFixture has some of this, but not full robot pattern.
**Recommendation:** ✅ Consider formalizing robot pattern for complex UI tests.

---

### 6. Testing Database (Room) Best Practices

#### a) **In-Memory Database for Tests**
```kotlin
@Before
fun setup() {
    db = Room.inMemoryDatabaseBuilder(
        context,
        AppDatabase::class.java
    ).allowMainThreadQueries().build()
}
```
**Status:** Your instrumented tests use real database.
**Note:** For your custom SQLite extensions, real DB is correct. But for pure Room tests, in-memory is faster.

#### b) **Migration Testing**
```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO events ...")
            close()
        }
        
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
    }
}
```
**Status:** You have migration tests.
**Recommendation:** Ensure using `MigrationTestHelper` for automatic validation.

---

### 7. Parameterized Testing

#### a) **JUnit 4 Parameterized** (Current)
```kotlin
@RunWith(Parameterized::class)
class EventFormatterTest(
    private val input: Long,
    private val expected: String
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data() = listOf(
            arrayOf(3600000L, "1 hour"),
            arrayOf(7200000L, "2 hours"),
        )
    }
}
```

#### b) **JUnit 5 Parameterized** (Better syntax)
```kotlin
@ParameterizedTest
@CsvSource(
    "3600000, '1 hour'",
    "7200000, '2 hours'"
)
fun formatDuration(input: Long, expected: String) {
    assertEquals(expected, formatter.format(input))
}
```
**Blocker:** JUnit 5 has limited Android support.
**Recommendation:** Stay with JUnit 4 parameterized for now.

---

### 8. Flaky Test Prevention

#### a) **IdlingResources** (You're already using this!)
```kotlin
class AsyncTaskIdlingResource : IdlingResource {
    // Track async operations
}
```
**Status:** ✅ Already implemented in `AsyncTaskIdlingResource`.

#### b) **Retry Rules**
```kotlin
class RetryRule(private val retryCount: Int) : TestRule {
    override fun apply(base: Statement, description: Description) = 
        object : Statement() {
            override fun evaluate() {
                var lastThrowable: Throwable? = null
                repeat(retryCount) {
                    try {
                        base.evaluate()
                        return
                    } catch (t: Throwable) {
                        lastThrowable = t
                    }
                }
                throw lastThrowable!!
            }
        }
}
```
**Use Case:** For inherently flaky tests (timing-dependent).
**Recommendation:** Use sparingly - fix root cause when possible.

#### c) **Test Sharding**
**Status:** ✅ Already using sharding in CI (4 shards visible in artifacts).

---

## Priority Recommendations

### Quick Wins (Low Effort, High Value)
1. ✅ **Add Truth assertions** - Better failure messages, minimal change
2. ✅ **Use ShadowAlarmManager** - Cleaner than manual mocking
3. ✅ **Create Object Mother classes** - Consolidate `createTestEvent()` variants
4. ✅ **Verify PAUSED looper mode** - More realistic async testing

### Medium Effort
5. 📝 **Formalize Robot Pattern** - For complex UI test flows
6. 📝 **Extract Test Data Builders** - From fixtures to standalone classes
7. 📝 **Document fixture usage** - Add examples and explanations

### Investigate Further
8. 🔍 **Gradle Managed Devices** - For CI stability
9. 🔍 **Test Orchestrator** - If seeing cross-test pollution
10. 🔍 **Kaspresso** - If Ultron becomes limiting

### Not Recommended Now
- ❌ Hilt Testing - Requires architectural changes
- ❌ JUnit 5 - Limited Android support
- ❌ Moving SQLite tests to Robolectric - Your concerns are valid

---

## Sample Implementation: Object Mother

Here's how you could consolidate event creation:

```kotlin
// testutils/EventMother.kt
object EventMother {
    private val clock = { System.currentTimeMillis() }
    
    fun default(
        eventId: Long = 1L,
        calendarId: Long = 1L,
        title: String = "Test Event"
    ) = EventAlertRecord(
        calendarId = calendarId,
        eventId = eventId,
        isAllDay = false,
        isRepeating = false,
        alertTime = clock(),
        notificationId = eventId.toInt(),
        title = title,
        desc = "",
        startTime = clock() + Consts.HOUR_IN_MILLISECONDS,
        endTime = clock() + 2 * Consts.HOUR_IN_MILLISECONDS,
        instanceStartTime = clock() + Consts.HOUR_IN_MILLISECONDS,
        instanceEndTime = clock() + 2 * Consts.HOUR_IN_MILLISECONDS,
        location = "",
        lastStatusChangeTime = clock(),
        snoozedUntil = 0L,
        displayStatus = EventDisplayStatus.Hidden,
        color = 0,
        origin = EventOrigin.ProviderBroadcast,
        timeFirstSeen = clock(),
        eventStatus = EventStatus.Confirmed,
        attendanceStatus = AttendanceStatus.None,
        flags = 0
    )
    
    fun muted(eventId: Long = 1L) = default(eventId).also { it.isMuted = true }
    fun task(eventId: Long = 1L) = default(eventId).also { it.isTask = true }
    fun alarm(eventId: Long = 1L) = default(eventId).also { it.isAlarm = true }
    fun snoozed(eventId: Long = 1L, until: Long) = default(eventId).copy(snoozedUntil = until)
    fun allDay(eventId: Long = 1L) = default(eventId).copy(isAllDay = true)
}

// Usage in tests:
val event = EventMother.muted()
val snoozedEvent = EventMother.snoozed(eventId = 2, until = tomorrow)
```

---

## References

- [Android Testing Codelab](https://developer.android.com/codelabs/advanced-android-kotlin-testing)
- [Robolectric Documentation](http://robolectric.org/)
- [Testing Kotlin Flows](https://developer.android.com/kotlin/flow/test)
- [Espresso Idling Resources](https://developer.android.com/training/testing/espresso/idling-resource)
- [Truth Assertion Library](https://truth.dev/)
- [Kaspresso Framework](https://github.com/KasperskyLab/Kaspresso)
