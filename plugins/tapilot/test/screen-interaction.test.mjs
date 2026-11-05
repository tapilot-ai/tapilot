import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { imagePoint } from '../ui/geometry.mjs';
import { t, setLanguage, connectionErrorMessage } from '../ui/i18n.mjs';
import { PreviewActivity } from '../ui/preview-activity.mjs';
import { panelRequest } from '../ui/panel-request.mjs';
import { createPanelBridge } from '../ui/panel-bridge.mjs';

const source = (await readFile(new URL('../ui/screen.mjs', import.meta.url), 'utf8')).replace(/^import .*;\n/gm, '');
const live = {state:'live',dataUrl:'data:image/png;base64,AA==',frame_id:'frame-1',width:100,height:200,capturedAt:0};
async function fixture(locale, {autoLoad = true, host = {}, context = {}, displayError} = {}) {
  setLanguage('en');
  const elements = new Map();
  const element = key => {
    if (!elements.has(key)) {
      const value = {hidden:false,value:'',checked:true,dataset:{},handlers:{},children:[],attributes:{},style:{},
        querySelector(selector){return element(key+' '+selector);},replaceChildren(){this.children=[];},append(child){this.children.push(child);},focus(){doc.activeElement=this;},contains(target){return this===target || this.children.includes(target);},
        setAttribute(name,value){this.attributes[name]=value;},removeAttribute(name){delete this.attributes[name];},addEventListener(name, fn){this.handlers[name]=fn;}};
      if (key === 'img') Object.defineProperty(value, 'src', {
        get() { return this.currentSrc; },
        set(src) { this.currentSrc = src; if (autoLoad) queueMicrotask(() => this.onload?.()); },
      });
      elements.set(key, value);
    }
    return elements.get(key);
  };
  const back = element('back'); back.dataset.action='back';
  let created = 0;
  const doc = {hidden:false, body:element('body'), handlers:{},querySelector:element,createElement:tag=>element(`${tag}-${++created}`),
    querySelectorAll:selector=>selector==='[data-control]'?[back,element('submit')]:[back],
    addEventListener(name,fn){this.handlers[name]=fn;}};
  const win = {handlers:{},addEventListener(name,fn){this.handlers[name]=fn;}};
  let client;
  const timers=[];
  class App {constructor(){client=this;this.calls=[];this.displayRequests=[];this.sizes=[];} async connect(){} getHostContext(){return {locale,...context};} async requestDisplayMode(request){this.displayRequests.push(request);if(displayError)throw displayError;return {mode:context.resultMode ?? request.mode};} async sendSizeChanged(size){this.sizes.push(size);} async callServerTool(request){this.calls.push(request);return this.respond(request);} }
  await new (Object.getPrototypeOf(async function(){}).constructor)('panelRequest','createPanelBridge','imagePoint','PreviewActivity','document','window','setTimeout','clearTimeout','t','setLanguage','localize','connectionErrorMessage','globalThis',source)((call,request)=>panelRequest(call,request,{setTimer:(fn,delay)=>{const timer={fn,delay};timers.push(timer);return timer;},clearTimer:timer=>{const index=timers.indexOf(timer);if(index>=0)timers.splice(index,1);}}),options=>createPanelBridge({...options,host,AppClass:App}),imagePoint,PreviewActivity,doc,win,(fn,delay)=>{timers.push({fn,delay});return timers.length;},()=>{},t,setLanguage,()=>{},connectionErrorMessage,{__TAPILOT_PANEL__:host});
  client.ontoolresult({structuredContent:live});
  await Promise.resolve();
  return {client,element,doc,back,win,timers};
}
const deferred = () => {let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve};};

test('Doubao requests its expanded surface once, without reopening it on frame updates', async () => {
  const f = await fixture('en', {host:{name:'doubao'},context:{displayMode:'inline',availableDisplayModes:['inline','fullscreen']}});
  assert.deepEqual(f.client.displayRequests,[{mode:'fullscreen'}]);
  f.client.ontoolresult({structuredContent:live});
  f.client.onhostcontextchanged({displayMode:'inline'});
  assert.equal(f.client.displayRequests.length,1);
});

test('expansion rejection preserves polling and other hosts keep their existing display policy', async () => {
  const f = await fixture('en', {host:{name:'doubao'},context:{availableDisplayModes:['fullscreen']},displayError:new Error('Host rejected expansion')});
  assert.ok(f.timers.length);
  assert.deepEqual(f.client.sizes,[]);
  const inline = await fixture('en', {host:{name:'doubao'},context:{availableDisplayModes:['fullscreen'],resultMode:'inline'}});
  assert.deepEqual(inline.client.sizes,[]);
  for (const host of ['codex','workbuddy','cursor','vscode']) {
    const other = await fixture('en', {host:{name:host},context:{availableDisplayModes:['fullscreen']}});
    assert.equal(other.client.displayRequests.length,0);
  }
});

test('a closed Doubao sidebar has an explicit expansion action without reopening on context changes', async () => {
  const f = await fixture('en', {host:{name:'doubao'},context:{displayMode:'inline',availableDisplayModes:['inline','fullscreen']}});
  assert.equal(f.element('#expand-panel').hidden,true);
  f.client.onhostcontextchanged({displayMode:'inline'});
  assert.equal(f.element('#expand-panel').hidden,false);
  assert.equal(f.client.displayRequests.length,1);
  await f.element('#expand-panel').onclick();
  assert.equal(f.client.displayRequests.length,2);
  assert.equal(f.element('#expand-panel').hidden,true);
});

test('failed privacy resume exposes the connection error while preserving the pause', async () => {
  const f = await fixture('en');
  f.client.ontoolresult({structuredContent:{state:'manual'}});
  f.client.respond = async () => ({isError:true,structuredContent:{state:'manual'},content:[{type:'text',text:'Phone connection timed out'}]});
  await f.element('#resume').onclick();
  assert.equal(f.element('img').hidden,true);
  assert.equal(f.element('[role=alert]').hidden,false);
  assert.match(f.element('[role=alert]').textContent,/timed out|Wi-Fi/);
  assert.equal(f.element('#resume').hidden,false);
});

test('phone controls wait until the displayed image has loaded the matching frame', async () => {
  const f = await fixture('en', {autoLoad:false});
  f.client.respond = async () => ({structuredContent:{...live,action_completed:true}});
  assert.equal(f.back.disabled, true);
  f.back.onclick();
  assert.equal(f.client.calls.length, 0);
  f.element('img').onload?.();
  assert.equal(f.back.disabled, false);
  f.client.ontoolresult({structuredContent:{...live,frame_id:'frame-2',dataUrl:'data:image/png;base64,AQ=='}});
  assert.equal(f.back.disabled, true, 'the old pixels cannot use the new frame ID');
  f.back.onclick();
  assert.equal(f.client.calls.length, 0);
  f.element('img').onload?.();
  await f.back.onclick();
  assert.equal(f.client.calls[0].arguments.frame_id, 'frame-2');
});

test('a late image load cannot restore controls after a privacy pause', async () => {
  const f = await fixture('en', {autoLoad:false});
  const loaded = f.element('img').onload;
  f.client.ontoolresult({structuredContent:{state:'manual'}});
  loaded?.();
  assert.equal(f.element('img').hidden, true);
  assert.equal(f.back.disabled, true);
});

test('an action without a refreshed image cannot reuse its consumed frame', async () => {
  const f = await fixture();
  f.client.respond = async () => ({structuredContent:{state:'live',action_completed:true}});
  f.element('textarea').value = 'sent';
  await f.element('form').onsubmit({preventDefault(){}});
  assert.equal(f.back.disabled, true);
  f.back.onclick();
  assert.equal(f.client.calls.length, 1);
});

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
  assert.match(f.element('#pairing-status').textContent,/Could not create/);
  assert.equal(f.element('#pair-refresh').hidden,false);
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
    ? {structuredContent:request.arguments.action === 'pair' ? {url:'http://127.0.0.1:1234/pair',expires_at:Date.now()+300000} : {paired:true}} : {structuredContent:live};
  await f.element('#pair').onclick();
  assert.equal(f.timers.at(-1).delay,0);
  await f.timers.at(-1).fn();
  assert.equal(f.element('img').hidden,false);
  assert.equal(f.client.calls.filter(x=>x.name==='tapilot_preview_resume').length,0);
});

test('phone language is a fallback when the host provides no locale', async()=>{
  const f=await fixture();
  f.client.ontoolresult({structuredContent:{...live,language:'zh'}});
  await Promise.resolve();
  assert.match(f.element('[role=status]').textContent,/已连接/);
  f.client.ontoolresult({structuredContent:{...live,language:'en'}});
  await Promise.resolve();
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

test('unreachable phone offers retry; explicit add-device renders expiring QR', async () => {
  const f = await fixture();
  f.client.ontoolresult({structuredContent:{state:'unavailable'}});
  assert.equal(f.element('#pair').hidden, true);
  assert.equal(f.element('#installation').hidden, true);
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
  assert.match(f.element('[role=status]').textContent, /自动重连/);
  f.client.onhostcontextchanged({locale:'en-US'});
  assert.match(f.element('[role=status]').textContent, /Reconnecting automatically/);
  f.client.ontoolresult({structuredContent:{state:'unavailable',language:'zh'}});
  assert.match(f.element('[role=status]').textContent, /Reconnecting automatically/);
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

test('failed connection confirmation preserves pending phone and displays actionable error', async () => {
  const f = await fixture('zh-CN');
  const message = '已收到手机扫码信息，但电脑无法连接手机。请检查手机无线控制和两端网络，恢复后再次点击确认连接，无需重新扫码。';
  let attempts = 0;
  f.client.respond = async request => {
    if (request.arguments.action === 'pair') return {structuredContent:{url:'http://127.0.0.1:1234/pair/',expires_at:Date.now()+300000},_meta:{pairingQr:'data:image/png;base64,AA=='}};
    if (request.arguments.action === 'pair_status') return {structuredContent:{pending:{host:'192.168.1.10'},error:attempts ? message : undefined}};
    if (request.arguments.action === 'pair_confirm') {attempts++;return {isError:true,content:[{type:'text',text:message}]};}
    return {structuredContent:{state:'unavailable'}};
  };
  await f.element('#pair').onclick();
  await f.timers.at(-1).fn();
  await f.element('#pair-confirm').onclick();
  await f.timers.at(-1).fn();
  assert.equal(f.element('#pair-confirm').hidden,false);
  assert.match(f.element('#pairing-status').textContent,/手机已扫码/);
  assert.equal(f.element('[role=alert]').textContent,message);
  assert.equal(attempts,1);
});


test('locked panel promises automatic recovery without requiring a resume button', async () => {
  const f = await fixture('en');
  f.client.ontoolresult({structuredContent:{state:'device_locked'}});
  assert.equal(f.element('#resume').hidden, true);
  assert.match(f.element('[role=status]').textContent, /resume automatically/);
  f.client.ontoolresult({structuredContent:live});
  assert.equal(f.element('img').hidden, false);
  assert.equal(f.client.calls.filter(r => r.name === 'tapilot_preview_resume').length, 0);
});

const phoneA = {id:'a'.repeat(64), name:'Phone A', selected:true, state:'connected'};
const phoneB = {id:'b'.repeat(64), name:'Phone B', selected:false, state:'not_connected'};
async function loadPicker(f, devices = [phoneA, phoneB]) {
  f.client.respond = async () => ({structuredContent:{devices}});
  f.element('#devices-panel').open = true;
  await f.element('#devices-panel').ontoggle();
}
test('paired phones are directly visible and selection updates connection actions', async () => {
  const f = await fixture('en'); await loadPicker(f);
  assert.equal(f.element('#devices-list').children.length, 2);
  assert.equal(f.element('#device-connect').hidden, true);
  assert.equal(f.element('#device-disconnect').hidden, false);
  const second = f.element('#devices-list').children[1];
  assert.equal(second.children[0].textContent, 'Phone B');
  assert.equal(second.children[1].textContent, 'Not connected');
  await second.onclick();
  assert.equal(f.element('#devices-list').children[1].attributes['aria-pressed'], 'true');
  assert.equal(f.element('#device-connect').hidden, true);
  assert.equal(f.element('#device-disconnect').hidden, true);
  assert.equal(f.element('#devices-panel').open, false);
  assert.equal(f.doc.activeElement, f.element('#devices-panel summary'));
});
test('pairing another phone keeps current connection and cancel restores its preview', async () => {
  const f = await fixture('en'); await loadPicker(f);
  f.client.calls = [];
  f.client.respond = async request => request.arguments.action === 'pair'
    ? {structuredContent:{url:'http://127.0.0.1:1234/pair/',expires_at:Date.now()+300000},_meta:{pairingQr:'data:image/png;base64,AA=='}}
    : {structuredContent:request.arguments.action === 'pair_cancel' ? {cancelled:true} : live};
  await f.element('#device-pair').onclick();
  assert.deepEqual(f.client.calls.map(r=>r.arguments.action), ['pair']);
  assert.equal(f.element('#pairing-qr').hidden, false);
  assert.equal(f.element('#pair-cancel').hidden, false);
  await f.element('#pair-cancel').onclick();
  await f.timers.at(-1).fn();
  assert.equal(f.element('#pairing-qr').hidden, true);
  assert.equal(f.element('img').hidden, false);
});
test('pairing and cancellation share one slot and hide saved-device controls until cancelled', async () => {
  const f = await fixture('en'); await loadPicker(f);
  f.client.calls=[];
  f.client.respond = async request => request.arguments.action === 'pair'
    ? {structuredContent:{url:'http://127.0.0.1:1234/pair/',expires_at:Date.now()+300000},_meta:{pairingQr:'data:image/png;base64,AA=='}}
    : {structuredContent:{...live,devices:[phoneA]}};
  await f.element('#device-pair').onclick();
  assert.equal(f.element('#device-pair').hidden, true);
  assert.equal(f.element('#pair-cancel').hidden, false);
  assert.equal(f.element('.device-picker').hidden, true);
  assert.equal(f.element('.device-actions').hidden, true);
  assert.equal(f.element('#devices-status').hidden, true);
  await f.element('#device-connect').onclick();
  assert.deepEqual(f.client.calls.map(r=>r.arguments.action), ['pair']);
  await f.element('#pair-cancel').onclick();
  assert.equal(f.element('#device-pair').hidden, false);
  assert.equal(f.element('#pair-cancel').hidden, true);
  assert.equal(f.element('.device-picker').hidden, false);
  assert.equal(f.element('.device-actions').hidden, false);
  assert.equal(f.element('#devices-status').hidden, false);
});

test('failed device refresh invalidates cached connected state and allows reconnect', async () => {
  const f = await fixture('en');
  f.client.respond = async () => ({structuredContent:{devices:[{id:'phone-a',name:'Phone A',selected:true,state:'connected'}]}});
  f.element('#devices-panel').open = true;
  await f.element('#devices-panel').ontoggle();
  assert.equal(f.element('#device-connect').hidden, true);
  f.client.respond = async () => ({isError:true});
  f.element('#devices-panel').open = true;
  await f.element('#devices-panel').ontoggle();
  assert.equal(f.element('#devices-list').children[0].children[1].textContent, 'Status unknown');
  assert.equal(f.element('#device-connect').hidden, true);
  assert.equal(f.element('#device-disconnect').hidden, true);
});

test('unavailable preview invalidates the previously connected device label', async () => {
  const f = await fixture('en');
  f.client.respond = async () => ({structuredContent:{devices:[{id:'phone-a',name:'Phone A',selected:true,state:'connected'}]}});
  f.element('#devices-panel').open = true;
  await f.element('#devices-panel').ontoggle();
  f.client.ontoolresult({structuredContent:{state:'unavailable'}});
  assert.equal(f.element('#devices-list').children[0].children[1].textContent, 'Reconnecting');
  assert.equal(f.element('#device-connect').hidden, true);
});

test('clicking a saved device connects once and ignores repeated clicks while connecting', async () => {
  const f = await fixture('en'); await loadPicker(f);
  f.client.calls = [];
  const pending = deferred();
  f.client.respond = request => request.arguments.action === 'connect' ? pending.promise
    : Promise.resolve({structuredContent:{devices:[{...phoneA,state:'not_connected'},{...phoneB,state:'connected',selected:true}]}});
  const row = f.element('#devices-list').children[1];
  const connecting = row.onclick();
  await row.onclick();
  assert.equal(f.element('[role=status]').textContent, 'Connecting… Phone B');
  assert.deepEqual(f.client.calls.map(r=>r.arguments), [{action:'connect',device_id:phoneB.id}]);
  pending.resolve({structuredContent:{state:'live'}});
  await connecting;
  assert.equal(f.element('#device-connect').hidden, true);
  await f.element('#devices-list').children[1].onclick();
  assert.equal(f.client.calls.filter(r=>r.arguments.action==='connect').length, 1);
});

for (const locale of ['zh-CN','en']) test(`device connection errors follow host locale ${locale}`, async () => {
  const f = await fixture(locale); await loadPicker(f);
  f.client.respond = async request => request.arguments.action === 'connect'
    ? {isError:true,structuredContent:{state:'unavailable',language:'en'},content:[{type:'text',text:'Phone connection failed. Check Wi-Fi and enable wireless control on the phone.'}]}
    : {structuredContent:{devices:[phoneA,phoneB]}};
  await f.element('#devices-list').children[1].onclick();
  assert.match(f.element('#recovery').textContent, locale==='zh-CN' ? /同一局域网 Wi-Fi.*亮屏并解锁/ : /same local Wi-Fi.*screen is on and unlocked/);
  assert.equal(f.element('#device-connect').disabled, false);
});

test('successful automatic recovery clears the earlier connection error', async () => {
  const f = await fixture('zh-CN'); await loadPicker(f);
  f.client.respond = async request => request.arguments.action === 'connect'
    ? {isError:true,structuredContent:{state:'unavailable'},content:[{type:'text',text:'Phone connection failed. Check Wi-Fi and enable wireless control on the phone.'}]}
    : {structuredContent:{devices:[phoneA,phoneB]}};
  await f.element('#devices-list').children[1].onclick();
  assert.equal(f.element('#recovery-details').hidden,false);
  assert.equal(f.element('[role=alert]').hidden,true);
  f.client.ontoolresult({structuredContent:live});
  assert.equal(f.element('#recovery-details').hidden,true);
  assert.equal(f.element('[role=alert]').hidden,true);
});

test('failed switch keeps picker open and offers retry only for failed target', async () => {
  const f = await fixture('en'); await loadPicker(f);
  const pending = deferred();
  f.client.respond = request => request.arguments.action === 'connect' ? pending.promise : Promise.resolve({structuredContent:{devices:[{...phoneA,selected:false}, {...phoneB,selected:true}]}});
  const work = f.element('#devices-list').children[1].onclick();
  assert.equal(f.element('#devices-list').children[1].attributes['aria-busy'], 'true');
  pending.resolve({isError:true,structuredContent:{state:'unavailable'}});
  await work;
  assert.equal(f.element('#devices-panel').open, true);
  assert.equal(f.element('#device-connect').hidden, false);
  assert.equal(f.element('#devices-list').children[1].attributes['aria-busy'], 'false');
  f.client.ontoolresult({structuredContent:live});
  assert.equal(f.element('#device-connect').hidden, true);
});

test('device keyboard navigation wraps and supports Home, End and Escape', async () => {
  const f = await fixture('en'); await loadPicker(f);
  const list = f.element('#devices-list');
  list.children[0].focus();
  const key = key => list.handlers.keydown({key,preventDefault(){}});
  key('ArrowDown'); assert.equal(f.doc.activeElement, list.children[1]);
  key('ArrowDown'); assert.equal(f.doc.activeElement, list.children[0]);
  key('End'); assert.equal(f.doc.activeElement, list.children[1]);
  key('Home'); assert.equal(f.doc.activeElement, list.children[0]);
  f.doc.handlers.keydown({key:'Escape'});
  assert.equal(f.element('#devices-panel').open, false);
  assert.equal(f.doc.activeElement, f.element('#devices-panel summary'));
});

test('expired pairing remains visible and can refresh or cancel without losing devices', async () => {
  const f = await fixture('zh-CN'); await loadPicker(f);
  f.client.respond = async () => ({structuredContent:{url:'http://127.0.0.1:1234/pair',expires_at:Date.now()-1}});
  await f.element('#device-pair').onclick();
  await f.timers.at(-1).fn();
  assert.equal(f.element('#pair-refresh').hidden, false);
  assert.equal(f.element('#pairing-status').textContent, '二维码已过期');
  assert.equal(f.element('#pair-cancel').hidden, false);
  f.client.respond = async () => ({structuredContent:{url:'http://127.0.0.1:1234/pair',expires_at:Date.now()+300000},_meta:{pairingQr:'data:image/png;base64,AA=='}});
  await f.element('#pair-refresh').onclick();
  assert.equal(f.element('#pair-refresh').hidden, true);
  assert.equal(f.element('#pairing-qr').hidden, false);
  await f.element('#pair-cancel').onclick();
  assert.equal(f.element('.device-picker').hidden, false);
});

test('only active device gets lock status; IP stays in device details', async () => {
  const f = await fixture('en'); await loadPicker(f, [{...phoneA,host:'192.168.1.2'},phoneB]);
  f.client.ontoolresult({structuredContent:{state:'device_locked',device_host:'192.168.1.2'}});
  assert.equal(f.element('#devices-list').children[0].children[1].textContent, 'Unlock phone');
  assert.equal(f.element('#devices-list').children[1].children[1].textContent, 'Not connected');
  assert.match(f.element('[role=status]').textContent, /Phone A/);
  assert.doesNotMatch(f.element('[role=status]').textContent, /192\.168/);
  assert.match(f.element('#device-details').textContent, /192\.168\.1\.2/);
});

test('a click inside a rebuilt picker is not mistaken for an outside click', async () => {
  const f = await fixture('en'); await loadPicker(f);
  const oldRow = f.element('#devices-list').children[1];
  f.doc.handlers.click({target:oldRow,composedPath:()=>[oldRow,f.element('#devices-list'),f.element('#devices-panel')]});
  assert.equal(f.element('#devices-panel').open,true);
  f.doc.handlers.click({target:f.element('outside'),composedPath:()=>[f.element('outside')]});
  assert.equal(f.element('#devices-panel').open,false);
});

test('live metadata without pixels cannot claim a visible connected screen', async () => {
  const f = await fixture('en');
  f.client.ontoolresult({structuredContent:{state:'live'}});
  assert.equal(f.element('img').hidden,true);
  assert.doesNotMatch(f.element('[role=status]').textContent,/Connected ·/);
});

test('failed image decoding leaves a recoverable status instead of connected', async () => {
  const f = await fixture('en',{autoLoad:false});
  f.element('img').onerror();
  assert.equal(f.element('img').hidden,true);
  assert.doesNotMatch(f.element('[role=status]').textContent,/Connected ·/);
  assert.equal(f.element('#resume').hidden,false);
});

test('address recovery connects only after the server verifies the new address', async () => {
  const f=await fixture('en'); await loadPicker(f);
  f.element('#device-address').value='100.82.240.18:39877';
  f.client.calls=[];
  f.client.respond=async request=>request.arguments.action==='update_address'
    ? {isError:true,content:[{type:'text',text:'Could not verify the paired phone at this address. Check the phone IP, wireless control, and network isolation.'}]}
    : {structuredContent:{devices:[phoneA,phoneB]}};
  await f.element('#device-address-save').onclick();
  assert.equal(f.client.calls.some(r=>r.arguments.action==='connect'),false);
  assert.match(f.element('[role=alert]').textContent,/Could not verify/);
  f.client.calls=[];
  f.client.respond=async()=>({structuredContent:{devices:[phoneA,phoneB],state:'live'}});
  await f.element('#device-address-save').onclick();
  assert.deepEqual(f.client.calls.filter(r=>r.arguments.action!=='list').map(r=>r.arguments.action),['update_address','connect']);
});

test('slow preview clears the old screen and connected label while awaiting the response', async () => {
  const f=await fixture('en'); await loadPicker(f);
  const pending=deferred();f.client.respond=()=>pending.promise;
  const poll=f.timers.find(timer=>timer.delay===0).fn;
  const work=poll();
  f.timers.find(timer=>timer.delay===5000).fn();
  assert.equal(f.element('img').hidden,true);
  assert.doesNotMatch(f.element('[role=status]').textContent,/Connected ·/);
  pending.resolve({structuredContent:live});await work;await Promise.resolve();
  assert.match(f.element('[role=status]').textContent,/Connected ·/);
});

test('QR creation shows progress and cancellation ignores a late QR response', async () => {
  const f=await fixture('en'), pending=deferred();
  f.client.respond=request=>request.arguments.action==='pair'?pending.promise:Promise.resolve({structuredContent:{cancelled:true}});
  const opening=f.element('#pair').onclick();
  assert.equal(f.element('#pairing-status').hidden,false);
  assert.equal(f.element('#pairing-status').textContent,'Creating QR code…');
  assert.equal(f.element('#pair-cancel').disabled,false);
  await f.element('#pair-cancel').onclick();
  pending.resolve({structuredContent:{url:'http://127.0.0.1:1234/pair',expires_at:Date.now()+300000},_meta:{pairingQr:'data:image/png;base64,AA=='}});
  await opening;
  assert.equal(f.element('#pairing-qr').hidden,true);
  assert.equal(f.element('#devices-panel').hidden,false);
});

test('QR creation deadline leaves visible retry and cancel actions', async () => {
  const f=await fixture('en');f.client.respond=()=>new Promise(()=>{});
  const opening=f.element('#pair').onclick();
  f.timers.find(timer=>timer.delay===12000).fn();
  await opening;
  assert.equal(f.element('#pair-refresh').hidden,false);
  assert.equal(f.element('#pair-refresh').disabled,false);
  assert.equal(f.element('#pair-cancel').disabled,false);
  assert.match(f.element('#pairing-status').textContent,/Could not create/);
});

test('connection failures expose scan recovery without asking the user for an IP', async () => {
  const f=await fixture('en');f.client.ontoolresult({structuredContent:{state:'unavailable'}});
  assert.equal(f.element('#reconnect-pair').hidden,false);
  f.client.respond=async()=>({structuredContent:{url:'http://127.0.0.1:1234/pair',expires_at:Date.now()+300000},_meta:{pairingQr:'data:image/png;base64,AA=='}});
  await f.element('#reconnect-pair').onclick();
  assert.equal(f.element('#pairing-qr').hidden,false);
  assert.equal(f.element('#reconnect-pair').hidden,true);
});

test('pairing checks at 300ms and shows confirmation without waiting for the device list', async () => {
 const f=await fixture('en');
 f.client.respond=async()=>({structuredContent:{url:'http://127.0.0.1:1234/pair',expires_at:Date.now()+300000},_meta:{pairingQr:'data:image/png;base64,AA=='}});
 await f.element('#pair').onclick();
 f.client.calls=[];
 f.client.respond=async request=>{
   assert.equal(request.arguments.action,'pair_status');
   return {structuredContent:{pending:{host:'192.168.1.20'}}};
 };
 await f.timers.at(-1).fn();
 assert.equal(f.element('#pair-confirm').hidden,false);
 assert.equal(f.timers.at(-1).delay,300);
 assert.equal(f.client.calls.length,1);
});

const qrResult = () => ({structuredContent:{url:'http://127.0.0.1:1234/pair',expires_at:Date.now()+300000},_meta:{pairingQr:'data:image/png;base64,AA=='}});

test('a stalled old preview cannot block scan status or overwrite its polling timer', async () => {
 const f=await fixture('en'); await loadPicker(f);
 const pending=deferred();
 f.client.respond=request=>request.name==='tapilot_preview_frame'?pending.promise:Promise.resolve(qrResult());
 const oldPoll=f.timers.at(-1).fn();
 await Promise.resolve();
 await f.element('#pair').onclick();
 f.client.respond=async()=>({structuredContent:{pending:{host:'192.168.1.20'}}});
 await f.timers.at(-1).fn();
 assert.equal(f.element('#pair-confirm').hidden,false);
 assert.equal(f.timers.at(-1).delay,300);
 const timer=f.timers.at(-1);
 pending.resolve({structuredContent:live}); await oldPoll;
 assert.equal(f.timers.at(-1),timer);
});

test('cancelling a visible QR immediately restores a recoverable screen', async () => {
 const f=await fixture('en');f.client.respond=async()=>qrResult();
 await f.element('#pair').onclick();
 f.client.respond=async()=>({structuredContent:{cancelled:true}});
 await f.element('#pair-cancel').onclick();
 assert.equal(f.element('#pairing-qr').hidden,true);
 assert.equal(f.element('#resume').hidden,false);
 assert.doesNotMatch(f.element('[role=status]').textContent,/Scan with your phone/);
 assert.equal(f.element('img').hidden,true);
});

test('scan status transport failure exposes retry instead of leaving an inert QR', async () => {
 const f=await fixture('en');f.client.respond=async()=>qrResult();
 await f.element('#pair').onclick();
 f.client.respond=async()=>{throw Error('host transport failed');};
 await f.timers.at(-1).fn();
 assert.equal(f.element('#pair-refresh').hidden,false);
 assert.equal(f.element('#pairing-qr').hidden,true);
});

test('pair confirmation shows progress and clears the QR on completion', async () => {
 const f=await fixture('en');f.client.respond=async()=>qrResult();
 await f.element('#pair').onclick();
 const pending=deferred();f.client.respond=request=>request.arguments.action==='pair_confirm'?pending.promise:Promise.resolve({structuredContent:{devices:[]}});
 const work=f.element('#pair-confirm').onclick();
 assert.match(f.element('#pairing-status').textContent,/Confirming/);
 pending.resolve({structuredContent:{paired:true}});await work;
 assert.equal(f.element('#pairing-qr').hidden,true);
});

test('pairing disables unrelated disconnect and pause actions', async () => {
 const f=await fixture('en');f.client.respond=async()=>qrResult();
 await f.element('#pair').onclick();
 f.client.calls=[];
 assert.equal(f.element('#disconnect').disabled,true);
 assert.equal(f.element('#pause').disabled,true);
 await f.element('#disconnect').onclick();
 await f.element('#pause').onclick();
 assert.equal(f.client.calls.length,0);
});

test('forgetting the active phone removes its stale name from the screen', async () => {
 const f=await fixture('en');await loadPicker(f);
 f.client.respond=async()=>({structuredContent:{state:'disconnected',devices:[]}});
 await f.element('#device-forget-confirm').onclick();
 assert.equal(f.element('#device-details').textContent,'');
 assert.doesNotMatch(f.element('[role=status]').textContent,/Phone A/);
});

test('returning after QR creation was interrupted does not leave every button disabled', async () => {
 const f=await fixture('en'),pending=deferred();f.client.respond=()=>pending.promise;
 const opening=f.element('#pair').onclick();
 f.win.handlers.pagehide();f.win.handlers.pageshow();
 pending.resolve(qrResult());await opening;
 assert.equal(f.element('#pair').disabled,false);
 assert.equal(f.element('#device-pair').disabled,false);
 assert.equal(f.element('#pairing-qr').hidden,true);
});

test('a hung scan-status request times out into retry and ignores its late answer', async () => {
 const f=await fixture('en');f.client.respond=async()=>qrResult();
 await f.element('#pair').onclick();
 const pending=deferred();f.client.respond=()=>pending.promise;
 const work=f.timers.at(-1).fn();
 f.timers.at(-1).fn();
 await work;
 assert.equal(f.element('#pair-refresh').hidden,false);
 pending.resolve({structuredContent:{pending:{host:'192.168.1.20'}}});await Promise.resolve();
 assert.equal(f.element('#pair-confirm').hidden,true);
});

test('a failed pairing confirmation restores controls and ignores an older scan status', async () => {
 const f=await fixture('en');f.client.respond=async()=>qrResult();
 await f.element('#pair').onclick();
 const status=deferred(),confirmation=deferred();
 f.client.respond=request=>request.arguments.action==='pair_status'?status.promise:confirmation.promise;
 const polling=f.timers.at(-1).fn();
 const confirming=f.element('#pair-confirm').onclick();
 status.resolve({structuredContent:{paired:true}});await polling;
 assert.equal(f.element('#pairing-qr').hidden,false);
 assert.match(f.element('#pairing-status').textContent,/Confirming/);
 confirmation.resolve({isError:true,content:[{type:'text',text:'Phone connection timed out'}]});await confirming;
 assert.equal(f.element('#pair-confirm').disabled,false);
 assert.equal(f.element('#pair-cancel').disabled,false);
 assert.equal(f.element('[role=alert]').hidden,false);
});

test('reconnect renders its returned frame immediately and keeps its viewer identity', async () => {
 const f=await fixture('en');f.client.respond=async()=>({structuredContent:live});
 await f.element('#resume').onclick();
 await Promise.resolve();
 assert.equal(f.element('img').hidden,false);
 assert.equal(f.back.disabled,false);
 const resumed=f.client.calls.find(r=>r.name==='tapilot_preview_resume');
 assert.ok(resumed.arguments.viewer_id);
 await f.back.onclick();
 const control=f.client.calls.find(r=>r.name==='tapilot_preview_control');
 assert.equal(control.arguments.viewer_id,resumed.arguments.viewer_id);
});

test('missing QR image offers the pairing window without a false generating message', async () => {
 const f=await fixture('en');
 f.client.respond=async()=>({...qrResult(),_meta:{}});
 await f.element('#pair').onclick();
 assert.equal(f.element('#pairing-link').hidden,false);
 assert.doesNotMatch(f.element('#pairing-status').textContent,/Creating/);
 assert.match(f.element('#pairing-status').textContent,/window/);
});

test('QR image decoding failure offers refresh and ignores errors from an old QR', async () => {
 const f=await fixture('en');f.client.respond=async()=>qrResult();
 await f.element('#pair').onclick();
 const oldError=f.element('#pairing-qr').onerror;
 assert.equal(typeof oldError,'function');
 oldError();
 assert.equal(f.element('#pairing-qr').hidden,true);
 assert.equal(f.element('#pair-refresh').hidden,false);
 assert.equal(f.element('#pairing-link').hidden,false);
 await f.element('#pair-refresh').onclick();
 oldError();
 assert.equal(f.element('#pairing-qr').hidden,false);
 assert.equal(f.element('#pair-refresh').hidden,true);
});


test('read-only host keeps preview but hides manual, pairing and device mutations', async () => {
  const f = await fixture('en', {host:{interactive:false,transport:'resources'}});
  assert.equal(f.element('img').hidden, false);
  assert.equal(f.back.disabled, true);
  assert.equal(f.element('.navigation').hidden, true);
  assert.equal(f.element('.text-entry').hidden, true);
  assert.equal(f.element('#device-pair').hidden, true);
  assert.equal(f.element('#host-notice').hidden, false);
  assert.match(f.element('#host-notice').textContent, /Live preview/);
  assert.match(f.element('[role=status]').textContent, /Connected.*Live preview/);
  await f.back.onclick();
  assert.equal(f.client.calls.length, 0);
  f.client.ontoolresult({structuredContent:{state:'setup_required'}});
  assert.equal(f.element('#pair').hidden, true);
});


test('stalled reconnect releases busy controls and ignores the late frame', async () => {
  const f = await fixture('en');
  const pending = deferred();
  f.client.respond = () => pending.promise;
  const work = f.element('#resume').onclick();
  await Promise.resolve();
  assert.equal(f.element('#resume').disabled, true);
  f.timers.filter(timer => timer.delay === 45000).at(-1).fn();
  await work;
  assert.equal(f.element('#resume').disabled, false);
  assert.equal(f.element('img').hidden, true);
  pending.resolve({ structuredContent: live });
  await Promise.resolve();
  assert.equal(f.element('img').hidden, true, 'late reply must not revive a consumed frame');
});


test('a stalled preview deadline schedules another observation and never accepts its late frame', async () => {
  const f = await fixture('en');
  await loadPicker(f);
  const pending = deferred();
  f.client.respond = request => request.name === 'tapilot_preview_frame' ? pending.promise : Promise.resolve({structuredContent:{devices:[]}});
  const work = f.timers.find(timer => timer.delay === 0).fn();
  for (let step=0; step<12 && !f.client.calls.some(request=>request.name === "tapilot_preview_frame"); step++) await Promise.resolve();
  assert.equal(f.client.calls.some(request=>request.name === "tapilot_preview_frame"),true);
  f.timers.filter(timer => timer.delay === 45000).at(-1).fn();
  await work;
  assert.equal(f.element('img').hidden, true);
  assert.equal(f.timers.at(-1).delay, 4000);
  pending.resolve({structuredContent:live});
  await Promise.resolve();
  assert.equal(f.element('img').hidden, true);
});
