import React, { createContext, useContext, useMemo, useState, useEffect, useCallback } from 'react';
import { NativeModules, useColorScheme, AppState, AppStateStatus } from 'react-native';
import { getColors, ThemeColors } from './colors';

const { ThemeModule } = NativeModules;

interface ThemeContextValue {
  isDark: boolean;
  colors: ThemeColors;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

/**
 * Provides theme colors based on the app's theme setting.
 * Reads from native SharedPreferences to respect in-app theme choice (system/light/dark).
 */
export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // Fallback to system color scheme while loading or if native module unavailable
  const systemColorScheme = useColorScheme();
  const [colorScheme, setColorScheme] = useState<'light' | 'dark'>(systemColorScheme ?? 'light');

  const loadTheme = useCallback(async () => {
    try {
      if (ThemeModule?.getColorScheme) {
        const scheme = await ThemeModule.getColorScheme();
        setColorScheme(scheme as 'light' | 'dark');
      } else {
        // Fallback if native module not available
        setColorScheme(systemColorScheme ?? 'light');
      }
    } catch (error) {
      console.warn('Failed to get theme from native module:', error);
      setColorScheme(systemColorScheme ?? 'light');
    }
  }, [systemColorScheme]);

  // Load theme on mount
  useEffect(() => {
    loadTheme();
  }, [loadTheme]);

  // Reload theme when app comes back to foreground (user might have changed it in settings)
  useEffect(() => {
    const handleAppStateChange = (nextAppState: AppStateStatus) => {
      if (nextAppState === 'active') {
        loadTheme();
      }
    };

    const subscription = AppState.addEventListener('change', handleAppStateChange);
    return () => subscription.remove();
  }, [loadTheme]);

  // Also update when system color scheme changes (for "follow system" mode)
  useEffect(() => {
    loadTheme();
  }, [systemColorScheme, loadTheme]);

  const isDark = colorScheme === 'dark';
  
  const value = useMemo(() => ({
    isDark,
    colors: getColors(isDark),
  }), [isDark]);

  return (
    <ThemeContext.Provider value={value}>
      {children}
    </ThemeContext.Provider>
  );
};

/**
 * Hook to access current theme colors.
 */
export const useTheme = (): ThemeContextValue => {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
};

