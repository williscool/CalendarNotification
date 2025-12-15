# Future Simplification: Raising minSdkVersion

## Current State (after Android 15 update)

- `minSdkVersion`: 24 (Android 7.0 Nougat, August 2016)
- `compileSdkVersion`: 35 (Android 15)
- `targetSdkVersion`: 35 (Android 15)

## Simplification Opportunities

### If Raising minSdk to 26 (Android 8.0 Oreo, August 2017)

**Benefits:**
- Notification channels are always available (no version check needed)
- Can use `Notification.Builder` directly instead of `NotificationCompat`
- Oreo has ~<3% of active devices (as of late 2024)

**Files to simplify:**
- `NotificationChannels.kt` - Remove `Build.VERSION.SDK_INT < Build.VERSION_CODES.O` check

### If Raising minSdk to 31 (Android 12, October 2021)

**Benefits:**
- `FLAG_IMMUTABLE` always required for PendingIntents
- Can remove `pendingIntentFlagCompat()` wrapper entirely
- Just always add `PendingIntent.FLAG_IMMUTABLE`

**Files to simplify:**
- `SystemUtils.kt` - Remove `pendingIntentFlagCompat()`, inline the flag
- `CalendarMonitor.kt` - Direct use of FLAG_IMMUTABLE
- `EventNotificationManager.kt` - Direct use of FLAG_IMMUTABLE

### If Raising minSdk to 33 (Android 13, August 2022)

**Benefits:**
- `POST_NOTIFICATIONS` permission always required
- Can simplify notification permission flow
- Runtime receiver export flags always required

**Files to simplify:**
- `PermissionsManager.kt` - Remove version checks in `hasNotificationPermission()`
- `MainActivity.kt` - Simplify runtime receiver registration (always use RECEIVER_NOT_EXPORTED flag)

## Recommendations

1. **Conservative approach**: Raise to minSdk 26 first
   - Small user impact (~3% of users)
   - Removes notification channel version checks
   - Aligns with modern Android development practices

2. **Aggressive approach**: Raise to minSdk 31
   - Moderate user impact
   - Significant code simplification
   - PendingIntent flags become much simpler

## Android Version Distribution Reference

Check current distribution at: https://apilevels.com/

As of late 2024:
- API 24-25 (Nougat): ~2%
- API 26-27 (Oreo): ~3%
- API 28 (Pie): ~5%
- API 29-30 (Android 10-11): ~20%
- API 31+ (Android 12+): ~70%

## Implementation Notes

When raising minSdk:
1. Update `minSdkVersion` in both `android/build.gradle` and `android/app/build.gradle`
2. Search for version checks: `grep -r "VERSION_CODES\." android/app/src/main/`
3. Remove/simplify checks that are now always true
4. Update Robolectric test `@Config(sdk = [...])` annotations if needed
5. Test on emulator at the new minimum API level

