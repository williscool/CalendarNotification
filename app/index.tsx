import React, { useEffect, useState } from 'react';
import { Linking } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Box, Text, Button, ButtonText, Center, VStack, Pressable } from '@gluestack-ui/themed';
import type { AppNavigationProp } from '@lib/navigation/types';
import { setupPowerSync } from '@lib/powersync';
import { useSettings } from '@lib/hooks/SettingsContext';
import { GITHUB_README_URL } from '@lib/constants';
import { SetupSync } from '@lib/features/SetupSync';
import { colors } from '@lib/theme/colors';

const InitialSetupScreen = () => {
  const navigation = useNavigation<AppNavigationProp>();

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
      <Center flex={1} bg={colors.backgroundWhite}>
        <Text fontSize="$xl" color={colors.text}>Initializing...</Text>
      </Center>
    );
  }

  return settings.syncEnabled ? <SetupSync /> : <InitialSetupScreen />;
}
