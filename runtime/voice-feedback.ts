export type VoiceGesture = 'recording' | 'cancel' | 'transcribe';

export function voiceGesture(startY: number, x: number, y: number, width: number): VoiceGesture {
  if (startY - y <= 72) return 'recording';
  return x > width * .62 ? 'transcribe' : 'cancel';
}

const bars = [6, 7, 6, 8, 7, 8, 10, 12, 14, 15, 14, 13, 15, 14, 12, 10, 8, 7, 8, 6, 7, 6, 6, 6];

export function recordingFeedback(host: HTMLElement, hint: string, cancelLabel: string, transcribeLabel: string): void {
  if (!host.querySelector('.voice-bubble')) {
    host.innerHTML = `<div class="voice-bubble"><div class="voice-wave" aria-hidden="true">${bars.map((height, i) =>
      `<i style="height:${height}px;animation-delay:${-i * .09}s"></i>`).join('')}</div></div>
      <div class="voice-release-hint"></div><div class="voice-slide-target voice-slide-cancel"></div>
      <div class="voice-slide-target voice-slide-transcribe"></div><div class="voice-hold-surface" aria-hidden="true"></div>`;
  }
  host.querySelector<HTMLElement>('.voice-release-hint')!.textContent = hint;
  host.querySelector<HTMLElement>('.voice-slide-cancel')!.textContent = cancelLabel;
  host.querySelector<HTMLElement>('.voice-slide-transcribe')!.textContent = transcribeLabel;
}
