/**
 * Hooks for UI components.
 * Separated from React components so tests don't need to load Gluestack.
 */
import { useState, useCallback } from 'react';

/**
 * Hook for managing secure input visibility state.
 * Useful when you need to control multiple secure inputs or access visibility state externally.
 */
export const useSecureInputVisibility = (initialVisible = false) => {
  const [isVisible, setIsVisible] = useState(initialVisible);
  
  const toggle = useCallback(() => {
    setIsVisible(prev => !prev);
  }, []);

  return {
    isVisible,
    setIsVisible,
    toggle,
  };
};

