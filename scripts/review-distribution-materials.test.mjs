import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { verifyMaterialsReview } from './review-distribution-materials.mjs';

test('material approval is invalidated by changed lock, receipt, rebuild, or incomplete review', () => {
  const digest = bytes => createHash('sha256').update(bytes).digest('hex');
  const lockBytes = Buffer.from(JSON.stringify({ materials: [{ sha256: 'fixture' }] }));
  const sharpBytes = Buffer.from('rebuilt binary record');
  const receiptBytes = Buffer.from(JSON.stringify({ materialsVerified: 1, sourcesLockSha256: digest(lockBytes) }));
  const input = { lockBytes, sharpBytes, receiptBytes, review: {
    reviewComplete: true, receiptSha256: digest(receiptBytes), sharpRebuildSha256: digest(sharpBytes),
  } };
  assert.equal(verifyMaterialsReview(input).redistributionApproved, true);
  assert.throws(() => verifyMaterialsReview({ ...input, lockBytes: Buffer.from('{}') }), /lock changed/);
  assert.throws(() => verifyMaterialsReview({ ...input, sharpBytes: Buffer.from('unreviewed') }), /rebuild review changed/);
  assert.throws(() => verifyMaterialsReview({ ...input, receiptBytes: Buffer.from('{}') }), /receipt changed/);
  assert.throws(() => verifyMaterialsReview({ ...input, review: { ...input.review, reviewComplete: false } }), /incomplete/);
});
