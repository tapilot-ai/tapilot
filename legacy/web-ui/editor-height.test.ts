import { describe, expect, it } from '@rstest/core';
import {
  MAX_EDITOR_HEIGHT,
  MIN_EDITOR_HEIGHT,
  editorHeightForContent,
} from './editor-height';

describe('editorHeightForContent', () => {
  it('uses a compact minimum height for short tasks', () => {
    expect(editorHeightForContent(60)).toBe(MIN_EDITOR_HEIGHT);
  });

  it('grows with the content until the maximum height', () => {
    expect(editorHeightForContent(196.2)).toBe(197);
    expect(editorHeightForContent(800)).toBe(MAX_EDITOR_HEIGHT);
  });
});
