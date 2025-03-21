import React from 'react';
import { View } from 'react-native';

// Define default insets
const DEFAULT_INSETS = {
  top: 0,
  right: 0,
  bottom: 0,
  left: 0
};

// Define default frame
const DEFAULT_FRAME = {
  x: 0,
  y: 0,
  width: 0,
  height: 0
};

// Create context objects
export const SafeAreaInsetsContext = React.createContext(DEFAULT_INSETS);
export const SafeAreaFrameContext = React.createContext(DEFAULT_FRAME);

// Create provider component
export const SafeAreaProvider = ({ children, initialMetrics, ...props }) => {
  return <View {...props}>{children}</View>;
};

// Create hook functions
export const useSafeAreaInsets = () => DEFAULT_INSETS;
export const useSafeAreaFrame = () => DEFAULT_FRAME;

// Create view component
export const SafeAreaView = ({ children, ...props }) => {
  return <View {...props}>{children}</View>;
};

// Initial metrics for optimization
export const initialWindowMetrics = {
  insets: DEFAULT_INSETS,
  frame: DEFAULT_FRAME
}; 