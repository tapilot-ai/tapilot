import { t, setLanguage, localize } from './i18n.mjs';
import { BrowserApp } from './browser-app.mjs';
import { App } from '@modelcontextprotocol/ext-apps';
import { imagePoint } from './geometry.mjs';
import { PreviewActivity } from './preview-activity.mjs';
const app = globalThis.__ANDROID_USE_BROWSER__ ? new BrowserApp() : new App({ name: 'Android Use', version: '0.6.1' }, {});
const viewerId = crypto.randomUUID();
const callServerTool = app.callServerTool.bind(app);
app.callServerTool = request => {
  const scoped = ['tapilot_preview_frame', 'tapilot_preview_control', 'tapilot_preview'].includes(request.name);
  return callServerTool(scoped ? {...request, arguments:{...request.arguments, viewer_id:viewerId}} : request);
};
const image = document.querySelector('img');
const status = document.querySelector('[role=status]');
const resume = document.querySelector('#resume');
const pause = document.querySelector('#pause');
const disconnect = document.querySelector('#disconnect');
const notice = document.querySelector('[role=alert]');
for (const link of document.querySelectorAll('.installation a')) {
  link.onclick = async event => {
    event.preventDefault();
    try {
      const result = await app.openLink({url: link.href});
      if (result?.isError) throw new Error('open failed');
    } catch {
      notice.textContent = t('无法打开下载链接，请用手机扫描下载二维码。'); notice.hidden = false;
    }
  };
}
const recovery = document.querySelector('#recovery');
const pair = document.querySelector('#pair');
const pairingLink = document.querySelector('#pairing-link');
const pairingQr = document.querySelector('#pairing-qr');
const pairingStatus = document.querySelector('#pairing-status');
const pairConfirm = document.querySelector('#pair-confirm');
const devicesPanel = document.querySelector('#devices-panel');
const devicesSelect = document.querySelector('#devices-select');
const devicesStatus = document.querySelector('#devices-status');
const deviceForgetConfirm = document.querySelector('#device-forget-confirm');
let lastDeviceRefresh = 0, loadingDevices = false;
async function refreshDevices() {
  if (loadingDevices) return;
  loadingDevices = true;
  try {
    const result = await app.callServerTool({name:'tapilot_devices',arguments:{action:'list'}});
    if (result.isError) throw Error('Device list unavailable');
    const devices = result.structuredContent?.devices ?? [];
    const chosen = devicesSelect.value;
    devicesSelect.replaceChildren();
    for (const device of devices) {
      const option = document.createElement('option');
      option.value = device.id;
      const label = {connected:t('已连接'),not_connected:t('未连接'),disconnected:t('已主动断开')}[device.state];
      option.textContent = `${device.name} · ${label}`;
      option.selected = devices.some(d => d.id === chosen) ? device.id === chosen : device.selected;
      devicesSelect.append(option);
    }
    devicesStatus.textContent = devices.length ? t('配对由此电脑上的各个 agent 共享。') : t('没有已配对设备');
    deviceForgetConfirm.hidden = true;
    lastDeviceRefresh = Date.now();
  } catch { devicesStatus.textContent = t('无法读取设备列表'); }
  finally { loadingDevices = false; syncControls(); }
}
devicesPanel.ontoggle = () => { if (devicesPanel.open) refreshDevices(); };
document.querySelector('#devices-refresh').onclick = refreshDevices;
devicesSelect.onchange = () => { deviceForgetConfirm.hidden = true; };
async function deviceAction(command) {
  if (busy || !devicesSelect.value) return;
  await action('tapilot_devices', {action:command,device_id:devicesSelect.value});
  await refreshDevices();
}
document.querySelector('#device-connect').onclick = () => deviceAction('connect');
document.querySelector('#device-disconnect').onclick = () => deviceAction('disconnect');
document.querySelector('#device-forget').onclick = () => { deviceForgetConfirm.hidden = false; };
deviceForgetConfirm.onclick = () => deviceAction('forget');
let hostLocale, lastData;
function applyLocale(data) {
  const locale = hostLocale || app.getHostContext?.()?.locale || data?.language || globalThis.navigator?.language || 'en';
  setLanguage(/^zh(?:[-_]|$)/i.test(locale) ? 'zh' : 'en'); localize();
}
let pairingExpiresAt = 0;
function clearPairingQr() { pairingQr.hidden = true; pairingQr.removeAttribute('src'); pairConfirm.hidden = true; pairingStatus.hidden = true; }
const controls = [...document.querySelectorAll('[data-control]')];
const form = document.querySelector('form');
const text = document.querySelector('textarea');
let stopped = false, timer, busy = false, polling = false, epoch = 0, frame, displayedFrame, pointer, state = 'disconnected';
let windowFocused = true;
const activity = new PreviewActivity({
  graceMs: 0, setTimer: setTimeout, clearTimer: clearTimeout,
  onSuspend: () => { ++epoch; clearTimeout(timer); pointer = undefined; clear(); syncControls();
    app.callServerTool({name:'tapilot_preview',arguments:{action:'suspend'}}).catch(() => {});
  },
  onResume: () => { ++epoch; pointer = undefined; clear(); syncControls(); schedule(0); },
});
function updateActivity() { activity.update(!document.hidden && windowFocused); }

const labels = () => ({
  accessibility_required: t("手机的设备控制服务未连接"),
  host_reload_required: t("请重载电脑上的 Android Use 插件"),
  setup_required: t("尚未配对手机"),
  live: t("已连接 · 点击或拖动屏幕操作"), disconnected: t("已断开，点击重新连接"),
  manual: t("已暂停，请在手机上完成私密操作"), device_locked: t("请在手机上解锁，画面会自动恢复"),
  occupied: t("手机正被另一个控制端使用，请先结束那边的控制"),
  pairing_required: t("配对已失效，请在手机的无线控制设置中重新配对"),
  unavailable: t("无法连接手机，请保持解锁并检查 Wi-Fi 和无线控制开关"),
});
function syncControls() {
  for (const el of controls) el.disabled = busy || !!pointer || state !== 'live' || !displayedFrame;
  resume.disabled = pause.disabled = disconnect.disabled = pair.disabled = busy;
  devicesSelect.disabled = busy || loadingDevices;
  for (const id of ['#device-connect', '#device-disconnect', '#device-forget', '#device-forget-confirm'])
    document.querySelector(id).disabled = busy || loadingDevices || !devicesSelect.value;
  image.setAttribute('aria-disabled', String(busy || !!pointer || state !== 'live' || !displayedFrame));
}
function clear() { frame = displayedFrame = undefined; image.onload = image.onerror = null; image.removeAttribute('src'); image.hidden = true; }
function render(data) {
  lastData = data; applyLocale(data);
  if (stopped || document.hidden || !windowFocused) { clear(); syncControls(); return; }
  if (!pairingQr.hidden && Date.now() < pairingExpiresAt) return;
  state = data?.state ?? 'unavailable';
  if (state === 'live' && !pairingQr.hidden) { notice.hidden = true; notice.textContent = ''; }
  if (state === 'live' || Date.now() >= pairingExpiresAt) clearPairingQr();
  if (state === 'live') { pairingLink.hidden = true; pairingLink.removeAttribute('href'); }
  status.textContent = (labels()[state] ?? labels().unavailable) + (data?.device_host ? ` · ${data.device_host}` : '');
  resume.hidden = ['live', 'host_reload_required', 'device_locked'].includes(state);
  pair.hidden = !pairingQr.hidden || !['setup_required', 'pairing_required', 'unavailable'].includes(state);
  recovery.hidden = state === 'live';
  const installation = document.querySelector('#installation');
  if (installation) installation.hidden = !['setup_required', 'pairing_required', 'unavailable'].includes(state);
  recovery.textContent = ({
    accessibility_required: t("请在手机系统辅助功能中开启 Tapilot 设备控制；若开关已开启，请关闭再开启。恢复后会自动连接，无需重新配对。"),
    host_reload_required: t("插件已更新或安装文件缺失。重载插件或重新打开客户端后继续，无需重新配对手机。"),
    setup_required: t("在手机 Tapilot 设置中开启无线控制，再点击下方按钮，用手机扫描电脑二维码。无需 USB。"),
    pairing_required: t("在手机上重新扫描电脑二维码，并在电脑窗口确认。完成后点击重新连接。"),
    unavailable: t("请检查手机无线控制已开启、两端 Wi-Fi 可互通。恢复后点击重新连接；不会重放之前的操作。"),
    occupied: t("请先在另一控制端结束任务或释放手机，然后重新连接。"),
    device_locked: t("保持手机解锁，预览会自动恢复，无需刷新连接。"),
    manual: t("画面已隐藏。完成手机上的私密操作后，点击已完成继续。"),
    disconnected: t("手机已释放。点击重新连接可恢复画面。"),
  })[state] ?? t("检查手机连接后重试。");
  pause.hidden = state !== 'live';
  const resumeLabel = state === 'device_locked' || state === 'manual' ? t("已完成继续") : t("重新连接");
  resume.title = resumeLabel; resume.setAttribute('aria-label', resumeLabel);
  if (state === 'live' && /^data:image\/(png|jpeg);base64,/.test(data.dataUrl ?? '')) {
    const snapshot = { id: data.frame_id, width: data.width, height: data.height };
    frame = snapshot; displayedFrame = undefined;
    image.onload = () => {
      if (frame !== snapshot || stopped || document.hidden || !windowFocused || state !== 'live') return;
      displayedFrame = snapshot; syncControls();
    };
    image.onerror = () => {
      if (frame !== snapshot) return;
      clear(); syncControls(); schedule(0);
    };
    image.src = data.dataUrl; image.hidden = false;
    document.querySelector('time').textContent = new Date(data.capturedAt).toLocaleTimeString();
  } else if (state !== 'live') { clear(); document.querySelector('time').textContent = ''; }
  syncControls();
}
async function poll() {
  if (stopped || !activity.canPoll || busy || pointer || polling) return;
  polling = true; const generation = epoch;
  try {
    if (devicesPanel.open && Date.now() - lastDeviceRefresh > 3000 && deviceForgetConfirm.hidden) await refreshDevices();
    if (!pairingQr.hidden) { await refreshPairing(); if (!pairingQr.hidden) return; }
    const r = await app.callServerTool({ name: 'tapilot_preview_frame', arguments: {} }); if (generation === epoch && !busy && !pointer) render(r.structuredContent); }
  catch { if (generation === epoch) render({ state: 'unavailable' }); }
  finally { polling = false; if (!busy && !pointer) schedule(); }
}
function schedule(delay = state === 'live' ? 250 : 4000) {
  clearTimeout(timer);
  if (!stopped && activity.canPoll && !pointer) timer = setTimeout(poll, delay);
}
async function action(name, args = {}) {
  if (busy) return false;
  const generation = ++epoch; clearTimeout(timer); busy = true; frame = displayedFrame = undefined;
  if (name !== 'tapilot_preview_control') { pointer = undefined; clear(); }
  syncControls();
  status.textContent = t("正在执行…");
  notice.textContent = ''; notice.hidden = true;
  try {
    const r = await app.callServerTool({ name, arguments: args });
    const completed = !r.isError && r.structuredContent?.action_completed === true;
    if (generation !== epoch || stopped) return completed;
    if (r.isError) {
      render(r.structuredContent ?? { state: 'unavailable' });
      if (name === 'tapilot_preview_control') {
        notice.textContent = t("操作失败或结果未知，未自动重试。请查看新画面后再操作。"); notice.hidden = false;
      } else if (name === 'tapilot_devices') {
        notice.textContent = r.content?.find(item => item.type === 'text')?.text || t('无法读取设备列表'); notice.hidden = false;
      }
    } else {
      render(name === 'tapilot_disconnect' ? { state: 'disconnected' } : r.structuredContent);
      if (r.structuredContent?.action_completed && state !== 'live') {notice.textContent = t("操作已执行，但画面刷新失败。请重新获取画面，不要重复操作。");notice.hidden=false;}
    }
    return completed;
  } catch { if (generation === epoch && !stopped) { render({ state: 'unavailable' }); notice.textContent = t("操作结果未知，请重新连接并检查。输入草稿已保留，请勿直接重复提交。"); notice.hidden = false; } return false; }
  finally { busy = false; syncControls(); schedule(frame ? 250 : 0); }
}
function manual(args, snapshot = displayedFrame) {
  if (busy || !snapshot || state !== 'live' || stopped) return Promise.resolve(false);
  // Never apply coordinates selected on an older image to a later frame.
  return action('tapilot_preview_control', { ...args, frame_id: snapshot.id });
}
function point(event, snapshot = displayedFrame, clamp = false) {
  return snapshot && imagePoint(image.getBoundingClientRect(), snapshot.width, snapshot.height, event.clientX, event.clientY, clamp);
}
image.addEventListener('pointerdown', event => {
  if (busy) return;
  if (event.button !== 0 || pointer || state !== 'live') return;
  const start = point(event); if (!start) return;
  event.preventDefault(); clearTimeout(timer); ++epoch;
  pointer = { id: event.pointerId, start, frame: { ...displayedFrame }, time: performance.now(), clientX: event.clientX, clientY: event.clientY };
  image.setPointerCapture(event.pointerId); syncControls();
});
image.addEventListener('pointerup', event => {
  const gesture = pointer;
  if (!gesture || event.pointerId !== gesture.id) return;
  const end = point(event, gesture.frame, true); pointer = undefined; image.releasePointerCapture(event.pointerId);
  if (!end) { syncControls(); schedule(0); return; }
  const moved = Math.hypot(event.clientX - gesture.clientX, event.clientY - gesture.clientY) > 8;
  manual(moved ? { action: 'swipe', start_x: gesture.start.x, start_y: gesture.start.y, end_x: end.x, end_y: end.y, duration_ms: Math.round(Math.max(100, Math.min(2000, performance.now() - gesture.time))) } : { action: 'tap', x: end.x, y: end.y }, gesture.frame);
});
function cancelGesture() { if (!pointer) return; pointer = undefined; syncControls(); schedule(0); }
image.addEventListener('pointercancel', cancelGesture);
image.addEventListener('lostpointercapture', cancelGesture);
for (const button of document.querySelectorAll('button[data-action]')) button.onclick = () => {
  const command = button.dataset.action;
  if (command === 'up' || command === 'down') {
    if (!displayedFrame) return;
    const { width, height } = displayedFrame;
    manual({ action: 'swipe', start_x: width / 2, end_x: width / 2, start_y: height * (command === 'down' ? .75 : .3), end_y: height * (command === 'down' ? .3 : .75), duration_ms: 500 });
  } else manual({ action: command });
};
form.onsubmit = async event => {
  event.preventDefault(); if (!text.value || !displayedFrame) return;
  const draft = text.value;
  const completed = await manual({ action: 'input', text: draft, replace: document.querySelector('#replace').checked });
  if (completed && text.value === draft) text.value = '';
};
pair.onclick = async () => {
  if (busy) return;
  ++epoch; clearTimeout(timer); clear();
  busy = true; syncControls(); notice.hidden = true; pairingLink.hidden = true; pairingLink.removeAttribute('href');
  try {
    const result = await app.callServerTool({ name: 'tapilot_preview_pair', arguments: { action: 'pair' } });
    if (result.isError || !result.structuredContent?.url) throw new Error('pairing unavailable');
    const url = new URL(result.structuredContent.url);
    if (url.protocol !== 'http:' || !['127.0.0.1', 'localhost'].includes(url.hostname)) throw new Error('invalid pairing window');
    pairingLink.href = url.href; pairingLink.hidden = true;
    pairingExpiresAt = result.structuredContent.expires_at ?? 0;
    clearPairingQr();
    if (/^data:image\/png;base64,/.test(result._meta?.pairingQr ?? '') && pairingExpiresAt > Date.now()) {
      pairingQr.src = result._meta.pairingQr; pairingQr.hidden = false; pair.hidden = true;
      pairingStatus.textContent = t("等待手机扫码…"); pairingStatus.hidden = false;
    }
    if (pairingQr.hidden) pairingLink.hidden = false;
    pairingLink.onclick = async event => {
      event.preventDefault();
      try { const opened = await app.openLink({ url: url.href }); if (opened?.isError) throw new Error('open failed'); }
      catch { notice.textContent = t("无法打开配对窗口，请让聊天助手提供配对链接。"); notice.hidden = false; }
    };
    notice.textContent = t("用手机扫描二维码，然后在此页面确认连接。配对成功后画面会自动恢复，二维码 5 分钟内有效。"); notice.hidden = false;
  } catch { notice.textContent = t("无法创建配对窗口，请检查电脑 Wi-Fi 后重试。"); notice.hidden = false; }
  finally { busy = false; syncControls(); schedule(0); }
};
async function refreshPairing() {
  if (Date.now() >= pairingExpiresAt) { clearPairingQr(); return; }
  const result = await app.callServerTool({name:'tapilot_preview_pair', arguments:{action:'pair_status'}});
  if (result.isError) { clearPairingQr(); notice.textContent = t("配对已过期或无法继续，请重新生成二维码。"); notice.hidden = false; return; }
  const data = result.structuredContent;
  if (data?.paired) { clearPairingQr(); pairingExpiresAt = 0; notice.hidden = true; return; }
  if (data?.error) { notice.textContent = t(data.error); notice.hidden = false; }
  pairConfirm.hidden = !data?.pending; pairConfirm.disabled = busy || !!data?.pairing;
  pairingStatus.textContent = data?.pending ? `${data.pending.host} · ${t('手机已扫码，请确认配对')}` : t("等待手机扫码…");
}
pairConfirm.onclick = async () => {
  if (busy) return;
  busy = true; pairConfirm.disabled = true; syncControls(); notice.hidden = true;
  try {
    const result = await app.callServerTool({name:'tapilot_preview_pair', arguments:{action:'pair_confirm'}});
    if (result.isError) throw Error(result.content?.find(item => item.type === 'text')?.text || t('配对未完成，结果可能不确定。请重新扫码后确认连接。'));
    clearPairingQr(); pairingExpiresAt = 0;
  } catch (error) { notice.textContent = t(error.message); notice.hidden = false; }
  finally { busy = false; syncControls(); schedule(0); }
};
app.onhostcontextchanged = context => {
  if (context.locale) hostLocale = context.locale;
  applyLocale(lastData);
  if (lastData) render(lastData);
};
resume.onclick = () => action('tapilot_preview_resume');
pause.onclick = () => action('tapilot_preview', { action: 'pause' });
disconnect.onclick = () => action('tapilot_disconnect');
app.ontoolresult = result => { ++epoch; pointer = undefined; frame = displayedFrame = undefined; if (stopped || document.hidden || !windowFocused) { clear(); syncControls(); return; } if (result.isError) render(result.structuredContent ?? { state: 'unavailable' }); else if (result.structuredContent) render(result.structuredContent); schedule(0); };
app.onteardown = async () => { activity.close(); stopped = true; ++epoch; clearTimeout(timer); clear(); clearPairingQr(); text.value = ''; return {}; };
app.onclose = () => { activity.close(); stopped = true; clearTimeout(timer); clear(); clearPairingQr(); text.value = ''; render({ state: 'disconnected' }); };
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) windowFocused = true;
  ++epoch; pointer = undefined; clear(); syncControls(); updateActivity();
});
// iframe blur also fires when the user types in the host chat. It is not app backgrounding.
window.addEventListener('blur', () => {});
window.addEventListener('focus', () => { windowFocused = true; updateActivity(); });
window.addEventListener('pagehide', () => { stopped = true; ++epoch; clearTimeout(timer); clear(); clearPairingQr(); text.value = ''; });
window.addEventListener('pageshow', () => { stopped = false; windowFocused = true; updateActivity(); schedule(0); });
localize();
syncControls();
try {
  await app.connect();
  applyLocale(lastData);
  if (lastData) render(lastData);
  // The sole open tool requests the panel. Repeated frame/tool results must not
  // request another fullscreen host tab.
  updateActivity(); schedule(0);
} catch { status.textContent = t("请在支持 MCP App 的宿主中打开 Android Use"); }
