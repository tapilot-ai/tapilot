import { App } from '@modelcontextprotocol/ext-apps';
import { imagePoint } from './geometry.mjs';
import { PreviewActivity } from './preview-activity.mjs';
const app = new App({ name: 'Android Use', version: '0.5.3' }, {});
const image = document.querySelector('img');
const status = document.querySelector('[role=status]');
const resume = document.querySelector('#resume');
const pause = document.querySelector('#pause');
const disconnect = document.querySelector('#disconnect');
const notice = document.querySelector('[role=alert]');
const recovery = document.querySelector('#recovery');
const pair = document.querySelector('#pair');
const pairingLink = document.querySelector('#pairing-link');
const controls = [...document.querySelectorAll('[data-control]')];
const form = document.querySelector('form');
const text = document.querySelector('textarea');
let stopped = false, timer, busy = false, polling = false, epoch = 0, frame, displayedFrame, pointer, state = 'disconnected';
let windowFocused = true;
const activity = new PreviewActivity({
  onSuspend: () => { ++epoch; clearTimeout(timer); pointer = undefined; clear(); syncControls(); },
  onResume: () => { ++epoch; pointer = undefined; clear(); syncControls(); schedule(0); },
});
function updateActivity() { activity.update(!document.hidden && windowFocused); }

const labels = {
  host_reload_required: '请重载电脑上的 Android Use 插件',
  setup_required: '尚未配对手机',
  live: '已连接 · 点击或拖动屏幕操作', disconnected: '已断开，点击重新连接',
  manual: '已暂停，请在手机上完成私密操作', device_locked: '请手动解锁手机，然后点击“已完成继续”',
  occupied: '手机正被另一个控制端使用，请先结束那边的控制',
  pairing_required: '配对已失效，请在手机的无线控制设置中重新配对',
  unavailable: '无法连接手机，请保持解锁并检查 Wi-Fi 和无线控制开关',
};
function syncControls() {
  for (const el of controls) el.disabled = busy || !!pointer || state !== 'live' || !displayedFrame;
  resume.disabled = pause.disabled = disconnect.disabled = pair.disabled = busy;
  image.setAttribute('aria-disabled', String(busy || !!pointer || state !== 'live' || !displayedFrame));
}
function clear() { frame = displayedFrame = undefined; image.removeAttribute('src'); image.hidden = true; }
function render(data) {
  if (stopped || document.hidden || !windowFocused) { clear(); syncControls(); return; }
  state = data?.state ?? 'unavailable';
  if (state === 'live') { pairingLink.hidden = true; pairingLink.removeAttribute('href'); }
  status.textContent = labels[state] ?? labels.unavailable;
  resume.hidden = ['live', 'host_reload_required'].includes(state);
  pair.hidden = !['setup_required', 'pairing_required'].includes(state);
  recovery.hidden = state === 'live';
  recovery.textContent = ({
    host_reload_required: '插件已更新或安装文件缺失。重载插件或重新打开客户端后继续，无需重新配对手机。',
    setup_required: '在手机 Tapilot 设置中开启无线控制，再点击下方按钮，用手机扫描电脑二维码。无需 USB。',
    pairing_required: '在手机上重新扫描电脑二维码，并在电脑窗口确认。完成后点击重新连接。',
    unavailable: '请检查手机无线控制已开启、两端 Wi-Fi 可互通。恢复后点击重新连接；不会重放之前的操作。',
    occupied: '请先在另一控制端结束任务或释放手机，然后重新连接。',
    device_locked: '请在手机上解锁，再点击已完成继续。不要在聊天中输入密码。',
    manual: '画面已隐藏。完成手机上的私密操作后，点击已完成继续。',
    disconnected: '手机已释放。点击重新连接可恢复画面。',
  })[state] ?? '检查手机连接后重试。';
  pause.hidden = state !== 'live';
  const resumeLabel = state === 'device_locked' || state === 'manual' ? '已完成继续' : '重新连接';
  resume.title = resumeLabel; resume.setAttribute('aria-label', resumeLabel);
  if (state === 'live' && /^data:image\/(png|jpeg);base64,/.test(data.dataUrl ?? '')) {
    frame = { id: data.frame_id, width: data.width, height: data.height };
    displayedFrame = frame;
    image.src = data.dataUrl; image.hidden = false;
    document.querySelector('time').textContent = new Date(data.capturedAt).toLocaleTimeString();
  } else if (state !== 'live') { clear(); document.querySelector('time').textContent = ''; }
  syncControls();
}
async function poll() {
  if (stopped || !activity.canPoll || busy || pointer || polling) return;
  polling = true; const generation = epoch;
  try { const r = await app.callServerTool({ name: 'tapilot_preview_frame', arguments: {} }); if (generation === epoch && !busy && !pointer) render(r.structuredContent); }
  catch { if (generation === epoch) render({ state: 'unavailable' }); }
  finally { polling = false; if (!busy && !pointer) schedule(); }
}
function schedule(delay = state === 'live' ? 250 : 4000) {
  clearTimeout(timer);
  if (!stopped && activity.canPoll && !pointer) timer = setTimeout(poll, delay);
}
async function action(name, args = {}) {
  if (busy) return false;
  const generation = ++epoch; clearTimeout(timer); busy = true; frame = undefined;
  if (name !== 'tapilot_preview_control') clear();
  syncControls();
  status.textContent = '正在执行…';
  notice.textContent = ''; notice.hidden = true;
  try {
    const r = await app.callServerTool({ name, arguments: args });
    const completed = !r.isError && r.structuredContent?.action_completed === true;
    if (generation !== epoch || stopped) return completed;
    if (r.isError) {
      render(r.structuredContent ?? { state: 'unavailable' });
      notice.textContent = '操作失败或结果未知，未自动重试。请查看新画面后再操作。'; notice.hidden = false;
    } else {
      render(name === 'tapilot_disconnect' ? { state: 'disconnected' } : r.structuredContent);
      if (r.structuredContent?.action_completed && state !== 'live') {notice.textContent = '操作已执行，但画面刷新失败。请重新获取画面，不要重复操作。';notice.hidden=false;}
    }
    return completed;
  } catch { if (generation === epoch && !stopped) { render({ state: 'unavailable' }); notice.textContent = '操作结果未知，请重新连接并检查。输入草稿已保留，请勿直接重复提交。'; notice.hidden = false; } return false; }
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
  busy = true; syncControls(); notice.hidden = true; pairingLink.hidden = true; pairingLink.removeAttribute('href');
  try {
    const result = await app.callServerTool({ name: 'tapilot_midscene', arguments: { action: 'pair' } });
    if (result.isError || !result.structuredContent?.url) throw new Error('pairing unavailable');
    const url = new URL(result.structuredContent.url);
    if (url.protocol !== 'http:' || !['127.0.0.1', 'localhost'].includes(url.hostname)) throw new Error('invalid pairing window');
    pairingLink.href = url.href; pairingLink.hidden = false;
    pairingLink.onclick = async event => {
      event.preventDefault();
      try { const opened = await app.openLink({ url: url.href }); if (opened?.isError) throw new Error('open failed'); }
      catch { notice.textContent = '无法打开配对窗口，请让聊天助手提供配对链接。'; notice.hidden = false; }
    };
    notice.textContent = '打开配对窗口，用手机扫描二维码并在电脑确认，然后点击重新连接。窗口 5 分钟内有效。'; notice.hidden = false;
  } catch { notice.textContent = '无法创建配对窗口，请检查电脑 Wi-Fi 后重试。'; notice.hidden = false; }
  finally { busy = false; syncControls(); }
};
resume.onclick = () => action('tapilot_preview_resume');
pause.onclick = () => action('tapilot_preview', { action: 'pause' });
disconnect.onclick = () => action('tapilot_disconnect');
app.ontoolresult = result => { ++epoch; pointer = undefined; frame = undefined; if (stopped || document.hidden || !windowFocused) { clear(); syncControls(); return; } if (result.isError) render(result.structuredContent ?? { state: 'unavailable' }); else if (result.structuredContent) render(result.structuredContent); schedule(0); };
app.onteardown = async () => { activity.close(); stopped = true; ++epoch; clearTimeout(timer); clear(); text.value = ''; return {}; };
app.onclose = () => { activity.close(); stopped = true; clearTimeout(timer); clear(); text.value = ''; render({ state: 'disconnected' }); };
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) windowFocused = true;
  ++epoch; pointer = undefined; clear(); syncControls(); updateActivity();
});
window.addEventListener('blur', () => { windowFocused = false; ++epoch; pointer = undefined; clear(); syncControls(); updateActivity(); });
window.addEventListener('focus', () => { windowFocused = true; updateActivity(); });
window.addEventListener('pagehide', () => { stopped = true; ++epoch; clearTimeout(timer); clear(); text.value = ''; });
window.addEventListener('pageshow', () => { stopped = false; windowFocused = true; updateActivity(); schedule(0); });
syncControls();
try {
  await app.connect();
  // The sole open tool requests the panel. Repeated frame/tool results must not
  // request another fullscreen host tab.
  updateActivity(); schedule(0);
} catch { status.textContent = '请在支持 MCP App 的宿主中打开 Android Use'; }
