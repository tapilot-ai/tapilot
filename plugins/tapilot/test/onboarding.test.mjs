import { sameLink } from '../server/clients/entries.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm, readFile, readlink, unlink, symlink } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { clientCommand } from '../server/clients/index.mjs';
import { packageRoot } from '../server/clients/catalog.mjs';
import { installationGuide } from '../server/clients/onboarding.mjs';

async function fixture(t) {
  const home = await mkdtemp(join(tmpdir(), 'tapilot-onboard-'));
  t.after(() => rm(home, { recursive: true, force: true }));
  return { home, skillsRoot: join(home, 'canonical skills'), prepareRuntime: async () => ({ root: packageRoot, node: process.execPath, dispose: async () => {} }) };
}

test('onboarding runs the installed connector and leaves native/phone acceptance pending', async t => {
  const options = await fixture(t);
  const first = await clientCommand('onboard', { client: 'workbuddy' }, options);
  assert.equal(first.installed, true);
  assert.equal(first.diagnostics.protocol, 'passed');
  assert.equal(first.diagnostics.skillLauncher, 'passed');
  assert.equal(first.onboarding.complete, false);
  assert.equal(first.onboarding.checks.find(c => c.id === 'native_panel').status, 'not_tested');
  assert.equal(first.onboarding.checks.find(c => c.id === 'phone_task').status, 'not_tested');
  const config = await readFile(first.configFile, 'utf8');
  const updated = await clientCommand('onboard', { client: 'workbuddy' }, options);
  assert.deepEqual(updated.launcher, first.launcher);
  assert.equal(await readFile(first.configFile, 'utf8'), config, 'host config stays stable across updates');
  assert.ok(sameLink(await readlink(join(first.directory, 'current')), packageRoot));
  const record = await readFile(first.record, 'utf8');
  await unlink(join(first.directory, 'current'));
  await symlink(options.home, join(first.directory, 'current'), 'dir');
  await assert.rejects(clientCommand('onboard', { client: 'workbuddy' }, options), /edited or is unmanaged/);
  assert.equal(await readFile(first.record, 'utf8'), record, 'failed update retains installation record');
  assert.equal(await readFile(first.configFile, 'utf8'), config);
  assert.equal(await readlink(join(first.directory, 'current')), options.home, 'user edit is preserved');
});

test('DSH onboarding keeps custom home and stable registration path through update', async t => {
  const options = await fixture(t);
  const dshHome = join(options.home, 'custom DSH');
  const first = await clientCommand('onboard', { client: 'dsh' }, { ...options, dshHome });
  const updated = await clientCommand('onboard', { client: 'dsh' }, options);
  assert.equal(updated.pluginDirectory, first.pluginDirectory);
  assert.equal(updated.dshHome, dshHome);
  const native = updated.onboarding.checks.find(c => c.id === 'native_registration');
  assert.equal(native.status, 'manual');
  assert.equal(native.command.env.DSH_HOME, dshHome);
  assert.deepEqual(native.command.args.slice(-1), [first.pluginDirectory]);
  assert.equal(updated.diagnostics.protocol, 'native-plugin');
});

test('Doubao onboarding provides the actual form values without claiming account-side import', async t => {
  const options = await fixture(t);
  const result = await clientCommand('onboard', { client: 'doubao' }, options);
  const registration = result.onboarding.checks.find(c => c.id === 'host_registration');
  assert.equal(registration.status, 'manual');
  assert.deepEqual(registration.launcher, result.launcher);
  const guide = installationGuide(result);
  assert.ok(guide.includes(result.launcher.command));
  assert.ok(guide.includes(result.skillFolder));
  assert.ok(guide.includes('In-session panel · not_tested'));
});

test('Cherry exports a managed Skill without registering MCP or touching private app state', async t => {
  const options = await fixture(t);
  const result = await clientCommand('onboard', { client: 'cherry' }, options);
  assert.equal(result.installed, true);
  assert.equal(result.connector, null);
  assert.equal(result.configFile, null);
  assert.equal(result.skillLink, null);
  assert.equal(result.panel, 'browser-sidebar');
  assert.equal(result.diagnostics.protocol, 'not_applicable');
  assert.equal(result.diagnostics.hostRendering, 'not_tested');
  assert.ok(installationGuide(result).includes(result.skillFolder));
  const { readdir, access } = await import('node:fs/promises');
  await assert.rejects(access(join(options.home, 'Library/Application Support')), {code: 'ENOENT'});
  await assert.rejects(access(join(result.directory, 'mcp.mjs')), {code: 'ENOENT'});
  assert.ok((await readdir(result.skill)).includes('SKILL.md'));
  const registration = result.onboarding.checks.find(c => c.id === 'host_registration');
  assert.equal(registration.status, 'manual');
  assert.equal(result.onboarding.checks.find(c => c.id === 'native_panel').status, 'not_tested');
  const updated = await clientCommand('onboard', {client:'cherry'}, options);
  assert.equal(updated.skillFolder, result.skillFolder);
  assert.equal((await clientCommand('uninstall', {client:'cherry'}, options)).pairingPreserved, true);
  await assert.rejects(access(result.skillFolder), {code: 'ENOENT'});
});

test('a standalone imported Skill finds a Cherry-only installation without a checkout', async t => {
  const options = await fixture(t);
  const installed = await clientCommand('onboard', {client:'cherry'}, options);
  const { cp, mkdir } = await import('node:fs/promises');
  const { execFile } = await import('node:child_process');
  const { promisify } = await import('node:util');
  const loose = join(options.home, 'imported skill', 'scripts');
  await mkdir(loose, {recursive:true});
  await cp(join(packageRoot,'skills/tapilot/scripts/tapilot.mjs'),join(loose,'tapilot.mjs'));
  const {stdout} = await promisify(execFile)(process.execPath,[join(loose,'tapilot.mjs'),'--help'],{
    env:{...process.env,HOME:options.home,USERPROFILE:options.home,TAPILOT_ROOT:'',ANDROID_USE_ROOT:''},timeout:15000,
  });
  assert.ok(stdout.includes('Tapilot CLI'));
  assert.equal(installed.diagnostics.protocol,'not_applicable');
});
