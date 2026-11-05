import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, cp, symlink, readFile, writeFile, readdir, rm, access, realpath } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { execFile, spawn } from 'node:child_process';
import { once } from 'node:events';
import { createHash } from 'node:crypto';
import { promisify } from 'node:util';
import { installClient, uninstallClient } from '../server/clients/install.mjs';
import { packageRoot } from '../server/clients/catalog.mjs';
import { parse } from 'jsonc-parser';
import { clientCommand, detection } from '../server/clients/index.mjs';
import { probeConnector } from '../server/clients/probe.mjs';
const exec = promisify(execFile);

test('explicit target overrides inherited host evidence; missing target never installs', async t => {
  const home = await mkdtemp(join(tmpdir(), 'tapilot-target-'));
  t.after(() => rm(home, { recursive: true, force: true }));
  assert.equal(detection('doubao', { host: 'codex' }).selectedClient, 'doubao');
  assert.equal(detection('doubao', { host: 'codex' }).recommendation, 'doubao');
  const result = await clientCommand('install', {}, { home });
  assert.equal(result.installed, false);
  assert.equal(result.selection, 'confirmation_required');
  assert.deepEqual(await readdir(home), []);
  await assert.rejects(clientCommand('install', { client: 'typo' }, { home }), /Unknown client/);
  assert.deepEqual(await readdir(home), []);
});

test('real managed install survives removed source and Node, updates old launcher, and preserves prior install on failed update', { timeout: 120000 }, async t => {
  const home = await mkdtemp(join(tmpdir(), 'tapilot-stable-'));
  t.after(() => rm(home, { recursive: true, force: true }));
  const source = join(home, 'temporary checkout');
  await mkdir(source);
  for (const name of ['server', 'ui', 'assets', 'integrations', 'skills', 'scripts', 'package.json', 'LICENSE'])
    await cp(join(packageRoot, name), join(source, name), { recursive: true });
  await symlink(join(packageRoot, 'node_modules'), join(source, 'node_modules'), 'dir');
  const originalNode = join(home, 'temporary-node');
  await cp(process.execPath, originalNode);
  const options = { home, root: source, node: originalNode, skillsRoot: join(home, 'canonical skills') };
  const result = await installClient('doubao', options);
  assert.equal(result.probe.protocol, 'passed');
  assert.equal(result.probe.hostRendering, 'not_tested');
  assert.ok(!result.runtime.node.includes('temporary-node'));
  assert.equal((await readdir(result.runtime.root, { recursive: true, withFileTypes: true })).some(entry => entry.isSymbolicLink()), false, 'managed code and dependencies have no external symlinks');
  await rm(source, { recursive: true });
  await rm(originalNode);
  assert.match((await exec(result.runtime.node, [join(result.skill, 'scripts/tapilot.mjs'), '--help'])).stdout, /Tapilot CLI/);
  assert.equal((await probeConnector({ ...result.launcher, env: {} })).protocol, 'passed');
  const configPath = join(home, 'isolated-phone.json');
  const stateRoot = join(home, 'isolated-phone-state');
  const daemonEnv = { ...process.env, TAPILOT_CONFIG: configPath, TAPILOT_STATE_DIR: stateRoot, TAPILOT_INSTALLATIONS_DIR: join(home, 'empty-installations') };
  const cli = join(result.runtime.root, 'server/cli.mjs');
  try {
    const key = createHash('sha256').update(configPath).digest('hex').slice(0, 12);
    const metadata = join(stateRoot, 'runtime', key, 'runtime.json');
    await exec(process.execPath, [join(packageRoot, 'server/cli.mjs'), 'devices'], { env: daemonEnv, timeout: 15000 });
    const unmanaged = JSON.parse(await readFile(metadata, 'utf8'));
    assert.equal(unmanaged.managed, false);
    await exec(process.execPath, [join(packageRoot, 'server/cli.mjs'), 'pause'], { env: daemonEnv, timeout: 15000 });
    // Import the managed package under an external host Node, just as DSH does.
    const started = JSON.parse((await exec(process.execPath, [cli, 'devices'], { env: daemonEnv, timeout: 15000 })).stdout);
    assert.equal(started.devices.length, 0);
    const daemon = JSON.parse(await readFile(metadata, 'utf8'));
    assert.equal(await realpath(daemon.execPath), await realpath(result.runtime.node), 'background startup uses the retained Node rather than the importing host executable');
    assert.equal(daemon.managed, true);
    assert.notEqual(daemon.pid, unmanaged.pid, 'same-patch host-Node daemon is handed off gracefully');
    const health = await fetch(`http://127.0.0.1:${daemon.port}/health`, { headers: { Authorization: `Bearer ${daemon.token}` } });
    assert.equal((await health.json()).privacyPaused, true, 'daemon handoff preserves a private pause');
    const nativeEnv = { ...daemonEnv, TAPILOT_INSTALLATIONS_DIR: join(home, '.local/share/tapilot/clients') };
    await exec(process.execPath, [join(packageRoot, 'server/cli.mjs'), 'devices'], { env: nativeEnv, timeout: 15000 });
    assert.equal(JSON.parse(await readFile(metadata, 'utf8')).pid, daemon.pid, 'a native plugin reuses the shared managed daemon without restart');
  } finally {
    await exec(process.execPath, [cli, 'runtime-stop'], { env: daemonEnv, timeout: 15000 });
  }
  const doctor = await clientCommand('doctor', { client: 'doubao' }, { home, skillsRoot: options.skillsRoot });
  assert.equal(doctor.skillLauncher, 'passed');
  assert.equal(doctor.protocol, 'passed');
  assert.equal(doctor.hostRendering, 'not_tested');
  assert.deepEqual(doctor.launcher, result.launcher, 'repair guidance includes the actual form-ready launch command');
  assert.equal(doctor.skillFolder, result.skillFolder);
  const before = await readFile(result.record, 'utf8');
  await assert.rejects(installClient('doubao', {
    ...options, root: result.runtime.root, node: result.runtime.node,
    probe: async () => { throw new Error('update probe rejected'); },
  }), /update probe rejected/);
  assert.equal(await readFile(result.record, 'utf8'), before);
  assert.equal((await readdir(join(result.directory, 'releases'))).length, 1);
  const updated = await installClient('doubao', { ...options, root: result.runtime.root, node: result.runtime.node });
  assert.notEqual(updated.runtime.root, result.runtime.root);
  assert.deepEqual(updated.launcher, result.launcher, 'the whole saved GUI command remains stable');
  assert.equal(await realpath(updated.launcher.command), await realpath(updated.runtime.node));
  assert.equal((await probeConnector({ ...result.launcher, env: {} })).protocol, 'passed', 'old saved GUI command starts the new record-backed connector');
  const config = await clientCommand('config', { client: 'doubao' }, { home, skillsRoot: options.skillsRoot });
  assert.equal(config.runtime.root, updated.runtime.root);
  assert.ok(!JSON.stringify(config).includes('temporary checkout'));
  assert.ok(!JSON.stringify(config).includes('temporary-node'));
  const running = spawn(updated.runtime.node, ['--input-type=module', '-e', 'setInterval(() => {}, 1000)'], { stdio: 'ignore' });
  t.after(() => running.kill());
  await once(running, 'spawn');
  const removed = await uninstallClient('doubao', { home, skillsRoot: options.skillsRoot });
  assert.equal(removed.pairingPreserved, true);
  assert.equal(removed.runtimeRetained, true, 'uninstall cannot delete code still used by a shared daemon or host');
  await access(updated.runtime.node);
  const stopped = once(running, 'exit'); running.kill(); await stopped;
  await assert.rejects(access(result.record), { code: 'ENOENT' });
});

test('skill-only integration exports usable guidance without an MCP connector', async t => {
  const home = await mkdtemp(join(tmpdir(), 'tapilot-skill-'));
  t.after(() => rm(home, { recursive: true, force: true }));
  const options = { home, skillsRoot: join(home, 'canonical'), prepareRuntime: async () => ({ root: packageRoot, node: process.execPath, dispose: async () => {} }) };
  const result = await installClient('skill', options);
  assert.equal(result.panel, 'none');
  assert.equal(result.connector, null);
  assert.equal(result.registration, 'skill_exported');
  assert.match(await readFile(join(result.skill, 'SKILL.md'), 'utf8'), /Do not call tapilot_preview_open/);
  await access(join(result.skill, 'references/client-setup.md'));
  const doctor = await clientCommand('doctor', { client: 'skill' }, options);
  assert.equal(doctor.skillLauncher, 'passed');
  assert.equal(doctor.protocol, 'not_applicable');
  assert.equal(doctor.hostRendering, 'not_applicable');
  await assert.rejects(access(join(home, '.workbuddy')), { code: 'ENOENT' });
});

test('doctor reports an edited installed config and installer preserves an edited launcher', async t => {
  const home = await mkdtemp(join(tmpdir(), 'tapilot-diagnosis-'));
  t.after(() => rm(home, { recursive: true, force: true }));
  const options = { home, skillsRoot: join(home, 'canonical'), prepareRuntime: async () => ({ root: packageRoot, node: process.execPath, dispose: async () => {} }) };
  const result = await installClient('workbuddy', options);
  const original = await readFile(result.configFile, 'utf8');
  const config = parse(original);
  config.mcpServers.tapilot.command = 'user-edited-command';
  const edited = JSON.stringify(config);
  await writeFile(result.configFile, edited);
  const doctor = await clientCommand('doctor', { client: 'workbuddy' }, options);
  assert.equal(doctor.registration, 'missing_or_edited');
  assert.equal(doctor.isError, true);
  assert.equal(await readFile(result.configFile, 'utf8'), edited);
  await writeFile(result.configFile, original);
  const launcher = result.launcher.args[0];
  await writeFile(launcher, '// user edit\n');
  await assert.rejects(installClient('workbuddy', options), /launcher was edited/);
  await assert.rejects(uninstallClient('workbuddy', options), /launcher was edited/);
  assert.equal(await readFile(launcher, 'utf8'), '// user edit\n');
  assert.equal(await readFile(result.configFile, 'utf8'), original);
});
