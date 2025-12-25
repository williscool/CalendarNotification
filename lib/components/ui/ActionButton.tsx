import React, { useState } from 'react';
import { Pressable, Text, View, ViewStyle, TextStyle } from 'react-native';
import { useTheme } from '@lib/theme/ThemeContext';
import { getVariantStyle, ButtonVariant } from './variants';

interface ActionButtonProps {
  onPress: () => void;
  children: string;
  variant?: ButtonVariant;
  disabled?: boolean;
  testID?: string;
}

const buttonStyle: ViewStyle = {
  marginHorizontal: 16,
  marginTop: 16,
  paddingVertical: 12,
  paddingHorizontal: 16,
  borderRadius: 8,
  alignItems: 'center',
  justifyContent: 'center',
};

const textStyle: TextStyle = {
  fontWeight: '600',
  fontSize: 16,
};

/**
 * A styled button with variant support and pressed state feedback.
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
  const [pressed, setPressed] = useState(false);

  return (
    <Pressable
      onPress={disabled ? undefined : onPress}
      onPressIn={() => setPressed(true)}
      onPressOut={() => setPressed(false)}
      disabled={disabled}
      testID={testID}
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      aria-disabled={disabled}
    >
      <View
        style={[
          buttonStyle,
          { backgroundColor: bg },
          pressed && !disabled ? { opacity: 0.8 } : undefined,
        ]}
      >
        <Text style={[textStyle, { color: textColor }]}>
          {children}
        </Text>
      </View>
    </Pressable>
  );
};
