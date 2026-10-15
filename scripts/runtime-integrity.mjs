import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
export function sourceFingerprint(directory = root) {
  const hash = createHash('sha256');
  function visit(relative) {
    const absolute = path.join(directory, relative);
    if (fs.statSync(absolute).isDirectory()) {
      for (const name of fs.readdirSync(absolute).sort()) visit(path.join(relative, name));
    } else if (!relative.endsWith('.test.mjs')) {
      hash.update(relative).update('\0').update(fs.readFileSync(absolute)).update('\0');
    }
  }
  for (const relative of ['node-runtime/src', 'node-runtime/bundle/package.json',
    'node-runtime/bundle/pnpm-lock.yaml', 'node-runtime/bundle/pnpm-workspace.yaml', 'node-runtime/bundle/patches', 'packages/android-accessibility/src',
    'packages/android-accessibility/package.json', 'packages/android-accessibility/rslib.config.ts',
    'scripts/bundle-node-runtime.mjs', 'scripts/runtime-integrity.mjs', 'scripts/third-party-notices.mjs', 'LICENSE']) visit(relative);
  return hash.digest('hex');
}
export function verifyRuntime(directory = root) {
  const assets = path.join(directory, 'app/src/main/assets');
  const manifest = JSON.parse(fs.readFileSync(path.join(assets, 'node-runtime-manifest.json'), 'utf8'));
  if (manifest.sourceFingerprint !== sourceFingerprint(directory)) {
    throw new Error('Node runtime sources changed. Run pnpm runtime:bundle before building the APK.');
  }
  const hash = createHash('sha256').update(fs.readFileSync(path.join(assets, 'node-runtime-bundle.zip'))).digest('hex');
  if (manifest.bundleSha256 !== hash) throw new Error('Node runtime archive does not match its manifest. Run pnpm runtime:bundle.');
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyRuntime();
  console.log('Node runtime source fingerprint and archive verified');
}
