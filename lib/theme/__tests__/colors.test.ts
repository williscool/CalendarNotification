import { getColors } from '../colors';

/**
 * Tests for theme color logic.
 * We test OUR logic (light/dark color mapping), not React context.
 */
describe('Theme colors', () => {
  describe('getColors', () => {
    it('returns light colors when isDark is false', () => {
      const colors = getColors(false);
      
      // Light mode should have light backgrounds
      expect(colors.background).toBe('#f5f5f5');
      expect(colors.backgroundWhite).toBe('#fff');
      expect(colors.text).toBe('#333');
    });

    it('returns dark colors when isDark is true', () => {
      const colors = getColors(true);
      
      // Dark mode should have dark backgrounds
      expect(colors.background).toBe('#000000');
      expect(colors.backgroundWhite).toBe('#1C1C1E');
      expect(colors.text).toBe('#F5F5F5');
    });

    it('has consistent color keys between light and dark themes', () => {
      const lightColors = getColors(false);
      const darkColors = getColors(true);
      
      const lightKeys = Object.keys(lightColors).sort();
      const darkKeys = Object.keys(darkColors).sort();
      
      expect(lightKeys).toEqual(darkKeys);
    });

    it('returns different primary colors for light and dark modes', () => {
      const light = getColors(false);
      const dark = getColors(true);
      
      // Primary colors should be slightly different for better contrast
      expect(light.primary).toBe('#007AFF');
      expect(dark.primary).toBe('#0A84FF');
    });

    it('has all required color keys', () => {
      const colors = getColors(false);
      
      // Core colors
      expect(colors).toHaveProperty('primary');
      expect(colors).toHaveProperty('danger');
      expect(colors).toHaveProperty('success');
      expect(colors).toHaveProperty('warning');
      
      // Text colors
      expect(colors).toHaveProperty('text');
      expect(colors).toHaveProperty('textMuted');
      expect(colors).toHaveProperty('textLight');
      
      // Background colors
      expect(colors).toHaveProperty('background');
      expect(colors).toHaveProperty('backgroundWhite');
      expect(colors).toHaveProperty('backgroundMuted');
      expect(colors).toHaveProperty('backgroundDisabled');
      
      // Border colors
      expect(colors).toHaveProperty('border');
      expect(colors).toHaveProperty('borderLight');
      
      // Log level colors
      expect(colors).toHaveProperty('logError');
      expect(colors).toHaveProperty('logWarn');
      expect(colors).toHaveProperty('logInfo');
      expect(colors).toHaveProperty('logDebug');
    });

    it('all color values are valid hex strings', () => {
      const colors = getColors(false);
      // Accept both 3-digit (#333) and 6-digit (#333333) hex
      const hexRegex = /^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$/;
      
      Object.entries(colors).forEach(([key, value]) => {
        expect(value).toMatch(hexRegex);
      });
    });
  });
});

