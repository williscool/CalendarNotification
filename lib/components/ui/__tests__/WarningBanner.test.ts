import { getWarningVariantStyle } from '../WarningBanner';
import { colors } from '@lib/theme/colors';

/**
 * Tests for WarningBanner's variant logic.
 * We test OUR logic (variant style mapping), not Gluestack's rendering.
 */
describe('WarningBanner variant logic', () => {
  describe('getWarningVariantStyle', () => {
    it('returns warning styles for warning variant', () => {
      const style = getWarningVariantStyle('warning');
      expect(style.bg).toBe(colors.warningBackground);
      expect(style.border).toBe(colors.warningBorder);
      expect(style.text).toBe(colors.warningText);
    });

    it('returns error styles for error variant', () => {
      const style = getWarningVariantStyle('error');
      expect(style.bg).toBe('#fff5f5');
      expect(style.border).toBe(colors.danger);
      expect(style.text).toBe(colors.danger);
    });

    it('returns info styles for info variant', () => {
      const style = getWarningVariantStyle('info');
      expect(style.bg).toBe(colors.initializingBackground);
      expect(style.border).toBe(colors.initializingBorder);
      expect(style.text).toBe(colors.initializingText);
    });

    it('all variants have required style properties', () => {
      const variants = ['warning', 'error', 'info'] as const;
      
      variants.forEach(variant => {
        const style = getWarningVariantStyle(variant);
        expect(style).toHaveProperty('bg');
        expect(style).toHaveProperty('border');
        expect(style).toHaveProperty('text');
        expect(typeof style.bg).toBe('string');
        expect(typeof style.border).toBe('string');
        expect(typeof style.text).toBe('string');
      });
    });
  });
});

