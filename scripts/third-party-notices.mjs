import fs from 'node:fs';
import { verifyReviewedSharp } from './install-reviewed-sharp.mjs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const licenseName = /^(?:licen[cs]e|copying|notice|copyright)(?:$|[._-])/i;
const sha256 = data => createHash('sha256').update(data).digest('hex');
const repositoryRoot = fileURLToPath(new URL('../', import.meta.url));
function licenseFiles(root, relative = '') {
  const files = [];
  for (const entry of fs.readdirSync(path.join(root, relative), { withFileTypes: true })) {
    if (entry.isSymbolicLink() || entry.name === 'node_modules' || entry.name === '.git') continue;
    const name = path.join(relative, entry.name);
    if (entry.isDirectory()) files.push(...licenseFiles(root, name));
    else if (licenseName.test(entry.name)) files.push(name);
  }
  return files.sort();
}
function copyNotices(source, destination) {
  return licenseFiles(source).map(relative => {
    const bytes = fs.readFileSync(path.join(source, relative));
    const target = path.join(destination, relative);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, bytes);
    return { path: relative.split(path.sep).join('/'), sha256: sha256(bytes) };
  });
}
/** Collects actual installed files, without treating SPDX metadata as a license text. */
export function collectNpmNotices(modules, output) {
  const supplements = JSON.parse(fs.readFileSync(path.join(repositoryRoot, 'third-party/npm-supplements.json'), 'utf8'));
  fs.rmSync(output, { recursive: true, force: true });
  fs.mkdirSync(output, { recursive: true });
  const packages = [];
  function visit(directory) {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true }).sort((a,b) => a.name.localeCompare(b.name))) {
      if (!entry.isDirectory() || entry.name.startsWith('.')) continue;
      const source = path.join(directory, entry.name);
      if (entry.name.startsWith('@')) { visit(source); continue; }
      const manifest = path.join(source, 'package.json');
      if (!fs.existsSync(manifest)) continue;
      const pkg = JSON.parse(fs.readFileSync(manifest, 'utf8'));
      const id = `${encodeURIComponent(pkg.name)}@${encodeURIComponent(pkg.version)}`;
      if (!packages.some(item => item.id === id)) {
        let license = typeof pkg.license === 'string' ? pkg.license : pkg.license?.type ?? 'UNKNOWN';
        const files = copyNotices(source, path.join(output, id));
        for (const supplement of supplements.filter(item => item.name === pkg.name && item.version === pkg.version)) {
          const bytes = fs.readFileSync(path.join(repositoryRoot, supplement.path));
          if (sha256(bytes) !== supplement.sha256) throw new Error(`Supplement checksum mismatch: ${supplement.path}`);
          // A pinned upstream notice can identify omitted package metadata, but cannot
          // silently replace a package's declared license with different terms.
          if (supplement.license) {
            if (license !== 'UNKNOWN' && license !== supplement.license) throw new Error(`Conflicting license supplement: ${pkg.name}`);
            license = supplement.license;
          }
          const relative = `upstream-${path.basename(supplement.path)}`;
          fs.mkdirSync(path.join(output, id), { recursive: true });
          fs.writeFileSync(path.join(output, id, relative), bytes);
          files.push({ path: relative, sha256: supplement.sha256, source: supplement.url });
        }
        let verifiedRebuild = false;
        if (pkg.name === '@img/sharp-wasm32') {
          const review = JSON.parse(fs.readFileSync(path.join(repositoryRoot, 'third-party/sharp-rebuild.json'), 'utf8'));
          verifyReviewedSharp(source, review);
          verifiedRebuild = files.some(file => file.path === 'upstream-Tapilot-0.1.8-wasm-NOTICES.txt');
        }
        packages.push({ id, name: pkg.name, version: pkg.version, license,
          repository: pkg.repository?.url ?? pkg.repository ?? null, files,
          reviewRequired: files.length === 0 || license === 'UNKNOWN' || (/(?:A?GPL|LGPL|MPL|SEE LICENSE)/i.test(license) && !verifiedRebuild) });
      }
      if (fs.existsSync(path.join(source, 'node_modules'))) visit(path.join(source, 'node_modules'));
    }
  }
  visit(modules);
  packages.sort((a,b) => a.id.localeCompare(b.id));
  fs.writeFileSync(path.join(output, 'index.json'), JSON.stringify({ schemaVersion: 1, packages }, null, 2)+'\n');
  return packages;
}
export function collectNativeNotices(unpacked, output) {
  const supplements = JSON.parse(fs.readFileSync(path.join(repositoryRoot, 'third-party/native-supplements.json'), 'utf8'));
  fs.rmSync(output, { recursive: true, force: true });
  fs.mkdirSync(output, { recursive: true });
  const packages = fs.readdirSync(unpacked).sort().filter(name => name.endsWith('.deb')).map(name => {
    const files = copyNotices(path.join(unpacked, name), path.join(output, name));
    for (const supplement of supplements.filter(item => item.id === name)) {
      const bytes = fs.readFileSync(path.join(repositoryRoot, supplement.path));
      if (sha256(bytes) !== supplement.sha256) throw new Error(`Native supplement checksum mismatch: ${name}`);
      const relative = `upstream-${path.basename(supplement.path)}`;
      fs.mkdirSync(path.join(output, name), { recursive: true });
      fs.writeFileSync(path.join(output, name, relative), bytes);
      files.push({ path: relative, sha256: supplement.sha256, source: supplement.url });
    }
    return { id: name, files, reviewRequired: files.length === 0,
      sourceRecipes: 'https://github.com/termux/termux-packages',
      modifications: 'ELF dependency/SONAME changes documented in scripts/patch-node-elf.py' };
  });
  fs.writeFileSync(path.join(output, 'index.json'), JSON.stringify({ schemaVersion: 1, packages }, null, 2)+'\n');
  return packages;
}
if (process.argv[1] && fs.realpathSync(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [kind, source, output] = process.argv.slice(2);
  if (!['npm','native'].includes(kind) || !source || !output) throw new Error('Usage: third-party-notices.mjs npm|native <installed tree> <output>');
  const entries = kind === 'npm' ? collectNpmNotices(source, output) : collectNativeNotices(source, output);
  console.log(`Collected ${entries.length} ${kind} packages; ${entries.filter(item => item.reviewRequired).length} require redistribution review`);
}
