import React, { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { db as psDb, setupPowerSync } from '@lib/powersync';
import Logger from 'js-logger';
import { PowerSyncContext } from "@powersync/react";
import { SetupSync } from './SetupSync';
import { Settings } from './Settings';
import { enableScreens } from 'react-native-screens';

// Enable screens
enableScreens();

export type RootStackParamList = {
  Home: undefined;
  Settings: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export const App = () => {
  const [initialized, setInitialized] = useState(false);

  useEffect(() => {
    const init = async () => {
      await setupPowerSync();
      setInitialized(true);
    };
    init();
    Logger.useDefaults();
    Logger.setLevel(Logger.DEBUG);
  }, []);

  if (!initialized) {
    return (
      <View style={styles.container}>
        <Text style={styles.hello}>Initializing PowerSync...</Text>
      </View>
    );
  }

  return (
    <PowerSyncContext.Provider value={psDb}>
      <NavigationContainer>
        <Stack.Navigator initialRouteName="Home">
          <Stack.Screen 
            name="Home" 
            component={SetupSync}
            options={{ title: 'Calendar Notifications' }}
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
  },
  hello: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
}); 