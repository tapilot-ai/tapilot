import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const theme = JSON.parse(readFileSync(`${root}theme/brand.json`, 'utf8'));
const snake = key => key.replace(/[A-Z]/g, value => `_${value.toLowerCase()}`);
const vars = { background:'background',surface:'surface',text:'text',muted:'muted',line:'line',primary:'primary',primaryDark:'primary-dark',soft:'primary-soft',input:'input',success:'success',danger:'danger',header:'header',secondaryText:'text-secondary',contrast:'primary-contrast',bubbleText:'bubble-text',assistantBubble:'assistant-bubble',cardAlt:'card-alt',codeBackground:'code-background',codeText:'code-text' };
const gradient = `linear-gradient(120deg, ${theme.brand.blue}, ${theme.brand.blue})`;
function cssVars(mode) {
  const palette = theme[mode];
  return Object.entries(vars).map(([key,name]) => `  --${name}: ${palette[key]};`).join('\n') +
    `\n  --brand-bubble: linear-gradient(125deg, ${palette.bubbleStart}, ${palette.bubbleMiddle} 58%, ${palette.bubbleEnd});`;
}
const minimalCss = ['Light', 'Dark'].map(mode => `html[data-palette="minimal"][data-theme="${mode.toLowerCase()}"] {\n${cssVars('minimal'+mode)}\n}`).join('\n');
const css = `/* Generated from theme/brand.json. Run pnpm theme:generate. */\n:root {\n${cssVars('light')}\n  --brand-gradient: ${gradient};\n}\nhtml[data-theme="dark"] {\n${cssVars('dark')}\n}\n${minimalCss}\n`;
const xml = `<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from theme/brand.json. -->\n<resources>\n` +
  Object.entries(theme.brand).map(([k,v])=>`    <color name="${k==='launcher'?'launcher_background':`brand_${snake(k)}`}">${v.length===9?`#${v.slice(7)}${v.slice(1,7)}`:v}</color>`.replace('" >','">')).join('\n')+'\n'+
  ['light','dark'].flatMap(mode=>Object.entries(theme[mode]).map(([k,v])=>`    <color name="brand_${mode==='dark'?'dark_':''}${snake(k)}">${v.length===9?`#${v.slice(7)}${v.slice(1,7)}`:v}</color>`)).join('\n')+'\n</resources>\n';
const kotlin = `// Generated from theme/brand.json.\npackage lifeos.md.tapilot.theme\n\ninternal object BrandColors {\n`+
 Object.entries(theme).flatMap(([mode,values])=>Object.entries(values).map(([k,v])=>{
  const hex=v.length===9 ? v.slice(7)+v.slice(1,7) : 'FF'+v.slice(1);
  return `    val ${mode==='brand'?k:mode+k[0].toUpperCase()+k.slice(1)} = 0x${hex}.toInt()`;
 })).join('\n')+`\n    val reportCss = """${css.replace(/\n/g,' ')}"""\n}\n`;
const fields = Object.keys(theme.light);
const paletteSource = `// Generated from theme/brand.json.\npackage lifeos.md.tapilot.theme\n\nimport lifeos.md.tapilot.R\n\ninternal data class ThemePalette(\n${fields.map(k => `    val ${k}: Int,`).join('\n')}\n)\n\ninternal object ThemePalettes {\n` +
 ['light','dark','minimalLight','minimalDark'].map(mode => `    private val ${mode} = ThemePalette(\n${fields.map(k=>`        ${k} = BrandColors.${mode+k[0].toUpperCase()+k.slice(1)},`).join('\n')}\n    )`).join('\n') +
 `\n    fun get(id: String, dark: Boolean): ThemePalette =\n        if (id == "minimal") { if (dark) minimalDark else minimalLight } else { if (dark) this.dark else light }\n\n    fun resource(id: String, dark: Boolean, resource: Int): Int? {\n        val palette = get(id, dark)\n        return when (resource) {\n${fields.map(k=>`            R.color.brand_${snake(k)}, R.color.brand_dark_${snake(k)} -> palette.${k}`).join('\n')}\n            else -> null\n        }\n    }\n}\n`;
const outputs = {'app/src/main/java/lifeos/md/tapilot/theme/ThemePalettes.kt':paletteSource,'runtime/brand.generated.css':css,'app/src/main/res/values/colors.xml':xml,'app/src/main/java/lifeos/md/tapilot/theme/BrandColors.kt':kotlin};
let stale = false;
for (const [path,value] of Object.entries(outputs)) {
 if(process.argv.includes('--check')) { if(readFileSync(root+path,'utf8')!==value){process.stderr.write(`Stale theme output: ${path}\n`);stale=true;} }
 else writeFileSync(root+path,value);
}
if(stale) process.exitCode=1;
