import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { subscribeSyncLogs, SyncLogEntry, emitSyncLog } from '../logging/syncLog';
import { 
  getFailedOperations, 
  removeFailedOperation as removeFailedOp,
  clearFailedOperations as clearFailedOps,
  setLogFilterLevel as setFilterLevel,
  getLogFilterLevel,
  FailedOperation,
  LogFilterLevel,
} from '../powersync/Connector';

const MAX_LOG_ENTRIES_MEMORY = 2000;  // Keep more in memory for current session
const MAX_LOG_ENTRIES_STORAGE = 500;  // Persist fewer to avoid storage bloat
const LOGS_STORAGE_KEY = '@powersync_debug_logs';
const SAVE_DEBOUNCE_MS = 2000;  // Debounce saves to avoid excessive writes
const UI_UPDATE_BATCH_MS = 100;  // Batch UI updates to prevent stuttering

interface SyncDebugContextType {
  logs: SyncLogEntry[];
  logsVersion: number;  // For FlatList extraData - triggers re-render when bumped
  failedOperations: FailedOperation[];
  logFilterLevel: LogFilterLevel;
  setLogFilterLevel: (level: LogFilterLevel) => void;
  clearLogs: () => void;
  refreshFailedOperations: () => Promise<void>;
  removeFailedOperation: (id: string) => Promise<void>;
  clearFailedOperations: () => Promise<void>;
}

const SyncDebugContext = createContext<SyncDebugContextType | undefined>(undefined);

export const SyncDebugProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // Use version counter instead of logs in state - avoids creating new array on every update
  const [logsVersion, setLogsVersion] = useState(0);
  const [failedOperations, setFailedOperations] = useState<FailedOperation[]>([]);
  const [logFilterLevel, setLogFilterLevelState] = useState<LogFilterLevel>(getLogFilterLevel());
  const logsRef = useRef<SyncLogEntry[]>([]);
  const saveTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const uiUpdateTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const isLoadedRef = useRef(false);
  const pendingUiUpdateRef = useRef(false);

  // Batch UI updates to prevent stuttering with high log volume
  const scheduleUiUpdate = useCallback(() => {
    if (pendingUiUpdateRef.current) return; // Already scheduled
    pendingUiUpdateRef.current = true;
    
    uiUpdateTimeoutRef.current = setTimeout(() => {
      pendingUiUpdateRef.current = false;
      // Bump version to trigger re-render, FlatList reads from ref
      setLogsVersion(v => v + 1);
    }, UI_UPDATE_BATCH_MS);
  }, []);

  // Debounced save to AsyncStorage
  const scheduleSave = useCallback((logsToSave: SyncLogEntry[]) => {
    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
    }
    saveTimeoutRef.current = setTimeout(async () => {
      try {
        // Only persist the most recent entries to storage
        const toStore = logsToSave.slice(0, MAX_LOG_ENTRIES_STORAGE);
        await AsyncStorage.setItem(LOGS_STORAGE_KEY, JSON.stringify(toStore));
      } catch (e) {
        emitSyncLog('warn', 'Failed to save sync logs to storage', { error: e });
      }
    }, SAVE_DEBOUNCE_MS);
  }, []);

  const setLogFilterLevel = useCallback((level: LogFilterLevel) => {
    setFilterLevel(level);
    setLogFilterLevelState(level);
  }, []);

  // Load persisted logs on mount
  useEffect(() => {
    let migrationCounter = 0;
    const loadLogs = async () => {
      try {
        const stored = await AsyncStorage.getItem(LOGS_STORAGE_KEY);
        if (stored) {
          const parsed = JSON.parse(stored) as SyncLogEntry[];
          // Migrate legacy entries that don't have an id field
          const migrated = parsed.map(entry => 
            entry.id ? entry : { ...entry, id: `legacy-${entry.timestamp}-${++migrationCounter}` }
          );
          logsRef.current = migrated;
          setLogsVersion(v => v + 1);  // Trigger re-render with loaded logs
        }
      } catch (e) {
        emitSyncLog('warn', 'Failed to load sync logs from storage', { error: e });
      }
      isLoadedRef.current = true;
    };
    loadLogs();

    // Cleanup timeouts on unmount
    return () => {
      if (saveTimeoutRef.current) {
        clearTimeout(saveTimeoutRef.current);
      }
      if (uiUpdateTimeoutRef.current) {
        clearTimeout(uiUpdateTimeoutRef.current);
      }
    };
  }, []);

  // Subscribe to sync log events
  useEffect(() => {
    const unsubscribe = subscribeSyncLogs((entry) => {
      // Use ref to avoid stale closure issues
      const newLogs = [entry, ...logsRef.current].slice(0, MAX_LOG_ENTRIES_MEMORY);
      logsRef.current = newLogs;
      
      // Batch UI updates to prevent stuttering
      scheduleUiUpdate();
      
      // Schedule save to storage (only after initial load)
      if (isLoadedRef.current) {
        scheduleSave(newLogs);
      }
    });

    return () => unsubscribe();
  }, [scheduleSave, scheduleUiUpdate]);

  // Load failed operations on mount
  useEffect(() => {
    const loadFailedOps = async () => {
      const ops = await getFailedOperations();
      setFailedOperations(ops);
    };
    loadFailedOps();
  }, []);

  const clearLogs = useCallback(async () => {
    logsRef.current = [];
    setLogsVersion(v => v + 1);  // Trigger re-render with empty logs
    try {
      await AsyncStorage.removeItem(LOGS_STORAGE_KEY);
    } catch (e) {
      emitSyncLog('warn', 'Failed to clear sync logs from storage', { error: e });
    }
  }, []);

  const refreshFailedOperations = useCallback(async () => {
    const ops = await getFailedOperations();
    setFailedOperations(ops);
  }, []);

  const removeFailedOperation = useCallback(async (id: string) => {
    await removeFailedOp(id);
    await refreshFailedOperations();
  }, [refreshFailedOperations]);

  const clearFailedOperations = useCallback(async () => {
    await clearFailedOps();
    setFailedOperations([]);
  }, []);

  return (
    <SyncDebugContext.Provider value={{ 
      logs: logsRef.current,  // Read directly from ref
      logsVersion,  // Pass to FlatList extraData for re-render trigger
      failedOperations,
      logFilterLevel,
      setLogFilterLevel,
      clearLogs,
      refreshFailedOperations,
      removeFailedOperation,
      clearFailedOperations
    }}>
      {children}
    </SyncDebugContext.Provider>
  );
};

export const useSyncDebug = () => {
  const context = useContext(SyncDebugContext);
  if (context === undefined) {
    throw new Error('useSyncDebug must be used within a SyncDebugProvider');
  }
  return context;
};

