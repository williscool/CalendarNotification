import { getWarningVariantStyle } from '../variants';
import { getColors } from '@lib/theme/colors';

/**
 * Tests for WarningBanner's variant logic.
 * We test OUR logic (variant style mapping), not Gluestack's rendering.
 */
describe('WarningBanner variant logic', () => {
  const lightColors = getColors(false);
  const darkColors = getColors(true);

  describe('getWarningVariantStyle with light theme', () => {
    it('returns warning styles for warning variant', () => {
      const style = getWarningVariantStyle('warning', lightColors);
      expect(style.bg).toBe(lightColors.warningBackground);
      expect(style.border).toBe(lightColors.warningBorder);
      expect(style.text).toBe(lightColors.warningText);
    });

    it('returns error styles for error variant', () => {
      const style = getWarningVariantStyle('error', lightColors);
      expect(style.bg).toBe(lightColors.errorBackground);
      expect(style.border).toBe(lightColors.danger);
      expect(style.text).toBe(lightColors.danger);
    });

    it('returns info styles for info variant', () => {
      const style = getWarningVariantStyle('info', lightColors);
      expect(style.bg).toBe(lightColors.initializingBackground);
      expect(style.border).toBe(lightColors.initializingBorder);
      expect(style.text).toBe(lightColors.initializingText);
    });

    it('all variants have required style properties', () => {
      const variants = ['warning', 'error', 'info'] as const;
      
      variants.forEach(variant => {
        const style = getWarningVariantStyle(variant, lightColors);
        expect(style).toHaveProperty('bg');
        expect(style).toHaveProperty('border');
        expect(style).toHaveProperty('text');
        expect(typeof style.bg).toBe('string');
        expect(typeof style.border).toBe('string');
        expect(typeof style.text).toBe('string');
      });
    });
  });

  describe('getWarningVariantStyle with dark theme', () => {
    it('returns dark theme warning colors', () => {
      const style = getWarningVariantStyle('warning', darkColors);
      expect(style.bg).toBe(darkColors.warningBackground);
      expect(style.bg).toBe('#3D3200'); // Dark mode warning bg
    });

    it('returns dark theme error colors', () => {
      const style = getWarningVariantStyle('error', darkColors);
      expect(style.bg).toBe(darkColors.errorBackground);
      expect(style.border).toBe(darkColors.danger);
    });

    it('uses different colors than light theme', () => {
      const lightStyle = getWarningVariantStyle('warning', lightColors);
      const darkStyle = getWarningVariantStyle('warning', darkColors);
      
      expect(lightStyle.bg).not.toBe(darkStyle.bg);
    });
  });

  describe('getWarningVariantStyle defaults to light theme', () => {
    it('works without explicit colors parameter', () => {
      const style = getWarningVariantStyle('warning');
      expect(style.bg).toBe(lightColors.warningBackground);
    });
  });
});
