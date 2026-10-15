import { describe, expect, it } from '@rstest/core';
import { resolveAccessibilityStatus } from './accessibility-status';

describe('accessibility status', () => {
  it('distinguishes an enabled but disconnected service from a missing grant', () => {
    expect(resolveAccessibilityStatus(false, true)).toBe('disconnected');
    expect(resolveAccessibilityStatus(false, false)).toBe('not-enabled');
  });

  it('uses the live connection as the source of truth', () => {
    expect(resolveAccessibilityStatus(true, true)).toBe('connected');
    expect(resolveAccessibilityStatus(true, false)).toBe('connected');
  });
});
