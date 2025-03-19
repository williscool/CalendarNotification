import React from 'react';
import { StyleSheet, View, Text, Switch, ScrollView, TextInput } from 'react-native';
import { useStoredSettings } from '../lib/hooks/useStoredSettings';

export const Settings = () => {
  const { storedSettings, updateSettings } = useStoredSettings();

  const areAllSettingsValid = (s: typeof storedSettings) => {
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
            value={storedSettings.syncEnabled}
            onValueChange={(value) => {
              if (!value || areAllSettingsValid(storedSettings)) {
                updateSettings({ ...storedSettings, syncEnabled: value });
              }
            }}
            disabled={!areAllSettingsValid(storedSettings)}
          />
        </View>

        {storedSettings.syncEnabled && (
          <View style={styles.setting}>
            <Text style={styles.settingLabel}>Sync Type</Text>
            <View style={styles.syncTypeButtons}>
              <Text
                style={[
                  styles.syncTypeButton,
                  storedSettings.syncType === 'unidirectional' && styles.syncTypeButtonActive,
                ]}
                onPress={() => updateSettings({ ...storedSettings, syncType: 'unidirectional' })}
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
            value={storedSettings.supabaseUrl}
            onChangeText={(text) => updateSettings({ ...storedSettings, supabaseUrl: text })}
            placeholder="https://your-project.supabase.co"
            placeholderTextColor="#999"
          />
        </View>

        <View style={styles.setting}>
          <Text style={styles.settingLabel}>Supabase Anon Key</Text>
          <TextInput
            style={styles.input}
            value={storedSettings.supabaseAnonKey}
            onChangeText={(text) => updateSettings({ ...storedSettings, supabaseAnonKey: text })}
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
            value={storedSettings.powersyncUrl}
            onChangeText={(text) => updateSettings({ ...storedSettings, powersyncUrl: text })}
            placeholder="https://your-project.powersync.journeyapps.com"
            placeholderTextColor="#999"
          />
        </View>

        <View style={styles.setting}>
          <Text style={styles.settingLabel}>PowerSync Token</Text>
          <TextInput
            style={styles.input}
            value={storedSettings.powersyncToken}
            onChangeText={(text) => updateSettings({ ...storedSettings, powersyncToken: text })}
            placeholder="your-powersync-token"
            placeholderTextColor="#999"
            secureTextEntry
          />
        </View>
      </View>

      {!areAllSettingsValid(storedSettings) && (
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