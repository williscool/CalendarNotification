import React, { useContext, useEffect, useState } from 'react';
import { StyleSheet, Text, View, Button } from 'react-native';
import { hello, MyModuleView, setValueAsync, addChangeListener } from '../modules/my-module';
import { open } from '@op-engineering/op-sqlite';
import { useQuery } from '@powersync/react';
import { PowerSyncContext } from "@powersync/react";
import { installCrsqliteOnTable } from '@lib/cr-sqlite/install';
import { ConfigObj } from '@lib/config';
import { psInsertDbTable } from '@lib/orm';

export const SetupSync = () => {

  const debugDisplayKeys = ['id', 'ttl', 'loc'];
  const debugDisplayQuery = `select ${debugDisplayKeys.join(', ')} from eventsV9`;

  const { data: psEvents } = useQuery<string>(debugDisplayQuery);
  const [sqliteEvents, setSqliteEvents] = useState<any[]>([]);
  const [tempTableEvents, setTempTableEvents] = useState<any[]>([]);
  const [dbStatus, setDbStatus] = useState<string>('');
  const [lastUpdate, setLastUpdate] = useState<string>('');
  const regDb = open({ name: 'Events' }); 
  const providerDb = useContext(PowerSyncContext);

  useEffect(() => {
    (async () => {
      if (ConfigObj.sync.type === 'bidirectional') {
        await installCrsqliteOnTable('Events', 'eventsV9');
      }

      // Query events from SQLite
      const result = await regDb.execute(debugDisplayQuery);
      if (result?.rows) {
        setSqliteEvents(result.rows || []);
      }

      // Query temp table events only in bidirectional mode
      if (ConfigObj.sync.type === 'bidirectional') {
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
  }, []);

  useEffect(() => {
    if (!providerDb) return;

    const syncData = async () => {
      try {
        await psInsertDbTable('Events', 'eventsV9', providerDb);
      } catch (error) {
        console.error('Failed to sync data:', error);
      }
    };

    (async () => {
      await syncData();
    })();
  }, [providerDb]); // Only re-run if providerDb changes

  return (
    <View style={styles.container}>
      <Text style={styles.hello}>Hello, World</Text>
      <Text style={styles.hello}>PowerSync Status: {dbStatus}</Text>
      <Text style={styles.hello}>Last Updated: {lastUpdate}</Text>
      <Text style={styles.hello}>PowerSync Events: {JSON.stringify(psEvents)}</Text>

      <Text style={styles.hello}>SQLite Events eventsV9: {JSON.stringify(sqliteEvents)}</Text>
      {ConfigObj.sync.type === 'bidirectional' && (
        <Text style={styles.hello}>Events V9 Temp Table: {JSON.stringify(tempTableEvents)}</Text>
      )}
      <MyModuleView name="MyModuleView" />
      <Button
        title="Click me!"
        onPress={async () => {
          await setValueAsync('blarf');
        }}
      ></Button>
    </View>
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