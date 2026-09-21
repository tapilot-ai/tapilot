import { describe, expect, it } from '@rstest/core';
import { shouldDismissBottomSheet } from './bottom-sheet';

describe('bottom sheet dismissal', () => {
  it('dismisses after a deliberate long drag', () => {
    expect(shouldDismissBottomSheet(130, 600, 700)).toBe(true);
  });

  it('dismisses after a short fast flick', () => {
    expect(shouldDismissBottomSheet(50, 60, 700)).toBe(true);
  });

  it('springs back after a short slow drag', () => {
    expect(shouldDismissBottomSheet(50, 500, 700)).toBe(false);
  });
});
