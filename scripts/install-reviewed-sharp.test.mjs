import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { verifyReviewedSharp } from './install-reviewed-sharp.mjs';

test('rebuilt Sharp review rejects changed binary bytes, package identity, and missing file hashes', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'sharp-review-'));
  try {
    const manifest = { name: '@img/sharp-wasm32', version: '0.35.5' };
    fs.writeFileSync(path.join(directory, 'package.json'), JSON.stringify(manifest));
    fs.writeFileSync(path.join(directory, 'addon.wasm'), 'verified wasm fixture');
    const review = { ...manifest, files: { 'addon.wasm': createHash('sha256').update('verified wasm fixture').digest('hex') } };
    verifyReviewedSharp(directory, review);
    assert.throws(() => verifyReviewedSharp(directory, { ...review, version: '0.35.4' }), /identity mismatch/);
    assert.throws(() => verifyReviewedSharp(directory, { ...review, files: {} }), /Missing reviewed/);
    fs.writeFileSync(path.join(directory, 'addon.wasm'), 'different binary');
    assert.throws(() => verifyReviewedSharp(directory, review), /checksum mismatch/);
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
  }
});
