import React, { useEffect, useState } from 'react';
import { StyleSheet, Text, View, Button, TouchableOpacity, BackHandler, Linking } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { db as psDb } from '@lib/powersync';
import { setupRemoteDatabaseConnections } from '@lib/init_remote_db_connections';
import Logger from 'js-logger';
import { PowerSyncContext } from "@powersync/react";
import { SetupSync } from './SetupSync';
import { Settings } from './Settings';
import { enableScreens } from 'react-native-screens';
import { useSettings } from '@lib/hooks/SettingsContext';
import { Ionicons } from '@expo/vector-icons';
import { SettingsProvider } from '@lib/hooks/SettingsContext';
import { GITHUB_README_URL } from '@lib/constants';

// Enable screens
enableScreens();

export type RootStackParamList = {
  Home: undefined;
  Settings: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

const InitialSetupScreen = ({ navigation }: { navigation: NativeStackNavigationProp<RootStackParamList> }) => (
  <View style={styles.container}>
    <Text style={styles.hello}>Sync is not enabled</Text>
    <Text style={styles.subtext}>Please enable sync in settings to continue</Text>
    <Button 
      title="Go to Settings" 
      onPress={() => navigation.navigate('Settings')}
    />
    <Text style={[styles.subtext, { marginTop: 20 }]}>
      or view our
    </Text>
    <Text   
      style={[styles.hello, styles.link]}
      onPress={() => Linking.openURL(GITHUB_README_URL)}
    >
      Setup Guide
    </Text>
  </View>
);

const HomeScreen = ({ navigation }: { navigation: NativeStackNavigationProp<RootStackParamList> }) => {
  const { settings } = useSettings();
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    const init = async () => {
      if (settings.syncEnabled) {
        await setupRemoteDatabaseConnections(settings, psDb);
      }
      setIsReady(true);
    };
    init();
  }, [settings.syncEnabled, settings]);

  if (!isReady) {
    return (
      <View style={styles.container}>
        <Text style={styles.hello}>Initializing...</Text>
      </View>
    );
  }

  return settings.syncEnabled ? <SetupSync /> : <InitialSetupScreen navigation={navigation} />;
};

export const App = () => {
  Logger.useDefaults();
  Logger.setLevel(Logger.DEBUG);

  return (
    <SettingsProvider>
      <PowerSyncContext.Provider value={psDb}>
        <NavigationContainer>
          <Stack.Navigator 
            initialRouteName="Home"
            screenOptions={{
              headerShown: true,
              headerBackTitle: 'Back',
            }}
          >
            <Stack.Screen 
              name="Home" 
              component={HomeScreen}
              options={({ navigation }) => ({
                title: 'Sync Info',
                headerLeft: () => (
                  <TouchableOpacity 
                    onPress={() => BackHandler.exitApp()}
                  >
                    <Ionicons name="arrow-back" size={24} color="#007AFF" style={{ marginRight: 20 }} />
                  </TouchableOpacity>
                ),
                headerRight: () => (
                  <TouchableOpacity 
                    onPress={() => navigation.navigate('Settings')}
                    style={{ marginRight: 15 }}
                  >
                    <Ionicons name="ellipsis-vertical" size={24} color="#007AFF" />
                  </TouchableOpacity>
                ),
              })}
            />
            <Stack.Screen 
              name="Settings" 
              component={Settings}
              options={{ title: 'Sync Settings' }}
            />
          </Stack.Navigator>
        </NavigationContainer>
      </PowerSyncContext.Provider>
    </SettingsProvider>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  hello: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
  subtext: {
    fontSize: 16,
    textAlign: 'center',
    marginBottom: 20,
    color: '#666',
  },
  link: {
    color: '#007AFF',
    textDecorationLine: 'underline',
  },
}); 