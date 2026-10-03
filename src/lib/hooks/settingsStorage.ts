import AsyncStorage from '@react-native-async-storage/async-storage';
import type { Settings } from './SettingsContext';

export const SETTINGS_STORAGE_KEY = '@calendar_notifications_settings';

export const DEFAULT_SETTINGS: Settings = {
  syncEnabled: false,
  syncType: 'unidirectional',
  supabaseUrl: '',
  supabaseAnonKey: '',
  powersyncUrl: '',
  powersyncSecret: '',
};

/** Check if all required sync credentials are configured */
export const isSettingsConfigured = (settings: Settings): boolean => Boolean(
  settings.supabaseUrl &&
  settings.supabaseAnonKey &&
  settings.powersyncUrl &&
  settings.powersyncSecret
);

/**
 * Reads the saved settings, or null when none have been saved yet.
 * Works outside React, so the background sync task can use it.
 */
export const loadStoredSettings = async (): Promise<Settings | null> => {
  const stored = await AsyncStorage.getItem(SETTINGS_STORAGE_KEY);
  // Merge with defaults to handle any missing keys (old keys like powersyncToken are just ignored)
  return stored ? { ...DEFAULT_SETTINGS, ...JSON.parse(stored) } : null;
};
