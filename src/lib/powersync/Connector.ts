import 'react-native-url-polyfill/auto'

import { UpdateType, AbstractPowerSyncDatabase, PowerSyncBackendConnector, CrudEntry } from '@powersync/react-native';
import { SupabaseClient, createClient, PostgrestSingleResponse } from '@supabase/supabase-js';
import CryptoJS from 'crypto-js';
import { Settings } from '../hooks/SettingsContext';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Logger from 'js-logger';
import { SyncLogEntry, emitSyncLog, emitCapturedLog } from '../logging/syncLog';
import { getOrCreateDeviceId } from './deviceId';

/**
 * Decodes a base64url encoded string to raw bytes (as a WordArray for crypto-js).
 * PowerSync stores HS256 secrets as base64url encoded.
 */
const base64UrlDecode = (str: string): CryptoJS.lib.WordArray => {
  // Convert base64url to base64
  let base64 = str.replace(/-/g, '+').replace(/_/g, '/');
  // Add padding if needed
  while (base64.length % 4) {
    base64 += '=';
  }
  return CryptoJS.enc.Base64.parse(base64);
};

/**
 * Creates a JWT token signed with HS256.
 * Uses crypto-js which is pure JavaScript and works in React Native.
 * @param payload - JWT payload claims
 * @param base64UrlSecret - The HS256 secret (base64url encoded, as stored in PowerSync)
 * @param kid - Key ID to include in the JWT header
 */
export const createHS256Token = (payload: Record<string, unknown>, base64UrlSecret: string, kid: string): string => {
  const header = { alg: 'HS256', typ: 'JWT', kid };
  
  const base64UrlEncode = (str: string): string => {
    return CryptoJS.enc.Base64.stringify(CryptoJS.enc.Utf8.parse(str))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
  };
  
  const headerEncoded = base64UrlEncode(JSON.stringify(header));
  const payloadEncoded = base64UrlEncode(JSON.stringify(payload));
  const dataToSign = `${headerEncoded}.${payloadEncoded}`;
  
  // Decode the base64url secret to raw bytes for HMAC signing
  const secretBytes = base64UrlDecode(base64UrlSecret);
  
  const signature = CryptoJS.HmacSHA256(dataToSign, secretBytes);
  const signatureEncoded = CryptoJS.enc.Base64.stringify(signature)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  
  return `${dataToSign}.${signatureEncoded}`;
};

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

// Re-export for backwards compatibility
export type { SyncLogEntry } from '../logging/syncLog';
export { emitSyncLog, subscribeSyncLogs } from '../logging/syncLog';

// Log filter levels - used by UI to filter what's displayed (not what's captured)
export type LogFilterLevel = 'info' | 'debug' | 'firehose';

let currentLogFilterLevel: LogFilterLevel = 'info';

export const setLogFilterLevel = (level: LogFilterLevel) => {
  currentLogFilterLevel = level;
};

export const getLogFilterLevel = (): LogFilterLevel => {
  return currentLogFilterLevel;
};

// Setup js-logger handler to capture all logs
let loggerHandlerInstalled = false;

export const setupPowerSyncLogCapture = () => {
  if (loggerHandlerInstalled) return;
  loggerHandlerInstalled = true;

  const defaultHandler = Logger.createDefaultHandler();

  // Capture ALL logs - filtering happens at display time in the UI
  Logger.setHandler((messages, context) => {
    defaultHandler(messages, context);
    emitCapturedLog(messages, context?.name || '', context?.level);
  });
};

// Failed operations storage helpers
export const getFailedOperations = async (): Promise<FailedOperation[]> => {
  try {
    const stored = await AsyncStorage.getItem(FAILED_OPS_STORAGE_KEY);
    return stored ? JSON.parse(stored) : [];
  } catch (e) {
    emitSyncLog('warn', 'Failed to load failed operations from storage', { error: e });
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
    emitSyncLog('error', 'Failed to save failed operation', { error: e });
  }
};

export const removeFailedOperation = async (id: string): Promise<void> => {
  try {
    const existing = await getFailedOperations();
    const updated = existing.filter(op => op.id !== id);
    await AsyncStorage.setItem(FAILED_OPS_STORAGE_KEY, JSON.stringify(updated));
  } catch (e) {
    emitSyncLog('warn', 'Failed to remove failed operation from storage', { error: e, id });
  }
};

export const clearFailedOperations = async (): Promise<void> => {
  try {
    await AsyncStorage.removeItem(FAILED_OPS_STORAGE_KEY);
  } catch (e) {
    emitSyncLog('warn', 'Failed to clear failed operations from storage', { error: e });
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
        emitSyncLog('debug', 'fetchCredentials called', {
            endpoint: this.settings.powersyncUrl,
            hasSecret: !!this.settings.powersyncToken,
        });
        
        // Generate a fresh JWT signed with the HS256 secret
        const deviceId = await getOrCreateDeviceId();
        const now = Math.floor(Date.now() / 1000);
        
        const payload = {
            sub: deviceId,
            aud: 'powersync',  // Must match JWT Audience in PowerSync dashboard
            iat: now,
            exp: now + 300,  // 5 minutes, auto-renewed by PowerSync
        };
        
        const token = createHS256Token(payload, this.settings.powersyncToken, 'powersync');
        
        emitSyncLog('debug', 'Generated JWT for device', { deviceId });
        return { endpoint: this.settings.powersyncUrl, token };
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
