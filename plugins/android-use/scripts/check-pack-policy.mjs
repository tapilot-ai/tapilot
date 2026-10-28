import fs from 'node:fs';

const manifest = JSON.parse(fs.readFileSync(new URL('../package.json', import.meta.url), 'utf8'));
if (!Array.isArray(manifest.bundleDependencies) ||
    Object.keys(manifest.dependencies).some(name => !manifest.bundleDependencies.includes(name))) {
  throw new Error('Build the locked, bundled CLI artifact with pnpm check:cli-package from the repository root. Plain npm pack/publish would lose root-only dependency overrides.');
}
