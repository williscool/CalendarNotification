# Expo Router Migration

## Status: Blocked - Awaiting React Native Upgrade

## Summary

Attempted migration to Expo Router v3 for file-based routing but encountered compatibility issues with the current bare React Native 0.74.5 setup.

## Current Setup (Working)

- **Navigation**: React Navigation (`@react-navigation/native-stack`)
- **UI Components**: Gluestack UI v1
- **Styling**: NativeWind v2 (Tailwind for RN)
- **Entry Point**: `index.tsx` with explicit screen imports

## What We Tried

### Expo Router v3
- File-based routing in `app/` directory
- `require.context` for auto-discovery of routes
- `ExpoRoot` component as entry point

### NativeWind v4
- Direct Tailwind CSS integration
- `withNativeWind` Metro wrapper

## Issues Encountered

### 1. `require.context` Failure
Expo Router uses `require.context('./app')` to auto-discover route files. This relies on Metro's experimental features that didn't work in this bare RN setup - `ExpoRoot` couldn't find any routes, resulting in blank screens.

### 2. Dependency Version Conflicts
- `expo-router@~3.5.x` → `expo-linking@~6.3.x` → expected newer gradle plugin infrastructure
- `expo-module-gradle-plugin` version mismatches between Expo SDK 51 packages
- `react-native-svg@15.x` incompatible with RN 0.74.5 (downgraded to 13.x)

### 3. NativeWind v4 Incompatibilities
- Required `tailwindcss@3.x` (v4 not supported)
- `react-native-worklets` incompatible with RN 0.74.5
- `lightningcss` platform-specific binaries failed in Windows/WSL environment

### 4. Windows/WSL Environment
- Separate `node_modules` on Windows and Linux due to file sync
- Native binary dependencies (like `lightningcss`) need platform-specific builds

## Retry When

- [ ] Upgrade to React Native 0.76+ (New Architecture stable)
- [ ] Upgrade to Expo SDK 52+
- [ ] Consider managed Expo workflow if bare RN issues persist

## Migration Plan (For Future)

1. **Upgrade React Native** to latest stable (0.76+)
2. **Upgrade Expo SDK** to 52+
3. **Try Expo Router v4** (or latest) - should have better bare RN support
4. **Try NativeWind v4** - worklets support should be better on new RN
5. **Test in Windows/WSL** - verify native dependencies work

## Files Created (Keep for Reference)

These files were created during the migration attempt and can be reused:

- `app/_layout.tsx` - Root layout with providers (needs expo-router imports)
- `app/index.tsx` - Home screen (currently using React Navigation)
- `app/settings.tsx` - Settings screen
- `app/sync-debug.tsx` - Debug screen
- `lib/components/ui/` - Reusable UI components (working)
- `lib/theme/colors.ts` - Centralized color tokens (working)

## References

- [Expo Router Docs](https://docs.expo.dev/router/introduction/)
- [NativeWind v4 Docs](https://www.nativewind.dev/)
- [React Native Upgrade Helper](https://react-native-community.github.io/upgrade-helper/)

