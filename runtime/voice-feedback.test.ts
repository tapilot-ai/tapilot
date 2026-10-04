import { expect, it } from '@rstest/core';
import { voiceGesture, voiceLayout } from './voice-feedback';
it('only the visible rotated controls select cancellation or sending', () => {
  for (const [width, height] of [[360, 740], [393, 873], [430, 932]]) {
    const layout = voiceLayout(width, height), y = layout.actionTop + 36;
    expect(voiceGesture(layout.cancelLeft + layout.actionWidth / 2, y, width, height)).toBe('cancel');
    expect(voiceGesture(layout.sendLeft + layout.actionWidth / 2, y, width, height)).toBe('send');
    expect(voiceGesture(width / 2, y, width, height)).toBe('recording');
    expect(voiceGesture(width * .9, height * .4, width, height)).toBe('recording');
    expect(voiceGesture(width / 2, height - 40, width, height)).toBe('recording');
  }
});
