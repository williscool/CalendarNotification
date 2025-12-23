import React, { useState, useCallback } from 'react';
import { View, TextInput, Pressable } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '@lib/theme/ThemeContext';

interface SecureInputProps {
  value: string;
  onChangeText: (text: string) => void;
  placeholder?: string;
  testID?: string;
  /** Optional: control visibility externally (requires onVisibilityChange) */
  isVisible?: boolean;
  /** Optional: callback when visibility changes (required if isVisible is provided) */
  onVisibilityChange?: (visible: boolean) => void;
}

/**
 * A password input with visibility toggle.
 * Uses NativeWind for styling.
 * 
 * Can be used in two modes:
 * 1. Uncontrolled: Don't pass isVisible/onVisibilityChange - component manages its own state
 * 2. Controlled: Pass BOTH isVisible AND onVisibilityChange - parent manages state
 */
export const SecureInput: React.FC<SecureInputProps> = ({
  value,
  onChangeText,
  placeholder,
  testID,
  isVisible: externalVisible,
  onVisibilityChange,
}) => {
  const { colors } = useTheme();
  const [internalVisible, setInternalVisible] = useState(false);
  
  // Only controlled if BOTH props are provided
  const isControlled = externalVisible !== undefined && onVisibilityChange !== undefined;
  const showPassword = isControlled ? externalVisible : internalVisible;

  const toggleVisibility = useCallback(() => {
    if (isControlled) {
      onVisibilityChange(!showPassword);
    } else {
      setInternalVisible(prev => !prev);
    }
  }, [isControlled, onVisibilityChange, showPassword]);

  return (
    <View
      className="flex-row flex-1 items-center rounded-lg px-3"
      style={{ 
        borderWidth: 1,
        borderColor: colors.border,
        backgroundColor: colors.backgroundWhite,
      }}
      testID={testID}
    >
      <TextInput
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={colors.textLight}
        secureTextEntry={!showPassword}
        className="flex-1 py-3"
        style={{ color: colors.text }}
        testID={testID ? `${testID}-field` : undefined}
      />
      <Pressable 
        onPress={toggleVisibility} 
        testID={testID ? `${testID}-toggle` : undefined}
        className="pl-2"
      >
        <Ionicons
          name={showPassword ? "eye-off" : "eye"}
          size={24}
          color={colors.textMuted}
        />
      </Pressable>
    </View>
  );
};

// Re-export hook for backwards compatibility
export { useSecureInputVisibility } from './hooks';
