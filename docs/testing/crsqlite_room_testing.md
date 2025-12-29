# cr-sqlite with Room Testing

This document covers the setup required to use cr-sqlite in Android instrumentation tests with Room.

## Prerequisites

Before cr-sqlite can be tested in instrumentation tests, several APK packaging configurations are required.

### Required Manifest Configuration

In `AndroidManifest.xml`, native libraries must be extracted to the filesystem:

```xml
<application
    android:extractNativeLibs="true"
    ...>
```

Without this, the native library directory will be **empty** at runtime, and `dlopen` will fail.

### Required Gradle Configuration

In `app/build.gradle`, legacy packaging must be enabled:

```groovy
packagingOptions {
    jniLibs {
        useLegacyPackaging = true
    }
    pickFirst '**/*.so'
}
```

- `useLegacyPackaging = true` is required when `extractNativeLibs="true"`
- `pickFirst '**/*.so'` resolves conflicts when multiple libraries have the same name

## Library Naming: Why `crsqlite_requery.so`

The project has **two** cr-sqlite builds:

1. **React Native's version** (`crsqlite.so`) - bundled via op-sqlite/PowerSync for the RN side
2. **Our custom build** (`crsqlite_requery.so`) - built specifically for requery's SQLite version

These are **not interchangeable**. The React Native version is built against op-sqlite's embedded SQLite, while our custom version is built for requery's SQLite 3.45.0.

To avoid Gradle's `pickFirst` choosing the wrong one, we renamed our build to `crsqlite_requery.so`.

## Loading cr-sqlite with Requery

### ✅ Correct: Use `SQLiteCustomExtension`

```kotlin
override fun createConfiguration(
    path: String?,
    @OpenFlags openFlags: Int
): SQLiteDatabaseConfiguration {
    val config = super.createConfiguration(path, openFlags)
    config.customExtensions.add(
        SQLiteCustomExtension("crsqlite_requery", "sqlite3_crsqlite_init")
    )
    return config
}
```

This uses requery's internal `nativeLoadExtension()` which works correctly.

### ❌ Wrong: Do NOT use `load_extension()` SQL

```kotlin
// DON'T DO THIS - fails with "error during initialization"
db.execSQL("SELECT load_extension('crsqlite_requery', 'sqlite3_crsqlite_init');")
```

Even though the library exists and is accessible, calling `load_extension()` from SQL fails with a generic "error during initialization" error. The root cause is unclear, but requery's native loading mechanism works while the SQL function does not.

## Verifying cr-sqlite is Loaded

After the database is opened, verify cr-sqlite functions are available:

```kotlin
db.rawQuery("SELECT crsql_db_version()", null).use { cursor ->
    if (cursor.moveToFirst()) {
        Log.d(TAG, "cr-sqlite loaded! db_version = ${cursor.getString(0)}")
    }
}
```

Note: Use `crsql_db_version()`, not `crsql_version()` - the latter doesn't exist in this build.

## The `CrSqliteSupportHelper` Bridge

`CrSqliteSupportHelper` bridges Room's `SupportSQLiteOpenHelper` interface with requery's implementation:

```
Room → SupportSQLiteOpenHelper → CrSqliteSupportHelper → Requery SQLiteOpenHelper → cr-sqlite
```

Key features:
- Configures cr-sqlite via `SQLiteCustomExtension` in `createConfiguration()`
- Calls `crsql_finalize()` before closing (required by cr-sqlite)
- Exposes `underlyingDatabase` for direct cr-sqlite function access in tests

## Troubleshooting

### "no such function: crsql_db_version"

The cr-sqlite extension isn't loading. Check:
1. `extractNativeLibs="true"` in AndroidManifest.xml
2. `useLegacyPackaging = true` in build.gradle
3. The `.so` file exists in `jniLibs/<arch>/`
4. Clean build: `./gradlew clean`

### "error during initialization" with `load_extension()`

Don't use SQL `load_extension()`. Use `SQLiteCustomExtension` instead (see above).

### "dlopen failed: library not found"

The library isn't being extracted. Check `extractNativeLibs` and `useLegacyPackaging` settings.

### Native library directory is empty

Same as above - the APK packaging isn't extracting native libraries.

### Tests pass but use wrong cr-sqlite version

If you see unexpected behavior, verify the correct `.so` is packaged:

```bash
unzip -l app/build/outputs/apk/*/debug/*.apk | grep crsqlite
```

You should see `crsqlite_requery.so` in the lib directories.

