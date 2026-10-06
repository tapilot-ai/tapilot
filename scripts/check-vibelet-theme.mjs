import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
// Read-only check against the actual Vibelet shared theme source; no TS runtime required.
const root=fileURLToPath(new URL('../',import.meta.url));
const source=resolve(process.argv[2]??resolve(root,'../studio/packages/mobile-theme/src'));
const brand=JSON.parse(readFileSync(resolve(root,'theme/brand.json'),'utf8'));
const classic=readFileSync(resolve(source,'classic.ts'),'utf8');
const palettes=readFileSync(resolve(source,'palettes.ts'),'utf8');
const readObject=(text,name)=>Object.fromEntries([...text.match(new RegExp(`${name}[^=]*= \\{([\\s\\S]*?)\\n\\};`))[1].matchAll(/(\w+):\s*'([^']+)'/g)].map(([,key,value])=>[key,value]));
const normalize=value=>{
 if(value.startsWith('#'))return value.toUpperCase();
 const rgba=value.match(/^rgba\((\d+), (\d+), (\d+), ([\d.]+)\)$/);
 assert(rgba,`Unsupported source color ${value}`);
 return '#'+rgba.slice(1,4).map(Number).concat(Math.round(Number(rgba[4])*255)).map(n=>n.toString(16).padStart(2,'0')).join('').toUpperCase();
};
const mapping={background:'bg',surface:'card',text:'text',muted:'textMuted',line:'border',primary:'accent',primaryDark:'accentSecondary',soft:'accentSoft',success:'success',danger:'danger',bubbleStart:'userBubble',bubbleMiddle:'userBubble',bubbleEnd:'userBubble',input:'input',header:'headerBg',secondaryText:'textSecondary',contrast:'accentContrast',assistantBubble:'assistantBubble',cardAlt:'cardAlt',strongLine:'borderStrong',codeBackground:'codeBg',codeText:'codeText',softStrong:'accentSoftStrong',softAlt:'accentSoftAlt'};
let count=0;
for(const mode of ['Light','Dark']){
 const base=readObject(classic,`classic${mode}Colors`);
 for(const [name,colors] of [[mode.toLowerCase(),base],['minimal'+mode,{...base,...readObject(palettes,'minimal'+mode)}]]){
  for(const [key,sourceKey] of Object.entries(mapping)){
   assert.equal(brand[name][key],normalize(colors[sourceKey]),`${name}.${key}`);count++;
  }
  assert.equal(brand[name].bubbleText,normalize(colors.userBubbleText??colors.accentContrast),`${name}.bubbleText`);count++;
 }
}
console.log(`Vibelet 主题对齐检查通过：4 组配色，${count} 个颜色值。`);
