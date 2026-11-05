import { protectPrivatePath } from './private-path.mjs';
import { installationGuide } from './installation.mjs';
import { homedir } from 'node:os';
import { join, dirname } from 'node:path';
import { readFile, mkdir, writeFile, rename, chmod } from 'node:fs/promises';
import { isIP } from 'node:net';
import lockfile from 'proper-lockfile';
import { randomUUID } from 'node:crypto';

export const configPath = () => process.env.TAPILOT_CONFIG || join(homedir(), '.config', 'tapilot', 'wireless.json');
function endpoints(values = []) {
  if (!Array.isArray(values)) return [];
  const seen = new Set();
  return values.filter(v => {
    if (!v || typeof v.host !== 'string' || isIP(v.host) !== 4 || !Number.isInteger(v.port) || v.port < 1024 || v.port > 65535) return false;
    const key = v.host + ':' + v.port;
    if (seen.has(key)) return false;
    seen.add(key); return true;
  }).slice(0, 8).map(({host, port}) => ({host, port}));
}
export function validateConfig(value) {
  if (!value || value.version !== 1 || isIP(value.host) !== 4 || !Number.isInteger(value.port) || value.port < 1024 || value.port > 65535
      || !/^[a-f0-9]{64}$/.test(value.fingerprint) || !/^[A-Za-z0-9_-]{43}$/.test(value.token)) {
    throw new Error('Invalid Tapilot pairing data');
  }
  if (value.language !== undefined && !['en', 'zh'].includes(value.language)) throw new Error('Invalid pairing language');
  return { version: 1, host: value.host, port: value.port, fingerprint: value.fingerprint, token: value.token,
    ...(value.endpoints ? {endpoints:endpoints(value.endpoints)} : {}),
    ...(value.language ? {language:value.language} : {}),
    ...(typeof value.name === 'string' && value.name.trim() ? {name:value.name.trim().slice(0,80)} : {}) };
}
export function parsePairingLink(link) {
  if (!link.startsWith('tapilot://pair/') || link.length > 2048) throw new Error('Paste the pairing link copied from Tapilot settings');
  try { return validateConfig(JSON.parse(Buffer.from(link.slice('tapilot://pair/'.length), 'base64url').toString('utf8'))); }
  catch { throw new Error('Invalid Tapilot pairing link'); }
}
export async function loadDevices(path = configPath()) {
  let value;
  try { value = JSON.parse(await readFile(path, 'utf8')); }
  catch (error) { if (error.code === 'ENOENT') return {version:2, activeDeviceId:null, devices:[]}; throw error; }
  if (value.version === 1) {
    const config = validateConfig(value);
    return {version:2, activeDeviceId:config.fingerprint, devices:[{...config, autoConnect:true}]};
  }
  if (value.version !== 2 || !Array.isArray(value.devices)) throw Error('Invalid paired device registry');
  const devices = value.devices.map(device => ({...validateConfig(device), autoConnect:device.autoConnect !== false}));
  if (new Set(devices.map(d => d.fingerprint)).size !== devices.length ||
      (value.activeDeviceId !== null && !devices.some(d => d.fingerprint === value.activeDeviceId)))
    throw Error('Invalid paired device selection');
  return {version:2, activeDeviceId:value.activeDeviceId, devices};
}
export async function loadConfig() {
  const registry = await loadDevices();
  const config = registry.devices.find(d => d.fingerprint === registry.activeDeviceId);
  if (config) return validateConfig(config);
  throw Object.assign(new Error('No selected paired device. List devices and connect, or pair this computer first.'),
    {code:'setup_required', installation:installationGuide});
}
async function changeDevices(path, change) {
  await mkdir(dirname(path), { recursive: true, mode: 0o700 });
  const release = await lockfile.lock(dirname(path), {lockfilePath:`${path}.lock`, retries:{retries:20,minTimeout:25,maxTimeout:100}});
  try {
    const registry = await loadDevices(path);
    change(registry);
    const temporary = `${path}.${randomUUID()}.tmp`;
    await writeFile(temporary, '', {mode:0o600,flag:'wx'});
    await protectPrivatePath(temporary);
    await writeFile(temporary, JSON.stringify(registry));
    await rename(temporary, path);
    await chmod(path,0o600);
    return registry;
  } finally { await release(); }
}
export async function saveConfig(value, path = configPath(), {activate = true} = {}) {
  const config = validateConfig(value);
  return changeDevices(path, registry => {
    const index = registry.devices.findIndex(d => d.fingerprint === config.fingerprint);
    // Endpoint refresh must not resurrect a pairing the user removed in another host.
    if (!activate && (index < 0 || registry.devices[index].token !== config.token)) return;
    const previous = registry.devices[index];
    const history = endpoints([config, ...(previous ? [previous, ...(previous.endpoints ?? [])] : [])]);
    const device = {...previous, ...config, endpoints:history, autoConnect:activate || registry.devices[index]?.autoConnect === true};
    if (index < 0) registry.devices.push(device); else registry.devices[index] = device;
    if (activate) registry.activeDeviceId = config.fingerprint;
  });
}
export async function selectDevice(action, id, path = configPath()) {
  return changeDevices(path, registry => {
    const device = registry.devices.find(d => d.fingerprint === (id ?? registry.activeDeviceId));
    if (!device) throw Error('Paired device not found. List devices first.');
    if (action === 'connect') { registry.activeDeviceId = device.fingerprint; device.autoConnect = true; }
    else if (action === 'disconnect') device.autoConnect = false;
    else if (action === 'forget') {
      registry.devices = registry.devices.filter(d => d !== device);
      if (registry.activeDeviceId === device.fingerprint) registry.activeDeviceId = null;
    } else throw Error('Invalid device action');
  });
}
