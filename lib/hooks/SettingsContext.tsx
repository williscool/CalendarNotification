import React, { createContext, useContext, useState, useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ConfigObj } from '../config';

type SyncType = 'unidirectional' | 'bidirectional' | 'none';

export interface Settings {
  syncEnabled: boolean;
  syncType: SyncType;
  supabaseUrl: string;
  supabaseAnonKey: string;
  powersyncUrl: string;
  powersyncToken: string;
}

const DEFAULT_SETTINGS: Settings = {
  syncEnabled: false,
  syncType: 'unidirectional',
  supabaseUrl: '',
  supabaseAnonKey: '',
  powersyncUrl: '',
  powersyncToken: '',
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
      if (storedSettingsStr) {
        const parsedSettings = JSON.parse(storedSettingsStr);
        setSettings(parsedSettings);
      } else {
        // Initialize with current ConfigObj values
        const currentSettings: Settings = {
          syncEnabled: ConfigObj.sync.enabled,
          syncType: ConfigObj.sync.type as SyncType,
          supabaseUrl: ConfigObj.supabase.url,
          supabaseAnonKey: ConfigObj.supabase.anonKey,
          powersyncUrl: ConfigObj.powersync.url,
          powersyncToken: ConfigObj.powersync.token,
        };
        setSettings(currentSettings);
        await AsyncStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(currentSettings));
      }
    } catch (error) {
      console.error('Error loading settings:', error);
    }
  };

  const updateSettings = async (newSettings: Settings) => {
    try {
      await AsyncStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(newSettings));
      setSettings(newSettings);
    } catch (error) {
      console.error('Error saving settings:', error);
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