import React, { useEffect, useState } from 'react';
import { StyleSheet, Text, View, Button } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { db as psDb, setupPowerSync } from '@lib/powersync';
import Logger from 'js-logger';
import { PowerSyncContext } from "@powersync/react";
import { SetupSync } from './SetupSync';
import { Settings } from './Settings';
import { enableScreens } from 'react-native-screens';
import { useStoredSettings } from '@lib/hooks/useStoredSettings';

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
  </View>
);

const HomeScreen = ({ navigation }: { navigation: NativeStackNavigationProp<RootStackParamList> }) => {
  const { storedSettings } = useStoredSettings();
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    const init = async () => {
      if (storedSettings.syncEnabled) {
        await setupPowerSync(storedSettings);
      }
      setIsReady(true);
    };
    init();
  }, [storedSettings.syncEnabled, storedSettings]);

  if (!isReady) {
    return (
      <View style={styles.container}>
        <Text style={styles.hello}>Initializing...</Text>
      </View>
    );
  }

  return storedSettings.syncEnabled ? <SetupSync /> : <InitialSetupScreen navigation={navigation} />;
};

export const App = () => {
  Logger.useDefaults();
  Logger.setLevel(Logger.INFO);

  return (
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
            options={{ title: 'Sync Setup' }}
          />
          <Stack.Screen 
            name="Settings" 
            component={Settings}
            options={{ title: 'Settings' }}
          />
        </Stack.Navigator>
      </NavigationContainer>
    </PowerSyncContext.Provider>
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
}); 