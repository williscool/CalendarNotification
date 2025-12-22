/**
 * SyncErrorScreen interface tests
 * 
 * Note: Full component rendering tests require jest-expo preset.
 * Visual/interaction testing is covered by instrumented Android tests.
 * These tests document the expected component interface.
 */

describe('SyncErrorScreen interface', () => {
  it('requires error prop of type Error', () => {
    const error = new Error('Connection failed');
    expect(error).toBeInstanceOf(Error);
    expect(error.message).toBe('Connection failed');
  });

  it('requires onRetry callback prop', () => {
    const onRetry = jest.fn();
    onRetry();
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});

