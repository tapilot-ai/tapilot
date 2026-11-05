import fs from 'node:fs';
import path from 'node:path';
const { directory } = JSON.parse(fs.readFileSync('build/client-distribution-path.json', 'utf8'));
const output = 'build/client-downloads';
fs.mkdirSync(output, { recursive: true });
for (const name of fs.readdirSync(directory)) {
  if (/\.(tar\.gz|tgz|zip)$/.test(name) || ['SHA256SUMS', 'INSTALL.zh.md', 'status.json'].includes(name))
    fs.copyFileSync(path.join(directory, name), path.join(output, name));
}
