import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

export function verifyReviewedSharp(directory, review) {
  const manifest = JSON.parse(fs.readFileSync(path.join(directory, 'package.json'), 'utf8'));
  if (manifest.name !== review.name || manifest.version !== review.version) {
    throw new Error('Reviewed Sharp package identity mismatch');
  }
  if (!Object.keys(review.files ?? {}).length) throw new Error('Missing reviewed Sharp file hashes');
  for (const [name, expected] of Object.entries(review.files)) {
    if (path.isAbsolute(name) || name.split('/').includes('..') || !/^[a-f0-9]{64}$/.test(expected)) {
      throw new Error('Invalid reviewed Sharp file record');
    }
    if (sha256(fs.readFileSync(path.join(directory, name))) !== expected) {
      throw new Error(`Reviewed Sharp checksum mismatch: ${name}`);
    }
  }
}

export async function installReviewedSharp(modules) {
  const review = JSON.parse(fs.readFileSync(path.join(root, 'third-party/sharp-rebuild.json'), 'utf8'));
  const cache = path.join(root, '.cache/distribution-sources/rebuilt', review.archive.name);
  if (!fs.existsSync(cache)) {
    const response = await fetch(review.archive.url, { signal: AbortSignal.timeout(120_000) });
    if (!response.ok) throw new Error(`Reviewed Sharp download failed (${response.status}); the release draft requires a verified local cache.`);
    const bytes = Buffer.from(await response.arrayBuffer());
    if (sha256(bytes) !== review.archive.sha256) throw new Error('Reviewed Sharp archive checksum mismatch');
    fs.mkdirSync(path.dirname(cache), { recursive: true });
    fs.writeFileSync(cache, bytes);
  }
  if (sha256(fs.readFileSync(cache)) !== review.archive.sha256) throw new Error('Cached Sharp archive checksum mismatch');
  const destination = path.join(modules, '@img/sharp-wasm32');
  const temporary = fs.mkdtempSync(path.join(path.dirname(destination), '.reviewed-sharp-'));
  try {
    execFileSync('tar', ['-xzf', cache, '-C', temporary]);
    verifyReviewedSharp(temporary, review);
    fs.rmSync(destination, { recursive: true, force: true });
    fs.renameSync(temporary, destination);
  } finally {
    fs.rmSync(temporary, { recursive: true, force: true });
  }
  console.log(`Installed checksum-verified rebuilt ${review.name}@${review.version}`);
}
