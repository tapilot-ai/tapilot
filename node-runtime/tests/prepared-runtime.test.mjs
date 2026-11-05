import test from 'node:test';
import assert from 'node:assert/strict';
import { PassThrough } from 'node:stream';
import { createInterface } from 'node:readline';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { waitForActivation } from './prepared-runtime.mjs';

test('activation relinquishes stdin to the native RPC reader', async () => {
  const input = new PassThrough();
  const activated = waitForActivation(input, 1000);
  input.write('{"type":"activate"}\n');
  assert.equal(await activated, true);
  const native = createInterface({ input });
  const response = once(native, 'line');
  input.write('{"requestId":"next","ok":true}\n');
  assert.equal(JSON.parse((await response)[0]).requestId, 'next');
  native.close(); input.destroy();
});

test('unused preparation expires or closes without activation', async () => {
  for (const end of [false, true]) {
    const input = new PassThrough();
    const activated = waitForActivation(input, 10);
    if (end) input.end();
    assert.equal(await activated, false);
    input.destroy();
  }
});

test('malformed or unexpected activation cannot run a task', async () => {
  for (const line of ['bad', '{"type":"execute"}', '{"type":"activate","action":"tap"}']) {
    const input = new PassThrough();
    const activated = waitForActivation(input, 1000);
    input.write(line + '\n');
    await assert.rejects(activated);
    input.destroy();
  }
});

test('product config preparation performs no phone RPC and terminates on cancellation', async () => {
  const root = dirname(fileURLToPath(import.meta.url));
  const child = spawn(process.execPath, ['--import', 'data:text/javascript,globalThis.fetch=()=>{throw Error("Unexpected preload fetch")}',
    fileURLToPath(new URL('./runner.mjs', import.meta.url)), 'prepare', '--project-root', root,
    '--result-dir', join(root, 'unused-results'), '--config', join(root, 'android.config.mjs')], { cwd: root, stdio: ['pipe','pipe','pipe'] });
  const exit = once(child, 'exit');
  let output = ''; child.stderr.on('data', b => { output += b; });
  const reader = createInterface({ input: child.stdout });
  try {
    let ready = false;
    for await (const line of reader) {
      output += line + '\n';
      if (line.includes('"type":"runtime.prepared"')) { ready = true; break; }
    }
    assert.equal(ready, true, output);
    assert.doesNotMatch(output, /rpc.request|test.started|Unexpected preload fetch/);
    child.kill('SIGTERM');
    await exit;
  } finally { child.kill('SIGKILL'); reader.close(); }
});

test('activated product config executes once and accepts native RPC replies', async () => {
  const { mkdtemp, writeFile, rm } = await import('node:fs/promises');
  const root = dirname(fileURLToPath(import.meta.url));
  const project = await mkdtemp(join(root, 'prepared-activation-'));
  await writeFile(join(project, 'task.yaml'), 'cases:\n  - name: Activated bridge\n    steps:\n      - home: {}\n');
  const child = spawn(process.execPath, [join(root, 'runner.mjs'), 'prepare', '--project-root', project,
    '--result-dir', join(project, 'results'), '--config', join(root, 'android.config.mjs'), '--project', 'android-accessibility'],
    { cwd: project, stdio: ['pipe', 'pipe', 'pipe'] });
  const exit = once(child, 'exit');
  const reader = createInterface({ input: child.stdout });
  const events = [];
  let activated = false;
  let stderr = ''; child.stderr.on('data', b => { stderr += b; });
  try {
    for await (const line of reader) {
      if (!line.startsWith('MIDSCENE_EVENT ')) continue;
      const event = JSON.parse(line.slice('MIDSCENE_EVENT '.length));
      events.push(event);
      if (event.type === 'runtime.prepared') {
        activated = true;
        child.stdin.write('{"type":"activate"}\n');
      }
      if (event.type === 'rpc.request') {
        assert.equal(activated, true);
        const result = event.method === 'device.getCapabilities'
          ? { protocolVersion: 1, canCapture: true, canPerformGestures: true, canInputText: true } : null;
        child.stdin.write(JSON.stringify({ requestId: event.requestId, ok: true, result }) + '\n');
      }
    }
    assert.equal((await exit)[0], 0, stderr);
    assert.equal(events.filter(e => e.type === 'test.completed').length, 1);
    assert.equal(events.filter(e => e.method === 'device.execute').length, 1);
  } finally { child.kill('SIGKILL'); reader.close(); await rm(project, { recursive: true, force: true }); }
});

test('expired preparation explicitly reports that no test was started', async () => {
  const root = dirname(fileURLToPath(import.meta.url));
  // Accelerate only the production activation deadline; keep imports and stdio real.
  const bootstrap = 'const original=setTimeout;globalThis.setTimeout=(fn,ms,...args)=>original(fn,ms===45000?20:ms,...args)';
  const child = spawn(process.execPath, ['--import', 'data:text/javascript,' + encodeURIComponent(bootstrap),
    join(root, 'runner.mjs'), 'prepare', '--project-root', root, '--result-dir', join(root, 'unused-results'),
    '--config', join(root, 'android.config.mjs')], { cwd: root, stdio: ['pipe', 'pipe', 'pipe'] });
  const exit = once(child, 'exit');
  let stdout = '', stderr = '';
  child.stdout.on('data', b => { stdout += b; });
  child.stderr.on('data', b => { stderr += b; });
  try {
    assert.equal((await exit)[0], 0, stderr);
    const events = stdout.split('\n').filter(line => line.startsWith('MIDSCENE_EVENT '))
      .map(line => JSON.parse(line.slice('MIDSCENE_EVENT '.length)).type);
    assert.deepEqual(events, ['runtime.prepared', 'runtime.expired']);
  } finally { child.kill('SIGKILL'); }
});
