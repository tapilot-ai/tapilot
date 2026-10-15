import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const output = path.join(root, 'build/distribution-materials');
const lock = JSON.parse(fs.readFileSync(path.join(root, 'third-party/sources.lock.json'), 'utf8'));
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
fs.mkdirSync(output, { recursive: true });
for (const material of lock.materials) {
  if (!material.path.startsWith('.cache/distribution-sources/') && !material.path.startsWith('third-party/notices/')) {
    throw new Error(`Unexpected material path: ${material.path}`);
  }
  const local = path.resolve(root, material.path);
  if (!local.startsWith(root) || !/^[a-f0-9]{64}$/.test(material.sha256)) throw new Error('Invalid material lock');
  if (!fs.existsSync(local)) {
    const response = await fetch(material.url, { signal: AbortSignal.timeout(120_000) });
    if (!response.ok) throw new Error(`Download failed: ${material.url} (${response.status})`);
    const bytes = Buffer.from(await response.arrayBuffer());
    if (digest(bytes) !== material.sha256) throw new Error(`Downloaded checksum mismatch: ${material.path}`);
    fs.mkdirSync(path.dirname(local), { recursive: true });
    fs.writeFileSync(local, bytes);
  }
  if (digest(fs.readFileSync(local)) !== material.sha256) throw new Error(`Cached checksum mismatch: ${material.path}`);
  const target = path.join(output, material.path.replace(/^\.cache\/distribution-sources\//, 'sources/'));
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.copyFileSync(local, target);
}
fs.cpSync(path.join(root, 'third-party'), path.join(output, 'third-party'), { recursive: true });
for (const name of ['LICENSE', 'CREDITS.md', 'PRIVACY.md', 'PRIVACY.zh.md']) fs.copyFileSync(path.join(root, name), path.join(output, name));
fs.writeFileSync(path.join(output, 'status.json'), JSON.stringify({
  materialsVerified: lock.materials.length,
  redistributionApproved: false,
  remaining: 'See third-party/README.md; collecting source archives does not resolve all source/rebuild and notice requirements.',
}, null, 2) + '\n');
console.log(`Verified and assembled ${lock.materials.length} pinned materials at ${output}; redistribution review remains incomplete.`);
