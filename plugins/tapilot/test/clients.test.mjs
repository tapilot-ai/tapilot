import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, writeFile, rm, readlink, access } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { parse } from 'jsonc-parser';
import { HOSTS, hostProfile, hostTools, hostResourceMeta, resourceRequest } from '../server/hosts.mjs';
import { clientConfiguration } from '../server/clients/catalog.mjs';
import { installClient, uninstallClient } from '../server/clients/install.mjs';
import { probeConnector } from '../server/clients/probe.mjs';
import { createPanelBridge } from '../ui/panel-bridge.mjs';
import { packageRoot } from '../server/clients/catalog.mjs';

async function fixture(t) {
  const home = await mkdtemp(join(tmpdir(), 'tapilot-clients-'));
  t.after(() => rm(home, { recursive: true, force: true }));
  return { home, skillsRoot: join(home, 'canonical skills'), probe: async () => ({ protocol: 'passed', hostRendering: 'not_tested' }), prepareRuntime: async () => ({ root: packageRoot, node: process.execPath, dispose: async () => {} }) };
}

for (const host of HOSTS) test(`${host}: actual stdio protocol and panel resource, without starting a phone runtime`, async t => {
  const options = await fixture(t);
  const plan = clientConfiguration(host, options);
  plan.connector.env = { ...plan.connector.env, TAPILOT_STATE_DIR: join(options.home, 'runtime-state'), TAPILOT_CONFIG: join(options.home, 'wireless.json') };
  const probe = await probeConnector(plan.connector);
  assert.equal(probe.protocol, 'passed');
  assert.equal(probe.panelResource, 'passed');
  assert.equal(probe.toolCount, host === 'workbuddy' ? 1 : 8);
  assert.equal(probe.hostRendering, 'not_tested');
  await assert.rejects(access(join(options.home, 'runtime-state')), { code: 'ENOENT' });
});

test('host metadata preserves Codex and isolates WorkBuddy read-only policy', () => {
  const codex = hostTools(hostProfile());
  assert.deepEqual(codex.filter(tool => !tool._meta?.ui?.visibility).map(tool => tool.name), ['tapilot_preview_open']);
  assert.ok(codex[1]._meta['openai/ui']);
  assert.ok(hostResourceMeta(hostProfile())['openai/ui']);
  assert.deepEqual(hostResourceMeta(hostProfile('doubao'))['openai/ui'], {availableDisplayModes:['fullscreen'],preferredDisplayMode:'fullscreen'});
  assert.equal(hostResourceMeta(hostProfile('cursor'))['openai/ui'], undefined);
  const workbuddy = hostTools(hostProfile('workbuddy'));
  assert.equal(workbuddy.length, 1);
  assert.equal(workbuddy[0]._meta.workbuddy.ui.launchSurface, 'panel');
  assert.equal(hostTools(hostProfile()).length, 8, 'a restricted connector never mutates the global tool catalog');
  assert.throws(() => hostProfile('arbitrary'), /Unknown/);
});

test('read-only resource routes cannot encode actions or reach admin endpoints', () => {
  const host = hostProfile('workbuddy');
  assert.deepEqual(resourceRequest('ui://tapilot/frame/viewer-1', host), { name: 'tapilot_preview_frame', arguments: { viewer_id: 'viewer-1' } });
  assert.deepEqual(resourceRequest('ui://tapilot/devices', host), { name: 'tapilot_devices', arguments: { action: 'list' } });
  for (const uri of ['ui://tapilot/devices?action=connect', 'ui://tapilot/frame/viewer?pause=1', 'ui://tapilot/pair_confirm', 'http://127.0.0.1/call', 'ui://tapilot/frame/../stop'])
    assert.throws(() => resourceRequest(uri, host), /Unknown/);
  assert.throws(() => resourceRequest('ui://tapilot/devices', hostProfile('codex')), /Unknown/);
});

test('panel declares expansion capability during MCP App initialization', () => {
  let capabilities;
  class App { constructor(info, declared) { capabilities = declared; } callServerTool() {} }
  createPanelBridge({host:hostProfile('doubao'),AppClass:App});
  assert.deepEqual(capabilities,{availableDisplayModes:['inline','fullscreen']});
});

test('read-only UI uses resource reads and never falls back to a write after failure', async () => {
  let reads = 0, writes = 0; const uris = [];
  class App {
    async callServerTool() { writes++; throw new Error('Host tool calls blocked'); }
    async readServerResource({ uri }) { reads++; uris.push(uri); return { contents: [{ uri, text: JSON.stringify({ structuredContent: { state: 'manual' }, content: [] }) }] }; }
  }
  const app = createPanelBridge({ host: hostProfile('workbuddy'), AppClass: App });
  assert.equal((await app.callServerTool({ name: 'tapilot_preview_frame', arguments: { viewer_id: 'test' } })).structuredContent.state, 'manual');
  await app.callServerTool({ name: 'tapilot_preview_frame', arguments: { viewer_id: 'test' } });
  assert.notEqual(uris[0], uris[1], 'resource URIs change so a host cache cannot freeze a live preview');
  await assert.rejects(app.callServerTool({ name: 'tapilot_devices', arguments: { action: 'connect' } }), /read-only/);
  await assert.rejects(app.callServerTool({ name: 'tapilot_preview_pair', arguments: { action: 'pair_confirm' } }), /read-only/);
  assert.equal(reads, 2); assert.equal(writes, 0);
});

test('installer preserves JSONC, other connectors and canonical skill source through update and uninstall', async t => {
  const options = await fixture(t);
  const configFile = join(options.home, '.cursor/mcp.json');
  await mkdir(dirname(configFile), { recursive: true });
  await writeFile(configFile, '{\n  // User comment\n  "mcpServers": {"other": {"command": "keep"}},\n  "unrelated": true,\n}\n');
  const result = await installClient('cursor', options);
  assert.equal(result.installed, true);
  assert.equal(await readlink(result.skillLink), result.skill);
  assert.ok(result.skill.startsWith(options.skillsRoot));
  assert.match(await readFile(join(result.skill, 'SKILL.md'), 'utf8'), /CLI for agent phone actions/);
  await installClient('cursor', options);
  const installed = await readFile(configFile, 'utf8');
  assert.match(installed, /User comment/);
  assert.equal(parse(installed).mcpServers.other.command, 'keep');
  assert.equal(parse(installed).unrelated, true);
  assert.equal((await uninstallClient('cursor', options)).pairingPreserved, true);
  const removed = await readFile(configFile, 'utf8');
  assert.match(removed, /User comment/);
  assert.equal(parse(removed).mcpServers.tapilot, undefined);
  assert.equal(parse(removed).mcpServers.other.command, 'keep');
  await assert.rejects(access(result.skillLink), { code: 'ENOENT' });
});

test('installer validates protocol before writes and rejects unmanaged or edited content', async t => {
  const options = await fixture(t);
  const configFile = join(options.home, '.workbuddy/mcp.json');
  await assert.rejects(installClient('workbuddy', { ...options, probe: async () => { throw new Error('probe failed'); } }), /probe failed/);
  await assert.rejects(access(configFile), { code: 'ENOENT' });
  await mkdir(dirname(configFile), { recursive: true });
  const original = '{"mcpServers":{"tapilot":{"command":"mine"}}}';
  await writeFile(configFile, original);
  await assert.rejects(installClient('workbuddy', options), /unmanaged/);
  assert.equal(await readFile(configFile, 'utf8'), original);
  await writeFile(configFile, '{}');
  const installed = await installClient('workbuddy', options);
  await writeFile(join(installed.skill, 'my-notes.md'), 'user content');
  await assert.rejects(uninstallClient('workbuddy', options), /extra files/);
  assert.equal(parse(await readFile(configFile, 'utf8')).mcpServers.tapilot.command, installed.launcher.command);
});

test('Doubao export includes a launcher without required environment fields and a complete skill', async t => {
  const options = await fixture(t);
  const result = await installClient('doubao', options);
  assert.equal(result.configFile, null);
  assert.equal(result.skillLink, null);
  assert.match(await readFile(result.launcher.args[0], 'utf8'), /TAPILOT_HOST="doubao"/);
  assert.match(await readFile(join(result.skill, 'scripts/tapilot.mjs'), 'utf8'), /server\/cli.mjs/);
  assert.equal((await uninstallClient('doubao', options)).removed, true);
});

test('VS Code uses its servers schema and native Codex install never creates duplicate registration', async t => {
  const options = await fixture(t);
  const result = await installClient('vscode', options);
  const config = parse(await readFile(result.configFile, 'utf8'));
  assert.equal(config.servers.tapilot.type, 'stdio');
  assert.equal(config.mcpServers, undefined);
  assert.equal((await installClient('codex', options)).installed, false);
  assert.throws(() => clientConfiguration('dsh-go', options), /Unknown client/);
});
