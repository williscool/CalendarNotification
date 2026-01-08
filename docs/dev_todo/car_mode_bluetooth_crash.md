# Car Mode Settings Page Crash - Missing BLUETOOTH_CONNECT Permission

**Issue:** [#159](https://github.com/williscool/CalendarNotification/issues/159)

## Status: ✅ COMPLETED

Implemented as part of the Settings Backup feature work. See `settings_backup.md`.

**Changes made:**
- Added `BLUETOOTH_CONNECT` permission to AndroidManifest.xml (with `maxSdkVersion="30"` on legacy `BLUETOOTH`)
- Added `hasBluetoothConnectPermission()`, `shouldShowBluetoothConnectRationale()`, `requestBluetoothConnectPermission()` to `PermissionsManager.kt`
- Updated `CarModeActivity.kt` to check/request permission on resume, handle permission results
- Added defensive try-catch in `BTDeviceManager.pairedDevices` for `SecurityException`
- Added `bluetooth_permission_required` string resource
- Added Robolectric tests for Bluetooth permission methods

---

## Problem Summary

The Car Mode Settings page crashes immediately when opened on Android 12+ devices due to missing `BLUETOOTH_CONNECT` runtime permission.

## Root Cause

Starting with Android 12 (API 31), Google introduced new Bluetooth permissions to replace the legacy `BLUETOOTH` and `BLUETOOTH_ADMIN` permissions:

- `BLUETOOTH_CONNECT` - Required to connect to paired devices, read device names/addresses
- `BLUETOOTH_SCAN` - Required to discover new devices
- `BLUETOOTH_ADVERTISE` - Required to make device discoverable

The app targets SDK 36 but only declares the legacy `BLUETOOTH` permission, which is insufficient for API 31+.

### Crash Flow

1. User opens Settings → Car Mode
2. `CarModeActivity.onResume()` calls `bluetoothManager.pairedDevices`
3. `BTDeviceManager.pairedDevices` accesses:
   - `adapter.bondedDevices` (requires BLUETOOTH_CONNECT on API 31+)
   - `BluetoothDevice.name` (requires BLUETOOTH_CONNECT on API 31+)
   - `BluetoothDevice.address` (requires BLUETOOTH_CONNECT on API 31+)
4. Without permission → `SecurityException` → crash

### Affected Files

- `android/app/src/main/AndroidManifest.xml` - Missing permission declaration
- `android/app/src/main/java/com/github/quarck/calnotify/permissions/PermissionsManager.kt` - No BLUETOOTH_CONNECT handling
- `android/app/src/main/java/com/github/quarck/calnotify/prefs/CarModeActivity.kt` - No permission check before Bluetooth access
- `android/app/src/main/java/com/github/quarck/calnotify/bluetooth/BTDeviceManager.kt` - No try-catch for SecurityException

## Implementation Plan

### Phase 1: Add Permission Declaration

Update `AndroidManifest.xml`:

```xml
<!-- Legacy Bluetooth permission - only needed on API 30 and below -->
<uses-permission android:name="android.permission.BLUETOOTH" 
    android:maxSdkVersion="30" />

<!-- Android 12+ (API 31) - Required for accessing paired Bluetooth devices -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Coarse location still needed for Bluetooth on some devices -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

### Phase 2: Update PermissionsManager

Add to `PermissionsManager.kt`:

```kotlin
const val PERMISSION_REQUEST_BLUETOOTH_CONNECT = 3

/**
 * Check if we have Bluetooth connect permission.
 * On Android 12+ (API 31), BLUETOOTH_CONNECT is required.
 * On older versions, permission is implicitly granted via BLUETOOTH.
 */
fun hasBluetoothConnectPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        true // Legacy BLUETOOTH permission covers this on older versions
    }
}

fun shouldShowBluetoothConnectRationale(activity: Activity): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        activity.shouldShowRationale(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        false
    }
}

fun requestBluetoothConnectPermission(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ActivityCompat.requestPermissions(activity,
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
            PERMISSION_REQUEST_BLUETOOTH_CONNECT)
    }
}
```

### Phase 3: Update CarModeActivity

Update `CarModeActivity.kt` to check permission before accessing Bluetooth:

```kotlin
override fun onResume() {
    super.onResume()

    // Check Bluetooth permission first (required on Android 12+)
    if (!PermissionsManager.hasBluetoothConnectPermission(this)) {
        PermissionsManager.requestBluetoothConnectPermission(this)
        return
    }

    // Also check location permission for older Android versions
    if (!PermissionsManager.hasAccessCoarseLocation(this)) {
        PermissionsManager.requestLocationPermissions(this)
    }

    loadBluetoothDevices()
}

override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    
    when (requestCode) {
        PermissionsManager.PERMISSION_REQUEST_BLUETOOTH_CONNECT -> {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadBluetoothDevices()
            } else {
                // Show message that Bluetooth permission is required for Car Mode
                noDevicesText.text = getString(R.string.bluetooth_permission_required)
                noDevicesText.visibility = View.VISIBLE
            }
        }
        PermissionsManager.PERMISSION_REQUEST_LOCATION -> {
            loadBluetoothDevices()
        }
    }
}

private fun loadBluetoothDevices() {
    background {
        val triggers = bluetoothManager.storage.carModeTriggerDevices
        DevLog.info(LOG_TAG, "Known triggers: ${triggers.joinToString { ", " }}")

        val triggersHash = triggers.toHashSet()
        val devices = bluetoothManager.pairedDevices?.map { 
            BlueboothDeviceListEntry(triggersHash.contains(it.address), it)
        }?.toList()

        runOnUiThread {
            if (devices != null) {
                noDevicesText.visibility = if (devices.isNotEmpty()) View.GONE else View.VISIBLE
                adapter.entries = devices.toTypedArray()
                adapter.notifyDataSetChanged()
            } else {
                noDevicesText.visibility = View.VISIBLE
            }
        }
    }
}
```

### Phase 4: Defensive Error Handling in BTDeviceManager

Add try-catch in `BTDeviceManager.kt` as a safety net:

```kotlin
val pairedDevices: List<BTDeviceSummary>?
    get() = try {
        adapter
            ?.bondedDevices
            ?.map { BTDeviceSummary(it.name ?: "Unknown", it.address, isDeviceConnected(it)) }
            ?.toList()
    } catch (e: SecurityException) {
        DevLog.error(LOG_TAG, "SecurityException accessing bonded devices - missing BLUETOOTH_CONNECT permission", e)
        null
    }
```

### Phase 5: Add String Resource

Add to `strings.xml`:

```xml
<string name="bluetooth_permission_required">Bluetooth permission is required to configure Car Mode devices</string>
```

## Testing Plan

### Unit Tests (Robolectric)

1. **PermissionsManager tests:**
   - Test `hasBluetoothConnectPermission` returns true on API < 31
   - Test `hasBluetoothConnectPermission` checks correct permission on API >= 31
   - Test `requestBluetoothConnectPermission` is no-op on API < 31

### Instrumentation Tests

1. **CarModeActivity permission flow:**
   - Test activity doesn't crash when permission is denied
   - Test activity shows appropriate message when permission denied
   - Test activity loads devices when permission granted

2. **BTDeviceManager defensive handling:**
   - Test `pairedDevices` returns null (not crash) on SecurityException

### Manual Testing

1. Fresh install on Android 12+ device
2. Open Settings → Car Mode
3. Verify permission dialog appears
4. Test denying permission shows appropriate message
5. Test granting permission shows paired devices
6. Test on Android 11 device (should work without new permission)

## Related Documentation

- [Android 12 Bluetooth Permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [BLUETOOTH_CONNECT Permission](https://developer.android.com/reference/android/Manifest.permission#BLUETOOTH_CONNECT)

## Notes

- The `BLUETOOTH_SCAN` permission is NOT needed since we're only listing paired devices, not scanning for new ones
- Location permission is still required for Bluetooth on some pre-Android 12 devices
- Consider adding rationale dialog explaining why Bluetooth permission is needed

