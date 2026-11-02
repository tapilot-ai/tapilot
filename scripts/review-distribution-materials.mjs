import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const digest = bytes => createHash('sha256').update(bytes).digest('hex');

export function verifyMaterialsReview({ review, receiptBytes, lockBytes, sharpBytes }) {
  if (review.reviewComplete !== true) throw new Error('Distribution material review is incomplete');
  if (digest(receiptBytes) !== review.receiptSha256) throw new Error('Source verification receipt changed');
  const receipt = JSON.parse(receiptBytes);
  if (receipt.sourcesLockSha256 !== digest(lockBytes)) throw new Error('Source lock changed after verification');
  if (review.sharpRebuildSha256 !== digest(sharpBytes)) throw new Error('Sharp rebuild review changed');
  if (receipt.materialsVerified !== JSON.parse(lockBytes).materials.length) throw new Error('Incomplete source verification receipt');
  return { redistributionApproved: true, materialsVerified: receipt.materialsVerified,
    sourceVerificationRun: receipt.run, sourceVerificationCommit: receipt.commit,
    sourcesLockSha256: receipt.sourcesLockSha256, receiptSha256: review.receiptSha256,
    note: 'Source and notice review only. APK byte integrity, signing and device acceptance are separate.' };
}

if (process.argv[1] && fs.realpathSync(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const read = name => fs.readFileSync(path.join(root, 'third-party', name));
  const status = verifyMaterialsReview({ review: JSON.parse(read('distribution-review.json')),
    receiptBytes: read('materials-verification.json'), lockBytes: read('sources.lock.json'),
    sharpBytes: read('sharp-rebuild.json') });
  const output = path.join(root, 'build/distribution-materials/status.json');
  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.writeFileSync(output, JSON.stringify(status, null, 2) + '\n');
  console.log('Verified source-review receipt and exact lock/rebuild bindings. APK verification remains required.');
}
