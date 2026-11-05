import { assertPrivatePath } from '../server/private-path.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,readFile,stat,rm} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {loadDevices,saveConfig,selectDevice} from '../server/config.mjs';
const a={version:1,host:'192.168.1.10',port:39877,fingerprint:'a'.repeat(64),token:'x'.repeat(43)};
const b={...a,host:'192.168.1.11',fingerprint:'b'.repeat(64),token:'y'.repeat(43)};
async function fixture(t) {const dir=await mkdtemp(join(tmpdir(),'paired-devices-'));t.after(()=>rm(dir,{recursive:true,force:true}));return join(dir,'wireless.json');}
test('legacy pairing migrates without losing credentials; additional phones retain earlier pairings',async t=>{
 const path=await fixture(t);await writeFile(path,JSON.stringify(a));
 assert.equal((await loadDevices(path)).devices[0].token,a.token);
 await saveConfig(b,path);
 const registry=await loadDevices(path);
 assert.equal(registry.activeDeviceId,b.fingerprint);assert.equal(registry.devices.length,2);
 assert.equal(registry.devices[0].token,a.token);
 await assertPrivatePath(path);
 await selectDevice('connect',a.fingerprint,path);
 assert.equal((await loadDevices(path)).activeDeviceId,a.fingerprint);
});
test('disconnect survives endpoint refresh, and stale refresh cannot resurrect removed pairing',async t=>{
 const path=await fixture(t);await saveConfig(a,path);
 await selectDevice('disconnect',a.fingerprint,path);
 await saveConfig({...a,host:'192.168.2.20'},path,{activate:false});
 assert.equal((await loadDevices(path)).devices[0].autoConnect,false);
 await selectDevice('forget',a.fingerprint,path);
 await saveConfig(a,path,{activate:false});
 assert.equal((await loadDevices(path)).devices.length,0);
});
test('a stale endpoint refresh cannot overwrite credentials from a newer pairing', async t => {
 const path = await fixture(t);
 await saveConfig(a, path);
 const replacement = {...a, host:'192.168.3.10', token:'z'.repeat(43), name:'Paired again'};
 await saveConfig(replacement, path);
 await saveConfig({...a, host:'192.168.2.10'}, path, {activate:false});
 const saved = (await loadDevices(path)).devices[0];
 assert.equal(saved.token, replacement.token);
 assert.equal(saved.host, replacement.host);
 assert.equal(saved.name, replacement.name);
});
test('concurrent pairing writes retain both devices; corrupt configuration is not overwritten',async t=>{
 const path=await fixture(t);await Promise.all([saveConfig(a,path),saveConfig(b,path)]);
 assert.equal((await loadDevices(path)).devices.length,2);
 await writeFile(path,'broken');await assert.rejects(saveConfig(a,path));
 assert.equal(await readFile(path,'utf8'),'broken');
});

test('address recovery verifies identity without credentials and retains selection and disconnect state', async t => {
 const {updatePhoneEndpoint} = await import('../server/endpoint.mjs');
 const path = await fixture(t); await saveConfig(a,path); await saveConfig(b,path);
 await selectDevice('disconnect',a.fingerprint,path);
 await updatePhoneEndpoint(a.fingerprint,'100.82.240.18',40000,{path,probe:async(candidate,fingerprint,port)=>{
   assert.equal(candidate.host,'100.82.240.18');assert.equal('token' in candidate,false);
   assert.equal(fingerprint,a.fingerprint);assert.equal(port,40000);return true;
 }});
 const registry=await loadDevices(path), updated=registry.devices.find(d=>d.fingerprint===a.fingerprint);
 assert.equal(updated.host,'100.82.240.18');assert.equal(updated.port,40000);
 assert.equal(updated.token,a.token);assert.equal(updated.autoConnect,false);assert.equal(registry.activeDeviceId,b.fingerprint);
 const before=await readFile(path,'utf8');
 await assert.rejects(updatePhoneEndpoint(a.fingerprint,'100.82.240.19',undefined,{path,probe:async()=>false}),/verify/);
 assert.equal(await readFile(path,'utf8'),before);
 await assert.rejects(updatePhoneEndpoint(a.fingerprint,'not-an-ip',undefined,{path,probe:()=>{throw Error('must not probe');}}),/IPv4/);
});

test('one phone retains bounded address history with current credentials across networks', async t => {
 const path=await fixture(t);
 await saveConfig(a,path);
 await saveConfig({...a,host:'100.82.240.18',token:'z'.repeat(43)},path);
 let registry=await loadDevices(path);
 assert.equal(registry.devices.length,1);
 assert.deepEqual(registry.devices[0].endpoints,[{host:'100.82.240.18',port:a.port},{host:a.host,port:a.port}]);
 assert.equal(registry.devices[0].token,'z'.repeat(43));
 for(let i=1;i<=12;i++) await saveConfig({...a,host:'192.168.2.'+i},path);
 registry=await loadDevices(path);
 assert.equal(registry.devices[0].endpoints.length,8);
 assert.equal(registry.devices[0].endpoints[0].host,'192.168.2.12');
 assert.equal(registry.devices[0].endpoints.some(e=>'token' in e),false);
 await selectDevice('forget',a.fingerprint,path);
 assert.equal((await loadDevices(path)).devices.length,0);
});
