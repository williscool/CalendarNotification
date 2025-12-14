/**
 * Pure variant logic for UI components.
 * Separated from React components so tests don't need to load Gluestack.
 */
import { colors } from '@lib/theme/colors';

// ActionButton variants
export type ButtonVariant = 'primary' | 'success' | 'danger' | 'warning' | 'secondary' | 'disabled';

const buttonVariantStyles: Record<ButtonVariant, { bg: string; textColor: string }> = {
  primary: { bg: colors.primary, textColor: '#fff' },
  success: { bg: colors.success, textColor: '#fff' },
  danger: { bg: colors.danger, textColor: '#fff' },
  warning: { bg: colors.warning, textColor: '#000' },
  secondary: { bg: '#6c757d', textColor: '#fff' },
  disabled: { bg: colors.backgroundDisabled, textColor: '#888' },
};

export const getVariantStyle = (variant: ButtonVariant, disabled: boolean) => {
  const effectiveVariant = disabled ? 'disabled' : variant;
  return buttonVariantStyles[effectiveVariant];
};

// WarningBanner variants
export type BannerVariant = 'warning' | 'error' | 'info';

const bannerVariantStyles: Record<BannerVariant, { bg: string; border: string; text: string }> = {
  warning: {
    bg: colors.warningBackground,
    border: colors.warningBorder,
    text: colors.warningText,
  },
  error: {
    bg: '#fff5f5',
    border: colors.danger,
    text: colors.danger,
  },
  info: {
    bg: colors.initializingBackground,
    border: colors.initializingBorder,
    text: colors.initializingText,
  },
};

export const getWarningVariantStyle = (variant: BannerVariant) => {
  return bannerVariantStyles[variant];
};

// SecureInput - no variant logic, just visibility toggle
export const getSecureInputState = (isVisible: boolean) => ({
  secureTextEntry: !isVisible,
  iconName: isVisible ? 'eye-off' : 'eye',
});

