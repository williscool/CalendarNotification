import "react-native-devsettings";
import React, { useContext, useEffect, useState } from 'react';
import { AppRegistry, StyleSheet, Text, View, Button } from 'react-native';
import Constants from 'expo-constants';
import { hello, PI, MyModuleView, setValueAsync, addChangeListener } from './modules/my-module';
import { db as psDb, setupPowerSync } from '@lib/powersync';
import { open } from '@op-engineering/op-sqlite';
import Logger from 'js-logger';
import { useQuery } from '@powersync/react';
import { PowerSyncContext } from "@powersync/react";
import { installCrsqliteOnTable } from '@lib/cr-sqlite/install';
import { ConfigObj } from '@lib/config';

console.log(Constants.systemFonts);
console.log(PI);
console.log(MyModuleView);
console.log(setValueAsync);

const HelloWorld = () => {
  const { data: psEvents } = useQuery<string>('select id, nid, ttl from eventsV9');
  const [sqliteEvents, setSqliteEvents] = useState<any[]>([]);
  const [tempTableEvents, setTempTableEvents] = useState<any[]>([]);
  const [dbStatus, setDbStatus] = useState<string>('');
  const [lastUpdate, setLastUpdate] = useState<string>('');
  const regDb = open({ name: 'Events' });

  useEffect(() => {
    (async () => {
      if (ConfigObj.sync.type === 'bidirectional') {
        await installCrsqliteOnTable('Events', 'eventsV9');
      }

      // Query events from SQLite
      const result = await regDb.execute('SELECT id, nid, ttl FROM eventsV9');
      if (result?.rows) {
        setSqliteEvents(result.rows || []);
      }
      // Query temp table events
      const tempResult = await regDb.execute('SELECT id, nid, ttl FROM eventsV9_temp');
      if (tempResult?.rows) {
        setTempTableEvents(tempResult.rows || []);
      }

    })();

    addChangeListener((value) => {
      console.log(hello());
      console.log('value changed', value);
    })

    // Set up interval to check PowerSync status
    const statusInterval = setInterval(() => {
      setDbStatus(JSON.stringify(psDb.currentStatus));
      setLastUpdate(new Date().toLocaleTimeString());
    }, 1000);

    // Cleanup interval on component unmount
    return () => clearInterval(statusInterval);
  }, []);


  // need the value of the PowerSyncContext.Provider
  const providerDb = useContext(PowerSyncContext);
  // console.log('providerDb', providerDb);

  useEffect(() => {
    if (!providerDb) return;

    const insertData = async (tableName: string) => {
      try {
        // convert fullTableEvents to array of arrays
        const fullTableResult = await regDb.execute(`SELECT * FROM ${tableName}`);
        const fullTableEvents = fullTableResult?.rows || [];
        // for each key in fullTableEvents, convert the value to an array
        const fullTableEventsKeys = Object.keys(fullTableEvents[0]);
        console.log('fullTableEventsKeys', fullTableEventsKeys);
        const fullTableEventsArray = fullTableEvents.map(event => fullTableEventsKeys.map(key => event[key]));
        console.log('fullTableEventsArray', fullTableEventsArray);

        const powerSyncInsertQuery = `INSERT OR REPLACE INTO ${tableName} (${fullTableEventsKeys.join(', ')}) VALUES (${fullTableEventsKeys.map(() => '?').join(', ')})`;
        console.log('powerSyncInsertQuery', powerSyncInsertQuery);

        const powerSyncInsertResult = await providerDb.executeBatch(powerSyncInsertQuery, fullTableEventsArray);

        console.log('powerSyncInsertResult', powerSyncInsertResult);
      } catch (error) {
        console.error('Failed to insert data:', error);
      }
    };

    insertData('eventsV9');
  }, [providerDb]); // Only re-run if providerDb changes

  return (
    <View style={styles.container}>
      <Text style={styles.hello}>Hello, World</Text>
      <Text style={styles.hello}>PowerSync Status: {dbStatus}</Text>
      <Text style={styles.hello}>Last Updated: {lastUpdate}</Text>
      <Text style={styles.hello}>PowerSync Events: {JSON.stringify(psEvents)}</Text>

      <Text style={styles.hello}>SQLite Events eventsV9: {JSON.stringify(sqliteEvents)}</Text>
      <Text style={styles.hello}>Events V9 Temp Table: {JSON.stringify(tempTableEvents)}</Text>
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

const App = () => {
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
      <HelloWorld />
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

AppRegistry.registerComponent(
  'MyReactNativeApp',
  () => App,
);

