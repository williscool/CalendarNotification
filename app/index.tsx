import React, { useCallback, useEffect, useState } from 'react';
import { Linking } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Text, Button, ButtonText, Center, VStack, Pressable } from '@gluestack-ui/themed';
import type { AppNavigationProp } from '@lib/navigation/types';
import { setupPowerSync } from '@lib/powersync';
import { useSettings } from '@lib/hooks/SettingsContext';
import { useTheme } from '@lib/theme/ThemeContext';
import { GITHUB_README_URL } from '@lib/constants';
import { SetupSync } from '@lib/features/SetupSync';
import { SyncErrorScreen } from '@lib/features/SyncErrorScreen';

const InitialSetupScreen = () => {
  const navigation = useNavigation<AppNavigationProp>();
  const { colors } = useTheme();

  return (
    <Center flex={1} p="$5" bg={colors.backgroundWhite}>
      <VStack space="md" alignItems="center">
        <Text fontSize="$xl" textAlign="center" color={colors.text}>
          Sync is not enabled
        </Text>
        <Text fontSize="$md" textAlign="center" color={colors.textMuted} mb="$2">
          Please enable sync in settings to continue
        </Text>
        <Button
          onPress={() => navigation.navigate('Settings')}
          bg={colors.primary}
          borderRadius="$lg"
          px="$5"
          py="$3"
          mb="$4"
        >
          <ButtonText fontWeight="$semibold">Go to Settings</ButtonText>
        </Button>
        <Text fontSize="$md" textAlign="center" color={colors.textMuted}>
          or view our
        </Text>
        <Pressable onPress={() => Linking.openURL(GITHUB_README_URL)}>
          <Text fontSize="$lg" color={colors.primary} underline>
            Setup Guide
          </Text>
        </Pressable>
      </VStack>
    </Center>
  );
};

const LoadingScreen = () => {
  const { colors } = useTheme();
  
  return (
    <Center flex={1} bg={colors.backgroundWhite}>
      <Text fontSize="$xl" color={colors.text}>Initializing...</Text>
    </Center>
  );
};

export default function HomeScreen() {
  const { settings } = useSettings();
  const [isReady, setIsReady] = useState(false);
  const [syncError, setSyncError] = useState<unknown>(null);

  const initSync = useCallback(async () => {
    setIsReady(false);
    setSyncError(null);
    if (settings.syncEnabled) {
      try {
        await setupPowerSync(settings);
      } catch (e) {
        setSyncError(e);
      }
    }
    setIsReady(true);
  }, [settings]);

  useEffect(() => {
    initSync();
  }, [initSync]);

  if (!isReady) {
    return <LoadingScreen />;
  }

  if (syncError) {
    return <SyncErrorScreen error={syncError} onRetry={initSync} />;
  }

  return settings.syncEnabled ? <SetupSync /> : <InitialSetupScreen />;
}
