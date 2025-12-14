import React, { useState, useCallback } from 'react';
import { Input, InputField, InputSlot, Pressable } from '@gluestack-ui/themed';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '@lib/theme/ThemeContext';

interface SecureInputProps {
  value: string;
  onChangeText: (text: string) => void;
  placeholder?: string;
  testID?: string;
  /** Optional: control visibility externally */
  isVisible?: boolean;
  /** Optional: callback when visibility changes */
  onVisibilityChange?: (visible: boolean) => void;
}

/**
 * A password input with visibility toggle.
 * Replaces the SecureInput pattern in Settings screen.
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
  
  // Use external control if provided, otherwise use internal state
  const isControlled = externalVisible !== undefined;
  const showPassword = isControlled ? externalVisible : internalVisible;

  const toggleVisibility = useCallback(() => {
    if (isControlled && onVisibilityChange) {
      onVisibilityChange(!showPassword);
    } else {
      setInternalVisible(!internalVisible);
    }
  }, [isControlled, onVisibilityChange, showPassword, internalVisible]);

  return (
    <Input
      variant="outline"
      size="md"
      borderColor={colors.border}
      borderRadius="$lg"
      flex={1}
      testID={testID}
    >
      <InputField
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={colors.textLight}
        secureTextEntry={!showPassword}
        color={colors.text}
        testID={testID ? `${testID}-field` : undefined}
      />
      <InputSlot pr="$3">
        <Pressable onPress={toggleVisibility} testID={testID ? `${testID}-toggle` : undefined}>
          <Ionicons
            name={showPassword ? "eye-off" : "eye"}
            size={24}
            color={colors.textMuted}
          />
        </Pressable>
      </InputSlot>
    </Input>
  );
};

// Re-export hook for backwards compatibility
export { useSecureInputVisibility } from './hooks';
