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

- `android/app/src/main/java/com/github/quarck/calnotify/prefs/CalendarsActivity.kt` - Main UI for calendar list
- `android/app/src/main/res/layout/content_calendars.xml` - Layout needs SwipeRefreshLayout
- `android/app/src/main/res/values/strings.xml` - New string resources
- Possibly `AndroidManifest.xml` if additional permissions needed

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

### Unit Tests (Robolectric)

1. **CalendarsActivity refresh tests:**
   - Test swipe-to-refresh triggers sync request
   - Test toolbar refresh button triggers sync request
   - Test help dialog shows correct content

### Instrumentation Tests

1. **Sync request integration:**
   - Test `ContentResolver.requestSync()` is called with correct parameters
   - Test calendar list reloads after sync request

### Manual Testing

1. Fresh install on device with multiple Google calendars
2. Open "Handled Calendars" immediately after install
3. Note how many calendars appear
4. Pull to refresh
5. Verify sync is requested (may see brief sync indicator in status bar)
6. After refresh completes, check if missing calendars now appear
7. Test the help dialog and "Open Sync Settings" button
8. Test on various Android versions (10, 11, 12, 13, 14, 15)

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
