import { describe, expect, it } from '@rstest/core';
import { resolveInitialModelConfigOpen } from './model-config-visibility';

describe('resolveInitialModelConfigOpen', () => {
  it('respects an explicit collapsed preference even when configuration is incomplete', () => {
    expect(resolveInitialModelConfigOpen('false', false)).toBe(false);
  });

  it('respects an explicit expanded preference', () => {
    expect(resolveInitialModelConfigOpen('true', true)).toBe(true);
  });

  it('opens only on first use when the loaded configuration is incomplete', () => {
    expect(resolveInitialModelConfigOpen(null, false)).toBe(true);
    expect(resolveInitialModelConfigOpen(null, true)).toBe(false);
  });
});
