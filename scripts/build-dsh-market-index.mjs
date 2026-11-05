import fs from 'node:fs';
import path from 'node:path';
import { parseArgs } from 'node:util';

// Only advertise an actual published npm version. A local tarball is not a
// remotely installable marketplace source.
const { values } = parseArgs({ options: {
  version: { type: 'string' }, output: { type: 'string' },
} });
if (!values.version || !values.output) throw new Error('Usage: --version VERSION --output index.json');
const response = await fetch('https://registry.npmjs.org/@tapilot%2fdsh', { signal: AbortSignal.timeout(15000) });
if (!response.ok) throw new Error(`Published DSH package unavailable (${response.status}); no index written`);
const registry = await response.json();
const pkg = registry.versions?.[values.version];
if (!pkg?.dsh?.bundle || !pkg.dist?.integrity || !registry.time?.[values.version]) {
  throw new Error('Version is not a published DSH bundle; no index written');
}
const index = { format: 1, title: { en: 'Tapilot plugins', zh: 'Tapilot 插件' }, plugins: [{
  name: '@tapilot/dsh', title: 'Tapilot',
  description: { en: 'Live Android phone panel inside DSH Web conversations', zh: '在 DSH Web 会话右侧查看和操作 Android 手机' },
  author: 'quanru', homepage: 'https://github.com/tapilot-ai/tapilot',
  versions: [{ version: values.version, publishTimestamp: Math.floor(Date.parse(registry.time[values.version]) / 1000),
    source: { type: 'npm' }, engines: { dsh: pkg.engines.dsh } }],
}] };
fs.mkdirSync(path.dirname(path.resolve(values.output)), { recursive: true });
fs.writeFileSync(values.output, JSON.stringify(index, null, 2) + '\n');
console.log(values.output);
