import React, { useState, useEffect } from 'react';
import { StyleSheet, View, Text, Switch, ScrollView, TextInput, TouchableOpacity, useWindowDimensions } from 'react-native';
import { useSettings } from '@lib/hooks/SettingsContext';
import { Ionicons } from '@expo/vector-icons';

export const Settings = () => {
  const { settings, updateSettings } = useSettings();
  const [tempSettings, setTempSettings] = useState(settings);
  const [isDirty, setIsDirty] = useState(false);
  const [showSupabaseKey, setShowSupabaseKey] = useState(false);
  const [showPowerSyncToken, setShowPowerSyncToken] = useState(false);
  const { width } = useWindowDimensions();
  const isSmallScreen = width < 450;

  useEffect(() => {
    setTempSettings(settings);
    setIsDirty(false);
  }, [settings]);

  const areAllSettingsValid = (s: typeof settings) => {
    return s.supabaseUrl.trim() !== '' &&
           s.supabaseAnonKey.trim() !== '' &&
           s.powersyncUrl.trim() !== '' &&
           s.powersyncToken.trim() !== '';
  };

  const handleSettingChange = (newSettings: typeof settings) => {
    setTempSettings(newSettings);
    setIsDirty(true);
  };

  const handleSave = () => {
    // Trim whitespace from text input fields before saving
    const trimmedSettings = {
      ...tempSettings,
      supabaseUrl: tempSettings.supabaseUrl.trim(),
      supabaseAnonKey: tempSettings.supabaseAnonKey.trim(),
      powersyncUrl: tempSettings.powersyncUrl.trim(),
      powersyncToken: tempSettings.powersyncToken.trim()
    };

    if (areAllSettingsValid(trimmedSettings)) {
      updateSettings(trimmedSettings);
      setIsDirty(false);
    }
  };

  const handleSyncToggle = (value: boolean) => {
    if (!value || areAllSettingsValid(tempSettings)) {
      const newSettings = { ...tempSettings, syncEnabled: value };
      setTempSettings(newSettings);
      updateSettings(newSettings);
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
        
        <View style={[styles.setting, isSmallScreen && styles.settingColumn]}>
          <Text style={[styles.settingLabel, isSmallScreen && styles.settingLabelFullWidth]}>Enable Sync</Text>
          <Switch
            value={tempSettings.syncEnabled}
            onValueChange={handleSyncToggle}
            disabled={!areAllSettingsValid(tempSettings)}
          />
        </View>

        {tempSettings.syncEnabled && (
          <View style={[styles.setting, isSmallScreen && styles.settingColumn]}>
            <Text style={[styles.settingLabel, isSmallScreen && styles.settingLabelFullWidth]}>Sync Type</Text>
            <View style={[styles.syncTypeContainer, isSmallScreen && styles.syncTypeContainerFullWidth]}>
              <View style={[styles.syncTypeButtons, isSmallScreen && styles.syncTypeButtonsFullWidth]}>
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
              <Text style={[styles.disabledMessage, isSmallScreen && styles.disabledMessageFullWidth]}>Bidirectional sync coming soon</Text>
            </View>
          </View>
        )}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Current Settings Output</Text>
        <View style={styles.debugContainer}>
          <Text style={styles.debugText} selectable>
            {JSON.stringify({
              ...tempSettings,
              supabaseAnonKey: showSupabaseKey ? tempSettings.supabaseAnonKey : '[Hidden Reveal Below]',
              powersyncToken: showPowerSyncToken ? tempSettings.powersyncToken : '[Hidden Reveal Below]'
            }, null, 2)}
          </Text>
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Supabase Settings</Text>
        
        <View style={[styles.setting, isSmallScreen && styles.settingColumn]}>
          <Text style={[styles.settingLabel, isSmallScreen && styles.settingLabelFullWidth]}>Supabase URL</Text>
          <TextInput
            style={[styles.input, isSmallScreen && styles.inputFullWidth]}
            value={tempSettings.supabaseUrl}
            onChangeText={(text) => handleSettingChange({ ...tempSettings, supabaseUrl: text })}
            placeholder="https://your-project.supabase.co"
            placeholderTextColor="#999"
          />
        </View>

        <View style={[styles.setting, isSmallScreen && styles.settingColumn]}>
          <Text style={[styles.settingLabel, isSmallScreen && styles.settingLabelFullWidth]}>Supabase Anon Key</Text>
          <View style={[styles.secureInputContainer, isSmallScreen && styles.secureInputContainerFullWidth]}>
            <TextInput
              style={[styles.input, styles.secureInput, isSmallScreen && styles.inputFullWidth]}
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
        
        <View style={[styles.setting, isSmallScreen && styles.settingColumn]}>
          <Text style={[styles.settingLabel, isSmallScreen && styles.settingLabelFullWidth]}>PowerSync URL</Text>
          <TextInput
            style={[styles.input, isSmallScreen && styles.inputFullWidth]}
            value={tempSettings.powersyncUrl}
            onChangeText={(text) => handleSettingChange({ ...tempSettings, powersyncUrl: text })}
            placeholder="https://your-project.powersync.journeyapps.com"
            placeholderTextColor="#999"
          />
        </View>

        <View style={[styles.setting, isSmallScreen && styles.settingColumn]}>
          <Text style={[styles.settingLabel, isSmallScreen && styles.settingLabelFullWidth]}>PowerSync Token</Text>
          <View style={[styles.secureInputContainer, isSmallScreen && styles.secureInputContainerFullWidth]}>
            <TextInput
              style={[styles.input, styles.secureInput, isSmallScreen && styles.inputFullWidth]}
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
  settingColumn: {
    flexDirection: 'column',
    alignItems: 'flex-start',
  },
  settingLabel: {
    fontSize: 16,
    color: '#333',
    flex: 1,
  },
  settingLabelFullWidth: {
    flex: 0,
    width: '100%',
    marginBottom: 8,
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
  inputFullWidth: {
    flex: 1,
    marginLeft: 0,
    width: '100%',
  },
  syncTypeContainer: {
    flex: 2,
    flexDirection: 'column',
    marginLeft: 16,
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  syncTypeContainerFullWidth: {
    flex: 1,
    width: '100%',
    marginLeft: 0,
    alignItems: 'flex-start',
  },
  syncTypeButtons: {
    flexDirection: 'row',
    gap: 8,
    alignItems: 'center',
  },
  syncTypeButtonsFullWidth: {
    marginTop: 8,
    justifyContent: 'flex-start',
    width: 'auto',
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
    textAlign: 'right',
  },
  disabledMessageFullWidth: {
    textAlign: 'left',
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
  secureInputContainerFullWidth: {
    flex: 1,
    marginLeft: 0,
    width: '100%',
  },
  secureInput: {
    marginLeft: 0,
    marginRight: 8,
    flex: 1,
  },
  eyeIcon: {
    padding: 8,
  },
  debugContainer: {
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 8,
    marginBottom: 16,
  },
  debugText: {
    fontSize: 14,
    color: '#333',
  },
}); 