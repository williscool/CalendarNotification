# Settings Import/Export Feature

## Status: Planning

## Overview

Implement manual settings import/export functionality to allow users to:
- Backup their settings before uninstalling
- Transfer settings between stable and debug releases
- Share settings between devices
- Restore after reinstallation

**GitHub Issues:** 
- [#29 - Import/Export settings](https://github.com/williscool/CalendarNotification/issues/29)
- [#159 - Car Mode crash on Android 12+](https://github.com/williscool/CalendarNotification/issues/159) (bundled fix)

## Research Notes (Jan 2026)

We researched current Android best practices for settings backup. Key findings:

**Android's Official Stance:** Google heavily promotes Auto Backup as the primary solution, with most documentation focused on cloud sync. However, Auto Backup doesn't solve our use case:
- Different signing keys between stable↔debug releases means they're treated as different apps
- Users have no manual control over when/where backups happen
- Can't easily transfer settings to a new device without Google account sync

**Format Decision: JSON**
- Google's docs note "JSON is often preferred for its simplicity and human-readable format" ([developer.android.com](https://developer.android.com/identity/data/autobackup))
- No Android-specific format exists for manual settings export
- Other apps with manual backup (Buzzkill, etc.) use JSON, though most are closed-source

**Security Consideration from Google:**
> "Apps that provide an 'export' or 'backup' feature creating copies of data in directories accessible by other apps can lead to data leaks"
> — [developer.android.com](https://developer.android.com/privacy-and-security/risks/backup-best-practices)

This confirms our plan to use **Storage Access Framework (SAF)** - user controls file location, not sitting in shared storage.

**Serialization Library: kotlinx.serialization**
- Official Kotlin library (JetBrains)
- No reflection (good for Android performance/size)
- Works well with data classes
- Modern, actively maintained

## Current Settings Architecture

### Storage Locations

The app uses SharedPreferences with multiple named preference files:

| Storage Class | Pref File Name | Type | Should Backup? |
|---------------|----------------|------|----------------|
| `Settings` | default (`com.github.quarck.calnotify_preferences.xml`) | User settings | ✅ Yes |
| `PersistentState` | `persistent_state` | Runtime state | ❌ No |
| `ReminderState` | `reminder_state` | Runtime state | ❌ No |
| `CalendarMonitorState` | `cal_monitor` | Runtime state | ❌ No |
| `BTCarModeStorage` | `car_mode_state` | User config | ✅ Yes |
| `CalendarChangePersistentState` | (deprecated) | N/A | ❌ No |

### Settings Categories to Export

**User Preferences (Settings.kt):**
- Theme mode
- Snooze presets
- Notification settings (vibration, LED, sounds, heads-up)
- Reminder settings (interval, max reminders)
- Event filtering (declined, cancelled, all-day)
- Per-calendar enabled/disabled flags (`calendar_handled_.<id>`)
- First day of week
- Developer mode
- All behavior toggles

**Car Mode (BTCarModeStorage):**
- Car mode trigger devices (Bluetooth MACs)

### What NOT to Export

Runtime state (timestamps, counters) that would be invalid after import:
- `notificationLastFireTime`
- `nextSnoozeAlarmExpectedAt`
- `lastCustomSnoozeIntervalMillis`
- `reminderLastFireTime`
- `numRemindersFired`
- `nextFireExpectedAt`
- `currentReminderPatternIndex`
- `nextEventFireFromScan`/`prevEventFireFromScan`

## Proposed Implementation

### Phase 1: Core Export/Import Logic

**New Files:**
```
android/app/src/main/java/com/github/quarck/calnotify/backup/
├── SettingsBackupManager.kt     # Main export/import logic
├── BackupData.kt                # Data class for backup structure
└── BackupVersion.kt             # Version handling for future compatibility
```

**Backup Format:** JSON

```json
{
  "version": 1,
  "exportedAt": 1704672000000,
  "appVersionCode": 123,
  "settings": {
    "theme_mode": -1,
    "pref_snooze_presets": "15m, 1h, 4h, 1d, -5m",
    "vibra_on": true,
    "calendar_handled_.1": true,
    "calendar_handled_.2": false
    // ... all user settings
  },
  "carMode": {
    "A": "AA:BB:CC:DD:EE:FF,11:22:33:44:55:66"
  }
}
```

**Why JSON:**
- Researched best practices (see above) - no Android-specific format for manual export
- Easier to parse/generate in Kotlin with kotlinx.serialization
- Human-readable for debugging
- Smaller file size than XML

### Phase 2: Storage Access Framework Integration

Use Android's Storage Access Framework (SAF) for file access:
- **No runtime permissions needed!** SAF grants access only to user-selected files
- User chooses save location via system file picker
- Works with cloud storage (Google Drive, Dropbox)
- No need to request WRITE_EXTERNAL_STORAGE or READ_EXTERNAL_STORAGE

```kotlin
// Export
val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = "application/json"
    putExtra(Intent.EXTRA_TITLE, "cnplus_settings_${dateStamp}.json")
}
startActivityForResult(intent, REQUEST_CODE_EXPORT)

// Import
val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = "application/json"
}
startActivityForResult(intent, REQUEST_CODE_IMPORT)
```

### Phase 3: UI Integration

Add to existing Settings screen (`SettingsActivityX`):

**Option 1:** New preference category in `misc_preferences.xml`:
```xml
<PreferenceCategory android:title="@string/backup_category">
    <Preference
        android:key="export_settings"
        android:title="@string/export_settings"
        android:summary="@string/export_settings_summary" />
    <Preference
        android:key="import_settings"
        android:title="@string/import_settings"
        android:summary="@string/import_settings_summary" />
</PreferenceCategory>
```

**Option 2:** Menu items in Settings activity toolbar

**Recommendation:** Option 1 - keeps it discoverable and consistent with other settings

### Phase 4: Calendar ID Mapping (Complex)

**Problem:** Calendar IDs are device-specific. If user exports on device A and imports on device B:
- Calendar ID `5` on device A might be `12` on device B
- Per-calendar settings would apply to wrong calendars

**Solutions:**

1. **Simple approach (MVP):** Export calendar settings by calendar *name* + *account*
   ```json
   "calendar_settings": [
     {"account": "user@gmail.com", "name": "Work", "enabled": true},
     {"account": "user@gmail.com", "name": "Personal", "enabled": false}
   ]
   ```
   On import: match by account+name, skip unmatched calendars

2. **Ignore per-calendar settings:** Only export global settings
   - Simpler but loses user's calendar selection
   - User note in UI: "Calendar selection not included"

**Recommendation:** Start with Solution 1 (match by name+account)

## Permission Strategy

**Goal:** No upfront permission requests at app start. Only ask when user accesses the specific feature.

| Feature | Permission Needed | When to Request |
|---------|-------------------|-----------------|
| Settings Export/Import | **None!** | N/A - SAF handles file access |
| Car Mode Settings | `BLUETOOTH_CONNECT` (Android 12+) | When user opens Car Mode page |

**Why this works:**

1. **Backup/Export:** Storage Access Framework (SAF) doesn't require runtime permissions. The system file picker grants scoped access to just the file the user selects. No `WRITE_EXTERNAL_STORAGE` or `READ_EXTERNAL_STORAGE` needed.

2. **Car Mode:** Permission requested in `CarModeActivity.onResume()` - only when user navigates to that settings page. If denied, show message explaining why it's needed.

---

## Bundled Fix: Car Mode Bluetooth Crash

**Issue:** [#159](https://github.com/williscool/CalendarNotification/issues/159)  
**Details:** See `car_mode_bluetooth_crash.md`

Since we're working in the settings code and backing up `BTCarModeStorage`, we'll fix the Android 12+ Bluetooth permission crash at the same time:

- [ ] Add `BLUETOOTH_CONNECT` permission to AndroidManifest.xml (manifest only, not runtime request at startup)
- [ ] Add permission check methods to `PermissionsManager.kt`
- [ ] Update `CarModeActivity.kt` to request permission when page opens (just-in-time)
- [ ] Add defensive try-catch in `BTDeviceManager.kt`
- [ ] Add `bluetooth_permission_required` string resource

This is low additional effort (~1-2 hours) since we're already in this code.

---

## Implementation Tasks

### MVP (Minimum Viable)

- [ ] Add kotlinx.serialization dependency to build.gradle
- [ ] Create `SettingsBackupManager` with export/import methods
- [ ] Define `BackupData` data class with `@Serializable` annotation
- [ ] Add SAF intents in SettingsActivityX
- [ ] Add UI preferences for export/import buttons
- [ ] Add string resources for UI text
- [ ] Handle import errors gracefully (show toast, don't crash)
- [ ] Add confirmation dialog before import (overwrites existing)

### Enhanced

- [ ] Calendar ID mapping by name+account
- [ ] Version migration support for future schema changes
- [ ] Progress indicator for large imports
- [ ] Backup file validation (checksum or schema validation)

### Testing

- [ ] Unit tests for `SettingsBackupManager`
- [ ] Export → import round-trip test
- [ ] Version migration tests
- [ ] Invalid/corrupted file handling tests
- [ ] Instrumentation test for SAF integration

## Effort Estimate

| Phase | Effort | Priority |
|-------|--------|----------|
| Phase 1: Core logic | 2-4 hours | High |
| Phase 2: SAF integration | 1-2 hours | High |
| Phase 3: UI | 1-2 hours | High |
| Phase 4: Calendar mapping | 2-4 hours | Medium |
| Bluetooth crash fix (#159) | 1-2 hours | High |
| Testing | 4-8 hours | High |
| **Total** | **12-22 hours** | |

## Existing Android Auto Backup

The app already has Android Auto Backup enabled:

```xml
<!-- AndroidManifest.xml -->
android:allowBackup="true"
android:fullBackupContent="@xml/backup_rules"
```

```xml
<!-- backup_rules.xml -->
<full-backup-content>
    <include domain="database" path="." />
    <include domain="sharedpref" path="com.github.quarck.calnotify_preferences.xml" />
</full-backup-content>
```

**Note:** This only backs up the main preferences file, not:
- `car_mode_state.xml` (BTCarModeStorage)
- Database backups happen but may not restore correctly across major versions

**Recommendation:** Update `backup_rules.xml` to include car mode:
```xml
<full-backup-content>
    <include domain="database" path="." />
    <include domain="sharedpref" path="com.github.quarck.calnotify_preferences.xml" />
    <include domain="sharedpref" path="car_mode_state.xml" />
</full-backup-content>
```

However, Auto Backup is not user-controllable and doesn't work across release variants (stable↔debug), so manual export remains valuable.

## Security Considerations

- Exported files contain no sensitive data (no passwords, tokens)
- Calendar IDs and names are visible in export (low sensitivity)
- Bluetooth MAC addresses for car mode are included
- SAF ensures user explicitly chooses file location

## Open Questions

1. Should we include dismissed event history in backups?
   - Pro: Full restore experience
   - Con: Large file size, privacy concerns
   - **Recommendation:** No - just settings, not event data

2. Should export include active events/snoozes?
   - Pro: Full state restore
   - Con: Events may be stale after import
   - **Recommendation:** No - events are time-sensitive

3. File extension: `.json` vs `.cnplus` vs `.cnpbackup`?
   - `.json` is universal, easy to inspect
   - Custom extension could enable "open with" feature
   - **Recommendation:** `.json` for MVP, consider custom later

## References

- [Android Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider)
- [Auto Backup for Apps](https://developer.android.com/guide/topics/data/autobackup)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)

