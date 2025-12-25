import React, { useState, useCallback } from 'react';
import { Pressable } from 'react-native';
import { Input, InputField, InputSlot } from '@/components/ui';
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
 * Uses Gluestack UI Input components.
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
    <Input
      variant="outline"
      size="md"
      className="rounded-lg"
      style={{ 
        borderColor: colors.border,
        backgroundColor: colors.backgroundWhite,
        flexDirection: 'row',
        alignItems: 'center',
      }}
      testID={testID}
    >
      <InputField
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={colors.textLight}
        secureTextEntry={!showPassword}
        selectTextOnFocus={true}
        style={{ color: colors.text, flex: 1 }}
        testID={testID ? `${testID}-field` : undefined}
      />
      <InputSlot style={{ paddingRight: 12 }}>
        <Pressable 
          onPress={toggleVisibility} 
          testID={testID ? `${testID}-toggle` : undefined}
        >
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
