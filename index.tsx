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
  const {data : psEvents } = useQuery<string>('select id, nid, ttl from eventsV9'); 
  const [sqliteEvents, setSqliteEvents] = useState<any[]>([]);
  const [tempTableEvents, setTempTableEvents] = useState<any[]>([]);
  const [dbStatus, setDbStatus] = useState<string>('');
  const [lastUpdate, setLastUpdate] = useState<string>('');

  useEffect(() => {
    (async () => {
      const regDb = open({name: 'Events'});
      
      // Load cr-sqlite extension
      try {
        await regDb.execute("SELECT load_extension('crsqlite')");
        
        // Check if eventsV9 exists before creating temp table
        const tableExists = await regDb.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='eventsV9'");
        console.log('tableExists', tableExists?.rows);

        if (tableExists?.rows?.length > 0) {
            // Unique index on 2 columns gets error below
            // statement execution error: Table eventsV9 has unique indices besides the primary key. This is not allowed for CRRs
            await regDb.execute("DROP INDEX IF EXISTS eventsIdxV9");
            await regDb.execute("CREATE TABLE IF NOT EXISTS eventsV9_temp AS SELECT * FROM eventsV9");

            const result = await regDb.execute("select id, Count(*) from eventsV9_temp group by id");
            console.log("num events in temp table:", result?.rows?.length);

            // Get schema for eventsV9 
            const eventsV9Schema = await regDb.execute("SELECT sql FROM sqlite_master WHERE type='table' AND name='eventsV9'");
            console.log('original eventsV9 schema:', eventsV9Schema?.rows);

            const newSchemaStatementLines = (eventsV9Schema?.rows?.[0]?.sql as string).split('\n')

            const idIndex = newSchemaStatementLines.findIndex(i => i === "  id INT," || i === "  id INTEGER,")
            newSchemaStatementLines[idIndex] = "  id INT NOT NULL PRIMARY KEY,"

            const newSchema = newSchemaStatementLines.join('\n')
            .replace(", PRIMARY KEY (id, istart)", "")

            console.log('new Crsql compatible schema:', newSchema);
            
            // Drop eventsV9 table with old schema
            await regDb.execute("DROP TABLE IF EXISTS eventsV9");

            // Create eventsV9 table with schema 
            await regDb.execute(newSchema);
            
            try {
              await regDb.execute(`INSERT OR REPLACE INTO eventsV9 SELECT * FROM eventsV9_temp`);
            } catch (error) {
              console.error('Failed to insert/update data from temp table:', error);
            }
        } 
        
        // recreate index with no unique constraint
        await regDb.execute("CREATE INDEX IF NOT EXISTS eventsIdxV9 ON eventsV9 (id, istart)");
        
        // Enable CRDT behavior for the table
        await regDb.execute("SELECT crsql_as_crr('eventsV9')");
      } catch (error) {
        console.error('Failed to load crsqlite extension:', error);
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

