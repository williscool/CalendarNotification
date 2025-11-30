import 'react-native-url-polyfill/auto'

import { UpdateType, AbstractPowerSyncDatabase, PowerSyncBackendConnector, CrudEntry } from '@powersync/react-native';
import { SupabaseClient, createClient, PostgrestSingleResponse } from '@supabase/supabase-js';
import { Settings } from '../hooks/SettingsContext';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Logger from 'js-logger';

const log = Logger.get('PowerSync');

/// Postgres Response codes that we cannot recover from by retrying.
const FATAL_RESPONSE_CODES = [
  // Class 22 — Data Exception
  // Examples include data type mismatch.
  new RegExp('^22...$'),
  // Class 23 — Integrity Constraint Violation.
  // Examples include NOT NULL, FOREIGN KEY and UNIQUE violations.
  new RegExp('^23...$'),
  // INSUFFICIENT PRIVILEGE - typically a row-level security violation
  new RegExp('^42501$')
];

// Retry configuration
const MAX_RETRIES = 3;
const BASE_DELAY_MS = 1000;
const MAX_FAILED_OPS = 50;
const FAILED_OPS_STORAGE_KEY = '@powersync_failed_operations';

interface SupabaseError {
  code: string;
  message?: string;
}

export interface FailedOperation {
  id: string;
  table: string;
  op: string;
  opData: Record<string, unknown> | undefined;
  recordId: string;
  error: string;
  errorCode: string;
  timestamp: number;
}

export interface SyncLogEntry {
  timestamp: number;
  level: 'info' | 'warn' | 'error' | 'debug';
  message: string;
  data?: Record<string, unknown>;
}

// Global event emitter for sync logs - allows SyncDebugContext to subscribe
type SyncLogListener = (entry: SyncLogEntry) => void;
const syncLogListeners: Set<SyncLogListener> = new Set();

export const subscribeSyncLogs = (listener: SyncLogListener): (() => void) => {
  syncLogListeners.add(listener);
  return () => syncLogListeners.delete(listener);
};

const emitSyncLog = (level: SyncLogEntry['level'], message: string, data?: Record<string, unknown>) => {
  const entry: SyncLogEntry = { timestamp: Date.now(), level, message, data };
  syncLogListeners.forEach(listener => listener(entry));
  
  // Also log to js-logger
  switch (level) {
    case 'error': log.error(message, data); break;
    case 'warn': log.warn(message, data); break;
    case 'info': log.info(message, data); break;
    case 'debug': log.debug(message, data); break;
  }
};

// PowerSync SDK log prefixes we want to capture
const POWERSYNC_LOG_PREFIXES = [
  'PowerSyncStream',
  'SqliteBucketStorage', 
  'PowerSync',
  'AbstractPowerSyncDatabase',
  'PowerSyncBackendConnector',
];

// Check if a log message is from PowerSync SDK
const isPowerSyncLog = (loggerName: string): boolean => {
  return POWERSYNC_LOG_PREFIXES.some(prefix => loggerName.includes(prefix));
};

// Setup js-logger handler to capture PowerSync SDK logs
let loggerHandlerInstalled = false;

export const setupPowerSyncLogCapture = () => {
  if (loggerHandlerInstalled) return;
  loggerHandlerInstalled = true;

  // Get the default handler
  const defaultHandler = Logger.createDefaultHandler();

  // Install custom handler that intercepts PowerSync logs
  Logger.setHandler((messages, context) => {
    // Always call default handler for console output
    defaultHandler(messages, context);

    // Guard against undefined/null messages
    if (!messages) return;

    // Check if this is a PowerSync-related log
    const loggerName = context?.name || '';
    if (isPowerSyncLog(loggerName) || loggerName === '') {
      // Convert js-logger level to our level
      let level: SyncLogEntry['level'] = 'debug';
      const contextLevel = context?.level;
      if (contextLevel === Logger.ERROR) level = 'error';
      else if (contextLevel === Logger.WARN) level = 'warn';
      else if (contextLevel === Logger.INFO) level = 'info';
      else if (contextLevel === Logger.DEBUG) level = 'debug';

      // Format the message - handle both array and non-array messages
      const prefix = loggerName ? `[${loggerName}] ` : '';
      let messageText: string;
      try {
        const msgArray = Array.isArray(messages) ? messages : [messages];
        messageText = msgArray.map(m => {
          if (m === undefined) return 'undefined';
          if (m === null) return 'null';
          if (typeof m === 'object') {
            try {
              return JSON.stringify(m);
            } catch {
              return String(m);
            }
          }
          return String(m);
        }).join(' ');
      } catch {
        messageText = String(messages);
      }

      // Emit to our subscribers (without re-logging to avoid loops)
      const entry: SyncLogEntry = { 
        timestamp: Date.now(), 
        level, 
        message: `${prefix}${messageText}` 
      };
      syncLogListeners.forEach(listener => listener(entry));
    }
  });
};

// Failed operations storage helpers
export const getFailedOperations = async (): Promise<FailedOperation[]> => {
  try {
    const stored = await AsyncStorage.getItem(FAILED_OPS_STORAGE_KEY);
    return stored ? JSON.parse(stored) : [];
  } catch {
    return [];
  }
};

export const saveFailedOperation = async (op: FailedOperation): Promise<void> => {
  try {
    const existing = await getFailedOperations();
    const updated = [op, ...existing].slice(0, MAX_FAILED_OPS);
    await AsyncStorage.setItem(FAILED_OPS_STORAGE_KEY, JSON.stringify(updated));
    emitSyncLog('warn', 'Failed operation saved for review', { table: op.table, op: op.op, id: op.recordId });
  } catch (e) {
    emitSyncLog('error', 'Failed to save failed operation', { error: String(e) });
  }
};

export const removeFailedOperation = async (id: string): Promise<void> => {
  try {
    const existing = await getFailedOperations();
    const updated = existing.filter(op => op.id !== id);
    await AsyncStorage.setItem(FAILED_OPS_STORAGE_KEY, JSON.stringify(updated));
  } catch {
    // Ignore errors
  }
};

export const clearFailedOperations = async (): Promise<void> => {
  try {
    await AsyncStorage.removeItem(FAILED_OPS_STORAGE_KEY);
  } catch {
    // Ignore errors
  }
};

const isFatalError = (error: SupabaseError): boolean => {
  return typeof error.code === 'string' && FATAL_RESPONSE_CODES.some((regex) => regex.test(error.code));
};

const isNetworkError = (error: unknown): boolean => {
  if (error instanceof Error) {
    const msg = error.message.toLowerCase();
    return msg.includes('network') || msg.includes('fetch') || msg.includes('timeout');
  }
  return false;
};

const sleep = (ms: number): Promise<void> => new Promise(resolve => setTimeout(resolve, ms));

const calculateBackoff = (attempt: number): number => {
  // Exponential backoff with jitter: base * 2^attempt + random(0-1000ms)
  const exponential = BASE_DELAY_MS * Math.pow(2, attempt);
  const jitter = Math.random() * 1000;
  return exponential + jitter;
};

export class Connector implements PowerSyncBackendConnector {
    client: SupabaseClient;
    private settings: Settings;

    constructor(settings: Settings) {
        this.settings = settings;
        // TODO setup session storage to support supabase auth
        // right now its not needed because will have people input
        // there own powersync token an supabase links in the app to start
        this.client = createClient(settings.supabaseUrl, settings.supabaseAnonKey);
    }

    async fetchCredentials() {
        return {
            endpoint: this.settings.powersyncUrl,
            token: this.settings.powersyncToken  // TODO: programattically generate token from user id (i.e. email or phone number) + random secret
        };
    }

    private async executeOperation(op: CrudEntry): Promise<PostgrestSingleResponse<null> | null> {
        const table = this.client.from(op.table);
        
        switch (op.op) {
            case UpdateType.PUT:
                const record = { ...op.opData, id: op.id };
                return await table.upsert(record);
            case UpdateType.PATCH:
                return await table.update(op.opData).eq('id', op.id);
            case UpdateType.DELETE:
                return await table.delete().eq('id', op.id);
            default:
                return null;
        }
    }

    private async executeWithRetry(op: CrudEntry): Promise<void> {
        let lastError: unknown = null;
        
        for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    const delay = calculateBackoff(attempt - 1);
                    emitSyncLog('info', `Retry attempt ${attempt + 1}/${MAX_RETRIES} after ${Math.round(delay)}ms`, {
                        table: op.table,
                        op: op.op,
                        id: op.id
                    });
                    await sleep(delay);
                }

                const result = await this.executeOperation(op);

                if (result?.error) {
                    const error = result.error as SupabaseError;
                    
                    // Fatal errors should not be retried
                    if (isFatalError(error)) {
                        emitSyncLog('error', 'Fatal error - not retryable', {
                            table: op.table,
                            op: op.op,
                            id: op.id,
                            errorCode: error.code,
                            error: error.message
                        });
                        throw result.error;
                    }
                    
                    // Non-fatal errors can be retried
                    lastError = result.error;
                    emitSyncLog('warn', `Operation failed (attempt ${attempt + 1}/${MAX_RETRIES})`, {
                        table: op.table,
                        op: op.op,
                        id: op.id,
                        errorCode: error.code,
                        error: error.message
                    });
                    continue;
                }

                // Success
                emitSyncLog('debug', 'Operation succeeded', {
                    table: op.table,
                    op: op.op,
                    id: op.id,
                    attempt: attempt + 1
                });
                return;

            } catch (ex) {
                lastError = ex;
                const error = ex as SupabaseError;
                
                // Fatal errors should not be retried
                if (typeof error.code === 'string' && isFatalError(error)) {
                    throw ex;
                }
                
                // Network errors are retryable
                if (isNetworkError(ex)) {
                    emitSyncLog('warn', `Network error (attempt ${attempt + 1}/${MAX_RETRIES})`, {
                        table: op.table,
                        op: op.op,
                        id: op.id,
                        error: ex instanceof Error ? ex.message : String(ex)
                    });
                    continue;
                }
                
                // Unknown errors - retry with caution
                emitSyncLog('warn', `Unknown error (attempt ${attempt + 1}/${MAX_RETRIES})`, {
                    table: op.table,
                    op: op.op,
                    id: op.id,
                    error: ex instanceof Error ? ex.message : String(ex)
                });
            }
        }

        // All retries exhausted
        emitSyncLog('error', `All ${MAX_RETRIES} retries exhausted`, {
            table: op.table,
            op: op.op,
            id: op.id
        });
        throw lastError;
    }

    async uploadData(database: AbstractPowerSyncDatabase): Promise<void> {
        const transaction = await database.getNextCrudTransaction();
    
        if (!transaction) {
            return;
        }
    
        emitSyncLog('info', 'Starting transaction upload', {
            operationCount: transaction.crud.length
        });

        let lastOp: CrudEntry | null = null;
        try {
            for (const op of transaction.crud) {
                lastOp = op;
                await this.executeWithRetry(op);
            }
    
            await transaction.complete();
            emitSyncLog('info', 'Transaction completed successfully', {
                operationCount: transaction.crud.length
            });
        } catch (ex: unknown) {
            const error = ex as SupabaseError;
            
            if (typeof error.code === 'string' && isFatalError(error)) {
                // Save the failed operation for user review
                if (lastOp) {
                    await saveFailedOperation({
                        id: `${lastOp.table}-${lastOp.id}-${Date.now()}`,
                        table: lastOp.table,
                        op: lastOp.op,
                        opData: lastOp.opData,
                        recordId: lastOp.id,
                        error: error.message || 'Unknown error',
                        errorCode: error.code,
                        timestamp: Date.now()
                    });
                }
                
                emitSyncLog('error', 'Fatal error - transaction discarded, operation saved', {
                    table: lastOp?.table,
                    op: lastOp?.op,
                    id: lastOp?.id,
                    errorCode: error.code
                });
                
                // Complete the transaction to unblock the queue
                await transaction.complete();
            } else {
                // Transient error - let PowerSync retry the whole transaction
                emitSyncLog('warn', 'Transient error - transaction will be retried by PowerSync', {
                    table: lastOp?.table,
                    op: lastOp?.op,
                    id: lastOp?.id,
                    error: ex instanceof Error ? ex.message : String(ex)
                });
                throw ex;
            }
        }
    }
}
