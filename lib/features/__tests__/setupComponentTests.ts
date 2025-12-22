/**
 * Setup file for React Native component tests.
 * Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
 */
import React from 'react';
import '@testing-library/jest-dom';

// Mock react-native-url-polyfill
jest.mock('react-native-url-polyfill/auto', () => ({}));

// Note: @expo/vector-icons is mocked via moduleNameMapper in jest.config.js

// Mock AsyncStorage
jest.mock('@react-native-async-storage/async-storage', () => ({
  getItem: jest.fn(),
  setItem: jest.fn(),
  removeItem: jest.fn(),
}));

// Mock @react-navigation/native
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({
    navigate: jest.fn(),
  }),
}));

// Mock the native module
jest.mock('../../../modules/my-module', () => ({
  hello: jest.fn(() => 'mocked'),
  sendRescheduleConfirmations: jest.fn(),
  addChangeListener: jest.fn(),
}));

// Mock op-sqlite
jest.mock('@op-engineering/op-sqlite', () => ({
  open: jest.fn(() => ({
    execute: jest.fn(() => Promise.resolve({ rows: [] })),
  })),
}));

// Mock PowerSync query hook
jest.mock('@powersync/react', () => ({
  useQuery: jest.fn(() => ({ data: [] })),
  PowerSyncContext: React.createContext(null),
}));

// Filter out Gluestack-specific style props that React DOM doesn't understand
const filterGluestackProps = (props: any) => {
  const { 
    testID, bg, p, m, mx, my, mt, mb, ml, mr, px, py, pt, pb, pl, pr,
    flex, alignItems, justifyContent, borderRadius, borderWidth, borderColor,
    space, fontSize, fontWeight, textAlign, color, underline, selectable,
    contentContainerStyle, textDecorationLine, ...rest 
  } = props;
  return { 'data-testid': testID, ...rest };
};

// Mock Gluestack UI - provide simple div-based components for testing
jest.mock('@gluestack-ui/themed', () => ({
  Box: ({ children, ...props }: any) => React.createElement('div', filterGluestackProps(props), children),
  Text: ({ children, onPress, ...props }: any) => {
    const filtered = filterGluestackProps(props);
    return onPress 
      ? React.createElement('span', { ...filtered, onClick: onPress, role: 'button' }, children)
      : React.createElement('span', filtered, children);
  },
  Button: ({ children, onPress, ...props }: any) => React.createElement('button', { onClick: onPress, ...filterGluestackProps(props) }, children),
  ButtonText: ({ children, ...props }: any) => React.createElement('span', filterGluestackProps(props), children),
  ScrollView: ({ children, ...props }: any) => React.createElement('div', filterGluestackProps(props), children),
  VStack: ({ children, ...props }: any) => React.createElement('div', filterGluestackProps(props), children),
  Center: ({ children, ...props }: any) => React.createElement('div', filterGluestackProps(props), children),
  Pressable: ({ children, onPress, disabled, ...props }: any) => React.createElement('button', { onClick: disabled ? undefined : onPress, disabled, ...filterGluestackProps(props) }, children),
}));

// Mock react-native Linking and components
jest.mock('react-native', () => ({
  Linking: { openURL: jest.fn() },
  useColorScheme: jest.fn(() => 'light'),
  Pressable: ({ children, onPress, disabled, testID, style, ...props }: any) => 
    React.createElement('button', { onClick: disabled ? undefined : onPress, disabled, 'data-testid': testID }, children),
}));

