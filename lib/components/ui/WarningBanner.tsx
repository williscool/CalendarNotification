import React from 'react';
import { View, Text } from 'react-native';
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
 * Uses NativeWind for styling.
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
    <View
      className="p-4 mx-4 my-4 rounded-lg items-center"
      style={{ 
        backgroundColor: bg,
        borderWidth: 1,
        borderColor: border,
      }}
      testID={testID}
    >
      {message ? (
        <Text
          className="text-base text-center"
          style={{ color: text }}
        >
          {message}
        </Text>
      ) : (
        children
      )}
    </View>
  );
};
