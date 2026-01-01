/**
 * Centralized color tokens with light and dark theme support.
 */

const lightColors = {
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
  
  // Error banner colors
  errorBackground: '#fff5f5',
  
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
  
  // Failed operation background
  failedOpBackground: '#fff5f5',
  failedOpDataBackground: '#f8f0f0',
} as const;

const darkColors = {
  // Primary brand color (slightly brighter for dark mode)
  primary: '#0A84FF',
  
  // Status colors (adjusted for dark backgrounds)
  danger: '#FF453A',
  success: '#32D74B',
  warning: '#FF9F0A',
  
  // Warning banner colors
  warningBackground: '#3D3200',
  warningBorder: '#665500',
  warningText: '#FFD60A',
  
  // Error banner colors
  errorBackground: '#3D1515',
  
  // Text colors
  text: '#F5F5F5',
  textMuted: '#A0A0A0',
  textLight: '#707070',
  
  // Border colors
  border: '#3A3A3C',
  borderLight: '#2C2C2E',
  
  // Background colors
  background: '#000000',
  backgroundWhite: '#1C1C1E',
  backgroundMuted: '#2C2C2E',
  backgroundDisabled: '#3A3A3C',
  
  // Debug/Log level colors (same, they're already vibrant)
  logError: '#FF453A',
  logWarn: '#FF9F0A',
  logInfo: '#0A84FF',
  logDebug: '#8E8E93',
  
  // Connection status
  initializingBackground: '#0D3A5C',
  initializingBorder: '#1565C0',
  initializingText: '#64B5F6',
  
  // Failed operation background
  failedOpBackground: '#3D1515',
  failedOpDataBackground: '#2D1010',
} as const;

export type ThemeColors = typeof lightColors;
export type ColorKey = keyof ThemeColors;

/**
 * Get colors for the current theme.
 */
export const getColors = (isDark: boolean): ThemeColors => {
  return isDark ? darkColors : lightColors;
};

/**
 * Legacy export for backwards compatibility during migration.
 * New code should use useTheme() hook instead.
 * @deprecated Use useTheme().colors instead
 */
export const colors = lightColors;
