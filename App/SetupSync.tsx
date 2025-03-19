import React, { useContext, useEffect, useState } from 'react';
import { StyleSheet, Text, View, Button, TouchableOpacity } from 'react-native';
import { hello, MyModuleView, setValueAsync, addChangeListener } from '../modules/my-module';
import { open } from '@op-engineering/op-sqlite';
import { useQuery } from '@powersync/react';
import { PowerSyncContext } from "@powersync/react";
import { installCrsqliteOnTable } from '@lib/cr-sqlite/install';
import { psInsertDbTable } from '@lib/orm';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { RootStackParamList } from './index';
import { useStoredSettings } from '../lib/hooks/useStoredSettings';

type NavigationProp = NativeStackNavigationProp<RootStackParamList>;

export const SetupSync = () => {
  const navigation = useNavigation<NavigationProp>();
  const { storedSettings } = useStoredSettings();
  const debugDisplayKeys = ['id', 'ttl', 'loc'];

  const numEventsToDisplay = 3;

  const debugDisplayQuery = `select ${debugDisplayKeys.join(', ')} from eventsV9 limit ${numEventsToDisplay}`;

  const { data: psEvents } = useQuery<string>(debugDisplayQuery);
  const [sqliteEvents, setSqliteEvents] = useState<any[]>([]);
  const [tempTableEvents, setTempTableEvents] = useState<any[]>([]);
  const [dbStatus, setDbStatus] = useState<string>('');
  const [lastUpdate, setLastUpdate] = useState<string>('');
  const regDb = open({ name: 'Events' }); 
  const providerDb = useContext(PowerSyncContext);

  useEffect(() => {
    (async () => {
      if (storedSettings.syncEnabled && storedSettings.syncType === 'bidirectional') {
        await installCrsqliteOnTable('Events', 'eventsV9');
      }

      // Query events from SQLite
      const result = await regDb.execute(debugDisplayQuery);
      if (result?.rows) {
        setSqliteEvents(result.rows || []);
      }

      // Query temp table events only in bidirectional mode
      if (storedSettings.syncEnabled && storedSettings.syncType === 'bidirectional') {
        const tempResult = await regDb.execute(debugDisplayQuery);
        if (tempResult?.rows) {
          setTempTableEvents(tempResult.rows || []);
        }
      }

    })();

    addChangeListener((value) => {
      console.log(hello());
      console.log('value changed', value);
    })

    // Set up interval to check PowerSync status
    const statusInterval = setInterval(() => {
      setDbStatus(JSON.stringify(providerDb.currentStatus));
      setLastUpdate(new Date().toLocaleTimeString());
    }, 1000);

    // Cleanup interval on component unmount
    return () => clearInterval(statusInterval);
  }, [storedSettings.syncEnabled, storedSettings.syncType]);

  const handleSync = async () => {
    if (!providerDb || !storedSettings.syncEnabled) return;
    
    try {
      await psInsertDbTable('Events', 'eventsV9', providerDb);
      // Refresh the events display after sync
      const result = await regDb.execute(debugDisplayQuery);
      if (result?.rows) {
        setSqliteEvents(result.rows || []);
      }
    } catch (error) {
      console.error('Failed to sync data:', error);
    }
  };

  if (!storedSettings.supabaseUrl || !storedSettings.supabaseAnonKey || !storedSettings.powersyncUrl || !storedSettings.powersyncToken) {
    return (
      <View style={styles.container}>
        <Text style={styles.hello}>PowerSync not configured</Text>
        <Text style={styles.subtext}>Please configure your sync settings to continue</Text>
        <Button 
          title="Go to Settings" 
          onPress={() => navigation.navigate('Settings')}
        />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.hello}>PowerSync Status: {dbStatus}</Text>
      <Text style={styles.hello}>Last Updated: {lastUpdate}</Text>

      <Text style={styles.hello}> Sample Local SQLite Events eventsV9: {JSON.stringify(sqliteEvents)}</Text>
      
      <Text style={styles.hello}> Sample PowerSync Remote Events: {JSON.stringify(psEvents)}</Text>

      {storedSettings.syncEnabled && storedSettings.syncType === 'bidirectional' && (
        <Text style={styles.hello}>Events V9 Temp Table: {JSON.stringify(tempTableEvents)}</Text>
      )}

      <TouchableOpacity 
        style={styles.syncButton}
        onPress={handleSync}
      >
        {/* TODO: move to background job instead of buttton  */}
        <Text style={styles.syncButtonText}>Sync Events Now</Text>
      </TouchableOpacity>

      {/* TODO: this native module can be used to communicate with the kolin code */}
      {/* I want to use it to get things like the mute status of a notification  */}
      {/* or whatever other useful things. so dont delete it so I remember to use it later  */}

      {/* <MyModuleView name="MyModuleView" />
      <Button
        title="Click me!"
        onPress={async () => {
          await setValueAsync('blarf');
        }}
      ></Button> */}

    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  hello: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
  settingsButton: {
    padding: 8,
  },
  settingsButtonText: {
    fontSize: 16,
    color: '#007AFF',
  },
  subtext: {
    textAlign: 'center',
    margin: 10,
  },
  syncButton: {
    backgroundColor: '#007AFF',
    padding: 15,
    borderRadius: 8,
    marginTop: 20,
    marginHorizontal: 20,
  },
  syncButtonText: {
    color: '#fff',
    fontSize: 16,
    textAlign: 'center',
    fontWeight: '600',
  },
}); 