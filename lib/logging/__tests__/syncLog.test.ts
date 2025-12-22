/**
 * Tests for sync logging utilities
 */

import {
  subscribeSyncLogs,
  emitSyncLog,
  emitSyncLogEntry,
  formatLogMessage,
  jsLoggerLevelToSyncLevel,
  emitCapturedLog,
  SyncLogEntry,
} from '../syncLog';

describe('subscribeSyncLogs', () => {
  it('should subscribe and receive log entries', () => {
    const listener = jest.fn();
    const unsubscribe = subscribeSyncLogs(listener);

    emitSyncLogEntry({
      timestamp: Date.now(),
      level: 'info',
      message: 'test message',
    });

    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener).toHaveBeenCalledWith(
      expect.objectContaining({
        level: 'info',
        message: 'test message',
      })
    );

    unsubscribe();
  });

  it('should stop receiving after unsubscribe', () => {
    const listener = jest.fn();
    const unsubscribe = subscribeSyncLogs(listener);
    unsubscribe();

    emitSyncLogEntry({
      timestamp: Date.now(),
      level: 'info',
      message: 'should not receive',
    });

    expect(listener).not.toHaveBeenCalled();
  });

  it('should support multiple listeners', () => {
    const listener1 = jest.fn();
    const listener2 = jest.fn();
    const unsub1 = subscribeSyncLogs(listener1);
    const unsub2 = subscribeSyncLogs(listener2);

    emitSyncLogEntry({
      timestamp: Date.now(),
      level: 'debug',
      message: 'multi-listener test',
    });

    expect(listener1).toHaveBeenCalledTimes(1);
    expect(listener2).toHaveBeenCalledTimes(1);

    unsub1();
    unsub2();
  });
});

describe('emitSyncLog', () => {
  it('should emit log entry with correct level and message', () => {
    const listener = jest.fn();
    const unsubscribe = subscribeSyncLogs(listener);

    emitSyncLog('error', 'something failed', { code: 500 });

    expect(listener).toHaveBeenCalledWith(
      expect.objectContaining({
        level: 'error',
        message: 'something failed',
        data: { code: 500 },
      })
    );

    unsubscribe();
  });

  it('should auto-format Error objects in data', () => {
    const listener = jest.fn();
    const unsubscribe = subscribeSyncLogs(listener);

    const testError = new Error('test error message');
    emitSyncLog('error', 'operation failed', { error: testError });

    const call = listener.mock.calls[0][0] as SyncLogEntry;
    expect(call.data?.error).toEqual(
      expect.objectContaining({
        message: 'test error message',
      })
    );

    unsubscribe();
  });

  it('should handle data with mixed types', () => {
    const listener = jest.fn();
    const unsubscribe = subscribeSyncLogs(listener);

    emitSyncLog('info', 'mixed data', {
      string: 'hello',
      number: 42,
      error: new Error('err'),
      nested: { a: 1 },
    });

    const call = listener.mock.calls[0][0] as SyncLogEntry;
    expect(call.data?.string).toBe('hello');
    expect(call.data?.number).toBe(42);
    expect(call.data?.error).toEqual(expect.objectContaining({ message: 'err' }));
    expect(call.data?.nested).toEqual({ a: 1 });

    unsubscribe();
  });
});

describe('formatLogMessage', () => {
  it('should format string message', () => {
    expect(formatLogMessage('hello')).toBe('hello');
  });

  it('should format array of strings', () => {
    expect(formatLogMessage(['hello', 'world'])).toBe('hello world');
  });

  it('should handle null', () => {
    expect(formatLogMessage(null)).toBe('null');
  });

  it('should handle undefined', () => {
    expect(formatLogMessage(undefined)).toBe('undefined');
  });

  it('should stringify objects', () => {
    expect(formatLogMessage({ key: 'value' })).toBe('{"key":"value"}');
  });

  it('should handle mixed array', () => {
    expect(formatLogMessage(['msg', 42, null, { a: 1 }])).toBe('msg 42 null {"a":1}');
  });

  it('should handle circular references gracefully', () => {
    const circular: Record<string, unknown> = { a: 1 };
    circular.self = circular;
    // Should not throw, falls back to String()
    expect(() => formatLogMessage(circular)).not.toThrow();
  });
});

describe('jsLoggerLevelToSyncLevel', () => {
  it('should convert ERROR level', () => {
    expect(jsLoggerLevelToSyncLevel({ value: 1, name: 'ERROR' })).toBe('error');
  });

  it('should convert WARN level', () => {
    expect(jsLoggerLevelToSyncLevel({ value: 2, name: 'WARN' })).toBe('warn');
  });

  it('should convert INFO level', () => {
    expect(jsLoggerLevelToSyncLevel({ value: 3, name: 'INFO' })).toBe('info');
  });

  it('should convert DEBUG level', () => {
    expect(jsLoggerLevelToSyncLevel({ value: 4, name: 'DEBUG' })).toBe('debug');
  });

  it('should default to debug for unknown levels', () => {
    expect(jsLoggerLevelToSyncLevel({ value: 99, name: 'UNKNOWN' })).toBe('debug');
  });

  it('should default to debug for undefined', () => {
    expect(jsLoggerLevelToSyncLevel(undefined)).toBe('debug');
  });

  it('should default to debug for null', () => {
    expect(jsLoggerLevelToSyncLevel(null)).toBe('debug');
  });
});

describe('emitCapturedLog', () => {
  it('should emit formatted log with prefix', () => {
    const listener = jest.fn();
    const unsubscribe = subscribeSyncLogs(listener);

    emitCapturedLog('test message', 'PowerSync', { value: 3, name: 'INFO' });

    expect(listener).toHaveBeenCalledWith(
      expect.objectContaining({
        level: 'info',
        message: '[PowerSync] test message',
      })
    );

    unsubscribe();
  });

  it('should emit without prefix when loggerName is empty', () => {
    const listener = jest.fn();
    const unsubscribe = subscribeSyncLogs(listener);

    emitCapturedLog('no prefix', '', { value: 4, name: 'DEBUG' });

    expect(listener).toHaveBeenCalledWith(
      expect.objectContaining({
        level: 'debug',
        message: 'no prefix',
      })
    );

    unsubscribe();
  });

  it('should emit warning for null messages', () => {
    const listener = jest.fn();
    const unsubscribe = subscribeSyncLogs(listener);

    emitCapturedLog(null, 'TestLogger', { value: 3, name: 'INFO' });

    expect(listener).toHaveBeenCalledWith(
      expect.objectContaining({
        level: 'warn',
        message: '[TestLogger] Empty log message received',
      })
    );

    unsubscribe();
  });

  it('should emit warning for undefined messages', () => {
    const listener = jest.fn();
    const unsubscribe = subscribeSyncLogs(listener);

    emitCapturedLog(undefined, '', { value: 3, name: 'INFO' });

    expect(listener).toHaveBeenCalledWith(
      expect.objectContaining({
        level: 'warn',
        message: '[unknown] Empty log message received',
      })
    );

    unsubscribe();
  });

  it('should handle array messages', () => {
    const listener = jest.fn();
    const unsubscribe = subscribeSyncLogs(listener);

    emitCapturedLog(['part1', 'part2', 123], 'Logger', { value: 2, name: 'WARN' });

    expect(listener).toHaveBeenCalledWith(
      expect.objectContaining({
        level: 'warn',
        message: '[Logger] part1 part2 123',
      })
    );

    unsubscribe();
  });
});

