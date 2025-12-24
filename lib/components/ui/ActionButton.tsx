import React from 'react';
import { Button, ButtonText } from '@/components/ui';
import { useTheme } from '@lib/theme/ThemeContext';
import { getVariantStyle, ButtonVariant } from './variants';

interface ActionButtonProps {
  onPress: () => void;
  children: string;
  variant?: ButtonVariant;
  disabled?: boolean;
  testID?: string;
}

// Map our variants to Gluestack action prop
const variantToAction: Record<ButtonVariant, 'primary' | 'secondary' | 'positive' | 'negative'> = {
  primary: 'primary',
  secondary: 'secondary',
  success: 'positive',
  danger: 'negative',
  warning: 'secondary', // Gluestack doesn't have warning, use secondary + custom color
  disabled: 'secondary',
};

/**
 * A styled button with variant support.
 * Uses Gluestack UI Button component with custom variant colors.
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
    <Button
      onPress={disabled ? undefined : onPress}
      disabled={disabled}
      testID={testID}
      aria-disabled={disabled}
      className="mx-4 mt-4 rounded-lg"
      style={{ backgroundColor: bg }}
      size="lg"
    >
      <ButtonText style={{ color: textColor }}>{children}</ButtonText>
    </Button>
  );
};
