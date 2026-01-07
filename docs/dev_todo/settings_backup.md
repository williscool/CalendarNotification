# Settings Import/Export Feature

## Status: Planning

## Overview

Implement manual settings import/export functionality to allow users to:
- Backup their settings before uninstalling
- Transfer settings between stable and debug releases
- Share settings between devices
- Restore after reinstallation

**GitHub Issue:** [#29 - Import/Export settings](https://github.com/williscool/CalendarNotification/issues/29)

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

**Why JSON over XML:**
- Easier to parse/generate in Kotlin
- Human-readable for debugging
- Standard serialization libraries available
- Smaller file size

### Phase 2: Storage Access Framework Integration

Use Android's Storage Access Framework (SAF) for file access:
- No special permissions needed
- User chooses save location
- Works with cloud storage (Google Drive, Dropbox)

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

## Implementation Tasks

### MVP (Minimum Viable)

- [ ] Create `SettingsBackupManager` with export/import methods
- [ ] Define `BackupData` data class with version field
- [ ] Implement JSON serialization (use kotlinx.serialization or Gson)
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
| Testing | 4-8 hours | High |
| **Total** | **10-20 hours** | |

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

