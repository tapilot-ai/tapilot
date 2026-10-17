import fs from 'node:fs';
import path from 'node:path';
const allows = (values, target) => !values || (!values.includes(`!${target}`) &&
  (!values.some(value => !value.startsWith('!')) || values.includes(target)));

/** Remove host-only optional packages from a separate deployment tree. */
export function pruneAndroidPackages(modules) {
  const removed = [];
  function visit(directory) {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      if (!entry.isDirectory() || entry.name.startsWith('.')) continue;
      const root = path.join(directory, entry.name);
      if (entry.name.startsWith('@')) { visit(root); continue; }
      const manifest = path.join(root, 'package.json');
      if (!fs.existsSync(manifest)) continue;
      const pkg = JSON.parse(fs.readFileSync(manifest, 'utf8'));
      if (!allows(pkg.os, 'android') || (!allows(pkg.cpu, 'arm64') && !allows(pkg.cpu, 'wasm32'))) {
        removed.push(`${pkg.name}@${pkg.version}`);
        fs.rmSync(root, { recursive: true });
      } else if (fs.existsSync(path.join(root, 'node_modules'))) visit(path.join(root, 'node_modules'));
    }
  }
  visit(modules);
  return removed.sort();
}

/** Source maps are optional debugger assets; retain executable code, types and licenses. */
export function pruneAndroidSourceMaps(root) {
  let files = 0, bytes = 0;
  function visit(directory) {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const file = path.join(directory, entry.name);
      if (entry.isDirectory()) { visit(file); continue; }
      if (!entry.isFile() || !/\.(?:c|m)?js\.map$/.test(entry.name)) continue;
      let map;
      try { map = JSON.parse(fs.readFileSync(file, 'utf8')); } catch { continue; }
      if (map?.version !== 3 || !Array.isArray(map.sources) || typeof map.mappings !== 'string') continue;
      bytes += fs.statSync(file).size;
      fs.unlinkSync(file); files++;
    }
  }
  visit(root);
  return { files, bytes };
}
