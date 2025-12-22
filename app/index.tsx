import React, { useEffect } from 'react';
import { setupPowerSync } from '@lib/powersync';
import { useSettings } from '@lib/hooks/SettingsContext';
import { SetupSync } from '@lib/features/SetupSync';
import { emitSyncLog } from '@lib/logging/syncLog';

export default function HomeScreen() {
  const { settings } = useSettings();

  // Start PowerSync in background - SetupSync handles connection status display
  useEffect(() => {
    if (settings.syncEnabled) {
      setupPowerSync(settings).catch(e => {
        emitSyncLog('error', 'PowerSync setup failed', { error: e });
      });
    }
  }, [settings]);

  return <SetupSync />;
}
