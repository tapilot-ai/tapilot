#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const gate = path.join(os.homedir(), '.local/bin/dev-gate');
if (!process.env.LIFEOS_DEV_GATE && fs.existsSync(gate)) {
  const result = spawnSync(gate, ['run', '--label', 'tapilot-check', '--', process.execPath, fileURLToPath(import.meta.url)], {cwd:root,stdio:'inherit'});
  process.exit(result.status ?? 1);
}
function run(command, args) {
  console.log(`\nChecking: ${command} ${args.join(' ')}`);
  const result=spawnSync(command,args,{cwd:root,stdio:'inherit'});
  if(result.error) throw result.error;
  if(result.status!==0) process.exit(result.status ?? 1);
}
run('pnpm',['theme:check']);
run('pnpm',['exec','tsc','--noEmit']);
run('pnpm',['exec','rstest','run','--pool.maxWorkers','2']);
run('pnpm',['--filter','@tapilot/android-accessibility','test','run','--pool.maxWorkers','2']);
// Builds the adapter and runs every Node runtime test against the exact bundled sources.
run('pnpm',['runtime:bundle']);
run(process.execPath,['--test','--test-concurrency=1',...fs.readdirSync(path.join(root,'scripts')).filter(f=>f.endsWith('.test.mjs')).map(f=>`scripts/${f}`)]);
run('pnpm',['runtime:node']);
run('./gradlew',[':app:testDebugUnitTest',':app:assembleBeta','--max-workers=2','--no-parallel','--no-daemon']);
run(process.execPath,['scripts/verify-apk.mjs','beta','--local-check']);
console.log('\nTapilot checks passed: types, legacy UI, adapter, Node agent, scripts, Kotlin and non-debuggable beta APK.');
