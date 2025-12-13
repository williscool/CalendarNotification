import React from 'react';
import { Box, Text } from '@gluestack-ui/themed';
import { colors } from '@lib/theme/colors';

type BannerVariant = 'warning' | 'error' | 'info';

interface WarningBannerProps {
  children: React.ReactNode;
  variant?: BannerVariant;
  testID?: string;
}

const variantStyles: Record<BannerVariant, { bg: string; border: string; text: string }> = {
  warning: {
    bg: colors.warningBackground,
    border: colors.warningBorder,
    text: colors.warningText,
  },
  error: {
    bg: '#fff5f5',
    border: colors.danger,
    text: colors.danger,
  },
  info: {
    bg: colors.initializingBackground,
    border: colors.initializingBorder,
    text: colors.initializingText,
  },
};

/**
 * A banner for displaying warning/error/info messages.
 * Replaces the warning container patterns across screens.
 */
export const WarningBanner: React.FC<WarningBannerProps> = ({
  children,
  variant = 'warning',
  testID,
}) => {
  const { bg, border, text } = variantStyles[variant];

  return (
    <Box
      bg={bg}
      p="$4"
      mx="$4"
      my="$4"
      borderRadius="$lg"
      borderWidth={1}
      borderColor={border}
      alignItems="center"
      testID={testID}
    >
      <Text color={text} fontSize="$md" textAlign="center">
        {children}
      </Text>
    </Box>
  );
};

/**
 * Returns the variant style configuration for testing purposes.
 */
export const getWarningVariantStyle = (variant: BannerVariant) => {
  return variantStyles[variant];
};

