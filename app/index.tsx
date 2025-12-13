import React, { useEffect, useState } from 'react';
import { Linking } from 'react-native';
import { useRouter } from 'expo-router';
import { Box, Text, Button, ButtonText, Center, VStack } from '@gluestack-ui/themed';
import { setupPowerSync } from '@lib/powersync';
import { useSettings } from '@lib/hooks/SettingsContext';
import { GITHUB_README_URL } from '@lib/constants';
import { SetupSync } from '@lib/features/SetupSync';
import { colors } from '@lib/theme/colors';

const InitialSetupScreen = () => {
  const router = useRouter();

  return (
    <Center flex={1} p="$5">
      <VStack space="md" alignItems="center">
        <Text fontSize="$xl" textAlign="center">
          Sync is not enabled
        </Text>
        <Text fontSize="$md" textAlign="center" color={colors.textMuted} mb="$4">
          Please enable sync in settings to continue
        </Text>
        <Button
          onPress={() => router.push('/settings')}
          bg={colors.primary}
        >
          <ButtonText>Go to Settings</ButtonText>
        </Button>
        <Text fontSize="$md" textAlign="center" color={colors.textMuted} mt="$5">
          or view our
        </Text>
        <Text
          fontSize="$xl"
          textAlign="center"
          color={colors.primary}
          underline
          onPress={() => Linking.openURL(GITHUB_README_URL)}
        >
          Setup Guide
        </Text>
      </VStack>
    </Center>
  );
};

export default function HomeScreen() {
  const { settings } = useSettings();
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    const init = async () => {
      if (settings.syncEnabled) {
        await setupPowerSync(settings);
      }
      setIsReady(true);
    };
    init();
  }, [settings.syncEnabled, settings]);

  if (!isReady) {
    return (
      <Center flex={1} p="$5">
        <Text fontSize="$xl" textAlign="center">
          Initializing...
        </Text>
      </Center>
    );
  }

  return settings.syncEnabled ? <SetupSync /> : <InitialSetupScreen />;
}
