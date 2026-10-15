import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { collectNpmNotices } from './third-party-notices.mjs';
test('notice inventory preserves full notices and exposes missing and copyleft reviews', () => {
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'tapilot-notices-'));
  try {
    for (const [name,license,text] of [['normal','MIT','Copyright upstream\nMIT terms'],['missing','MIT',null],['wasm','Apache-2.0 AND LGPL-3.0-or-later','Apache terms only']]) {
      const dir=path.join(root,'node_modules',name);fs.mkdirSync(dir,{recursive:true});
      fs.writeFileSync(path.join(dir,'package.json'),JSON.stringify({name,version:'1.0.0',license}));
      if(text)fs.writeFileSync(path.join(dir,'LICENSE'),text);
    }
    const out=path.join(root,'out');const packages=collectNpmNotices(path.join(root,'node_modules'),out);
    assert.equal(fs.readFileSync(path.join(out,'normal@1.0.0/LICENSE'),'utf8'),'Copyright upstream\nMIT terms');
    assert.equal(packages.find(p=>p.name==='normal').reviewRequired,false);
    assert.equal(packages.find(p=>p.name==='missing').reviewRequired,true);
    assert.equal(packages.find(p=>p.name==='wasm').reviewRequired,true);
    fs.writeFileSync(path.join(out,'stale'),'old');collectNpmNotices(path.join(root,'node_modules'),out);
    assert.equal(fs.existsSync(path.join(out,'stale')),false);
  } finally {fs.rmSync(root,{recursive:true,force:true});}
});
