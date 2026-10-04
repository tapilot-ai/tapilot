export type VoiceGesture = 'recording' | 'cancel' | 'send';

export function voiceLayout(width: number, height: number) {
  const actionTop = height - Math.min(214, height * .25);
  const actionWidth = width * .49;
  return { actionTop, actionWidth, cancelLeft: -12, sendLeft: width - actionWidth + 12,
    actionHeight: 72, bubbleTop: height * .3, bubbleHeight: Math.max(100, actionTop - 56 - height * .3),
    arcTop: height - Math.min(124, height * .145) };
}
export function voiceGesture(x: number, y: number, width: number, height: number): VoiceGesture {
  const layout = voiceLayout(width, height);
  for (const [action, left, degrees] of [['cancel', layout.cancelLeft, -10], ['send', layout.sendLeft, 10]] as const) {
    const angle = degrees * Math.PI / 180;
    const dx = x - left - layout.actionWidth / 2, dy = y - layout.actionTop - layout.actionHeight / 2;
    const localX = dx * Math.cos(angle) + dy * Math.sin(angle);
    const localY = -dx * Math.sin(angle) + dy * Math.cos(angle);
    if (Math.abs(localX) <= layout.actionWidth / 2 + 8 && Math.abs(localY) <= layout.actionHeight / 2 + 8) return action;
  }
  return 'recording';
}

const bars = [6, 7, 6, 8, 7, 8, 10, 12, 14, 15, 14, 13, 15, 14, 12, 10, 8, 7, 8, 6, 7, 6, 6, 6];
export function recordingFeedback(host: HTMLElement, hint: string, cancelLabel: string, sendLabel: string,
  finishing = false, callbacks?: { cancel: () => void; send: () => void }): void {
  if (!host.querySelector('.voice-bubble')) {
    host.innerHTML = `<div class="voice-bubble-group"><div class="voice-bubble"><div class="voice-wave" aria-hidden="true">${bars.map((height, i) =>
      `<i style="height:${height}px;animation-delay:${-i * .09}s"></i>`).join('')}</div></div><div class="voice-release-hint"></div></div>
      <button type="button" class="voice-slide-target voice-slide-cancel"></button>
      <button type="button" class="voice-slide-target voice-slide-transcribe"></button><div class="voice-hold-surface" aria-hidden="true"></div>`;
  }
  const layout = voiceLayout(window.innerWidth, window.innerHeight);
  host.style.setProperty('--voice-action-top', `${layout.actionTop}px`);
  host.style.setProperty('--voice-bubble-top', `${layout.bubbleTop}px`);
  host.style.setProperty('--voice-bubble-height', `${layout.bubbleHeight}px`);
  host.style.setProperty('--voice-arc-top', `${layout.arcTop}px`);
  host.querySelector<HTMLElement>('.voice-release-hint')!.textContent = hint;
  const cancel = host.querySelector<HTMLButtonElement>('.voice-slide-cancel')!;
  const send = host.querySelector<HTMLButtonElement>('.voice-slide-transcribe')!;
  cancel.textContent = cancelLabel; cancel.onclick = callbacks?.cancel ?? null;
  send.textContent = sendLabel; send.disabled = finishing; send.onclick = callbacks?.send ?? null;
  host.classList.toggle('is-transcribing', finishing);
}
