import type { AbstractPowerSyncDatabase } from '@powersync/react-native';
import { emitSyncLog } from '../logging/syncLog';
import { getPendingCrudCount } from '../orm';
import { isSettingsConfigured, loadStoredSettings } from '../hooks/settingsStorage';
import type { Settings } from '../hooks/SettingsContext';
import { getFailedOperations } from './Connector';
import { db, setupPowerSync } from './index';
import { reportBackgroundSyncOutcome } from '../../../modules/my-module';

/** Must match TASK_KEY in SyncForegroundService.kt */
export const BACKGROUND_SYNC_TASK = 'CNPlusBackgroundSync';

/** What the task needs to know about the upload queue */
export interface UploadQueue {
  isUploading(): boolean;
  pendingCount(): Promise<number>;
  /** Returns a function that removes the listener */
  onStatusChanged(listener: () => void): () => void;
}

/**
 * Everything the task touches outside its own logic: the upload queue, PowerSync,
 * stored settings, and the native service. The headless task passes the real ones;
 * tests pass fakes.
 */
export interface BackgroundSyncEnvironment {
  queue: UploadQueue;
  /** False when the service cold-started the app, so nothing has connected PowerSync yet */
  isConnectStarted(): boolean;
  loadSettings(): Promise<Settings | null>;
  connect(settings: Settings): Promise<void>;
  /** Id of the most recent op the server rejected, to tell whether this run added one */
  newestFailedOpId(): Promise<string | undefined>;
  /** Tells the service how the task ended; without it the service records a timeout */
  reportOutcome(ok: boolean, error?: string): Promise<void>;
}

/**
 * Resolves once nothing is queued and nothing is uploading. Re-checks on every
 * PowerSync status change rather than polling.
 */
export const waitForDrain = (queue: UploadQueue): Promise<void> =>
  new Promise((resolve, reject) => {
    let unsubscribe = () => {};
    const check = async () => {
      try {
        if ((await queue.pendingCount()) > 0 || queue.isUploading()) return;
        unsubscribe();
        resolve();
      } catch (error) {
        unsubscribe();
        reject(error);
      }
    };
    unsubscribe = queue.onStatusChanged(check);
    check();
  });

/**
 * Every way the task can end. The service adds one more it alone can see: the
 * native timeout, recorded as paused.
 */
export type BackgroundSyncOutcome =
  | { kind: 'complete' }
  | { kind: 'notConfigured' }
  /** Rejected ops are dropped from the queue, so it drains even though they never uploaded */
  | { kind: 'changesRejected' }
  | { kind: 'error'; message: string };

/** What gets logged and shown for an outcome; undefined when the sync completed */
export const describeFailure = (outcome: BackgroundSyncOutcome): string | undefined => {
  switch (outcome.kind) {
    case 'complete':
      return undefined;
    case 'notConfigured':
      return 'Sync is not configured';
    case 'changesRejected':
      return 'Some changes were rejected by the server. See Sync Debug for details.';
    case 'error':
      return outcome.message;
  }
};

/** Connects if needed, then waits for the upload queue to drain */
const connectAndDrain = async (environment: BackgroundSyncEnvironment): Promise<BackgroundSyncOutcome> => {
  if (!environment.isConnectStarted()) {
    const settings = await environment.loadSettings();
    if (!settings?.syncEnabled || !isSettingsConfigured(settings)) {
      return { kind: 'notConfigured' };
    }
    await environment.connect(settings);
  }
  const failedBefore = await environment.newestFailedOpId();
  await waitForDrain(environment.queue);
  return (await environment.newestFailedOpId()) === failedBefore
    ? { kind: 'complete' }
    : { kind: 'changesRejected' };
};

/**
 * Runs the sync and reports how it ended. Always resolves: React Native never
 * reports a rejected headless task as finished, which would leave the service
 * running until its timeout.
 */
export const runBackgroundSync = async (environment: BackgroundSyncEnvironment): Promise<void> => {
  let outcome: BackgroundSyncOutcome;
  try {
    outcome = await connectAndDrain(environment);
  } catch (error) {
    outcome = { kind: 'error', message: error instanceof Error ? error.message : String(error) };
  }
  const failure = describeFailure(outcome);
  if (failure) {
    emitSyncLog('error', 'Background sync failed', { outcome: outcome.kind, error: failure });
  } else {
    emitSyncLog('info', 'Background sync complete — upload queue drained');
  }
  try {
    await environment.reportOutcome(outcome.kind === 'complete', failure);
  } catch (error) {
    emitSyncLog('error', 'Failed to report the background sync outcome', { error });
  }
};

const powerSyncQueue = (psDb: AbstractPowerSyncDatabase): UploadQueue => ({
  isUploading: () => Boolean(psDb.currentStatus?.dataFlowStatus?.uploading),
  pendingCount: () => getPendingCrudCount(psDb),
  onStatusChanged: listener => psDb.registerListener({ statusChanged: listener }),
});

// About two minutes on the Dev page: long enough to press Home and watch it keep going
const DEV_PAGE_FAKE_QUEUE_OPS = 24;
const DEV_PAGE_FAKE_QUEUE_TICK_MS = 5000;

/**
 * A fake queue that drains one op per tick. The Dev page uses it to exercise the
 * service with no backend involved.
 */
export const createDevPageFakeQueue = (
  ops = DEV_PAGE_FAKE_QUEUE_OPS,
  tickMs = DEV_PAGE_FAKE_QUEUE_TICK_MS
): UploadQueue => {
  let pending = ops;
  const listeners = new Set<() => void>();
  const timer = setInterval(() => {
    pending--;
    emitSyncLog('info', `Dev page fake upload queue: ${pending} pending`);
    if (pending === 0) clearInterval(timer);
    listeners.forEach(listener => listener());
  }, tickMs);
  return {
    isUploading: () => false,
    pendingCount: async () => pending,
    onStatusChanged: listener => {
      listeners.add(listener);
      return () => { listeners.delete(listener); };
    },
  };
};

/** The headless task SyncForegroundService runs. `devPageFakeQueue` is set by the Dev page test button. */
export const backgroundSyncTask = (data?: { devPageFakeQueue?: boolean }): Promise<void> =>
  runBackgroundSync({
    queue: data?.devPageFakeQueue ? createDevPageFakeQueue() : powerSyncQueue(db),
    isConnectStarted: () =>
      Boolean(data?.devPageFakeQueue || db.currentStatus?.connected || db.currentStatus?.connecting),
    loadSettings: loadStoredSettings,
    connect: setupPowerSync,
    newestFailedOpId: async () => (await getFailedOperations())[0]?.id,
    reportOutcome: reportBackgroundSyncOutcome,
  });
