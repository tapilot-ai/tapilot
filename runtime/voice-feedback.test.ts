import { describe, expect, it } from '@rstest/core';
import { voiceGesture } from './voice-feedback';

describe('voice recording slide targets', () => {
  it('keeps recording until a deliberate upward slide', () => {
    expect(voiceGesture(800, 350, 750, 400)).toBe('recording');
  });
  it('arms cancellation above the input and returns to recording when moved back', () => {
    expect(voiceGesture(800, 200, 650, 400)).toBe('cancel');
    expect(voiceGesture(800, 200, 790, 400)).toBe('recording');
  });
  it('selects a transcript draft when sliding into the right target', () => {
    expect(voiceGesture(800, 350, 650, 400)).toBe('transcribe');
  });
});
