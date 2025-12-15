import React from 'react';
import { Box, Text } from '@gluestack-ui/themed';
import { useTheme } from '@lib/theme/ThemeContext';
import { getWarningVariantStyle, BannerVariant } from './variants';

interface WarningBannerProps {
  /** Simple text message (wrapped in Text component) */
  message?: string;
  /** Custom content (not wrapped - use for interactive elements) */
  children?: React.ReactNode;
  variant?: BannerVariant;
  testID?: string;
}

/**
 * A banner for displaying warning/error/info messages.
 * 
 * Two usage modes:
 * 1. Simple: <WarningBanner message="Warning text" />
 * 2. Custom: <WarningBanner><Text>Custom</Text><Pressable>...</Pressable></WarningBanner>
 */
export const WarningBanner: React.FC<WarningBannerProps> = ({
  message,
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
      {message ? (
        <Text color={text} fontSize="$md" textAlign="center">
          {message}
        </Text>
      ) : (
        children
      )}
    </Box>
  );
};
