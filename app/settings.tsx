import React, { useState, useEffect } from 'react';
import { useWindowDimensions } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import {
  Box,
  Text,
  Switch,
  ScrollView,
  Input,
  InputField,
  HStack,
  VStack,
  Pressable,
} from '@gluestack-ui/themed';
import { useSettings } from '@lib/hooks/SettingsContext';
import { Section, ActionButton, SecureInput, WarningBanner } from '@lib/components/ui';
import { colors } from '@lib/theme/colors';

export default function Settings() {
  const navigation = useNavigation<any>();
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

  const SettingRow = ({ label, children }: { label: string; children: React.ReactNode }) => (
    <Box
      py="$3"
      borderBottomWidth={1}
      borderBottomColor={colors.borderLight}
      flexDirection={isSmallScreen ? 'column' : 'row'}
      alignItems={isSmallScreen ? 'flex-start' : 'center'}
      justifyContent="space-between"
    >
      <Text fontSize="$md" color={colors.text} mb={isSmallScreen ? '$2' : '$0'} flex={isSmallScreen ? 0 : 1}>
        {label}
      </Text>
      <Box flex={isSmallScreen ? 1 : 2} width={isSmallScreen ? '100%' : 'auto'}>
        {children}
      </Box>
    </Box>
  );

  return (
    <ScrollView flex={1} bg={colors.background}>
      {isDirty && (
        <WarningBanner variant="warning">
          You have unsaved changes
        </WarningBanner>
      )}

      <Section title="Sync Settings">
        <SettingRow label="Enable Sync">
          <Switch
            value={tempSettings.syncEnabled}
            onToggle={handleSyncToggle}
            isDisabled={!areAllSettingsValid(tempSettings)}
          />
        </SettingRow>

        {tempSettings.syncEnabled && (
          <SettingRow label="Sync Type">
            <VStack space="xs" alignItems={isSmallScreen ? 'flex-start' : 'flex-end'}>
              <HStack space="sm">
                <Pressable
                  onPress={() => handleSettingChange({ ...tempSettings, syncType: 'unidirectional' })}
                  px="$3"
                  py="$1.5"
                  borderRadius="$full"
                  bg={tempSettings.syncType === 'unidirectional' ? colors.primary : colors.borderLight}
                >
                  <Text color={tempSettings.syncType === 'unidirectional' ? '#fff' : colors.textMuted} fontSize="$sm">
                    Unidirectional
                  </Text>
                </Pressable>
                <Pressable
                  px="$3"
                  py="$1.5"
                  borderRadius="$full"
                  bg={colors.border}
                  opacity={0.6}
                >
                  <Text color={colors.textLight} fontSize="$sm">
                    Bidirectional
                  </Text>
                </Pressable>
              </HStack>
              <Text fontSize="$xs" color={colors.textMuted} fontStyle="italic">
                Bidirectional sync coming soon
              </Text>
            </VStack>
          </SettingRow>
        )}
      </Section>

      <Pressable
        onPress={() => navigation.navigate('SyncDebug')}
        bg="#f0f0f0"
        p="$3.5"
        borderRadius="$lg"
        mx="$4"
        borderWidth={1}
        borderColor={colors.border}
        flexDirection="row"
        alignItems="center"
        justifyContent="center"
      >
        <Text color={colors.textMuted} mr="$2">🐛</Text>
        <Text color={colors.textMuted} fontSize="$sm" fontWeight="$medium">
          View Sync Debug Logs
        </Text>
      </Pressable>

      <Section title="Current Settings Output">
        <Box bg={colors.backgroundWhite} p="$4" borderRadius="$lg">
          <Text fontSize="$sm" color={colors.text} fontFamily="monospace">
            {JSON.stringify({
              ...tempSettings,
              supabaseAnonKey: showSupabaseKey ? tempSettings.supabaseAnonKey : '[Hidden Reveal Below]',
              powersyncToken: showPowerSyncToken ? tempSettings.powersyncToken : '[Hidden Reveal Below]'
            }, null, 2)}
          </Text>
        </Box>
      </Section>

      <Section title="Supabase Settings">
        <SettingRow label="Supabase URL">
          <Input variant="outline" size="md" borderColor={colors.border} borderRadius="$lg">
            <InputField
              value={tempSettings.supabaseUrl}
              onChangeText={(text) => handleSettingChange({ ...tempSettings, supabaseUrl: text })}
              placeholder="https://your-project.supabase.co"
              placeholderTextColor={colors.textLight}
            />
          </Input>
        </SettingRow>

        <SettingRow label="Supabase Anon Key">
          <SecureInput
            value={tempSettings.supabaseAnonKey}
            onChangeText={(text) => handleSettingChange({ ...tempSettings, supabaseAnonKey: text })}
            placeholder="your-supabase-anon-key"
            isVisible={showSupabaseKey}
            onVisibilityChange={setShowSupabaseKey}
          />
        </SettingRow>
      </Section>

      <Section title="PowerSync Settings">
        <SettingRow label="PowerSync URL">
          <Input variant="outline" size="md" borderColor={colors.border} borderRadius="$lg">
            <InputField
              value={tempSettings.powersyncUrl}
              onChangeText={(text) => handleSettingChange({ ...tempSettings, powersyncUrl: text })}
              placeholder="https://your-project.powersync.journeyapps.com"
              placeholderTextColor={colors.textLight}
            />
          </Input>
        </SettingRow>

        <SettingRow label="PowerSync Token">
          <SecureInput
            value={tempSettings.powersyncToken}
            onChangeText={(text) => handleSettingChange({ ...tempSettings, powersyncToken: text })}
            placeholder="your-powersync-token"
            isVisible={showPowerSyncToken}
            onVisibilityChange={setShowPowerSyncToken}
          />
        </SettingRow>
      </Section>

      {!areAllSettingsValid(tempSettings) && (
        <Text color={colors.danger} textAlign="center" mx="$4" fontStyle="italic">
          Please fill in all settings to enable sync
        </Text>
      )}

      <ActionButton
        onPress={handleSave}
        variant={isDirty ? 'success' : 'primary'}
        disabled={!isDirty || !areAllSettingsValid(tempSettings)}
      >
        {isDirty ? 'Save Changes*' : 'Save Changes'}
      </ActionButton>

      <Box h="$4" />
    </ScrollView>
  );
}
