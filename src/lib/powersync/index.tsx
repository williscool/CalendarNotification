import { OPSqliteOpenFactory } from '@powersync/op-sqlite';
import { PowerSyncDatabase } from '@powersync/react-native';
import { Connector, createHS256Token } from './Connector';
import { emitSyncLog } from '../logging/syncLog';
import { AppSchema } from './Schema';
import { Settings } from '../hooks/SettingsContext';
import { getOrCreateDeviceId } from './deviceId';

const factory = new OPSqliteOpenFactory({
    dbFilename: 'powerSyncEvents.db',
});

/**
 * Instantiate the local PowerSync database
 * https://github.com/powersync-ja/powersync-js/blob/%40powersync/react-native%401.18.0/packages/powersync-op-sqlite/README.md
 */
export const db = new PowerSyncDatabase({
  schema: AppSchema,
  database: factory,
});

// Track listener disposer to prevent accumulation on repeated calls
let statusListenerDisposer: (() => void) | null = null;

/**
 * Sets up PowerSync connection with the given settings.
 * @throws Error if connection or initialization fails
 */
export const setupPowerSync = async (settings: Settings): Promise<void> => {
  emitSyncLog('info', 'Setting up PowerSync connection', { url: settings.powersyncUrl });
  
  // Test network connectivity first (non-blocking, just for diagnostics)
  await testNetworkConnectivity(settings);

  const connector = new Connector(settings);
  
  // Clean up previous listener before registering new one
  if (statusListenerDisposer) {
    statusListenerDisposer();
    statusListenerDisposer = null;
  }
  
  // Add status listener to debug connection issues
  statusListenerDisposer = db.registerListener({
    statusChanged: (status) => {
      if (!status) return;
      emitSyncLog('debug', 'PowerSync status changed', { status });
      if (status.dataFlowStatus?.downloadError) {
        emitSyncLog('error', 'Download error detected', { error: status.dataFlowStatus.downloadError });
      }
    }
  });

  emitSyncLog('debug', 'Calling db.connect()...');
  try {
    await db.connect(connector);
    emitSyncLog('info', 'db.connect() completed successfully');
  } catch (e) {
    emitSyncLog('error', 'Error in db.connect()', { error: e });
    throw e;
  }

  try {
    emitSyncLog('debug', 'Calling db.init()...');
    await db.init();
    emitSyncLog('info', 'db.init() completed successfully');
  } catch (e) {
    emitSyncLog('error', 'Error in db.init()', { error: e });
    throw e;
  }
};

/**
 * Tests network connectivity to PowerSync server (non-blocking diagnostic)
 */
async function testNetworkConnectivity(settings: Settings): Promise<void> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 5000); // 5s timeout
  
  try {
    emitSyncLog('debug', 'Testing network connectivity...');
    
    // Generate a JWT for authentication (don't send raw secret!)
    const deviceId = await getOrCreateDeviceId();
    const now = Math.floor(Date.now() / 1000);
    const payload = { sub: deviceId, aud: 'powersync', iat: now, exp: now + 300 };
    const token = createHS256Token(payload, settings.powersyncSecret, 'powersync');
    
    const testUrl = `${settings.powersyncUrl}/sync/stream`;
    const response = await fetch(testUrl, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
      },
      signal: controller.signal,
    });
    
    // Immediately abort to close the streaming connection after getting status
    controller.abort();
    
    emitSyncLog('info', `Network test response: ${response.status}`, {
      status: response.status,
    });
    
    if (!response.ok) {
      emitSyncLog('warn', 'Network test returned error', { status: response.status });
    }
  } catch (e: unknown) {
    const error = e as Error;
    // Don't log abort errors - those are expected
    if (error.name !== 'AbortError') {
      emitSyncLog('error', 'Network test FAILED', { message: error.message });
    }
  } finally {
    clearTimeout(timeoutId);
  }
}