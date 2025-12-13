import React from 'react';
import { Button, ButtonText } from '@gluestack-ui/themed';
import { colors } from '@lib/theme/colors';

type ButtonVariant = 'primary' | 'success' | 'danger' | 'warning' | 'secondary' | 'disabled';

interface ActionButtonProps {
  onPress: () => void;
  children: string;
  variant?: ButtonVariant;
  disabled?: boolean;
  testID?: string;
}

const variantStyles: Record<ButtonVariant, { bg: string; textColor: string }> = {
  primary: { bg: colors.primary, textColor: '#fff' },
  success: { bg: colors.success, textColor: '#fff' },
  danger: { bg: colors.danger, textColor: '#fff' },
  warning: { bg: colors.warning, textColor: '#000' },
  secondary: { bg: '#6c757d', textColor: '#fff' },
  disabled: { bg: colors.backgroundDisabled, textColor: '#888' },
};

/**
 * A styled button with variant support.
 * Replaces the multiple button style patterns across screens.
 */
export const ActionButton: React.FC<ActionButtonProps> = ({
  onPress,
  children,
  variant = 'primary',
  disabled = false,
  testID,
}) => {
  const effectiveVariant = disabled ? 'disabled' : variant;
  const { bg, textColor } = variantStyles[effectiveVariant];

  return (
    <Button
      onPress={disabled ? undefined : onPress}
      disabled={disabled}
      bg={bg}
      p="$4"
      borderRadius="$lg"
      mx="$4"
      mt="$4"
      opacity={disabled ? 0.7 : 1}
      testID={testID}
    >
      <ButtonText color={textColor} fontWeight="$semibold" fontSize="$md">
        {children}
      </ButtonText>
    </Button>
  );
};

/**
 * Returns the variant style configuration for testing purposes.
 * This allows tests to verify our variant logic without testing Gluestack internals.
 */
export const getVariantStyle = (variant: ButtonVariant, disabled: boolean) => {
  const effectiveVariant = disabled ? 'disabled' : variant;
  return variantStyles[effectiveVariant];
};

