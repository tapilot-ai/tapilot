import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

// Reviewed Midscene 1.15, 1.17.4 and 1.17.6 builds use Zod 3 schema internals. The patched Zod 4 distribution
// retains that API at zod/v3; changing the default export to Zod 4 silently
// removes actions. Keep the supported compatibility entrypoint explicit.
export function patchMidsceneZod(root) {
  const require = createRequire(path.resolve(root, fs.existsSync(path.join(root, 'packages/android-accessibility/package.json')) ? 'packages/android-accessibility/package.json' : 'package.json'));
  let patched = 0;
  for (const name of ['core', 'shared', 'test', 'android']) {
    let manifest;
    try {
      let directory = path.dirname(require.resolve(`@midscene/${name}`));
      while (!fs.existsSync(path.join(directory, 'package.json'))) {
        const parent = path.dirname(directory);
        if (parent === directory) throw new Error(`Missing package manifest: ${name}`);
        directory = parent;
      }
      manifest = path.join(directory, 'package.json');
    }
    catch (error) { if (error.code === 'MODULE_NOT_FOUND') continue; throw error; }
    const pkg = JSON.parse(fs.readFileSync(manifest, 'utf8'));
    if (!/^(?:1\.15\.(2(?:-beta-20261012093542\.0)?|3)|1\.17\.(?:4|6))$/.test(pkg.version)) {
      throw new Error(`Review Zod compatibility before upgrading ${pkg.name}@${pkg.version}`);
    }
    function visit(dir) {
      for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const file = path.join(dir, entry.name);
        if (entry.isDirectory()) visit(file);
        else if (/\.(?:m?js|[cm]?ts)$/.test(entry.name)) {
          const before = fs.readFileSync(file, 'utf8');
          const after = before.replace(/(from\s*|require\()(["'])zod\2/g, '$1$2zod/v3$2');
          if (after !== before) { fs.writeFileSync(file, after); patched++; }
        }
      }
    }
    visit(path.join(path.dirname(manifest), 'dist'));
  }
  return patched;
}
if (process.argv[1] && fs.realpathSync(process.argv[1]) === fileURLToPath(import.meta.url)) {
  console.log(`Midscene Zod compatibility: ${patchMidsceneZod(process.argv[2] ?? process.cwd())} files updated`);
}
