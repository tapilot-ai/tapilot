import { App } from '@modelcontextprotocol/ext-apps';
import { imagePoint } from './geometry.mjs';
const app = new App({ name: 'Android Use', version: '0.4.9' }, {});
const image = document.querySelector('img');
const status = document.querySelector('[role=status]');
const resume = document.querySelector('#resume');
const pause = document.querySelector('#pause');
const disconnect = document.querySelector('#disconnect');
const notice = document.querySelector('[role=alert]');
const controls = [...document.querySelectorAll('[data-control]')];
const form = document.querySelector('form');
const text = document.querySelector('textarea');
let stopped = false, timer, busy = false, polling = false, epoch = 0, frame, displayedFrame, pointer, state = 'disconnected';
const pendingActions = [];
let draining = false;
const MAX_PENDING = 6;
const labels = {
  live: '已连接 · 点击或拖动屏幕操作', disconnected: '已断开，点击重新连接',
  manual: '已暂停，请在手机上完成私密操作', device_locked: '请手动解锁手机，然后点击“已完成继续”',
  occupied: '手机正被另一个控制端使用，请先结束那边的控制',
  pairing_required: '配对已失效，请在手机的无线控制设置中重新配对',
  unavailable: '无法连接手机，请保持解锁并检查 Wi-Fi 和无线控制开关',
};
function syncControls() {
  for (const el of controls) el.disabled = pendingActions.length >= MAX_PENDING || !!pointer || state !== 'live' || !displayedFrame;
  resume.disabled = pause.disabled = disconnect.disabled = busy;
  image.setAttribute('aria-disabled', String(pendingActions.length >= MAX_PENDING || state !== 'live' || !displayedFrame));
}
function discardPending() { while (pendingActions.length) pendingActions.shift().resolve(); }
function clear() { frame = displayedFrame = undefined; discardPending(); image.removeAttribute('src'); image.hidden = true; }
function render(data) {
  state = data?.state ?? 'unavailable';
  status.textContent = labels[state] ?? labels.unavailable;
  resume.hidden = state === 'live';
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
  if (stopped || document.hidden || busy || pointer || polling) return;
  polling = true; const generation = epoch;
  try { const r = await app.callServerTool({ name: 'tapilot_preview_frame', arguments: {} }); if (generation === epoch && !busy && !pointer) render(r.structuredContent); }
  catch { if (generation === epoch) render({ state: 'unavailable' }); }
  finally { polling = false; if (!busy && !pointer) schedule(); }
}
function schedule(delay = state === 'live' ? 250 : 4000) {
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
    } else {
      render(name === 'tapilot_disconnect' ? { state: 'disconnected' } : r.structuredContent);
      if (r.structuredContent?.action_completed && state !== 'live') {notice.textContent = '操作已执行，但画面刷新失败。请重新获取画面，不要重复操作。';notice.hidden=false;}
    }
  } catch { render({ state: 'unavailable' }); notice.textContent = '操作结果未知，请重新连接并检查。'; notice.hidden = false; }
  finally { busy = false; syncControls(); schedule(frame ? 250 : 0); }
}
function manual(args, snapshot = displayedFrame) {
  if (!snapshot || state !== 'live' || stopped) return;
  if (pendingActions.length >= MAX_PENDING) {
    notice.textContent = '已有 6 个操作排队，请等待执行后再操作。'; notice.hidden = false;
    return;
  }
  return new Promise(resolve => {
    pendingActions.push({ args, width: snapshot.width, height: snapshot.height, resolve });
    if (busy) status.textContent = `正在执行 · ${pendingActions.length} 个操作排队`;
    syncControls(); drain();
  });
}
async function drain() {
  if (draining || busy) return;
  draining = true;
  try {
    while (pendingActions.length && !stopped && state === 'live' && frame) {
      const next = pendingActions.shift();
      if (next.width !== frame.width || next.height !== frame.height) {
        next.resolve(); discardPending();
        notice.textContent = '屏幕方向已改变，排队操作已取消，请查看新画面。'; notice.hidden = false;
        break;
      }
      await action('tapilot_preview_control', { ...next.args, frame_id: frame.id });
      next.resolve();
    }
    if (pendingActions.length) discardPending();
  } finally { draining = false; syncControls(); }
}
function point(event, snapshot = displayedFrame, clamp = false) {
  return snapshot && imagePoint(image.getBoundingClientRect(), snapshot.width, snapshot.height, event.clientX, event.clientY, clamp);
}
image.addEventListener('pointerdown', event => {
  if (pendingActions.length >= MAX_PENDING) { notice.textContent = '已有 6 个操作排队，请等待执行后再操作。'; notice.hidden = false; return; }
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
  const completion = manual({ action: 'input', text: draft, replace: document.querySelector('#replace').checked });
  text.value = '';
  await completion;
};
resume.onclick = () => action('tapilot_preview_resume');
pause.onclick = () => action('tapilot_preview', { action: 'pause' });
disconnect.onclick = () => action('tapilot_disconnect');
app.ontoolresult = result => { discardPending(); ++epoch; pointer = undefined; frame = undefined; if (result.isError) render(result.structuredContent ?? { state: 'unavailable' }); else if (result.structuredContent) render(result.structuredContent); schedule(0); };
app.onteardown = async () => { stopped = true; ++epoch; clearTimeout(timer); clear(); text.value = ''; return {}; };
app.onclose = () => { stopped = true; clearTimeout(timer); clear(); text.value = ''; render({ state: 'disconnected' }); };
document.addEventListener('visibilitychange', () => { ++epoch; pointer = undefined; clear(); if (document.hidden) clearTimeout(timer); else schedule(0); });
window.addEventListener('pagehide', () => { stopped = true; ++epoch; clearTimeout(timer); clear(); text.value = ''; });
window.addEventListener('pageshow', () => { stopped = false; schedule(0); });
syncControls();
try {
  await app.connect();
  // The sole open tool requests the panel. Repeated frame/tool results must not
  // request another fullscreen host tab.
  schedule(0);
} catch { status.textContent = '请在支持 MCP App 的宿主中打开 Android Use'; }
