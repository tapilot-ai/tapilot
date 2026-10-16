import test from 'node:test';
import assert from 'node:assert/strict';
import https from 'node:https';
import { createHash } from 'node:crypto';
import { createPairingWindow } from '../server/pairing-window.mjs';
import { MidsceneController } from '../server/midscene.mjs';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { discoveredPhones } from '../server/discovery.mjs';

function offer(url, config, fingerprint) {
  return new Promise((resolve,reject)=>{
    const req=https.request(url,{method:'POST',agent:false,rejectUnauthorized:false,headers:{'Content-Type':'application/json'}},res=>{
      let body='';res.on('data',x=>body+=x);res.on('end',()=>resolve({status:res.statusCode,body}));
    });
    req.on('error',reject);
    req.on('socket',socket=>socket.on('secureConnect',()=>{
      const actual=createHash('sha256').update(socket.getPeerCertificate().raw).digest('hex');
      if(actual!==fingerprint){req.destroy(Error('Certificate mismatch'));return;}
      req.end(JSON.stringify(config));
    }));
  });
}
const config={version:1,host:'192.168.1.10',port:39877,fingerprint:'a'.repeat(64),token:'b'.repeat(43)};
test('phone scans computer QR, pins TLS, and waits for explicit local confirmation',async t=>{
  const calls=[];const window=await createPairingWindow({host:'127.0.0.1',discover:async()=>[],pair:async c=>calls.push(c)});t.after(()=>window.close());
  assert.match(window.qrDataUrl, /^data:image\/png;base64,/);
  assert.ok(window.expiresAt > Date.now());
  const qr=JSON.parse(Buffer.from(window.payload.slice('tapilot://computer/'.length),'base64url'));
  assert.ok(qr.url.startsWith('https://127.0.0.1:'));assert.match(qr.fingerprint,/^[a-f0-9]{64}$/);
  const wrong=await offer(qr.url,config,'f'.repeat(64)).then(()=>false,()=>true);assert.equal(wrong,true);
  assert.equal((await offer(qr.url,config,qr.fingerprint)).status,200);assert.equal(calls.length,0);
  const state=await(await fetch(window.url+'status')).json();assert.equal(state.pending.host,config.host);assert.ok(!JSON.stringify(state).includes(config.token));
  const denied=await fetch(window.url+'confirm',{method:'POST',headers:{Origin:'https://evil.example'}});assert.equal(denied.status,403);assert.equal(calls.length,0);
  const root=await mkdtemp(join(tmpdir(),'pair-panel-'));t.after(()=>rm(root,{recursive:true,force:true}));
  const controller=new MidsceneController({root});controller.pairWindow={url:window.url,expires_at:window.expiresAt};controller.pairProcess={connected:true};
  assert.equal((await controller.configure({action:'pair_status'})).structuredContent.pending.host,config.host);
  assert.equal(calls.length,0);
  const result=await controller.configure({action:'pair_confirm'});assert.equal(result.structuredContent.paired,true);assert.deepEqual(calls,[config]);
  assert.equal(controller.pairWindow.completed,true);
  await assert.rejects(controller.configure({action:'pair_confirm'}),/No pending phone/);
  controller.pairProcess.kill=()=>{throw Error('Starting a fresh pairing session');};
  await assert.rejects(controller.configure({action:'pair'}),/Starting a fresh pairing session/);
  assert.equal((await offer(qr.url,config,qr.fingerprint)).status,409);assert.equal(calls.length,1);
});
test('discovery metadata contains no pairing secret and ignores malformed services',()=>{
 const service={name:'Tapilot',port:39877,addresses:['192.168.1.10','::1','127.0.0.1'],txt:{version:'1',fingerprint:config.fingerprint,model:'SM-N9860',token:'must-not-leak'}};
 const found=discoveredPhones(service);assert.equal(found.length,1);assert.equal(found[0].fingerprint,config.fingerprint);assert.ok(!JSON.stringify(found).includes('must-not-leak'));
 assert.deepEqual(discoveredPhones({...service,txt:{version:'1',fingerprint:'bad'}}),[]);
});
