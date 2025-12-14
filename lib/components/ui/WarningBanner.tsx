import React from 'react';
import { Box, Text } from '@gluestack-ui/themed';
import { useTheme } from '@lib/theme/ThemeContext';
import { getWarningVariantStyle, BannerVariant } from './variants';

interface WarningBannerProps {
  children: React.ReactNode;
  variant?: BannerVariant;
  testID?: string;
}

/**
 * A banner for displaying warning/error/info messages.
 * Replaces the warning container patterns across screens.
 */
export const WarningBanner: React.FC<WarningBannerProps> = ({
  children,
  variant = 'warning',
  testID,
}) => {
  const { colors } = useTheme();
  const { bg, border, text } = getWarningVariantStyle(variant, colors);

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
