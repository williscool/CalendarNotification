import { OPSqliteOpenFactory } from '@powersync/op-sqlite';
import { PowerSyncDatabase } from '@powersync/react-native';
import { Connector, emitSyncLog } from './Connector';
import { AppSchema } from './Schema';
import { Settings } from '../hooks/SettingsContext';

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

export const setupPowerSync = async (settings: Settings) => {
  emitSyncLog('info', 'Setting up PowerSync connection', { url: settings.powersyncUrl });
  
  // Test network connectivity first
  try {
    emitSyncLog('debug', 'Testing network connectivity...');
    const testUrl = `${settings.powersyncUrl}/sync/stream`;
    const response = await fetch(testUrl, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${settings.powersyncToken}`,
      },
    });
    emitSyncLog('info', `Network test response: ${response.status}`, {
      status: response.status,
      headers: Object.fromEntries(response.headers.entries()),
    });
    if (!response.ok) {
      const text = await response.text();
      emitSyncLog('warn', 'Network test returned error', { status: response.status, body: text });
    }
  } catch (e: unknown) {
    const error = e as Error;
    emitSyncLog('error', 'Network test FAILED', { message: error.message, stack: error.stack });
  }

  const connector = new Connector(settings);
  
  // Add status listener to debug connection issues
  db.registerListener({
    statusChanged: (status) => {
      emitSyncLog('debug', 'PowerSync status changed', status as unknown as Record<string, unknown>);
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
    emitSyncLog('error', 'Error in db.connect()', { error: String(e) });
  }

  try {
    emitSyncLog('debug', 'Calling db.init()...');
    await db.init();
    emitSyncLog('info', 'db.init() completed successfully');
  } catch (e) {
    emitSyncLog('error', 'Error in db.init()', { error: String(e) });
  }
};