#!/usr/bin/env node

import { sourceFingerprint } from './runtime-integrity.mjs';
import { pruneAndroidPackages, pruneAndroidSourceMaps } from './android-runtime-tree.mjs';
import { execFileSync, spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { createInterface } from 'node:readline';
import { fileURLToPath, pathToFileURL } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const assistantRoot = path.resolve(scriptDirectory, '..');
const sourceRoot = path.join(assistantRoot, 'node-runtime');
const bundleRoot = path.join(sourceRoot, 'bundle');
const stagingRoot = path.join(assistantRoot, 'build/node-runtime-bundle');
const deploymentRoot = path.join(assistantRoot, 'build/node-runtime-android');
const assetRoot = path.join(assistantRoot, 'app/src/main/assets');
const bundleAsset = path.join(assetRoot, 'node-runtime-bundle.zip');
const manifestAsset = path.join(assetRoot, 'node-runtime-manifest.json');
const runtimeFiles = [
  'runner.mjs',
  'prepared-runtime.mjs',
  'pi-runner.mjs',
  'pi-conversation.mjs',
  'conversation-policy.mjs',
  'conversation-tools.mjs',
  'runtime-errors.mjs',
  'pi-context.mjs',
  'run-result.mjs',
  'phone-verification.mjs',
  'stdio-native-bridge.mjs',
  'siwc-fetch.mjs',
  'settled-capture.mjs',
  'android.config.mjs',
  'assistant-context.mjs',
  'action-progress.mjs',
  'execution-policy.mjs',
];
const sourceManifest = JSON.parse(
  fs.readFileSync(
    path.join(sourceRoot, 'src/manifest.json'),
    'utf8',
  ),
);

function run(command, args, cwd) {
  process.stdout.write(`$ ${command} ${args.join(' ')}\n`);
  execFileSync(command, args, { cwd, stdio: 'inherit' });
}

function verifySharpRuntimeVersions() {
  const sharpVersion = JSON.parse(
    fs.readFileSync(
      path.join(stagingRoot, 'node_modules/sharp/package.json'),
      'utf8',
    ),
  ).version;
  const wasmVersion = JSON.parse(
    fs.readFileSync(
      path.join(stagingRoot, 'node_modules/@img/sharp-wasm32/package.json'),
      'utf8',
    ),
  ).version;
  if (sharpVersion !== wasmVersion) {
    throw new Error(
      `Sharp runtime mismatch: sharp@${sharpVersion} cannot use @img/sharp-wasm32@${wasmVersion}`,
    );
  }
}

async function runAndroidBridgeSmoke() {
  const projectRoot = path.join(stagingRoot, 'smoke-android');
  const child = spawn(
    process.execPath,
    [
      'runner.mjs',
      'test',
      '--project-root',
      projectRoot,
      '--result-dir',
      path.join(stagingRoot, 'smoke-android-results'),
      '--config',
      path.join(stagingRoot, 'android.config.mjs'),
      '--project',
      'android-accessibility',
    ],
    { cwd: stagingRoot, stdio: ['pipe', 'pipe', 'inherit'] },
  );
  const lines = createInterface({ input: child.stdout });
  lines.on('line', (line) => {
    process.stdout.write(`${line}\n`);
    if (!line.startsWith('MIDSCENE_EVENT ')) return;
    const event = JSON.parse(line.slice('MIDSCENE_EVENT '.length));
    if (event.type !== 'rpc.request') return;
    const result =
      event.method === 'device.getCapabilities'
        ? {
            protocolVersion: 1,
            canCapture: true,
            canPerformGestures: true,
            canInputText: true,
          }
        : null;
    child.stdin.write(
      `${JSON.stringify({ requestId: event.requestId, ok: true, result })}\n`,
    );
  });
  const exitCode = await new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('exit', resolve);
  });
  if (exitCode !== 0) {
    throw new Error(`Android stdio bridge smoke test exited with ${exitCode}`);
  }
}

function removeLinks(directory) {
  if (!fs.existsSync(directory)) return;
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const absolutePath = path.join(directory, entry.name);
    if (entry.isSymbolicLink()) fs.rmSync(absolutePath);
    else if (entry.isDirectory()) removeLinks(absolutePath);
  }
}

function findLinks(directory, result = []) {
  if (!fs.existsSync(directory)) return result;
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const absolutePath = path.join(directory, entry.name);
    if (entry.isSymbolicLink()) result.push(absolutePath);
    else if (entry.isDirectory()) findLinks(absolutePath, result);
  }
  return result;
}

function updateHashWithTree(hash, directory, relativeRoot = directory) {
  const entries = fs
    .readdirSync(directory, { withFileTypes: true })
    .sort((left, right) => left.name.localeCompare(right.name));
  for (const entry of entries) {
    const absolutePath = path.join(directory, entry.name);
    const relativePath = path.relative(relativeRoot, absolutePath);
    hash.update(relativePath).update('\0');
    if (entry.isDirectory()) {
      updateHashWithTree(hash, absolutePath, relativeRoot);
    } else if (entry.isFile()) {
      hash.update(fs.readFileSync(absolutePath));
    }
  }
  return hash;
}

fs.rmSync(stagingRoot, { recursive: true, force: true });
fs.mkdirSync(stagingRoot, { recursive: true });
for (const file of ['package.json', 'pnpm-lock.yaml']) {
  fs.copyFileSync(path.join(bundleRoot, file), path.join(stagingRoot, file));
}
fs.copyFileSync(path.join(bundleRoot, 'pnpm-workspace.yaml'), path.join(stagingRoot, 'pnpm-workspace.yaml'));
fs.cpSync(path.join(bundleRoot, 'patches'), path.join(stagingRoot, 'patches'), { recursive: true });
run('pnpm', ['--filter', '@tapilot/android-accessibility', 'build'], assistantRoot);
run(
  'pnpm',
  [
    'install',
    '--frozen-lockfile',
    '--config.node-linker=hoisted',
    '--ignore-scripts',
  ],
  stagingRoot,
);
verifySharpRuntimeVersions();

const workspacePackages = ['android-accessibility'];
for (const file of runtimeFiles) {
  fs.copyFileSync(
    path.join(sourceRoot, 'src', file),
    path.join(stagingRoot, file),
  );
}
// Pi's public entry points otherwise load hundreds of small modules on every
// Android process start. Bundle the unchanged agent logic before testing and
// fingerprinting it; credentials and model requests still belong to native RPC.
const { build } = await import(pathToFileURL(path.join(stagingRoot, 'node_modules/esbuild/lib/main.js')));
const piBundle = await build({
  absWorkingDir: stagingRoot,
  entryPoints: ['pi-conversation.mjs'],
  outfile: 'pi-conversation.bundle.mjs',
  bundle: true,
  platform: 'node',
  format: 'esm',
  target: 'node20',
  write: false,
  metafile: true,
});
// This entry uses the supplied native stream function, not provider SDKs.
// Fail explicitly if a future dependency introduces unresolved runtime imports.
if (piBundle.outputFiles.length !== 1 || Object.values(piBundle.metafile.outputs).some(output => output.imports.length)) {
  throw new Error('Pi conversation bundle must be a single self-contained module');
}
fs.writeFileSync(path.join(stagingRoot, 'pi-conversation.mjs'), piBundle.outputFiles[0].contents);
fs.cpSync(path.join(sourceRoot, 'smoke'), path.join(stagingRoot, 'smoke'), {
  recursive: true,
});
fs.cpSync(
  path.join(sourceRoot, 'smoke-android'),
  path.join(stagingRoot, 'smoke-android'),
  { recursive: true },
);
removeLinks(path.join(stagingRoot, 'node_modules'));
const remainingLinks = findLinks(stagingRoot);
if (remainingLinks.length > 0) {
  throw new Error(
    `Node bundle still contains links: ${remainingLinks.slice(0, 3).join(', ')}`,
  );
}

const fingerprintHash = createHash('sha256').update(
  fs.readFileSync(path.join(stagingRoot, 'pnpm-lock.yaml')),
);
for (const file of runtimeFiles) {
  fingerprintHash.update(fs.readFileSync(path.join(stagingRoot, file)));
}
for (const name of workspacePackages) {
  updateHashWithTree(
    fingerprintHash,
    path.join(stagingRoot, 'node_modules/@tapilot', name),
  );
}
// The phone installs by runtimeVersion, so packaging rules and dependency patches
// must invalidate it too, even when the executable entry files did not change.
const sources = sourceFingerprint();
const fingerprint = fingerprintHash.update(sources).digest('hex').slice(0, 12);
const manifest = {
  ...sourceManifest,
  sourceFingerprint: sources,
  runtimeVersion: `${sourceManifest.runtimeVersion}-${fingerprint}`,
};
fs.writeFileSync(
  path.join(stagingRoot, 'manifest.json'),
  `${JSON.stringify(manifest, null, 2)}\n`,
);

fs.copyFileSync(path.join(assistantRoot, 'tests/fixtures/conversation.json'), path.join(stagingRoot, 'conversation-fixtures.json'));
const runtimeTests = fs.readdirSync(path.join(sourceRoot, 'tests')).filter(file => file.endsWith('.test.mjs')).sort();
for (const file of runtimeTests) fs.copyFileSync(path.join(sourceRoot, 'tests', file), path.join(stagingRoot, file));
run(process.execPath, ['--test', '--test-concurrency=1', '--test-timeout=10000', ...runtimeTests], stagingRoot);
run(process.execPath, ['runner.mjs', 'doctor'], stagingRoot);
run(
  process.execPath,
  [
    'runner.mjs',
    'test',
    '--project-root',
    path.join(stagingRoot, 'smoke'),
    '--result-dir',
    path.join(stagingRoot, 'smoke-results'),
    '--config',
    'midscene.config.ts',
    '--project',
    'android-local',
  ],
  stagingRoot,
);
await runAndroidBridgeSmoke();
fs.rmSync(deploymentRoot, { recursive: true, force: true });
fs.mkdirSync(deploymentRoot, { recursive: true });
for (const file of [...runtimeFiles, 'manifest.json', 'node_modules']) {
  fs.cpSync(path.join(stagingRoot, file), path.join(deploymentRoot, file), { recursive: true });
}
console.log('Removed host-only packages:', pruneAndroidPackages(path.join(deploymentRoot, 'node_modules')));
console.log('Removed optional JavaScript source maps:', pruneAndroidSourceMaps(path.join(deploymentRoot, 'node_modules')));
run(process.execPath, [path.join(assistantRoot, 'scripts/third-party-notices.mjs'), 'npm',
  path.join(deploymentRoot, 'node_modules'), path.join(assetRoot, 'third-party/npm')]);
fs.copyFileSync(path.join(assistantRoot, 'LICENSE'), path.join(assetRoot, 'third-party/Tapilot-LICENSE.txt'));
const supplementalNotices = path.join(assetRoot, 'third-party/supplemental');
fs.rmSync(supplementalNotices, { recursive: true, force: true });
fs.cpSync(path.join(assistantRoot, 'third-party/notices'), supplementalNotices, { recursive: true });
fs.mkdirSync(assetRoot, { recursive: true });
fs.rmSync(bundleAsset, { force: true });
run(
  'zip',
  [
    '-q',
    '-r',
    '-X',
    bundleAsset,
    ...runtimeFiles,
    'manifest.json',
    'node_modules',
  ],
  deploymentRoot,
);
fs.writeFileSync(manifestAsset, JSON.stringify({ ...manifest, bundleSha256: createHash('sha256').update(fs.readFileSync(bundleAsset)).digest('hex') }, null, 2) + '\n');
process.stdout.write(
  `Node runtime bundle: ${bundleAsset} (${(fs.statSync(bundleAsset).size / 1024 / 1024).toFixed(1)} MB)\n`,
);
