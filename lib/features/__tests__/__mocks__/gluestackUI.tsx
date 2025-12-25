/**
 * Mock Gluestack UI components for Jest tests.
 * These are simple passthrough components that render their children.
 */
import React from 'react';
import { View, Text as RNText, TextInput, Switch as RNSwitch, Pressable } from 'react-native';

// Provider
export const GluestackUIProvider: React.FC<any> = ({ children }) => (
  <>{children}</>
);

// Layout
export const Box: React.FC<any> = ({ children, ...props }) => (
  <View {...props}>{children}</View>
);

export const VStack: React.FC<any> = ({ children, ...props }) => (
  <View {...props}>{children}</View>
);

export const HStack: React.FC<any> = ({ children, ...props }) => (
  <View {...props}>{children}</View>
);

export const Divider: React.FC<any> = (props) => (
  <View {...props} />
);

export const Card: React.FC<any> = ({ children, ...props }) => (
  <View {...props}>{children}</View>
);

// Typography
export const Text: React.FC<any> = ({ children, ...props }) => (
  <RNText {...props}>{children}</RNText>
);

// Forms
export const Input: React.FC<any> = ({ children, ...props }) => (
  <View {...props}>{children}</View>
);

export const InputField: React.FC<any> = (props) => (
  <TextInput {...props} />
);

export const InputSlot: React.FC<any> = ({ children, ...props }) => (
  <View {...props}>{children}</View>
);

export const InputIcon: React.FC<any> = () => null;

export const Switch: React.FC<any> = (props) => (
  <RNSwitch {...props} />
);

export const Button: React.FC<any> = ({ children, ...props }) => (
  <View {...props}>{children}</View>
);

export const ButtonText: React.FC<any> = ({ children, ...props }) => (
  <RNText {...props}>{children}</RNText>
);

export const ButtonSpinner: React.FC<any> = () => null;
export const ButtonIcon: React.FC<any> = () => null;
export const ButtonGroup: React.FC<any> = ({ children, ...props }) => (
  <View {...props}>{children}</View>
);

// Feedback
export const Alert: React.FC<any> = ({ children, ...props }) => (
  <View {...props}>{children}</View>
);

export const AlertText: React.FC<any> = ({ children, ...props }) => (
  <RNText {...props}>{children}</RNText>
);

export const AlertIcon: React.FC<any> = () => null;

// Navigation
export const Link: React.FC<any> = ({ children, onPress, ...props }) => (
  <Pressable onPress={onPress} {...props}>{children}</Pressable>
);

export const LinkText: React.FC<any> = ({ children, ...props }) => (
  <RNText {...props}>{children}</RNText>
);

