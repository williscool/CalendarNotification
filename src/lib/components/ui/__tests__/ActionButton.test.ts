import { getVariantStyle } from '../variants';
import { getColors } from '@lib/theme/colors';

/**
 * Tests for ActionButton's variant logic.
 * We test OUR logic (variant style mapping), not Gluestack's rendering.
 */
describe('ActionButton variant logic', () => {
  const lightColors = getColors(false);
  const darkColors = getColors(true);

  describe('getVariantStyle with light theme', () => {
    it('returns primary styles for primary variant', () => {
      const style = getVariantStyle('primary', false, lightColors);
      expect(style.bg).toBe(lightColors.primary);
      expect(style.textColor).toBe('#fff');
    });

    it('returns success styles for success variant', () => {
      const style = getVariantStyle('success', false, lightColors);
      expect(style.bg).toBe(lightColors.success);
      expect(style.textColor).toBe('#fff');
    });

    it('returns danger styles for danger variant', () => {
      const style = getVariantStyle('danger', false, lightColors);
      expect(style.bg).toBe(lightColors.danger);
      expect(style.textColor).toBe('#fff');
    });

    it('returns warning styles for warning variant', () => {
      const style = getVariantStyle('warning', false, lightColors);
      expect(style.bg).toBe(lightColors.warning);
      expect(style.textColor).toBe('#000'); // Dark text on yellow
    });

    it('returns secondary styles for secondary variant', () => {
      const style = getVariantStyle('secondary', false, lightColors);
      expect(style.bg).toBe('#6c757d');
      expect(style.textColor).toBe('#fff');
    });

    it('returns disabled styles when disabled=true regardless of variant', () => {
      const primaryDisabled = getVariantStyle('primary', true, lightColors);
      const dangerDisabled = getVariantStyle('danger', true, lightColors);
      const successDisabled = getVariantStyle('success', true, lightColors);

      // All should return disabled styles
      expect(primaryDisabled.bg).toBe(lightColors.backgroundDisabled);
      expect(dangerDisabled.bg).toBe(lightColors.backgroundDisabled);
      expect(successDisabled.bg).toBe(lightColors.backgroundDisabled);
      
      expect(primaryDisabled.textColor).toBe('#888');
      expect(dangerDisabled.textColor).toBe('#888');
      expect(successDisabled.textColor).toBe('#888');
    });
  });

  describe('getVariantStyle with dark theme', () => {
    it('returns dark theme primary color', () => {
      const style = getVariantStyle('primary', false, darkColors);
      expect(style.bg).toBe(darkColors.primary);
      expect(style.bg).toBe('#0A84FF'); // Dark mode blue
    });

    it('returns dark theme disabled color', () => {
      const style = getVariantStyle('primary', true, darkColors);
      expect(style.bg).toBe(darkColors.backgroundDisabled);
      expect(style.bg).toBe('#3A3A3C'); // Dark mode disabled
    });

    it('uses different colors than light theme', () => {
      const lightStyle = getVariantStyle('primary', false, lightColors);
      const darkStyle = getVariantStyle('primary', false, darkColors);
      
      expect(lightStyle.bg).not.toBe(darkStyle.bg);
    });
  });

  describe('getVariantStyle defaults to light theme', () => {
    it('works without explicit colors parameter', () => {
      const style = getVariantStyle('primary', false);
      expect(style.bg).toBe(lightColors.primary);
    });
  });
});
