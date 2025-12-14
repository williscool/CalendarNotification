import React from 'react';
import { Button, ButtonText } from '@gluestack-ui/themed';
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
 * Replaces the multiple button style patterns across screens.
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
