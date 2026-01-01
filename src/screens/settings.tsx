import React, { useState, useEffect } from 'react';
import { ScrollView } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { AppNavigationProp } from '@lib/navigation/types';
import { useSettings } from '@lib/hooks/SettingsContext';
import { useTheme } from '@lib/theme/ThemeContext';
import { Section, ActionButton, SecureInput, WarningBanner } from '@lib/components/ui';
import { VStack, HStack, Box, Text, Switch, Input, InputField, Button, ButtonText, Card, Divider, Link, LinkText } from '@/components/ui';

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

  return (
    <VStack className="flex-1" style={{ backgroundColor: colors.background }}>
      <ScrollView className="flex-1" contentContainerStyle={{ paddingTop: 16, paddingBottom: Math.max(insets.bottom, 16) }}>
        {isDirty && (
          <WarningBanner variant="warning" message="You have unsaved changes" />
        )}

        <Section title="Sync Settings">
          <VStack className="py-3">
            <Text className="text-base mb-2" style={{ color: colors.text }}>
              Enable Sync
            </Text>
            <Switch
              value={tempSettings.syncEnabled}
              onValueChange={handleSyncToggle}
              disabled={!areAllSettingsValid(tempSettings)}
            />
            <Divider className="mt-3" />
          </VStack>

          {tempSettings.syncEnabled && (
            <VStack className="py-3">
              <Text className="text-base mb-2" style={{ color: colors.text }}>
                Sync Type
              </Text>
              <HStack space="sm">
                <Button
                  onPress={() => handleSettingChange({ ...tempSettings, syncType: 'unidirectional' })}
                  action={tempSettings.syncType === 'unidirectional' ? 'primary' : 'secondary'}
                  variant={tempSettings.syncType === 'unidirectional' ? 'solid' : 'outline'}
                  size="sm"
                  className="rounded-full"
                >
                  <ButtonText>Unidirectional</ButtonText>
                </Button>
                <Button
                  disabled
                  action="secondary"
                  variant="outline"
                  size="sm"
                  className="rounded-full opacity-60"
                >
                  <ButtonText>Bidirectional</ButtonText>
                </Button>
              </HStack>
              <Text className="text-xs italic mt-1" style={{ color: colors.textMuted }}>
                Bidirectional sync coming soon
              </Text>
              <Divider className="mt-3" />
            </VStack>
          )}
        </Section>

        <Card
          variant="outline"
          className="mx-4 p-3.5"
          style={{ backgroundColor: colors.backgroundMuted }}
        >
          <Link onPress={() => navigation.navigate('SyncDebug')} className="justify-center">
            <HStack space="sm" className="items-center justify-center">
              <Text style={{ color: colors.textMuted }}>🐛</Text>
              <LinkText className="font-medium" style={{ color: colors.textMuted }}>
                View Sync Debug Logs
              </LinkText>
            </HStack>
          </Link>
        </Card>

        <Section title="Current Settings Output">
          <Box className="p-4 rounded-lg" style={{ backgroundColor: colors.backgroundMuted }}>
            <Text className="text-sm font-mono" style={{ color: colors.text }} selectable>
              {JSON.stringify({
                ...tempSettings,
                supabaseAnonKey: showSupabaseKey ? tempSettings.supabaseAnonKey : '[Hidden Reveal Below]',
                powersyncToken: showPowerSyncToken ? tempSettings.powersyncToken : '[Hidden Reveal Below]'
              }, null, 2)}
            </Text>
          </Box>
        </Section>

        <Section title="Supabase Settings">
          <VStack space="sm">
            <VStack className="py-2">
              <Text className="text-base mb-2" style={{ color: colors.text }}>
                Supabase URL
              </Text>
              <Input
                variant="outline"
                size="md"
                className="rounded-lg"
                style={{ borderColor: colors.border, backgroundColor: colors.backgroundWhite }}
              >
                <InputField
                  value={tempSettings.supabaseUrl}
                  onChangeText={(text: string) => handleSettingChange({ ...tempSettings, supabaseUrl: text })}
                  placeholder="https://your-project.supabase.co"
                  placeholderTextColor={colors.textLight}
                  style={{ color: colors.text }}
                />
              </Input>
            </VStack>

            <Divider />

            <VStack className="py-2">
              <Text className="text-base mb-2" style={{ color: colors.text }}>
                Supabase Anon Key
              </Text>
              <SecureInput
                value={tempSettings.supabaseAnonKey}
                onChangeText={(text) => handleSettingChange({ ...tempSettings, supabaseAnonKey: text })}
                placeholder="your-supabase-anon-key"
                isVisible={showSupabaseKey}
                onVisibilityChange={setShowSupabaseKey}
              />
            </VStack>
          </VStack>
        </Section>

        <Section title="PowerSync Settings">
          <VStack space="sm">
            <VStack className="py-2">
              <Text className="text-base mb-2" style={{ color: colors.text }}>
                PowerSync URL
              </Text>
              <Input
                variant="outline"
                size="md"
                className="rounded-lg"
                style={{ borderColor: colors.border, backgroundColor: colors.backgroundWhite }}
              >
                <InputField
                  value={tempSettings.powersyncUrl}
                  onChangeText={(text: string) => handleSettingChange({ ...tempSettings, powersyncUrl: text })}
                  placeholder="https://your-project.powersync.journeyapps.com"
                  placeholderTextColor={colors.textLight}
                  style={{ color: colors.text }}
                />
              </Input>
            </VStack>

            <Divider />

            <VStack className="py-2">
              <Text className="text-base mb-2" style={{ color: colors.text }}>
                PowerSync Secret
              </Text>
              <SecureInput
                value={tempSettings.powersyncToken}
                onChangeText={(text) => handleSettingChange({ ...tempSettings, powersyncToken: text })}
                placeholder="HS256 secret from PowerSync dashboard"
                isVisible={showPowerSyncToken}
                onVisibilityChange={setShowPowerSyncToken}
              />
            </VStack>
          </VStack>
        </Section>

        {!areAllSettingsValid(tempSettings) && (
          <Text className="text-center mx-4 italic" style={{ color: colors.danger }}>
            Please fill in all settings to enable sync
          </Text>
        )}

        {/* Spacer for fixed footer */}
        <Box className="h-24" />
      </ScrollView>

      {/* Fixed footer - Save button always visible */}
      <VStack 
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
      </VStack>
    </VStack>
  );
}
