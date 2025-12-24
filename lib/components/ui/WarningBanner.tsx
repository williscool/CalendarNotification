import React from 'react';
import { Alert, AlertText, VStack } from '@/components/ui';

// Map our variant names to Gluestack Alert action prop
type BannerVariant = 'warning' | 'error' | 'info';
const variantToAction: Record<BannerVariant, 'warning' | 'error' | 'info'> = {
  warning: 'warning',
  error: 'error',
  info: 'info',
};

interface WarningBannerProps {
  /** Simple text message (wrapped in AlertText component) */
  message?: string;
  /** Custom content (not wrapped - use for interactive elements) */
  children?: React.ReactNode;
  variant?: BannerVariant;
  testID?: string;
}

/**
 * A banner for displaying warning/error/info messages.
 * Uses Gluestack UI Alert component.
 * 
 * Two usage modes:
 * 1. Simple: <WarningBanner message="Warning text" />
 * 2. Custom: <WarningBanner><VStack>...</VStack></WarningBanner> (auto-wrapped in VStack)
 */
export const WarningBanner: React.FC<WarningBannerProps> = ({
  message,
  children,
  variant = 'warning',
  testID,
}) => {
  return (
    <Alert
      action={variantToAction[variant]}
      variant="outline"
      className="mx-4 my-4 rounded-lg justify-center"
      testID={testID}
    >
      {message ? (
        <AlertText className="text-center">{message}</AlertText>
      ) : (
        // Wrap children in VStack since Alert has flex-row layout
        <VStack className="items-center">{children}</VStack>
      )}
    </Alert>
  );
};

// Re-export AlertText for custom banner content
export { AlertText } from '@/components/ui';
