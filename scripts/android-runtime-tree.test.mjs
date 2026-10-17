import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { pruneAndroidPackages, pruneAndroidSourceMaps } from './android-runtime-tree.mjs';
test('removes host binaries and preserves Android, WASM and portable packages', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'android-tree-'));
  try {
    for (const [name, metadata] of Object.entries({
      host: { os: ['darwin'], cpu: ['arm64'] }, linux: { os: ['linux'] },
      android: { os: ['android'], cpu: ['arm64'] }, wasm: { cpu: ['wasm32'] },
      portable: {}, excluded: { os: ['!android'] },
    })) {
      fs.mkdirSync(path.join(root, name));
      fs.writeFileSync(path.join(root, name, 'package.json'), JSON.stringify({ name, version: '1', ...metadata }));
    }
    assert.deepEqual(pruneAndroidPackages(root), ['excluded@1', 'host@1', 'linux@1']);
    assert.deepEqual(fs.readdirSync(root).sort(), ['android', 'portable', 'wasm']);
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});

test('deployment pruning removes only valid JavaScript source maps and never follows links', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'android-maps-'));
  try {
    const deploy = path.join(root, 'deploy'); fs.mkdirSync(deploy);
    const nested = path.join(deploy, 'node_modules', 'nested'); fs.mkdirSync(nested, { recursive: true });
    const map = JSON.stringify({ version: 3, sources: ['index.ts'], mappings: 'AAAA' });
    const outside = path.join(root, 'outside.js.map'); fs.writeFileSync(outside, map);
    fs.symlinkSync(outside, path.join(deploy, 'linked.js.map'));
    for (const name of ['index.js.map', 'index.mjs.map', 'index.cjs.map']) fs.writeFileSync(path.join(nested, name), map);
    for (const [name, content] of Object.entries({
      'index.js': 'export const value = 1;', 'index.d.ts': 'export declare const value: 1;',
      LICENSE: 'license', 'geography.map': map, 'invalid.js.map': 'not json',
      'data.js.map': JSON.stringify({ version: 1, sources: [] }),
    })) fs.writeFileSync(path.join(deploy, name), content);
    assert.deepEqual(pruneAndroidSourceMaps(deploy), { files: 3, bytes: 3 * Buffer.byteLength(map) });
    assert.equal(fs.readFileSync(outside, 'utf8'), map);
    assert.deepEqual(fs.readdirSync(deploy).sort(), ['LICENSE', 'data.js.map', 'geography.map', 'index.d.ts', 'index.js', 'invalid.js.map', 'linked.js.map', 'node_modules']);
    assert.deepEqual(pruneAndroidSourceMaps(deploy), { files: 0, bytes: 0 });
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});
