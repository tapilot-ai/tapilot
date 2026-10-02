import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const theme = JSON.parse(readFileSync(`${root}theme/brand.json`, 'utf8'));
const snake = key => key.replace(/[A-Z]/g, value => `_${value.toLowerCase()}`);
const vars = { background:'background',surface:'surface',text:'text',muted:'muted',line:'line',primary:'primary',primaryDark:'primary-dark',soft:'primary-soft',success:'success',danger:'danger' };
const gradient = `linear-gradient(120deg, ${theme.brand.blue}, ${theme.brand.middle} 55%, ${theme.brand.violet})`;
function cssVars(mode) {
  const palette = theme[mode];
  return Object.entries(vars).map(([key,name]) => `  --${name}: ${palette[key]};`).join('\n') +
    `\n  --brand-bubble: linear-gradient(125deg, ${palette.bubbleStart}, ${palette.bubbleMiddle} 58%, ${palette.bubbleEnd});`;
}
const css = `/* Generated from theme/brand.json. Run pnpm theme:generate. */\n:root {\n${cssVars('light')}\n  --brand-gradient: ${gradient};\n}\nhtml[data-theme="dark"] {\n${cssVars('dark')}\n}\n`;
const xml = `<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from theme/brand.json. -->\n<resources>\n` +
  Object.entries(theme.brand).map(([k,v])=>`    <color name="${k==='launcher'?'launcher_background':`brand_${snake(k)}`}">${v.length===9?`#${v.slice(7)}${v.slice(1,7)}`:v}</color>`.replace('" >','">')).join('\n')+'\n'+
  ['light','dark'].flatMap(mode=>Object.entries(theme[mode]).map(([k,v])=>`    <color name="brand_${mode==='dark'?'dark_':''}${snake(k)}">${v}</color>`)).join('\n')+'\n</resources>\n';
const kotlin = `// Generated from theme/brand.json.\npackage lifeos.md.tapilot.theme\n\ninternal object BrandColors {\n`+
 Object.entries(theme).flatMap(([mode,values])=>Object.entries(values).map(([k,v])=>{
  const hex=v.length===9 ? v.slice(7)+v.slice(1,7) : 'FF'+v.slice(1);
  return `    val ${mode==='brand'?k:mode+k[0].toUpperCase()+k.slice(1)} = 0x${hex}.toInt()`;
 })).join('\n')+`\n    val reportCss = """${css.replace(/\n/g,' ')}"""\n}\n`;
const outputs = {'runtime/brand.generated.css':css,'app/src/main/res/values/colors.xml':xml,'app/src/main/java/lifeos/md/tapilot/theme/BrandColors.kt':kotlin};
let stale = false;
for (const [path,value] of Object.entries(outputs)) {
 if(process.argv.includes('--check')) { if(readFileSync(root+path,'utf8')!==value){process.stderr.write(`Stale theme output: ${path}\n`);stale=true;} }
 else writeFileSync(root+path,value);
}
if(stale) process.exitCode=1;
