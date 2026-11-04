import { execFileSync } from 'node:child_process';
import { openSync, closeSync } from 'node:fs';

const serial = process.env.TAPILOT_E2E_DEVICE_ID;
const packageName = process.env.TAPILOT_E2E_PACKAGE;
if (serial !== 'emulator-5554' || packageName !== 'lifeos.md.tapilot.e2everify') {
  throw new Error('Native CI reports require the disposable verification package');
}
const runAs = ['-s', serial, 'exec-out', 'run-as', packageName];
const paths = execFileSync('adb', [...runAs, 'find', 'files/node-runs', '-type', 'd', '-name', 'report'],
  { encoding: 'utf8', timeout: 15_000 }).trim().split(/\r?\n/).filter(Boolean);
// Explicit UUID-based report-directory allowlist. No shell interpolation and no
// whole-project archive: sibling environment files and account stores stay private.
if (paths.some(path => !/^files\/node-runs\/[0-9a-f-]{36}\/midscene_run\/report$/.test(path))) {
  throw new Error('Unexpected native report directory; refusing to archive');
}
if (paths.length) {
  const output = openSync('build/apk-midscene/native-reports.tar', 'w', 0o600);
  try {
    execFileSync('adb', [...runAs, 'tar', '-cf', '-', ...paths], {
      stdio: ['ignore', output, 'pipe'], timeout: 60_000,
    });
  } finally { closeSync(output); }
}
console.log(`Captured ${paths.length} native report directories; no credential directories included`);
