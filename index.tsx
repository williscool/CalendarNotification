import "react-native-devsettings";
import React, {useEffect, useState} from 'react';
import {AppRegistry, StyleSheet, Text, View, Button} from 'react-native';
import Constants from 'expo-constants';
import { hello, PI, MyModuleView, setValueAsync, addChangeListener } from './modules/my-module';
import { db, setupPowerSync } from '@lib/powersync';
import { open } from '@op-engineering/op-sqlite';
import Logger from 'js-logger';


console.log(Constants.systemFonts);
console.log(PI);
console.log(MyModuleView);
console.log(setValueAsync);

const HelloWorld = () => {
  // const {data : events } = useQuery<string>('select * from eventsV9'); 
  const [psEvents, setPsEvents] = useState<any[]>([]);
  const [sqliteEvents, setSqliteEvents] = useState<any[]>([]);
  const [dbStatus, setDbStatus] = useState<string>('');
  const [lastUpdate, setLastUpdate] = useState<string>('');

  useEffect(() => {
    (async () => {
      await setupPowerSync();

      const psEvents = await db.getAll('SELECT * from eventsV9 limit 1') 

      console.log('db status', db.currentStatus);

      setPsEvents(psEvents);

      const regDb = open({name: 'Events'});
      
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
  () => HelloWorld,
);

