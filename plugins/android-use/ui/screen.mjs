import { App } from '@modelcontextprotocol/ext-apps';
import { imagePoint } from './geometry.mjs';
const app = new App({ name: 'Android Use', version: '0.4.1' }, {});
const image = document.querySelector('img');
const status = document.querySelector('[role=status]');
const resume = document.querySelector('#resume');
const pause = document.querySelector('#pause');
const disconnect = document.querySelector('#disconnect');
const notice = document.querySelector('[role=alert]');
const controls = [...document.querySelectorAll('[data-control]')];
const form = document.querySelector('form');
const text = document.querySelector('textarea');
let stopped = false, timer, busy = false, epoch = 0, frame, pointer, state = 'disconnected';
const labels = {
  live: '已连接 · 点击或拖动屏幕操作', disconnected: '已断开，点击重新连接',
  manual: '已暂停，请在手机上完成私密操作', device_locked: '请手动解锁手机，然后点击“已完成继续”',
  occupied: '手机正被另一个控制端使用，请先结束那边的控制',
  pairing_required: '配对已失效，请在手机的无线控制设置中重新配对',
  unavailable: '无法连接手机，请保持解锁并检查 Wi-Fi 和无线控制开关',
};
function syncControls() {
  for (const el of controls) el.disabled = busy || !!pointer || state !== 'live' || !frame;
  resume.disabled = pause.disabled = disconnect.disabled = busy;
  image.setAttribute('aria-disabled', String(busy || state !== 'live' || !frame));
}
function clear() { frame = undefined; image.removeAttribute('src'); image.hidden = true; }
function render(data) {
  state = data?.state ?? 'unavailable';
  status.textContent = labels[state] ?? labels.unavailable;
  resume.hidden = state === 'live';
  resume.textContent = state === 'device_locked' || state === 'manual' ? '已完成继续' : '重新连接';
  if (state === 'live' && data.dataUrl?.startsWith('data:image/png;base64,')) {
    frame = { id: data.frame_id, width: data.width, height: data.height };
    image.src = data.dataUrl; image.hidden = false;
    document.querySelector('time').textContent = new Date(data.capturedAt).toLocaleTimeString();
  } else if (state !== 'live') { clear(); document.querySelector('time').textContent = ''; }
  syncControls();
}
async function poll() {
  if (stopped || document.hidden || busy || pointer) return;
  busy = true; syncControls(); const generation = epoch;
  try { const r = await app.callServerTool({ name: 'tapilot_preview_frame', arguments: {} }); if (generation === epoch) render(r.structuredContent); }
  catch { if (generation === epoch) render({ state: 'unavailable' }); }
  finally { busy = false; syncControls(); schedule(); }
}
function schedule(delay = state === 'live' ? 1000 : 4000) {
  clearTimeout(timer);
  if (!stopped && !document.hidden && !pointer) timer = setTimeout(poll, delay);
}
async function action(name, args = {}) {
  if (busy) return;
  ++epoch; clearTimeout(timer); busy = true; frame = undefined;
  if (name !== 'tapilot_preview_control') clear();
  syncControls();
  status.textContent = '正在执行…';
  notice.textContent = ''; notice.hidden = true;
  try {
    const r = await app.callServerTool({ name, arguments: args });
    if (r.isError) {
      render(r.structuredContent ?? { state: 'unavailable' });
      notice.textContent = '操作失败或结果未知，未自动重试。请查看新画面后再操作。'; notice.hidden = false;
    } else render(name === 'tapilot_disconnect' ? { state: 'disconnected' } : r.structuredContent);
  } catch { render({ state: 'unavailable' }); notice.textContent = '操作结果未知，请重新连接并检查。'; notice.hidden = false; }
  finally { busy = false; syncControls(); schedule(0); }
}
function manual(args, snapshot = frame) {
  if (!snapshot || busy || state !== 'live') return;
  return action('tapilot_preview_control', { frame_id: snapshot.id, ...args });
}
function point(event, snapshot = frame) {
  return snapshot && imagePoint(image.getBoundingClientRect(), snapshot.width, snapshot.height, event.clientX, event.clientY);
}
image.addEventListener('pointerdown', event => {
  if (event.button !== 0 || busy || pointer || state !== 'live') return;
  const start = point(event); if (!start) return;
  event.preventDefault(); clearTimeout(timer); ++epoch;
  pointer = { id: event.pointerId, start, frame: { ...frame }, time: performance.now(), clientX: event.clientX, clientY: event.clientY };
  image.setPointerCapture(event.pointerId); syncControls();
});
image.addEventListener('pointerup', event => {
  const gesture = pointer;
  if (!gesture || event.pointerId !== gesture.id) return;
  const end = point(event, gesture.frame); pointer = undefined; image.releasePointerCapture(event.pointerId);
  if (!end) { syncControls(); schedule(0); return; }
  const moved = Math.hypot(event.clientX - gesture.clientX, event.clientY - gesture.clientY) > 8;
  manual(moved ? { action: 'swipe', start_x: gesture.start.x, start_y: gesture.start.y, end_x: end.x, end_y: end.y, duration_ms: Math.round(Math.max(100, Math.min(2000, performance.now() - gesture.time))) } : { action: 'tap', x: end.x, y: end.y }, gesture.frame);
});
image.addEventListener('pointercancel', () => { pointer = undefined; syncControls(); schedule(0); });
for (const button of document.querySelectorAll('button[data-action]')) button.onclick = () => {
  const command = button.dataset.action;
  if (command === 'up' || command === 'down') {
    if (!frame) return;
    const { width, height } = frame;
    manual({ action: 'swipe', start_x: width / 2, end_x: width / 2, start_y: height * (command === 'down' ? .75 : .3), end_y: height * (command === 'down' ? .3 : .75), duration_ms: 500 });
  } else manual({ action: command });
};
form.onsubmit = async event => {
  event.preventDefault(); if (!text.value || busy || !frame) return;
  const draft = text.value;
  await manual({ action: 'input', text: draft, replace: document.querySelector('#replace').checked });
  // Avoid retaining typed private content in the preview UI after submission.
  text.value = '';
};
resume.onclick = () => action('tapilot_preview_resume');
pause.onclick = () => action('tapilot_preview', { action: 'pause' });
disconnect.onclick = () => action('tapilot_disconnect');
app.ontoolresult = result => { ++epoch; pointer = undefined; frame = undefined; if (result.isError) render(result.structuredContent ?? { state: 'unavailable' }); else if (result.structuredContent) render(result.structuredContent); schedule(0); };
app.onteardown = async () => { stopped = true; ++epoch; clearTimeout(timer); clear(); text.value = ''; return {}; };
app.onclose = () => { stopped = true; clearTimeout(timer); clear(); text.value = ''; render({ state: 'disconnected' }); };
document.addEventListener('visibilitychange', () => { ++epoch; pointer = undefined; clear(); if (document.hidden) clearTimeout(timer); else schedule(0); });
window.addEventListener('pagehide', () => { stopped = true; ++epoch; clearTimeout(timer); clear(); text.value = ''; });
window.addEventListener('pageshow', () => { stopped = false; schedule(0); });
syncControls();
try {
  await app.connect();
  const context = app.getHostContext();
  if (context?.availableDisplayModes?.includes('fullscreen') && context.displayMode !== 'fullscreen') { try { await app.requestDisplayMode({ mode: 'fullscreen' }); } catch { /* Host chooses the available layout. */ } }
  schedule(0);
} catch { status.textContent = '请在支持 MCP App 的宿主中打开 Android Use'; }
