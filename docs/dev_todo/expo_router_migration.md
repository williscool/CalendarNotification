# Expo Router Migration

## Status: Evaluated & Intentionally Deferred

## Decision (December 2025)

After upgrading to React Native 0.81.5 and Expo SDK 54 (which removed the original blockers), we re-evaluated Expo Router and decided **not to migrate**.

### Why We're Skipping It

| Factor | Assessment |
|--------|------------|
| **App complexity** | Only 3 screens (Home, Settings, SyncDebug) |
| **Navigation needs** | Simple stack - no nested routes, tabs, or drawers |
| **Deep linking** | Not needed for a sync settings UI |
| **Web support** | Android-only app |
| **Current solution** | React Navigation works perfectly fine |

### Cost vs Benefit

**Costs of migrating:**
- Additional dependencies (`expo-router`, `expo-linking`, `expo-constants`, `@expo/metro-runtime`)
- More complex Metro/Babel configuration
- Migration effort (~2-4 hours)
- Previous attempt had `require.context` issues in bare RN

**Benefits:**
- File-based routing (marginal - we only have 3 screens)
- Automatic deep links (not needed)
- Slightly less boilerplate (trade ~50 lines in `index.tsx` for `_layout.tsx`)

**Verdict:** Over-engineering for a 3-screen settings UI.

## Current Setup (Working Well)

- **React Native**: 0.81.5
- **Expo SDK**: 54
- **Navigation**: React Navigation (`@react-navigation/native-stack`)
- **UI Components**: Gluestack UI v2
- **Styling**: NativeWind v4
- **Entry Point**: `index.tsx` with explicit screen imports
- **Screens**: `app/index.tsx`, `app/settings.tsx`, `app/sync-debug.tsx`

## When to Reconsider

Expo Router would make sense if the app grows to include:
- 10+ screens with complex navigation flows
- Nested navigators (tabs within stacks, drawers, etc.)
- Web support requirements
- Deep linking for sharing specific screens

## Historical Context

Originally blocked on RN 0.74.5 / Expo 51 due to:
- `require.context` failures in bare RN
- Dependency version conflicts with expo-module-gradle-plugin
- NativeWind v4 / react-native-worklets incompatibilities

These blockers were resolved by upgrading to RN 0.81.5 / Expo 54, but the migration was deemed unnecessary for the app's scope.
