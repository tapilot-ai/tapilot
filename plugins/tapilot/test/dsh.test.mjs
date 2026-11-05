import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, mkdtemp, rm, readlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import vm from 'node:vm';
import { registerDshPanel } from '../integrations/dsh/index.mjs';
import { installClient, uninstallClient } from '../server/clients/install.mjs';

test('DSH registers only authenticated owner routes and preserves the runtime panel allowlist', async () => {
  const routes = new Map(), calls = [];
  const ctx = { connection: { fetch: { register(route) { routes.set(route.path, route); } } } };
  registerDshPanel(ctx, async (path, params) => { calls.push({ path, params }); return { content: [], structuredContent: { state: 'manual' } }; });
  assert.deepEqual([...routes.keys()], ['/api/tapilot/panel', '/api/tapilot/call']);
  assert.ok([...routes.values()].every(route => route.requestBody === 'buffered'), 'GET must not be bridged as a streaming request body');
  assert.throws(() => registerDshPanel({ webServer: { register() { assert.fail('No unauthenticated fallback'); } } }), /authenticated/);
  const call = routes.get('/api/tapilot/call').fetch;
  const request = body => new Request('http://localhost/api/tapilot/call', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Tapilot-Panel': '1' }, body: JSON.stringify(body) });
  assert.equal((await call(new Request('http://localhost/api/tapilot/call', { method: 'POST', body: '{}' }))).status, 403);
  assert.equal((await call(request({ name: 'tapilot_midscene', arguments: { action: 'home' } }))).status, 400);
  assert.equal(calls.length, 0);
  const response = await call(request({ name: 'tapilot_preview_frame', arguments: { viewer_id: 'dsh' } }));
  assert.equal((await response.json()).structuredContent.state, 'manual');
  assert.equal(calls[0].path, '/panel');
  await call(request({ name: 'tapilot_preview_pair', arguments: { action: 'pair' } }));
  assert.equal(calls[1].params.name, 'tapilot_preview_pair', 'pairing is not translated twice before the runtime validates it');
  const panel = await routes.get('/api/tapilot/panel').fetch(new Request('http://localhost/api/tapilot/panel'));
  assert.match(await panel.text(), /__TAPILOT_DSH__=true/);
  assert.match(panel.headers.get('Content-Security-Policy'), /frame-ancestors 'self'/);
});

test('DSH client registers a native sidebar entry and embeds the authenticated panel', async () => {
  let plugin, descriptor;
  const React = { createElement: (type, props) => ({ type, props }) };
  const window = { __ModuleLoader__: { load(value) { descriptor = value; plugin = value.factory(name => { assert.equal(name, 'react'); return React; }); } } };
  vm.runInNewContext(await readFile(new URL('../integrations/dsh/client.js', import.meta.url), 'utf8'), { window });
  assert.equal(descriptor.id, '@tapilot/dsh');
  const entries = [], tabs = [], opened = [];
  plugin.apply({ effect(run) { run(); }, sidebarRightTabs: { register(tab) { tabs.push(tab); } }, sidebarRight: { openTab(kind) { opened.push(kind); } }, slots: { inject(name, run) { run(); }, register(options, render) { entries.push({ options, render }); } } });
  assert.ok(entries.some(entry => entry.options.name === 'sidebar.panellist'));
  assert.equal(tabs[0].kind, 'tapilot');
  const body = entries.find(entry => entry.options.name === 'sidebar.right.pane.tab');
  assert.equal(body.options.key, tabs[0].id);
  assert.equal(body.render().props.src, '/api/tapilot/panel');
  const action = entries.find(entry => entry.options.name === 'conversation.session.header.actions');
  action.render().props.onClick();
  assert.deepEqual(opened, ['tapilot'], 'open the native session sidebar without navigating away from the conversation');
  const panel = entries.find(entry => entry.options.name === 'main');
  assert.equal(panel.options.key, 'tapilot');
  assert.equal(panel.render().type, 'iframe');
  assert.equal(panel.render().props.src, '/api/tapilot/panel');
});

test('DSH install exports its skill to the canonical root and reports pending native registration', async t => {
  const home = await mkdtemp(join(tmpdir(), 'tapilot-dsh-install-'));
  t.after(() => rm(home, { recursive: true, force: true }));
  const options = { home, skillsRoot: join(home, 'canonical'), dshHome: join(home, 'custom harness') };
  const installed = await installClient('dsh', options);
  assert.equal(installed.nativeRegistration, 'pending');
  assert.equal(installed.launcher, null);
  assert.equal(await readlink(installed.skillLink), installed.skill);
  assert.equal(installed.skillLink, join(options.dshHome, 'skills/tapilot'));
  const text = await readFile(join(installed.skill, 'SKILL.md'), 'utf8');
  assert.doesNotMatch(text, /call `tapilot_preview_open`/);
  assert.match(text, /DSH Web conversation header/);
  assert.equal((await uninstallClient('dsh', { home, skillsRoot: options.skillsRoot })).removed, true);
});
