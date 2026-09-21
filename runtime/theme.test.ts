import { describe, expect, it } from '@rstest/core';
import { resolveTheme, resolveThemePreference } from './theme';

describe('theme preference', () => {
  it('defaults unknown and missing preferences to system', () => {
    expect(resolveThemePreference(null)).toBe('system');
    expect(resolveThemePreference('sepia')).toBe('system');
  });

  it('resolves system preference from the current color scheme', () => {
    expect(resolveTheme('system', true)).toBe('dark');
    expect(resolveTheme('system', false)).toBe('light');
  });

  it('keeps an explicit light or dark choice', () => {
    expect(resolveTheme('light', true)).toBe('light');
    expect(resolveTheme('dark', false)).toBe('dark');
  });
});
