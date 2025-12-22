import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { subscribeSyncLogs, SyncLogEntry } from '../logging/syncLog';
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

interface SyncDebugContextType {
  logs: SyncLogEntry[];
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
  const [logs, setLogs] = useState<SyncLogEntry[]>([]);
  const [failedOperations, setFailedOperations] = useState<FailedOperation[]>([]);
  const [logFilterLevel, setLogFilterLevelState] = useState<LogFilterLevel>(getLogFilterLevel());
  const logsRef = useRef<SyncLogEntry[]>([]);
  const saveTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const isLoadedRef = useRef(false);

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
      } catch {
        // Ignore storage errors
      }
    }, SAVE_DEBOUNCE_MS);
  }, []);

  const setLogFilterLevel = useCallback((level: LogFilterLevel) => {
    setFilterLevel(level);
    setLogFilterLevelState(level);
  }, []);

  // Load persisted logs on mount
  useEffect(() => {
    const loadLogs = async () => {
      try {
        const stored = await AsyncStorage.getItem(LOGS_STORAGE_KEY);
        if (stored) {
          const parsed = JSON.parse(stored) as SyncLogEntry[];
          logsRef.current = parsed;
          setLogs(parsed);
        }
      } catch {
        // Ignore load errors
      }
      isLoadedRef.current = true;
    };
    loadLogs();

    // Cleanup save timeout on unmount
    return () => {
      if (saveTimeoutRef.current) {
        clearTimeout(saveTimeoutRef.current);
      }
    };
  }, []);

  // Subscribe to sync log events
  useEffect(() => {
    const unsubscribe = subscribeSyncLogs((entry) => {
      // Use ref to avoid stale closure issues
      const newLogs = [entry, ...logsRef.current].slice(0, MAX_LOG_ENTRIES_MEMORY);
      logsRef.current = newLogs;
      setLogs(newLogs);
      
      // Schedule save to storage (only after initial load)
      if (isLoadedRef.current) {
        scheduleSave(newLogs);
      }
    });

    return () => unsubscribe();
  }, [scheduleSave]);

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
    setLogs([]);
    try {
      await AsyncStorage.removeItem(LOGS_STORAGE_KEY);
    } catch {
      // Ignore errors
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
      logs, 
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

