import test from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {mkdtemp, mkdir, writeFile, readFile, rm, unlink} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {compatibleRuntime, runtimePaths, runtimeVersion, runtimeRequest} from '../server/runtime-client.mjs';

test('runtime patch compatibility avoids downgrades and rejects incompatible releases', () => {
  assert.equal(compatibleRuntime(runtimeVersion), true);
  assert.equal(compatibleRuntime('0.6.999'), true);
  assert.equal(compatibleRuntime('0.6.7'), false);
  assert.throws(() => compatibleRuntime('0.7.0'), /reload/);
});

test('concurrent first requests upgrade an older runtime once before dispatch', async t => {
  const dir = await mkdtemp(join(tmpdir(),'tapilot-upgrade-'));
  process.env.TAPILOT_STATE_DIR = dir;
  process.env.TAPILOT_CONFIG = join(dir,'wireless.json');
  process.env.TAPILOT_INSTALLATIONS_DIR = join(dir, 'isolated-installations');
  let stops=0, dispatched=0;
  const paths = runtimePaths();
  const server=createServer(async (req,res) => {
    res.setHeader('Content-Type','application/json');
    if(req.url==='/health') return res.end(JSON.stringify({version:'0.6.7'}));
    if(req.url==='/stop') {
      stops++;
      await unlink(paths.metadata);
      res.end('{}'); server.close(); return;
    }
    dispatched++;res.end('{}');
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(async()=>{
    await runtimeRequest('/stop',{}, {start:false}).catch(()=>{});
    server.close();await rm(dir,{recursive:true,force:true});
  });
  await mkdir(paths.dir,{recursive:true});
  await writeFile(paths.metadata,JSON.stringify({port:server.address().port,token:'a'.repeat(64)}));
  const results=await Promise.all([runtimeRequest('/preview',{}),runtimeRequest('/preview',{})]);
  assert.equal(stops,1);
  assert.equal(dispatched,0,'no request is sent to the obsolete runtime');
  assert.ok(results.every(r=>r.url?.startsWith('http://127.0.0.1:')));
  const started = JSON.parse(await readFile(paths.metadata, 'utf8'));
  const health = await fetch(`http://127.0.0.1:${started.port}/health`, {headers:{Authorization:`Bearer ${started.token}`}});
  assert.equal((await health.json()).privacyPaused, true, 'legacy stop replies with no privacy state must not resume capture');
});
