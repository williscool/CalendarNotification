import { renderHook, act } from '@testing-library/react-hooks';
import { useSecureInputVisibility } from '../SecureInput';

/**
 * Tests for SecureInput's visibility toggle logic.
 * We test OUR hook logic, not Gluestack's Input component.
 */
describe('SecureInput visibility logic', () => {
  describe('useSecureInputVisibility hook', () => {
    it('starts with visibility false by default', () => {
      const { result } = renderHook(() => useSecureInputVisibility());
      expect(result.current.isVisible).toBe(false);
    });

    it('starts with visibility true when initialVisible=true', () => {
      const { result } = renderHook(() => useSecureInputVisibility(true));
      expect(result.current.isVisible).toBe(true);
    });

    it('toggles visibility from false to true', () => {
      const { result } = renderHook(() => useSecureInputVisibility());
      
      expect(result.current.isVisible).toBe(false);
      
      act(() => {
        result.current.toggle();
      });
      
      expect(result.current.isVisible).toBe(true);
    });

    it('toggles visibility from true to false', () => {
      const { result } = renderHook(() => useSecureInputVisibility(true));
      
      expect(result.current.isVisible).toBe(true);
      
      act(() => {
        result.current.toggle();
      });
      
      expect(result.current.isVisible).toBe(false);
    });

    it('allows direct setIsVisible control', () => {
      const { result } = renderHook(() => useSecureInputVisibility());
      
      act(() => {
        result.current.setIsVisible(true);
      });
      expect(result.current.isVisible).toBe(true);
      
      act(() => {
        result.current.setIsVisible(false);
      });
      expect(result.current.isVisible).toBe(false);
    });

    it('toggle function is stable across rerenders', () => {
      const { result, rerender } = renderHook(() => useSecureInputVisibility());
      
      const firstToggle = result.current.toggle;
      rerender();
      const secondToggle = result.current.toggle;
      
      expect(firstToggle).toBe(secondToggle);
    });
  });
});

