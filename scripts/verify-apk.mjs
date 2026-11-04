import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { verifyRuntime, sourceFingerprint } from './runtime-integrity.mjs';
import { distributionBlockers } from './distribution-readiness.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const variant=process.argv[2];
if(!['beta','release'].includes(variant)) throw new Error('Expected beta or release');
const sdk=process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
if(!sdk) throw new Error('Set ANDROID_HOME or ANDROID_SDK_ROOT');
const bin=path.join(sdk,'build-tools/35.0.0');
const apk=path.join(root,`app/build/outputs/apk/${variant}/app-${variant}.apk`);
verifyRuntime();
const entries=execFileSync('unzip',['-Z1',apk],{encoding:'utf8'}).trim().split('\n');
for(const required of ['assets/report-export.js','assets/tapilot-icon.png','assets/node-runtime-bundle.zip',
  'assets/third-party/Tapilot-LICENSE.txt','assets/third-party/npm/index.json',
  'assets/third-party/native/index.json','assets/third-party/android/index.json','lib/arm64-v8a/libnodebin.so']) {
  if(!entries.includes(required)) throw new Error(`APK missing ${required}`);
}
if(entries.some(name=>name==='assets/index.html'||name.startsWith('assets/static/'))) throw new Error('Public APK contains legacy web UI');
const embedded=JSON.parse(execFileSync('unzip',['-p',apk,'assets/node-runtime-manifest.json'],{encoding:'utf8'}));
const embeddedZip=execFileSync('unzip',['-p',apk,'assets/node-runtime-bundle.zip'],{maxBuffer:128*1024*1024});
if(embedded.sourceFingerprint!==sourceFingerprint() || embedded.bundleSha256!==createHash('sha256').update(embeddedZip).digest('hex'))
  throw new Error('APK embedded Node runtime is stale or corrupt');
const badging=execFileSync(path.join(bin,'aapt'),['dump','badging',apk],{encoding:'utf8'});
if(badging.includes('application-debuggable')) throw new Error('Public APK must not be debuggable');
const manifestTree=execFileSync(path.join(bin,'aapt'),['dump','xmltree',apk,'AndroidManifest.xml'],{encoding:'utf8'});
for (const component of ['ShellApplication', 'ShellSetupActivity', 'ShellSetupService', 'ShellHandoffProvider']) {
  if (!manifestTree.includes(`lifeos.md.tapilot.shell.${component}`)) throw new Error(`Public APK missing background-screen component: ${component}`);
}
for (const component of ['PerformanceExperimentReceiver', 'BackgroundDisplayActivity']) {
  if (manifestTree.includes(component)) throw new Error(`Public APK exposes a debug-only component: ${component}`);
}
const handoff=manifestTree.split(/\n\s*E: /).find(node=>node.includes('lifeos.md.tapilot.shell.ShellHandoffProvider'));
if (!handoff?.includes('android.permission.DUMP')) throw new Error('Shell handoff must remain protected by DUMP');

const version=JSON.parse(fs.readFileSync(path.join(root,'package.json'),'utf8')).version;
if(!badging.includes("name='lifeos.md.tapilot'") || !badging.includes(`versionName='${version}'`)) throw new Error('APK package/version mismatch');
const signed=execFileSync(path.join(bin,'apksigner'),['verify','--print-certs',apk],{encoding:'utf8'});
const digest=signed.match(/Signer #1 certificate SHA-256 digest: ([a-f0-9]+)/i)?.[1]?.toLowerCase();
// Compatibility beta intentionally retains the installed certificate. Production has no default.
const expected=variant==='beta' ? '698ed83c55d3874e1b9fd5e6fc3d622ad3a5c01cdc0f5a03964b35d4b562d063'
  : process.env.TAPILOT_SIGNING_CERT_SHA256?.toLowerCase();
const localCheck=variant==='beta' && process.argv.includes('--local-check');
if(!digest || (!localCheck && (!expected || digest!==expected))) throw new Error('Signing certificate mismatch; do not distribute or replace the installed app');
if(localCheck && digest!==expected) console.log('Local verification certificate only; this APK is not approved for distribution.');
if (!localCheck) {
  // Review the inventories actually inside this APK, not a stale build directory.
  const inventories = Object.fromEntries(['npm','native','android'].map(kind => [kind,
    JSON.parse(execFileSync('unzip',['-p',apk,`assets/third-party/${kind}/index.json`],{encoding:'utf8'}))]));
  const materialsPath = path.join(root,'build/distribution-materials/status.json');
  const materials = fs.existsSync(materialsPath) ? JSON.parse(fs.readFileSync(materialsPath,'utf8')) : undefined;
  const blockers = distributionBlockers(inventories, materials);
  if (blockers.length) throw new Error(`Binary redistribution is blocked by ${blockers.length} unresolved reviews. Run pnpm check:distribution and see third-party/README.md; signing alone is not approval.`);
}
console.log(`Verified ${variant} ${version}: correct package, non-debuggable, runtime current, certificate ${digest}`);
