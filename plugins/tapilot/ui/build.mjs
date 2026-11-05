import { fileURLToPath } from 'node:url';
import { installationHtml } from '../server/installation-html.mjs';
import { build } from 'esbuild';
import { readFile, writeFile } from 'node:fs/promises';
const result = await build({ entryPoints: [fileURLToPath(new URL('./screen.mjs', import.meta.url))], bundle: true, write: false, format: 'esm', minify: true, target: 'es2022' });
const html = (await readFile(new URL('./screen.html', import.meta.url), 'utf8')).replace('<!--INSTALLATION-->', await installationHtml()).replace('/*BUNDLE*/', () => result.outputFiles[0].text.replaceAll('</script', '<\\/script'));
const output = new URL('../assets/screen.html', import.meta.url);
if (process.argv.includes('--check')) {
  if (await readFile(output, 'utf8') !== html) throw new Error('Stale screen UI. Run npm run build:ui in plugins/tapilot.');
} else {
  await writeFile(output, html);
}

const browserHtml = html.replace('<head>', '<head><script>globalThis.__TAPILOT_BROWSER__=true;</script>');
const browserOutput = new URL('../assets/browser.html', import.meta.url);
if (process.argv.includes('--check')) {
  if (await readFile(browserOutput, 'utf8') !== browserHtml) throw new Error('Stale browser UI');
} else await writeFile(browserOutput, browserHtml);

const dshHtml = html.replace('<head>', '<head><script>globalThis.__TAPILOT_DSH__=true;</script>');
const dshOutput = new URL('../assets/dsh.html', import.meta.url);
if (process.argv.includes('--check')) {
  if (await readFile(dshOutput, 'utf8') !== dshHtml) throw new Error('Stale DSH UI');
} else await writeFile(dshOutput, dshHtml);
