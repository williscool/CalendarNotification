# Calendar Notifications Plus React Native

## Prerequisites
For the original ReadMe, see [android/README.md](android/README.md)

## Building

```bash
yarn 
yarn start

```

## Running an emulator

```bash
cd C:\Users\<username>\appdata\local\android\sdk
.\emulator\emulator.exe -avd 7.6_Fold-in_with_outer_display_API_34q
```

## Wsl setup

```bash

# get the wsl2 ip address
ifconfig eth0 | grep 'inet '

# open the developer menu in the react native activity once loaded
./adb.exe shell input keyevent 82

# input the aformentioned ip address in the developer menu
./adb.exe shell input text "172.21.60.143:8081"

```

## Debugging

WARNING: BY DEFAULT IN THE CHROME DEBUGGER Expo Module Function does not work AT ALL! https://docs.expo.dev/modules/module-api/#function 

this is because either the methods are using  @ReactMethod(isBlockingSynchronousMethod = true) 

https://reactnative.dev/docs/native-modules-android#synchronous-methods

and or JSI, a JavaScript interface for native code, is being used which requires the JS VM to share memory with the app.

For the Google Chrome debugger, React Native runs inside the JS VM in Google Chrome, and communicates asynchronously with the mobile devices via WebSockets.

So it wont have access to the native modules.

Alternative setups:

Hermes on Chrome - https://reactnative.dev/docs/hermes?package-manager=yarn#debugging-js-on-hermes-using-google-chromes-devtools
VSCode - https://marketplace.visualstudio.com/items?itemName=msjsdiag.vscode-react-native
Flipper - https://fbflipper.com/


LOST A LOT OF TIME ON THIS! so wanted to document it here.

Sources:
https://github.com/williscool/CalendarNotification/issues/13#issuecomment-1760712053
https://reactnative.dev/docs/native-modules-android?android-language=kotlin#synchronous-methods