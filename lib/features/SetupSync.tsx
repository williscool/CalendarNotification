import React, { useContext, useEffect, useState, memo } from 'react';
import { Linking, ScrollView } from 'react-native';
import { hello, sendRescheduleConfirmations, addChangeListener } from '../../modules/my-module';
import { open } from '@op-engineering/op-sqlite';
import { useQuery } from '@powersync/react';
import { PowerSyncContext } from "@powersync/react";
import { installCrsqliteOnTable } from '@lib/cr-sqlite/install';
import { psInsertDbTable, psClearTable } from '@lib/orm';
import { useNavigation } from '@react-navigation/native';
import type { AppNavigationProp } from '@lib/navigation/types';
import { useSettings } from '@lib/hooks/SettingsContext';
import { useTheme } from '@lib/theme/ThemeContext';
import { GITHUB_README_URL } from '@lib/constants';
import { ActionButton, WarningBanner, AlertText } from '@lib/components/ui';
import { emitSyncLog } from '@lib/logging/syncLog';
import { VStack, Card, Text, Link, LinkText, Button, ButtonText } from '@/components/ui';

import type { RawRescheduleConfirmation } from '../../modules/my-module';
import type { Settings } from '@lib/hooks/SettingsContext';

/** Isolated component for polling timestamp - re-renders every second without affecting parent */
const PollingTimestamp = memo(({ color }: { color: string }) => {
  const [time, setTime] = useState(new Date().toLocaleTimeString());
  
  useEffect(() => {
    const interval = setInterval(() => {
      setTime(new Date().toLocaleTimeString());
    }, 1000);
    return () => clearInterval(interval);
  }, []);
  
  return (
    <Text className="text-xl text-center m-2.5" style={{ color }}>
      Last Updated: {time}
    </Text>
  );
});

/** Check if all required sync credentials are configured */
export const isSettingsConfigured = (settings: Settings): boolean => Boolean(
  settings.supabaseUrl &&
  settings.supabaseAnonKey &&
  settings.powersyncUrl &&
  settings.powersyncToken
);

export const SetupSync = () => {
  const navigation = useNavigation<AppNavigationProp>();
  const { colors } = useTheme();
  const { settings } = useSettings();
  const debugDisplayKeys = ['id', 'ttl', 'istart' ,'loc'];
  const [showDangerZone, setShowDangerZone] = useState(false);
  const [showDebugOutput, setShowDebugOutput] = useState(false);
  const [isConnected, setIsConnected] = useState<boolean | null>(null);

  const isConfigured = isSettingsConfigured(settings);

  const numEventsToDisplay = 3;

  const debugDisplayQuery = `select ${debugDisplayKeys.join(', ')} from eventsV9 limit ${numEventsToDisplay}`;

  const { data: psEvents } = useQuery<string>(debugDisplayQuery);
  const { data: rawConfirmations } = useQuery<RawRescheduleConfirmation>(`select event_id, calendar_id, original_instance_start_time, title, new_instance_start_time, is_in_future, created_at, updated_at from reschedule_confirmations`);

  const [sqliteEvents, setSqliteEvents] = useState<any[]>([]);
  const [tempTableEvents, setTempTableEvents] = useState<any[]>([]);
  const [dbStatus, setDbStatus] = useState<string>('');
  const regDb = open({ name: 'Events' });
  const providerDb = useContext(PowerSyncContext);

  useEffect(() => {
    // If sync is disabled, treat as "not connected" state
    if (!settings.syncEnabled) {
      setIsConnected(false);
      return;
    }

    (async () => {
      if (settings.syncEnabled && settings.syncType === 'bidirectional') {
        await installCrsqliteOnTable('Events', 'eventsV9');
      }

      const result = await regDb.execute(debugDisplayQuery);
      if (result?.rows) {
        setSqliteEvents(result.rows || []);
      }

      if (settings.syncEnabled && settings.syncType === 'bidirectional') {
        const tempResult = await regDb.execute(debugDisplayQuery);
        if (tempResult?.rows) {
          setTempTableEvents(tempResult.rows || []);
        }
      }
    })();

    addChangeListener((value) => {
      emitSyncLog('debug', 'Native module change event', { hello: hello(), value });
    });

    // Track previous values to avoid unnecessary re-renders for expensive updates
    let prevStatus = '';
    let prevConnected: boolean | null = null;
    
    const statusInterval = setInterval(() => {
      if (providerDb) {
        const newStatus = JSON.stringify(providerDb.currentStatus);
        
        // Only update dbStatus if it actually changed (avoid expensive re-render)
        if (newStatus !== prevStatus) {
          prevStatus = newStatus;
          setDbStatus(newStatus);
        }

        if (providerDb.currentStatus && providerDb.currentStatus.hasSynced !== undefined) {
          const newConnected = providerDb.currentStatus.connected;
          if (newConnected !== prevConnected) {
            prevConnected = newConnected;
            setIsConnected(newConnected);
          }
        }
      }
    }, 1000);

    return () => clearInterval(statusInterval);
  }, [settings.syncEnabled, settings.syncType]);

  const handleSync = async () => {
    if (!providerDb || !settings.syncEnabled) return;

    try {
      await psInsertDbTable('Events', 'eventsV9', providerDb);
      const result = await regDb.execute(debugDisplayQuery);
      if (result?.rows) {
        setSqliteEvents(result.rows || []);
      }
    } catch (error) {
      emitSyncLog('error', 'Failed to sync data', { error });
    }
  };

  if (!isConfigured) {
    return (
      <VStack className="flex-1 p-5 justify-center items-center" style={{ backgroundColor: colors.background }}>
        <VStack space="md" className="items-center">
          <Text className="text-xl text-center" style={{ color: colors.text }}>PowerSync not configured</Text>
          <Text className="text-base text-center" style={{ color: colors.textMuted }}>
            Please configure your sync settings to continue
          </Text>
          <Text className="text-base text-center" style={{ color: colors.textMuted }}>
            For setup instructions, please visit our
          </Text>
          <Link onPress={() => Linking.openURL(GITHUB_README_URL)}>
            <LinkText size="xl">GitHub README</LinkText>
          </Link>
          <Text className="text-base text-center" style={{ color: colors.textMuted }}>
            or
          </Text>
          <Button 
            onPress={() => navigation.navigate('Settings')}
            action="primary"
            size="lg"
          >
            <ButtonText>Go to Settings</ButtonText>
          </Button>
        </VStack>
      </VStack>
    );
  }

  return (
    <ScrollView 
      className="flex-1" 
      style={{ backgroundColor: colors.background }}
      contentContainerStyle={{ paddingVertical: 20, paddingHorizontal: 10 }}
    >
      <Text className="text-xl text-center m-2.5" style={{ color: colors.text }} selectable>
        PowerSync Status: {dbStatus}
      </Text>
      <PollingTimestamp color={colors.text} />

      {isConnected === false && (
        <WarningBanner variant="warning">
          <AlertText className="text-center">
            ⚠️ PowerSync is not connected. Sync features are disabled.
          </AlertText>
          <Link onPress={() => navigation.navigate('Settings')} className="mt-1">
            <LinkText bold>Go to Settings</LinkText>
          </Link>
        </WarningBanner>
      )}

      {isConnected === null && (
        <WarningBanner variant="info" message="⏳ PowerSync is initializing... Please wait." />
      )}

      <ActionButton
        onPress={() => setShowDebugOutput(!showDebugOutput)}
        variant="secondary"
      >
        {showDebugOutput ? 'Hide Debug Data' : 'Show Debug Data'}
      </ActionButton>

      {showDebugOutput && (
        <Card
          variant="outline"
          className="my-2.5 p-2.5 mx-4"
          style={{ backgroundColor: colors.backgroundMuted }}
        >
          <VStack space="sm">
            <Text className="text-sm text-center" style={{ color: colors.text }} selectable>
              Sample Local SQLite Events eventsV9: {JSON.stringify(sqliteEvents)}
            </Text>
            <Text className="text-sm text-center" style={{ color: colors.text }} selectable>
              Sample PowerSync Remote Events: {JSON.stringify(psEvents)}
            </Text>
            <Text className="text-sm text-center" style={{ color: colors.text }} selectable>
              Sample PowerSync Remote Events reschedule_confirmations: {JSON.stringify(rawConfirmations?.slice(0, numEventsToDisplay))}
            </Text>
            {settings.syncEnabled && settings.syncType === 'bidirectional' && (
              <Text className="text-sm text-center" style={{ color: colors.text }} selectable>
                Events V9 Temp Table: {JSON.stringify(tempTableEvents)}
              </Text>
            )}
          </VStack>
        </Card>
      )}

      <ActionButton
        onPress={handleSync}
        variant="success"
        disabled={!isConnected}
        testID="sync-button"
      >
        Sync Events Local To PowerSync Now
      </ActionButton>

      <ActionButton
        onPress={() => setShowDangerZone(!showDangerZone)}
        variant={showDangerZone ? 'danger' : 'primary'}
        disabled={!isConnected}
        testID="danger-zone-button"
      >
        {showDangerZone ? '🔒 Hide Danger Zone' : '⚠️ Show Danger Zone'}
      </ActionButton>

      {showDangerZone && isConnected !== false && (
        <>
          <WarningBanner variant="error" message="⚠️ WARNING: This will dismiss potentially many events from your local device! You can restore them from the bin." />

          <ActionButton
            onPress={async () => {
              if (rawConfirmations) {
                await sendRescheduleConfirmations(rawConfirmations);
              }
            }}
            variant="warning"
          >
            Send Reschedule Confirmations
          </ActionButton>

          <WarningBanner variant="error" message="⚠️ WARNING: This will only delete events from the remote PowerSync database. Your local device events will remain unchanged." />

          <ActionButton
            onPress={async () => {
              try {
                await psClearTable('eventsV9', providerDb);
              } catch (error) {
                emitSyncLog('error', 'Failed to clear PowerSync events', { error });
              }
            }}
            variant="danger"
          >
            Clear Remote PowerSync Events
          </ActionButton>
        </>
      )}
    </ScrollView>
  );
};
