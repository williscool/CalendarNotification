# Debugging Guide

This guide covers various debugging scenarios and solutions for Calendar Notifications Plus development.

## Chrome Debugger Limitations

⚠️ **Important Warning**: The Chrome debugger has significant limitations with Expo Module Functions.

### Known Issues

1. Expo Module Functions do not work at all in the Chrome debugger when:
   - Methods use `@ReactMethod(isBlockingSynchronousMethod = true)`
   - JSI (JavaScript Interface) is being used for native code

### Why This Happens

The Chrome debugger runs React Native inside its JS VM and communicates with mobile devices via WebSockets. This means:
- No direct access to native modules
- No shared memory between JS VM and app
- Synchronous native methods cannot function properly

### Alternative Debugging Solutions

1. **Hermes on Chrome**
   - [Official Documentation](https://reactnative.dev/docs/hermes?package-manager=yarn#debugging-js-on-hermes-using-google-chromes-devtools)
   - ⚠️ **Warning**: Only works in Release mode, not Development mode

2. **VSCode Debugging**
   - Install [React Native Tools](https://marketplace.visualstudio.com/items?itemName=msjsdiag.vscode-react-native)
   - Provides integrated debugging experience

3. **Flipper**
   - [Official Website](https://fbflipper.com/)
   - Comprehensive debugging platform for mobile apps

### Development Mode Hermes Debugging

Currently, Hermes debugging in development mode is limited. There are ongoing efforts to improve this:
- [react-native-devsettings](https://github.com/gusgard/react-native-devsettings)
- [Related Discussion](https://github.com/jhen0409/react-native-debugger/issues/573#issuecomment-1533894331)

## Debugging Dependencies

### react-native-safe-area-context

We've implemented a custom mock for `react-native-safe-area-context` due to build issues. The mock:
- Provides dummy implementations of required components
- Allows React Navigation to function without errors
- Is automatically created during `yarn install`

To manually set up the mock:
```bash
node scripts/setup_safe_area_mock.js
```

#### Mock Implementation Details

Located in `scripts/safe-area-mock-templates/`:
- `package.json` - Package metadata
- `module-index.js` - ES modules implementation
- `commonjs-index.js` - CommonJS implementation
- `typescript-index.d.ts` - TypeScript definitions

Note: This is a temporary solution until SDK compatibility issues are resolved. UI elements may not properly respect safe areas on devices with notches or system UI elements. 