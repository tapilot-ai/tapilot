import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Audit all lockfiles even if an earlier tree fails; never hide later findings.
const root = fileURLToPath(new URL('../', import.meta.url));
const checks = [
  ['pnpm', ['audit']],
  ['pnpm', ['--dir', 'node-runtime/bundle', 'audit']],
  ['npm', ['audit', '--prefix', 'plugins/android-use']],
  ['npm', ['audit', '--prefix', 'tests/midscene']],
];
for (const [command, args] of checks) {
  console.log(`\nAuditing: ${command} ${args.join(' ')}`);
  const result = spawnSync(command, args, { cwd: root, stdio: 'inherit' });
  if (result.error) console.error(result.error.message);
  if (result.status !== 0) process.exitCode = 1;
}
