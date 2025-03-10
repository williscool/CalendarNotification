import "react-native-devsettings";
import React, {useEffect, useState} from 'react';
import {AppRegistry, StyleSheet, Text, View, Button} from 'react-native';
import Constants from 'expo-constants';
import { hello, PI, MyModuleView, setValueAsync, addChangeListener } from './modules/my-module';
import { db, setupPowerSync } from '@lib/powersync';
import { open } from '@op-engineering/op-sqlite';
import Logger from 'js-logger';
import { useQuery } from '@powersync/react';
import { PowerSyncContext } from "@powersync/react";

console.log(Constants.systemFonts);
console.log(PI);
console.log(MyModuleView);
console.log(setValueAsync);

const HelloWorld = () => {
  const {data : psEvents } = useQuery<string>('select * from eventsV9'); 
  const [sqliteEvents, setSqliteEvents] = useState<any[]>([]);
  const [dbStatus, setDbStatus] = useState<string>('');
  const [lastUpdate, setLastUpdate] = useState<string>('');

  useEffect(() => {
    (async () => {
      const regDb = open({name: 'Events'});
      
      // Load cr-sqlite extension
      try {
        await regDb.execute("SELECT load_extension('crsqlite')");
        
        // Unique index on 2 columns gets error below
        // statement execution error: Table eventsV9 has unique indices besidesthe primary key. This is not allowed for CRRs
        await regDb.execute("DROP INDEX IF EXISTS eventsIdxV9");

        // add primary key
        await regDb.execute("ALTER TABLE eventsV9 ADD PRIMARY KEY id");

        // recreate index with no unique constraint
        await regDb.execute("CREATE INDEX IF NOT EXISTS eventsIdxV9 ON eventsV9 (id, istart)");
        
        // Initialize cr-sqlite
        await regDb.execute("SELECT crsql_finalize()");
        
        // Enable CRDT behavior for the table
        await regDb.execute("SELECT crsql_as_crr('eventsV9')");
      } catch (error) {
        console.error('Failed to load crsqlite extension:', error);
      }

      
      // Query events from SQLite
      const result = await regDb.execute('SELECT * FROM eventsV9 limit 1');
      if (result?.rows) {
        setSqliteEvents(result.rows || []);
      }
    })();

    addChangeListener((value) => {
      console.log(hello());
      console.log('value changed', value);
    })
    
    // Set up interval to check PowerSync status
    const statusInterval = setInterval(() => {
      setDbStatus(JSON.stringify(db.currentStatus));
      setLastUpdate(new Date().toLocaleTimeString());
    }, 1000);

    // Cleanup interval on component unmount
    return () => clearInterval(statusInterval);
  }, []);
  

  return (
    <View style={styles.container}>
      <Text style={styles.hello}>Hello, World</Text>
      <Text style={styles.hello}>PowerSync Status: {dbStatus}</Text>
      <Text style={styles.hello}>Last Updated: {lastUpdate}</Text>
      <Text style={styles.hello}>PowerSync Events: {JSON.stringify(psEvents)}</Text>
      <Text style={styles.hello}>SQLite Events: {JSON.stringify(sqliteEvents)}</Text>
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
    <PowerSyncContext.Provider value={db}>
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

