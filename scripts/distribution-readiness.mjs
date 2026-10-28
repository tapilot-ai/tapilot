import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** Source/build readiness never implies permission to publish bundled binaries. */
export function distributionBlockers(inventories, materials) {
  const blockers = [];
  for (const kind of ['npm', 'native', 'android']) {
    const inventory = inventories[kind];
    if (!Array.isArray(inventory?.packages) || !inventory.packages.length) {
      blockers.push({ kind, reason: 'Missing or empty generated inventory' });
      continue;
    }
    for (const pkg of inventory.packages) {
      if (pkg.reviewRequired !== false) blockers.push({ kind, id: pkg.id, reason: 'Unresolved upstream notice/source review' });
    }
  }
  if (materials?.redistributionApproved !== true) {
    blockers.push({ kind: 'source-materials', reason: 'Corresponding source and rebuild/replacement review is incomplete; see third-party/README.md' });
  }
  return blockers;
}

if (process.argv[1] && fs.realpathSync(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = fileURLToPath(new URL('../', import.meta.url));
  const variant = process.argv[2] ?? 'beta';
  if (!['beta', 'release'].includes(variant)) throw Error('Expected beta or release');
  const read = file => fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : undefined;
  const inventories = Object.fromEntries(['npm', 'native'].map(kind =>
    [kind, read(path.join(root, 'app/src/main/assets/third-party', kind, 'index.json'))]));
  inventories.android = read(path.join(root, `app/build/generated/third-party-assets/${variant}/third-party/android/index.json`));
  const blockers = distributionBlockers(inventories, read(path.join(root, 'build/distribution-materials/status.json')));
  const output = path.join(root, 'build/distribution-readiness.json');
  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.writeFileSync(output, JSON.stringify({ binaryPublicationReady: !blockers.length, variant, blockers }, null, 2) + '\n');
  console.log(JSON.stringify({ binaryPublicationReady: !blockers.length, blockerCount: blockers.length, report: output }));
  if (blockers.length) process.exitCode = 1;
}
