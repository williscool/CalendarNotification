import React, { useContext, useEffect, useState } from 'react';
import { Linking } from 'react-native';
import { Box, Text, Button, ButtonText, ScrollView, VStack, Center, Pressable } from '@gluestack-ui/themed';
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
import { ActionButton, WarningBanner } from '@lib/components/ui';
import { emitSyncLog } from '@lib/logging/syncLog';

import type { RawRescheduleConfirmation } from '../../modules/my-module';

export const SetupSync = () => {
  const navigation = useNavigation<AppNavigationProp>();
  const { colors } = useTheme();
  const { settings } = useSettings();
  const debugDisplayKeys = ['id', 'ttl', 'istart' ,'loc'];
  const [showDangerZone, setShowDangerZone] = useState(false);
  const [showDebugOutput, setShowDebugOutput] = useState(false);
  const [isConnected, setIsConnected] = useState<boolean | null>(null);

  const isConfigured = Boolean(
    settings.supabaseUrl &&
    settings.supabaseAnonKey &&
    settings.powersyncUrl &&
    settings.powersyncToken
  );

  const numEventsToDisplay = 3;

  const debugDisplayQuery = `select ${debugDisplayKeys.join(', ')} from eventsV9 limit ${numEventsToDisplay}`;

  const { data: psEvents } = useQuery<string>(debugDisplayQuery);
  const { data: rawConfirmations } = useQuery<RawRescheduleConfirmation>(`select event_id, calendar_id, original_instance_start_time, title, new_instance_start_time, is_in_future, created_at, updated_at from reschedule_confirmations`);

  const [sqliteEvents, setSqliteEvents] = useState<any[]>([]);
  const [tempTableEvents, setTempTableEvents] = useState<any[]>([]);
  const [dbStatus, setDbStatus] = useState<string>('');
  const [lastUpdate, setLastUpdate] = useState<string>('');
  const regDb = open({ name: 'Events' });
  const providerDb = useContext(PowerSyncContext);

  useEffect(() => {
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

    const statusInterval = setInterval(() => {
      if (providerDb) {
        setDbStatus(JSON.stringify(providerDb.currentStatus));
        setLastUpdate(new Date().toLocaleTimeString());

        if (providerDb.currentStatus && providerDb.currentStatus.hasSynced !== undefined) {
          setIsConnected(providerDb.currentStatus.connected);
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
      <Center flex={1} p="$5" bg={colors.background}>
        <VStack space="md" alignItems="center">
          <Text fontSize="$xl" textAlign="center" color={colors.text}>PowerSync not configured</Text>
          <Text fontSize="$md" textAlign="center" color={colors.textMuted}>
            Please configure your sync settings to continue
          </Text>
          <Text fontSize="$md" textAlign="center" color={colors.textMuted}>
            For setup instructions, please visit our
          </Text>
          <Text
            fontSize="$xl"
            textAlign="center"
            color={colors.primary}
            underline
            onPress={() => Linking.openURL(GITHUB_README_URL)}
          >
            GitHub README
          </Text>
          <Text fontSize="$md" textAlign="center" color={colors.textMuted} mb="$4">
            or
          </Text>
          <Button onPress={() => navigation.navigate('Settings')} bg={colors.primary}>
            <ButtonText>Go to Settings</ButtonText>
          </Button>
        </VStack>
      </Center>
    );
  }

  return (
    <ScrollView flex={1} bg={colors.background} contentContainerStyle={{ paddingVertical: 20, paddingHorizontal: 10 }}>
      <Text fontSize="$xl" textAlign="center" m="$2.5" color={colors.text} selectable>
        PowerSync Status: {dbStatus}
      </Text>
      <Text fontSize="$xl" textAlign="center" m="$2.5" color={colors.text}>
        Last Updated: {lastUpdate}
      </Text>

      {isConnected === false && (
        <WarningBanner variant="warning">
          <Text color={colors.warningText} fontSize="$md" textAlign="center">
            ⚠️ PowerSync is not connected. Sync features are disabled.
            {'\n'}
            <Text
              color={colors.primary}
              fontWeight="$semibold"
              textDecorationLine="underline"
              onPress={() => navigation.navigate('Settings')}
            >
              Go to Settings
            </Text>
          </Text>
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
        <Box
          my="$2.5"
          p="$2.5"
          bg={colors.backgroundMuted}
          borderRadius="$lg"
          borderWidth={1}
          borderColor={colors.border}
          mx="$4"
        >
          <Text fontSize="$xl" textAlign="center" m="$2.5" color={colors.text} selectable>
            Sample Local SQLite Events eventsV9: {JSON.stringify(sqliteEvents)}
          </Text>
          <Text fontSize="$xl" textAlign="center" m="$2.5" color={colors.text} selectable>
            Sample PowerSync Remote Events: {JSON.stringify(psEvents)}
          </Text>
          <Text fontSize="$xl" textAlign="center" m="$2.5" color={colors.text} selectable>
            Sample PowerSync Remote Events reschedule_confirmations: {JSON.stringify(rawConfirmations?.slice(0, numEventsToDisplay))}
          </Text>
          {settings.syncEnabled && settings.syncType === 'bidirectional' && (
            <Text fontSize="$xl" textAlign="center" m="$2.5" color={colors.text} selectable>
              Events V9 Temp Table: {JSON.stringify(tempTableEvents)}
            </Text>
          )}
        </Box>
      )}

      <ActionButton
        onPress={handleSync}
        variant="success"
        disabled={isConnected === false}
      >
        Sync Events Local To PowerSync Now
      </ActionButton>

      <ActionButton
        onPress={() => setShowDangerZone(!showDangerZone)}
        variant={showDangerZone ? 'danger' : 'primary'}
        disabled={isConnected === false}
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
