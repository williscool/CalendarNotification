import React from 'react';
import { Pressable } from 'react-native';
import { Box, Text } from '@/components/ui';
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
 * Uses Gluestack UI Box and Text with custom variant colors.
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
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      aria-disabled={disabled}
      style={({ pressed }) => ({
        opacity: disabled ? 0.7 : pressed ? 0.8 : 1,
      })}
    >
      <Box
        className="p-4 rounded-lg mx-4 mt-4 items-center justify-center"
        style={{ backgroundColor: bg }}
      >
        <Text
          className="font-semibold text-base"
          style={{ color: textColor }}
        >
          {children}
        </Text>
      </Box>
    </Pressable>
  );
};
