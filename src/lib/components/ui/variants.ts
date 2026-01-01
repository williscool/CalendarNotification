/**
 * Pure variant logic for UI components.
 * Separated from React components so tests don't need to load Gluestack.
 */
import { getColors, ThemeColors } from '@lib/theme/colors';

// ActionButton variants
export type ButtonVariant = 'primary' | 'success' | 'danger' | 'warning' | 'secondary' | 'disabled';

export const getVariantStyle = (variant: ButtonVariant, disabled: boolean, colors?: ThemeColors) => {
  const c = colors ?? getColors(false); // Default to light for tests
  
  const buttonVariantStyles: Record<ButtonVariant, { bg: string; textColor: string }> = {
    primary: { bg: c.primary, textColor: '#fff' },
    success: { bg: c.success, textColor: '#fff' },
    danger: { bg: c.danger, textColor: '#fff' },
    warning: { bg: c.warning, textColor: '#000' },
    secondary: { bg: '#6c757d', textColor: '#fff' },
    disabled: { bg: c.backgroundDisabled, textColor: '#888' },
  };
  
  const effectiveVariant = disabled ? 'disabled' : variant;
  return buttonVariantStyles[effectiveVariant];
};

// WarningBanner variants
export type BannerVariant = 'warning' | 'error' | 'info';

export const getWarningVariantStyle = (variant: BannerVariant, colors?: ThemeColors) => {
  const c = colors ?? getColors(false); // Default to light for tests
  
  const bannerVariantStyles: Record<BannerVariant, { bg: string; border: string; text: string }> = {
    warning: {
      bg: c.warningBackground,
      border: c.warningBorder,
      text: c.warningText,
    },
    error: {
      bg: c.errorBackground,
      border: c.danger,
      text: c.danger,
    },
    info: {
      bg: c.initializingBackground,
      border: c.initializingBorder,
      text: c.initializingText,
    },
  };
  
  return bannerVariantStyles[variant];
};

