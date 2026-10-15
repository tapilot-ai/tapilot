import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { imagePoint } from '../ui/geometry.mjs';
import { PreviewActivity } from '../ui/preview-activity.mjs';

const source = (await readFile(new URL('../ui/screen.mjs', import.meta.url), 'utf8')).replace(/^import .*;\n/gm, '');
const live = {state:'live',dataUrl:'data:image/png;base64,AA==',frame_id:'frame-1',width:100,height:200,capturedAt:0};
async function fixture() {
  const elements = new Map();
  const element = key => {
    if (!elements.has(key)) elements.set(key, {hidden:false,value:'',checked:true,dataset:{},handlers:{},
      setAttribute(){},removeAttribute(){},addEventListener(name, fn){this.handlers[name]=fn;}});
    return elements.get(key);
  };
  const back = element('back'); back.dataset.action='back';
  const doc = {hidden:false, handlers:{},querySelector:element,
    querySelectorAll:selector=>selector==='[data-control]'?[back,element('submit')]:[back],
    addEventListener(name,fn){this.handlers[name]=fn;}};
  const win = {handlers:{},addEventListener(name,fn){this.handlers[name]=fn;}};
  let client;
  class App {constructor(){client=this;this.calls=[];} async connect(){} async callServerTool(request){this.calls.push(request);return this.respond(request);} }
  await new (Object.getPrototypeOf(async function(){}).constructor)('App','imagePoint','PreviewActivity','document','window','setTimeout','clearTimeout',source)(App,imagePoint,PreviewActivity,doc,win,()=>1,()=>{});
  client.ontoolresult({structuredContent:live});
  return {client,element,doc,back,win};
}
const deferred = () => {let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve};};

test('manual input preserves failed drafts and never queues a second mutation', async()=>{
  const f=await fixture(), pending=deferred(); f.client.respond=()=>pending.promise;
  const draft=f.element('textarea');draft.value='你好';
  const submitted=f.element('form').onsubmit({preventDefault(){}});
  f.back.onclick();
  assert.equal(f.client.calls.length,1);
  assert.equal(f.back.disabled,true);
  assert.equal(draft.value,'你好');
  pending.resolve({isError:true,structuredContent:{state:'unavailable'}});await submitted;
  assert.equal(draft.value,'你好');
});

test('confirmed input clears only the submitted draft, including failed refresh', async()=>{
  const f=await fixture();f.client.respond=async()=>({structuredContent:{state:'unavailable',action_completed:true}});
  f.element('textarea').value='sent';await f.element('form').onsubmit({preventDefault(){}});
  assert.equal(f.element('textarea').value,'');
  f.client.ontoolresult({structuredContent:live});
  const pending=deferred();f.client.respond=()=>pending.promise;
  f.element('textarea').value='first';const sent=f.element('form').onsubmit({preventDefault(){}});
  f.element('textarea').value='next';pending.resolve({structuredContent:{...live,action_completed:true}});await sent;
  assert.equal(f.element('textarea').value,'next');
});

test('late action response cannot redisplay screen after the panel is hidden', async()=>{
  const f=await fixture(),pending=deferred();f.client.respond=()=>pending.promise;
  f.element('textarea').value='sent';const sent=f.element('form').onsubmit({preventDefault(){}});
  f.doc.hidden=true;f.doc.handlers.visibilitychange();
  pending.resolve({structuredContent:{...live,action_completed:true}});await sent;
  assert.equal(f.element('img').hidden,true);
});

test('unpaired panel provides a local pairing window without phone access', async()=>{
  const f=await fixture();f.client.ontoolresult({structuredContent:{state:'setup_required'}});
  assert.equal(f.element('#pair').hidden,false);
  f.client.respond=async()=>({structuredContent:{url:'http://127.0.0.1:1234/pair'}});
  await f.element('#pair').onclick();
  assert.deepEqual(f.client.calls[0],{name:'tapilot_midscene',arguments:{action:'pair'}});
  assert.equal(f.element('#pairing-link').href,'http://127.0.0.1:1234/pair');
  f.client.respond=async()=>({structuredContent:{url:'https://example.com/untrusted'}});
  await f.element('#pair').onclick();
  assert.match(f.element('[role=alert]').textContent,/无法创建/);
});

test('host tool results cannot reveal a backgrounded or closed panel', async()=>{
  const f=await fixture();
  f.doc.hidden=true;f.doc.handlers.visibilitychange();
  f.client.ontoolresult({structuredContent:live});
  assert.equal(f.element('img').hidden,true);
  f.doc.hidden=false;await f.client.onteardown();
  f.client.ontoolresult({structuredContent:live});
  assert.equal(f.element('img').hidden,true);
});

test('missing host files offer reload guidance instead of phone reconnection', async()=>{
  const f=await fixture();
  f.client.ontoolresult({isError:true,structuredContent:{state:'host_reload_required'}});
  assert.equal(f.element('#resume').hidden,true);
  assert.equal(f.element('#pair').hidden,true);
  assert.match(f.element('#recovery').textContent,/重载插件/);
  assert.equal(f.element('img').hidden,true);
});

test('losing focus hides the screen immediately and discards a late action', async()=>{
  const f=await fixture(),pending=deferred();f.client.respond=()=>pending.promise;
  f.element('textarea').value='sent';const sent=f.element('form').onsubmit({preventDefault(){}});
  f.win.handlers.blur();
  assert.equal(f.element('img').hidden,true);
  pending.resolve({structuredContent:{...live,action_completed:true}});await sent;
  assert.equal(f.element('img').hidden,true);
  f.client.ontoolresult({structuredContent:live});
  assert.equal(f.element('img').hidden,true);
});
