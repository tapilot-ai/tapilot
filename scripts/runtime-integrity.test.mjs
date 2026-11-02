import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { sourceFingerprint, verifyRuntime } from './runtime-integrity.mjs';

test('rejects stale source and archive even when all runtime assets exist', () => {
  const fixture=fs.mkdtempSync(path.join(os.tmpdir(),'tapilot-integrity-'));
  const root=fileURLToPath(new URL('../',import.meta.url));
  try {
    for(const item of ['node-runtime/src','node-runtime/bundle/package.json','node-runtime/bundle/pnpm-lock.yaml', 'node-runtime/bundle/pnpm-workspace.yaml', 'node-runtime/bundle/patches',
      'packages/android-accessibility/src','packages/android-accessibility/package.json','packages/android-accessibility/rslib.config.ts',
      'plugins/tapilot/scripts/patch-midscene-zod.mjs','scripts/bundle-node-runtime.mjs','scripts/runtime-integrity.mjs','scripts/android-runtime-tree.mjs','scripts/third-party-notices.mjs','scripts/install-reviewed-sharp.mjs','third-party/sharp-rebuild.json','third-party/npm-supplements.json','third-party/native-supplements.json','third-party/notices','LICENSE']) {
      fs.mkdirSync(path.dirname(path.join(fixture,item)),{recursive:true});
      fs.cpSync(path.join(root,item),path.join(fixture,item),{recursive:true});
    }
    const assets=path.join(fixture,'app/src/main/assets');fs.mkdirSync(assets,{recursive:true});
    const archive=path.join(assets,'node-runtime-bundle.zip');fs.writeFileSync(archive,'fixture archive');
    fs.writeFileSync(path.join(assets,'node-runtime-manifest.json'),JSON.stringify({sourceFingerprint:sourceFingerprint(fixture),
      bundleSha256:createHash('sha256').update(fs.readFileSync(archive)).digest('hex')}));
    verifyRuntime(fixture);
    const source=path.join(fixture,'node-runtime/src/pi-runner.mjs');
    const original=fs.readFileSync(source);fs.appendFileSync(source,'\n// changed source');
    assert.throws(()=>verifyRuntime(fixture),/sources changed/);
    fs.writeFileSync(source,original);
    const packaging=path.join(fixture,'scripts/android-runtime-tree.mjs');
    const packagingOriginal=fs.readFileSync(packaging);
    fs.appendFileSync(packaging,'\n// changed deployment pruning');
    assert.throws(()=>verifyRuntime(fixture),/sources changed/);
    fs.writeFileSync(packaging,packagingOriginal);
    fs.appendFileSync(archive,'changed archive');
    assert.throws(()=>verifyRuntime(fixture),/archive does not match/);
  } finally {fs.rmSync(fixture,{recursive:true,force:true});}
});
