#!/usr/bin/env node
import { existsSync, readFileSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { homedir } from 'node:os';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { spawn } from 'node:child_process';
const root = process.env.TAPILOT_ROOT || process.env.ANDROID_USE_ROOT;
const entry = root ? resolve(root, 'server/cli.mjs')
  : fileURLToPath(new URL('../../../server/cli.mjs', import.meta.url));
if (existsSync(entry)) {
  await import(pathToFileURL(entry).href);
} else {
  let runtime;
  // CLI operations are host-independent. Finding an installed runtime here does
  // not select a panel adapter or authorize installing into that client.
  if (!root) for (const client of ['skill', 'doubao', 'workbuddy', 'vscode', 'dsh', 'cherry', 'standard', 'cursor']) {
    try {
      const record = JSON.parse(readFileSync(join(homedir(), '.local/share/tapilot/clients', client, 'installation.json'), 'utf8'));
      if (record.runtime?.node && existsSync(record.runtime.node) && existsSync(join(record.root, 'server/cli.mjs'))) { runtime = record.runtime; break; }
    } catch {}
  }
  if (runtime) {
    const child = spawn(runtime.node, [join(runtime.root, 'server/cli.mjs'), ...process.argv.slice(2)], { stdio: 'inherit' });
    for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => child.kill(signal));
    child.on('error', error => { process.stderr.write(error.message + '\n'); process.exitCode = 1; });
    child.on('exit', code => { process.exitCode = code ?? 1; });
  } else {
    process.stderr.write('Tapilot runtime is missing. Follow references/client-setup.md to install from a verified package, then run install --client NAME. A standalone Skill does not contain the runtime.\n');
    process.exitCode = 1;
  }
}
