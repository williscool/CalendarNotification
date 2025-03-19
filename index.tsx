import "react-native-devsettings";
import { AppRegistry } from 'react-native';
import Constants from 'expo-constants';
import { PI, MyModuleView, setValueAsync } from './modules/my-module';
import { App } from './App';

console.log(Constants.systemFonts);
console.log(PI);
console.log(MyModuleView);
console.log(setValueAsync);

AppRegistry.registerComponent(
  'CNPlusSync',
  () => App,
);

