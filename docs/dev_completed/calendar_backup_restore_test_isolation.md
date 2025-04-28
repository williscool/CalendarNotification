# Fixing Intermittent Failures in CalendarBackupRestoreTest

## Problem

`CalendarBackupRestoreTest` started failing intermittently, particularly when run "in concert" with other tests. The failures manifested as assertion errors where the test expected a specific calendar ID (e.g., `233`) but received a different one (e.g., `153`).

Initially, this was suspected to be related to global locale changes introduced by fixes in other tests (`OriginalEventDismissTest`). However, restoring the locale in `CalendarBackupRestoreTest` did not resolve the issue.

The root cause was identified as a **test isolation failure** due to shared, persistent state within the Android `CalendarContract.Provider`.

1.  **Reliance on Predictable IDs:** The test implicitly assumed that the calendars it created using `createTestCalendar` would receive predictable, sequential IDs.
2.  **State Leakage:** Other tests (or previous runs of this test if cleanup failed) were creating calendars in the shared Android Calendar Provider database on the emulator/device.
3.  **ID Drift:** Because calendars weren't consistently cleaned up between test runs or by other tests, the auto-incrementing IDs assigned by the Calendar Provider drifted. When `CalendarBackupRestoreTest` ran, it received higher-than-expected IDs (like 153 instead of 233).
4.  **Matching Failures:** The test's `findMatchingCalendarId` calls, while using the correct logic, might have inadvertently matched older, leftover calendars if the test data (display name, account name, owner account) wasn't unique enough, leading to assertion failures when comparing the *expected* ID (from `createTestCalendar`) with the *matched* ID.

## Solution

The fix involved making the test robust against calendar ID drift and ensuring it always operates on uniquely identifiable data:

1.  **Generate Unique Identifiers:** A unique suffix (derived from `UUID.randomUUID()`) is generated in the `@Before` setup method for each test run.
2.  **Apply Unique Suffix:** This suffix is appended to the `displayName`, `accountName`, and `ownerAccount` used when calling `createTestCalendar`.
3.  **Update Assertions:** The tests (`testFindMatchingCalendarId_ExactMatch`, `testRestoreEvent_WithMatchingCalendar`, etc.) were updated to use these unique names/accounts when creating the `CalendarBackupInfo` object used for matching.

This ensures that `CalendarProvider.findMatchingCalendarId` will always find the specific calendars created *during the current test run*, regardless of any pre-existing state in the Android Calendar Provider. The test already correctly used the IDs returned by `createTestCalendar` in its assertions, so no changes were needed there.

This approach guarantees test isolation concerning the calendar data, preventing failures caused by ID drift or accidental matching of unrelated calendars.
