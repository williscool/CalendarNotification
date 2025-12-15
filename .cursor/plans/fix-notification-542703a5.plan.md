---
name: Fix Notification Permission Dialog Dismissal
overview: ""
todos:
  - id: b7d9debf-f1f7-4a59-99cf-f190937d1f2a
    content: Add resourceId-based matching for permission controller buttons
    status: pending
  - id: 0aa2f211-35e2-4a66-835d-dea1ce8f353e
    content: Replace text() with textContains() for better flexibility
    status: pending
---

# Fix Notification Permission Dialog Dismissal

## Problem

The `dismissTargetSdkWarningDialog()` function in [UITestFixture.kt](android/app/src/androidTest/java/com/github/quarck/calnotify/testutils/UITestFixture.kt) works for battery optimization, background running, and SDK warning dialogs, but fails for the Android 13+ notification permission dialog.

## Root Cause

The current implementation uses exact text matching with `UiSelector().text(buttonText)`. The Android 13+ notification permission dialog uses a different UI framework (Material permission controller) where buttons may:

- Have wrapped text or accessibility labels that don't match exactly
- Require resourceId-based matching (`com.android.permissioncontroller:id/permission_allow_button`)
- Need `textContains()` instead of exact `text()` matching

## Solution

Add a multi-strategy button finding approach:

1. **Try resourceId first** for known permission controller IDs:

   - `com.android.permissioncontroller:id/permission_allow_button`
   - `com.android.permissioncontroller:id/permission_deny_button`

2. **Fallback to textContains()** instead of exact text matching - handles cases where button text has subtle differences

3. **Keep existing text() matching** as final fallback for older dialogs

## Changes to [UITestFixture.kt](android/app/src/androidTest/java/com/github/quarck/calnotify/testutils/UITestFixture.kt)

Update `dismissTargetSdkWarningDialog()` around lines 86-119:

```kotlin
// First, try known resourceIds for permission dialogs
val resourceIdsToTry = listOf(
    "com.android.permissioncontroller:id/permission_allow_button",
    "com.android.permissioncontroller:id/permission_deny_button"
)

for (resourceId in resourceIdsToTry) {
    val button = device.findObject(UiSelector().resourceId(resourceId))
    if (button.waitForExists(FIRST_DIALOG_TIMEOUT_MS)) {
        DevLog.info(LOG_TAG, "Found permission dialog via resourceId: $resourceId")
        button.click()
        button.waitUntilGone(DIALOG_DISMISS_ANIMATION_MS)
        totalDismissed++
        foundOne = true
        break
    }
}

// If no resourceId match, try textContains() for flexibility
if (!foundOne) {
    for (buttonText in buttonsToTry) {
        val button = device.findObject(UiSelector().textContains(buttonText))
        if (button.waitForExists(FIRST_DIALOG_TIMEOUT_MS)) {
            // ... existing click logic
        }
    }
}
```

## Why This Should Work

- Android's permission controller (the system component that shows permission dialogs) consistently uses these resourceIds across Android 13+
- `textContains()` is more flexible than exact `text()` matching and handles edge cases
- The existing text-based matching remains as fallback for app-specific dialogs (SDK warning, battery optimization)