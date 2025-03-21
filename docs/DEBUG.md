# Debugging Guide

This guide covers various debugging scenarios and solutions for Calendar Notifications Plus development.

## Chrome Debugger Limitations

⚠️ **IMPORTANT WARNING: BY DEFAULT IN THE CHROME DEBUGGER Expo Module Functions DO NOT WORK AT ALL!**

(This should be in big bold red letters in the Expo documentation but isn't!)

This issue occurs because:
1. Methods using `@ReactMethod(isBlockingSynchronousMethod = true)`
2. JSI (JavaScript Interface) usage for native code which requires shared memory between JS VM and app

The Chrome debugger runs React Native inside its JS VM and communicates with mobile devices via WebSockets, meaning:
- No direct access to native modules
- No shared memory between JS VM and app
- Synchronous native methods cannot function

We lost a lot of time on this issue! See:
- [Original Issue Discussion](https://github.com/williscool/CalendarNotification/issues/13#issuecomment-1760712053)
- [React Native Docs on Synchronous Methods](https://reactnative.dev/docs/native-modules-android?android-language=kotlin#synchronous-methods)

## Hermes Debugging

⚠️ **CRITICAL WARNING: HERMES DEBUGGING DOES NOT WORK IN DEVELOPMENT MODE!**

You MUST build your Android app in RELEASE mode for Hermes debugging to work. While the official documentation mentions needing a release build, it doesn't explicitly state that it won't work in development mode at all!

### Alternative Debugging Solutions

1. **Hermes on Chrome** (Release mode only)
   - [Official Documentation](https://reactnative.dev/docs/hermes?package-manager=yarn#debugging-js-on-hermes-using-google-chromes-devtools)
   - Current development mode efforts:
     - [react-native-devsettings](https://github.com/gusgard/react-native-devsettings)
     - [Ongoing Discussion](https://github.com/jhen0409/react-native-debugger/issues/573#issuecomment-1533894331)

2. **VSCode Debugging**
   - Install [React Native Tools](https://marketplace.visualstudio.com/items?itemName=msjsdiag.vscode-react-native)
   - More reliable for development mode debugging

3. **Flipper**
   - [Official Website](https://fbflipper.com/)
   - Comprehensive debugging platform

## Additional Resources

- [Expo v48 Remote Debugging with Hermes](https://stackoverflow.com/questions/76604735/expo-v48-remote-debugging-w-hermes)
- [React Native Hermes Documentation](https://reactnative.dev/docs/hermes) 