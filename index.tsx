import React from 'react';
import {AppRegistry, StyleSheet, Text, View, NativeModules, Button} from 'react-native';
import CalendarModule from './src/CalendarModule';

const NewModuleButton = () => {
  const onPress = () => {
    console.log(NativeModules);
    console.log(CalendarModule);
    CalendarModule.createCalendarEvent('testName', 'testLocation');
  };

  return (
    <Button
      title="Click to invoke your native module!"
      color="#841584"
      onPress={onPress}
    />
  );
};

const HelloWorld = () => {
  return (
    <View style={styles.container}>
      <Text style={styles.hello}>Hello, World</Text>
      <NewModuleButton />
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