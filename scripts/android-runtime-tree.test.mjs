import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { pruneAndroidPackages } from './android-runtime-tree.mjs';
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
