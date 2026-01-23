# Google Sign-In + Google Drive Backup Research

## Executive Summary

This document researches what it would take to implement Google Sign-In authentication with Google Drive backup as a simpler alternative to the current Supabase/PowerSync cloud sync setup. The goal is to provide a more accessible backup solution for less technical users.

## Current Backup System Overview

### 1. Settings/Manual Backup (Local)
The app has `SettingsBackupManager` that exports/imports JSON files via Android's Storage Access Framework:
- Backs up: SharedPreferences settings, car mode settings, calendar enabled/disabled preferences
- User manually exports to/imports from a local file
- No cloud sync, no account needed

### 2. Supabase/PowerSync Cloud Sync (Technical)
The current cloud sync requires users to:
1. Create a Supabase account and project
2. Run database migrations
3. Create a PowerSync account and instance
4. Configure HS256 JWT authentication
5. Enter 4 separate credentials in the app

**This is powerful but complex** - great for developers, challenging for average users.

### Data to Back Up
Based on the codebase analysis:

| Data | Storage | Table/Format |
|------|---------|--------------|
| Active notifications | Room DB | `eventsV9` (EventAlertEntity) |
| Dismissed events | Room DB | `dismissed_events` (DismissedEventEntity) |
| Settings | SharedPreferences | JSON exportable |
| Calendar selections | SharedPreferences | Per-calendar enabled flags |

---

## Google Drive Backup Approach

### Why Google Drive?

1. **Zero Setup** - 95%+ of Android users already have a Google account
2. **Familiar** - Users understand "Sign in with Google"
3. **Automatic** - Can sync in background without user intervention
4. **Cross-device** - Works across devices with same Google account
5. **Free Tier** - Google Drive provides 15GB free storage

### Required Google APIs

#### 1. Google Sign-In (Identity)
- **Library**: `com.google.android.gms:play-services-auth`
- **Purpose**: Authenticate user, get OAuth tokens
- **Permissions**: Basic profile (email, name)

#### 2. Google Drive API
- **Library**: `com.google.api-client:google-api-client-android` + `com.google.apis:google-api-services-drive`
- **Purpose**: Store/retrieve backup files
- **Scope**: `https://www.googleapis.com/auth/drive.appdata` (App-specific hidden folder)

### The "App Data" Folder Approach

Google Drive provides a special hidden folder for app data that:
- Is **invisible to users** (won't clutter their Drive)
- Is **private to your app** (other apps can't access)
- Is **automatically deleted** when app is uninstalled
- Requires minimal permissions (no access to user's files)

This is the **recommended approach** for app backup/sync.

---

## Implementation Requirements

### 1. Google Cloud Console Setup (One-time by Developer)

```
1. Create Google Cloud Project
2. Enable Google Drive API
3. Create OAuth 2.0 Client ID (Android type)
4. Configure OAuth consent screen
5. Add SHA-1 fingerprint for release builds
```

**Cost**: Free (within quota limits - 1 billion API calls/day)

### 2. Android Dependencies

```gradle
// In android/app/build.gradle
dependencies {
    // Google Sign-In
    implementation 'com.google.android.gms:play-services-auth:21.3.0'
    
    // Google Drive API (REST)
    implementation 'com.google.api-client:google-api-client-android:2.7.0'
    implementation 'com.google.apis:google-api-services-drive:v3-rev20250115-2.0.0'
    
    // OR use the simpler Google Drive API for Android (deprecated but simpler)
    // implementation 'com.google.android.gms:play-services-drive:17.0.0'  // Deprecated
}
```

### 3. Required Code Components

#### A. GoogleSignInManager.kt
```kotlin
class GoogleSignInManager(private val context: Context) {
    
    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()
    
    fun getSignInClient(): GoogleSignInClient {
        return GoogleSignIn.getClient(context, signInOptions)
    }
    
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
    
    fun isSignedIn(): Boolean {
        return getLastSignedInAccount() != null
    }
}
```

#### B. GoogleDriveBackupService.kt
```kotlin
class GoogleDriveBackupService(
    private val context: Context,
    private val account: GoogleSignInAccount
) {
    private val driveService: Drive by lazy {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, 
            listOf(DriveScopes.DRIVE_APPDATA)
        ).apply {
            selectedAccount = account.account
        }
        
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
        .setApplicationName("Calendar Notifications Plus")
        .build()
    }
    
    suspend fun uploadBackup(backupData: BackupData): Result<String> {
        // Serialize to JSON
        val json = Json.encodeToString(backupData)
        
        // Create file metadata
        val fileMetadata = File().apply {
            name = "cnplus_backup.json"
            parents = listOf("appDataFolder")
        }
        
        // Upload
        val mediaContent = ByteArrayContent.fromString("application/json", json)
        val file = driveService.files().create(fileMetadata, mediaContent)
            .setFields("id")
            .execute()
            
        return Result.success(file.id)
    }
    
    suspend fun downloadBackup(): Result<BackupData?> {
        // Find existing backup
        val files = driveService.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = 'cnplus_backup.json'")
            .execute()
            
        if (files.files.isEmpty()) {
            return Result.success(null)
        }
        
        // Download latest
        val fileId = files.files.first().id
        val outputStream = ByteArrayOutputStream()
        driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        
        val json = outputStream.toString(Charsets.UTF_8.name())
        val backupData = Json.decodeFromString<BackupData>(json)
        
        return Result.success(backupData)
    }
}
```

#### C. Backup Data Structure Extension

The existing `BackupData` class would need to be extended:

```kotlin
@Serializable
data class CloudBackupData(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val deviceId: String,
    val appVersionCode: Long,
    val appVersionName: String,
    
    // Existing settings backup
    val settings: Map<String, JsonElement>,
    val carModeSettings: Map<String, JsonElement>,
    val calendarSettings: List<CalendarSettingBackup>,
    
    // NEW: Event data for cloud backup
    val activeEvents: List<EventAlertRecord>? = null,
    val dismissedEvents: List<DismissedEventAlertRecord>? = null
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
```

### 4. UI Flow

```
┌─────────────────────────────────────────────────┐
│         Cloud Backup Settings                    │
├─────────────────────────────────────────────────┤
│                                                  │
│  ┌─────────────────────────────────────────┐    │
│  │  [G] Sign in with Google                │    │
│  └─────────────────────────────────────────┘    │
│                                                  │
│  OR (if signed in):                             │
│                                                  │
│  Signed in as: user@gmail.com                   │
│  Last backup: Jan 15, 2026 3:45 PM              │
│                                                  │
│  [✓] Auto-backup enabled                        │
│                                                  │
│  ┌────────────────┐  ┌────────────────┐         │
│  │  Backup Now    │  │   Restore      │         │
│  └────────────────┘  └────────────────┘         │
│                                                  │
│  ┌─────────────────────────────────────────┐    │
│  │  Sign Out                               │    │
│  └─────────────────────────────────────────┘    │
│                                                  │
└─────────────────────────────────────────────────┘
```

---

## Comparison: Google Drive vs. Supabase/PowerSync

| Feature | Google Drive | Supabase/PowerSync |
|---------|--------------|-------------------|
| **Setup Complexity** | 1 click (sign in) | 15+ steps |
| **User Knowledge Required** | None | Database/API knowledge |
| **Cost to User** | Free | Free (Supabase free tier) |
| **Sync Type** | Backup/Restore | Real-time bidirectional |
| **Cross-device Sync** | Manual restore | Automatic |
| **Data Location** | User's Google Drive | Developer's Supabase |
| **Privacy** | User controls data | Developer hosts data |
| **Conflict Resolution** | Last-write-wins | CRDTs possible |
| **Offline Support** | Full (local-first) | Full (PowerSync) |

### When to Use Which?

**Google Drive Backup** is ideal for:
- Casual users who just want their data backed up
- Users switching phones
- Users who don't want to set up external services
- Simple "set and forget" backup

**Supabase/PowerSync** is ideal for:
- Power users with multiple devices needing real-time sync
- Developers who want full control
- Users who want to self-host their data
- Advanced sync scenarios (reschedule confirmations across devices)

---

## Implementation Phases

### Phase 1: Basic Google Sign-In + Backup
**Estimated complexity: Medium**

1. Add Google Sign-In dependencies
2. Create `GoogleSignInManager` 
3. Add sign-in UI (single button)
4. Implement manual backup to Google Drive
5. Implement manual restore from Google Drive

### Phase 2: Automatic Backup
**Estimated complexity: Low (after Phase 1)**

1. Add WorkManager periodic backup job
2. Backup on significant events (app close, data changes)
3. Background sync using WorkManager constraints (WiFi, charging)

### Phase 3: Advanced Features (Optional)
**Estimated complexity: Medium-High**

1. Backup history (keep last N backups)
2. Backup preview before restore
3. Selective restore (settings only, events only)
4. Encryption of backup data
5. React Native integration for UI

---

## Technical Considerations

### 1. Play Services Dependency
- Requires Google Play Services (not available on some devices like Huawei)
- Should gracefully handle missing Play Services

### 2. OAuth Consent Screen
- Needs verification if requesting sensitive scopes (Drive app data is NOT sensitive)
- App data folder scope doesn't require verification

### 3. Backup Size
- Events table is relatively small (likely < 1MB)
- Settings are tiny (< 100KB)
- Well within Drive quotas

### 4. Network Handling
- Must handle offline gracefully
- Retry logic for transient failures
- WorkManager handles constraints well

### 5. Migration from Existing Users
- Should work alongside existing settings backup
- Optional - users can continue using manual JSON export

### 6. Data Privacy
- Backup stored in user's own Google Drive
- App never sees user's other Drive files
- Clear privacy policy needed

---

## Alternative Approaches Considered

### 1. Firebase Realtime Database / Firestore
- **Pro**: Real-time sync, similar to PowerSync
- **Con**: Requires Firebase setup, developer-hosted
- **Verdict**: More complex, less user-owned data

### 2. iCloud (iOS only)
- **Pro**: Native to Apple ecosystem
- **Con**: Android app, not applicable
- **Verdict**: N/A

### 3. Dropbox API
- **Pro**: Similar to Google Drive approach
- **Con**: Fewer users have Dropbox accounts
- **Verdict**: Could be added as alternative later

### 4. Android Backup Service (Auto Backup)
- **Pro**: Zero code, automatic
- **Con**: Limited to 25MB, no control, SharedPreferences only
- **Verdict**: Already enabled for settings, not suitable for events DB

---

## Recommended Next Steps

1. **Validate approach**: Review this research and confirm Google Drive is the preferred direction

2. **Google Cloud Console setup**: Create project, enable APIs, get OAuth client ID

3. **Prototype Phase 1**: Implement basic sign-in + manual backup/restore

4. **Test on multiple devices**: Verify backup/restore works across devices

5. **Add automatic backup**: Implement WorkManager-based periodic backup

6. **UI polish**: Integrate with existing settings screens

---

## References

- [Google Sign-In for Android](https://developers.google.com/identity/sign-in/android)
- [Google Drive API v3](https://developers.google.com/drive/api/v3/about-sdk)
- [Drive App Data Folder](https://developers.google.com/drive/api/v3/appdata)
- [Android WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- [OAuth 2.0 Scopes for Google APIs](https://developers.google.com/identity/protocols/oauth2/scopes#drive)

---

## Appendix: Sample OAuth Consent Screen Text

**App Name**: Calendar Notifications Plus

**Scopes Requested**: 
- Google Drive App Data (manage app-specific data in your Google Drive)

**Why we need this**:
Calendar Notifications Plus uses a private folder in your Google Drive to back up your notification settings and event history. This data is:
- Stored in your own Google Drive
- Only accessible by Calendar Notifications Plus
- Not visible in your Drive file list
- Automatically removed if you uninstall the app

We never access any of your other Google Drive files.
