# Architecture Overview: GlobalState and ApplicationController

## Summary

This app uses a **non-standard but sensible** architecture that separates Android lifecycle concerns from business logic. This document explains how the two core classes interact.

## The Two Core Classes

### GlobalState - The Android Application Class

**Location:** `android/app/src/main/java/com/github/quarck/calnotify/GlobalState.kt`

**What it is:** The actual Android `Application` subclass - the entry point that Android creates when the app process starts.

**Responsibilities (minimal by design):**
- Holds only ephemeral/instance-specific state:
  - `lastNotificationRePost` - when notifications were last refreshed
  - `lastTimerBroadcastReceived` - when last timer broadcast arrived
- Initializes React Native (this is a hybrid RN app)
- Initializes SoLoader for native code
- Applies theme preferences on startup
- Creates notification channels (required for Android 8+)

**Extension property for access:**
```kotlin
val Context.globalState: GlobalState?
    get() {
        val appCtx = applicationContext
        if (appCtx is GlobalState)
            return appCtx
        return null
    }
```

This allows any code with a `Context` to access `context.globalState?.lastNotificationRePost`.

### ApplicationController - The Business Logic Singleton

**Location:** `android/app/src/main/java/com/github/quarck/calnotify/app/ApplicationController.kt`

**What it is:** A Kotlin `object` (singleton) - **not** an Android component! It's just a regular Kotlin object that holds all the app's business logic.

**Responsibilities (extensive):**
- Event management (register, dismiss, snooze, restore)
- Notification management
- Alarm scheduling
- Calendar monitoring
- Quiet hours management
- All coordination between subsystems

**Key design pattern:** Receives `Context` as a parameter rather than holding one:
```kotlin
object ApplicationController : ApplicationControllerInterface {
    fun onBootComplete(context: Context) { ... }
    fun onCalendarChanged(context: Context) { ... }
    fun dismissEvent(context: Context, ...) { ... }
}
```

This avoids memory leaks and makes the code more testable.

## How They Interact

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android OS                                │
└────────────────────────────┬────────────────────────────────────┘
                             │ creates on app start
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  GlobalState (Application)                                       │
│  - React Native host                                             │
│  - Ephemeral state (lastNotificationRePost, etc.)                │
│  - Theme/Notification channel init                               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────┐  ┌─────────────────────┐  ┌───────────────┐
│  BroadcastReceivers │  │     Activities      │  │   Services    │
│  - Boot             │  │  - MainActivity     │  │  - Snooze     │
│  - Calendar changed │  │  - ViewEventActivity│  │  - Dismiss    │
│  - Alarms           │  │  - Settings         │  │  - etc.       │
└─────────┬───────────┘  └──────────┬──────────┘  └───────┬───────┘
          │                         │                      │
          │    All call into        │                      │
          └─────────────────────────┼──────────────────────┘
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│  ApplicationController (object/singleton)                        │
│  - ALL business logic                                            │
│  - Event management                                              │
│  - Notification management                                       │
│  - Calendar monitoring                                           │
│  - Alarm scheduling                                              │
└─────────────────────────────────────────────────────────────────┘
```

## Entry Points

The key insight is that **Android components call ApplicationController**, not the other way around:

### BroadcastReceivers
```kotlin
// BootCompleteBroadcastReceiver.kt
class BootCompleteBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null)
            ApplicationController.onBootComplete(context)
    }
}

// CalendarChangedBroadcastReceiver.kt
class CalendarChangedBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null)
            ApplicationController.onCalendarChanged(context)
    }
}
```

### Activities
```kotlin
// MainActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ApplicationController.onMainActivityCreate(this)
}

override fun onResume() {
    super.onResume()
    ApplicationController.onMainActivityResumed(this, shouldForceRepost, monitorSettingsChanged)
}
```

### Services
```kotlin
// NotificationActionDismissService.kt
ApplicationController.dismissEvent(context, dismissType, event)
```

## Why This Pattern?

### 1. Multiple Entry Points
Calendar notification apps respond to many system events:
- Phone boots → need to restore notifications
- Calendar changes → need to check for new/moved events
- Snooze alarm fires → need to re-show notification
- User taps notification → need to handle action
- Time zone changes → need to recalculate times
- App updates → need to refresh state

All these entry points need **consistent behavior**. Centralizing logic in one place ensures this.

### 2. Separation of Concerns
- **GlobalState:** Android lifecycle only (Application class boilerplate)
- **ApplicationController:** All business logic (pure Kotlin, no Android lifecycle)

### 3. Testability
The ApplicationController pattern with injectable providers makes testing easier:
```kotlin
// In ApplicationController
var eventsStorageProvider: ((Context) -> EventsStorageInterface)? = null

private fun getEventsStorage(ctx: Context): EventsStorageInterface {
    return eventsStorageProvider?.invoke(ctx) ?: EventsStorage(ctx)
}

// In tests
ApplicationController.eventsStorageProvider = { mockEventsStorage }
```

### 4. Avoiding Memory Leaks
By receiving `Context` as a parameter rather than storing it, there's no risk of holding onto an Activity context after it's destroyed.

## Comparison to "Standard" Simple Apps

A typical simple Android app might look like:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // UI setup
        val button = findViewById<Button>(R.id.myButton)
        
        // Business logic mixed right in
        button.setOnClickListener {
            val items = loadItemsFromDatabase()  // DB logic here
            showNotification(items.size)          // Notification logic here
            updateUI(items)                       // UI logic here
        }
    }
}
```

This works for simple apps with one entry point (user opens app). It breaks down when you have 10+ entry points that all need the same behavior.

## Historical Context

This codebase was started in 2016, before Google provided official architecture guidance (Architecture Components came in 2017). The original author created a sensible pattern that predates the official recommendations.

The pattern is actually **ahead of its time** in some ways - the separation of concerns and injectable providers are exactly what modern architecture recommends, just implemented differently.

## Modern Equivalent

Today, a similar architecture would use:
- **ViewModel** instead of singleton object (for lifecycle awareness)
- **Repository pattern** for data access
- **Hilt** for dependency injection (instead of manual providers)
- **Coroutines** for async operations

See `docs/dev_todo/android_modernization.md` for migration considerations.

