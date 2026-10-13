import { randomUUID } from 'node:crypto';
import { control, imageSize } from './panel-controls.mjs';
export const SCREEN_URI = 'ui://android-use/screen-0.4.2.html';
export const SCREEN_META = { ui: { csp: { connectDomains: [], resourceDomains: [] }, prefersBorder: false }, 'openai/ui': { availableDisplayModes: ['fullscreen'], preferredDisplayMode: 'fullscreen' } };
export const PANEL_META = { 'openai/widgetSessionId': 'android-use-screen' };
export const isLocked = error => /^(Unlock the phone first|Phone is locked\. Unlock it on the device\.)$/.test(error.message);
export const connectionState = error => /Another task is already registered|already owned/i.test(error.message) ? 'occupied' : /certificate changed|Not paired|Pair this computer first/i.test(error.message) ? 'pairing_required' : 'unavailable';
const result = data => ({ content: [], structuredContent: data });

export class Preview {
  reason = 'disconnected';
  revision = 0;
  lastFrame;
  invalidate() { this.lastFrame = undefined; }
  pause(reason = 'manual') { this.reason = reason; this.revision++; this.invalidate(); }
  observe() { if (this.reason === 'disconnected') this.reason = null; }
  async resume(phone) {
    // This probe cannot invalidate the model's last actionable frame.
    await phone.open();
    await phone.request('device.preview');
    this.reason = null;
    return result({ state: 'live' });
  }
  async control(phone, args) {
    const frame = this.lastFrame;
    this.invalidate(); // Every submitted action consumes the visible frame.
    if (this.reason) throw new Error('Screen paused or disconnected. Resume and inspect first.');
    await control(phone, frame, args);
    return result({ state: 'live', action_completed: true });
  }
  async frame(phone) {
    if (this.reason) return result({ state: this.reason });
    try {
      await phone.open();
      const frame = await phone.request('device.preview');
      const size = imageSize(frame.dataUrl);
      this.lastFrame = { id: randomUUID(), receivedAt: performance.now(), ...size };
      return result({ state: 'live', ...frame, frame_id: this.lastFrame.id, ...size });
    } catch (error) {
      if (isLocked(error)) this.pause('device_locked');
      this.invalidate();
      return result({ state: this.reason ?? connectionState(error), message: error.message });
    }
  }
}

export async function unlockRequired(server, preview, phone, name, retryObservation) {
  preview.pause('device_locked');
  const revision = preview.revision;
  let confirmed = false;
  if (server.getClientCapabilities()?.elicitation?.form) {
    const reply = await server.elicitInput({
      mode: 'form', message: '请在手机上手动解锁，保持屏幕亮起。不要在聊天中输入密码。完成后选择“已完成继续”。',
      requestedSchema: { type: 'object', properties: { completion: { type: 'string', title: '解锁手机', enum: ['已完成继续'], default: '已完成继续' } }, required: ['completion'] },
    });
    confirmed = reply.action === 'accept' && reply.content?.completion === '已完成继续';
  }
  if (confirmed && preview.revision === revision && preview.reason === 'device_locked' && ['tapilot_status', 'tapilot_screen'].includes(name)) {
    // One fresh observation only. A mutation is never replayed after user input.
    try { const value = await retryObservation(); preview.reason = null; return value; }
    catch (error) { if (!isLocked(error)) throw error; }
  }
  return { isError: true, structuredContent: { state: 'device_locked' }, content: [{ type: 'text', text: JSON.stringify({
    code: confirmed ? 'fresh_observation_required' : 'phone_locked',
    needs_user_input: !confirmed,
    message: confirmed ? 'Call tapilot_screen for a fresh observation. The previous action was not retried.' : '请手动解锁手机后选择“已完成继续”。',
    host_instruction: 'If no unlock form was shown, ask using the host interactive question tool, first option 已完成继续. Wait for the actual reply, then call tapilot_screen. Never ask for the passcode or replay the previous mutation.',
  }) }] };
}
