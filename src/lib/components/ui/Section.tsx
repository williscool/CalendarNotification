import React from 'react';
import { Card, Text } from '@/components/ui';
import { useTheme } from '@lib/theme/ThemeContext';

interface SectionProps {
  title?: string;
  children: React.ReactNode;
  testID?: string;
}

/**
 * A card-like container with optional title.
 * Uses Gluestack UI Card component.
 */
export const Section: React.FC<SectionProps> = ({ title, children, testID }) => {
  const { colors } = useTheme();
  
  return (
    <Card
      variant="elevated"
      className="p-4 mb-4 mx-4"
      style={{ backgroundColor: colors.backgroundWhite }}
      testID={testID}
    >
      {title && (
        <Text
          className="text-lg font-medium mb-4"
          style={{ color: colors.text }}
        >
          {title}
        </Text>
      )}
      {children}
    </Card>
  );
};
