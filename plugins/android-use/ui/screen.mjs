import { App } from '@modelcontextprotocol/ext-apps';
const app = new App({ name: 'Android Use', version: '0.2.0' }, {});
const image = document.querySelector('img');
const status = document.querySelector('[role=status]');
const resume = document.querySelector('#resume');
const pause = document.querySelector('#pause');
let stopped = false, timer, busy = false, epoch = 0;
const labels = { live: '实时画面 · Wi-Fi', disconnected: '已断开', manual: '画面已暂停，请在手机上完成操作', device_locked: '请手动解锁手机，然后点击“已完成继续”', unavailable: '画面暂不可用，请检查 Wi-Fi 和手机无线控制' };
function clear() { image.removeAttribute('src'); image.hidden = true; }
function render(data) {
  const state = data?.state ?? 'unavailable';
  status.textContent = labels[state] ?? labels.unavailable;
  resume.hidden = state === 'live';
  resume.textContent = state === 'device_locked' || state === 'manual' ? '已完成继续' : '重新连接';
  if (state === 'live' && data.dataUrl?.startsWith('data:image/png;base64,')) {
    image.src = data.dataUrl; image.hidden = false;
    document.querySelector('time').textContent = new Date(data.capturedAt).toLocaleTimeString();
  } else if (state !== 'live') { clear(); document.querySelector('time').textContent = ''; }
}
async function poll() {
  if (stopped || document.hidden || busy) return;
  busy = true; const generation = epoch;
  try { const r = await app.callServerTool({ name: 'tapilot_preview_frame', arguments: {} }); if (generation === epoch) render(r.structuredContent); }
  catch { if (generation === epoch) render({ state: 'unavailable' }); }
  finally { busy = false; schedule(); }
}
function schedule() { clearTimeout(timer); if (!stopped && !document.hidden) timer = setTimeout(poll, 1000); }
async function action(name, args = {}) {
  ++epoch; clearTimeout(timer); clear(); resume.disabled = pause.disabled = true;
  try { const r = await app.callServerTool({ name, arguments: args }); render(r.structuredContent); }
  catch { render({ state: 'unavailable' }); }
  finally { resume.disabled = pause.disabled = false; schedule(); }
}
resume.onclick = () => action('tapilot_preview_resume');
pause.onclick = () => action('tapilot_preview', { action: 'pause' });
app.ontoolresult = result => { ++epoch; if (result.isError) render(result.structuredContent ?? { state: 'unavailable' }); else if(result.structuredContent) render(result.structuredContent); schedule(); };
app.onteardown = async () => { stopped = true; ++epoch; clearTimeout(timer); clear(); return {}; };
app.onclose = () => { stopped = true; clearTimeout(timer); clear(); status.textContent = '连接已关闭，请重新打开预览'; };
document.addEventListener('visibilitychange', () => { ++epoch; clear(); if (document.hidden) clearTimeout(timer); else schedule(); });
window.addEventListener('pagehide', () => { stopped = true; ++epoch; clearTimeout(timer); clear(); });
window.addEventListener('pageshow', () => { stopped = false; schedule(); });
try {
 await app.connect();
 const context = app.getHostContext();
 if (context?.availableDisplayModes?.includes('fullscreen') && context.displayMode !== 'fullscreen') { try { await app.requestDisplayMode({ mode: 'fullscreen' }); } catch { /* Use the host's supported layout. */ } }
 schedule();
} catch { status.textContent = '请在支持 MCP App 的宿主中打开 Android Use'; }
