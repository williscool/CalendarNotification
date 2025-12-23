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

// Filter out NativeWind className props that React DOM doesn't understand
const filterNativeWindProps = (props: any) => {
  const { testID, className, ...rest } = props;
  return { 'data-testid': testID, ...rest };
};

// Mock react-native components with NativeWind support
jest.mock('react-native', () => ({
  Linking: { openURL: jest.fn() },
  useColorScheme: jest.fn(() => 'light'),
  View: ({ children, testID, className, style, ...props }: any) => 
    React.createElement('div', { 'data-testid': testID, style, ...props }, children),
  Text: ({ children, onPress, testID, className, style, selectable, ...props }: any) => {
    return onPress 
      ? React.createElement('span', { 'data-testid': testID, onClick: onPress, role: 'button', style, ...props }, children)
      : React.createElement('span', { 'data-testid': testID, style, ...props }, children);
  },
  ScrollView: ({ children, testID, className, style, contentContainerStyle, ...props }: any) => 
    React.createElement('div', { 'data-testid': testID, style, ...props }, children),
  Pressable: ({ children, onPress, disabled, testID, className, style, ...props }: any) => 
    React.createElement('button', { onClick: disabled ? undefined : onPress, disabled, 'data-testid': testID, style, ...props }, children),
  TextInput: ({ testID, className, style, ...props }: any) => 
    React.createElement('input', { 'data-testid': testID, style, ...props }),
  Switch: ({ value, onValueChange, disabled, testID, ...props }: any) => 
    React.createElement('input', { type: 'checkbox', checked: value, onChange: (e: any) => onValueChange?.(e.target.checked), disabled, 'data-testid': testID, ...props }),
  useWindowDimensions: () => ({ width: 800, height: 600 }),
  FlatList: ({ data, renderItem, ListHeaderComponent, ListEmptyComponent, keyExtractor, ...props }: any) => {
    const header = ListHeaderComponent ? (typeof ListHeaderComponent === 'function' ? ListHeaderComponent() : ListHeaderComponent) : null;
    const empty = (!data || data.length === 0) && ListEmptyComponent ? (typeof ListEmptyComponent === 'function' ? ListEmptyComponent() : ListEmptyComponent) : null;
    const items = data?.map((item: any, index: number) => renderItem({ item, index })) || [];
    return React.createElement('div', props, header, empty || items);
  },
  RefreshControl: () => null,
}));

