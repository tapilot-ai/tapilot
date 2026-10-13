import { build } from 'esbuild';
import { readFile, writeFile } from 'node:fs/promises';
const result = await build({ entryPoints: [new URL('./screen.mjs', import.meta.url).pathname], bundle: true, write: false, format: 'esm', minify: true, target: 'es2022' });
const html = (await readFile(new URL('./screen.html', import.meta.url), 'utf8')).replace('/*BUNDLE*/', () => result.outputFiles[0].text.replaceAll('</script', '<\\/script'));
await writeFile(new URL('../assets/screen.html', import.meta.url), html);
