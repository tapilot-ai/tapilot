import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Build and exercise the artifact as a consumer, outside the source dependency tree.
const root = fileURLToPath(new URL('../', import.meta.url));
const output = path.join(root, 'build/cli-package-validation');
fs.mkdirSync(output, { recursive: true });
const work = fs.mkdtempSync(path.join(output, 'run-'));
const source = path.join(work, 'source');
const consumer = path.join(work, 'consumer');
const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const run = (command, args, cwd) => {
  if (command === 'npm.cmd') {
    args = [path.join(path.dirname(process.execPath), 'node_modules/npm/bin/npm-cli.js'), ...args];
    command = process.execPath;
  }
  return execFileSync(command, args, { cwd, stdio: 'inherit' });
};
fs.cpSync(path.join(root, 'plugins/tapilot'), source, {
  recursive: true, filter: file => !file.split(path.sep).includes('node_modules'),
});
run(npm, ['ci', '--ignore-scripts'], source);
run(process.execPath, ['scripts/patch-midscene-zod.mjs'], source);
run(npm, ['audit', '--omit=dev', '--audit-level=low'], source);
// npm overrides are root-only. Bundle the exact audited tree, including the
// portable Sharp WASM fallback, instead of letting consumers resolve it again.
const manifestPath = path.join(source, 'package.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
manifest.bundleDependencies = Object.keys(manifest.dependencies);
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
run(npm, ['pack'], source);
const artifact = path.join(source, `${manifest.name}-${manifest.version}.tgz`);
fs.mkdirSync(consumer);
fs.writeFileSync(path.join(consumer, 'package.json'), '{"name":"tapilot-install-verification","version":"1.0.0","private":true}\n');
// Also prove that consumers with lifecycle scripts disabled get the patched SDK.
run(npm, ['install', '--ignore-scripts', artifact], consumer);
run(npm, ['audit', '--audit-level=low'], consumer);
const installed = path.join(consumer, 'node_modules/tapilot');
run(process.execPath, ['server/cli.mjs', '--help'], installed);
fs.cpSync(path.join(root, 'plugins/tapilot/test'), path.join(installed, 'test'), { recursive: true });
run(process.execPath, ['--test', '--test-reporter=tap', '--test-timeout=300000', '--test-concurrency=2', ...fs.readdirSync(path.join(installed, 'test')).filter(f => f.endsWith('.test.mjs')).map(f => `test/${f}`)], installed);
// Force the portable path rather than accidentally testing the Mac's native addon.
const img = path.join(installed, 'node_modules/@img');
for (const name of fs.readdirSync(img)) {
  if (name.startsWith('sharp-') && name !== 'sharp-wasm32') fs.rmSync(path.join(img, name), { recursive: true });
}
run(process.execPath, ['--input-type=module', '-e', `
import sharp from 'sharp';
import assert from 'node:assert/strict';
assert.ok(sharp.versions.vips);
const bytes = await sharp({create:{width:8,height:8,channels:4,background:'#ff0000'}}).resize(4,4).png().toBuffer();
const result = await sharp(bytes).metadata();
assert.equal(result.width,4); assert.equal(result.height,4);
console.log('Portable WASM image encode/resize/decode passed');
`], installed);
fs.writeFileSync(path.join(output, 'latest.json'), JSON.stringify({ artifact, consumer, passed: true, published: false }, null, 2) + '\n');
console.log(`Verified local npm artifact: ${artifact}. No registry publication was performed.`);
