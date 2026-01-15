# Calendar Notifications Plus Documentation

## Architecture & Core Concepts

- [Architecture Overview](architecture/architecture_overview.md) - GlobalState and ApplicationController pattern
- [Calendar Monitoring](architecture/calendar_monitoring.md) - How event detection works (detailed flows)
- [Clock Implementation](architecture/clock_implementation.md) - CNPlusClockInterface for testable time
- [Domain Model](architecture/domain_model.md) - Storage classes, Room databases, SharedPreferences
- [Notification Architecture](architecture/notification_architecture.md) - Channels, sound logic, muting system
- [Next Alert Indicator](architecture/next_alert_architecture.md) - 📅/🔔 next notification display

## Build & Development

- [Build Instructions](build/BUILD.md) - Setting up and building the app
- [CR-SQLite Build](build/CR_SQLITE_BUILD.md) - Building cr-sqlite native library
- [Debug Guide](build/DEBUG.md) - Debugging tips (Chrome/Hermes limitations)
- [WSL Log Cleaning](build/wsl_test_log_clean.md) - Log cleaning scripts for sharing

## Data & Sync

- [Data Sync Setup](DATA_SYNC_README.md) - Supabase/PowerSync configuration

## Features

User-facing feature documentation:

- [Upcoming Events View](features/UPCOMING_EVENTS_README.md) - See events before reminders fire (new tabbed UI)

## Testing Infrastructure

- [Dependency Injection Patterns](testing/dependency_injection_patterns.md) - Manual DI for testability ⭐ *Key Reference*
- [Test Development Chronology](testing/test_development_chronology.md) - History and reading order
- [Test Fixture Refactoring](testing/test_fixture_refactoring.md) - Modular test infrastructure
- [Test Reporting](testing/TEST_REPORTING.md) - CI test reporting with JaCoCo
- [Test Sharding](testing/test_sharding.md) - Parallel test execution
- [Async Task Idling Resource](testing/async_task_idling_resource.md) - Espresso synchronization
- [CR-SQLite + Room Testing](testing/crsqlite_room_testing.md) - APK packaging & extension loading ⭐ *New*

## Completed RFCs (`dev_completed/`)

Historical decisions and completed work, kept for reference:

- [Constructor Mocking Limitations](dev_completed/constructor-mocking-android.md) ⭐ *Key Reference*
- [Calendar Backup/Restore Test Isolation](dev_completed/calendar_backup_restore_test_isolation.md)
- [Calendar Sync Refresh](dev_completed/calendar_sync_refresh.md) - Pull-to-refresh in Handled Calendars ✅
- [Car Mode Bluetooth Crash](dev_completed/car_mode_bluetooth_crash.md) - Android 12+ BLUETOOTH_CONNECT permission ✅
- [Database Modernization Plan](dev_completed/database_modernization_plan.md) - Room migration complete ✅
- [Room Database Migration](dev_completed/room_database_migration.md) - Implementation details & patterns
- [Event Dismissal Testing Notes](dev_completed/event_dismissal_testing_notes.md)
- [Expo Router Migration Decision](dev_completed/expo_router_migration.md) - Evaluated & intentionally deferred
- [Forward to Calendar Search](dev_completed/forward_to_calendar_search.md) - Fallback when event not found ([#66](https://github.com/williscool/CalendarNotification/issues/66)) ✅
- [Intermittent Build Issue Fix](dev_completed/intermittent_build_issue_resources_npe.md)
- [Mock Calendar Provider Refactor](dev_completed/mock_calendar_provider_refactor.md)
- [Settings Import/Export](dev_completed/settings_backup.md) - Manual settings backup feature ✅
- [SQLite Mocking in Robolectric](dev_completed/sqlite-mocking-robolectric.md)
- [Sync Database Mismatch Fix](dev_completed/sync_database_mismatch.md) - RN sync + Room DB alignment ✅
- [Test Exception Fallback](dev_completed/test_exception_fallback.md) - XML results file behavior on crash
- [Unhandled Calendar Event Count](dev_completed/unhandled_calendar_event_count.md) - Show upcoming events for unchecked calendars ✅

## TODO/In Progress (`dev_todo/`)

Features and changes under consideration:

- [Deprecated Features Removal](dev_todo/deprecated_features.md) - QuietHours, CalendarEditor
- [Android Modernization](dev_todo/android_modernization.md) - Coroutines, Hilt DI opportunities
- [Raise Min SDK](dev_todo/raise_min_sdk.md) - API 24 → 26+ considerations
- [Event Deletion Issues](dev_todo/event_deletion_issues.md) - Cleanup mechanisms
- [Event Restore Behavior](dev_todo/event_restore_behavior.md) - Restoration logic
- [Reschedule Confirmation Handling](dev_todo/reschedule_confirmation_handling.md) - UI improvements
- [Dismissed Events Long Storage](dev_todo/dismissed_events_long_storage.md) - Configurable bin retention period

