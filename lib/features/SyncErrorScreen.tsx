import React from 'react';
import { useNavigation } from '@react-navigation/native';
import { Text, Button, ButtonText, Center, VStack } from '@gluestack-ui/themed';
import type { AppNavigationProp } from '@lib/navigation/types';
import { useTheme } from '@lib/theme/ThemeContext';

interface SyncErrorScreenProps {
  error: unknown;
  onRetry: () => void;
}

const getErrorMessage = (error: unknown): string => {
  if (error instanceof Error) return error.message;
  return String(error);
};

export const SyncErrorScreen = ({ error, onRetry }: SyncErrorScreenProps) => {
  const navigation = useNavigation<AppNavigationProp>();
  const { colors } = useTheme();

  return (
    <Center flex={1} p="$5" bg={colors.backgroundWhite} testID="sync-error-screen">
      <VStack space="md" alignItems="center">
        <Text fontSize="$xl" textAlign="center" color={colors.danger} testID="error-title">
          Sync Failed
        </Text>
        <Text fontSize="$md" textAlign="center" color={colors.textMuted} mb="$2" testID="error-message">
          {getErrorMessage(error)}
        </Text>
        <Button onPress={onRetry} bg={colors.primary} borderRadius="$lg" px="$5" py="$3" testID="retry-button">
          <ButtonText fontWeight="$semibold">Retry</ButtonText>
        </Button>
        <Button
          onPress={() => navigation.navigate('Settings')}
          variant="outline"
          borderRadius="$lg"
          px="$5"
          py="$3"
          testID="settings-button"
        >
          <ButtonText color={colors.primary}>Check Settings</ButtonText>
        </Button>
      </VStack>
    </Center>
  );
};

