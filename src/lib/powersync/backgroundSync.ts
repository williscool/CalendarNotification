import type { AbstractPowerSyncDatabase } from '@powersync/react-native';
import { emitSyncLog } from '../logging/syncLog';
import { getPendingCrudCount } from '../orm';
import { isSettingsConfigured, loadStoredSettings } from '../hooks/settingsStorage';
import type { Settings } from '../hooks/SettingsContext';
import { db, setupPowerSync } from './index';

/** Must match TASK_KEY in SyncForegroundService.kt */
export const BACKGROUND_SYNC_TASK = 'CNPlusBackgroundSync';

/** What the task needs to know about the upload queue */
export interface UploadQueue {
  isUploading(): boolean;
  pendingCount(): Promise<number>;
  /** Returns a function that removes the listener */
  onStatusChanged(listener: () => void): () => void;
}

export interface BackgroundSyncDeps {
  queue: UploadQueue;
  /** False when the service cold-started the app, so nothing has connected PowerSync yet */
  isConnectStarted(): boolean;
  loadSettings(): Promise<Settings | null>;
  connect(settings: Settings): Promise<void>;
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
 * Connects if needed, then waits for the upload queue to drain. Always resolves:
 * React Native never reports a rejected headless task as finished, which would
 * leave the service running until its timeout.
 */
export const runBackgroundSync = async (deps: BackgroundSyncDeps): Promise<void> => {
  try {
    if (!deps.isConnectStarted()) {
      const settings = await deps.loadSettings();
      if (!settings?.syncEnabled || !isSettingsConfigured(settings)) {
        emitSyncLog('warn', 'Background sync skipped — sync is not configured');
        return;
      }
      await deps.connect(settings);
    }
    await waitForDrain(deps.queue);
    emitSyncLog('info', 'Background sync complete — upload queue drained');
  } catch (error) {
    emitSyncLog('error', 'Background sync failed', { error });
  }
};

const powerSyncQueue = (psDb: AbstractPowerSyncDatabase): UploadQueue => ({
  isUploading: () => Boolean(psDb.currentStatus?.dataFlowStatus?.uploading),
  pendingCount: () => getPendingCrudCount(psDb),
  onStatusChanged: listener => psDb.registerListener({ statusChanged: listener }),
});

/**
 * A fake queue that drains one op per tick. The Dev page uses it to exercise the
 * service with no backend involved.
 */
export const createDevPageFakeQueue = (ops = 24, tickMs = 5000): UploadQueue => {
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
  });
