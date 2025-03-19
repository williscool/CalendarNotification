import React, { useState } from 'react';
import { StyleSheet, View, Text, Switch, ScrollView } from 'react-native';
import { ConfigObj } from '../lib/config';

type SyncType = 'unidirectional' | 'bidirectional' | 'none';

interface Settings {
  syncEnabled: boolean;
  syncType: SyncType;
  devMode: boolean;
}

const DEFAULT_SETTINGS: Settings = {
  syncEnabled: true,
  syncType: 'unidirectional',
  devMode: false,
};

export const Settings = () => {
  const [settings, setSettings] = useState<Settings>(DEFAULT_SETTINGS);

  const saveSettings = (newSettings: Settings) => {
    setSettings(newSettings);
    // Update ConfigObj to reflect new settings
    ConfigObj.sync.enabled = newSettings.syncEnabled;
    ConfigObj.sync.type = newSettings.syncType;
  };

  return (
    <ScrollView style={styles.container}>
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Sync Settings</Text>
        
        <View style={styles.setting}>
          <Text style={styles.settingLabel}>Enable Sync</Text>
          <Switch
            value={settings.syncEnabled}
            onValueChange={(value) => saveSettings({ ...settings, syncEnabled: value })}
          />
        </View>

        <View style={styles.setting}>
          <Text style={styles.settingLabel}>Sync Type</Text>
          <View style={styles.syncTypeButtons}>
            {(['unidirectional', 'bidirectional', 'none'] as SyncType[]).map((type) => (
              <Text
                key={type}
                style={[
                  styles.syncTypeButton,
                  settings.syncType === type && styles.syncTypeButtonActive,
                ]}
                onPress={() => saveSettings({ ...settings, syncType: type })}
              >
                {type.charAt(0).toUpperCase() + type.slice(1)}
              </Text>
            ))}
          </View>
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Developer Settings</Text>
        
        <View style={styles.setting}>
          <Text style={styles.settingLabel}>Developer Mode</Text>
          <Switch
            value={settings.devMode}
            onValueChange={(value) => saveSettings({ ...settings, devMode: value })}
          />
        </View>
      </View>
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
}); 