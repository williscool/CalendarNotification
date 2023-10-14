import "react-native-devsettings";
import React, {useEffect} from 'react';
import {AppRegistry, StyleSheet, Text, View, Button} from 'react-native';
import Constants from 'expo-constants';
import { hello, PI, MyModuleView, setValueAsync, addChangeListener } from './modules/my-module';


console.log(Constants.systemFonts);
console.log(PI);
console.log(MyModuleView);
console.log(setValueAsync);


const HelloWorld = () => {

  useEffect(() => {
    addChangeListener((value) => {
      console.log(hello());
      console.log('value changed', value);
    })
  }, []);
  

  return (
    <View style={styles.container}>
      <Text style={styles.hello}>Hello, World</Text>
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