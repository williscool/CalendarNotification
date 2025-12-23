import React from 'react';
import { Pressable, View, Text } from 'react-native';
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
 * Uses NativeWind for styling.
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
      <View
        className="p-4 rounded-lg mx-4 mt-4 items-center justify-center"
        style={{ backgroundColor: bg }}
      >
        <Text
          className="font-semibold text-base"
          style={{ color: textColor }}
        >
          {children}
        </Text>
      </View>
    </Pressable>
  );
};
