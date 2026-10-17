import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const cwd = dirname(fileURLToPath(import.meta.url));

for (const flavor of ['es', 'lib']) {
  test(`${flavor}: native config skips tsx while TypeScript configs still load`, async () => {
    const root = await mkdtemp(join(cwd, 'lazy-config-'));
    try {
      for (const format of ['native', 'ts-esm', 'ts-cjs']) {
        const extension = format === 'native' ? 'mjs' : 'ts';
        const file = join(root, `${format}.${extension}`);
        await writeFile(file, format === 'ts-cjs'
          ? 'const config: { nodes: unknown[] } = { nodes: [] }; export = config;'
          : `export default ${extension === 'ts' ? '({ nodes: [] } satisfies { nodes: unknown[] })' : '{ nodes: [] }'};`);
        const code = `import {createRequire} from 'node:module';
const require = createRequire(import.meta.url);
const api = ${flavor === 'lib' ? "require('@midscene/test/config')" : "await import('@midscene/test/config')"};
if (Object.keys(require.cache).some(p=>p.includes('/node_modules/tsx/'))) throw Error('Eager tsx initialization');
await api.loadTestProject(${JSON.stringify(file)});
if (${JSON.stringify(extension)} === 'mjs' && Object.keys(require.cache).some(p=>p.includes('/node_modules/tsx/'))) throw Error('Native config loaded tsx');
console.log('config-loaded');`;
        const result = spawnSync(process.execPath, ['--input-type=module', '-e', code], {cwd, encoding: 'utf8', timeout: 30000});
        assert.equal(result.status, 0, result.stderr);
        assert.match(result.stdout, /config-loaded/);
      }
    } finally { await rm(root, {recursive: true, force: true}); }
  });
}
