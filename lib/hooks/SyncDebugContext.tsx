import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { 
  subscribeSyncLogs, 
  getFailedOperations, 
  removeFailedOperation as removeFailedOp,
  clearFailedOperations as clearFailedOps,
  setLogFilterLevel as setFilterLevel,
  getLogFilterLevel,
  SyncLogEntry,
  FailedOperation,
  LogFilterLevel,
} from '../powersync/Connector';

const MAX_LOG_ENTRIES = 2000;

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

  const setLogFilterLevel = useCallback((level: LogFilterLevel) => {
    setFilterLevel(level);
    setLogFilterLevelState(level);
  }, []);

  // Subscribe to sync log events
  useEffect(() => {
    const unsubscribe = subscribeSyncLogs((entry) => {
      // Use ref to avoid stale closure issues
      const newLogs = [entry, ...logsRef.current].slice(0, MAX_LOG_ENTRIES);
      logsRef.current = newLogs;
      setLogs(newLogs);
    });

    return () => unsubscribe();
  }, []);

  // Load failed operations on mount
  useEffect(() => {
    const loadFailedOps = async () => {
      const ops = await getFailedOperations();
      setFailedOperations(ops);
    };
    loadFailedOps();
  }, []);

  const clearLogs = useCallback(() => {
    logsRef.current = [];
    setLogs([]);
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

