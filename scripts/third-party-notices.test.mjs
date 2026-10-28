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

test('missing metadata uses the retained upstream terms only for the reviewed version', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'tapilot-notice-version-'));
  try {
    const dir = path.join(root, 'node_modules/union'); fs.mkdirSync(dir, { recursive: true });
    const manifest = path.join(dir, 'package.json');
    fs.writeFileSync(manifest, JSON.stringify({ name: 'union', version: '0.5.0' }));
    const [reviewed] = collectNpmNotices(path.join(root, 'node_modules'), path.join(root, 'out'));
    assert.equal(reviewed.license, 'MIT');
    assert.equal(reviewed.reviewRequired, false);
    assert.ok(reviewed.files.some(file => file.source.includes('b69c2c0eb95d48b49e72182e53c1788efa195cc0')));
    fs.writeFileSync(manifest, JSON.stringify({ name: 'union', version: '0.5.1' }));
    const [unknown] = collectNpmNotices(path.join(root, 'node_modules'), path.join(root, 'out'));
    assert.equal(unknown.reviewRequired, true);
    fs.writeFileSync(manifest, JSON.stringify({ name: 'union', version: '0.5.0', license: 'GPL-3.0' }));
    assert.throws(() => collectNpmNotices(path.join(root, 'node_modules'), path.join(root, 'out')), /Conflicting license/);
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});

test('CLI collects an inventory when invoked through a symlinked checkout', async () => {
  const { spawnSync } = await import('node:child_process');
  const { fileURLToPath } = await import('node:url');
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'tapilot-notices-cli-'));
  try {
    fs.symlinkSync(fileURLToPath(new URL('../', import.meta.url)), path.join(root, 'checkout'), 'dir');
    const source = path.join(root, 'native/package.deb');
    fs.mkdirSync(source, {recursive:true});
    fs.writeFileSync(path.join(source, 'LICENSE'), 'fixture notice');
    const output = path.join(root, 'notices');
    const result = spawnSync(process.execPath, [path.join(root,'checkout/scripts/third-party-notices.mjs'), 'native', path.join(root,'native'), output], {encoding:'utf8'});
    assert.equal(result.status, 0, result.stderr);
    assert.equal(JSON.parse(fs.readFileSync(path.join(output,'index.json'))).packages.length, 1);
  } finally { fs.rmSync(root, {recursive:true,force:true}); }
});

test('native supplemental notices apply only to the reviewed binary version', async () => {
  const { collectNativeNotices } = await import('./third-party-notices.mjs');
  const root = fs.mkdtempSync(path.join(os.tmpdir(),'tapilot-native-notices-'));
  try {
    for (const name of ['openssl_1%3A3.6.5_aarch64.deb','openssl_1%3A3.6.6_aarch64.deb']) {
      fs.mkdirSync(path.join(root,'input',name),{recursive:true});
    }
    const result = collectNativeNotices(path.join(root,'input'),path.join(root,'output'));
    assert.equal(result.find(p=>p.id.includes('3.6.5')).reviewRequired,false);
    assert.equal(result.find(p=>p.id.includes('3.6.6')).reviewRequired,true);
    assert.match(result[0].files[0].source,/c8bd5a57108599ac650bbae77fcabe3109dab2e8/);
  } finally { fs.rmSync(root,{recursive:true,force:true}); }
});
