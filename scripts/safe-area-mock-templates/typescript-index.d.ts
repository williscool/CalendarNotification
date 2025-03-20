import { ViewProps } from 'react-native';
import * as React from 'react';

export interface EdgeInsets {
  top: number;
  right: number;
  bottom: number;
  left: number;
}

export interface Rect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface SafeAreaViewProps extends ViewProps {
  children?: React.ReactNode;
}

export interface SafeAreaProviderProps extends ViewProps {
  children?: React.ReactNode;
  initialMetrics?: {
    insets: EdgeInsets;
    frame: Rect;
  };
}

export const SafeAreaInsetsContext: React.Context<EdgeInsets>;
export const SafeAreaFrameContext: React.Context<Rect>;
export const SafeAreaProvider: React.FC<SafeAreaProviderProps>;
export const SafeAreaView: React.FC<SafeAreaViewProps>;
export const useSafeAreaInsets: () => EdgeInsets;
export const useSafeAreaFrame: () => Rect;
export const initialWindowMetrics: {
  insets: EdgeInsets;
  frame: Rect;
}; 