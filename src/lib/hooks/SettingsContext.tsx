import React, { createContext, useContext, useState, useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ConfigObj } from '../config';
import { emitSyncLog } from '../logging/syncLog';

type SyncType = 'unidirectional' | 'bidirectional' | 'none';

export interface Settings {
  syncEnabled: boolean;
  syncType: SyncType;
  supabaseUrl: string;
  supabaseAnonKey: string;
  powersyncUrl: string;
  powersyncSecret: string;
}

// Legacy settings interface for migration
interface LegacySettings {
  powersyncToken?: string;
}

const DEFAULT_SETTINGS: Settings = {
  syncEnabled: false,
  syncType: 'unidirectional',
  supabaseUrl: '',
  supabaseAnonKey: '',
  powersyncUrl: '',
  powersyncSecret: '',
};

/**
 * Migrates old settings format to new format.
 * - Renames powersyncToken to powersyncSecret (and clears it since old tokens are invalid)
 */
const migrateSettings = (stored: Settings & LegacySettings): Settings => {
  const migrated = { ...stored };
  
  // If old powersyncToken exists, clear it (old dev tokens won't work with new HS256 auth)
  if ('powersyncToken' in migrated) {
    delete (migrated as LegacySettings).powersyncToken;
    // Don't migrate the value - old tokens are incompatible with new HS256 secret
    migrated.powersyncSecret = '';
  }
  
  // Ensure powersyncSecret exists
  if (!('powersyncSecret' in migrated)) {
    migrated.powersyncSecret = '';
  }
  
  return migrated;
};

const SETTINGS_STORAGE_KEY = '@calendar_notifications_settings';

interface SettingsContextType {
  settings: Settings;
  updateSettings: (newSettings: Settings) => Promise<void>;
}

const SettingsContext = createContext<SettingsContextType | undefined>(undefined);

export const SettingsProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [settings, setSettings] = useState<Settings>(DEFAULT_SETTINGS);

  useEffect(() => {
    loadSettings();
  }, []);

  const loadSettings = async () => {
    try {
      const storedSettingsStr = await AsyncStorage.getItem(SETTINGS_STORAGE_KEY);
      if (__DEV__) {
        console.log('[SettingsContext] Stored settings:', storedSettingsStr ? 'FOUND' : 'NOT FOUND');
      }
      
      if (storedSettingsStr) {
        const parsedSettings = JSON.parse(storedSettingsStr);
        // Migrate old settings format if needed
        const migratedSettings = migrateSettings(parsedSettings);
        if (__DEV__) {
          console.log('[SettingsContext] Using stored settings (migrated if needed)');
        }
        setSettings(migratedSettings);
        // Save migrated settings back
        if (JSON.stringify(parsedSettings) !== JSON.stringify(migratedSettings)) {
          await AsyncStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(migratedSettings));
          if (__DEV__) {
            console.log('[SettingsContext] Migrated settings saved');
          }
        }
      } else {
        // Initialize with current ConfigObj values
        if (__DEV__) {
          console.log('[SettingsContext] Initializing from ConfigObj:', {
            supabaseUrl: ConfigObj.supabase.url ? 'SET' : 'EMPTY',
            supabaseAnonKey: ConfigObj.supabase.anonKey ? 'SET' : 'EMPTY',
            powersyncUrl: ConfigObj.powersync.url ? 'SET' : 'EMPTY',
            powersyncSecret: ConfigObj.powersync.secret ? 'SET' : 'EMPTY',
          });
        }
        const currentSettings: Settings = {
          syncEnabled: ConfigObj.sync.enabled,
          syncType: ConfigObj.sync.type as SyncType,
          supabaseUrl: ConfigObj.supabase.url,
          supabaseAnonKey: ConfigObj.supabase.anonKey,
          powersyncUrl: ConfigObj.powersync.url,
          powersyncSecret: ConfigObj.powersync.secret,
        };
        setSettings(currentSettings);
        await AsyncStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(currentSettings));
      }
    } catch (error) {
      emitSyncLog('error', 'Error loading settings', { error });
    }
  };

  const updateSettings = async (newSettings: Settings) => {
    try {
      await AsyncStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(newSettings));
      setSettings(newSettings);
    } catch (error) {
      emitSyncLog('error', 'Error saving settings', { error });
    }
  };

  return (
    <SettingsContext.Provider value={{ settings, updateSettings }}>
      {children}
    </SettingsContext.Provider>
  );
};

export const useSettings = () => {
  const context = useContext(SettingsContext);
  if (context === undefined) {
    throw new Error('useSettings must be used within a SettingsProvider');
  }
  return context;
}; 