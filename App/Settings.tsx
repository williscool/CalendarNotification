import React, { useState, useEffect } from 'react';
import { StyleSheet, View, Text, Switch, ScrollView, TextInput, TouchableOpacity } from 'react-native';
import { useStoredSettings } from '../lib/hooks/useStoredSettings';
import { Ionicons } from '@expo/vector-icons';

export const Settings = () => {
  const { storedSettings, updateSettings } = useStoredSettings();
  const [tempSettings, setTempSettings] = useState(storedSettings);
  const [isDirty, setIsDirty] = useState(false);
  const [showSupabaseKey, setShowSupabaseKey] = useState(false);
  const [showPowerSyncToken, setShowPowerSyncToken] = useState(false);

  useEffect(() => {
    setTempSettings(storedSettings);
    setIsDirty(false);
  }, [storedSettings]);

  const areAllSettingsValid = (s: typeof storedSettings) => {
    return s.supabaseUrl.trim() !== '' &&
           s.supabaseAnonKey.trim() !== '' &&
           s.powersyncUrl.trim() !== '' &&
           s.powersyncToken.trim() !== '';
  };

  const handleSettingChange = (newSettings: typeof storedSettings) => {
    setTempSettings(newSettings);
    setIsDirty(true);
  };

  const handleSave = () => {
    if (areAllSettingsValid(tempSettings)) {
      updateSettings(tempSettings);
      setIsDirty(false);
    }
  };

  return (
    <ScrollView style={styles.container}>
      {isDirty && (
        <View style={styles.dirtyIndicator}>
          <Text style={styles.dirtyText}>You have unsaved changes</Text>
        </View>
      )}
      
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Sync Settings</Text>
        
        <View style={styles.setting}>
          <Text style={styles.settingLabel}>Enable Sync</Text>
          <Switch
            value={tempSettings.syncEnabled}
            onValueChange={(value) => {
              if (!value || areAllSettingsValid(tempSettings)) {
                handleSettingChange({ ...tempSettings, syncEnabled: value });
              }
            }}
            disabled={!areAllSettingsValid(tempSettings)}
          />
        </View>

        {tempSettings.syncEnabled && (
          <View style={styles.setting}>
            <Text style={styles.settingLabel}>Sync Type</Text>
            <View style={styles.syncTypeButtons}>
              <Text
                style={[
                  styles.syncTypeButton,
                  tempSettings.syncType === 'unidirectional' && styles.syncTypeButtonActive,
                ]}
                onPress={() => handleSettingChange({ ...tempSettings, syncType: 'unidirectional' })}
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
            value={tempSettings.supabaseUrl}
            onChangeText={(text) => handleSettingChange({ ...tempSettings, supabaseUrl: text })}
            placeholder="https://your-project.supabase.co"
            placeholderTextColor="#999"
          />
        </View>

        <View style={styles.setting}>
          <Text style={styles.settingLabel}>Supabase Anon Key</Text>
          <View style={styles.secureInputContainer}>
            <TextInput
              style={[styles.input, styles.secureInput]}
              value={tempSettings.supabaseAnonKey}
              onChangeText={(text) => handleSettingChange({ ...tempSettings, supabaseAnonKey: text })}
              placeholder="your-supabase-anon-key"
              placeholderTextColor="#999"
              secureTextEntry={!showSupabaseKey}
            />
            <TouchableOpacity 
              style={styles.eyeIcon}
              onPress={() => setShowSupabaseKey(!showSupabaseKey)}
            >
              <Ionicons 
                name={showSupabaseKey ? "eye-off" : "eye"} 
                size={24} 
                color="#666" 
              />
            </TouchableOpacity>
          </View>
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>PowerSync Settings</Text>
        
        <View style={styles.setting}>
          <Text style={styles.settingLabel}>PowerSync URL</Text>
          <TextInput
            style={styles.input}
            value={tempSettings.powersyncUrl}
            onChangeText={(text) => handleSettingChange({ ...tempSettings, powersyncUrl: text })}
            placeholder="https://your-project.powersync.journeyapps.com"
            placeholderTextColor="#999"
          />
        </View>

        <View style={styles.setting}>
          <Text style={styles.settingLabel}>PowerSync Token</Text>
          <View style={styles.secureInputContainer}>
            <TextInput
              style={[styles.input, styles.secureInput]}
              value={tempSettings.powersyncToken}
              onChangeText={(text) => handleSettingChange({ ...tempSettings, powersyncToken: text })}
              placeholder="your-powersync-token"
              placeholderTextColor="#999"
              secureTextEntry={!showPowerSyncToken}
            />
            <TouchableOpacity 
              style={styles.eyeIcon}
              onPress={() => setShowPowerSyncToken(!showPowerSyncToken)}
            >
              <Ionicons 
                name={showPowerSyncToken ? "eye-off" : "eye"} 
                size={24} 
                color="#666" 
              />
            </TouchableOpacity>
          </View>
        </View>
      </View>

      {!areAllSettingsValid(tempSettings) && (
        <Text style={styles.validationMessage}>
          Please fill in all settings to enable sync
        </Text>
      )}

      <TouchableOpacity 
        style={[
          styles.saveButton,
          !areAllSettingsValid(tempSettings) && styles.saveButtonDisabled,
          isDirty && styles.saveButtonDirty
        ]}
        onPress={handleSave}
        disabled={!areAllSettingsValid(tempSettings)}
      >
        <Text style={styles.saveButtonText}>
          {isDirty ? 'Save Changes*' : 'Save Changes'}
        </Text>
      </TouchableOpacity>
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
  saveButton: {
    backgroundColor: '#007AFF',
    padding: 16,
    borderRadius: 8,
    margin: 16,
    alignItems: 'center',
  },
  saveButtonDisabled: {
    backgroundColor: '#ccc',
  },
  saveButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: 'bold',
  },
  dirtyIndicator: {
    backgroundColor: '#fff3cd',
    padding: 12,
    margin: 16,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#ffeeba',
  },
  dirtyText: {
    color: '#856404',
    textAlign: 'center',
    fontSize: 14,
  },
  saveButtonDirty: {
    backgroundColor: '#28a745',
  },
  secureInputContainer: {
    flex: 2,
    flexDirection: 'row',
    alignItems: 'center',
    marginLeft: 16,
  },
  secureInput: {
    marginLeft: 0,
    marginRight: 8,
  },
  eyeIcon: {
    padding: 8,
  },
}); 