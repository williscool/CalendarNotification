import React, { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { db as psDb, setupPowerSync } from '@lib/powersync';
import Logger from 'js-logger';
import { PowerSyncContext } from "@powersync/react";
import { SetupSync } from './SetupSync';

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
      <SetupSync />
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