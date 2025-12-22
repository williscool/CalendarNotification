/**
 * Sync logging utilities - centralized log emission and subscription.
 * Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
 */

import { formatError } from './errors';

const LOG_PREFIX = 'CNPlusSync';

export interface SyncLogEntry {
  id: string;
  timestamp: number;
  level: 'info' | 'warn' | 'error' | 'debug';
  message: string;
  data?: Record<string, unknown>;
}

type SyncLogListener = (entry: SyncLogEntry) => void;
const syncLogListeners: Set<SyncLogListener> = new Set();

// Counter for unique IDs (combined with timestamp for uniqueness)
let logIdCounter = 0;
const generateLogId = (): string => `${Date.now()}-${++logIdCounter}`;

export const subscribeSyncLogs = (listener: SyncLogListener): (() => void) => {
  syncLogListeners.add(listener);
  return () => syncLogListeners.delete(listener);
};

/** Safely stringify data for console output */
const safeStringify = (data: unknown): string => {
  try {
    return JSON.stringify(data);
  } catch {
    return '[unserializable data]';
  }
};

export const emitSyncLog = (level: SyncLogEntry['level'], message: string, data?: Record<string, unknown>) => {
  // Auto-format any Error values in data
  const formattedData = data ? Object.fromEntries(
    Object.entries(data).map(([key, value]) => 
      [key, value instanceof Error ? formatError(value) : value]
    )
  ) : undefined;
  
  // Also output to console for developer visibility
  const consoleMsg = formattedData ? `${message} ${safeStringify(formattedData)}` : message;
  switch (level) {
    case 'error': console.error(`[${LOG_PREFIX}] ${consoleMsg}`); break;
    case 'warn': console.warn(`[${LOG_PREFIX}] ${consoleMsg}`); break;
    case 'info': console.info(`[${LOG_PREFIX}] ${consoleMsg}`); break;
    case 'debug': console.debug(`[${LOG_PREFIX}] ${consoleMsg}`); break;
  }
  
  const entry: SyncLogEntry = { id: generateLogId(), timestamp: Date.now(), level, message, data: formattedData };
  syncLogListeners.forEach(listener => listener(entry));
};

/** Emits directly to listeners without formatting (for internal use by log capture) */
export const emitSyncLogEntry = (entry: Omit<SyncLogEntry, 'id'>) => {
  const entryWithId: SyncLogEntry = { ...entry, id: generateLogId() };
  syncLogListeners.forEach(listener => listener(entryWithId));
};

/** Formats an array of log message parts into a string */
export function formatLogMessage(messages: unknown): string {
  try {
    const msgArray = Array.isArray(messages) ? messages : [messages];
    return msgArray.map(m => {
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
    return String(messages);
  }
}

/** js-logger level object shape */
interface JsLoggerLevel {
  value: number;
  name: string;
}

/** Known js-logger level values (from js-logger source) */
const JS_LOGGER_LEVELS = {
  ERROR: 1,
  WARN: 2,
  INFO: 3,
  DEBUG: 4,
} as const;

/** Converts js-logger level to SyncLogEntry level */
export function jsLoggerLevelToSyncLevel(contextLevel: unknown): SyncLogEntry['level'] {
  const level = contextLevel as JsLoggerLevel | undefined;
  if (!level?.value) return 'debug';
  
  switch (level.value) {
    case JS_LOGGER_LEVELS.ERROR: return 'error';
    case JS_LOGGER_LEVELS.WARN: return 'warn';
    case JS_LOGGER_LEVELS.INFO: return 'info';
    default: return 'debug';
  }
}

/** Emits a captured log from js-logger with proper formatting */
export function emitCapturedLog(
  messages: unknown,
  loggerName: string,
  contextLevel: unknown
): void {
  // Guard against undefined/null messages
  if (messages === undefined || messages === null) {
    emitSyncLogEntry({
      timestamp: Date.now(),
      level: 'warn',
      message: `[${loggerName || 'unknown'}] Empty log message received`,
    });
    return;
  }

  const level = jsLoggerLevelToSyncLevel(contextLevel);
  const prefix = loggerName ? `[${loggerName}] ` : '';
  const messageText = formatLogMessage(messages);
  
  emitSyncLogEntry({
    timestamp: Date.now(),
    level,
    message: `${prefix}${messageText}`,
  });
}

