import { t, setLanguage, localize, connectionErrorMessage } from './i18n.mjs';
import { createPanelBridge } from './panel-bridge.mjs';
import { applyDocumentTheme, applyHostStyleVariables } from '@modelcontextprotocol/ext-apps';
import { imagePoint } from './geometry.mjs';
import { PreviewActivity } from './preview-activity.mjs';
import { panelRequest } from './panel-request.mjs';
const app = createPanelBridge({ browser: !!globalThis.__TAPILOT_BROWSER__, dsh: !!globalThis.__TAPILOT_DSH__, host: globalThis.__TAPILOT_PANEL__ });
const interactive = app.capabilities.interactive;
const viewerId = crypto.randomUUID();
const callServerTool = app.callServerTool.bind(app);
app.callServerTool = request => {
  const scoped = ['tapilot_preview_frame', 'tapilot_preview_control', 'tapilot_preview', 'tapilot_preview_resume'].includes(request.name);
  return panelRequest(callServerTool, scoped ? {...request, arguments:{...request.arguments, viewer_id:viewerId}} : request);
};
const image = document.querySelector('img');
const status = document.querySelector('[role=status]');
const resume = document.querySelector('#resume');
const expandPanel = document.querySelector('#expand-panel');
let displayContext = {};
function syncDisplayMode(context = {}) {
  displayContext = {...displayContext, ...context};
  expandPanel.hidden = globalThis.__TAPILOT_PANEL__?.name !== 'doubao' || displayContext.displayMode === 'fullscreen' || !displayContext.availableDisplayModes?.includes('fullscreen');
  expandPanel.textContent = t('在侧栏打开');
}
async function expand() {
  expandPanel.disabled = true;
  try {
    const result = await app.requestDisplayMode({mode:'fullscreen'}, {timeout:5000});
    syncDisplayMode({displayMode:result.mode});
    if (result.mode !== 'fullscreen') throw new Error('Expansion rejected');
  } catch { notice.textContent = t('无法打开侧栏，请重试。'); notice.hidden = false; }
  finally { expandPanel.disabled = false; }
}
expandPanel.onclick = expand;
const pause = document.querySelector('#pause');
const disconnect = document.querySelector('#disconnect');
const notice = document.querySelector('[role=alert]');
let connectionErrorActive = false;
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
const devicesList = document.querySelector('#devices-list');
const pairCancel = document.querySelector('#pair-cancel');
let savedDevices = [], selectedDeviceId = '', connectingDeviceId = '', failedDeviceId = '';
let activeDeviceId = '';
const recoveryDetails = document.querySelector('#recovery-details');
const pairRefresh = document.querySelector('#pair-refresh');
const selectedDevice = () => savedDevices.find(device => device.id === selectedDeviceId);
let pickerSignature = '';
function renderDevicePicker() {
  const signature = JSON.stringify([savedDevices, selectedDeviceId, activeDeviceId, connectingDeviceId, state, t('状态未知')]);
  if (signature === pickerSignature) return;
  pickerSignature = signature;
  const focused = document.activeElement?.dataset?.deviceId;
  devicesList.replaceChildren();
  for (const device of savedDevices) {
    const row = document.createElement('button');
    row.type = 'button'; row.className = 'device-row'; row.dataset.deviceId = device.id;
    row.setAttribute('aria-pressed', String(device.id === selectedDeviceId));
    row.disabled = busy || loadingDevices;
    const name = document.createElement('span'); name.className = 'device-name'; name.textContent = device.name; name.title = device.name;
    const status = document.createElement('span'); status.className = 'device-state';
    const current = device.id === activeDeviceId;
    const detail = current ? {device_locked:'待解锁',occupied:'被占用',unavailable:'重连中',manual:'已暂停',pairing_required:'需要重新配对',accessibility_required:'待开启控制服务'}[state] : undefined;
    status.textContent = device.id === connectingDeviceId ? t('正在连接…') : detail ? t(detail) : {unknown:t('状态未知'),connected:t('已连接'),not_connected:t('未连接'),disconnected:t('已主动断开')}[device.state];
    row.setAttribute('aria-busy', String(device.id === connectingDeviceId));
    row.append(name); row.append(status);
    row.onclick = async () => {
      if (!interactive || busy || loadingDevices || pairingActive) return;
      const alreadyConnected = device.state === 'connected';
      selectedDeviceId = device.id; deviceForgetConfirm.hidden = true;
      renderDevicePicker(); syncControls();
      [...devicesList.children].find(item => item.dataset.deviceId === device.id)?.focus();
      if (!alreadyConnected) await deviceAction('connect');
      else { devicesPanel.open = false; devicesPanel.querySelector('summary').focus(); }
    };
    devicesList.append(row);
  }
  if (focused) [...devicesList.children].find(item => item.dataset.deviceId === focused)?.focus();
}
const devicesStatus = document.querySelector('#devices-status');
const deviceForgetConfirm = document.querySelector('#device-forget-confirm');
let lastDeviceRefresh = 0, loadingDevices = false;
async function refreshDevices() {
  if (pairingActive || loadingDevices) return;
  loadingDevices = true;
  const refreshEpoch = epoch;
  try {
    const result = await app.callServerTool({name:'tapilot_devices',arguments:{action:'list'}});
    if (result.isError) throw Error('Device list unavailable');
    if (refreshEpoch !== epoch) return;
    const devices = result.structuredContent?.devices ?? [];
    const chosen = selectedDeviceId;
    savedDevices = devices;
    activeDeviceId = devices.find(d => d.selected)?.id ?? '';
    selectedDeviceId = devices.some(d => d.id === chosen) ? chosen : (devices.find(d => d.selected) ?? devices[0])?.id ?? '';
    renderDevicePicker();
    devicesStatus.textContent = devices.length ? t('点击设备即可连接或切换；一次控制一台。') : t('没有已配对设备');
    deviceForgetConfirm.hidden = true;
    lastDeviceRefresh = Date.now();
  } catch {
    if (refreshEpoch !== epoch) return;
    savedDevices = savedDevices.map(device => ({...device, state:'unknown'}));
    renderDevicePicker();
    devicesStatus.textContent = t('无法读取设备列表');
  }
  finally { loadingDevices = false; syncControls(); }
}
devicesPanel.ontoggle = () => { if (devicesPanel.open) return refreshDevices(); };
document.addEventListener('click', event => {
  if (devicesPanel.open && !(event.composedPath?.().includes(devicesPanel) ?? devicesPanel.contains(event.target))) devicesPanel.open = false;
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && devicesPanel.open) {
    devicesPanel.open = false;
    devicesPanel.querySelector('summary').focus();
  }
});
async function deviceAction(command, extra = {}) {
  if (busy || pairingActive || !selectedDeviceId) return;
  const target = selectedDeviceId;
  if (command === 'connect') { connectingDeviceId = target; failedDeviceId = ''; activeDeviceId = target; renderDevicePicker(); }
  const succeeded = await action('tapilot_devices', {action:command,device_id:target,...extra});
  if (command === 'connect') {
    connectingDeviceId = ''; failedDeviceId = succeeded ? '' : target;
    if (succeeded) { devicesPanel.open = false; devicesPanel.querySelector('summary').focus(); }
  }
  await refreshDevices();
  return succeeded;
}
devicesList.addEventListener('keydown', event => {
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
  const rows = [...devicesList.children].filter(row => !row.disabled);
  if (!rows.length) return;
  event.preventDefault();
  const index = rows.indexOf(document.activeElement);
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? rows.length - 1 : (index + (event.key === 'ArrowDown' ? 1 : -1) + rows.length) % rows.length;
  rows[next].focus();
});
document.querySelector('#device-connect').onclick = () => deviceAction('connect');
document.querySelector('#device-disconnect').onclick = () => deviceAction('disconnect');
document.querySelector('#device-address-save').onclick = async () => {
  const value = document.querySelector('#device-address').value.trim();
  const [host, port, ...rest] = value.split(':');
  if (rest.length || !host || (port !== undefined && !/^\d+$/.test(port))) { notice.textContent = t('请输入手机当前的 IPv4 地址，可附加端口。'); notice.hidden = false; return; }
  if (await deviceAction('update_address', {host, ...(port ? {port:Number(port)} : {})})) await deviceAction('connect');
};
document.querySelector('#device-forget').onclick = () => { deviceForgetConfirm.hidden = false; };
deviceForgetConfirm.onclick = () => deviceAction('forget');
let hostLocale, lastData;
function applyLocale(data) {
  const locale = hostLocale || app.getHostContext?.()?.locale || data?.language || globalThis.navigator?.language || 'en';
  setLanguage(/^zh(?:[-_]|$)/i.test(locale) ? 'zh' : 'en'); localize();
  if (!interactive) document.querySelector('#host-notice').textContent = t('当前客户端支持实时预览。配对、切换设备和操作手机请交给助手完成。');
}
let pairingExpiresAt = 0, pairingActive = false, pairingGeneration = 0, pairingExpired = false, pairingStarting = false;
function clearPairingQr() { if (pairingStarting) { pairingStarting = false; busy = false; } pairingExpired = false; pairRefresh.hidden = true; pairingActive = false; ++pairingGeneration; pairCancel.hidden = true; pairingQr.hidden = true; pairingQr.removeAttribute('src'); pairConfirm.hidden = true; pairingStatus.hidden = true; syncPairingControls(); }
const controls = [...document.querySelectorAll('[data-control]')];
const form = document.querySelector('form');
const text = document.querySelector('textarea');
let stopped = false, timer, busy = false, polling, epoch = 0, frame, displayedFrame, pointer, state = 'disconnected';
let windowFocused = true;
const activity = new PreviewActivity({
  graceMs: 0, setTimer: setTimeout, clearTimer: clearTimeout,
  onSuspend: () => { ++epoch; clearTimeout(timer); pointer = undefined; clear(); syncControls();
    app.callServerTool({name:'tapilot_preview',arguments:{action:'suspend'}}).catch(() => {});
  },
  onResume: () => { ++epoch; pointer = undefined; render({state:'connecting'}); schedule(0); },
});
function updateActivity() { activity.update(!document.hidden && windowFocused); }

const labels = () => ({
  connecting: t("正在连接手机…"),
  waiting_frame: t('正在获取手机画面…'),
  accessibility_required: t("手机的设备控制服务未连接"),
  host_reload_required: t("请重载电脑上的 Tapilot 插件"),
  setup_required: t("尚未配对手机"),
  live: interactive ? t("已连接 · 点击或拖动屏幕操作") : t('已连接 · 实时预览'), disconnected: t("已断开，点击重新连接"),
  manual: t("已暂停，请在手机上完成私密操作"), device_locked: t("请在手机上解锁，画面会自动恢复"),
  occupied: t("手机正被另一个控制端使用，请先结束那边的控制"),
  pairing_required: t("配对已失效，请在手机的无线控制设置中重新配对"),
  unavailable: t('连接中断，正在自动重连…'),
});
function syncPairingControls() {
  devicesPanel.hidden = pairingActive;
  document.querySelector('#device-pair').hidden = pairingActive;
  pairCancel.hidden = !pairingActive;
  document.querySelector('.device-picker').hidden = pairingActive;
  document.querySelector('.device-actions').hidden = pairingActive;
  devicesStatus.hidden = pairingActive;
  document.querySelector('#devices-title').textContent = pairingActive ? t('配对新手机') : t('已配对设备');

}
function syncControls() {
  syncPairingControls();
  for (const el of controls) el.disabled = !interactive || busy || !!pointer || state !== 'live' || !displayedFrame;
  resume.disabled = pause.disabled = disconnect.disabled = busy || pairingActive;
  pair.disabled = busy;
  document.querySelector('#device-pair').disabled = busy;
  for (const row of devicesList.children) row.disabled = busy || loadingDevices;
  pairCancel.disabled = busy && !pairingStarting;
  const selected = selectedDevice();
  document.querySelector('#device-connect').hidden = !selected || selected.id !== failedDeviceId;
  document.querySelector('#device-connect').textContent = t('重试连接');
  document.querySelector('#device-details').textContent = selected ? `${selected.name}${selected.host ? ' · ' + selected.host : ''}` : '';
  pairRefresh.disabled = busy;
  document.querySelector('#reconnect-pair').disabled = busy;
  document.querySelector('#device-disconnect').hidden = pairingActive || !selected || selected.state !== 'connected';
  for (const id of ['#device-connect', '#device-disconnect', '#device-forget', '#device-forget-confirm', '#device-address-save', '#device-address'])
    document.querySelector(id).disabled = busy || loadingDevices || !selectedDeviceId;
  image.setAttribute('aria-disabled', String(!interactive || busy || !!pointer || state !== 'live' || !displayedFrame));
  if (!interactive) {
    for (const selector of ['.navigation', '.text-entry', '.device-actions', '#pair', '#device-pair', '#reconnect-pair', '#pair-confirm', '#pair-refresh', '#pair-cancel']) document.querySelector(selector).hidden = true;
    for (const row of devicesList.children) row.disabled = true;
  }
}
function clear() { if (state === 'live') status.textContent = t('正在获取手机画面…'); frame = displayedFrame = undefined; image.onload = image.onerror = null; image.removeAttribute('src'); image.hidden = true; }
function render(data) {
  lastData = data; applyLocale(data);
  if (stopped || document.hidden || !windowFocused) { clear(); syncControls(); return; }
  if (pairingActive) { syncPairingControls(); return; }
  state = data?.state ?? 'unavailable';
  if (state === 'live' && !/^data:image\/(png|jpeg);base64,/.test(data?.dataUrl ?? '')) state = 'waiting_frame';
  if (state === 'live' && connectionErrorActive) {
    notice.textContent = ''; notice.hidden = true; connectionErrorActive = false; failedDeviceId = '';
  }
  if (['unavailable', 'disconnected', 'pairing_required', 'host_reload_required', 'occupied'].includes(state)) {
    savedDevices = savedDevices.map(device => device.state === 'connected' ? {...device, state:'unknown'} : device);
    renderDevicePicker();
  }
  if (state === 'live' && !pairingQr.hidden) { notice.hidden = true; notice.textContent = ''; }
  if (state === 'live' || Date.now() >= pairingExpiresAt) clearPairingQr();
  if (state === 'live') { pairingLink.hidden = true; pairingLink.removeAttribute('href'); }
  const active = savedDevices.find(device => device.id === activeDeviceId);
  status.textContent = (active ? `${active.name} · ` : '') + (state === 'live' ? t('正在获取手机画面…') : (labels()[state] ?? labels().unavailable));
  renderDevicePicker();
  document.querySelector('.preview-empty h1').textContent = active?.name ?? t('无线连接你的手机');
  document.querySelector('.preview-empty p').textContent = t('连接后即可在这里查看和操作手机屏幕。');
  resume.hidden = ['connecting', 'live', 'host_reload_required', 'device_locked', 'setup_required', 'pairing_required'].includes(state);
  pair.hidden = !pairingQr.hidden || !['setup_required', 'pairing_required'].includes(state);
  document.querySelector('#reconnect-pair').hidden = !['unavailable','waiting_frame'].includes(state);
  recovery.hidden = ['live', 'connecting'].includes(state);
  recoveryDetails.hidden = recovery.hidden;
  if (recovery.hidden) recoveryDetails.open = false;
  const installation = document.querySelector('#installation');
  if (installation) installation.hidden = !['setup_required', 'pairing_required'].includes(state);
  recovery.textContent = ({
    accessibility_required: t("请在手机系统辅助功能中开启 Tapilot 设备控制；若开关已开启，请关闭再开启。恢复后会自动连接，无需重新配对。"),
    host_reload_required: t("插件已更新或安装文件缺失。重载插件或重新打开客户端后继续，无需重新配对手机。"),
    setup_required: t("在手机 Tapilot 设置中开启无线控制，再点击下方按钮，用手机扫描电脑二维码。无需 USB。"),
    pairing_required: t("在手机上重新扫描电脑二维码，并在电脑窗口确认。完成后点击重新连接。"),
    waiting_frame: t('尚未收到手机画面，正在检查连接。长时间没有画面时可重试。'),
    unavailable: t("请确认手机已解锁、无线控制已开启且 Wi-Fi 可以互通。若切到后台后断线，请打开手机 Tapilot，检查后台运行和联网限制。恢复后会自动连接，不会重放操作。"),
    occupied: t("请先在另一控制端结束任务或释放手机，然后重新连接。"),
    device_locked: t("请点亮手机屏幕并解锁，预览会自动恢复，无需刷新连接。"),
    manual: t("画面已隐藏。完成手机上的私密操作后，点击已完成继续。"),
    disconnected: t("手机已释放。点击重新连接可恢复画面。"),
  })[state] ?? t("检查手机连接后重试。");
  if (state === 'unavailable') recovery.textContent += ' ' + t('换了 Wi-Fi？点击“扫码重新连接”，用手机 Tapilot 扫码，无需填写 IP。公司网络可能限制设备互通。');
  pause.hidden = state !== 'live';
  const resumeLabel = state === 'unavailable' ? t('立即重试') : state === 'device_locked' || state === 'manual' ? t("已完成继续") : t("重新连接");
  document.querySelector('#resume-label').textContent = resumeLabel;
  resume.title = resumeLabel; resume.setAttribute('aria-label', resumeLabel);
  if (state === 'live' && /^data:image\/(png|jpeg);base64,/.test(data.dataUrl ?? '')) {
    const snapshot = { id: data.frame_id, width: data.width, height: data.height };
    frame = snapshot; displayedFrame = undefined;
    image.onload = () => {
      if (frame !== snapshot || stopped || document.hidden || !windowFocused || state !== 'live') return;
      displayedFrame = snapshot;
      status.textContent = (active ? `${active.name} · ` : '') + labels().live;
      syncControls();
    };
    image.onerror = () => {
      if (frame !== snapshot) return;
      render({state:'unavailable'}); schedule(4000);
    };
    image.src = data.dataUrl; image.hidden = false;
    document.querySelector('time').textContent = new Date(data.capturedAt).toLocaleTimeString();
  } else if (state !== 'live') { clear(); document.querySelector('time').textContent = ''; }
  syncControls();
}
async function poll() {
  if (stopped || !activity.canPoll || busy || pointer || polling?.epoch === epoch) return;
  const request = {epoch}; polling = request; const generation = epoch;
  const slowPreview = setTimeout(() => {
    if (generation === epoch && !busy && !pointer && activity.canPoll && !document.hidden) render({state:'waiting_frame'});
  }, 5000);
  try {
    if (!pairingActive && Date.now() - lastDeviceRefresh > 3000 && deviceForgetConfirm.hidden) await refreshDevices();
    if (generation !== epoch || stopped || busy) return;
    if (pairingActive) { await refreshPairing(); if (pairingActive || generation !== epoch) return; }
    const r = await app.callServerTool({ name: 'tapilot_preview_frame', arguments: {} }); if (generation === epoch && !busy && !pointer) render(r.structuredContent); }
  catch { if (generation === epoch) render({ state: 'unavailable' }); }
  finally { clearTimeout(slowPreview); if (polling === request) { polling = undefined; if (!busy && !pointer) schedule(); } }
}
function schedule(delay = pairingActive && !pairingExpired ? 300 : state === 'live' ? 250 : 4000) {
  clearTimeout(timer);
  if (!stopped && activity.canPoll && !pointer) timer = setTimeout(poll, delay);
}
async function action(name, args = {}) {
  if (busy || pairingActive) return false;
  const generation = ++epoch; clearTimeout(timer); busy = true; frame = displayedFrame = undefined;
  if (name !== 'tapilot_preview_control') { pointer = undefined; clear(); }
  syncControls();
  status.textContent = name === 'tapilot_devices' && args.action === 'connect' ? `${t('正在连接…')} ${selectedDevice()?.name ?? ''}` : t('正在执行…');
  notice.textContent = ''; notice.hidden = true; connectionErrorActive = false;
  try {
    const r = await app.callServerTool({ name, arguments: args });
    const completed = !r.isError && r.structuredContent?.action_completed === true;
    if (generation !== epoch || stopped) return completed;
    if (name === 'tapilot_devices' && r.structuredContent?.devices) {
      savedDevices = r.structuredContent.devices;
      activeDeviceId = savedDevices.find(d => d.selected)?.id ?? '';
    }
    if (r.isError) {
      render(r.structuredContent ?? { state: 'unavailable' });
      if (name === 'tapilot_preview_resume' && r.content?.some(item => item.type === 'text' && item.text)) {
        connectionErrorActive = true;
        notice.textContent = connectionErrorMessage(r.content?.find(item => item.type === 'text')?.text || '无法读取手机画面');
        notice.hidden = false;
      } else if (name === 'tapilot_preview_control') {
        notice.textContent = t("操作失败或结果未知，未自动重试。请查看新画面后再操作。"); notice.hidden = false;
      } else if (name === 'tapilot_devices') {
        connectionErrorActive = args.action === 'connect';
        const message = connectionErrorMessage(r.content?.find(item => item.type === 'text')?.text || '无法读取设备列表');
        if (connectionErrorActive) { recovery.textContent = message; recoveryDetails.hidden = false; }
        else { notice.textContent = message; notice.hidden = false; }
      }
    } else {
      render(name === 'tapilot_disconnect' ? { state: 'disconnected' } : r.structuredContent);
      if (r.structuredContent?.action_completed && state !== 'live') {notice.textContent = t("操作已执行，但画面刷新失败。请重新获取画面，不要重复操作。");notice.hidden=false;}
    }
    return name === 'tapilot_devices' ? !r.isError : completed;
  } catch { if (generation === epoch && !stopped) { render({ state: 'unavailable' }); if (['tapilot_devices', 'tapilot_preview_resume'].includes(name)) return false; notice.textContent = t("操作结果未知，请重新连接并检查。输入草稿已保留，请勿直接重复提交。"); notice.hidden = false; } return false; }
  finally { busy = false; syncControls(); schedule(frame ? 250 : 0); }
}
function manual(args, snapshot = displayedFrame) {
  if (!interactive || busy || !snapshot || state !== 'live' || stopped) return Promise.resolve(false);
  // Never apply coordinates selected on an older image to a later frame.
  return action('tapilot_preview_control', { ...args, frame_id: snapshot.id });
}
function point(event, snapshot = displayedFrame, clamp = false) {
  return snapshot && imagePoint(image.getBoundingClientRect(), snapshot.width, snapshot.height, event.clientX, event.clientY, clamp);
}
image.addEventListener('pointerdown', event => {
  if (!interactive || busy) return;
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
  const generation = ++pairingGeneration;
  pairingActive = true; pairingStarting = true; pairingExpired = false; pairRefresh.hidden = true; devicesPanel.open = false;
  pairingQr.hidden = true; pairConfirm.hidden = true; pair.hidden = true;
  document.querySelector('#reconnect-pair').hidden = true;
  pairingStatus.hidden = false; pairingStatus.textContent = t('正在生成二维码…');
  recoveryDetails.hidden = true; resume.hidden = true; status.textContent = t('配对新手机');
  busy = true; syncControls(); notice.hidden = true; pairingLink.hidden = true; pairingLink.removeAttribute('href');
  try {
    let deadline;
    const result = await Promise.race([
      app.callServerTool({ name: 'tapilot_preview_pair', arguments: { action: 'pair' } }),
      new Promise((_, reject) => { deadline = setTimeout(() => reject(Error('pairing timeout')), 12000); }),
    ]).finally(() => clearTimeout(deadline));
    if (generation !== pairingGeneration || stopped) return;
    if (result.isError || !result.structuredContent?.url) throw new Error('pairing unavailable');
    const url = new URL(result.structuredContent.url);
    if (url.protocol !== 'http:' || !['127.0.0.1', 'localhost'].includes(url.hostname)) throw new Error('invalid pairing window');
    pairingLink.href = url.href; pairingLink.hidden = true;
    pairingExpiresAt = result.structuredContent.expires_at ?? 0;
    pairingQr.hidden = true; pairingQr.removeAttribute('src'); pairConfirm.hidden = true; pairRefresh.hidden = true;
    if (/^data:image\/png;base64,/.test(result._meta?.pairingQr ?? '') && pairingExpiresAt > Date.now()) {
      pairingQr.onerror = () => {
        if (generation !== pairingGeneration || !pairingActive) return;
        pairingQr.hidden = true; pairRefresh.hidden = false; pairingLink.hidden = false;
        pairingStatus.textContent = t('二维码无法显示，请刷新或打开配对窗口。');
      };
      pairingQr.src = result._meta.pairingQr; pairingQr.hidden = false; pair.hidden = true;
      pairingStatus.textContent = t("等待手机扫码…"); pairingStatus.hidden = false;
    }
    pairingActive = true; pairCancel.hidden = false;
    if (pairingQr.hidden) { pairingLink.hidden = false; pairingStatus.textContent = t('请打开配对窗口，用手机扫描二维码。'); }
    pairingLink.onclick = async event => {
      event.preventDefault();
      try { const opened = await app.openLink({ url: url.href }); if (opened?.isError) throw new Error('open failed'); }
      catch { notice.textContent = t("无法打开配对窗口，请让聊天助手提供配对链接。"); notice.hidden = false; }
    };
    status.textContent = t('用手机扫码，再在电脑确认连接');
  } catch {
    if (generation !== pairingGeneration || stopped) return;
    pairingExpired = true; pairRefresh.hidden = false; pairingStatus.hidden = false;
    pairingStatus.textContent = t('二维码生成失败，请重试。');
  } finally {
    if (generation === pairingGeneration) { pairingStarting = false; busy = false; syncControls(); schedule(0); }
  }
};
document.querySelector('#device-pair').onclick = () => pair.onclick();
document.querySelector('#reconnect-pair').onclick = () => pair.onclick();
for (const id of ['#device-pair', '#pair-cancel', '#device-disconnect']) {
  document.querySelector(id).addEventListener('click', event => event.stopPropagation());
}
async function cancelPairing() {
  if (pairingStarting) {
    clearPairingQr(); pairingStarting = false; busy = false; pairingExpiresAt = 0;
    pairingLink.hidden = true; pairingLink.removeAttribute('href');
    app.callServerTool({name:'tapilot_preview_pair',arguments:{action:'pair_cancel'}}).catch(() => {});
    render({state:'unavailable'}); schedule(0); return true;
  }
  if (busy) return false;
  pairingStarting = false;
  busy = true; ++epoch; ++pairingGeneration; clearTimeout(timer); syncControls();
  try {
    const result = await app.callServerTool({name:'tapilot_preview_pair', arguments:{action:'pair_cancel'}});
    if (result.isError) throw Error('cancel failed');
    clearPairingQr(); pairingExpiresAt = 0; pairingLink.hidden = true; pairingLink.removeAttribute('href');
    notice.hidden = true; notice.textContent = '';
    render({state:'unavailable'});
    return true;
  } catch { notice.textContent = t('无法取消配对，请重试。'); notice.hidden = false; return false; }
  finally { busy = false; syncControls(); schedule(0); }
}
pairCancel.onclick = cancelPairing;
function expirePairing() {
  pairingExpired = true; pairingQr.hidden = true; pairingQr.removeAttribute('src'); pairingLink.hidden = true; pairingLink.removeAttribute('href'); pairConfirm.hidden = true;
  pairRefresh.hidden = false; pairingStatus.hidden = false; pairingStatus.textContent = t('二维码已过期'); status.textContent = t('配对新手机');
}
pairRefresh.onclick = () => pair.onclick();
async function refreshPairing() {
  if (pairingExpired) return;
  if (Date.now() >= pairingExpiresAt) { expirePairing(); return; }
  const generation = pairingGeneration;
  let result, deadline;
  try {
    result = await Promise.race([
      app.callServerTool({name:'tapilot_preview_pair', arguments:{action:'pair_status'}}),
      new Promise((_, reject) => { deadline = setTimeout(() => reject(Error('scan status timeout')), 5000); }),
    ]);
  }
  catch {
    if (generation !== pairingGeneration || !pairingActive) return;
    expirePairing(); pairingStatus.textContent = t('无法读取扫码状态，请重试。'); return;
  } finally { clearTimeout(deadline); }
  if (generation !== pairingGeneration || !pairingActive) return;
  if (result.isError) { expirePairing(); notice.textContent = t("配对已过期或无法继续，请重新生成二维码。"); notice.hidden = false; return; }
  const data = result.structuredContent;
  if (data?.paired) { clearPairingQr(); pairingExpiresAt = 0; notice.hidden = true; status.textContent = t('配对成功，正在连接…'); devicesPanel.open = false; selectedDeviceId = ''; await refreshDevices(); return; }
  if (data?.error) { notice.textContent = t(data.error); notice.hidden = false; }
  pairConfirm.hidden = !data?.pending; pairConfirm.disabled = busy || !!data?.pairing;
  if (!data?.pending && pairingQr.hidden && !pairingLink.hidden) return;
  pairingStatus.textContent = data?.pending ? `${data.pending.host} · ${t('手机已扫码，请确认配对')}` : t("等待手机扫码…");
}
pairConfirm.onclick = async () => {
  if (busy) return;
  busy = true; ++epoch; clearTimeout(timer); ++pairingGeneration; pairConfirm.disabled = true; syncControls(); notice.hidden = true;
  pairingStatus.textContent = t('正在确认连接…');
  try {
    const result = await app.callServerTool({name:'tapilot_preview_pair', arguments:{action:'pair_confirm'}});
    if (result.isError) throw Error(result.content?.find(item => item.type === 'text')?.text || t('配对未完成，结果可能不确定。请重新扫码后确认连接。'));
    clearPairingQr(); pairingExpiresAt = 0; status.textContent = t('配对成功，正在连接…'); devicesPanel.open = false; selectedDeviceId = ''; await refreshDevices();
  } catch (error) { notice.textContent = connectionErrorMessage(error.message); notice.hidden = false; pairingStatus.textContent = t('手机已扫码，请确认配对'); }
  finally { busy = false; pairConfirm.disabled = false; syncControls(); schedule(0); }
};
function applyTheme(context) {
  if (context?.theme) applyDocumentTheme(context.theme);
  if (context?.styles?.variables) applyHostStyleVariables(context.styles.variables);
}
app.onhostcontextchanged = context => {
  syncDisplayMode(context);
  applyTheme(context);
  if (context.locale) hostLocale = context.locale;
  applyLocale(lastData);
  if (lastData) render(lastData);
};
resume.onclick = async () => { if (pairingActive && !await cancelPairing()) return; await action('tapilot_preview_resume'); };
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
window.addEventListener('pageshow', () => { stopped = false; windowFocused = true; render({state:'connecting'}); updateActivity(); schedule(0); });
localize();
const hostNotice = document.querySelector('#host-notice');
hostNotice.hidden = interactive;
if (!interactive) {
  hostNotice.textContent = t('当前客户端支持实时预览。配对、切换设备和操作手机请交给助手完成。');
  image.style.cursor = 'default';
}
syncControls();
try {
  await app.connect();
  applyTheme(app.getHostContext?.());
  applyLocale(lastData);
  if (lastData) render(lastData);
  // Doubao initially embeds apps in the message. Request its expanded surface
  // once per mounted view; polling and subsequent results must not reopen it.
  const context = app.getHostContext?.();
  syncDisplayMode(context);
  if (!expandPanel.hidden) await expand();
  // The sole open tool requests the panel. Repeated frame/tool results must not
  // request another fullscreen host tab.
  updateActivity(); schedule(0);
} catch { status.textContent = t("请在支持 MCP App 的宿主中打开 Tapilot"); }
