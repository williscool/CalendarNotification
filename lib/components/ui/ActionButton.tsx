import React from 'react';
import { Pressable } from 'react-native';
import { Box, Text } from '@gluestack-ui/themed';
import { useTheme } from '@lib/theme/ThemeContext';
import { getVariantStyle, ButtonVariant } from './variants';

interface ActionButtonProps {
  onPress: () => void;
  children: string;
  variant?: ButtonVariant;
  disabled?: boolean;
  testID?: string;
}

/**
 * A styled button with variant support.
 * Uses RN Pressable instead of Gluestack Button to avoid text rendering issues.
 */
export const ActionButton: React.FC<ActionButtonProps> = ({
  onPress,
  children,
  variant = 'primary',
  disabled = false,
  testID,
}) => {
  const { colors } = useTheme();
  const { bg, textColor } = getVariantStyle(variant, disabled, colors);

  return (
    <Pressable
      onPress={disabled ? undefined : onPress}
      disabled={disabled}
      testID={testID}
      style={({ pressed }) => ({
        opacity: disabled ? 0.7 : pressed ? 0.8 : 1,
      })}
    >
      <Box
        bg={bg}
        p="$4"
        borderRadius="$lg"
        mx="$4"
        mt="$4"
        alignItems="center"
        justifyContent="center"
      >
        <Text color={textColor} fontWeight="$semibold" fontSize="$md">
          {children}
        </Text>
      </Box>
    </Pressable>
  );
};
