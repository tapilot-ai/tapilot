import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { patchMidsceneZod } from '../plugins/android-use/scripts/patch-midscene-zod.mjs';

test('Zod compatibility patches only pinned Midscene imports and is idempotent', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'tapilot-zod-'));
  try {
    fs.writeFileSync(path.join(root, 'package.json'), '{}');
    const dir = path.join(root, 'node_modules/@midscene/core');
    fs.mkdirSync(path.join(dir, 'dist'), {recursive:true});
    const manifest = path.join(dir, 'package.json');
    fs.writeFileSync(manifest, JSON.stringify({name:'@midscene/core',version:'1.15.2',main:'dist/index.js'}));
    const file = path.join(dir, 'dist/index.js');
    fs.writeFileSync(file, 'const z = require("zod"); // zod is retained, only its API entry changes\n');
    assert.equal(patchMidsceneZod(root), 1);
    assert.match(fs.readFileSync(file,'utf8'), /require\("zod\/v3"\)/);
    assert.equal(patchMidsceneZod(root), 0);
    fs.writeFileSync(manifest, JSON.stringify({name:'@midscene/core',version:'2.0.0',main:'dist/index.js'}));
    assert.throws(() => patchMidsceneZod(root), /Review Zod compatibility/);
  } finally { fs.rmSync(root, {recursive:true,force:true}); }
});
