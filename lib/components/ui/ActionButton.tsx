import React from 'react';
import { Pressable } from 'react-native';
import { ButtonText } from '@/components/ui';
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
 * A styled button with variant support and pressed state feedback.
 * Uses Pressable with opacity feedback for consistent UX.
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
        backgroundColor: bg,
        opacity: pressed && !disabled ? 0.8 : 1,
        marginHorizontal: 16,
        marginTop: 16,
        paddingVertical: 12,
        paddingHorizontal: 16,
        borderRadius: 8,
        alignItems: 'center',
        justifyContent: 'center',
      })}
    >
      <ButtonText style={{ color: textColor, fontWeight: '600', fontSize: 16 }}>
        {children}
      </ButtonText>
    </Pressable>
  );
};
