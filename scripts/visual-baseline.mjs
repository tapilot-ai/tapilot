import {readFileSync,writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {resolve} from 'node:path';
const folder=resolve(process.argv.slice(2).find(arg=>!arg.startsWith('--'))||'docs/visual-baseline/20261005-theme-config');
const palette=JSON.parse(readFileSync('theme/brand.json','utf8')).light;
const allStates=['chat','keyboard','recording','cancel','transcribe','attachment','settings','report','chat-dark','settings-dark','report-dark','empty','speech-error','minimal-settings-light','minimal-settings-dark','minimal-chat-dark','minimal-report-dark','minimal-panel-dark'];
const selectedStates=process.argv.find(arg=>arg.startsWith('--states='))?.slice('--states='.length);
const states=selectedStates ? selectedStates.split(',') : ['settings', 'settings-dark', 'minimal-settings-light', 'minimal-settings-dark', 'minimal-chat-dark', 'minimal-report-dark', 'minimal-panel-dark'];
if(states.length===0 || states.some(state=>!allStates.includes(state)) || new Set(states).size!==states.length) {
 throw Error('Choose unique supported visual states with --states=chat,keyboard,...');
}
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
if(process.argv.includes('--check')) {
 const manifest=JSON.parse(readFileSync(`${folder}/manifest.json`,'utf8'));
 for(const shot of manifest.screenshots) {
  if(hash(readFileSync(`${folder}/${shot.file}`))!==shot.sha256)throw Error(`Baseline changed: ${shot.file}`);
 }
 if(hash(readFileSync('theme/brand.json'))!==manifest.themeSha256)throw Error('Theme differs from the visual baseline. Review and recapture.');
 console.log('Visual baseline integrity passed. This does not replace visual review.');
} else {
 const screenshots=states.map(state=>{const file=`${state}.png`;const bytes=readFileSync(`${folder}/${file}`);
  if(bytes.subarray(0,8).toString('hex')!=='89504e470d0a1a0a')throw Error(`Expected PNG: ${file}`);
  return {state,file,width:bytes.readUInt32BE(16),height:bytes.readUInt32BE(20),sha256:hash(bytes)};});
 const manifest={capturedAt:new Date().toISOString(),sourceRevision:execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),
  sourceDirty:!!execFileSync('git',['status','--porcelain'],{encoding:'utf8'}).trim(),device:'Xiaomi M2006J10C / MIUI 14',
  themeSha256:hash(readFileSync('theme/brand.json')),screenshots};
 writeFileSync(`${folder}/manifest.json`,JSON.stringify(manifest,null,2)+'\n');
 writeFileSync(`${folder}/index.html`,`<!doctype html><meta charset="utf-8"><title>Tapilot 界面基准</title><style>body{margin:24px;background:${palette.background};color:${palette.text};font-family:system-ui}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:24px}img{width:100%;max-width:300px;border:1px solid ${palette.line};border-radius:12px}</style><h1>Tapilot 真机界面基准</h1><p>供人工逐状态对照；完整链路结果另见验收报告。</p><main>${screenshots.map(s=>`<section><h2>${s.state}</h2><img src="${s.file}" alt="${s.state}"></section>`).join('')}</main>`);
 console.log(`Saved ${states.length} visual states and integrity manifest.`);
}
