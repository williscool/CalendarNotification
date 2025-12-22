/**
 * Error handling utilities for consistent error logging across the app.
 * Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
 */

/** Extracted error info for structured logging */
export interface ErrorInfo {
  message: string;
  stack?: string;
  cause?: string;
}

/** Extracts useful information from an unknown caught value, recursively handling cause chain */
export function formatError(e: unknown): ErrorInfo {
  if (!(e instanceof Error)) {
    return { message: String(e) };
  }
  
  // Handle cause chain (ES2022+ feature, check via 'in' operator)
  let causeStr: string | undefined;
  if ('cause' in e && e.cause) {
    const causeInfo = formatError(e.cause);
    causeStr = causeInfo.stack || causeInfo.message;
  }
  
  return {
    message: e.message,
    stack: e.stack,
    cause: causeStr,
  };
}

