import React from 'react';
import { Box, Heading } from '@gluestack-ui/themed';

interface SectionProps {
  title?: string;
  children: React.ReactNode;
  testID?: string;
}

/**
 * A card-like container with optional title.
 * Replaces the repeated styles.section pattern across screens.
 */
export const Section: React.FC<SectionProps> = ({ title, children, testID }) => {
  return (
    <Box
      bg="$white"
      p="$4"
      mb="$4"
      mx="$4"
      borderRadius="$lg"
      testID={testID}
    >
      {title && (
        <Heading size="md" mb="$4" color="$textDark900">
          {title}
        </Heading>
      )}
      {children}
    </Box>
  );
};

