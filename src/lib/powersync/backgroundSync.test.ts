/**
 * Background sync task tests.
 * Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
 */

// The real module builds the PowerSync database on import
jest.mock('./index', () => ({ db: {}, setupPowerSync: jest.fn() }));
jest.mock('../logging/syncLog', () => ({ emitSyncLog: jest.fn() }));

import { emitSyncLog } from '../logging/syncLog';
import type { Settings } from '../hooks/SettingsContext';
import {
  BackgroundSyncDeps,
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

const flushPromises = async () => {
  for (let i = 0; i < 5; i++) await Promise.resolve();
};

/** Tracks whether a promise has settled without awaiting it */
const track = (promise: Promise<void>) => {
  const result = { done: false };
  promise.then(() => { result.done = true; });
  return result;
};

const createDeps = (overrides: Partial<BackgroundSyncDeps> = {}): BackgroundSyncDeps => ({
  queue: createFakeQueue(0).queue,
  isConnectStarted: () => true,
  loadSettings: jest.fn(async () => configuredSettings),
  connect: jest.fn(async () => {}),
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
    const deps = createDeps();
    await runBackgroundSync(deps);

    expect(deps.connect).not.toHaveBeenCalled();
    expect(emitSyncLog).toHaveBeenCalledWith('info', expect.stringContaining('complete'));
  });

  it('connects with the stored settings on a cold start, then drains', async () => {
    const fake = createFakeQueue(1);
    const deps = createDeps({ queue: fake.queue, isConnectStarted: () => false });
    const result = track(runBackgroundSync(deps));
    await flushPromises();

    expect(deps.connect).toHaveBeenCalledWith(configuredSettings);
    expect(result.done).toBe(false);

    fake.state.pending = 0;
    fake.emit();
    await flushPromises();
    expect(result.done).toBe(true);
  });

  it.each([
    ['no settings are stored', null],
    ['sync is disabled', { ...configuredSettings, syncEnabled: false }],
    ['credentials are missing', { ...configuredSettings, powersyncSecret: '' }],
  ])('finishes without connecting when %s', async (_name, settings) => {
    const fake = createFakeQueue(3);
    const deps = createDeps({
      queue: fake.queue,
      isConnectStarted: () => false,
      loadSettings: async () => settings as Settings | null,
    });
    await runBackgroundSync(deps);

    expect(deps.connect).not.toHaveBeenCalled();
    expect(fake.listeners.size).toBe(0);
    expect(emitSyncLog).toHaveBeenCalledWith('warn', expect.stringContaining('not configured'));
  });

  it('resolves rather than rejects when connecting fails', async () => {
    const deps = createDeps({
      isConnectStarted: () => false,
      connect: async () => { throw new Error('network down'); },
    });

    await expect(runBackgroundSync(deps)).resolves.toBeUndefined();
    expect(emitSyncLog).toHaveBeenCalledWith('error', 'Background sync failed', expect.anything());
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
