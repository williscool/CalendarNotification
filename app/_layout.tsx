// Initialize dev settings and environment
import "react-native-devsettings";
import '../lib/env';

import React from 'react';
import { Stack } from 'expo-router';
import { TouchableOpacity, BackHandler } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { db as psDb } from '@lib/powersync';
import { setupPowerSyncLogCapture } from '@lib/powersync/Connector';
import Logger from 'js-logger';
import { PowerSyncContext } from "@powersync/react";
import { SettingsProvider } from '@lib/hooks/SettingsContext';
import { SyncDebugProvider } from '@lib/hooks/SyncDebugContext';
import { GluestackUIProvider } from '@gluestack-ui/themed';
import { config } from '@gluestack-ui/config';
import { useRouter } from 'expo-router';

import '../global.css';

// Initialize logger
Logger.useDefaults();
Logger.setLevel(Logger.DEBUG);

// Capture PowerSync SDK logs for the debug UI
setupPowerSyncLogCapture();

export default function RootLayout() {
  const router = useRouter();

  return (
    <GluestackUIProvider config={config}>
      <SettingsProvider>
        <SyncDebugProvider>
          <PowerSyncContext.Provider value={psDb}>
            <Stack
              screenOptions={{
                headerShown: true,
                headerBackTitle: 'Back',
              }}
            >
              <Stack.Screen
                name="index"
                options={{
                  title: 'Sync Info',
                  headerLeft: () => (
                    <TouchableOpacity onPress={() => BackHandler.exitApp()}>
                      <Ionicons name="arrow-back" size={24} color="#007AFF" style={{ marginRight: 20 }} />
                    </TouchableOpacity>
                  ),
                  headerRight: () => (
                    <TouchableOpacity
                      onPress={() => router.push('/settings')}
                      style={{ marginRight: 15 }}
                    >
                      <Ionicons name="ellipsis-vertical" size={24} color="#007AFF" />
                    </TouchableOpacity>
                  ),
                }}
              />
              <Stack.Screen
                name="settings"
                options={{ title: 'Sync Settings' }}
              />
              <Stack.Screen
                name="sync-debug"
                options={{ title: 'Sync Debug' }}
              />
            </Stack>
          </PowerSyncContext.Provider>
        </SyncDebugProvider>
      </SettingsProvider>
    </GluestackUIProvider>
  );
}

