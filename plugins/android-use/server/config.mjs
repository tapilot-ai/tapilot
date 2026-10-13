import { installationGuide } from './installation.mjs';
import { homedir } from 'node:os';
import { join, dirname } from 'node:path';
import { readFile, mkdir, writeFile, rename, chmod } from 'node:fs/promises';
import { isIP } from 'node:net';
import { randomUUID } from 'node:crypto';

export const configPath = () => process.env.TAPILOT_CONFIG || join(homedir(), '.config', 'tapilot', 'wireless.json');
export function validateConfig(value) {
  if (!value || value.version !== 1 || isIP(value.host) !== 4 || !Number.isInteger(value.port) || value.port < 1024 || value.port > 65535
      || !/^[a-f0-9]{64}$/.test(value.fingerprint) || !/^[A-Za-z0-9_-]{43}$/.test(value.token)) {
    throw new Error('Invalid Tapilot pairing data');
  }
  return { version: 1, host: value.host, port: value.port, fingerprint: value.fingerprint, token: value.token };
}
export function parsePairingLink(link) {
  if (!link.startsWith('tapilot://pair/') || link.length > 2048) throw new Error('Paste the pairing link copied from Tapilot settings');
  try { return validateConfig(JSON.parse(Buffer.from(link.slice('tapilot://pair/'.length), 'base64url').toString('utf8'))); }
  catch { throw new Error('Invalid Tapilot pairing link'); }
}
export async function loadConfig() {
  try { return validateConfig(JSON.parse(await readFile(configPath(), 'utf8'))); }
  catch { throw Object.assign(new Error('Pair this computer first. Call tapilot_midscene(action=setup) for Tapilot APK downloads and installation guidance. Keep pairing links out of chat.'), { code: 'setup_required', installation: installationGuide }); }
}
export async function saveConfig(value, path = configPath()) {
  validateConfig(value);
  await mkdir(dirname(path), { recursive: true, mode: 0o700 });
  const temporary = `${path}.${randomUUID()}.tmp`;
  await writeFile(temporary, JSON.stringify(value), { mode: 0o600, flag: 'wx' });
  await rename(temporary, path);
  await chmod(path, 0o600);
}
