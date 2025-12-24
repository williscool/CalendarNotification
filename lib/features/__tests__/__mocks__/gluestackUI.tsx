/**
 * Mock Gluestack UI components for Jest tests.
 * These are simple passthrough components that render their children.
 */
import React from 'react';
import { View, Text as RNText, TextInput, Switch as RNSwitch } from 'react-native';

// Simple wrapper components for tests
export const Box: React.FC<any> = ({ children, ...props }) => (
  <View {...props}>{children}</View>
);

export const Text: React.FC<any> = ({ children, ...props }) => (
  <RNText {...props}>{children}</RNText>
);

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

export const GluestackUIProvider: React.FC<any> = ({ children }) => (
  <>{children}</>
);

