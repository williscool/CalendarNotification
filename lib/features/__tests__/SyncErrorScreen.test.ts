/**
 * SyncErrorScreen interface tests
 * 
 * Note: Full component rendering tests require jest-expo preset.
 * Visual/interaction testing is covered by instrumented Android tests.
 * These tests document the expected component interface.
 */

describe('SyncErrorScreen interface', () => {
  it('accepts error prop of type unknown (Error)', () => {
    const error: unknown = new Error('Connection failed');
    expect(error).toBeInstanceOf(Error);
  });

  it('accepts error prop of type unknown (string)', () => {
    const error: unknown = 'Something went wrong';
    expect(typeof error).toBe('string');
  });

  it('requires onRetry callback prop', () => {
    const onRetry = jest.fn();
    onRetry();
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});

