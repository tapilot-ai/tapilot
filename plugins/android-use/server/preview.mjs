import { t } from '../ui/i18n.mjs';
import { randomUUID } from 'node:crypto';
import { control, imageSize } from './panel-controls.mjs';
export const SCREEN_URI = 'ui://android-use/screen-0.6.0.html';
export const SCREEN_META = { ui: { csp: { connectDomains: [], resourceDomains: [] }, prefersBorder: false }, 'openai/ui': { availableDisplayModes: ['fullscreen'], preferredDisplayMode: 'fullscreen' } };
export const PANEL_META = { 'openai/widgetSessionId': 'android-use-screen' };
export const isLocked = error => /^(Unlock the phone first|Phone is locked\. Unlock it on the device\.)$/.test(error.message);
export const needsHostReload = error => ['ERR_MODULE_NOT_FOUND', 'MODULE_NOT_FOUND'].includes(error.code) || /updated while this session|Worker files or dependencies are missing/.test(error.message);
export const connectionMessage = error => needsHostReload(error) ? t("Android Use 已更新或安装文件缺失。请重载电脑上的插件或客户端；重启手机不会修复此问题。") : error.message;
export const connectionState = error => needsHostReload(error) ? 'host_reload_required' : /Enable Tapilot device control in Accessibility settings/i.test(error.message) ? 'accessibility_required' : error.code === 'setup_required' ? 'setup_required' : /Another task is already registered|already owned/i.test(error.message) ? 'occupied' : /certificate changed|Not paired|Pair this computer first/i.test(error.message) ? 'pairing_required' : 'unavailable';
const result = data => ({ content: [], structuredContent: data });

export class Preview {
  // An existing panel may resume polling after the host restarts.
  // Only an explicit disconnect should leave it waiting for Reconnect.
  reason = null;
  revision = 0;
  lastFrame;
  recentFrames = [];
  invalidate() { this.lastFrame = undefined; this.recentFrames = []; }
  invalidateViewer(viewerId = 'default') { this.recentFrames = this.recentFrames.filter(f => f.viewerId !== viewerId); }
  pause(reason = 'manual') { this.reason = reason; this.revision++; this.invalidate(); }
  observe() { if (this.reason === 'disconnected') this.reason = null; }
  reconnect() {
    this.revision++; this.invalidate();
    if (!['manual', 'device_locked'].includes(this.reason)) this.reason = null;
  }
  async resume(phone) {
    const revision = this.revision;
    // This probe cannot invalidate the model's last actionable frame.
    await phone.open();
    await phone.request('device.preview', phone.features?.jpegPreview ? {format:'jpeg'} : {});
    if (revision !== this.revision) return result({ state: this.reason ?? 'disconnected' });
    this.reason = null;
    return result({ state: 'live' });
  }
  async control(phone, args) {
    const frame = this.recentFrames.find(frame => frame.id === args.frame_id && frame.viewerId === (args.viewer_id ?? 'default'));
    this.invalidate(); // Every submitted action consumes the visible frame.
    if (this.reason) throw new Error('Screen paused or disconnected. Resume and inspect first.');
    const start = performance.now();
    const revision = this.revision;
    await control(phone, frame, args, () => {
      if (revision !== this.revision || this.reason) throw new Error('Screen paused or disconnected. Resume and inspect first.');
    });
    const actionMs = performance.now() - start;
    // One MCP response includes the next actionable frame; no extra host round trip.
    const next = await this.frame(phone, args.viewer_id);
    return result({...next.structuredContent, action_completed:true, timing:{action_ms:Math.round(actionMs),refresh_ms:Math.round(performance.now()-start-actionMs)}});
  }
  async frame(phone, viewerId = 'default') {
    if (this.reason) return result({ state: this.reason });
    const revision = this.revision;
    try {
      await phone.open();
      const frame = await phone.request('device.preview', phone.features?.jpegPreview ? {format:'jpeg'} : {});
      if (phone.config && ['en', 'zh'].includes(frame.language)) phone.config.language = frame.language;
      if (revision !== this.revision) return result({ state: this.reason ?? 'disconnected' });
      const size = imageSize(frame.dataUrl);
      this.lastFrame = { id: randomUUID(), viewerId, receivedAt: performance.now(), ...size };
      // Keep two recent frames per window: other viewers must not evict the
      // image a user is clicking. Mutations still consume every viewer's frames.
      const recent = this.recentFrames.filter(f => performance.now() - f.receivedAt <= 10000);
      const own = recent.filter(f => f.viewerId === viewerId).slice(0, 1);
      this.recentFrames = [this.lastFrame, ...own, ...recent.filter(f => f.viewerId !== viewerId)].slice(0, 64);
      return result({ state: 'live', ...frame, frame_id: this.lastFrame.id, ...size });
    } catch (error) {
      if (revision !== this.revision) return result({ state: this.reason ?? 'disconnected' });
      if (isLocked(error)) this.pause('device_locked');
      this.invalidate();
      return result({ state: this.reason ?? connectionState(error), message: connectionMessage(error) });
    }
  }
}

export async function unlockRequired(server, preview, phone, name, retryObservation) {
  preview.pause('device_locked');
  const revision = preview.revision;
  const continueLabel = t('已完成继续');
  let confirmed = false;
  if (server.getClientCapabilities()?.elicitation?.form) {
    const reply = await server.elicitInput({
      mode: 'form', message: t("请在手机上手动解锁，保持屏幕亮起。不要在聊天中输入密码。完成后选择“已完成继续”。"),
      requestedSchema: { type: 'object', properties: { completion: { type: 'string', title: t("解锁手机"), enum: [continueLabel], default: continueLabel } }, required: ['completion'] },
    });
    confirmed = reply.action === 'accept' && reply.content?.completion === continueLabel;
  }
  if (confirmed && preview.revision === revision && preview.reason === 'device_locked' && ['tapilot_status', 'tapilot_screen'].includes(name)) {
    // One fresh observation only. A mutation is never replayed after user input.
    try {
      const value = await retryObservation();
      if (preview.revision !== revision) return result({ state: preview.reason ?? 'disconnected' });
      preview.reason = null; return value;
    }
    catch (error) { if (!isLocked(error)) throw error; }
  }
  return { isError: true, structuredContent: { state: 'device_locked' }, content: [{ type: 'text', text: JSON.stringify({
    code: confirmed ? 'fresh_observation_required' : 'phone_locked',
    needs_user_input: !confirmed,
    message: confirmed ? 'Call tapilot_screen for a fresh observation. The previous action was not retried.' : t("请手动解锁手机后选择“已完成继续”。"),
    host_instruction: 'If no unlock form was shown, ask using the host interactive question tool, first option Continue (or its localized label). Wait for the actual reply, then call tapilot_screen. Never ask for the passcode or replay the previous mutation.',
  }) }] };
}
