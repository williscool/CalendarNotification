<!-- 01f42113-b6cb-4093-a74c-e9b9720b7bce 0b6df41c-ac56-4fa6-9fd2-1b1be58ee17f -->
# PowerSync Upload Retry and Debug View Implementation

## Summary

Enhance `uploadData` with robust retry logic (exponential backoff) for transient errors, store persistently-failing operations, and add a new "Sync Debug" screen to view PowerSync logs and failed operations.

## Key Files to Modify/Create

- [lib/powersync/Connector.ts](lib/powersync/Connector.ts) - Add retry logic and failed operation tracking
- [lib/hooks/SyncDebugContext.tsx](lib/hooks/SyncDebugContext.tsx) - New context for capturing sync logs
- [App/SyncDebug.tsx](App/SyncDebug.tsx) - New debug view screen
- [App/index.tsx](App/index.tsx) - Add SyncDebug route and wrap with context
- [App/Settings.tsx](App/Settings.tsx) - Add link to Sync Debug screen

## Implementation Details

### 1. Enhanced Connector.ts

**Retry Logic:**

- Exponential backoff (1s, 2s, 4s) with jitter for transient errors (network, 5xx)
- Max 3 retries per operation before throwing (lets PowerSync's built-in retry handle at transaction level)
- Fatal errors (22xxx, 23xxx, 42501) skip retries as they can't be recovered

**Failed Operations Storage:**

- Store failed operations in AsyncStorage when max retries exceeded for fatal errors
- Include: operation data, error, timestamp, table name
- Cap at ~50 entries to avoid storage bloat

**Logging:**

- Use existing `js-logger` pattern (already in App/index.tsx)
- Log operation attempts, retries, successes, failures with structured data

### 2. SyncDebugContext

- In-memory circular buffer (~200 log entries max)
- Subscribe to PowerSync status changes
- Capture upload success/failure events from Connector
- Expose: logs array, clearLogs(), failed operations list

### 3. SyncDebug Screen

- Display sync status (connected, hasSynced, lastSyncedAt, uploadQueueSize from PowerSync's currentStatus)
- Scrollable log view with timestamps
- List of failed operations with ability to retry or discard
- Button to clear logs
- Simple, functional UI matching existing Settings.tsx style

### 4. Navigation

- Add "SyncDebug" to RootStackParamList
- Add screen to Stack.Navigator
- Add "View Sync Debug" button in Settings.tsx (only when sync enabled)

## Important Notes

- PowerSync already has built-in retry at the transaction level (throws = retry later). Our retry is per-operation within a transaction.
- Using js-logger (already a dependency) rather than adding new logging lib
- Failed operations stored locally - user can inspect and decide to discard
- Keeping implementation minimal - no over-engineering

### To-dos

- [ ] Add exponential backoff retry logic and failed operation storage to Connector.ts
- [ ] Create SyncDebugContext for log capture and failed operation tracking
- [ ] Create SyncDebug.tsx screen to display logs and failed operations
- [ ] Add SyncDebug route to App/index.tsx and link from Settings.tsx