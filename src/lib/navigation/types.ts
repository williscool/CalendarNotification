import type { NativeStackNavigationProp } from '@react-navigation/native-stack';

/**
 * Type definitions for the app's navigation stack.
 * Using these instead of `any` provides type safety for navigation.
 */
export type RootStackParamList = {
  Home: undefined;
  Settings: undefined;
  SyncDebug: undefined;
};

export type AppNavigationProp = NativeStackNavigationProp<RootStackParamList>;

