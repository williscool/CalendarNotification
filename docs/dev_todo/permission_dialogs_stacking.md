# Permission Dialogs Stacking Fix

**Issue:** [#114 - Bug: Perms dialogs may stack causing confusing UI](https://github.com/williscool/CalendarNotification/issues/114)

**Status:** Implemented (awaiting testing)

## Problem

On fresh app install (Android 13+), multiple permission dialogs can appear simultaneously:

1. After calendar permission is granted, `checkNotificationPermission()` is called (non-blocking)
2. Immediately after, battery optimization dialog check runs
3. Both dialogs stack on top of each other, confusing users

The user sees a mess of overlapping dialogs and may:
- Accidentally dismiss the wrong one
- Get frustrated and force-close the app
- Deny everything just to make dialogs go away

## Current Permission Flow

```
onResume()
  └── checkPermissions()
        ├── Calendar not granted → Show calendar dialog/request (BLOCKS)
        └── Calendar granted
              ├── checkNotificationPermission() ← NON-BLOCKING
              └── Battery optimization check ← RUNS IMMEDIATELY (BUG!)
```

## Solution

### Phase 1: Sequential Permission Flow (This PR)

Make permission dialogs appear one at a time:

```
onResume()
  └── checkPermissions()
        ├── Calendar not granted → Show calendar dialog/request (BLOCKS)
        └── Calendar granted → checkNotificationPermission()
                                    ├── Already granted → checkBatteryOptimization()
                                    ├── Shows rationale dialog → onDismiss → checkBatteryOptimization()
                                    └── Shows system dialog → onRequestPermissionsResult → checkBatteryOptimization()
```

**Implementation:**
1. Extract battery check to `checkBatteryOptimization()` method
2. Add `pendingBatteryOptimizationCheck` flag
3. Modify `checkNotificationPermission()` to:
   - If permission already granted, call `checkBatteryOptimization()` directly
   - If showing rationale dialog, call `checkBatteryOptimization()` in dismiss callbacks
   - If requesting system permission, set flag for `onRequestPermissionsResult`
4. In `onRequestPermissionsResult`, check flag and call `checkBatteryOptimization()`

### Phase 2: User Feedback for Missing Permissions

When user dismisses/denies permissions, provide clear feedback:

**Notification Permission Denied:**
- Show toast: "Notifications disabled - you won't receive calendar reminders"
- On subsequent opens, show non-modal banner at top of event list

**Calendar Permission Denied:**
- Already handled: Shows "Application has no calendar permissions" message
- App is essentially non-functional without this

**Battery Optimization Not Exempted:**
- Show toast when "Later" tapped: "Reminders may be delayed by battery optimization"
- Already has "Don't show again" option

### String Resources Needed

```xml
<!-- Permission feedback toasts -->
<string name="notification_permission_denied_toast">Notifications disabled - you won\'t receive calendar reminders</string>
<string name="battery_optimization_later_toast">Reminders may be delayed by battery optimization</string>

<!-- Permission required blocking messages (for future use) -->
<string name="notification_permission_required_action">Please enable notifications to use this feature</string>
```

## Testing Considerations

The permission flow is difficult to test in instrumented tests because:
- System permission dialogs can't be easily controlled
- `shouldShowRequestPermissionRationale` behavior is OS-controlled

Testing approach:
- Unit test the sequencing logic with mocked permission states
- Manual testing on fresh install
- Robolectric tests for permission state checking

## Files to Modify

- `android/app/src/main/java/com/github/quarck/calnotify/ui/MainActivityBase.kt` - Main fix
- `android/app/src/main/res/values/strings.xml` - New string resources
- Test files as needed

## Notes

- Calendar permission is already properly blocking (in if/else structure)
- The "persistent asking" on each resume is intentional - permissions ARE essential
- Battery optimization "Don't show again" is preserved - user can opt out permanently
