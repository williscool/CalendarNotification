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

const MAX_CAUSE_DEPTH = 5;

/** Extracts useful information from an unknown caught value, with circular reference protection */
export function formatError(e: unknown, seen = new WeakSet<object>(), depth = 0): ErrorInfo {
  if (!(e instanceof Error)) {
    return { message: String(e) };
  }
  
  // Circular reference and depth protection
  if (seen.has(e) || depth >= MAX_CAUSE_DEPTH) {
    return { message: e.message, stack: e.stack };
  }
  seen.add(e);
  
  // Handle cause chain (ES2022+ feature, check via 'in' operator)
  let causeStr: string | undefined;
  if ('cause' in e && e.cause) {
    const causeInfo = formatError(e.cause, seen, depth + 1);
    causeStr = causeInfo.stack || causeInfo.message;
  }
  
  return {
    message: e.message,
    stack: e.stack,
    cause: causeStr,
  };
}

