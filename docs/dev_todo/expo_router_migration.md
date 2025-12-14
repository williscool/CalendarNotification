# Expo Router Migration

## Status: Blocked - Awaiting React Native Upgrade

## Summary

Attempted migration to Expo Router v3 for file-based routing but encountered compatibility issues with the current bare React Native 0.74.5 setup.

## Current Setup (Working)

- **React Native**: 0.74.5
- **Expo SDK**: 51
- **Navigation**: React Navigation (`@react-navigation/native-stack`)
- **UI Components**: Gluestack UI v1
- **Styling**: NativeWind v2 (Tailwind for RN)
- **Entry Point**: `index.tsx` with explicit screen imports

## Target Versions for Original Goals

| Package | Current | Target | Why |
|---------|---------|--------|-----|
| **React Native** | 0.74.5 | **0.76.x** | New Architecture required for worklets |
| **Expo SDK** | 51 | **52** | Expo Router v4 + updated gradle plugins |
| **Expo Router** | ❌ removed | **~4.x** | Better bare RN support in v4 |
| **NativeWind** | 2.x | **4.x** | Requires RN 0.76+ for worklets |
| **react-native-svg** | 15.2.0 (broken) | **15.x** | Compatible with RN 0.76+ |

### Upgrade Helper

Use this link to see all required changes:
https://react-native-community.github.io/upgrade-helper/?from=0.74.5&to=0.76.0

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

## Migration Checklist

### Prerequisites
- [ ] Upgrade to React Native 0.76.x
- [ ] Upgrade to Expo SDK 52
- [ ] Verify New Architecture is enabled
- [ ] Clean rebuild on Windows (`.\gradlew clean`)

### Migration Steps
1. [ ] Add `expo-router@~4.x` and `expo-linking`
2. [ ] Update `package.json` main to `"expo-router/entry"`
3. [ ] Update `app.json` with expo-router plugin
4. [ ] Recreate `app/_layout.tsx` with providers
5. [ ] Update screen files to use `useRouter()` instead of `useNavigation()`
6. [ ] Add NativeWind v4 + tailwindcss v3
7. [ ] Update `metro.config.js` with `withNativeWind`
8. [ ] Test on Windows/WSL environment
9. [ ] Remove React Navigation packages

## Files to Reuse

These files were created and can be adapted:

| File | Status | Notes |
|------|--------|-------|
| `app/index.tsx` | ✅ Working | Update imports for expo-router |
| `app/settings.tsx` | ✅ Working | Update imports for expo-router |
| `app/sync-debug.tsx` | ✅ Working | No navigation changes needed |
| `lib/components/ui/` | ✅ Working | Keep as-is |
| `lib/theme/colors.ts` | ✅ Working | Keep as-is |
| `lib/navigation/types.ts` | ⚠️ Remove | Not needed with expo-router |
| `app/_layout.tsx` | ❌ Deleted | Recreate with expo-router imports |

## References

- [Expo Router Docs](https://docs.expo.dev/router/introduction/)
- [NativeWind v4 Docs](https://www.nativewind.dev/)
- [React Native Upgrade Helper](https://react-native-community.github.io/upgrade-helper/?from=0.74.5&to=0.76.0)
- [Expo SDK 52 Release Notes](https://expo.dev/changelog)
- [React Native 0.76 Release](https://reactnative.dev/blog)
