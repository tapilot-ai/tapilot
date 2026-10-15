import { build } from 'esbuild';
import { readFile, writeFile } from 'node:fs/promises';
const result = await build({ entryPoints: [new URL('./screen.mjs', import.meta.url).pathname], bundle: true, write: false, format: 'esm', minify: true, target: 'es2022' });
const html = (await readFile(new URL('./screen.html', import.meta.url), 'utf8')).replace('/*BUNDLE*/', () => result.outputFiles[0].text.replaceAll('</script', '<\\/script'));
const output = new URL('../assets/screen.html', import.meta.url);
if (process.argv.includes('--check')) {
  if (await readFile(output, 'utf8') !== html) throw new Error('Stale screen UI. Run npm run build:ui in plugins/android-use.');
} else {
  await writeFile(output, html);
}
