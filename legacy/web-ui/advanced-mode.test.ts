import { describe, expect, it } from '@rstest/core';
import {
  resolveAdvancedMode,
  resolveRunKindForAdvancedMode,
} from './advanced-mode';

describe('advanced mode', () => {
  it('is disabled by default', () => {
    expect(resolveAdvancedMode(null)).toBe(false);
    expect(resolveAdvancedMode('false')).toBe(false);
  });

  it('restores an explicitly enabled preference', () => {
    expect(resolveAdvancedMode('true')).toBe(true);
  });

  it('forces natural language when advanced mode is disabled', () => {
    expect(resolveRunKindForAdvancedMode('yaml', false)).toBe('task');
    expect(resolveRunKindForAdvancedMode('yaml', true)).toBe('yaml');
  });
});
