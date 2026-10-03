import { emitSyncLog } from '../logging/syncLog';

/** Must match TASK_KEY in SyncForegroundService.kt */
export const BACKGROUND_SYNC_TASK = 'CNPlusBackgroundSync';

const TICK_MS = 5000;
const TICKS = 24;

/**
 * Smoke-test placeholder: logs on a timer for two minutes, then finishes.
 * It only exists to show that JS timers keep firing while the foreground
 * service runs; the real task (drain the upload queue) replaces it.
 */
export const backgroundSyncTask = async (): Promise<void> => {
  for (let tick = 1; tick <= TICKS; tick++) {
    await new Promise(resolve => setTimeout(resolve, TICK_MS));
    emitSyncLog('info', `Background sync placeholder tick ${tick}/${TICKS}`);
  }
};
