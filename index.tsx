import "react-native-devsettings";
import React, {useEffect, useState} from 'react';
import {AppRegistry, StyleSheet, Text, View, Button} from 'react-native';
import Constants from 'expo-constants';
import { hello, PI, MyModuleView, setValueAsync, addChangeListener } from './modules/my-module';
import { setupPowerSync } from '@lib/powersync';
import { useQuery } from '@powersync/react-native';

console.log(Constants.systemFonts);
console.log(PI);
console.log(MyModuleView);
console.log(setValueAsync);


const HelloWorld = () => {

  const {data : events } = useQuery<string>('select * from eventsV9'); 

  useEffect(() => {
    (async () => {
      await setupPowerSync();
    })();

    addChangeListener((value) => {
      console.log(hello());
      console.log('value changed', value);
    })
  }, []);
  

  return (
    <View style={styles.container}>
      <Text style={styles.hello}>Hello, World</Text>
      <Text style={styles.hello}>{JSON.stringify(events)}</Text>
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

