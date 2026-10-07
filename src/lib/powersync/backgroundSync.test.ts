/**
 * Background sync task tests.
 * Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
 */

// The real module builds the PowerSync database on import
jest.mock('./index', () => ({ db: {}, setupPowerSync: jest.fn() }));
jest.mock('../logging/syncLog', () => ({ emitSyncLog: jest.fn() }));
jest.mock('../../../modules/my-module', () => ({
  reportBackgroundSyncOutcome: jest.fn(),
  reportBackgroundSyncProgress: jest.fn(),
}));

import { emitSyncLog } from '../logging/syncLog';
import type { Settings } from '../hooks/SettingsContext';
import {
  BackgroundSyncEnvironment,
  UploadQueue,
  createDevPageFakeQueue,
  runBackgroundSync,
  waitForDrain,
} from './backgroundSync';

const configuredSettings: Settings = {
  syncEnabled: true,
  syncType: 'unidirectional',
  supabaseUrl: 'https://example.supabase.co',
  supabaseAnonKey: 'anon-key-123',
  powersyncUrl: 'https://example.powersync.com',
  powersyncSecret: 'token123',
};

/** A queue whose state the test sets directly; `emit` stands in for a PowerSync status change */
const createFakeQueue = (pending: number, uploading = false) => {
  const listeners = new Set<() => void>();
  const state = { pending, uploading };
  const queue: UploadQueue = {
    isUploading: () => state.uploading,
    pendingCount: async () => state.pending,
    onStatusChanged: listener => {
      listeners.add(listener);
      return () => { listeners.delete(listener); };
    },
  };
  return { queue, state, listeners, emit: () => listeners.forEach(listener => listener()) };
};

/** A zero-delay timeout only fires once every queued microtask has run */
const AFTER_QUEUED_MICROTASKS_MS = 0;

/** Lets every pending promise continuation run before the test asserts, however deep the chain */
const flushPromises = () => new Promise<void>(resolve => setTimeout(resolve, AFTER_QUEUED_MICROTASKS_MS));

/** Tracks whether a promise has settled without awaiting it */
const track = (promise: Promise<void>) => {
  const result = { done: false };
  promise.then(() => { result.done = true; });
  return result;
};

const createEnvironment = (overrides: Partial<BackgroundSyncEnvironment> = {}): BackgroundSyncEnvironment => ({
  queue: createFakeQueue(0).queue,
  isConnectStarted: () => true,
  loadSettings: jest.fn(async () => configuredSettings),
  connect: jest.fn(async () => {}),
  newestFailedOpId: jest.fn(async () => undefined),
  reportOutcome: jest.fn(async () => {}),
  inFlightUploaded: () => 0,
  onUploadProgress: () => () => {},
  reportProgress: jest.fn(async () => {}),
  currentOperation: () => null,
  ...overrides,
});

describe('waitForDrain', () => {
  it('resolves immediately when the queue is empty', async () => {
    await expect(waitForDrain(createFakeQueue(0).queue)).resolves.toBeUndefined();
  });

  it('resolves once the queue drains', async () => {
    const fake = createFakeQueue(2);
    const result = track(waitForDrain(fake.queue));

    fake.state.pending = 1;
    fake.emit();
    await flushPromises();
    expect(result.done).toBe(false);

    fake.state.pending = 0;
    fake.emit();
    await flushPromises();
    expect(result.done).toBe(true);
  });

  it('stays pending while an upload is in flight even if the count is zero', async () => {
    const fake = createFakeQueue(0, true);
    const result = track(waitForDrain(fake.queue));
    await flushPromises();
    expect(result.done).toBe(false);

    fake.state.uploading = false;
    fake.emit();
    await flushPromises();
    expect(result.done).toBe(true);
  });

  it('stops listening once drained', async () => {
    const fake = createFakeQueue(0);
    await waitForDrain(fake.queue);
    expect(fake.listeners.size).toBe(0);
  });

  it('rejects and stops listening when the count cannot be read', async () => {
    const fake = createFakeQueue(1);
    fake.queue.pendingCount = async () => { throw new Error('db closed'); };

    await expect(waitForDrain(fake.queue)).rejects.toThrow('db closed');
    expect(fake.listeners.size).toBe(0);
  });
});

describe('runBackgroundSync', () => {
  it('does not connect when a connection was already started', async () => {
    const environment = createEnvironment();
    await runBackgroundSync(environment);

    expect(environment.connect).not.toHaveBeenCalled();
    expect(environment.reportOutcome).toHaveBeenCalledWith(true, undefined);
  });

  it('connects with the stored settings on a cold start, then drains', async () => {
    const fake = createFakeQueue(1);
    const environment = createEnvironment({ queue: fake.queue, isConnectStarted: () => false });
    const result = track(runBackgroundSync(environment));
    await flushPromises();

    expect(environment.connect).toHaveBeenCalledWith(configuredSettings);
    expect(result.done).toBe(false);

    expect(environment.reportOutcome).not.toHaveBeenCalled();

    fake.state.pending = 0;
    fake.emit();
    await flushPromises();
    expect(result.done).toBe(true);
    expect(environment.reportOutcome).toHaveBeenCalledWith(true, undefined);
  });

  it.each([
    ['no settings are stored', null],
    ['sync is disabled', { ...configuredSettings, syncEnabled: false }],
    ['credentials are missing', { ...configuredSettings, powersyncSecret: '' }],
  ])('reports failure without connecting when %s', async (_name, settings) => {
    const fake = createFakeQueue(3);
    const environment = createEnvironment({
      queue: fake.queue,
      isConnectStarted: () => false,
      loadSettings: async () => settings as Settings | null,
    });
    await runBackgroundSync(environment);

    expect(environment.connect).not.toHaveBeenCalled();
    expect(fake.listeners.size).toBe(0);
    expect(environment.reportOutcome).toHaveBeenCalledWith(false, 'Sync is not configured');
  });

  it('resolves and reports the error when connecting fails', async () => {
    const environment = createEnvironment({
      isConnectStarted: () => false,
      connect: async () => { throw new Error('network down'); },
    });

    await expect(runBackgroundSync(environment)).resolves.toBeUndefined();
    expect(environment.reportOutcome).toHaveBeenCalledWith(false, 'network down');
  });

  it('reports failure when the server rejected an op during the run', async () => {
    const newestFailedOpId = jest.fn()
      .mockResolvedValueOnce('older-failure')
      .mockResolvedValueOnce('eventsV9-42-1700000000000');
    const environment = createEnvironment({ newestFailedOpId });
    await runBackgroundSync(environment);

    expect(environment.reportOutcome).toHaveBeenCalledWith(false, expect.stringContaining('rejected by the server'));
    expect(emitSyncLog).toHaveBeenCalledWith('error', 'Background sync failed', expect.objectContaining({ outcome: 'changesRejected' }));
  });

  it('reports complete when an earlier rejected op is still on record', async () => {
    const environment = createEnvironment({ newestFailedOpId: jest.fn(async () => 'older-failure') });
    await runBackgroundSync(environment);

    expect(environment.reportOutcome).toHaveBeenCalledWith(true, undefined);
  });

  it('still resolves when the outcome cannot be reported', async () => {
    const environment = createEnvironment({ reportOutcome: async () => { throw new Error('module gone'); } });

    await expect(runBackgroundSync(environment)).resolves.toBeUndefined();
    expect(emitSyncLog).toHaveBeenCalledWith('error', expect.stringContaining('report'), expect.anything());
  });
});

describe('progress reporting', () => {
  /** Stands in for the Connector's in-flight counter and its listeners */
  const createFakeUploads = () => {
    const listeners = new Set<() => void>();
    const state = { inFlight: 0 };
    return {
      state,
      listeners,
      set: (inFlight: number) => {
        state.inFlight = inFlight;
        listeners.forEach(listener => listener());
      },
      environment: {
        inFlightUploaded: () => state.inFlight,
        onUploadProgress: (listener: () => void) => {
          listeners.add(listener);
          return () => { listeners.delete(listener); };
        },
      },
    };
  };

  it('reports ops uploaded from the transaction in flight before the queue shrinks', async () => {
    const fake = createFakeQueue(4);
    const uploads = createFakeUploads();
    const environment = createEnvironment({ queue: fake.queue, ...uploads.environment });
    const result = track(runBackgroundSync(environment));
    await flushPromises();
    expect(environment.reportProgress).toHaveBeenLastCalledWith(0, 4, 4, null);

    uploads.set(1);
    expect(environment.reportProgress).toHaveBeenLastCalledWith(1, 4, 4, null);
    uploads.set(2);
    expect(environment.reportProgress).toHaveBeenLastCalledWith(2, 4, 4, null);

    // The transaction completes: its two ops leave the queue and nothing is in flight
    const reportsBeforeReset = (environment.reportProgress as jest.Mock).mock.calls.length;
    uploads.set(0);
    expect(environment.reportProgress).toHaveBeenCalledTimes(reportsBeforeReset);
    fake.state.pending = 2;
    fake.emit();
    await flushPromises();
    expect(environment.reportProgress).toHaveBeenLastCalledWith(2, 4, 2, null);

    fake.state.pending = 0;
    fake.emit();
    await flushPromises();
    expect(environment.reportProgress).toHaveBeenLastCalledWith(4, 4, 0, null);
    expect(result.done).toBe(true);
    expect(uploads.listeners.size).toBe(0);
  });

  it('reports what the op being uploaded does', async () => {
    const fake = createFakeQueue(4);
    const uploads = createFakeUploads();
    let operation: string | null = null;
    const environment = createEnvironment({ queue: fake.queue, ...uploads.environment, currentOperation: () => operation });
    track(runBackgroundSync(environment));
    await flushPromises();

    operation = 'DELETE';
    uploads.set(1);
    expect(environment.reportProgress).toHaveBeenLastCalledWith(1, 4, 4, 'DELETE');
  });

  it('never reports more done than the total when a retried transaction re-uploads', async () => {
    const fake = createFakeQueue(2);
    const uploads = createFakeUploads();
    const environment = createEnvironment({ queue: fake.queue, ...uploads.environment });
    track(runBackgroundSync(environment));
    await flushPromises();

    uploads.set(5);
    expect(environment.reportProgress).toHaveBeenLastCalledWith(2, 2, 2, null);
  });

  it('still finishes when progress cannot be reported', async () => {
    const environment = createEnvironment({ reportProgress: async () => { throw new Error('module gone'); } });

    await runBackgroundSync(environment);
    await flushPromises();
    expect(environment.reportOutcome).toHaveBeenCalledWith(true, undefined);
  });
});

describe('createDevPageFakeQueue', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it('drains one op per tick and then lets the task finish', async () => {
    const result = track(waitForDrain(createDevPageFakeQueue(2, 1000)));

    await jest.advanceTimersByTimeAsync(1000);
    expect(result.done).toBe(false);

    await jest.advanceTimersByTimeAsync(1000);
    expect(result.done).toBe(true);
    expect(jest.getTimerCount()).toBe(0);
  });
});
