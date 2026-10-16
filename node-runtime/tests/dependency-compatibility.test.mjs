import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { spawnSync } from 'node:child_process';
const require = createRequire(import.meta.url);

test('patched YAML parser preserves normal task documents and rejects unsafe pollution', () => {
  const yaml = require('js-yaml');
  const task = yaml.load('cases:\n  - name: 中文任务\n    steps:\n      - home: {}\n');
  assert.equal(task.cases[0].name, '中文任务');
  const data = yaml.load('base: &base\n  __proto__: {polluted: true}\ncopy:\n  <<: *base\n');
  assert.equal(Object.getPrototypeOf(data.copy), Object.prototype);
  assert.equal({}.polluted, undefined);
});

test('updated UUID preserves v4 and rejects an undersized caller buffer', () => {
  const uuid = require('uuid');
  assert.equal(uuid.validate(uuid.v4()), true);
  assert.throws(() => uuid.v5('tapilot', uuid.v5.DNS, new Uint8Array(2)));
});

test('native and Android WASM Sharp decode, resize and re-encode screenshot pixels', () => {
  for (const wasm of [false, true]) {
    const child = spawnSync(process.execPath, ['--input-type=commonjs', '-e', `
      const assert = require('node:assert/strict');
      ${wasm ? `Object.defineProperty(process, 'platform', {value: 'android'});` : ''}
      const sharp = require('sharp');
      (async () => {
        const png = await sharp({create: {width: 40, height: 80, channels: 3, background: '#ff0000'}}).png().toBuffer();
        const jpeg = await sharp(png).resize(20, 40).jpeg().toBuffer();
        const meta = await sharp(jpeg).metadata();
        assert.equal(meta.width, 20); assert.equal(meta.height, 40); assert.equal(meta.format, 'jpeg');
        const {data, info} = await sharp(jpeg).raw().toBuffer({resolveWithObject: true});
        assert.equal(info.channels, 3); assert.ok(data[0] > 240); assert.ok(data[1] < 10);
      })().catch(error => { console.error(error); process.exitCode = 1; });
    `], {encoding: 'utf8', timeout: 15000});
    assert.equal(child.status, 0, `${wasm ? 'WASM' : 'native'}: ${child.stderr}`);
  }
});

test('pixel bbox far edges accept image dimensions without accepting out-of-screen targets', async () => {
  const { dirname, resolve } = await import('node:path');
  const { pathToFileURL } = await import('node:url');
  const lib = dirname(require.resolve('@midscene/core'));
  const modulePath = 'ai-model/shared/model-locate-result/factory';
  const modules = [require(resolve(lib, `${modulePath}.js`)),
    await import(pathToFileURL(resolve(lib, '../es', `${modulePath}.mjs`)).href)];
  for (const { createLocateResultCodec } of modules) {
    const context = { preparedSize: { width: 988, height: 1972 } };
    for (const order of ['xy', 'yx']) {
      const bbox = createLocateResultCodec({ coordinates: { shape: 'bbox', order } });
      const coords = order === 'xy' ? [43, 608, 944, 1972] : [608, 43, 1972, 944];
      const result = bbox.toPixelResult(coords, context);
      assert.deepEqual(result.rect, { left: 43, top: 608, width: 902, height: 1364 });
      assert.ok(result.center[0] < 988 && result.center[1] < 1972);
      assert.deepEqual(bbox.toPixelResult(order === 'xy' ? [0, 0, 988, 1972] : [0, 0, 1972, 988], context).rect,
        { left: 0, top: 0, width: 988, height: 1972 });
      for (const invalid of [[-1, 0, 50, 50], [0, 0, 3000, 3000], [10, 10, 5, 5], [988, 1972, 988, 1972]])
        assert.throws(() => bbox.toPixelResult(invalid, context));
    }
    const point = createLocateResultCodec({ coordinates: { shape: 'point' } });
    assert.throws(() => point.toPixelResult([988, 1972], context));
    const normalized = createLocateResultCodec({ coordinates: { shape: 'bbox', normalizedBy: 1000 } });
    assert.throws(() => normalized.toPixelResult([0, 0, 1001, 1000], context));
    assert.deepEqual(normalized.toPixelResult([0, 0, 1000, 1000], context).rect,
      { left: 0, top: 0, width: 988, height: 1972 });
  }
});
