import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { imagePoint } from '../ui/geometry.mjs';
import { t, setLanguage } from '../ui/i18n.mjs';
import { PreviewActivity } from '../ui/preview-activity.mjs';

const source = (await readFile(new URL('../ui/screen.mjs', import.meta.url), 'utf8')).replace(/^import .*;\n/gm, '');
const live = {state:'live',dataUrl:'data:image/png;base64,AA==',frame_id:'frame-1',width:100,height:200,capturedAt:0};
async function fixture(locale) {
  setLanguage('en');
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
  const timers=[];
  class App {constructor(){client=this;this.calls=[];} async connect(){} getHostContext(){return {locale};} async callServerTool(request){this.calls.push(request);return this.respond(request);} }
  await new (Object.getPrototypeOf(async function(){}).constructor)('App','imagePoint','PreviewActivity','document','window','setTimeout','clearTimeout','t','setLanguage','localize',source)(App,imagePoint,PreviewActivity,doc,win,(fn,delay)=>{timers.push({fn,delay});return timers.length;},()=>{},t,setLanguage,()=>{});
  client.ontoolresult({structuredContent:live});
  return {client,element,doc,back,win,timers};
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
  assert.deepEqual(f.client.calls[0],{name:'tapilot_preview_pair',arguments:{action:'pair'}});
  assert.equal(f.element('#pairing-link').href,'http://127.0.0.1:1234/pair');
  f.client.respond=async()=>({structuredContent:{url:'https://example.com/untrusted'}});
  await f.element('#pair').onclick();
  assert.match(f.element('[role=alert]').textContent,/Cannot create/);
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
  assert.match(f.element('#recovery').textContent,/Reload the plugin/);
  assert.equal(f.element('img').hidden,true);
});

test('focus moving to host chat preserves the screen and the ongoing action', async()=>{
  const f=await fixture(),pending=deferred();f.client.respond=()=>pending.promise;
  f.element('textarea').value='sent';const sent=f.element('form').onsubmit({preventDefault(){}});
  f.win.handlers.blur();
  assert.equal(f.element('img').hidden,false);
  pending.resolve({structuredContent:{...live,action_completed:true}});await sent;
  assert.equal(f.element('img').hidden,false);
  f.client.ontoolresult({structuredContent:live});
  assert.equal(f.element('img').hidden,false);
});


test('pair completion polling resumes automatically without a reconnect click', async()=>{
  const f=await fixture();
  f.client.ontoolresult({structuredContent:{state:'pairing_required'}});
  f.client.respond=async request=>request.name==='tapilot_preview_pair'
    ? {structuredContent:{url:'http://127.0.0.1:1234/pair'}} : {structuredContent:live};
  await f.element('#pair').onclick();
  assert.equal(f.timers.at(-1).delay,0);
  await f.timers.at(-1).fn();
  assert.equal(f.element('img').hidden,false);
  assert.equal(f.client.calls.filter(x=>x.name==='tapilot_preview_resume').length,0);
});

test('phone language is a fallback when the host provides no locale', async()=>{
  const f=await fixture();
  f.client.ontoolresult({structuredContent:{...live,language:'zh'}});
  assert.match(f.element('[role=status]').textContent,/已连接/);
  f.client.ontoolresult({structuredContent:{...live,language:'en'}});
  assert.match(f.element('[role=status]').textContent,/Connected/);
});


test('failed reconnect shows connection recovery without an ambiguous phone-action warning', async () => {
  const f = await fixture();
  f.client.respond = async () => ({isError:true, structuredContent:{state:'host_reload_required'}});
  await f.element('#resume').onclick();
  assert.equal(f.element('[role=alert]').hidden, true);
  assert.match(f.element('#recovery').textContent, /Reload the plugin/);
  assert.equal(f.element('#resume').hidden, true);
});

test('unreachable phone offers pairing and renders expiring QR from UI-only metadata', async () => {
  const f = await fixture();
  f.client.ontoolresult({structuredContent:{state:'unavailable'}});
  assert.equal(f.element('#pair').hidden, false);
  f.client.respond = async () => ({structuredContent:{url:'http://127.0.0.1:1234/pair',expires_at:Date.now()+300000},_meta:{pairingQr:'data:image/png;base64,AA=='}});
  await f.element('#pair').onclick();
  assert.equal(f.element('#pairing-qr').hidden, false);
  assert.equal(f.element('#pairing-qr').src, 'data:image/png;base64,AA==');
  f.client.ontoolresult({structuredContent:live});
  assert.equal(f.element('#pairing-qr').hidden, false);
  f.client.respond = async () => ({structuredContent:{url:'http://127.0.0.1:1234/pair',expires_at:Date.now()-1},_meta:{pairingQr:'data:image/png;base64,AA=='}});
  await f.element('#pair').onclick();
  assert.equal(f.element('#pairing-qr').hidden, true);
});


test('Chinese host locale wins over English phone responses and follows host changes', async () => {
  const f = await fixture('zh-CN');
  f.client.ontoolresult({structuredContent:{state:'unavailable',language:'en'}});
  assert.match(f.element('[role=status]').textContent, /无法连接手机/);
  f.client.onhostcontextchanged({locale:'en-US'});
  assert.match(f.element('[role=status]').textContent, /Cannot connect/);
  f.client.ontoolresult({structuredContent:{state:'unavailable',language:'zh'}});
  assert.match(f.element('[role=status]').textContent, /Cannot connect/);
});

test('inline scan waits for user confirmation and restores frames without opening a browser', async () => {
  const f = await fixture('zh-CN');
  f.client.ontoolresult({structuredContent:{state:'unavailable'}});
  let confirmed = false;
  f.client.openLink = () => {throw Error('Must stay in panel');};
  f.client.respond = async request => {
    if (request.arguments.action === 'pair') return {structuredContent:{url:'http://127.0.0.1:1234/pair/',expires_at:Date.now()+300000},_meta:{pairingQr:'data:image/png;base64,AA=='}};
    if (request.arguments.action === 'pair_status') return {structuredContent:{pending:{host:'192.168.1.10'},paired:false}};
    if (request.arguments.action === 'pair_confirm') {confirmed = true; return {structuredContent:{paired:true}};}
    return {structuredContent:confirmed ? live : {state:'unavailable'}};
  };
  await f.element('#pair').onclick();
  assert.equal(f.element('#pairing-link').hidden, true);
  assert.equal(f.element('#pair').hidden, true);
  await f.timers.at(-1).fn();
  assert.equal(confirmed, false);
  assert.equal(f.element('#pair-confirm').hidden, false);
  await f.element('#pair-confirm').onclick();
  await f.timers.at(-1).fn();
  assert.equal(confirmed, true);
  assert.equal(f.element('#pairing-qr').hidden, true);
  assert.equal(f.element('img').hidden, false);
});


test('hidden page releases transport and foreground starts fresh polling', async () => {
  const f = await fixture();
  f.client.respond = async () => ({structuredContent:{suspended:true}});
  f.doc.hidden = true; f.doc.handlers.visibilitychange();
  await f.timers.at(-1).fn();
  assert.equal(f.client.calls.at(-1).arguments.action, 'suspend');
  assert.equal(f.element('img').hidden, true);
  f.doc.hidden = false; f.doc.handlers.visibilitychange();
  f.client.respond = async () => ({structuredContent:live});
  await f.timers.at(-1).fn();
  assert.equal(f.element('img').hidden, false);
});

test('accessibility failure explains service recovery instead of Wi-Fi or pairing', async () => {
  const f=await fixture('zh-CN');
  f.client.ontoolresult({structuredContent:{state:'accessibility_required'}});
  assert.match(f.element('[role=status]').textContent,/设备控制服务未连接/);
  assert.match(f.element('#recovery').textContent,/辅助功能/);
  assert.equal(f.element('#pair').hidden,true);
});

test('late live results cannot dismiss an active pairing QR', async () => {
  const f=await fixture();
  f.client.respond=async()=>({structuredContent:{url:'http://127.0.0.1:1234/pair/',expires_at:Date.now()+300000},_meta:{pairingQr:'data:image/png;base64,AA=='}});
  await f.element('#pair').onclick();
  f.client.ontoolresult({structuredContent:live});
  assert.equal(f.element('#pairing-qr').hidden,false);
  assert.equal(f.element('img').hidden,true);
});
