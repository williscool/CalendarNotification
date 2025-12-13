/**
 * Centralized color tokens extracted from existing StyleSheet definitions.
 * These colors are also defined in tailwind.config.js for NativeWind usage.
 */
export const colors = {
  // Primary brand color (iOS blue)
  primary: '#007AFF',
  
  // Status colors
  danger: '#FF3B30',
  success: '#28a745',
  warning: '#FF9500',
  
  // Warning banner colors
  warningBackground: '#fff3cd',
  warningBorder: '#ffeeba',
  warningText: '#856404',
  
  // Text colors
  text: '#333',
  textMuted: '#666',
  textLight: '#999',
  
  // Border colors
  border: '#ddd',
  borderLight: '#eee',
  
  // Background colors
  background: '#f5f5f5',
  backgroundWhite: '#fff',
  backgroundMuted: '#f8f8f8',
  backgroundDisabled: '#ccc',
  
  // Debug/Log level colors
  logError: '#FF3B30',
  logWarn: '#FF9500',
  logInfo: '#007AFF',
  logDebug: '#8E8E93',
  
  // Connection status
  initializingBackground: '#e2f3fc',
  initializingBorder: '#90caf9',
  initializingText: '#0277bd',
} as const;

export type ColorKey = keyof typeof colors;

