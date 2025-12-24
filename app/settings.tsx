import React, { useState, useEffect } from 'react';
import { View, Text, Switch, ScrollView, TextInput, Pressable } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { AppNavigationProp } from '@lib/navigation/types';
import { useSettings } from '@lib/hooks/SettingsContext';
import { useTheme } from '@lib/theme/ThemeContext';
import { Section, ActionButton, SecureInput, WarningBanner } from '@lib/components/ui';

export default function Settings() {
  const navigation = useNavigation<AppNavigationProp>();
  const { colors } = useTheme();
  const { settings, updateSettings } = useSettings();
  const [tempSettings, setTempSettings] = useState(settings);
  const [isDirty, setIsDirty] = useState(false);
  const [showSupabaseKey, setShowSupabaseKey] = useState(false);
  const [showPowerSyncToken, setShowPowerSyncToken] = useState(false);
  const insets = useSafeAreaInsets();

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
    const trimmedSettings = {
      ...tempSettings,
      supabaseUrl: tempSettings.supabaseUrl.trim(),
      supabaseAnonKey: tempSettings.supabaseAnonKey.trim(),
      powersyncUrl: tempSettings.powersyncUrl.trim(),
      powersyncToken: tempSettings.powersyncToken.trim()
    };

    const hasActualChanges =
      trimmedSettings.supabaseUrl !== settings.supabaseUrl.trim() ||
      trimmedSettings.supabaseAnonKey !== settings.supabaseAnonKey.trim() ||
      trimmedSettings.powersyncUrl !== settings.powersyncUrl.trim() ||
      trimmedSettings.powersyncToken !== settings.powersyncToken.trim() ||
      trimmedSettings.syncEnabled !== settings.syncEnabled ||
      trimmedSettings.syncType !== settings.syncType;

    if (areAllSettingsValid(trimmedSettings) && hasActualChanges) {
      updateSettings(trimmedSettings);
      setIsDirty(false);
    } else if (!hasActualChanges) {
      setTempSettings(settings);
      setIsDirty(false);
    }
  };

  const handleSyncToggle = (value: boolean) => {
    if (!value || areAllSettingsValid(tempSettings)) {
      const newSettings = { ...tempSettings, syncEnabled: value };
      setTempSettings(newSettings);
      setIsDirty(true);
    }
  };

  // Stacked layout: label on top, input below
  const rowClassName = "py-3 border-b";

  return (
    <View className="flex-1" style={{ backgroundColor: colors.background }}>
      <ScrollView className="flex-1" contentContainerStyle={{ paddingTop: 16, paddingBottom: Math.max(insets.bottom, 16) }}>
        {isDirty && (
          <WarningBanner variant="warning" message="You have unsaved changes" />
        )}

        <Section title="Sync Settings">
          <View className={rowClassName} style={{ borderBottomColor: colors.borderLight }}>
            <Text className={"text-base mb-2"} style={{ color: colors.text }}>
            Enable Sync
          </Text>
            <Switch
            value={tempSettings.syncEnabled}
            onValueChange={handleSyncToggle}
            disabled={!areAllSettingsValid(tempSettings)}
          />
          </View>

        {tempSettings.syncEnabled && (
            <View className={rowClassName} style={{ borderBottomColor: colors.borderLight }}>
              <Text className={"text-base mb-2"} style={{ color: colors.text }}>
              Sync Type
            </Text>
              <View className={"w-full"}>
                <View className="flex-row gap-2">
                  <Pressable
                    onPress={() => handleSettingChange({ ...tempSettings, syncType: 'unidirectional' })}
                    className="px-3 py-1.5 rounded-full"
                    style={{ backgroundColor: tempSettings.syncType === 'unidirectional' ? colors.primary : colors.borderLight }}
                  >
                    <Text className="text-sm" style={{ color: tempSettings.syncType === 'unidirectional' ? '#fff' : colors.textMuted }}>
                      Unidirectional
                    </Text>
                  </Pressable>
                  <Pressable
                    className="px-3 py-1.5 rounded-full opacity-60"
                    style={{ backgroundColor: colors.border }}
                  >
                    <Text className="text-sm" style={{ color: colors.textLight }}>
                      Bidirectional
                    </Text>
                  </Pressable>
                </View>
                <Text className="text-xs italic mt-1" style={{ color: colors.textMuted }}>
                  Bidirectional sync coming soon
                </Text>
              </View>
            </View>
        )}
      </Section>

      <Pressable
        onPress={() => navigation.navigate('SyncDebug')}
          className="p-3.5 rounded-lg mx-4 flex-row items-center justify-center"
          style={{ 
            backgroundColor: colors.backgroundMuted,
            borderWidth: 1,
            borderColor: colors.border,
          }}
        >
          <Text className="mr-2" style={{ color: colors.textMuted }}>🐛</Text>
          <Text className="text-sm font-medium" style={{ color: colors.textMuted }}>
          View Sync Debug Logs
        </Text>
      </Pressable>

      <Section title="Current Settings Output">
          <View className="p-4 rounded-lg" style={{ backgroundColor: colors.backgroundMuted }}>
            <Text className="text-sm font-mono" style={{ color: colors.text }}>
            {JSON.stringify({
              ...tempSettings,
              supabaseAnonKey: showSupabaseKey ? tempSettings.supabaseAnonKey : '[Hidden Reveal Below]',
              powersyncToken: showPowerSyncToken ? tempSettings.powersyncToken : '[Hidden Reveal Below]'
            }, null, 2)}
          </Text>
          </View>
      </Section>

      <Section title="Supabase Settings">
          <View className={rowClassName} style={{ borderBottomColor: colors.borderLight }}>
            <Text className={"text-base mb-2"} style={{ color: colors.text }}>
            Supabase URL
          </Text>
            <View className={'w-full'}>
              <TextInput
                value={tempSettings.supabaseUrl}
                onChangeText={(text) => handleSettingChange({ ...tempSettings, supabaseUrl: text })}
                placeholder="https://your-project.supabase.co"
                placeholderTextColor={colors.textLight}
                className="px-3 py-3 rounded-lg"
                style={{ 
                  color: colors.text,
                  borderWidth: 1,
                  borderColor: colors.border,
                  backgroundColor: colors.backgroundWhite,
                }}
              />
            </View>
          </View>

          <View className={rowClassName} style={{ borderBottomColor: colors.borderLight }}>
            <Text className={"text-base mb-2"} style={{ color: colors.text }}>
            Supabase Anon Key
          </Text>
            <View className={'w-full'}>
            <SecureInput
              value={tempSettings.supabaseAnonKey}
              onChangeText={(text) => handleSettingChange({ ...tempSettings, supabaseAnonKey: text })}
              placeholder="your-supabase-anon-key"
              isVisible={showSupabaseKey}
              onVisibilityChange={setShowSupabaseKey}
            />
            </View>
          </View>
      </Section>

      <Section title="PowerSync Settings">
          <View className={rowClassName} style={{ borderBottomColor: colors.borderLight }}>
            <Text className={"text-base mb-2"} style={{ color: colors.text }}>
            PowerSync URL
          </Text>
            <View className={'w-full'}>
              <TextInput
                value={tempSettings.powersyncUrl}
                onChangeText={(text) => handleSettingChange({ ...tempSettings, powersyncUrl: text })}
                placeholder="https://your-project.powersync.journeyapps.com"
                placeholderTextColor={colors.textLight}
                className="px-3 py-3 rounded-lg"
                style={{ 
                  color: colors.text,
                  borderWidth: 1,
                  borderColor: colors.border,
                  backgroundColor: colors.backgroundWhite,
                }}
              />
            </View>
          </View>

          <View className={rowClassName} style={{ borderBottomColor: colors.borderLight }}>
            <Text className={"text-base mb-2"} style={{ color: colors.text }}>
            PowerSync Token
          </Text>
            <View className={'w-full'}>
            <SecureInput
              value={tempSettings.powersyncToken}
              onChangeText={(text) => handleSettingChange({ ...tempSettings, powersyncToken: text })}
              placeholder="your-powersync-token"
              isVisible={showPowerSyncToken}
              onVisibilityChange={setShowPowerSyncToken}
            />
            </View>
          </View>
      </Section>

        {!areAllSettingsValid(tempSettings) && (
          <Text className="text-center mx-4 italic" style={{ color: colors.danger }}>
            Please fill in all settings to enable sync
          </Text>
        )}

        {/* Spacer for fixed footer */}
        <View className="h-24" />
      </ScrollView>

      {/* Fixed footer - Save button always visible */}
      <View 
        className="absolute bottom-0 left-0 right-0 pt-2"
        style={{ 
          backgroundColor: colors.background,
          borderTopWidth: 1,
          borderTopColor: colors.borderLight,
          paddingBottom: Math.max(insets.bottom, 16) + 16,
        }}
      >
        <ActionButton
          onPress={handleSave}
          variant={isDirty ? 'success' : 'primary'}
          disabled={!isDirty || !areAllSettingsValid(tempSettings)}
        >
          {isDirty ? 'Save Changes*' : 'Save Changes'}
        </ActionButton>
      </View>
    </View>
  );
}
