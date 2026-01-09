# Calendar Sync Refresh - Missing Calendars After Fresh Install

**Issue:** User reported only 1 of 2 Google calendars showing in "Handled Calendars" on fresh install (Pixel Fold). Required phone restart to see both.

## Status: 📋 PLANNING

---

## Problem Summary

On fresh app install (especially on new devices), the Android calendar provider may not have fully synced all calendars yet. The app queries `CalendarContract.Calendars` and only sees what Android has synced so far, leading to missing calendars in the "Handled Calendars" list.

Currently, the only solution is:
- Wait for Android to sync on its own
- Restart the phone (forces sync)
- Manually toggle sync settings

**We should provide an in-app way to request a calendar sync and refresh the list.**

## Root Cause

1. Android's calendar sync is managed by the system `SyncManager`
2. On fresh install, account sync may not be complete
3. `CalendarProvider.getCalendars()` queries `CalendarContract.Calendars.CONTENT_URI` with no filtering
4. The query returns only calendars that Android has synced - it cannot return what doesn't exist in the local DB yet
5. The app has no mechanism to request Android to sync calendar data

### Current Flow

```
User opens "Handled Calendars"
    → CalendarsActivity.onResume()
    → CalendarProvider.getCalendars()
    → ContentResolver.query(CalendarContract.Calendars.CONTENT_URI)
    → Returns only locally-synced calendars (may be incomplete)
```

### Desired Flow

```
User opens "Handled Calendars"
    → Same query (may be incomplete)
    → User pulls to refresh OR taps refresh button
    → ContentResolver.requestSync() for calendar accounts
    → Short delay
    → Re-query calendars
    → Shows updated list with newly-synced calendars
```

## Affected Files

### Production Code
- `android/app/src/main/java/com/github/quarck/calnotify/prefs/CalendarsActivity.kt` - Main UI for calendar list
- `android/app/src/main/res/layout/content_calendars.xml` - Layout needs SwipeRefreshLayout
- `android/app/src/main/res/values/strings.xml` - New string resources
- `android/app/src/main/res/menu/menu_calendars.xml` - New menu resource
- `android/app/src/main/res/drawable/ic_refresh.xml` - Refresh icon (if not already present)
- Possibly `AndroidManifest.xml` if additional permissions needed

### Test Files (New)
- `android/app/src/test/java/com/github/quarck/calnotify/prefs/CalendarsActivityRobolectricTest.kt` - Robolectric UI tests

## Implementation Plan

### Phase 1: Add SwipeRefreshLayout to Calendar List

Update `content_calendars.xml` to wrap RecyclerView in SwipeRefreshLayout:

```xml
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout ...>

    <androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        android:id="@+id/swipe_refresh_calendars"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/list_calendars"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@color/cardview_light_background" />

    </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>

    <TextView
        android:id="@+id/no_calendars_text"
        ... />

</RelativeLayout>
```

### Phase 2: Add Toolbar Refresh Menu Item

Add menu resource `res/menu/menu_calendars.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    
    <item
        android:id="@+id/action_refresh_calendars"
        android:icon="@drawable/ic_refresh"
        android:title="@string/refresh_calendars"
        app:showAsAction="ifRoom" />
    
    <item
        android:id="@+id/action_calendar_sync_help"
        android:icon="@drawable/ic_help_outline"
        android:title="@string/calendar_sync_help"
        app:showAsAction="never" />
        
</menu>
```

### Phase 3: Update CalendarsActivity

Add sync request and refresh logic:

```kotlin
class CalendarsActivity : AppCompatActivity() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    // ... existing fields ...

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... existing code ...

        swipeRefreshLayout = findOrThrow<SwipeRefreshLayout>(R.id.swipe_refresh_calendars)
        swipeRefreshLayout.setOnRefreshListener {
            requestCalendarSyncAndRefresh()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_calendars, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh_calendars -> {
                requestCalendarSyncAndRefresh()
                true
            }
            R.id.action_calendar_sync_help -> {
                showCalendarSyncHelp()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun requestCalendarSyncAndRefresh() {
        swipeRefreshLayout.isRefreshing = true
        
        background {
            try {
                // Request sync for all calendar accounts
                val extras = Bundle().apply {
                    putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                }
                ContentResolver.requestSync(null, CalendarContract.AUTHORITY, extras)
                
                DevLog.info(LOG_TAG, "Requested calendar sync")
                
                // Wait a bit for sync to start/complete
                Thread.sleep(2000)
                
            } catch (ex: SecurityException) {
                DevLog.error(LOG_TAG, "SecurityException requesting sync: ${ex.message}")
            }
            
            // Refresh the calendar list
            loadCalendars()
            
            runOnUiThread {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun showCalendarSyncHelp() {
        AlertDialog.Builder(this)
            .setTitle(R.string.calendar_sync_help_title)
            .setMessage(R.string.calendar_sync_help_message)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.open_sync_settings) { _, _ ->
                openSyncSettings()
            }
            .show()
    }

    private fun openSyncSettings() {
        try {
            // Try to open account sync settings directly
            val intent = Intent(Settings.ACTION_SYNC_SETTINGS)
            startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            // Fall back to general settings
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun loadCalendars() {
        // Extract existing onResume logic into this method
        val calendars = CalendarProvider.getCalendars(this).toTypedArray()
        // ... rest of existing calendar loading code ...
    }

    override fun onResume() {
        super.onResume()
        background {
            loadCalendars()
        }
    }
}
```

### Phase 4: Add String Resources

Add to `strings.xml`:

```xml
<!-- Calendar sync refresh -->
<string name="refresh_calendars">Refresh calendars</string>
<string name="calendar_sync_help">Troubleshoot missing calendars</string>
<string name="calendar_sync_help_title">Missing calendars?</string>
<string name="calendar_sync_help_message">If some calendars aren\'t showing:\n\n1. Pull down to refresh this list\n\n2. Check that calendar sync is enabled in Android Settings → Accounts → Google → Calendar\n\n3. Open the Google Calendar app and verify your calendars appear there\n\n4. If calendars still don\'t appear, try restarting your device</string>
<string name="open_sync_settings">Open Sync Settings</string>
<string name="syncing_calendars">Syncing calendars…</string>
```

### Phase 5: Add Refresh Icon Drawable

Either use an existing icon or add `ic_refresh.xml` to drawable:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"/>
</vector>
```

## Permission Considerations

The `ContentResolver.requestSync(null, authority, extras)` call with `null` account should work with existing calendar permissions. However:

- On some devices/Android versions, this may be a no-op
- The sync is a *request*, not guaranteed to happen immediately
- If issues arise, we may need to enumerate accounts via `AccountManager` (requires `GET_ACCOUNTS` permission, deprecated on newer Android)

**Start without additional permissions** - the null-account requestSync should work.

## Testing Plan

### What's Testable vs What's Not

**Testable (automated):**
- UI logic: SwipeRefreshLayout triggers callback, menu item clicks
- That `ContentResolver.requestSync()` is called with correct parameters
- That calendar list reloads after the sync/refresh flow
- Help dialog content and "Open Settings" intent creation
- Error handling (SecurityException catch)

**NOT practically testable (requires manual verification):**
- That sync actually happens (system-level SyncManager behavior)
- That new calendars appear after sync (requires real Google account + network + time)
- Device-specific sync quirks

**Why sync is hard to test:**
The `ContentResolver.requestSync()` call is a *request* to the Android SyncManager - a system service we don't control. Even instrumentation tests run on a real device can't easily verify that:
1. The sync request was received by SyncManager
2. The sync adapter actually ran
3. New data was fetched from Google servers

This is similar to testing "did this notification actually show" - we can verify we called the right APIs, but the system behavior is opaque.

### Recommended Test Structure

Following existing patterns in the codebase (see `CalendarProviderBasicTest.kt`, `CalendarProviderBasicRobolectricTest.kt`):

#### 1. Extract Sync Logic into Testable Helper

Instead of putting all logic in the Activity, extract to a helper:

```kotlin
// CalendarSyncHelper.kt
object CalendarSyncHelper {
    private const val LOG_TAG = "CalendarSyncHelper"
    
    /**
     * Requests Android to sync calendar data.
     * @return true if request was made, false if failed
     */
    fun requestCalendarSync(): Boolean {
        return try {
            val extras = Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            ContentResolver.requestSync(null, CalendarContract.AUTHORITY, extras)
            DevLog.info(LOG_TAG, "Requested calendar sync")
            true
        } catch (ex: SecurityException) {
            DevLog.error(LOG_TAG, "SecurityException requesting sync: ${ex.message}")
            false
        }
    }
}
```

#### 2. Robolectric Tests (Unit-level)

File: `android/app/src/test/java/com/github/quarck/calnotify/prefs/CalendarsActivityRobolectricTest.kt`

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [28])
class CalendarsActivityRobolectricTest {
    
    @Test
    fun testSwipeRefreshLayoutIsConfigured() {
        val activity = Robolectric.buildActivity(CalendarsActivity::class.java)
            .create()
            .start()
            .resume()
            .get()
        
        val swipeRefresh = activity.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_calendars)
        assertNotNull("SwipeRefreshLayout should exist", swipeRefresh)
    }
    
    @Test
    fun testMenuInflation() {
        val activity = Robolectric.buildActivity(CalendarsActivity::class.java)
            .create()
            .start()
            .resume()
            .get()
        
        val menu = ShadowActivity.shadowOf(activity).optionsMenu
        assertNotNull("Menu should be inflated", menu)
        assertNotNull("Refresh item should exist", menu.findItem(R.id.action_refresh_calendars))
        assertNotNull("Help item should exist", menu.findItem(R.id.action_calendar_sync_help))
    }
    
    @Test
    fun testHelpDialogContent() {
        // Test that help dialog shows expected strings
        // Use Robolectric's ShadowAlertDialog to verify
    }
}
```

#### 3. Instrumentation Tests (Optional)

Skip for now - can't mock `ContentResolver.requestSync()` easily per `docs/dev_completed/constructor-mocking-android.md`. The actual sync behavior is system-level and untestable anyway.

### Manual Testing (Basic Smoke Test)

Quick verification after implementation:
- Pull-to-refresh shows spinner and reloads list
- Menu refresh button works
- Help dialog opens and "Open Settings" launches system settings
- No crash when calendar permissions missing

## Test Priority

1. **Robolectric UI tests** - Verify menu/swipe setup, no crashes, intent creation
2. **Instrumentation tests** - Optional, limited value (can't verify actual sync behavior)

The feature is mostly UI plumbing + one system API call. Robolectric can catch layout/menu issues.

## Future Enhancements

1. **Show sync status indicator** - Display if calendars are currently syncing
2. **Detect stale calendar data** - Warn if last sync was long ago
3. **Per-account refresh** - Let user request sync for specific accounts
4. **Automatic retry** - If fewer calendars than expected, auto-retry after delay

## Related Documentation

- [ContentResolver.requestSync()](https://developer.android.com/reference/android/content/ContentResolver#requestSync(android.accounts.Account,%20java.lang.String,%20android.os.Bundle))
- [CalendarContract.AUTHORITY](https://developer.android.com/reference/android/provider/CalendarContract#AUTHORITY)
- [Sync Adapters](https://developer.android.com/training/sync-adapters)

## Notes

- `requestSync()` is a hint to the system - it may not sync immediately
- On fresh device setup, Android may still be initializing account sync infrastructure
- The 2-second delay after requestSync is a heuristic; may need adjustment
- SwipeRefreshLayout provides familiar UX pattern users expect
- Help dialog provides fallback troubleshooting for edge cases
