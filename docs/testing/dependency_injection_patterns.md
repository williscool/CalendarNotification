# Dependency Injection Patterns for Testing

## Overview

This codebase uses manual dependency injection (DI) to enable testing of legacy code without relying on unreliable mocking techniques. Two patterns are used depending on the context.

**Default Pattern:** Optional Parameter Injection (Pattern A)  
**Fallback Pattern:** Companion Object Provider (Pattern B)

### Prior Art

This document consolidates patterns that evolved across several PRs and docs:

- [Constructor Mocking Limitations](../dev_completed/constructor-mocking-android.md) - Original doc (PR #73, April 2025) explaining why MockK constructor mocking fails and proposing the optional parameter solution
- [Event Dismissal Testing Notes](../dev_completed/event_dismissal_testing_notes.md) - GPT-4.1's assessment noting the "minimal optional-parameter pattern"
- [Test Development Chronology](test_development_chronology.md) - Documents the evolution of these patterns through PRs #60-82
- [Android Modernization](../dev_todo/android_modernization.md) - Documents Hilt as a potential future replacement (not recommended currently)

---

## Why Manual DI?

MockK's `mockkConstructor` and `mockkStatic` **do not work reliably** in Android instrumentation tests, especially for classes extending Android framework classes like `SQLiteOpenHelper`. These fail with errors like:

- `FAILURE TO RETRANSFORM Unable to perform redefinition...`
- `ArrayIndexOutOfBoundsException`
- Mock verifications failing even when stubbing worked

The solution is to allow optional injection of dependencies, so tests can pass mocks while production code uses real implementations.

---

## Pattern A: Optional Parameter Injection (Preferred)

Add an optional parameter with a default of `null`. When `null`, use the real implementation.

### Example Implementation

```kotlin
// In ApplicationController
fun dismissEvent(
    context: Context,
    eventId: Long,
    instanceStartTime: Long,
    notificationId: Int,
    notifyActivity: Boolean,
    db: EventsStorageInterface? = null,  // Optional DI
    dismissedEventsStorage: DismissedEventsStorage? = null
) {
    // Use injected or create real
    val storage = db ?: EventsStorage(context)
    val dismissed = dismissedEventsStorage ?: DismissedEventsStorage(context)
    
    // ... rest of implementation
}
```

### Example Test Usage

```kotlin
@Test
fun testDismissEvent() {
    val mockStorage = mockk<EventsStorageInterface>(relaxed = true)
    val mockDismissed = mockk<DismissedEventsStorage>(relaxed = true)
    
    // Pass mocks directly - no setup/cleanup needed
    controller.dismissEvent(
        context = testContext,
        eventId = 123L,
        instanceStartTime = now,
        notificationId = 1,
        notifyActivity = false,
        db = mockStorage,
        dismissedEventsStorage = mockDismissed
    )
    
    verify { mockStorage.deleteEvent(any()) }
}
```

### When to Use Pattern A

- ✅ **Default choice** for any new testability refactor
- ✅ Methods in objects/singletons like `ApplicationController`
- ✅ When dependency is used in only 1-2 methods
- ✅ When thread safety matters (parallel tests)

### Advantages

- Explicit at call site - clear what dependencies are being used
- No shared mutable state
- No cleanup required in `@After`
- Thread-safe for parallel test execution
- Easy to understand dependency flow

### Disadvantages

- Parameters propagate through call chains
- Can get verbose with many dependencies

---

## Pattern B: Companion Object Provider (Fallback)

Use a nullable provider lambda in a companion object. When `null`, create the real implementation.

### Example Implementation

```kotlin
class MainActivity : AppCompatActivity() {
    
    companion object {
        /**
         * Provider for EventsStorage to enable dependency injection in tests.
         */
        var eventsStorageProvider: ((Context) -> EventsStorageInterface)? = null
        
        /**
         * Gets EventsStorage - uses provider if set, otherwise creates real instance.
         */
        fun getEventsStorage(context: Context): EventsStorageInterface {
            return eventsStorageProvider?.invoke(context) ?: EventsStorage(context)
        }
        
        /**
         * Reset all providers - call in @After to prevent test pollution.
         */
        fun resetProviders() {
            eventsStorageProvider = null
        }
    }
    
    private fun someMethod() {
        getEventsStorage(this).use { storage ->
            // ... use storage
        }
    }
}
```

### Example Test Usage

```kotlin
@Before
fun setup() {
    mockStorage = mockk<EventsStorageInterface>(relaxed = true)
    MainActivity.eventsStorageProvider = { mockStorage }
}

@After
fun cleanup() {
    MainActivity.resetProviders()  // CRITICAL: prevent test pollution
}

@Test
fun testSomething() {
    // Activity will use mockStorage automatically
    launchActivity<MainActivity>()
    // ...
}
```

### When to Use Pattern B

- ✅ **Activities** - can't easily pass parameters to lifecycle methods
- ✅ **Fragments** - lifecycle methods like `onViewCreated()` have fixed signatures
- ✅ **BroadcastReceivers** - `onReceive()` signature is fixed by Android
- ✅ **Services** - similar lifecycle constraints
- ✅ When same dependency is used in many methods of a class
- ✅ When Pattern A would require propagating parameters through 3+ call levels

### Advantages

- Cleaner method signatures
- Scales well when many methods use same dependency
- Works with Android components that have fixed signatures

### Disadvantages

- **Must remember to reset in `@After`** or tests will pollute each other
- Mutable static state - not safe for parallel tests on same class
- "Magic" - harder to trace where dependencies come from
- Easy to forget cleanup

---

## Pattern Comparison

| Aspect | Pattern A (Optional Param) | Pattern B (Companion Provider) |
|--------|---------------------------|-------------------------------|
| **Use by default** | ✅ Yes | ❌ Only when A doesn't work |
| **Explicit at call site** | ✅ Yes | ❌ No |
| **Thread/parallel safe** | ✅ Yes | ❌ No |
| **Cleanup required** | ✅ None | ❌ Must reset providers |
| **Works with Activities** | ❌ Awkward | ✅ Yes |
| **Works with Fragments** | ❌ Awkward | ✅ Yes |
| **Works with BroadcastReceivers** | ❌ No | ✅ Yes |
| **Scales with many deps** | ❌ Gets verbose | ✅ Better |

---

## Current Usage in Codebase

### Pattern A (Optional Parameter)
- `ApplicationController.dismissEvent()`
- `ApplicationController.registerNewEvent()`
- `ApplicationController.restoreEvent()`
- `ApplicationController.safeDismissEventsFromRescheduleConfirmations()`
- `UpcomingEventsProvider` - constructor injection for `monitorStorage`, `calendarProvider`, `clock`, `settings`

### Pattern B (Companion Provider)
- `ApplicationController` - `eventsStorageProvider`, `reminderStateProvider`, `monitorStorageProvider`
- `MainActivity` - `eventsStorageProvider`, `dismissedEventsStorageProvider`
- `ViewEventActivityNoRecents` - `eventsStorageProvider`
- `DismissedEventsActivity` - `dismissedEventsStorageProvider`
- `PreActionActivity` - `clockProvider`, `monitorStorageProvider`, `eventsStorageProvider`, `dismissedEventsStorageProvider`
- `AlarmScheduler` - `eventsStorageProvider`, `reminderStateProvider`
- `ReminderAlarmBroadcastReceiver` - multiple providers (clock, settings, storage, etc.)
- `ActiveEventsFragment` - `eventsStorageProvider`
- `UpcomingEventsFragment` - `monitorStorageProvider`, `calendarProviderProvider`
- `DismissedEventsFragment` - `dismissedEventsStorageProvider`

---

## Decision Flowchart

```
Need to make legacy code testable?
│
├─ Can you add optional parameters to the method?
│  │
│  ├─ YES → Use Pattern A (Optional Parameter)
│  │
│  └─ NO (Activity/Fragment lifecycle, BroadcastReceiver, etc.)
│     │
│     └─ Use Pattern B (Companion Provider)
│        └─ Remember to add resetProviders() and call in @After!
```

---

## What About Hilt?

Hilt is documented in `../dev_todo/android_modernization.md` as a potential modernization. However:

- **Effort:** 20-30 hours to implement
- **Return:** Manual DI is already working well
- **Verdict:** Not recommended unless the app grows significantly

The current manual patterns are simple, explicit, and sufficient for this codebase's needs.
