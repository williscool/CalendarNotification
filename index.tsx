import "react-native-devsettings";
import React, { useEffect, useState } from 'react';
import { AppRegistry, StyleSheet, Text, View, Button } from 'react-native';
import Constants from 'expo-constants';
import { hello, PI, MyModuleView, setValueAsync, addChangeListener } from './modules/my-module';
import { Example } from "./src/electric/Example";
import { StorageAccessFramework } from 'expo-file-system';

import { decode as atob, encode as btoa } from 'base-64'

if (!global.btoa) {
  global.btoa = btoa;
}

if (!global.atob) {
  global.atob = atob;
}

type AppState = {
  permissionGranted: boolean;
}




const isHermes = () => !!global.HermesInternal;
console.log("Is Hermes enabled " + isHermes())

console.log(Constants.systemFonts);
console.log(PI);
console.log(MyModuleView);
console.log(setValueAsync);



const HelloWorld = () => {

  const [state, setState] = useState<AppState>({
    permissionGranted: false,
  });

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
          // Requests permissions for external directory
          const permissions = await StorageAccessFramework.requestDirectoryPermissionsAsync();

          if (permissions.granted) {
            // Gets SAF URI from response
            const uri = permissions.directoryUri;

            // Gets all files inside of selected directory
            const files = await StorageAccessFramework.readDirectoryAsync(uri);
            console.log(`Files inside ${uri}:\n\n${JSON.stringify(files)}`);

            setState({ permissionGranted: true });
          }
        }}
      ></Button>
      {<Example />}
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