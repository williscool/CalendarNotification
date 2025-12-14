// Entry point for React Native
import 'react-native-devsettings';
import './lib/env';

import React from 'react';
import { AppRegistry } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { TouchableOpacity, BackHandler, Text } from 'react-native';
import { GluestackUIProvider } from '@gluestack-ui/themed';
import { config } from '@gluestack-ui/config';
import { PowerSyncContext } from '@powersync/react';
import { db as psDb } from './lib/powersync';
import { setupPowerSyncLogCapture } from './lib/powersync/Connector';
import { SettingsProvider } from './lib/hooks/SettingsContext';
import { SyncDebugProvider } from './lib/hooks/SyncDebugContext';
import Logger from 'js-logger';

// Screens
import HomeScreen from './app/index';
import SettingsScreen from './app/settings';
import SyncDebugScreen from './app/sync-debug';

// Initialize logger
Logger.useDefaults();
Logger.setLevel(Logger.DEBUG);
setupPowerSyncLogCapture();

const Stack = createNativeStackNavigator();

function App() {
  return (
    <GluestackUIProvider config={config}>
      <SettingsProvider>
        <SyncDebugProvider>
          <PowerSyncContext.Provider value={psDb}>
            <NavigationContainer>
              <Stack.Navigator>
                <Stack.Screen
                  name="Home"
                  component={HomeScreen}
                  options={({ navigation }) => ({
                    title: 'Sync Info',
                    headerLeft: () => (
                      <TouchableOpacity onPress={() => BackHandler.exitApp()}>
                        <Text style={{ marginRight: 20, color: '#007AFF', fontSize: 24 }}>←</Text>
                      </TouchableOpacity>
                    ),
                    headerRight: () => (
                      <TouchableOpacity onPress={() => navigation.navigate('Settings')}>
                        <Text style={{ color: '#007AFF', fontSize: 24 }}>⋮</Text>
                      </TouchableOpacity>
                    ),
                  })}
                />
                <Stack.Screen
                  name="Settings"
                  component={SettingsScreen}
                  options={{ title: 'Sync Settings' }}
                />
                <Stack.Screen
                  name="SyncDebug"
                  component={SyncDebugScreen}
                  options={{ title: 'Sync Debug' }}
                />
              </Stack.Navigator>
            </NavigationContainer>
          </PowerSyncContext.Provider>
        </SyncDebugProvider>
      </SettingsProvider>
    </GluestackUIProvider>
  );
}

AppRegistry.registerComponent('CNPlusSync', () => App);
