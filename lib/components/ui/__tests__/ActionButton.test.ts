import { getVariantStyle } from '../ActionButton';
import { colors } from '@lib/theme/colors';

/**
 * Tests for ActionButton's variant logic.
 * We test OUR logic (variant style mapping), not Gluestack's rendering.
 */
describe('ActionButton variant logic', () => {
  describe('getVariantStyle', () => {
    it('returns primary styles for primary variant', () => {
      const style = getVariantStyle('primary', false);
      expect(style.bg).toBe(colors.primary);
      expect(style.textColor).toBe('#fff');
    });

    it('returns success styles for success variant', () => {
      const style = getVariantStyle('success', false);
      expect(style.bg).toBe(colors.success);
      expect(style.textColor).toBe('#fff');
    });

    it('returns danger styles for danger variant', () => {
      const style = getVariantStyle('danger', false);
      expect(style.bg).toBe(colors.danger);
      expect(style.textColor).toBe('#fff');
    });

    it('returns warning styles for warning variant', () => {
      const style = getVariantStyle('warning', false);
      expect(style.bg).toBe(colors.warning);
      expect(style.textColor).toBe('#000'); // Dark text on yellow
    });

    it('returns secondary styles for secondary variant', () => {
      const style = getVariantStyle('secondary', false);
      expect(style.bg).toBe('#6c757d');
      expect(style.textColor).toBe('#fff');
    });

    it('returns disabled styles when disabled=true regardless of variant', () => {
      const primaryDisabled = getVariantStyle('primary', true);
      const dangerDisabled = getVariantStyle('danger', true);
      const successDisabled = getVariantStyle('success', true);

      // All should return disabled styles
      expect(primaryDisabled.bg).toBe(colors.backgroundDisabled);
      expect(dangerDisabled.bg).toBe(colors.backgroundDisabled);
      expect(successDisabled.bg).toBe(colors.backgroundDisabled);
      
      expect(primaryDisabled.textColor).toBe('#888');
      expect(dangerDisabled.textColor).toBe('#888');
      expect(successDisabled.textColor).toBe('#888');
    });

    it('returns correct styles when disabled=false', () => {
      const style = getVariantStyle('primary', false);
      expect(style.bg).not.toBe(colors.backgroundDisabled);
    });
  });
});

