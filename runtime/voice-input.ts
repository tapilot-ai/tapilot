import { WebViewNativeBridge } from './native-bridge';
import { bindHoldToSpeak } from './hold-to-speak';
import { recordingFeedback, voiceGesture } from './voice-feedback';
import { VoiceSession } from './voice-session';

export function installVoiceInput(options: {
  input: HTMLTextAreaElement; toggle: HTMLButtonElement; record: HTMLButtonElement;
  recordLabel: HTMLElement; feedback: HTMLElement; panel: HTMLElement;
  enabled: () => boolean; chinese: () => boolean; provider: () => 'volcengine' | 'deepgram';
  requireSettings: () => void; insert: (text: string) => void; send: (text: string) => Promise<void>;
}) {
  let capture: HTMLElement | undefined;
  let timeout: number | undefined;
  let errorTimeout: number | undefined;
  function setMode(voice: boolean) {
    if (session.busy || !options.enabled()) return;
    options.panel.classList.toggle('is-voice-mode', voice);
    localStorage.setItem('tapilot.inputMode', voice ? 'voice' : 'text');
    options.record.hidden = !voice;
    options.toggle.setAttribute('aria-pressed', String(voice));
    options.toggle.setAttribute('aria-label', options.chinese() ? (voice ? '切换到文字输入' : '切换到语音输入') : (voice ? 'Switch to text input' : 'Switch to voice input'));
    if (voice) options.input.blur(); else options.input.focus();
  }
  function detach() {
    capture?.classList.remove('is-recording'); capture = undefined;
  }
  const session = new VoiceSession({
    driver: () => {
      const bridge = new WebViewNativeBridge();
      return {
        start: async alive => {
          const provider = options.provider();
          const { configured } = await bridge.hasSpeechCredential(provider);
          if (!alive()) return;
          if (!configured) {
            options.requireSettings();
            throw Error(options.chinese() ? '请先保存语音转写 API 配置' : 'Save speech API settings first');
          }
          await bridge.startSpeech(provider, options.chinese() ? 'zh-CN' : 'en-US');
        },
        finish: async () => (await bridge.finishSpeech()).text,
        cancel: () => bridge.cancelSpeech(), destroy: () => bridge.destroy(),
      };
    },
    render: (phase, action, error) => {
      const active = phase !== 'idle', zh = options.chinese();
      if (!active) { detach(); window.clearTimeout(timeout); }
      window.clearTimeout(errorTimeout);
      document.body.classList.toggle('voice-recording', active);
      options.feedback.hidden = !active && !error;
      options.feedback.classList.toggle('is-recording', active);
      options.feedback.classList.toggle('will-cancel', action === 'cancel');
      options.feedback.classList.toggle('will-transcribe', action === 'send');
      options.record.disabled = phase === 'transcribing' || !options.enabled();
      options.recordLabel.textContent = zh ? '按住说话' : 'Hold to speak';
      if (active) {
        const hint = phase === 'transcribing' ? (zh ? '正在转文字…' : 'Transcribing…')
          : action === 'cancel' ? (zh ? '松手 取消' : 'Release to cancel')
          : action === 'send' ? (zh ? '松手 发送' : 'Release to send')
          : (zh ? '松手 填入输入框' : 'Release to insert text');
        recordingFeedback(options.feedback, hint, zh ? '取消' : 'Cancel', zh ? '发送' : 'Send', phase === 'transcribing',
          { cancel: () => void session.cancel(), send: () => void session.release('send') });
      } else {
        options.feedback.textContent = error ?? '';
        if (error) errorTimeout = window.setTimeout(() => { if (session.phase === 'idle') options.feedback.hidden = true; }, 5000);
      }
    },
    insert: text => {
      options.insert(text);
      // The session is still unwinding; switch the visible editor before focusing it.
      options.panel.classList.remove('is-voice-mode'); options.record.hidden = true;
      options.toggle.setAttribute('aria-pressed', 'false'); localStorage.setItem('tapilot.inputMode', 'text');
      options.input.focus();
    },
    send: options.send,
  });
  function begin(event: PointerEvent, target: HTMLElement) {
    if (!options.enabled() || session.busy) return;
    event.preventDefault();
    capture = target; target.setPointerCapture(event.pointerId); target.classList.add('is-recording');
    options.input.blur();
    session.begin(); timeout = window.setTimeout(() => void session.cancel(), 60000);
  }
  function move(event: PointerEvent) {
    if (session.phase !== 'recording') return;
    const action = voiceGesture(event.clientX, event.clientY, window.innerWidth, window.innerHeight);
    session.move(action === 'recording' ? 'insert' : action);
  }
  options.toggle.addEventListener('click', () => setMode(!options.panel.classList.contains('is-voice-mode')));
  options.record.addEventListener('pointerdown', event => begin(event, options.record));
  options.record.addEventListener('pointermove', move);
  options.record.addEventListener('pointerup', () => void session.release());
  options.record.addEventListener('pointercancel', () => void session.cancel());
  options.record.addEventListener('lostpointercapture', () => { if (session.phase === 'recording') void session.cancel(); });
  bindHoldToSpeak(options.input, {
    enabled: () => options.enabled() && !session.busy && !options.panel.classList.contains('is-voice-mode'),
    placeholder: hold => options.chinese() ? (hold ? '按住说话' : '输入消息') : (hold ? 'Hold to speak' : 'Type a message'),
    begin: event => begin(event, options.input), move,
    finish: () => void session.release(), cancel: () => void session.cancel(),
  });
  document.addEventListener('visibilitychange', () => { if (document.hidden) void session.cancel(); });
  window.addEventListener('resize', () => session.refresh());
  window.addEventListener('pagehide', () => void session.cancel());
  window.addEventListener('blur', () => void session.cancel());
  return { setMode, notify: (message: string) => {
    if (session.busy) return;
    options.feedback.textContent = message;
    options.feedback.hidden = !message;
  } };
}
