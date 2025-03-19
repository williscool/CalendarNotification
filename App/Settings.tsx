import React, { useState, useEffect } from 'react';
import { StyleSheet, View, Text, Switch, ScrollView, TextInput } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ConfigObj } from '../lib/config';

type SyncType = 'unidirectional' | 'bidirectional';

interface Settings {
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

export const Settings = () => {
  const [settings, setSettings] = useState<Settings>(DEFAULT_SETTINGS);

  useEffect(() => {
    loadSettings();
  }, []);

  const loadSettings = async () => {
    try {
      const storedSettings = await AsyncStorage.getItem(SETTINGS_STORAGE_KEY);
      if (storedSettings) {
        const parsedSettings = JSON.parse(storedSettings);
        setSettings(parsedSettings);
        updateConfigObj(parsedSettings);
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
      }
    } catch (error) {
      console.error('Error loading settings:', error);
    }
  };

  const updateConfigObj = (newSettings: Settings) => {
    ConfigObj.sync.enabled = newSettings.syncEnabled;
    ConfigObj.sync.type = newSettings.syncEnabled ? newSettings.syncType : 'none';
    ConfigObj.supabase.url = newSettings.supabaseUrl;
    ConfigObj.supabase.anonKey = newSettings.supabaseAnonKey;
    ConfigObj.powersync.url = newSettings.powersyncUrl;
    ConfigObj.powersync.token = newSettings.powersyncToken;
  };

  const saveSettings = async (newSettings: Settings) => {
    try {
      await AsyncStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(newSettings));
      setSettings(newSettings);
      updateConfigObj(newSettings);
    } catch (error) {
      console.error('Error saving settings:', error);
    }
  };

  const areAllSettingsValid = (s: Settings) => {
    return s.supabaseUrl.trim() !== '' &&
           s.supabaseAnonKey.trim() !== '' &&
           s.powersyncUrl.trim() !== '' &&
           s.powersyncToken.trim() !== '';
  };

  return (
    <ScrollView style={styles.container}>
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Sync Settings</Text>
        
        <View style={styles.setting}>
          <Text style={styles.settingLabel}>Enable Sync</Text>
          <Switch
            value={settings.syncEnabled}
            onValueChange={(value) => {
              if (!value || areAllSettingsValid(settings)) {
                saveSettings({ ...settings, syncEnabled: value });
              }
            }}
            disabled={!areAllSettingsValid(settings)}
          />
        </View>

        {settings.syncEnabled && (
          <View style={styles.setting}>
            <Text style={styles.settingLabel}>Sync Type</Text>
            <View style={styles.syncTypeButtons}>
              <Text
                style={[
                  styles.syncTypeButton,
                  settings.syncType === 'unidirectional' && styles.syncTypeButtonActive,
                ]}
                onPress={() => saveSettings({ ...settings, syncType: 'unidirectional' })}
              >
                Unidirectional
              </Text>
              <Text
                style={[
                  styles.syncTypeButton,
                  styles.syncTypeButtonDisabled,
                ]}
                onPress={() => {}}
              >
                Bidirectional
              </Text>
            </View>
            <Text style={styles.disabledMessage}>Bidirectional sync coming soon</Text>
          </View>
        )}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Supabase Settings</Text>
        
        <View style={styles.setting}>
          <Text style={styles.settingLabel}>Supabase URL</Text>
          <TextInput
            style={styles.input}
            value={settings.supabaseUrl}
            onChangeText={(text) => saveSettings({ ...settings, supabaseUrl: text })}
            placeholder="https://your-project.supabase.co"
            placeholderTextColor="#999"
          />
        </View>

        <View style={styles.setting}>
          <Text style={styles.settingLabel}>Supabase Anon Key</Text>
          <TextInput
            style={styles.input}
            value={settings.supabaseAnonKey}
            onChangeText={(text) => saveSettings({ ...settings, supabaseAnonKey: text })}
            placeholder="your-supabase-anon-key"
            placeholderTextColor="#999"
            secureTextEntry
          />
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>PowerSync Settings</Text>
        
        <View style={styles.setting}>
          <Text style={styles.settingLabel}>PowerSync URL</Text>
          <TextInput
            style={styles.input}
            value={settings.powersyncUrl}
            onChangeText={(text) => saveSettings({ ...settings, powersyncUrl: text })}
            placeholder="https://your-project.powersync.journeyapps.com"
            placeholderTextColor="#999"
          />
        </View>

        <View style={styles.setting}>
          <Text style={styles.settingLabel}>PowerSync Token</Text>
          <TextInput
            style={styles.input}
            value={settings.powersyncToken}
            onChangeText={(text) => saveSettings({ ...settings, powersyncToken: text })}
            placeholder="your-powersync-token"
            placeholderTextColor="#999"
            secureTextEntry
          />
        </View>
      </View>

      {!areAllSettingsValid(settings) && (
        <Text style={styles.validationMessage}>
          Please fill in all settings to enable sync
        </Text>
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  section: {
    backgroundColor: '#fff',
    padding: 16,
    marginBottom: 16,
    borderRadius: 8,
    margin: 16,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 16,
    color: '#333',
  },
  setting: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  settingLabel: {
    fontSize: 16,
    color: '#333',
    flex: 1,
  },
  input: {
    flex: 2,
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    padding: 8,
    marginLeft: 16,
    fontSize: 16,
  },
  syncTypeButtons: {
    flexDirection: 'row',
    gap: 8,
  },
  syncTypeButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: '#eee',
    color: '#666',
  },
  syncTypeButtonActive: {
    backgroundColor: '#007AFF',
    color: '#fff',
  },
  syncTypeButtonDisabled: {
    backgroundColor: '#ddd',
    color: '#999',
  },
  disabledMessage: {
    fontSize: 12,
    color: '#666',
    fontStyle: 'italic',
    marginTop: 4,
  },
  validationMessage: {
    color: '#ff3b30',
    textAlign: 'center',
    margin: 16,
    fontStyle: 'italic',
  },
}); 