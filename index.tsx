// Entry point for React Native
import 'react-native-devsettings';
import './global.css';
import './lib/env';

import React from 'react';
import { AppRegistry, useColorScheme, Text } from 'react-native';
import { NavigationContainer, DefaultTheme, DarkTheme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { enableScreens } from 'react-native-screens';

// Enable native screens for performance
enableScreens();
import { TouchableOpacity, BackHandler } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { PowerSyncContext } from '@powersync/react';
import { db as psDb } from './lib/powersync';
import { setupPowerSyncLogCapture } from './lib/powersync/Connector';
import { SettingsProvider } from './lib/hooks/SettingsContext';
import { SyncDebugProvider } from './lib/hooks/SyncDebugContext';
import { ThemeProvider } from './lib/theme/ThemeContext';
import { getColors } from './lib/theme/colors';
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

function BackButton({ onPress, color, hasRightHeader }: { onPress: () => void; color: string; hasRightHeader?: boolean }) {
  return (
    <TouchableOpacity 
      onPress={onPress}
      hitSlop={{ top: 15, bottom: 15, left: 15, right: 15 }}
      style={{ padding: 8, marginLeft: hasRightHeader ? 8 : -8 }}
    >
      <Ionicons name="arrow-back" size={24} color={color} />
    </TouchableOpacity>
  );
}

function App() {
  const colorScheme = useColorScheme();
  const isDark = colorScheme === 'dark';
  const colors = getColors(isDark);

  // Custom navigation theme
  const navigationTheme = isDark ? {
    ...DarkTheme,
    colors: {
      ...DarkTheme.colors,
      primary: colors.primary,
      background: colors.background,
      card: colors.backgroundWhite,
      text: colors.text,
      border: colors.border,
    },
  } : {
    ...DefaultTheme,
    colors: {
      ...DefaultTheme.colors,
      primary: colors.primary,
      background: colors.background,
      card: colors.backgroundWhite,
      text: colors.text,
      border: colors.border,
    },
  };

  return (
    <ThemeProvider>
      <SettingsProvider>
        <SyncDebugProvider>
          <PowerSyncContext.Provider value={psDb}>
            <NavigationContainer theme={navigationTheme}>
              <Stack.Navigator
                screenOptions={{
                  headerStyle: { backgroundColor: colors.backgroundWhite },
                  headerTintColor: colors.primary,
                  headerTitleStyle: { color: colors.text },
                  headerBackVisible: false,
                }}
              >
                <Stack.Screen
                  name="Home"
                  component={HomeScreen}
                  options={({ navigation }) => ({
                    title: 'Sync Info',
                    headerLeft: () => (
                      <BackButton onPress={() => BackHandler.exitApp()} color={colors.primary} hasRightHeader />
                    ),
                    headerRight: () => (
                      <TouchableOpacity 
                        onPress={() => navigation.navigate('Settings')}
                        hitSlop={{ top: 15, bottom: 15, left: 15, right: 15 }}
                        style={{ padding: 8, marginRight: 4 }}
                      >
                        <Text style={{ fontSize: 24, color: colors.primary }}>⋮</Text>
                      </TouchableOpacity>
                    ),
                  })}
                />
                <Stack.Screen
                  name="Settings"
                  component={SettingsScreen}
                  options={({ navigation }) => ({
                    title: 'Sync Settings',
                    headerLeft: () => (
                      <BackButton onPress={() => navigation.goBack()} color={colors.primary} />
                    ),
                  })}
                />
                <Stack.Screen
                  name="SyncDebug"
                  component={SyncDebugScreen}
                  options={({ navigation }) => ({
                    title: 'Sync Debug',
                    headerLeft: () => (
                      <BackButton onPress={() => navigation.goBack()} color={colors.primary} />
                    ),
                  })}
                />
              </Stack.Navigator>
            </NavigationContainer>
          </PowerSyncContext.Provider>
        </SyncDebugProvider>
      </SettingsProvider>
    </ThemeProvider>
  );
}

AppRegistry.registerComponent('CNPlusSync', () => App);
