import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const script = readFileSync(new URL('../public/report-export.js', import.meta.url), 'utf8');
function harness({ failAppend = false } = {}) {
  const messages = [];
  let normalClicks = 0;
  let done;
  const completed = new Promise(resolve => { done = resolve; });
  class Anchor {
    href = 'blob:https://midscene-report.local/export';
    download = 'midscene_replay.webm';
    click() { normalClicks++; }
  }
  const host = {
    onmessage: undefined,
    postMessage(raw) {
      const request = JSON.parse(raw);
      messages.push(request);
      queueMicrotask(() => {
        host.onmessage({ data: JSON.stringify({
          id: request.id,
          ...(failAppend && request.method === 'append' ? { error: 'disk full' } : {}),
        }) });
        if (request.method === 'finish' || request.method === 'abort') done();
      });
    },
  };
  const context = {
    window: { TapilotVideo: host }, HTMLAnchorElement: Anchor,
    document: { addEventListener() {} }, Element: class {},
    fetch: async () => ({ blob: async () => new Blob([new Uint8Array(150000)], { type: 'video/webm' }) }),
    Uint8Array, String, Error, JSON, Map, setTimeout, clearTimeout, btoa,
  };
  runInNewContext(script, context);
  return { Anchor, messages, completed, normalClicks: () => normalClicks };
}

test('detached video anchor streams bounded chunks and waits for native acknowledgements', async () => {
  const h = harness();
  new h.Anchor().click();
  await h.completed;
  assert.equal(h.normalClicks(), 0);
  assert.deepEqual(h.messages.map(x => x.method), ['begin', 'append', 'append', 'append', 'finish']);
  assert.equal(h.messages[0].size, 150000);
  assert.equal(h.messages.filter(x => x.method === 'append').reduce(
    (total, x) => total + Buffer.from(x.data, 'base64').length, 0), 150000);
});

test('native write failures abort without publishing a partial video', async () => {
  const h = harness({ failAppend: true });
  new h.Anchor().click();
  await h.completed;
  assert.deepEqual(h.messages.map(x => x.method), ['begin', 'append', 'abort']);
  assert.match(h.messages.at(-1).error, /disk full/);
});

test('HTML downloads keep their original browser behavior', () => {
  const h = harness();
  const anchor = new h.Anchor();
  anchor.download = 'report.html';
  anchor.click();
  assert.equal(h.normalClicks(), 1);
  assert.equal(h.messages.length, 0);
});
