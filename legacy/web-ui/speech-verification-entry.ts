/** Optional verification entry: production interactions with a synthetic speech bridge. */
import './brand.generated.css';
import './style.css';
import './voice-input.css';
import { installVoiceInput } from './voice-input';

const query = new URLSearchParams(location.search);
globalThis.MidsceneNative = { postMessage(payload) {
  const request = JSON.parse(payload) as { requestId: string; method: string };
  const result = request.method === 'speech.hasCredential' ? { configured: true }
    : request.method === 'speech.finish' ? { text: '语音测试文字' } : null;
  const delay = request.method === 'speech.finish' ? 1600 : request.method === 'speech.start' ? Number(query.get('startDelay') ?? 0) : 0;
  setTimeout(() => globalThis.__midsceneNativeResolve?.(JSON.stringify({ requestId: request.requestId, ok: true, result })), delay);
} };
document.body.innerHTML = `<div class="chat-app" style="height:100dvh;padding:24px;box-sizing:border-box;background:var(--background);display:flex;flex-direction:column">
  <h2>Tapilot</h2><div id="sent" style="color:magenta">验证发送次数 0</div>
  <div style="flex:1;display:grid;place-items:center">有什么需要帮忙？</div>
  <div id="panel" style="display:flex;gap:8px;align-items:center;padding-bottom:24px">
    <button id="toggle" type="button">语音</button>
    <textarea id="input" style="flex:1;min-height:48px;padding:12px;font-size:16px;border-radius:16px"></textarea>
    <button id="record" class="voice-record" hidden><span id="label">按住说话</span></button>
  </div><div id="feedback" class="voice-state" hidden></div></div>`;
const element = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const input = element<HTMLTextAreaElement>('input');
let sends = 0;
installVoiceInput({ input, toggle: element('toggle'), record: element('record'), recordLabel: element('label'),
  feedback: element('feedback'), panel: element('panel'), enabled: () => true, chinese: () => true,
  provider: () => 'volcengine', requireSettings: () => {}, insert: text => { input.value = text; input.dispatchEvent(new Event('input')); },
  send: async () => { element('sent').textContent = `验证发送次数 ${++sends}`; },
});
