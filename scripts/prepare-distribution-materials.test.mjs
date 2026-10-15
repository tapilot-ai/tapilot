import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';

test('material collection checks bytes and never marks a collected bundle redistribution-ready', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'distribution-materials-'));
  try {
    fs.mkdirSync(path.join(root, 'scripts'));
    fs.mkdirSync(path.join(root, 'third-party/notices'), { recursive: true });
    fs.copyFileSync(new URL('./prepare-distribution-materials.mjs', import.meta.url), path.join(root, 'scripts/prepare-distribution-materials.mjs'));
    for (const name of ['LICENSE', 'CREDITS.md', 'PRIVACY.md', 'PRIVACY.zh.md']) fs.writeFileSync(path.join(root, name), 'fixture');
    const material = path.join(root, 'third-party/notices/fixture.txt');
    fs.writeFileSync(material, 'original notice');
    fs.writeFileSync(path.join(root, 'third-party/sources.lock.json'), JSON.stringify({ materials: [{
      path: 'third-party/notices/fixture.txt', sha256: createHash('sha256').update('original notice').digest('hex'),
    }] }));
    const run = () => spawnSync(process.execPath, [path.join(root, 'scripts/prepare-distribution-materials.mjs')], { encoding: 'utf8' });
    assert.equal(run().status, 0);
    assert.equal(JSON.parse(fs.readFileSync(path.join(root, 'build/distribution-materials/status.json'))).redistributionApproved, false);
    fs.writeFileSync(material, 'tampered notice');
    const failure = run();
    assert.notEqual(failure.status, 0);
    assert.match(failure.stderr, /checksum mismatch/);
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});
