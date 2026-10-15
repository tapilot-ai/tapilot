import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';

test('RPC timings count success and failure without retaining private payloads', async () => {
  const child = spawn(process.execPath, ['--input-type=module', '--eval', `
    import { StdioNativeBridge, nativeRpcTimings } from ${JSON.stringify(new URL('./stdio-native-bridge.mjs', import.meta.url).href)};
    const bridge = new StdioNativeBridge();
    await bridge.capture();
    try { await bridge.execute({ type: 'input', text: 'PRIVATE_INPUT' }); } catch {}
    bridge.close();
    const snapshot = nativeRpcTimings();
    snapshot['device.capture'].count = 999;
    console.log('TIMINGS ' + JSON.stringify(nativeRpcTimings()));
  `], { stdio: ['pipe', 'pipe', 'pipe'] });
  let timings, diagnostics = '';
  child.stderr.on('data', chunk => { diagnostics += chunk; });
  const lines = createInterface({ input: child.stdout });
  lines.on('line', line => {
    if (line.startsWith('TIMINGS ')) timings = JSON.parse(line.slice(8));
    if (!line.startsWith('MIDSCENE_EVENT ')) return;
    const event = JSON.parse(line.slice('MIDSCENE_EVENT '.length));
    child.stdin.write(JSON.stringify({ requestId: event.requestId,
      ok: event.method === 'device.capture', result: 'PRIVATE_SCREEN', error: { message: 'PRIVATE_ERROR' } }) + '\n');
  });
  const code = await new Promise((resolve, reject) => { child.once('error', reject); child.once('exit', resolve); });
  assert.equal(code, 0, diagnostics);
  assert.equal(timings['device.capture'].count, 1);
  assert.equal(timings['device.capture'].failures, 0);
  assert.equal(timings['device.execute'].count, 1);
  assert.equal(timings['device.execute'].failures, 1);
  assert.ok(timings['device.capture'].totalMs >= 0);
  assert.ok(timings['device.execute'].maxMs >= 0);
  assert.ok(!JSON.stringify(timings).includes('PRIVATE_'));
});
