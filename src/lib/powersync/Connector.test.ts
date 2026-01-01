import AsyncStorage from '@react-native-async-storage/async-storage';
import { createClient } from '@supabase/supabase-js';
import { subscribeSyncLogs, SyncLogEntry } from '../logging/syncLog';
import {
  Connector,
  getFailedOperations,
  saveFailedOperation,
  removeFailedOperation,
  clearFailedOperations,
  setLogFilterLevel,
  getLogFilterLevel,
  FailedOperation,
  LogFilterLevel,
} from './Connector';
import * as deviceIdModule from './deviceId';

// Mock deviceId module
jest.mock('./deviceId', () => ({
  getOrCreateDeviceId: jest.fn().mockResolvedValue('mock-device-uuid'),
}));

// Type the mocked modules
const mockedAsyncStorage = AsyncStorage as jest.Mocked<typeof AsyncStorage>;
const mockedCreateClient = createClient as jest.MockedFunction<typeof createClient>;

// Helper to create mock Settings
const createMockSettings = () => ({
  syncEnabled: true,
  syncType: 'unidirectional' as const,
  supabaseUrl: 'https://test.supabase.co',
  supabaseAnonKey: 'test-anon-key',
  powersyncUrl: 'https://test.powersync.com',
  powersyncToken: 'test-token',
});

// Helper to create mock CrudEntry
const createMockCrudEntry = (overrides = {}) => ({
  id: 'test-id-123',
  table: 'eventsV9',
  op: 'PUT' as const,
  opData: { title: 'Test Event', loc: 'Test Location' },
  ...overrides,
});

// Helper to create mock transaction
const createMockTransaction = (crud: any[] = [createMockCrudEntry()]) => ({
  crud,
  complete: jest.fn().mockResolvedValue(undefined),
});

// Helper to create mock database
const createMockDatabase = (transaction: any = null) => ({
  getNextCrudTransaction: jest.fn().mockResolvedValue(transaction),
});

// Helper to create mock Supabase client
const createMockSupabaseClient = (mockResult: any = { error: null }) => {
  const mockFrom = jest.fn().mockReturnValue({
    upsert: jest.fn().mockResolvedValue(mockResult),
    update: jest.fn().mockReturnValue({
      eq: jest.fn().mockResolvedValue(mockResult),
    }),
    delete: jest.fn().mockReturnValue({
      eq: jest.fn().mockResolvedValue(mockResult),
    }),
  });
  return { from: mockFrom };
};

describe('Connector', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAsyncStorage.getItem.mockResolvedValue(null);
    mockedAsyncStorage.setItem.mockResolvedValue(undefined);
    mockedAsyncStorage.removeItem.mockResolvedValue(undefined);
  });

  describe('constructor', () => {
    it('should create a Supabase client with provided settings', () => {
      const settings = createMockSettings();
      mockedCreateClient.mockReturnValue({} as any);

      new Connector(settings);

      expect(mockedCreateClient).toHaveBeenCalledWith(
        settings.supabaseUrl,
        settings.supabaseAnonKey
      );
    });
  });

  describe('fetchCredentials', () => {
    it('should generate JWT using HS256 secret and device ID', async () => {
      const settings = createMockSettings();
      mockedCreateClient.mockReturnValue({} as any);
      
      const connector = new Connector(settings);
      const credentials = await connector.fetchCredentials();

      // Should return the endpoint
      expect(credentials.endpoint).toBe(settings.powersyncUrl);
      
      // Should have called getOrCreateDeviceId
      expect(deviceIdModule.getOrCreateDeviceId).toHaveBeenCalled();
      
      // Should return a valid JWT (three base64url parts separated by dots)
      const parts = credentials.token.split('.');
      expect(parts).toHaveLength(3);
      
      // Decode and verify header
      const header = JSON.parse(atob(parts[0].replace(/-/g, '+').replace(/_/g, '/')));
      expect(header.alg).toBe('HS256');
      expect(header.kid).toBe('powersync');
      
      // Decode and verify payload has required claims
      const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
      expect(payload.sub).toBe('mock-device-uuid');
      expect(payload.iat).toBeDefined();
      expect(payload.exp).toBeDefined();
      expect(payload.exp).toBeGreaterThan(payload.iat);
    });
  });

  describe('uploadData', () => {
    it('should return early when no transaction is pending', async () => {
      const settings = createMockSettings();
      const mockClient = createMockSupabaseClient();
      mockedCreateClient.mockReturnValue(mockClient as any);

      const connector = new Connector(settings);
      const mockDatabase = createMockDatabase(null);

      await connector.uploadData(mockDatabase as any);

      expect(mockDatabase.getNextCrudTransaction).toHaveBeenCalled();
      expect(mockClient.from).not.toHaveBeenCalled();
    });

    it('should process PUT operation successfully', async () => {
      const settings = createMockSettings();
      const mockClient = createMockSupabaseClient({ error: null });
      mockedCreateClient.mockReturnValue(mockClient as any);

      const crudEntry = createMockCrudEntry({ op: 'PUT' });
      const mockTransaction = createMockTransaction([crudEntry]);
      const mockDatabase = createMockDatabase(mockTransaction);

      const connector = new Connector(settings);
      await connector.uploadData(mockDatabase as any);

      expect(mockClient.from).toHaveBeenCalledWith('eventsV9');
      expect(mockTransaction.complete).toHaveBeenCalled();
    });

    it('should process PATCH operation successfully', async () => {
      const settings = createMockSettings();
      const mockResult = { error: null };
      const mockEq = jest.fn().mockResolvedValue(mockResult);
      const mockUpdate = jest.fn().mockReturnValue({ eq: mockEq });
      const mockClient = {
        from: jest.fn().mockReturnValue({
          update: mockUpdate,
        }),
      };
      mockedCreateClient.mockReturnValue(mockClient as any);

      const crudEntry = createMockCrudEntry({ op: 'PATCH' });
      const mockTransaction = createMockTransaction([crudEntry]);
      const mockDatabase = createMockDatabase(mockTransaction);

      const connector = new Connector(settings);
      await connector.uploadData(mockDatabase as any);

      expect(mockUpdate).toHaveBeenCalledWith(crudEntry.opData);
      expect(mockEq).toHaveBeenCalledWith('id', crudEntry.id);
      expect(mockTransaction.complete).toHaveBeenCalled();
    });

    it('should process DELETE operation successfully', async () => {
      const settings = createMockSettings();
      const mockResult = { error: null };
      const mockEq = jest.fn().mockResolvedValue(mockResult);
      const mockDelete = jest.fn().mockReturnValue({ eq: mockEq });
      const mockClient = {
        from: jest.fn().mockReturnValue({
          delete: mockDelete,
        }),
      };
      mockedCreateClient.mockReturnValue(mockClient as any);

      const crudEntry = createMockCrudEntry({ op: 'DELETE' });
      const mockTransaction = createMockTransaction([crudEntry]);
      const mockDatabase = createMockDatabase(mockTransaction);

      const connector = new Connector(settings);
      await connector.uploadData(mockDatabase as any);

      expect(mockDelete).toHaveBeenCalled();
      expect(mockEq).toHaveBeenCalledWith('id', crudEntry.id);
      expect(mockTransaction.complete).toHaveBeenCalled();
    });

    it('should retry on transient error and succeed', async () => {
      const settings = createMockSettings();
      let callCount = 0;
      const mockUpsert = jest.fn().mockImplementation(() => {
        callCount++;
        if (callCount === 1) {
          return Promise.resolve({ error: { code: '500', message: 'Server error' } });
        }
        return Promise.resolve({ error: null });
      });
      const mockClient = {
        from: jest.fn().mockReturnValue({ upsert: mockUpsert }),
      };
      mockedCreateClient.mockReturnValue(mockClient as any);

      const crudEntry = createMockCrudEntry({ op: 'PUT' });
      const mockTransaction = createMockTransaction([crudEntry]);
      const mockDatabase = createMockDatabase(mockTransaction);

      const connector = new Connector(settings);
      await connector.uploadData(mockDatabase as any);

      expect(mockUpsert).toHaveBeenCalledTimes(2);
      expect(mockTransaction.complete).toHaveBeenCalled();
    });

    it('should save failed operation and complete transaction on fatal error (23xxx)', async () => {
      const settings = createMockSettings();
      const mockUpsert = jest.fn().mockResolvedValue({
        error: { code: '23505', message: 'Unique constraint violation' },
      });
      const mockClient = {
        from: jest.fn().mockReturnValue({ upsert: mockUpsert }),
      };
      mockedCreateClient.mockReturnValue(mockClient as any);

      const crudEntry = createMockCrudEntry({ op: 'PUT' });
      const mockTransaction = createMockTransaction([crudEntry]);
      const mockDatabase = createMockDatabase(mockTransaction);

      const connector = new Connector(settings);
      await connector.uploadData(mockDatabase as any);

      // Should save the failed operation
      expect(mockedAsyncStorage.setItem).toHaveBeenCalled();
      // Should complete transaction to unblock queue
      expect(mockTransaction.complete).toHaveBeenCalled();
    });

    it('should throw on transient error after max retries', async () => {
      const settings = createMockSettings();
      const mockUpsert = jest.fn().mockResolvedValue({
        error: { code: '500', message: 'Server error' },
      });
      const mockClient = {
        from: jest.fn().mockReturnValue({ upsert: mockUpsert }),
      };
      mockedCreateClient.mockReturnValue(mockClient as any);

      const crudEntry = createMockCrudEntry({ op: 'PUT' });
      const mockTransaction = createMockTransaction([crudEntry]);
      const mockDatabase = createMockDatabase(mockTransaction);

      const connector = new Connector(settings);
      
      await expect(connector.uploadData(mockDatabase as any)).rejects.toBeTruthy();

      // Should have retried 3 times
      expect(mockUpsert).toHaveBeenCalledTimes(3);
      // Should NOT complete transaction (let PowerSync retry later)
      expect(mockTransaction.complete).not.toHaveBeenCalled();
    });
  });
});

describe('subscribeSyncLogs', () => {
  it('should subscribe to sync logs and receive events', async () => {
    const listener = jest.fn();
    const unsubscribe = subscribeSyncLogs(listener);

    // Trigger a log by saving a failed operation
    await saveFailedOperation({
      id: 'test-1',
      table: 'eventsV9',
      op: 'PUT',
      opData: {},
      recordId: 'record-1',
      error: 'Test error',
      errorCode: '23505',
      timestamp: Date.now(),
    });

    expect(listener).toHaveBeenCalled();
    const logEntry = listener.mock.calls[0][0] as SyncLogEntry;
    expect(logEntry.level).toBe('warn');
    expect(logEntry.message).toContain('Failed operation saved');

    unsubscribe();
  });

  it('should stop receiving events after unsubscribe', async () => {
    const listener = jest.fn();
    const unsubscribe = subscribeSyncLogs(listener);
    unsubscribe();

    await saveFailedOperation({
      id: 'test-2',
      table: 'eventsV9',
      op: 'PUT',
      opData: {},
      recordId: 'record-2',
      error: 'Test error',
      errorCode: '23505',
      timestamp: Date.now(),
    });

    expect(listener).not.toHaveBeenCalled();
  });
});

describe('Failed Operations Storage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAsyncStorage.getItem.mockResolvedValue(null);
    mockedAsyncStorage.setItem.mockResolvedValue(undefined);
    mockedAsyncStorage.removeItem.mockResolvedValue(undefined);
  });

  describe('getFailedOperations', () => {
    it('should return empty array when no operations stored', async () => {
      mockedAsyncStorage.getItem.mockResolvedValue(null);

      const result = await getFailedOperations();

      expect(result).toEqual([]);
    });

    it('should return parsed operations from storage', async () => {
      const storedOps: FailedOperation[] = [
        {
          id: 'op-1',
          table: 'eventsV9',
          op: 'PUT',
          opData: { title: 'Test' },
          recordId: 'rec-1',
          error: 'Error message',
          errorCode: '23505',
          timestamp: 1234567890,
        },
      ];
      mockedAsyncStorage.getItem.mockResolvedValue(JSON.stringify(storedOps));

      const result = await getFailedOperations();

      expect(result).toEqual(storedOps);
    });

    it('should return empty array on parse error', async () => {
      mockedAsyncStorage.getItem.mockResolvedValue('invalid json');

      const result = await getFailedOperations();

      expect(result).toEqual([]);
    });
  });

  describe('saveFailedOperation', () => {
    it('should save operation to storage', async () => {
      const op: FailedOperation = {
        id: 'op-1',
        table: 'eventsV9',
        op: 'PUT',
        opData: { title: 'Test' },
        recordId: 'rec-1',
        error: 'Error message',
        errorCode: '23505',
        timestamp: 1234567890,
      };

      await saveFailedOperation(op);

      expect(mockedAsyncStorage.setItem).toHaveBeenCalledWith(
        '@powersync_failed_operations',
        expect.stringContaining('op-1')
      );
    });

    it('should prepend new operation to existing ones', async () => {
      const existingOp: FailedOperation = {
        id: 'op-1',
        table: 'eventsV9',
        op: 'PUT',
        opData: {},
        recordId: 'rec-1',
        error: 'Old error',
        errorCode: '23505',
        timestamp: 1000,
      };
      mockedAsyncStorage.getItem.mockResolvedValue(JSON.stringify([existingOp]));

      const newOp: FailedOperation = {
        id: 'op-2',
        table: 'eventsV9',
        op: 'DELETE',
        opData: undefined,
        recordId: 'rec-2',
        error: 'New error',
        errorCode: '23503',
        timestamp: 2000,
      };

      await saveFailedOperation(newOp);

      const savedData = JSON.parse(mockedAsyncStorage.setItem.mock.calls[0][1]);
      expect(savedData[0].id).toBe('op-2'); // New one first
      expect(savedData[1].id).toBe('op-1'); // Old one second
    });

    it('should cap storage at 50 operations', async () => {
      const existingOps: FailedOperation[] = Array.from({ length: 50 }, (_, i) => ({
        id: `op-${i}`,
        table: 'eventsV9',
        op: 'PUT',
        opData: {},
        recordId: `rec-${i}`,
        error: 'Error',
        errorCode: '23505',
        timestamp: i,
      }));
      mockedAsyncStorage.getItem.mockResolvedValue(JSON.stringify(existingOps));

      const newOp: FailedOperation = {
        id: 'op-new',
        table: 'eventsV9',
        op: 'PUT',
        opData: {},
        recordId: 'rec-new',
        error: 'New error',
        errorCode: '23505',
        timestamp: 9999,
      };

      await saveFailedOperation(newOp);

      const savedData = JSON.parse(mockedAsyncStorage.setItem.mock.calls[0][1]);
      expect(savedData.length).toBe(50);
      expect(savedData[0].id).toBe('op-new');
      expect(savedData[49].id).toBe('op-48'); // op-49 should be dropped
    });
  });

  describe('removeFailedOperation', () => {
    it('should remove operation by id', async () => {
      const ops: FailedOperation[] = [
        { id: 'op-1', table: 'eventsV9', op: 'PUT', opData: {}, recordId: 'rec-1', error: 'E', errorCode: '23505', timestamp: 1 },
        { id: 'op-2', table: 'eventsV9', op: 'PUT', opData: {}, recordId: 'rec-2', error: 'E', errorCode: '23505', timestamp: 2 },
      ];
      mockedAsyncStorage.getItem.mockResolvedValue(JSON.stringify(ops));

      await removeFailedOperation('op-1');

      const savedData = JSON.parse(mockedAsyncStorage.setItem.mock.calls[0][1]);
      expect(savedData.length).toBe(1);
      expect(savedData[0].id).toBe('op-2');
    });

    it('should handle non-existent id gracefully', async () => {
      const ops: FailedOperation[] = [
        { id: 'op-1', table: 'eventsV9', op: 'PUT', opData: {}, recordId: 'rec-1', error: 'E', errorCode: '23505', timestamp: 1 },
      ];
      mockedAsyncStorage.getItem.mockResolvedValue(JSON.stringify(ops));

      await removeFailedOperation('non-existent');

      const savedData = JSON.parse(mockedAsyncStorage.setItem.mock.calls[0][1]);
      expect(savedData.length).toBe(1);
    });
  });

  describe('clearFailedOperations', () => {
    it('should remove all failed operations from storage', async () => {
      await clearFailedOperations();

      expect(mockedAsyncStorage.removeItem).toHaveBeenCalledWith('@powersync_failed_operations');
    });
  });
});

describe('Error Classification', () => {
  // These tests verify error classification behavior indirectly through uploadData

  it('should treat 22xxx errors as fatal (data exception)', async () => {
    const settings = createMockSettings();
    const mockUpsert = jest.fn().mockResolvedValue({
      error: { code: '22001', message: 'String data right truncation' },
    });
    const mockClient = {
      from: jest.fn().mockReturnValue({ upsert: mockUpsert }),
    };
    mockedCreateClient.mockReturnValue(mockClient as any);

    const mockTransaction = createMockTransaction([createMockCrudEntry()]);
    const mockDatabase = createMockDatabase(mockTransaction);

    const connector = new Connector(settings);
    await connector.uploadData(mockDatabase as any);

    // Fatal error: no retries, transaction completed, operation saved
    expect(mockUpsert).toHaveBeenCalledTimes(1);
    expect(mockTransaction.complete).toHaveBeenCalled();
    expect(mockedAsyncStorage.setItem).toHaveBeenCalled();
  });

  it('should treat 42501 as fatal (insufficient privilege)', async () => {
    const settings = createMockSettings();
    const mockUpsert = jest.fn().mockResolvedValue({
      error: { code: '42501', message: 'Insufficient privilege' },
    });
    const mockClient = {
      from: jest.fn().mockReturnValue({ upsert: mockUpsert }),
    };
    mockedCreateClient.mockReturnValue(mockClient as any);

    const mockTransaction = createMockTransaction([createMockCrudEntry()]);
    const mockDatabase = createMockDatabase(mockTransaction);

    const connector = new Connector(settings);
    await connector.uploadData(mockDatabase as any);

    // Fatal error: no retries
    expect(mockUpsert).toHaveBeenCalledTimes(1);
    expect(mockTransaction.complete).toHaveBeenCalled();
  });

  it('should treat network errors as retryable', async () => {
    const settings = createMockSettings();
    let callCount = 0;
    const mockUpsert = jest.fn().mockImplementation(() => {
      callCount++;
      if (callCount === 1) {
        return Promise.reject(new Error('Network request failed'));
      }
      return Promise.resolve({ error: null });
    });
    const mockClient = {
      from: jest.fn().mockReturnValue({ upsert: mockUpsert }),
    };
    mockedCreateClient.mockReturnValue(mockClient as any);

    const mockTransaction = createMockTransaction([createMockCrudEntry()]);
    const mockDatabase = createMockDatabase(mockTransaction);

    const connector = new Connector(settings);
    await connector.uploadData(mockDatabase as any);

    // Network error retried, then succeeded
    expect(mockUpsert).toHaveBeenCalledTimes(2);
    expect(mockTransaction.complete).toHaveBeenCalled();
  });
});

describe('Log Filter Level (UI display preference)', () => {
  beforeEach(() => {
    setLogFilterLevel('info');
  });

  describe('getLogFilterLevel', () => {
    it('should return default filter level as info', () => {
      setLogFilterLevel('info');
      expect(getLogFilterLevel()).toBe('info');
    });
  });

  describe('setLogFilterLevel', () => {
    it('should update filter level to debug', () => {
      setLogFilterLevel('debug');
      expect(getLogFilterLevel()).toBe('debug');
    });

    it('should update filter level to firehose', () => {
      setLogFilterLevel('firehose');
      expect(getLogFilterLevel()).toBe('firehose');
    });

    it('should update filter level back to info', () => {
      setLogFilterLevel('firehose');
      setLogFilterLevel('info');
      expect(getLogFilterLevel()).toBe('info');
    });
  });

  describe('filter level type', () => {
    it('should accept valid LogFilterLevel values', () => {
      const validLevels: LogFilterLevel[] = ['info', 'debug', 'firehose'];
      
      validLevels.forEach(level => {
        setLogFilterLevel(level);
        expect(getLogFilterLevel()).toBe(level);
      });
    });
  });
});

